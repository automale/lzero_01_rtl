package npu.core

import chisel3._
import chisel3.util._
import npu.utils.Universal_Wide_LUT

// ============================================================================
// Normalization function
// ============================================================================
object NormMode {
  val BYPASS    = 0.U(2.W)
  val RMSNORM   = 1.U(2.W)
  val LAYERNORM = 2.U(2.W)
  val SOFTMAX   = 3.U(2.W)
}

// ============================================================================
// Input layout
//
// ONLINE:
//   16 lanes are 16 independent normalization contexts.
//   One element per lane is accepted per beat.
//   phase1_last is generated from rope_param_update.
//
// DISTRIBUTED:
//   16 lanes are 16 pieces of ONE logical vector.
//   16 physical elements are accepted per beat.
//   phase1_last is generated from logical_vector_length.
//   Padding lanes in the final physical beat are excluded from Phase-1 stats,
//   but are still processed normally in Phase 2 so the physical 16-lane stream
//   remains aligned.
// ============================================================================
object NormLayoutMode {
  val ONLINE      = 0.U(1.W)
  val DISTRIBUTED = 1.U(1.W)
}

// ============================================================================
// 16 -> 1 signed max tree, 4 accepted-cycle latency.
// ============================================================================
class NormMaxReduce16(
  val inBits: Int
) extends Module {
  val io = IO(new Bundle {
    val in_vec   = Input(Vec(16, SInt(inBits.W)))
    val in_valid = Input(Bool())
    val stall    = Input(Bool())

    val out_max   = Output(SInt(inBits.W))
    val out_valid = Output(Bool())
  })

  val run = !io.stall

  def maxS(a: SInt, b: SInt): SInt =
    Mux(a > b, a, b)

  val s1 = Reg(Vec(8, SInt(inBits.W)))
  val s2 = Reg(Vec(4, SInt(inBits.W)))
  val s3 = Reg(Vec(2, SInt(inBits.W)))
  val s4 = Reg(SInt(inBits.W))

  val v1 = RegInit(false.B)
  val v2 = RegInit(false.B)
  val v3 = RegInit(false.B)
  val v4 = RegInit(false.B)

  when(run) {
    for (i <- 0 until 8) {
      s1(i) := maxS(io.in_vec(2*i), io.in_vec(2*i+1))
    }
    v1 := io.in_valid

    for (i <- 0 until 4) {
      s2(i) := maxS(s1(2*i), s1(2*i+1))
    }
    v2 := v1

    for (i <- 0 until 2) {
      s3(i) := maxS(s2(2*i), s2(2*i+1))
    }
    v3 := v2

    s4 := maxS(s3(0), s3(1))
    v4 := v3
  }

  io.out_max   := s4
  io.out_valid := v4
}

// ============================================================================
// 16 -> 1 unsigned sum tree, 4 accepted-cycle latency.
// ============================================================================
class NormUSumReduce16(
  val accBits: Int
) extends Module {
  val io = IO(new Bundle {
    val in_vec   = Input(Vec(16, UInt(accBits.W)))
    val in_valid = Input(Bool())
    val stall    = Input(Bool())

    val out_sum   = Output(UInt(accBits.W))
    val out_valid = Output(Bool())
  })

  val run = !io.stall

  def addU(a: UInt, b: UInt): UInt = {
    val full = a +& b
    full(accBits - 1, 0)
  }

  val s1 = Reg(Vec(8, UInt(accBits.W)))
  val s2 = Reg(Vec(4, UInt(accBits.W)))
  val s3 = Reg(Vec(2, UInt(accBits.W)))
  val s4 = Reg(UInt(accBits.W))

  val v1 = RegInit(false.B)
  val v2 = RegInit(false.B)
  val v3 = RegInit(false.B)
  val v4 = RegInit(false.B)

  when(run) {
    for (i <- 0 until 8) {
      s1(i) := addU(io.in_vec(2*i), io.in_vec(2*i+1))
    }
    v1 := io.in_valid

    for (i <- 0 until 4) {
      s2(i) := addU(s1(2*i), s1(2*i+1))
    }
    v2 := v1

    for (i <- 0 until 2) {
      s3(i) := addU(s2(2*i), s2(2*i+1))
    }
    v3 := v2

    s4 := addU(s3(0), s3(1))
    v4 := v3
  }

  io.out_sum   := s4
  io.out_valid := v4
}

// ============================================================================
// 16 -> 1 {signed sum, unsigned square-sum} tree, 4 accepted-cycle latency.
// ============================================================================
class NormStatReduce16(
  val accBits: Int
) extends Module {
  val io = IO(new Bundle {
    val in_sum   = Input(Vec(16, SInt(accBits.W)))
    val in_sqsum = Input(Vec(16, UInt(accBits.W)))
    val in_valid = Input(Bool())
    val stall    = Input(Bool())

    val out_sum   = Output(SInt(accBits.W))
    val out_sqsum = Output(UInt(accBits.W))
    val out_valid = Output(Bool())
  })

  val run = !io.stall

  def addS(a: SInt, b: SInt): SInt = {
    val full = (a +& b).asUInt
    full(accBits - 1, 0).asSInt
  }

  def addU(a: UInt, b: UInt): UInt = {
    val full = a +& b
    full(accBits - 1, 0)
  }

  val s1Sum = Reg(Vec(8, SInt(accBits.W)))
  val s1Sq  = Reg(Vec(8, UInt(accBits.W)))
  val s2Sum = Reg(Vec(4, SInt(accBits.W)))
  val s2Sq  = Reg(Vec(4, UInt(accBits.W)))
  val s3Sum = Reg(Vec(2, SInt(accBits.W)))
  val s3Sq  = Reg(Vec(2, UInt(accBits.W)))
  val s4Sum = Reg(SInt(accBits.W))
  val s4Sq  = Reg(UInt(accBits.W))

  val v1 = RegInit(false.B)
  val v2 = RegInit(false.B)
  val v3 = RegInit(false.B)
  val v4 = RegInit(false.B)

  when(run) {
    for (i <- 0 until 8) {
      s1Sum(i) := addS(io.in_sum(2*i), io.in_sum(2*i+1))
      s1Sq(i)  := addU(io.in_sqsum(2*i), io.in_sqsum(2*i+1))
    }
    v1 := io.in_valid

    for (i <- 0 until 4) {
      s2Sum(i) := addS(s1Sum(2*i), s1Sum(2*i+1))
      s2Sq(i)  := addU(s1Sq(2*i), s1Sq(2*i+1))
    }
    v2 := v1

    for (i <- 0 until 2) {
      s3Sum(i) := addS(s2Sum(2*i), s2Sum(2*i+1))
      s3Sq(i)  := addU(s2Sq(2*i), s2Sq(2*i+1))
    }
    v3 := v2

    s4Sum := addS(s3Sum(0), s3Sum(1))
    s4Sq  := addU(s3Sq(0), s3Sq(1))
    v4 := v3
  }

  io.out_sum   := s4Sum
  io.out_sqsum := s4Sq
  io.out_valid := v4
}

// ============================================================================
// 1-bit metadata FIFO backed by SyncReadMem.
//
// One entry is stored per accepted PHYSICAL 16-lane Phase-1 beat.
// Therefore padding does not alter metadata/data correspondence.
// ============================================================================
class NormMetaFifo(
  val depth: Int
) extends Module {
  require(depth > 1)

  private val ptrBits = math.max(1, log2Ceil(depth))
  private val cntBits = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val enq_valid = Input(Bool())
    val enq_bits  = Input(Bool())

    val deq_ready = Input(Bool())
    val deq_valid = Output(Bool())
    val deq_bits  = Output(Bool())

    val clear = Input(Bool())

    val count = Output(UInt(cntBits.W))
    val overflow = Output(Bool())
    val underflow = Output(Bool())
  })

  val mem = SyncReadMem(depth, Bool())

  val wrPtr = RegInit(0.U(ptrBits.W))
  val rdPtr = RegInit(0.U(ptrBits.W))
  val count = RegInit(0.U(cntBits.W))

  val full  = count === depth.U
  val empty = count === 0.U

  val enqFire = io.enq_valid && !full
  val deqFire = io.deq_ready && !empty

  when(enqFire) {
    mem.write(wrPtr, io.enq_bits)
    wrPtr := Mux(wrPtr === (depth - 1).U, 0.U, wrPtr + 1.U)
  }

  val rdData = mem.read(rdPtr, deqFire)

  when(deqFire) {
    rdPtr := Mux(rdPtr === (depth - 1).U, 0.U, rdPtr + 1.U)
  }

  when(enqFire =/= deqFire) {
    when(enqFire) {
      count := count + 1.U
    }.otherwise {
      count := count - 1.U
    }
  }

  when(io.clear) {
    wrPtr := 0.U
    rdPtr := 0.U
    count := 0.U
  }

  io.deq_valid := RegNext(deqFire, false.B)
  io.deq_bits  := rdData

  io.count     := count
  io.overflow  := io.enq_valid && full
  io.underflow := io.deq_ready && empty
}


