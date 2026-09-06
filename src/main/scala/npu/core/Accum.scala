package npu.core

import chisel3._
import chisel3.util._

// ============================================================================
// [1] Per-column true ping-pong accumulation buffer
//
// Two symmetric accumulation banks:
//
//   bank0[numLines]
//   bank1[numLines]
//
// Current output tile accumulates into write_bank.
// When the final K tile / final row is committed:
//
//   write_bank := !write_bank
//
// The completed bank can simultaneously be streamed while the other bank
// accumulates the next output tile.
//
// IMPORTANT:
// Storage is row-addressed rather than shift-register based.
// This allows an old output bank to be streamed while that same physical bank
// begins to be reused by a later tile after already-consumed rows become free.
// ============================================================================
class Accum_buffer(
  val numLines: Int = 16,
  val dataBits: Int = 32
) extends Module {

  private val rowBits = math.max(1, log2Ceil(numLines))

  val io = IO(new Bundle {
    val in_scalar = Input(SInt(dataBits.W))
    val accum_en = Input(Bool())
    val first_tile = Input(Bool())
    val snapshot = Input(Bool())
    val stall = Input(Bool())

    // Flat output read port
    val read_en = Input(Bool())
    val read_bank = Input(Bool())
    val read_row = Input(UInt(rowBits.W))
    val out_scalar = Output(SInt(dataBits.W))

    val accum_alert = Output(Bool())
  })

  val run = !io.stall

  // Two fully symmetric physical banks.
  val bank0 = RegInit(VecInit(Seq.fill(numLines)(0.S(dataBits.W))))
  val bank1 = RegInit(VecInit(Seq.fill(numLines)(0.S(dataBits.W))))

  // Current accumulation destination.
  // false -> bank0
  // true  -> bank1
  val write_bank = RegInit(false.B)

  // Current M row being accumulated.
  val row_ptr = RegInit(0.U(rowBits.W))

  val old_value = Mux(
    write_bank,
    bank1(row_ptr),
    bank0(row_ptr)
  )

  val addend = Mux(
    io.first_tile,
    0.S(dataBits.W),
    old_value
  )

  val current_sum_full = addend +& io.in_scalar
  val current_sum = current_sum_full(dataBits - 1, 0).asSInt

  val overflow =
    current_sum_full(dataBits) =/=
    current_sum_full(dataBits - 1)

  val lastRow =
    row_ptr === (numLines - 1).U

  // snapshot must arrive exactly with the final row of an output tile.
  val snapshotMisalign =
    io.snapshot &&
    io.accum_en &&
    !lastRow

  // True ping-pong may read and write the same physical bank simultaneously,
  // but never the same row.
  val readWriteCollision =
    run &&
    io.accum_en &&
    io.read_en &&
    (write_bank === io.read_bank) &&
    (row_ptr === io.read_row)

  when(run && io.accum_en) {
    when(write_bank) {
      bank1(row_ptr) := current_sum
    }.otherwise {
      bank0(row_ptr) := current_sum
    }

    when(lastRow) {
      row_ptr := 0.U
    }.otherwise {
      row_ptr := row_ptr + 1.U
    }

    // Final row of final K tile committed.
    // Next output tile immediately starts accumulating into the other bank.
    when(io.snapshot) {
      write_bank := !write_bank
    }
  }

  io.out_scalar := Mux(
    io.read_bank,
    bank1(io.read_row),
    bank0(io.read_row)
  )

  io.accum_alert :=
    run &&
    (
      (io.accum_en && overflow) ||
      snapshotMisalign ||
      readWriteCollision
    )
}

