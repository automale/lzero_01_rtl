package npu.core

import chisel3._
import chisel3.util._
import npu.utils.Universal_Wide_LUT

// ============================================================================
// One RoPE feature pair: lanes (2j, 2j+1).
//
// Input beat already contains both members of the pair.
// A single angle index is issued to replicated Sin/Cos LUTs.
// LUT latency is one cycle; x_even/x_odd are delayed to match.
// ============================================================================
class RopePairCore(
  val inBits: Int,
  val outBits: Int,
  val indexBits: Int,
  val trigBits: Int,
  val writeBits: Int,
  val trigFracBits: Int
) extends Module {

  require(writeBits % trigBits == 0)

  private val wordsPerBurst = writeBits / trigBits
  private val burstAddrBits =
    math.max(1, log2Ceil((1 << indexBits) / wordsPerBurst))

  val io = IO(new Bundle {
    val x_even   = Input(SInt(inBits.W))
    val x_odd    = Input(SInt(inBits.W))
    val in_valid = Input(Bool())

    val angle_idx = Input(UInt(indexBits.W))
    val stall     = Input(Bool())

    val lut_cos_wr_en = Input(Bool())
    val lut_sin_wr_en = Input(Bool())
    val lut_wr_addr   = Input(UInt(burstAddrBits.W))
    val lut_wr_data   = Input(Vec(wordsPerBurst, UInt(trigBits.W)))

    val y_even    = Output(SInt(outBits.W))
    val y_odd     = Output(SInt(outBits.W))
    val out_valid = Output(Bool())

    val lut_ready  = Output(Bool())
    val sync_alert = Output(Bool())
  })

  val run = !io.stall
  val accept = io.in_valid && run

  val cosLut =
    Module(new Universal_Wide_LUT(indexBits, trigBits, writeBits))

  val sinLut =
    Module(new Universal_Wide_LUT(indexBits, trigBits, writeBits))

  cosLut.io.wr_en   := io.lut_cos_wr_en
  cosLut.io.wr_addr := io.lut_wr_addr
  cosLut.io.wr_data := io.lut_wr_data

  sinLut.io.wr_en   := io.lut_sin_wr_en
  sinLut.io.wr_addr := io.lut_wr_addr
  sinLut.io.wr_data := io.lut_wr_data

  cosLut.io.rd_en   := accept
  cosLut.io.rd_addr := io.angle_idx

  sinLut.io.rd_en   := accept
  sinLut.io.rd_addr := io.angle_idx

  val xEvenD1 = RegEnable(io.x_even, 0.S, accept)
  val xOddD1  = RegEnable(io.x_odd, 0.S, accept)

  val coeffValid =
    cosLut.io.rd_valid &&
    sinLut.io.rd_valid

  val cos = cosLut.io.rd_data.asSInt
  val sin = sinLut.io.rd_data.asSInt

  val evenFull =
    ((xEvenD1 * cos) -& (xOddD1 * sin)).asSInt

  val oddFull =
    ((xEvenD1 * sin) +& (xOddD1 * cos)).asSInt

  val evenScaled =
    (evenFull >> trigFracBits).asSInt

  val oddScaled =
    (oddFull >> trigFracBits).asSInt

  def satToOut(x: SInt): SInt = {
    val maxV = ((BigInt(1) << (outBits - 1)) - 1).S
    val minV = (-(BigInt(1) << (outBits - 1))).S
    val clipped = Mux(x > maxV, maxV, Mux(x < minV, minV, x))
    clipped.asUInt(outBits - 1, 0).asSInt
  }

  val rawEven = satToOut(evenScaled)
  val rawOdd  = satToOut(oddScaled)

  // A SyncReadMem response may arrive on the first wall-clock stall cycle.
  // Retain the completed pair and release it when run resumes.
  val holdValid = RegInit(false.B)
  val holdEven  = Reg(SInt(outBits.W))
  val holdOdd   = Reg(SInt(outBits.W))

  when(io.stall && coeffValid && !holdValid) {
    holdValid := true.B
    holdEven := rawEven
    holdOdd := rawOdd
  }.elsewhen(run && holdValid) {
    holdValid := false.B
  }

  io.y_even := Mux(holdValid, holdEven, rawEven)
  io.y_odd  := Mux(holdValid, holdOdd, rawOdd)
  io.out_valid := run && (holdValid || coeffValid)

  val expectedRd = RegNext(accept, false.B)
  val expectedCosWr = RegNext(io.lut_cos_wr_en, false.B)
  val expectedSinWr = RegNext(io.lut_sin_wr_en, false.B)

  io.sync_alert :=
    (expectedRd =/= cosLut.io.rd_valid) ||
    (expectedRd =/= sinLut.io.rd_valid) ||
    (expectedCosWr =/= cosLut.io.wr_valid) ||
    (expectedSinWr =/= sinLut.io.wr_valid) ||
    (holdValid && coeffValid && run)

  io.lut_ready :=
    cosLut.io.lut_ready &&
    sinLut.io.lut_ready
}

