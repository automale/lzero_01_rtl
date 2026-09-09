package npu.core.memory

import chisel3._
import chisel3.util._

// ============================================================================
// Generic 64B-line parameter OCM for QB / FB.
//
// Physical organization:
//   Ping : 4KB = 64 x 512b
//   Pong : 4KB = 64 x 512b
//
// Each bank supports one 512b DMA write and one 512b CU read port.
// Ping/Pong addressing prevents intentional same-address read/write use.
//
// CACHE mode:
//   tensor_lines <= 128
//   both banks form one unified 8KB circular store.
//   DMA loads the full tensor before ready.
//
// SPILL mode:
//   each 4KB chunk is a logical ping/pong bank.
//   CU consumes one bank while DMA refills the empty bank.
//   DMA source addressing is expected to be circular over the tensor so that
//   after tensor_last the next hungry service starts again from tensor base.
//
// Read contract:
//   req_line at cycle t -> line_valid/data at t+1.
// ============================================================================
class WideLineParamBuffer(
  val lineBytes: Int = 64,
  val chunkBytes: Int = 4096
) extends Module {

  require(lineBytes == 64)
  require(chunkBytes % lineBytes == 0)

  private val lineBits = lineBytes * 8
  private val linesPerChunk = chunkBytes / lineBytes
  private val totalCacheLines = 2 * linesPerChunk
  private val lineAddrBits = math.max(1, log2Ceil(linesPerChunk))
  private val cacheAddrBits = math.max(1, log2Ceil(totalCacheLines))
  private val countBits = math.max(1, log2Ceil(linesPerChunk + 1))

  val io = IO(new Bundle {
    val soft_reset = Input(Bool())
    val enable     = Input(Bool())
    val shoot      = Input(Bool())

    val cache_mode = Input(Bool())

    // Number of valid 64B lines in the full tensor.
    // CACHE: must be 1..128.
    // SPILL: informational for DFD; chunk boundaries come from DMA.
    val tensor_lines = Input(UInt(32.W))

    // DMA write side.
    val dma_valid       = Input(Bool())
    val dma_data        = Input(UInt(lineBits.W))
    val dma_chunk_last  = Input(Bool())
    val dma_tensor_last = Input(Bool())

    // CU line-pull side.
    val req_line = Input(Bool())
    val line_data  = Output(UInt(lineBits.W))
    val line_valid = Output(Bool())

    // Initializer / DMA arbiter status.
    val ready     = Output(Bool())
    val hungry    = Output(Bool())
    val impending = Output(Bool())
    val working   = Output(Bool())

    // Pulses when the consumer crosses the logical tensor end.
    // In SPILL mode DMA may use this as an optional address-wrap hint.
    val tensor_wrap = Output(Bool())

    val sync_alert = Output(Bool())
  })

  val ping = SyncReadMem(linesPerChunk, UInt(lineBits.W))
  val pong = SyncReadMem(linesPerChunk, UInt(lineBits.W))

  val bankFull = RegInit(VecInit(Seq.fill(2)(false.B)))
  val bankLines = RegInit(VecInit(Seq.fill(2)(0.U(countBits.W))))
  val bankLastTag = RegInit(VecInit(Seq.fill(2)(false.B)))

  val writeBank = RegInit(0.U(1.W))
  val writePtr  = RegInit(0.U(lineAddrBits.W))

  val activeBank = RegInit(0.U(1.W))
  val spillReadPtr = RegInit(0.U(lineAddrBits.W))
  val cacheReadPtr = RegInit(0.U(cacheAddrBits.W))

  val tensorLoaded = RegInit(false.B)
  val started = RegInit(false.B)

  // --------------------------------------------------------------------------
  // DMA write path.
  // --------------------------------------------------------------------------
  val writeBlocked = bankFull(writeBank)
  val dmaFire = io.dma_valid && io.enable && !writeBlocked

  when(dmaFire) {
    when(writeBank === 0.U) {
      ping.write(writePtr, io.dma_data)
    }.otherwise {
      pong.write(writePtr, io.dma_data)
    }

    val physicalLast = writePtr === (linesPerChunk - 1).U
    val closeChunk = io.dma_chunk_last || physicalLast

    when(closeChunk) {
      bankFull(writeBank) := true.B
      bankLines(writeBank) := writePtr + 1.U
      bankLastTag(writeBank) := io.dma_tensor_last
      writePtr := 0.U
      writeBank := ~writeBank

      when(io.dma_tensor_last) {
        tensorLoaded := true.B
      }
    }.otherwise {
      writePtr := writePtr + 1.U
    }
  }

  // --------------------------------------------------------------------------
  // Ready / start barrier.
  // --------------------------------------------------------------------------
  val cacheTensorSizeOk =
    (io.tensor_lines =/= 0.U) &&
    (io.tensor_lines <= totalCacheLines.U)

  val spillPreloaded =
    bankFull(0)

  io.ready :=
    !io.enable ||
    Mux(
      io.cache_mode,
      tensorLoaded && cacheTensorSizeOk,
      spillPreloaded
    )

  when(io.soft_reset || !io.enable) {
    started := false.B
  }.elsewhen(io.shoot && io.ready) {
    started := true.B
  }

  io.working := started

  // --------------------------------------------------------------------------
  // Read request selection.
  // --------------------------------------------------------------------------
  val cacheReqOk =
    started &&
    io.cache_mode &&
    cacheTensorSizeOk

  val spillReqOk =
    started &&
    !io.cache_mode &&
    bankFull(activeBank)

  val readFire =
    io.req_line &&
    (cacheReqOk || spillReqOk)

  val readBank = Wire(UInt(1.W))
  val readAddr = Wire(UInt(lineAddrBits.W))

  when(io.cache_mode) {
    readBank :=
      Mux(
        cacheReadPtr < linesPerChunk.U,
        0.U,
        1.U
      )

    readAddr :=
      Mux(
        cacheReadPtr < linesPerChunk.U,
        cacheReadPtr(lineAddrBits - 1, 0),
        (cacheReadPtr - linesPerChunk.U)(lineAddrBits - 1, 0)
      )
  }.otherwise {
    readBank := activeBank
    readAddr := spillReadPtr
  }

  val pingReadData =
    ping.read(
      readAddr,
      readFire && (readBank === 0.U)
    )

  val pongReadData =
    pong.read(
      readAddr,
      readFire && (readBank === 1.U)
    )

  val readBankD1 =
    RegEnable(readBank, 0.U, readFire)

  val readFireD1 =
    RegNext(readFire, false.B)

  io.line_data :=
    Mux(readBankD1 === 0.U, pingReadData, pongReadData)

  io.line_valid := readFireD1

  // --------------------------------------------------------------------------
  // CACHE circular progress.
  // --------------------------------------------------------------------------
  val cacheLastLine =
    cacheReadPtr === (io.tensor_lines - 1.U)

  val cacheWrapFire =
    readFire &&
    io.cache_mode &&
    cacheLastLine

  when(readFire && io.cache_mode) {
    when(cacheLastLine) {
      cacheReadPtr := 0.U
    }.otherwise {
      cacheReadPtr := cacheReadPtr + 1.U
    }
  }

  // --------------------------------------------------------------------------
  // SPILL ping-pong progress.
  //
  // Current bank is released one cycle after issuing its final read, so DMA
  // cannot overwrite a location whose SyncReadMem response is still pending.
  // --------------------------------------------------------------------------
  val activeValidLines =
    bankLines(activeBank)

  val spillLastLine =
    spillReadPtr === (activeValidLines - 1.U)

  val spillLastReq =
    readFire &&
    !io.cache_mode &&
    spillLastLine

  val spillLastTagNow =
    bankLastTag(activeBank)

  val freePending =
    RegNext(spillLastReq, false.B)

  val freeBankD1 =
    RegEnable(activeBank, 0.U, spillLastReq)

  when(spillLastReq) {
    activeBank := ~activeBank
    spillReadPtr := 0.U
  }.elsewhen(readFire && !io.cache_mode) {
    spillReadPtr := spillReadPtr + 1.U
  }

  when(freePending) {
    bankFull(freeBankD1) := false.B
    bankLines(freeBankD1) := 0.U
    bankLastTag(freeBankD1) := false.B

    // If DMA had been blocked because both banks were full, start writing into
    // the newly freed bank.
    when(bankFull(writeBank)) {
      writeBank := freeBankD1
      writePtr := 0.U
    }
  }

  val spillWrapFire =
    spillLastReq &&
    spillLastTagNow

  io.tensor_wrap :=
    cacheWrapFire ||
    spillWrapFire

  // --------------------------------------------------------------------------
  // DMA refill status.
  //
  // CACHE: request data until the complete tensor has been loaded.
  // SPILL: keep filling any empty bank. DMA source generator is circular.
  // --------------------------------------------------------------------------
  val bothFull =
    bankFull(0) && bankFull(1)

  io.hungry :=
    io.enable &&
    Mux(
      io.cache_mode,
      !tensorLoaded && !bothFull,
      !bothFull
    )

  val activeRemaining =
    Mux(
      bankFull(activeBank),
      bankLines(activeBank) - spillReadPtr,
      0.U
    )

  io.impending :=
    started &&
    !io.cache_mode &&
    (activeRemaining <= 8.U) &&
    !bankFull(~activeBank)

  // --------------------------------------------------------------------------
  // Reset / DFD.
  // --------------------------------------------------------------------------
  when(io.soft_reset) {
    bankFull := VecInit(Seq.fill(2)(false.B))
    bankLines := VecInit(Seq.fill(2)(0.U(countBits.W)))
    bankLastTag := VecInit(Seq.fill(2)(false.B))

    writeBank := 0.U
    writePtr := 0.U

    activeBank := 0.U
    spillReadPtr := 0.U
    cacheReadPtr := 0.U

    tensorLoaded := false.B
    started := false.B
  }

  val badCacheSize =
    io.enable &&
    io.cache_mode &&
    !cacheTensorSizeOk

  val readUnderflow =
    io.req_line &&
    started &&
    !(
      cacheReqOk ||
      spillReqOk
    )

  val dmaOverflow =
    io.dma_valid &&
    io.enable &&
    writeBlocked

  val chunkProtocolError =
    dmaFire &&
    io.dma_tensor_last &&
    !io.dma_chunk_last &&
    (writePtr =/= (linesPerChunk - 1).U)

  io.sync_alert :=
    badCacheSize ||
    readUnderflow ||
    dmaOverflow ||
    chunkProtocolError
}

// Semantic wrappers. They intentionally share identical OCM mechanics;
// only the consumer-side interpretation of each 64B line differs.
class QbController extends WideLineParamBuffer()
class FbController extends WideLineParamBuffer()