// ============================================================================
// Complete distributed-Softmax local context.
//
// A distributed vector is not allowed to enter the global-max tree until all
// 16 lane-local {max, expSum} states belong to the SAME logical vector.
// ============================================================================
class DistSoftContext(
  val numLines: Int,
  val accBits: Int,
  val inBits: Int
) extends Bundle {
  val sum =
    Vec(
      numLines,
      UInt(accBits.W)
    )

  val max =
    Vec(
      numLines,
      SInt(inBits.W)
    )
}

// ============================================================================
// Small register FIFO for complete distributed-Softmax contexts.
//
// Why a FIFO is required:
//   the final Exp-LUT response of vector N can overlap Phase1 of vector N+1.
//   Per-lane "ready" bits are therefore insufficient: padded lanes may become
//   ready one cycle earlier than non-padded lanes and can otherwise be mixed
//   with a later vector.
//
// The FIFO only holds completed local statistics, not vector data.
// ============================================================================
class NormSoftContextFifo(
  val numLines: Int,
  val accBits: Int,
  val inBits: Int,
  val depth: Int = 4
) extends Module {

  require(depth > 1)

  private val ptrBits =
    math.max(
      1,
      log2Ceil(depth)
    )

  private val cntBits =
    math.max(
      1,
      log2Ceil(depth + 1)
    )

  val io = IO(new Bundle {
    val enq_valid =
      Input(Bool())

    val enq_bits =
      Input(
        new DistSoftContext(
          numLines,
          accBits,
          inBits
        )
      )

    val deq_ready =
      Input(Bool())

    val deq_valid =
      Output(Bool())

    val deq_bits =
      Output(
        new DistSoftContext(
          numLines,
          accBits,
          inBits
        )
      )

    val clear =
      Input(Bool())

    val overflow =
      Output(Bool())

    val underflow =
      Output(Bool())

    val count =
      Output(UInt(cntBits.W))
  })

  val mem =
    Reg(
      Vec(
        depth,
        new DistSoftContext(
          numLines,
          accBits,
          inBits
        )
      )
    )

  val wrPtr =
    RegInit(
      0.U(ptrBits.W)
    )

  val rdPtr =
    RegInit(
      0.U(ptrBits.W)
    )

  val count =
    RegInit(
      0.U(cntBits.W)
    )

  val full =
    count === depth.U

  val empty =
    count === 0.U

  val enqFire =
    io.enq_valid &&
    !full

  val deqFire =
    io.deq_ready &&
    !empty

  when(enqFire) {
    mem(wrPtr) :=
      io.enq_bits

    wrPtr :=
      Mux(
        wrPtr === (depth - 1).U,
        0.U,
        wrPtr + 1.U
      )
  }

  when(deqFire) {
    rdPtr :=
      Mux(
        rdPtr === (depth - 1).U,
        0.U,
        rdPtr + 1.U
      )
  }

  when(enqFire =/= deqFire) {
    when(enqFire) {
      count :=
        count + 1.U
    }.otherwise {
      count :=
        count - 1.U
    }
  }

  when(io.clear) {
    wrPtr := 0.U
    rdPtr := 0.U
    count := 0.U
  }

  io.deq_valid :=
    !empty

  io.deq_bits :=
    mem(rdPtr)

  io.overflow :=
    io.enq_valid &&
    full

  io.underflow :=
    io.deq_ready &&
    empty

  io.count :=
    count
}


// ============================================================================
// Completed DISTRIBUTED Phase-2 normalization context.
//
// This context is generated when the Scale LUT response returns, but it becomes
// ACTIVE only when the matching NB vector actually starts Phase 2.
//
// This distinction is required under global stall:
//   Scale LUT response is wall-clock based (SyncReadMem, 1 cycle)
//   NB/Phase2 progress is accepted-cycle based (frozen by stall)
// ============================================================================
class NormDistPhase2Context(
  val scaleBits: Int,
  val accBits: Int,
  val inBits: Int
) extends Bundle {

  val scale =
    UInt(scaleBits.W)

  val mean =
    SInt(accBits.W)

  val max =
    SInt(inBits.W)
}

// ============================================================================
// Small flow-through register FIFO for completed distributed Phase-2 contexts.
//
// Flow-through is important for the zero-latency transition:
//   cycle t   : scale LUT response + NB first Phase2 beat can coexist
//   FIFO empty: enqueued context is visible directly on deq_bits that cycle
//
// During stall:
//   scale response may still arrive and is stored,
//   but deq_ready is false because phase2Fire is false.
// ============================================================================
class NormDistPhase2ContextFifo(
  val scaleBits: Int,
  val accBits: Int,
  val inBits: Int,
  val depth: Int = 4
) extends Module {

  require(depth > 1)

  private val ptrBits =
    math.max(
      1,
      log2Ceil(depth)
    )

  private val cntBits =
    math.max(
      1,
      log2Ceil(depth + 1)
    )

  val io = IO(new Bundle {
    val enq_valid =
      Input(Bool())

    val enq_bits =
      Input(
        new NormDistPhase2Context(
          scaleBits,
          accBits,
          inBits
        )
      )

    val deq_ready =
      Input(Bool())

    val deq_valid =
      Output(Bool())

    val deq_bits =
      Output(
        new NormDistPhase2Context(
          scaleBits,
          accBits,
          inBits
        )
      )

    val clear =
      Input(Bool())

    val count =
      Output(UInt(cntBits.W))

    val overflow =
      Output(Bool())

    val underflow =
      Output(Bool())
  })

  val mem =
    Reg(
      Vec(
        depth,
        new NormDistPhase2Context(
          scaleBits,
          accBits,
          inBits
        )
      )
    )

  val wrPtr =
    RegInit(
      0.U(ptrBits.W)
    )

  val rdPtr =
    RegInit(
      0.U(ptrBits.W)
    )

  val count =
    RegInit(
      0.U(cntBits.W)
    )

  val empty =
    count === 0.U

  val full =
    count === depth.U

  // Empty FIFO can forward a just-arriving context directly to Phase2 start.
  io.deq_valid :=
    !empty ||
    io.enq_valid

  io.deq_bits :=
    Mux(
      empty,
      io.enq_bits,
      mem(rdPtr)
    )

  val deqFire =
    io.deq_ready &&
    io.deq_valid

  // If full, a simultaneous dequeue creates the space needed for enqueue.
  val enqAccepted =
    io.enq_valid &&
    (
      !full ||
      deqFire
    )

  // Empty + simultaneous enq/deq is pure flow-through; nothing is stored.
  val flowConsumed =
    empty &&
    io.enq_valid &&
    io.deq_ready

  val storeEnq =
    enqAccepted &&
    !flowConsumed

  val storedDeq =
    deqFire &&
    !empty

  when(storeEnq) {
    mem(wrPtr) :=
      io.enq_bits

    wrPtr :=
      Mux(
        wrPtr === (depth - 1).U,
        0.U,
        wrPtr + 1.U
      )
  }

  when(storedDeq) {
    rdPtr :=
      Mux(
        rdPtr === (depth - 1).U,
        0.U,
        rdPtr + 1.U
      )
  }

  when(storeEnq =/= storedDeq) {
    when(storeEnq) {
      count :=
        count + 1.U
    }.otherwise {
      count :=
        count - 1.U
    }
  }

  when(io.clear) {
    wrPtr := 0.U
    rdPtr := 0.U
    count := 0.U
  }

  io.count :=
    count

  io.overflow :=
    io.enq_valid &&
    full &&
    !deqFire

  io.underflow :=
    io.deq_ready &&
    !io.deq_valid
}