// ============================================================================
// 16-lane RoPE Unit
//
// Physical beat:
//   one output-matrix row x 16 consecutive columns.
//   Therefore 8 RoPE pairs are processed in parallel each accepted cycle.
//
// Position:
//   position_init loads shadow_m_base.
//   row_change_update occurs on row0 of the first N tile of a new 16-row
//   M group. On that cycle:
//      active_m_base <- shadow_m_base
//      shadow_m_base <- shadow_m_base + 16
//   Current row position is active/base + rowCounter.
//
// Frequency:
//   one 64-byte FB line = 32 x UInt16 frequencies.
//   8 frequencies are used by one 16-column output tile.
//   Therefore one line contains 4 tile-frequency groups.
//   active line is locally indexed; shadow line prefetches the next 64B line.
//
// Frequency fixed-point contract:
//   UInt16 Q0.16 turns/token
//   freq = theta/(2*pi) * 2^16
// ============================================================================
class RopeUnit(
  val numLines: Int = 16,
  val inBits: Int = 8,
  val outBits: Int = 8,
  val indexBits: Int = 10,
  val trigBits: Int = 16,
  val writeBits: Int = 256,
  val trigFracBits: Int = 14,
  val freqBits: Int = 16,
  val mBits: Int = 32
) extends Module {

  require(numLines == 16)
  require(freqBits == 16)
  require(indexBits <= freqBits)
  require(writeBits % trigBits == 0)

  private val numPairs = numLines / 2
  private val freqsPerLine = 512 / freqBits
  private val groupsPerLine = freqsPerLine / numPairs
  require(freqsPerLine == 32)
  require(groupsPerLine == 4)

  private val wordsPerBurst = writeBits / trigBits
  private val burstAddrBits =
    math.max(1, log2Ceil((1 << indexBits) / wordsPerBurst))

  val io = IO(new Bundle {
    val in_vec   = Input(Vec(numLines, SInt(inBits.W)))
    val in_valid = Input(Vec(numLines, Bool()))

    val rope_en    = Input(Bool())
    val stall      = Input(Bool())
    val soft_reset = Input(Bool())

    // TPU metadata: row0 of first N tile in each new 16-row M group.
    val row_change_update = Input(Bool())

    // npu_struct / Initializer position programming.
    val position_init = Input(Bool())
    val base_m_in     = Input(UInt(mBits.W))

    // FB line interface: 64B = 32 x 16-bit frequencies.
    val freq_req_line   = Output(Bool())
    val freq_line_in    = Input(Vec(freqsPerLine, UInt(freqBits.W)))
    val freq_line_valid = Input(Bool())

    val lut_cos_wr_en = Input(Bool())
    val lut_sin_wr_en = Input(Bool())
    val lut_wr_addr   = Input(UInt(burstAddrBits.W))
    val lut_wr_data   = Input(Vec(wordsPerBurst, UInt(trigBits.W)))

    val out_vec   = Output(Vec(numLines, SInt(outBits.W)))
    val out_valid = Output(Vec(numLines, Bool()))

    val prefetch_ready = Output(Bool())
    val lut_ready      = Output(Bool())
    val sync_alert     = Output(Bool())
  })

  val run = !io.stall

  val anyValid = io.in_valid.asUInt.orR
  val allValid = io.in_valid.asUInt.andR
  val inputFire = allValid && run && io.rope_en

  // ==========================================================================
  // 16 physical rows per output tile.
  // ==========================================================================
  val rowCounter = RegInit(0.U(4.W))
  val tileStart = inputFire && (rowCounter === 0.U)
  val tileEnd   = inputFire && (rowCounter === 15.U)

  when(io.soft_reset || (!io.rope_en && run)) {
    rowCounter := 0.U
  }.elsewhen(inputFire) {
    when(tileEnd) {
      rowCounter := 0.U
    }.otherwise {
      rowCounter := rowCounter + 1.U
    }
  }

  // ==========================================================================
  // Position shadow / active.
  // ==========================================================================
  val activeMBase = RegInit(0.U(mBits.W))
  val shadowMBase = RegInit(0.U(mBits.W))
  val activeMValid = RegInit(false.B)
  val shadowMValid = RegInit(false.B)

  when(io.soft_reset) {
    activeMBase := 0.U
    shadowMBase := 0.U
    activeMValid := false.B
    shadowMValid := false.B
  }.otherwise {
    when(io.position_init) {
      shadowMBase := io.base_m_in
      shadowMValid := true.B
    }

    when(inputFire && io.row_change_update) {
      when(shadowMValid) {
        activeMBase := shadowMBase
        activeMValid := true.B
        shadowMBase := shadowMBase + 16.U
        shadowMValid := true.B
      }
    }
  }

  val positionSwap =
    inputFire &&
    io.row_change_update

  val effectiveMBase =
    Mux(positionSwap, shadowMBase, activeMBase)

  val currentM =
    effectiveMBase + rowCounter

  // ==========================================================================
  // Frequency 64B line shadow / active.
  // ==========================================================================
  val activeFreqLine =
    RegInit(VecInit(Seq.fill(freqsPerLine)(0.U(freqBits.W))))

  val shadowFreqLine =
    RegInit(VecInit(Seq.fill(freqsPerLine)(0.U(freqBits.W))))

  val activeFreqValid = RegInit(false.B)
  val shadowFreqValid = RegInit(false.B)
  val freqReqOutstanding = RegInit(false.B)

  // Index of the frequency group used by the current tile inside active line.
  val freqGroup = RegInit(0.U(2.W))

  // First tile consumes shadow line group0. Every fourth tile after that consumes
  // the next shadow line group0.
  val consumeFreqLine =
    tileStart &&
    (!activeFreqValid || (freqGroup === 3.U))

  val nextGroup =
    Mux(
      !activeFreqValid || (freqGroup === 3.U),
      0.U(2.W),
      freqGroup + 1.U
    )

  io.freq_req_line :=
    io.rope_en &&
    !freqReqOutstanding &&
    (!shadowFreqValid || consumeFreqLine)

  when(io.soft_reset) {
    activeFreqValid := false.B
    shadowFreqValid := false.B
    freqReqOutstanding := false.B
    freqGroup := 0.U
  }.otherwise {
    when(io.freq_req_line) {
      freqReqOutstanding := true.B
    }

    when(tileStart) {
      when(consumeFreqLine) {
        when(shadowFreqValid) {
          activeFreqLine := shadowFreqLine
          activeFreqValid := true.B
        }
        shadowFreqValid := false.B
        freqGroup := 0.U
      }.otherwise {
        freqGroup := freqGroup + 1.U
      }
    }

    // Response priority refills shadow after same-cycle line consumption.
    when(io.freq_line_valid) {
      shadowFreqLine := io.freq_line_in
      shadowFreqValid := true.B
      freqReqOutstanding := false.B
    }
  }

  // Current beat must use the NEW line/group on tileStart.
  val groupForBeat =
    Mux(tileStart, nextGroup, freqGroup)

  val useShadowLine =
    consumeFreqLine

  // ==========================================================================
  // 8 parallel RoPE pairs.
  // ==========================================================================
  val pairs =
    Seq.fill(numPairs)(
      Module(new RopePairCore(
        inBits = inBits,
        outBits = outBits,
        indexBits = indexBits,
        trigBits = trigBits,
        writeBits = writeBits,
        trigFracBits = trigFracBits
      ))
    )

  for (p <- 0 until numPairs) {
    val freqIndex =
      (groupForBeat << 3) + p.U

    val theta =
      Mux(
        useShadowLine,
        shadowFreqLine(freqIndex),
        activeFreqLine(freqIndex)
      )

    val phaseProduct =
      currentM * theta

    // modulo one turn in Q0.16.
    val phaseModulo =
      phaseProduct(freqBits - 1, 0)

    val angleIdx =
      phaseModulo(freqBits - 1, freqBits - indexBits)

    pairs(p).io.x_even := io.in_vec(2 * p)
    pairs(p).io.x_odd  := io.in_vec(2 * p + 1)
    pairs(p).io.in_valid := allValid && io.rope_en
    pairs(p).io.angle_idx := angleIdx
    pairs(p).io.stall := io.stall

    pairs(p).io.lut_cos_wr_en := io.lut_cos_wr_en
    pairs(p).io.lut_sin_wr_en := io.lut_sin_wr_en
    pairs(p).io.lut_wr_addr := io.lut_wr_addr
    pairs(p).io.lut_wr_data := io.lut_wr_data

    io.out_vec(2 * p) :=
      Mux(io.rope_en, pairs(p).io.y_even, io.in_vec(2 * p))

    io.out_vec(2 * p + 1) :=
      Mux(io.rope_en, pairs(p).io.y_odd, io.in_vec(2 * p + 1))

    io.out_valid(2 * p) :=
      Mux(io.rope_en, pairs(p).io.out_valid, io.in_valid(2 * p) && run)

    io.out_valid(2 * p + 1) :=
      Mux(io.rope_en, pairs(p).io.out_valid, io.in_valid(2 * p + 1) && run)
  }

  io.lut_ready :=
    VecInit(pairs.map(_.io.lut_ready)).asUInt.andR

  val primed = RegInit(false.B)

  when(io.soft_reset || !io.rope_en) {
    primed := false.B
  }.elsewhen(shadowMValid && shadowFreqValid) {
    primed := true.B
  }

  io.prefetch_ready :=
    !io.rope_en ||
    (primed && io.lut_ready)

  val coreAlert =
    VecInit(pairs.map(_.io.sync_alert)).asUInt.orR

  val laneMismatch =
    anyValid && !allValid

  val rowUpdateMisaligned =
    inputFire &&
    io.row_change_update &&
    !tileStart

  val rowUpdateWithoutPosition =
    positionSwap &&
    !shadowMValid

  val firstRowWithoutPosition =
    inputFire &&
    !positionSwap &&
    !activeMValid

  val freqLineMissing =
    consumeFreqLine &&
    !shadowFreqValid

  val unexpectedFreqResponse =
    io.freq_line_valid &&
    shadowFreqValid &&
    !consumeFreqLine

  val inputBeforePrimed =
    inputFire &&
    !primed

  io.sync_alert :=
    coreAlert ||
    laneMismatch ||
    rowUpdateMisaligned ||
    rowUpdateWithoutPosition ||
    firstRowWithoutPosition ||
    freqLineMissing ||
    unexpectedFreqResponse ||
    inputBeforePrimed
}