// ============================================================================
// [2] Tile Accumulator
//
// ComputeTimer controls are referenced to MXU column 0.
//
// TileAccumulator handles:
//   - exact column skew
//   - two-bank accumulation
//   - per-column bank role swapping
//   - automatic completed-tile streaming
//   - flat row-major de-skew output
//
// No external accum_stream_en is required.
// ============================================================================
class TileAccumulator(
  val numCols: Int = 16,
  val numLines: Int = 16,
  val dataBits: Int = 32
) extends Module {

  private val rowBits = math.max(1, log2Ceil(numLines))

  val io = IO(new Bundle {
    val in_vec = Input(Vec(numCols, SInt(dataBits.W)))

    // Controls referenced to MXU output column 0
    val accum_en = Input(Bool())
    val accum_first = Input(Bool())
    val accum_snapshot = Input(Bool())

    val stall = Input(Bool())

    // Flat row-major output
    val out_vec = Output(Vec(numCols, SInt(dataBits.W)))
    val out_valid = Output(Vec(numCols, Bool()))

    // Useful for TPU metadata attachment
    val out_tile_start = Output(Bool())
    val out_tile_end = Output(Bool())

    // Last physical column completed its bank commit.
    val snapshot_done = Output(Bool())

    val accum_alert = Output(Bool())
  })

  val run = !io.stall

  val buffers = Seq.fill(numCols)(
    Module(
      new Accum_buffer(
        numLines = numLines,
        dataBits = dataBits
      )
    )
  )

  // ==========================================================================
  // [A] Exact column skew
  //
  // col0 : direct
  // col1 : +1 cycle
  // ...
  // col15: +15 cycles
  // ==========================================================================

  val accumEnByCol = Wire(Vec(numCols, Bool()))
  val firstByCol = Wire(Vec(numCols, Bool()))
  val snapshotByCol = Wire(Vec(numCols, Bool()))

  accumEnByCol(0) := io.accum_en
  firstByCol(0) := io.accum_first
  snapshotByCol(0) := io.accum_snapshot

  for (c <- 1 until numCols) {
    accumEnByCol(c) := RegEnable(
      accumEnByCol(c - 1),
      false.B,
      run
    )

    firstByCol(c) := RegEnable(
      firstByCol(c - 1),
      false.B,
      run
    )

    snapshotByCol(c) := RegEnable(
      snapshotByCol(c - 1),
      false.B,
      run
    )
  }

  val snapshotDone =
    run &&
    snapshotByCol(numCols - 1) &&
    accumEnByCol(numCols - 1)

  io.snapshot_done := snapshotDone

  // ==========================================================================
  // [B] Completed-bank sequence
  //
  // First output tile -> bank0
  // Second            -> bank1
  // Third             -> bank0
  // ...
  //
  // Per-column write_bank switches locally with column skew, but the logical
  // completed tile bank is globally deterministic.
  // ==========================================================================

  val nextCompletedBank = RegInit(false.B)

  // ==========================================================================
  // [C] Automatic flat output streaming
  // ==========================================================================

  val streamActive = RegInit(false.B)
  val streamBank = RegInit(false.B)
  val read_ptr = RegInit(0.U(rowBits.W))

  val lastReadRow =
    read_ptr === (numLines - 1).U

  // A newly completed tile must arrive either:
  //   1. while no previous tile is being streamed, or
  //   2. exactly while previous tile row15 is being streamed.
  //
  // Case 2 gives zero-bubble bank handoff for intermNum == 1.
  val streamCatchupHazard =
    snapshotDone &&
    streamActive &&
    !lastReadRow

  when(run) {
    when(snapshotDone) {
      // The bank that just completed becomes the next stream source.
      streamBank := nextCompletedBank
      nextCompletedBank := !nextCompletedBank

      // Start new tile immediately after this edge.
      // If previous row15 is currently being streamed, this becomes a
      // zero-bubble bank handoff.
      streamActive := true.B
      read_ptr := 0.U

    }.elsewhen(streamActive) {
      when(lastReadRow) {
        streamActive := false.B
        read_ptr := 0.U
      }.otherwise {
        read_ptr := read_ptr + 1.U
      }
    }.otherwise {
      read_ptr := 0.U
    }
  }

  // ==========================================================================
  // [D] Per-column bank connection
  // ==========================================================================

  for (c <- 0 until numCols) {
    buffers(c).io.in_scalar := io.in_vec(c)
    buffers(c).io.accum_en := accumEnByCol(c)
    buffers(c).io.first_tile := firstByCol(c)
    buffers(c).io.snapshot := snapshotByCol(c)
    buffers(c).io.stall := io.stall

    buffers(c).io.read_en := streamActive
    buffers(c).io.read_bank := streamBank
    buffers(c).io.read_row := read_ptr

    io.out_vec(c) := Mux(
      streamActive && run,
      buffers(c).io.out_scalar,
      0.S(dataBits.W)
    )

    io.out_valid(c) :=
      streamActive && run
  }

  io.out_tile_start :=
    streamActive &&
    run &&
    (read_ptr === 0.U)

  io.out_tile_end :=
    streamActive &&
    run &&
    lastReadRow

  // ==========================================================================
  // [E] DFD
  // ==========================================================================

  val bufferAlerts =
    VecInit(
      buffers.map(_.io.accum_alert)
    ).asUInt.orR

  io.accum_alert :=
    bufferAlerts ||
    streamCatchupHazard
}