// ============================================================================
// Universal 16-lane Normalizer
//
// Supported functions:
//   BYPASS / RMSNorm / LayerNorm / Softmax
//
// Supported layouts:
//   ONLINE:
//     16 lanes = 16 independent vectors.
//     phase1_last = phase1_rope_param_update_in.
//
//   DISTRIBUTED:
//     16 lanes = one vector split across lanes.
//     phase1_last is generated from logical_vector_length.
//     Last-beat padding lanes are excluded from Phase-1 statistics.
//
// Softmax:
//   Phase-1 streaming recurrence uses the online Milakov/Gimelshein correction:
//
//     m' = max(m, x)
//
//     x > m:
//       d' = d * exp(m - x) + 1
//
//     x <= m:
//       d' = d + exp(x - m)
//
//   DISTRIBUTED mode then integrates 16 local states with:
//
//     M = max(m_i)
//     D = sum_i d_i * exp(m_i - M)
//
// LUT topology:
//   streamExpLut[16] : Phase-1 Milakov recurrence
//   correctionExpLut[16] : distributed local->global-max correction
//   phase2ExpLut   [16] : Phase-2 Softmax
//   scaleLut    [16] : 16 independent online scale lookups
//                     distributed mode uses scaleLut(0) only
//
// Phase-2 lookahead:
//   phase2_req is asserted in the SAME cycle as scale-LUT rd_en.
//   Both Scale LUT and NB are assumed to have 1-cycle read latency.
//
// Fixed-point contracts:
//   inv_vector_length : unsigned Q(invFracBits), compiler-generated 1/N
//   Exp LUT           : unsigned Q(lutFracBits), exp(-delta)
//   Scale LUT RMS/LN  : unsigned Q(lutFracBits), rsqrt(normalized mantissa)
//   Scale LUT Softmax : unsigned Q(lutFracBits), recip(normalized mantissa)
// ============================================================================
class UniversalNormUnit(
  val numLines: Int = 16,
  val writeBits: Int = 256,
  val inBits: Int = 8,
  val outBits: Int = 8,
  val vectorSize: Int = 4096,        // maximum supported logical vector length
  val indexBits: Int = 8,
  val dataBits: Int = 16,
  val lutFracBits: Int = 14,
  val invBits: Int = 24,
  val invFracBits: Int = 20,
  val metadataDepth: Int = 8192
) extends Module {

  require(numLines == 16, "UniversalNormUnit currently requires exactly 16 lanes.")
  require(vectorSize > 0)
  require(indexBits > 0)
  require(writeBits % dataBits == 0)
  require(lutFracBits > 0 && lutFracBits < dataBits)
  require(invFracBits > 0 && invFracBits < invBits)

  private val wordsPerBurst = writeBits / dataBits
  private val burstAddrBits =
    math.max(1, log2Ceil((1 << indexBits) / wordsPerBurst))

  // Enough for signed sum + square sum over maximum logical vector.
  val accBits =
    math.max(32, 2 * inBits + log2Ceil(vectorSize) + 3)

  private val scaleBits =
    dataBits + lutFracBits + 6

  private val scaleExpBits = 8

  private val minInputValue =
    -(BigInt(1) << (inBits - 1))

  private val oneQ =
    BigInt(1) << lutFracBits

  private val invSqrt2Q =
    BigInt(math.round((1.0 / math.sqrt(2.0)) * (1L << lutFracBits)))

  val io = IO(new Bundle {
    // ------------------------------------------------------------------------
    // Phase 1: Compute -> Normalizer -> NB
    // ------------------------------------------------------------------------
    val phase1_in_vec =
      Input(Vec(numLines, UInt(inBits.W)))

    val phase1_valid_vec =
      Input(Vec(numLines, Bool()))

    // Dynamic RoPE metadata from the TPU stream.
    //
    // ONLINE:
    //   also defines the normalization-vector boundary.
    //
    // DISTRIBUTED:
    //   metadata only; vector boundary is generated internally.
    val phase1_rope_param_update_in =
      Input(Bool())

    val phase1_out_vec =
      Output(Vec(numLines, UInt(inBits.W)))

    val phase1_out_valid_vec =
      Output(Vec(numLines, Bool()))

    // ------------------------------------------------------------------------
    // Phase 2: NB -> Normalizer
    // ------------------------------------------------------------------------
    val phase2_in_vec =
      Input(Vec(numLines, UInt(inBits.W)))

    val phase2_valid_vec =
      Input(Vec(numLines, Bool()))

    val phase2_out_vec =
      Output(Vec(numLines, UInt(outBits.W)))

    val phase2_out_valid_vec =
      Output(Vec(numLines, Bool()))

    val phase2_rope_param_update_out =
      Output(Bool())

    // ------------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------------
    val mode_sel =
      Input(UInt(2.W))

    val layout_mode =
      Input(UInt(1.W))

    // Mathematical vector length N, excluding physical padding.
    val logical_vector_length =
      Input(UInt(32.W))

    // Compiler-generated reciprocal 1/N in Q(invFracBits).
    val inv_vector_length =
      Input(UInt(invBits.W))

    // Epsilon in the same integer domain as E[x^2].
    val epsilon =
      Input(UInt(accBits.W))

    val clr_acc =
      Input(Bool())

    val stall =
      Input(Bool())

    // ------------------------------------------------------------------------
    // NB control
    // ------------------------------------------------------------------------
    // One-cycle-early request:
    // asserted together with Scale LUT read request.
    val phase2_req =
      Output(Bool())

    // ------------------------------------------------------------------------
    // LUT programming
    // ------------------------------------------------------------------------
    val lut_wr_en =
      Input(Bool())

    val lut_is_exp =
      Input(Bool())

    val lut_wr_addr =
      Input(UInt(burstAddrBits.W))

    val lut_wr_data =
      Input(Vec(wordsPerBurst, UInt(dataBits.W)))

    // ------------------------------------------------------------------------
    // Status / DFD
    // ------------------------------------------------------------------------
    val lut_ready =
      Output(Bool())

    val sync_alert =
      Output(Bool())

    val phase1_vector_done =
      Output(Bool())

    val metadata_count =
      Output(UInt(log2Ceil(metadataDepth + 1).W))
  })

  // ==========================================================================
  // Common mode / stream state
  // ==========================================================================
  val run          = !io.stall
  val isBypass     = io.mode_sel === NormMode.BYPASS
  val isRms        = io.mode_sel === NormMode.RMSNORM
  val isLayer      = io.mode_sel === NormMode.LAYERNORM
  val isSoftmax    = io.mode_sel === NormMode.SOFTMAX
  val isNorm       = !isBypass

  val isOnline =
    io.layout_mode === NormLayoutMode.ONLINE

  val isDistributed =
    io.layout_mode === NormLayoutMode.DISTRIBUTED

  val p1AnyValid =
    io.phase1_valid_vec.asUInt.orR

  val p1AllValid =
    io.phase1_valid_vec.asUInt.andR

  val p2AnyValid =
    io.phase2_valid_vec.asUInt.orR

  val p2AllValid =
    io.phase2_valid_vec.asUInt.andR

  // Physical contract:
  // padding is still carried as a full 16-lane beat.
  val phase1Fire =
    p1AllValid && run && isNorm

  val phase2Fire =
    p2AllValid && run && isNorm

  val logicalLengthSafe =
    Mux(io.logical_vector_length === 0.U, 1.U, io.logical_vector_length)

  // ==========================================================================
  // Internal phase1_last generation
  // ==========================================================================
  val distributedBeatCounter =
    RegInit(0.U(32.W))

  val onlineElementCounter =
    RegInit(0.U(32.W))

  // ceil(N / 16)
  val distributedBeatsPerVector =
    (logicalLengthSafe + 15.U) >> 4

  val distributedLastBeat =
    phase1Fire &&
    isDistributed &&
    (distributedBeatCounter === (distributedBeatsPerVector - 1.U))

  // ONLINE vector boundary is explicitly tied to RoPE metadata timing.
  val onlineLastBeat =
    phase1Fire &&
    isOnline &&
    io.phase1_rope_param_update_in

  val phase1Last =
    onlineLastBeat || distributedLastBeat

  io.phase1_vector_done :=
    phase1Last && run

  when(io.clr_acc) {
    distributedBeatCounter := 0.U
    onlineElementCounter    := 0.U
  }.elsewhen(phase1Fire) {
    when(isDistributed) {
      when(distributedLastBeat) {
        distributedBeatCounter := 0.U
      }.otherwise {
        distributedBeatCounter := distributedBeatCounter + 1.U
      }
    }

    when(isOnline) {
      when(onlineLastBeat) {
        onlineElementCounter := 0.U
      }.otherwise {
        onlineElementCounter := onlineElementCounter + 1.U
      }
    }
  }

  // --------------------------------------------------------------------------
  // Statistics-valid mask.
  //
  // ONLINE:
  //   every physical lane is one real element of its own vector.
  //
  // DISTRIBUTED:
  //   padding lanes in the final physical beat are NOT allowed to modify
  //   sum/sqsum/max/exp-sum.
  // --------------------------------------------------------------------------
  val remainder =
    io.logical_vector_length(3, 0)

  val validLanesOnLastBeat =
    Mux(remainder === 0.U, 16.U(5.W), remainder.pad(5))

  val statLaneValid =
    Wire(Vec(numLines, Bool()))

  for (i <- 0 until numLines) {
    statLaneValid(i) :=
      Mux(
        isOnline,
        true.B,
        !distributedLastBeat || (i.U < validLanesOnLastBeat)
      )
  }

  // ==========================================================================
  // Phase-1 raw forwarding to NB.
  //
  // Physical padding is forwarded exactly like real data.
  // ==========================================================================
  io.phase1_out_vec :=
    io.phase1_in_vec

  for (i <- 0 until numLines) {
    io.phase1_out_valid_vec(i) :=
      io.phase1_valid_vec(i) &&
      run &&
      isNorm
  }

  // ==========================================================================
  // Metadata FIFO
  //
  // One bit per PHYSICAL beat. The same number of entries is consumed by
  // Phase 2, including padded beats.
  // ==========================================================================
  val metaFifo =
    Module(new NormMetaFifo(metadataDepth))

  metaFifo.io.enq_valid :=
    phase1Fire

  metaFifo.io.enq_bits :=
    io.phase1_rope_param_update_in

  metaFifo.io.deq_ready :=
    phase2Fire

  metaFifo.io.clear :=
    io.clr_acc

  io.metadata_count :=
    metaFifo.io.count

  // ==========================================================================
  // Helper arithmetic
  // ==========================================================================
  def addS(a: SInt, b: SInt): SInt = {
    val full = (a +& b).asUInt
    full(accBits - 1, 0).asSInt
  }

  def addU(a: UInt, b: UInt): UInt = {
    val full = a +& b
    full(accBits - 1, 0)
  }

  def squareToAcc(x: SInt): UInt = {
    val sq = (x * x).asUInt
    val padded = sq.pad(accBits)
    padded(accBits - 1, 0)
  }

  def deltaToIndex(a: SInt, b: SInt): UInt = {
    val diff =
      Mux(a >= b, a - b, b - a)

    val mag =
      diff.asUInt

    val maxIdx =
      ((BigInt(1) << indexBits) - 1).U

    Mux(
      mag > maxIdx,
      maxIdx,
      mag(indexBits - 1, 0)
    )
  }

  def normalizedIndex(x: UInt): (UInt, UInt) = {
    val zero =
      !x.orR

    val lz =
      PriorityEncoder(x.asBools.reverse)

    val exp =
      Mux(
        zero,
        0.U,
        (accBits - 1).U - lz
      )

    val shifted =
      Mux(
        zero,
        0.U(accBits.W),
        (x << lz)(accBits - 1, 0)
      )

    val idx =
      shifted(accBits - 1, accBits - indexBits)

    (idx, exp)
  }

  def meanFromSignedSum(sum: SInt): SInt = {
    val invS =
      io.inv_vector_length.zext.asSInt

    val prod =
      sum * invS

    val shifted =
      (prod >> invFracBits).asSInt

    val bits =
      shifted.asUInt

    bits(accBits - 1, 0).asSInt
  }

  def meanFromUnsignedSum(sum: UInt): UInt = {
    val prod =
      sum * io.inv_vector_length

    val shifted =
      prod >> invFracBits

    val padded =
      shifted.pad(accBits)

    padded(accBits - 1, 0)
  }

  def rmsStatistic(sqSum: UInt): UInt = {
    addU(
      meanFromUnsignedSum(sqSum),
      io.epsilon
    )
  }

  def layerStatistic(
    sum: SInt,
    sqSum: UInt
  ): (SInt, UInt) = {
    val mean =
      meanFromSignedSum(sum)

    val ex2 =
      meanFromUnsignedSum(sqSum)

    val meanSqFull =
      (mean * mean).asUInt

    val meanSqPadded =
      meanSqFull.pad(accBits)

    val meanSq =
      meanSqPadded(accBits - 1, 0)

    val variance =
      Mux(
        ex2 >= meanSq,
        ex2 - meanSq,
        0.U
      )

    (
      mean,
      addU(variance, io.epsilon)
    )
  }

  def satSignedToUInt(x: SInt): UInt = {
    val maxV =
      ((BigInt(1) << (outBits - 1)) - 1).S

    val minV =
      (-(BigInt(1) << (outBits - 1))).S

    val clipped =
      Mux(
        x > maxV,
        maxV,
        Mux(
          x < minV,
          minV,
          x
        )
      )

    val u =
      clipped.asUInt

    u(outBits - 1, 0)
  }

  // ==========================================================================
  // Per-lane RMS/Layer state
  // ==========================================================================
  val sumRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(accBits.W)
        )
      )
    )

  val sqSumRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.U(accBits.W)
        )
      )
    )

  val finalSumVec =
    Wire(Vec(numLines, SInt(accBits.W)))

  val finalSqVec =
    Wire(Vec(numLines, UInt(accBits.W)))

  for (i <- 0 until numLines) {
    val x =
      io.phase1_in_vec(i).asSInt

    val validForStats =
      phase1Fire &&
      statLaneValid(i)

    val sumNext =
      Mux(
        validForStats,
        addS(sumRegs(i), x.pad(accBits)),
        sumRegs(i)
      )

    val sqNext =
      Mux(
        validForStats,
        addU(sqSumRegs(i), squareToAcc(x)),
        sqSumRegs(i)
      )

    finalSumVec(i) := sumNext
    finalSqVec(i)  := sqNext

    when(io.clr_acc) {
      sumRegs(i)   := 0.S
      sqSumRegs(i) := 0.U
    }.elsewhen(phase1Fire && (isRms || isLayer)) {
      when(phase1Last) {
        // Context is complete. Final values are consumed combinationally above.
        sumRegs(i)   := 0.S
        sqSumRegs(i) := 0.U
      }.otherwise {
        sumRegs(i)   := sumNext
        sqSumRegs(i) := sqNext
      }
    }
  }

  // ==========================================================================
  // LUTs
  // ==========================================================================
  val streamExpLuts =
    Seq.fill(numLines)(
      Module(
        new Universal_Wide_LUT(
          indexBits,
          dataBits,
          writeBits
        )
      )
    )

  // Distributed Softmax correction and Phase-2 Softmax must be able to
  // run in the same cycle. Give them independent read ports by using separate
  // LUT banks. All three Exp-LUT banks are programmed from the same write bus.
  val correctionExpLuts =
    Seq.fill(numLines)(
      Module(
        new Universal_Wide_LUT(
          indexBits,
          dataBits,
          writeBits
        )
      )
    )

  val phase2ExpLuts =
    Seq.fill(numLines)(
      Module(
        new Universal_Wide_LUT(
          indexBits,
          dataBits,
          writeBits
        )
      )
    )

  val scaleLuts =
    Seq.fill(numLines)(
      Module(
        new Universal_Wide_LUT(
          indexBits,
          dataBits,
          writeBits
        )
      )
    )

  for (i <- 0 until numLines) {
    val expWr =
      io.lut_wr_en &&
      io.lut_is_exp

    streamExpLuts(i).io.wr_en   := expWr
    streamExpLuts(i).io.wr_addr := io.lut_wr_addr
    streamExpLuts(i).io.wr_data := io.lut_wr_data

    correctionExpLuts(i).io.wr_en   := expWr
    correctionExpLuts(i).io.wr_addr := io.lut_wr_addr
    correctionExpLuts(i).io.wr_data := io.lut_wr_data

    phase2ExpLuts(i).io.wr_en   := expWr
    phase2ExpLuts(i).io.wr_addr := io.lut_wr_addr
    phase2ExpLuts(i).io.wr_data := io.lut_wr_data

    scaleLuts(i).io.wr_en :=
      io.lut_wr_en &&
      !io.lut_is_exp

    scaleLuts(i).io.wr_addr :=
      io.lut_wr_addr

    scaleLuts(i).io.wr_data :=
      io.lut_wr_data
  }

  // ==========================================================================
  // Softmax Phase-1 Milakov state
  //
  // Streaming recurrence is lane-local and II=1.
  //
  // IMPORTANT:
  //   ONLINE and DISTRIBUTED finalization are intentionally different.
  //
  //   ONLINE:
  //     all 16 lane contexts are held in softFinal* until the 16 independent
  //     scale lookups launch.
  //
  //   DISTRIBUTED:
  //     a COMPLETE {sum[16], max[16]} vector context is assembled atomically
  //     and pushed into softContextFifo. This prevents padded lanes of vector
  //     N+1 from being mixed with delayed Exp-LUT responses of vector N.
  // ==========================================================================
  val softSeen =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          false.B
        )
      )
    )

  val softMaxRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          minInputValue.S(inBits.W)
        )
      )
    )

  // Q(lutFracBits)
  val softSumRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.U(accBits.W)
        )
      )
    )

  val softPendingValid =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          false.B
        )
      )
    )

  val softPendingBaseSum =
    Reg(Vec(numLines, UInt(accBits.W)))

  val softPendingNewMax =
    Reg(Vec(numLines, SInt(inBits.W)))

  val softPendingWasNewMax =
    Reg(Vec(numLines, Bool()))

  val softPendingLast =
    Reg(Vec(numLines, Bool()))

  // ONLINE-only final context.
  val softFinalSum =
    Reg(Vec(numLines, UInt(accBits.W)))

  val softFinalMax =
    Reg(Vec(numLines, SInt(inBits.W)))

  val softFinalReady =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          false.B
        )
      )
    )

  val streamExpReq =
    Wire(Vec(numLines, Bool()))

  val streamExpAddr =
    Wire(Vec(numLines, UInt(indexBits.W)))

  val softResolvedSum =
    Wire(Vec(numLines, UInt(accBits.W)))

  val softResolvedMax =
    Wire(Vec(numLines, SInt(inBits.W)))

  val softBaseSum =
    Wire(Vec(numLines, UInt(accBits.W)))

  val softBaseMax =
    Wire(Vec(numLines, SInt(inBits.W)))

  val softBaseSeen =
    Wire(Vec(numLines, Bool()))

  // --------------------------------------------------------------------------
  // Distributed-final staging.
  //
  // For N > 16 the final valid lanes issue one final Exp-LUT request.
  // Padding lanes do not issue a request, so their already-resolved base state
  // is captured here and merged with the one-cycle-later valid-lane responses.
  // --------------------------------------------------------------------------
  val distFinalAwaitingResp =
    RegInit(false.B)

  val distFinalLaneMask =
    Reg(Vec(numLines, Bool()))

  val distFinalBaseSum =
    Reg(Vec(numLines, UInt(accBits.W)))

  val distFinalBaseMax =
    Reg(Vec(numLines, SInt(inBits.W)))

  val distributedSingleBeat =
    distributedBeatsPerVector === 1.U

  // --------------------------------------------------------------------------
  // Complete-context FIFO.
  // --------------------------------------------------------------------------
  val softContextFifo =
    Module(
      new NormSoftContextFifo(
        numLines =
          numLines,
        accBits =
          accBits,
        inBits =
          inBits,
        depth =
          4
      )
    )

  val distContextEnqValid =
    WireDefault(false.B)

  val distContextEnqBits =
    Wire(
      new DistSoftContext(
        numLines,
        accBits,
        inBits
      )
    )

  for (i <- 0 until numLines) {
    distContextEnqBits.sum(i) :=
      0.U

    distContextEnqBits.max(i) :=
      minInputValue.S(inBits.W)
  }

  // ==========================================================================
  // Per-lane streaming recurrence
  // ==========================================================================
  for (i <- 0 until numLines) {
    streamExpReq(i)  := false.B
    streamExpAddr(i) := 0.U

    val expRespValid =
      streamExpLuts(i).io.rd_valid &&
      softPendingValid(i)

    val expFactorWide =
      streamExpLuts(i).io.rd_data.pad(accBits)

    val scaledOldWide =
      softPendingBaseSum(i) *
      expFactorWide

    val scaledOldShifted =
      scaledOldWide >>
      lutFracBits

    val scaledOld =
      scaledOldShifted(
        accBits - 1,
        0
      )

    softResolvedSum(i) :=
      Mux(
        softPendingWasNewMax(i),
        addU(
          scaledOld,
          oneQ.U(accBits.W)
        ),
        addU(
          softPendingBaseSum(i),
          expFactorWide
        )
      )

    softResolvedMax(i) :=
      softPendingNewMax(i)

    // A final response belongs to the vector that just ended and must never
    // feed the first sample of the next vector.
    val responseFeedsCurrent =
      expRespValid &&
      !softPendingLast(i)

    softBaseSeen(i) :=
      Mux(
        responseFeedsCurrent,
        true.B,
        softSeen(i)
      )

    softBaseSum(i) :=
      Mux(
        responseFeedsCurrent,
        softResolvedSum(i),
        softSumRegs(i)
      )

    softBaseMax(i) :=
      Mux(
        responseFeedsCurrent,
        softResolvedMax(i),
        softMaxRegs(i)
      )

    // ------------------------------------------------------------------------
    // Commit the previous non-final response to the running state.
    //
    // Final DISTRIBUTED responses are consumed atomically by the context FIFO
    // logic below rather than written into shared softFinal* registers.
    // ------------------------------------------------------------------------
    when(expRespValid) {
      when(softPendingLast(i)) {
        when(isOnline) {
          softFinalSum(i) :=
            softResolvedSum(i)

          softFinalMax(i) :=
            softResolvedMax(i)

          softFinalReady(i) :=
            true.B
        }
      }.otherwise {
        softSumRegs(i) :=
          softResolvedSum(i)

        softMaxRegs(i) :=
          softResolvedMax(i)

        softSeen(i) :=
          true.B
      }
    }

    // ------------------------------------------------------------------------
    // Current accepted sample.
    // ------------------------------------------------------------------------
    val takeSample =
      phase1Fire &&
      isSoftmax &&
      statLaneValid(i)

    val x =
      io.phase1_in_vec(i).asSInt

    val isNewMax =
      x >
      softBaseMax(i)

    when(takeSample) {
      when(!softBaseSeen(i)) {
        // First valid element: exp(0)=1.
        when(phase1Last) {
          // ONLINE N=1: final context can be completed immediately.
          when(isOnline) {
            softFinalSum(i) :=
              oneQ.U(accBits.W)

            softFinalMax(i) :=
              x

            softFinalReady(i) :=
              true.B
          }

          softSeen(i) :=
            false.B

          softSumRegs(i) :=
            0.U

          softMaxRegs(i) :=
            minInputValue.S(inBits.W)
        }.otherwise {
          softSeen(i) :=
            true.B

          softSumRegs(i) :=
            oneQ.U(accBits.W)

          softMaxRegs(i) :=
            x
        }
      }.otherwise {
        // Milakov/Gimelshein update request.
        streamExpReq(i) :=
          true.B

        streamExpAddr(i) :=
          deltaToIndex(
            x,
            softBaseMax(i)
          )

        softPendingBaseSum(i) :=
          softBaseSum(i)

        softPendingNewMax(i) :=
          Mux(
            isNewMax,
            x,
            softBaseMax(i)
          )

        softPendingWasNewMax(i) :=
          isNewMax

        softPendingLast(i) :=
          phase1Last

        when(phase1Last) {
          // Release the running bank immediately; the accepted final request
          // carries every value needed to finish the old vector.
          softSeen(i) :=
            false.B

          softSumRegs(i) :=
            0.U

          softMaxRegs(i) :=
            minInputValue.S(inBits.W)
        }
      }
    }

    // Padding lane on the distributed final physical beat:
    // no statistic update, but release the running state for the next vector.
    when(
      phase1Fire &&
      isSoftmax &&
      isDistributed &&
      phase1Last &&
      !statLaneValid(i)
    ) {
      softSeen(i) :=
        false.B

      softSumRegs(i) :=
        0.U

      softMaxRegs(i) :=
        minInputValue.S(inBits.W)
    }

    when(io.clr_acc) {
      softSeen(i) :=
        false.B

      softSumRegs(i) :=
        0.U

      softMaxRegs(i) :=
        minInputValue.S(inBits.W)

      softFinalReady(i) :=
        false.B
    }
  }

  // LUT request/response pipeline state.
  for (i <- 0 until numLines) {
    streamExpLuts(i).io.rd_en :=
      streamExpReq(i)

    streamExpLuts(i).io.rd_addr :=
      streamExpAddr(i)

    softPendingValid(i) :=
      streamExpReq(i)
  }

  // ==========================================================================
  // DISTRIBUTED complete-context assembly
  // ==========================================================================

  // --------------------------------------------------------------------------
  // Single physical beat (N <= 16):
  // no lane needs a final recurrence response. Build the context immediately.
  // Invalid padding lanes contribute sum=0 and max=-INF.
  // --------------------------------------------------------------------------
  val distDirectFinal =
    phase1Fire &&
    isSoftmax &&
    isDistributed &&
    phase1Last &&
    distributedSingleBeat

  when(distDirectFinal) {
    distContextEnqValid :=
      true.B

    for (i <- 0 until numLines) {
      distContextEnqBits.sum(i) :=
        Mux(
          statLaneValid(i),
          oneQ.U(accBits.W),
          0.U
        )

      distContextEnqBits.max(i) :=
        Mux(
          statLaneValid(i),
          io.phase1_in_vec(i).asSInt,
          minInputValue.S(inBits.W)
        )
    }
  }

  // --------------------------------------------------------------------------
  // Multi-beat distributed final beat.
  //
  // All valid final lanes issue the one-cycle Exp request above.
  // Capture padding-lane base state now; valid-lane final values arrive on the
  // following Exp-LUT response.
  // --------------------------------------------------------------------------
  val distMultiBeatFinal =
    phase1Fire &&
    isSoftmax &&
    isDistributed &&
    phase1Last &&
    !distributedSingleBeat

  when(distMultiBeatFinal) {
    distFinalAwaitingResp :=
      true.B

    for (i <- 0 until numLines) {
      distFinalLaneMask(i) :=
        statLaneValid(i)

      distFinalBaseSum(i) :=
        softBaseSum(i)

      distFinalBaseMax(i) :=
        softBaseMax(i)
    }
  }

  val distFinalRespReadyPerLane =
    Wire(Vec(numLines, Bool()))

  for (i <- 0 until numLines) {
    distFinalRespReadyPerLane(i) :=
      !distFinalLaneMask(i) ||
      (
        streamExpLuts(i).io.rd_valid &&
        softPendingValid(i) &&
        softPendingLast(i)
      )
  }

  val distFinalRespReady =
    distFinalAwaitingResp &&
    distFinalRespReadyPerLane
      .asUInt
      .andR

  when(distFinalRespReady) {
    distContextEnqValid :=
      true.B

    for (i <- 0 until numLines) {
      distContextEnqBits.sum(i) :=
        Mux(
          distFinalLaneMask(i),
          softResolvedSum(i),
          distFinalBaseSum(i)
        )

      distContextEnqBits.max(i) :=
        Mux(
          distFinalLaneMask(i),
          softResolvedMax(i),
          distFinalBaseMax(i)
        )
    }

    distFinalAwaitingResp :=
      false.B
  }

  when(io.clr_acc) {
    distFinalAwaitingResp :=
      false.B
  }

  softContextFifo.io.enq_valid :=
    distContextEnqValid

  softContextFifo.io.enq_bits :=
    distContextEnqBits

  softContextFifo.io.clear :=
    io.clr_acc

  // ONLINE final context stays in the existing 16-lane final registers.
  val softFinalGroupReady =
    softFinalReady.asUInt.andR

  val launchOnlineSoftFinal =
    softFinalGroupReady &&
    run &&
    isSoftmax &&
    isOnline

  when(launchOnlineSoftFinal) {
    for (i <- 0 until numLines) {
      softFinalReady(i) :=
        false.B
    }
  }

  // ==========================================================================
  // Distributed normal-stat integration
  // ==========================================================================
  val statTree =
    Module(
      new NormStatReduce16(
        accBits
      )
    )

  val launchDistributedNormal =
    phase1Fire &&
    phase1Last &&
    isDistributed &&
    (isRms || isLayer)

  statTree.io.in_sum :=
    finalSumVec

  statTree.io.in_sqsum :=
    finalSqVec

  statTree.io.in_valid :=
    launchDistributedNormal

  statTree.io.stall :=
    io.stall

  // ==========================================================================
  // Distributed Softmax integration
  //
  // IMPORTANT CONTEXT RULE:
  //   softMaxTree is a 4-stage pipelined reduction tree, so local {max,sum}
  //   context must travel beside it. softFinal* registers may already be reused
  //   by the next vector before the global-max result appears.
  //
  // local (m_i,d_i)
  //   -> global max tree + 4-stage context delay
  //   -> d_i * exp(m_i - M) using correctionExpLut
  //   -> global sum tree + global-max context delay
  //   -> reciprocal Scale LUT
  // ==========================================================================
  val softMaxTree =
    Module(
      new NormMaxReduce16(
        inBits
      )
    )

  val launchDistributedSoftContext =
    softContextFifo.io.deq_valid &&
    run &&
    isSoftmax &&
    isDistributed

  softContextFifo.io.deq_ready :=
    launchDistributedSoftContext

  softMaxTree.io.in_vec :=
    softContextFifo.io.deq_bits.max

  softMaxTree.io.in_valid :=
    launchDistributedSoftContext

  softMaxTree.io.stall :=
    io.stall

  // --------------------------------------------------------------------------
  // Delay each lane's COMPLETE local state exactly beside the 4-stage
  // global-max tree.
  // --------------------------------------------------------------------------
  val distSoftSumPipe1 = Reg(Vec(numLines, UInt(accBits.W)))
  val distSoftSumPipe2 = Reg(Vec(numLines, UInt(accBits.W)))
  val distSoftSumPipe3 = Reg(Vec(numLines, UInt(accBits.W)))
  val distSoftSumPipe4 = Reg(Vec(numLines, UInt(accBits.W)))

  val distSoftMaxPipe1 = Reg(Vec(numLines, SInt(inBits.W)))
  val distSoftMaxPipe2 = Reg(Vec(numLines, SInt(inBits.W)))
  val distSoftMaxPipe3 = Reg(Vec(numLines, SInt(inBits.W)))
  val distSoftMaxPipe4 = Reg(Vec(numLines, SInt(inBits.W)))

  when(run) {
    when(launchDistributedSoftContext) {
      distSoftSumPipe1 :=
        softContextFifo.io.deq_bits.sum

      distSoftMaxPipe1 :=
        softContextFifo.io.deq_bits.max
    }

    distSoftSumPipe2 := distSoftSumPipe1
    distSoftSumPipe3 := distSoftSumPipe2
    distSoftSumPipe4 := distSoftSumPipe3

    distSoftMaxPipe2 := distSoftMaxPipe1
    distSoftMaxPipe3 := distSoftMaxPipe2
    distSoftMaxPipe4 := distSoftMaxPipe3
  }

  // Active global max belongs to the same Phase-2 context as globalScaleReg.
  val globalSoftMaxReg =
    RegInit(
      minInputValue.S(inBits.W)
    )

  // Max for a vector whose correction/reduction is still in flight.
  val correctionGlobalMaxReg =
    RegInit(
      minInputValue.S(inBits.W)
    )

  val correctionReq =
    softMaxTree.io.out_valid &&
    run &&
    isDistributed &&
    isSoftmax

  when(correctionReq) {
    correctionGlobalMaxReg :=
      softMaxTree.io.out_max
  }

  // Local sums have to survive the correction-LUT read latency as well.
  val correctionBaseSum =
    Reg(Vec(numLines, UInt(accBits.W)))

  when(correctionReq) {
    correctionBaseSum :=
      distSoftSumPipe4
  }

  val correctionRespValid =
    RegNext(
      correctionReq,
      false.B
    )

  val correctedLocalSums =
    Reg(Vec(numLines, UInt(accBits.W)))

  for (i <- 0 until numLines) {
    when(correctionRespValid) {
      val factorWide =
        correctionExpLuts(i).io.rd_data.pad(accBits)

      val product =
        correctionBaseSum(i) *
        factorWide

      val shifted =
        product >> lutFracBits

      correctedLocalSums(i) :=
        shifted(accBits - 1, 0)
    }
  }

  val correctionHoldValid =
    RegInit(false.B)

  val correctedGlobalMaxHold =
    RegInit(
      minInputValue.S(inBits.W)
    )

  when(correctionRespValid) {
    correctionHoldValid := true.B

    // correctionGlobalMaxReg still holds the vector whose LUT response is
    // arriving in this cycle. A back-to-back next request updates it only at
    // the edge, so this capture remains correctly aligned.
    correctedGlobalMaxHold :=
      correctionGlobalMaxReg
  }

  val correctedSumTree =
    Module(
      new NormUSumReduce16(
        accBits
      )
    )

  correctedSumTree.io.in_vec :=
    correctedLocalSums

  correctedSumTree.io.in_valid :=
    correctionHoldValid &&
    run

  correctedSumTree.io.stall :=
    io.stall

  // Global max must also follow the corrected-sum tree's 4 stages.
  val correctedMaxPipe1 = Reg(SInt(inBits.W))
  val correctedMaxPipe2 = Reg(SInt(inBits.W))
  val correctedMaxPipe3 = Reg(SInt(inBits.W))
  val correctedMaxPipe4 = Reg(SInt(inBits.W))

  when(run) {
    when(correctionHoldValid) {
      correctedMaxPipe1 :=
        correctedGlobalMaxHold
    }

    correctedMaxPipe2 := correctedMaxPipe1
    correctedMaxPipe3 := correctedMaxPipe2
    correctedMaxPipe4 := correctedMaxPipe3
  }

  when(correctionHoldValid && run) {
    correctionHoldValid := false.B
  }

  // ==========================================================================
  // Scale lookup generation
  // ==========================================================================
  val onlineNormalScaleLookup =
    phase1Fire &&
    phase1Last &&
    isOnline &&
    (isRms || isLayer)

  val distributedNormalScaleLookup =
    statTree.io.out_valid &&
    run &&
    isDistributed &&
    (isRms || isLayer)

  val onlineSoftScaleLookup =
    launchOnlineSoftFinal

  val distributedSoftScaleLookup =
    correctedSumTree.io.out_valid &&
    run &&
    isDistributed &&
    isSoftmax

  val scaleLookupFire =
    onlineNormalScaleLookup ||
    distributedNormalScaleLookup ||
    onlineSoftScaleLookup ||
    distributedSoftScaleLookup

  // EXACTLY one cycle before NB first-data availability.
  io.phase2_req :=
    scaleLookupFire &&
    run

  val scaleRdEn =
    Wire(Vec(numLines, Bool()))

  val scaleRdAddr =
    Wire(Vec(numLines, UInt(indexBits.W)))

  val scaleExpCapture =
    Wire(Vec(numLines, SInt(scaleExpBits.W)))

  val meanCapture =
    Wire(Vec(numLines, SInt(accBits.W)))

  val maxCapture =
    Wire(Vec(numLines, SInt(inBits.W)))

  for (i <- 0 until numLines) {
    scaleRdEn(i)       := false.B
    scaleRdAddr(i)     := 0.U
    scaleExpCapture(i) := 0.S
    meanCapture(i)     := 0.S
    maxCapture(i)      := minInputValue.S(inBits.W)
  }

  // --------------------------------------------------------------------------
  // ONLINE RMS / Layer
  // 16 lanes -> 16 independent Scale-LUT indices.
  // --------------------------------------------------------------------------
  when(onlineNormalScaleLookup) {
    for (i <- 0 until numLines) {
      val layerPair =
        layerStatistic(
          finalSumVec(i),
          finalSqVec(i)
        )

      val mean =
        layerPair._1

      val layerStat =
        layerPair._2

      val stat =
        Mux(
          isLayer,
          layerStat,
          rmsStatistic(finalSqVec(i))
        )

      val normPair =
        normalizedIndex(stat)

      scaleRdEn(i)   := true.B
      scaleRdAddr(i) := normPair._1

      scaleExpCapture(i) :=
        normPair._2.zext.asSInt

      meanCapture(i) :=
        Mux(
          isLayer,
          mean,
          0.S
        )
    }
  }

  // --------------------------------------------------------------------------
  // DISTRIBUTED RMS / Layer
  // 16 local partials -> global stat -> Scale LUT 0.
  // --------------------------------------------------------------------------
  when(distributedNormalScaleLookup) {
    val layerPair =
      layerStatistic(
        statTree.io.out_sum,
        statTree.io.out_sqsum
      )

    val mean =
      layerPair._1

    val layerStat =
      layerPair._2

    val stat =
      Mux(
        isLayer,
        layerStat,
        rmsStatistic(statTree.io.out_sqsum)
      )

    val normPair =
      normalizedIndex(stat)

    scaleRdEn(0)   := true.B
    scaleRdAddr(0) := normPair._1

    scaleExpCapture(0) :=
      normPair._2.zext.asSInt

    meanCapture(0) :=
      Mux(
        isLayer,
        mean,
        0.S
      )
  }

  // --------------------------------------------------------------------------
  // ONLINE Softmax
  // 16 independent reciprocal lookups.
  // --------------------------------------------------------------------------
  when(onlineSoftScaleLookup) {
    for (i <- 0 until numLines) {
      val normPair =
        normalizedIndex(
          softFinalSum(i)
        )

      scaleRdEn(i)   := true.B
      scaleRdAddr(i) := normPair._1

      // exp-sum is Q(lutFracBits), so real exponent is integer-MSB - fracBits.
      scaleExpCapture(i) :=
        normPair._2.zext.asSInt -
        lutFracBits.S(scaleExpBits.W)

      maxCapture(i) :=
        softFinalMax(i)
    }
  }

  // --------------------------------------------------------------------------
  // DISTRIBUTED Softmax
  // corrected global exp-sum -> Scale LUT 0.
  // --------------------------------------------------------------------------
  when(distributedSoftScaleLookup) {
    val normPair =
      normalizedIndex(
        correctedSumTree.io.out_sum
      )

    scaleRdEn(0)   := true.B
    scaleRdAddr(0) := normPair._1

    scaleExpCapture(0) :=
      normPair._2.zext.asSInt -
      lutFracBits.S(scaleExpBits.W)
  }

  // ==========================================================================
  // Scale context
  // ==========================================================================
  val onlineScaleRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.U(scaleBits.W)
        )
      )
    )

  val onlineScaleExpRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(scaleExpBits.W)
        )
      )
    )

  // Active LayerNorm mean context.
  // It is switched atomically with the Scale LUT result, not at lookup request.
  // This prevents Phase1(row N+1) from overwriting the mean still needed by
  // the final Phase2 beat of row N.
  val onlineMeanRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(accBits.W)
        )
      )
    )

  // Mean calculated at phase1 completion waits here while Scale LUT is in flight.
  val onlinePendingMeanRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(accBits.W)
        )
      )
    )

  val onlineMaxRegs =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          minInputValue.S(inBits.W)
        )
      )
    )

  // Distributed Softmax max context is switched atomically with its scale.
  val globalPendingSoftMaxReg =
    RegInit(
      minInputValue.S(inBits.W)
    )

  val globalScaleReg =
    RegInit(
      0.U(scaleBits.W)
    )

  val globalScaleExpReg =
    RegInit(
      0.S(scaleExpBits.W)
    )

  val globalMeanReg =
    RegInit(
      0.S(accBits.W)
    )

  val globalPendingMeanReg =
    RegInit(
      0.S(accBits.W)
    )

  // Completed distributed contexts wait here until the matching NB vector
  // actually starts Phase2. Scale/mean/max therefore cannot jump ahead of data
  // when a global stall delays the NB stream.
  val distPhase2ContextFifo =
    Module(
      new NormDistPhase2ContextFifo(
        scaleBits =
          scaleBits,
        accBits =
          accBits,
        inBits =
          inBits,
        depth =
          4
      )
    )

  distPhase2ContextFifo.io.clear :=
    io.clr_acc

  when(onlineNormalScaleLookup || onlineSoftScaleLookup) {
    for (i <- 0 until numLines) {
      onlineScaleExpRegs(i) :=
        scaleExpCapture(i)

      // Do NOT switch the active mean here.
      // The previous row may still have one Phase2 beat in flight.
      onlinePendingMeanRegs(i) :=
        meanCapture(i)

      when(isSoftmax) {
        onlineMaxRegs(i) :=
          maxCapture(i)
      }
    }
  }

  when(distributedNormalScaleLookup || distributedSoftScaleLookup) {
    globalScaleExpReg :=
      scaleExpCapture(0)

    globalPendingMeanReg :=
      meanCapture(0)

    when(distributedSoftScaleLookup) {
      globalPendingSoftMaxReg :=
        correctedMaxPipe4
    }
  }

  def adjustRsqrtScale(
    raw: UInt,
    exp: SInt
  ): UInt = {
    val rawWide =
      raw.pad(scaleBits)

    val expUnsigned =
      exp.asUInt

    val halfExp =
      expUnsigned >> 1

    val oddExp =
      expUnsigned(0)

    val oddProduct =
      rawWide *
      invSqrt2Q.U(scaleBits.W)

    val oddShifted =
      oddProduct >> lutFracBits

    val oddCorrected =
      Mux(
        oddExp,
        oddShifted(scaleBits - 1, 0),
        rawWide
      )

    val finalShift =
      oddCorrected >> halfExp

    finalShift(scaleBits - 1, 0)
  }

  def adjustRecipScale(
    raw: UInt,
    exp: SInt
  ): UInt = {
    val rawWide =
      raw.pad(scaleBits)

    val negative =
      exp(scaleExpBits - 1)

    val magnitude =
      Mux(
        negative,
        (-exp).asUInt,
        exp.asUInt
      )

    val shifted =
      Mux(
        negative,
        (rawWide << magnitude)(scaleBits - 1, 0),
        rawWide >> magnitude
      )

    shifted(scaleBits - 1, 0)
  }

  for (i <- 0 until numLines) {
    scaleLuts(i).io.rd_en :=
      scaleRdEn(i)

    scaleLuts(i).io.rd_addr :=
      scaleRdAddr(i)

    // ONLINE keeps the existing direct activation behavior.
    //
    // DISTRIBUTED activation is handled by distPhase2ContextFifo below:
    // the Scale-LUT response creates a COMPLETED context, but that context
    // becomes active only at the matching Phase2 vector start.
    when(
      scaleLuts(i).io.rd_valid &&
      isOnline
    ) {
      onlineScaleRegs(i) :=
        Mux(
          isSoftmax,
          adjustRecipScale(
            scaleLuts(i).io.rd_data,
            onlineScaleExpRegs(i)
          ),
          adjustRsqrtScale(
            scaleLuts(i).io.rd_data,
            onlineScaleExpRegs(i)
          )
        )

      onlineMeanRegs(i) :=
        onlinePendingMeanRegs(i)
    }
  }

  // ==========================================================================
  // Scale-LUT response / Phase2-context handoff
  // ==========================================================================
  val scaleContextValid =
    RegInit(false.B)

  val onlineScaleArriving =
    VecInit(
      scaleLuts.map(
        _.io.rd_valid
      )
    ).asUInt.andR

  val distributedScaleArriving =
    scaleLuts(0).io.rd_valid &&
    isDistributed

  val scaleArriving =
    Mux(
      isDistributed,
      distributedScaleArriving,
      onlineScaleArriving
    )

  // ONLINE-only legacy active-context flag.
  when(
    scaleLookupFire &&
    isOnline
  ) {
    scaleContextValid := false.B
  }

  when(
    onlineScaleArriving &&
    isOnline
  ) {
    scaleContextValid := true.B
  }

  // --------------------------------------------------------------------------
  // A distributed context is COMPLETE at Scale-LUT response time.
  // Store it even when io.stall is high: the request was accepted earlier and
  // SyncReadMem cannot be frozen retroactively.
  // --------------------------------------------------------------------------
  val distributedCompletedScale =
    Mux(
      isSoftmax,
      adjustRecipScale(
        scaleLuts(0).io.rd_data,
        globalScaleExpReg
      ),
      adjustRsqrtScale(
        scaleLuts(0).io.rd_data,
        globalScaleExpReg
      )
    )

  distPhase2ContextFifo.io.enq_valid :=
    distributedScaleArriving

  distPhase2ContextFifo.io.enq_bits.scale :=
    distributedCompletedScale

  distPhase2ContextFifo.io.enq_bits.mean :=
    globalPendingMeanReg

  distPhase2ContextFifo.io.enq_bits.max :=
    globalPendingSoftMaxReg

  // --------------------------------------------------------------------------
  // Phase2 vector boundary is self-generated from physical accepted beats.
  //
  // DISTRIBUTED:
  //   beats/vector = ceil(logical_vector_length / 16)
  //
  // The counter freezes automatically because phase2Fire is run-gated.
  // --------------------------------------------------------------------------
  val distributedPhase2BeatCounter =
    RegInit(
      0.U(32.W)
    )

  val distributedPhase2VectorStart =
    phase2Fire &&
    isDistributed &&
    (
      distributedPhase2BeatCounter ===
      0.U
    )

  val distributedPhase2VectorLast =
    phase2Fire &&
    isDistributed &&
    (
      distributedPhase2BeatCounter ===
      (distributedBeatsPerVector - 1.U)
    )

  when(io.clr_acc) {
    distributedPhase2BeatCounter :=
      0.U
  }.elsewhen(
    phase2Fire &&
    isDistributed
  ) {
    when(distributedPhase2VectorLast) {
      distributedPhase2BeatCounter :=
        0.U
    }.otherwise {
      distributedPhase2BeatCounter :=
        distributedPhase2BeatCounter +
        1.U
    }
  }

  // Consume exactly one completed normalization context at each accepted
  // distributed Phase2 vector start.
  distPhase2ContextFifo.io.deq_ready :=
    distributedPhase2VectorStart

  val distributedStartContextValid =
    distributedPhase2VectorStart &&
    distPhase2ContextFifo.io.deq_valid

  val distributedStartScale =
    distPhase2ContextFifo.io.deq_bits.scale

  val distributedStartMean =
    distPhase2ContextFifo.io.deq_bits.mean

  val distributedStartMax =
    distPhase2ContextFifo.io.deq_bits.max

  when(distributedStartContextValid) {
    globalScaleReg :=
      distributedStartScale

    globalMeanReg :=
      distributedStartMean

    globalSoftMaxReg :=
      distributedStartMax
  }

  // ==========================================================================
  // Softmax Exp-LUT read ports
  //
  // correctionExpLut : distributed local->global-max correction
  // phase2ExpLut     : final Phase-2 exp(x-globalMax)
  //
  // They are physically separate so Phase1 finalization of vector N+1 can
  // overlap Phase2 of vector N without losing either lookup.
  // ==========================================================================
  val phase2SoftReq =
    phase2Fire &&
    isSoftmax

  for (i <- 0 until numLines) {
    val correctionAddr =
      deltaToIndex(
        softMaxTree.io.out_max,
        distSoftMaxPipe4(i)
      )

    correctionExpLuts(i).io.rd_en :=
      correctionReq

    correctionExpLuts(i).io.rd_addr :=
      correctionAddr

    // First beat uses the context being consumed from the Phase2-context FIFO.
    // This also covers the zero-latency flow-through case where Scale-LUT
    // response and NB first beat arrive in the same cycle.
    val distributedPhase2Max =
      Mux(
        distributedStartContextValid &&
        isSoftmax,
        distributedStartMax,
        globalSoftMaxReg
      )

    val phase2Max =
      Mux(
        isDistributed,
        distributedPhase2Max,
        onlineMaxRegs(i)
      )

    val phase2Addr =
      deltaToIndex(
        phase2Max,
        io.phase2_in_vec(i).asSInt
      )

    phase2ExpLuts(i).io.rd_en :=
      phase2SoftReq

    phase2ExpLuts(i).io.rd_addr :=
      phase2Addr
  }

  // ==========================================================================
  // Phase-2 application
  //
  // IMPORTANT:
  //   No logical padding mask is applied here.
  //
  // The full physical 16-lane beat, including padded elements, is processed and
  // emitted so downstream physical alignment is preserved exactly as requested.
  // ==========================================================================
  val p2DataD1 =
    Reg(Vec(numLines, UInt(inBits.W)))

  val p2ValidD1 =
    RegNext(
      phase2Fire,
      false.B
    )

  when(phase2Fire) {
    p2DataD1 :=
      io.phase2_in_vec
  }

  val phase2SoftResp =
    RegNext(
      phase2SoftReq,
      false.B
    )

  val rawOutVec =
    Wire(Vec(numLines, UInt(outBits.W)))

  val rawOutValid =
    WireDefault(false.B)

  val rawOutMeta =
    WireDefault(false.B)

  for (i <- 0 until numLines) {
    rawOutVec(i) := 0.U
  }

  // --------------------------------------------------------------------------
  // RMSNorm / LayerNorm
  // --------------------------------------------------------------------------
  when(
    p2ValidD1 &&
    (isRms || isLayer)
  ) {
    for (i <- 0 until numLines) {
      val scale =
        Mux(
          isDistributed,
          globalScaleReg,
          onlineScaleRegs(i)
        )

      // Active mean is guaranteed to belong to the same row as active scale.
      // The next row's mean remains in the pending register until its Scale LUT
      // response arrives.
      val mean =
        Mux(
          isDistributed,
          globalMeanReg,
          onlineMeanRegs(i)
        )

      val x =
        p2DataD1(i).asSInt.pad(accBits)

      val centered =
        Mux(
          isLayer,
          x - mean,
          x
        )

      val product =
        centered *
        scale.zext.asSInt

      val scaled =
        (product >> lutFracBits).asSInt

      rawOutVec(i) :=
        satSignedToUInt(
          scaled
        )
    }

    rawOutValid := true.B

    rawOutMeta :=
      metaFifo.io.deq_valid &&
      metaFifo.io.deq_bits
  }

  // --------------------------------------------------------------------------
  // Softmax
  //
  // Probability is emitted as unsigned Q0.outBits.
  // Padding elements are intentionally processed as physical elements here;
  // they were excluded only from Phase-1 denominator/max statistics.
  // --------------------------------------------------------------------------
  val softmaxOutputShift =
    math.max(
      0,
      2 * lutFracBits - outBits
    )

  when(
    phase2SoftResp &&
    isSoftmax
  ) {
    for (i <- 0 until numLines) {
      val scale =
        Mux(
          isDistributed,
          globalScaleReg,
          onlineScaleRegs(i)
        )

      val product =
        phase2ExpLuts(i).io.rd_data *
        scale

      val shifted =
        product >> softmaxOutputShift

      val maxOut =
        ((BigInt(1) << outBits) - 1).U

      rawOutVec(i) :=
        Mux(
          shifted > maxOut,
          maxOut,
          shifted(outBits - 1, 0)
        )
    }

    rawOutValid := true.B

    rawOutMeta :=
      metaFifo.io.deq_valid &&
      metaFifo.io.deq_bits
  }

  // BYPASS is not expected to visit NB, but keep the interface defined.
  when(
    isBypass &&
    p2AllValid &&
    run
  ) {
    for (i <- 0 until numLines) {
      val inU =
        io.phase2_in_vec(i)

      rawOutVec(i) :=
        inU(
          math.min(inBits, outBits) - 1,
          0
        )
    }

    rawOutValid := true.B
    rawOutMeta  := false.B
  }

  // ==========================================================================
  // Output hold for a stall immediately after an already accepted Phase-2 beat.
  // ==========================================================================
  val outHoldValid =
    RegInit(false.B)

  val outHoldVec =
    Reg(Vec(numLines, UInt(outBits.W)))

  val outHoldMeta =
    RegInit(false.B)

  when(
    io.stall &&
    rawOutValid &&
    !outHoldValid
  ) {
    outHoldVec   := rawOutVec
    outHoldMeta  := rawOutMeta
    outHoldValid := true.B
  }.elsewhen(
    !io.stall &&
    outHoldValid
  ) {
    outHoldValid := false.B
  }

  val visibleOutValid =
    !io.stall &&
    (outHoldValid || rawOutValid)

  for (i <- 0 until numLines) {
    io.phase2_out_vec(i) :=
      Mux(
        outHoldValid,
        outHoldVec(i),
        rawOutVec(i)
      )

    io.phase2_out_valid_vec(i) :=
      visibleOutValid
  }

  io.phase2_rope_param_update_out :=
    visibleOutValid &&
    Mux(
      outHoldValid,
      outHoldMeta,
      rawOutMeta
    )

  // ==========================================================================
  // LUT ready / DFD
  // ==========================================================================
  val streamExpReady =
    VecInit(
      streamExpLuts.map(
        _.io.lut_ready
      )
    ).asUInt.andR

  val correctionExpReady =
    VecInit(
      correctionExpLuts.map(
        _.io.lut_ready
      )
    ).asUInt.andR

  val phase2ExpReady =
    VecInit(
      phase2ExpLuts.map(
        _.io.lut_ready
      )
    ).asUInt.andR

  val scaleReady =
    VecInit(
      scaleLuts.map(
        _.io.lut_ready
      )
    ).asUInt.andR

  io.lut_ready :=
    scaleReady &&
    (
      !isSoftmax ||
      (streamExpReady && correctionExpReady && phase2ExpReady)
    )

  val p1LaneMismatch =
    p1AnyValid &&
    !p1AllValid

  val p2LaneMismatch =
    p2AnyValid &&
    !p2AllValid

  val vectorLengthZero =
    isNorm &&
    (io.logical_vector_length === 0.U)

  val vectorTooLong =
    isNorm &&
    (io.logical_vector_length > vectorSize.U)

  // ONLINE is boundary-driven by RoPE, but the configured N should still match
  // the number of accepted samples/lane between boundaries.
  val onlineLengthMismatch =
    onlineLastBeat &&
    ((onlineElementCounter + 1.U) =/= logicalLengthSafe)

  val scaleLookupBeforeReady =
    scaleLookupFire &&
    !scaleReady

  val expUseBeforeReady =
    isSoftmax &&
    (
      streamExpReq.asUInt.orR ||
      correctionReq ||
      phase2SoftReq
    ) &&
    !(streamExpReady && correctionExpReady && phase2ExpReady)

  // ONLINE may use the existing direct active scale.
  // DISTRIBUTED requires one completed context exactly at vector start.
  val onlinePhase2ScaleMissing =
    phase2Fire &&
    isOnline &&
    isNorm &&
    !(
      scaleContextValid ||
      onlineScaleArriving
    )

  val distributedPhase2ContextMissing =
    distributedPhase2VectorStart &&
    !distPhase2ContextFifo.io.deq_valid

  val phase2ScaleMissing =
    onlinePhase2ScaleMissing ||
    distributedPhase2ContextMissing

  val outputHoldCollision =
    outHoldValid &&
    rawOutValid &&
    !io.stall

  io.sync_alert :=
    p1LaneMismatch ||
    p2LaneMismatch ||
    vectorLengthZero ||
    vectorTooLong ||
    onlineLengthMismatch ||
    metaFifo.io.overflow ||
    metaFifo.io.underflow ||
    softContextFifo.io.overflow ||
    softContextFifo.io.underflow ||
    distPhase2ContextFifo.io.overflow ||
    distPhase2ContextFifo.io.underflow ||
    scaleLookupBeforeReady ||
    expUseBeforeReady ||
    phase2ScaleMissing ||
    outputHoldCollision
}
