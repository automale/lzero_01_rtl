package npu.core

import chisel3._
import chisel3.util._


// ============================================================================
// [1] Per-column accumulation buffer
//
// One Accum_buffer corresponds to one output column N.
//
// Input       : signed INT32 MXU partial sum
// Accumulator : signed INT32
//
// For every K tile:
//
//   row0, row1, ..., row15
//
// are streamed into this buffer.
//
// ping_reg:
//   working K-tile accumulation
//
// pong_reg:
//   completed output tile snapshot
// ============================================================================
class Accum_buffer(
  val numLines: Int = 16,
  val dataBits: Int = 32
) extends Module {

  val io = IO(new Bundle {

    val in_scalar =
      Input(SInt(dataBits.W))

    val accum_en =
      Input(Bool())

    val first_tile =
      Input(Bool())

    val snapshot =
      Input(Bool())

    val stall =
      Input(Bool())

    val out_pong_array =
      Output(Vec(numLines, SInt(dataBits.W)))

    val accum_alert =
      Output(Bool())
  })

  val run = !io.stall

  val ping_reg =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(dataBits.W)
        )
      )
    )

  val pong_reg =
    RegInit(
      VecInit(
        Seq.fill(numLines)(
          0.S(dataBits.W)
        )
      )
    )

  // For first K tile:
  //
  //     result = 0 + MXU
  //
  // For following K tiles:
  //
  //     result = previous K accumulation + MXU
  val addend =
    Mux(
      io.first_tile,
      0.S(dataBits.W),
      ping_reg(0)
    )

  // INT32 + INT32 -> INT33
  val current_sum_full =
    addend +& io.in_scalar

  val current_sum =
    current_sum_full(
      dataBits - 1,
      0
    ).asSInt

  val overflow =
    current_sum_full(dataBits) =/=
    current_sum_full(dataBits - 1)

  when(run) {

    // ------------------------------------------------------------------------
    // Working accumulation
    // ------------------------------------------------------------------------
    when(io.accum_en) {

      ping_reg(numLines - 1) :=
        current_sum

      for (i <- 0 until numLines - 1) {
        ping_reg(i) :=
          ping_reg(i + 1)
      }
    }

    // ------------------------------------------------------------------------
    // Final tile snapshot
    //
    // At the last row:
    //
    // ping(1..15) already contain final rows 0..14
    // current_sum contains final row 15
    // ------------------------------------------------------------------------
    when(io.snapshot && io.accum_en) {

      pong_reg(numLines - 1) :=
        current_sum

      for (i <- 0 until numLines - 1) {
        pong_reg(i) :=
          ping_reg(i + 1)
      }
    }
  }

  io.out_pong_array :=
    pong_reg

  io.accum_alert :=
    run &&
    io.accum_en &&
    overflow
}


// ============================================================================
// [2] Tile Accumulator
//
// MXU output is skewed across N columns.
//
// If column 0 result arrives at t:
//
//   col0 : t
//   col1 : t+1
//   ...
//   col15: t+15
//
// Therefore accumulation control must have exactly the same skew.
//
// IMPORTANT:
//
//   col0 control delay = 0
//
// not 1 cycle.
// ============================================================================
class TileAccumulator(
  val numCols: Int = 16,
  val numLines: Int = 16,
  val dataBits: Int = 32
) extends Module {

  val io = IO(new Bundle {

    val in_vec =
      Input(
        Vec(
          numCols,
          SInt(dataBits.W)
        )
      )

    // Controls referenced to MXU output column 0
    val accum_en =
      Input(Bool())

    val accum_first =
      Input(Bool())

    val accum_snapshot =
      Input(Bool())

    val accum_stream_en =
      Input(Bool())

    val stall =
      Input(Bool())

    val out_vec =
      Output(
        Vec(
          numCols,
          SInt(dataBits.W)
        )
      )

    val out_valid =
      Output(
        Vec(
          numCols,
          Bool()
        )
      )

    val accum_alert =
      Output(Bool())
  })

  val run = !io.stall

  val accum_buffer =
    Seq.fill(numCols)(
      Module(
        new Accum_buffer(
          numLines = numLines,
          dataBits = dataBits
        )
      )
    )

  // ==========================================================================
  // [A] Exact column skew of control signals
  //
  // col0 : direct
  // col1 : 1-cycle delay
  // ...
  // ==========================================================================

  val accumEnByCol =
    Wire(Vec(numCols, Bool()))

  val firstByCol =
    Wire(Vec(numCols, Bool()))

  val snapshotByCol =
    Wire(Vec(numCols, Bool()))


  accumEnByCol(0) :=
    io.accum_en

  firstByCol(0) :=
    io.accum_first

  snapshotByCol(0) :=
    io.accum_snapshot


  for (c <- 1 until numCols) {

    accumEnByCol(c) :=
      RegEnable(
        accumEnByCol(c - 1),
        false.B,
        run
      )

    firstByCol(c) :=
      RegEnable(
        firstByCol(c - 1),
        false.B,
        run
      )

    snapshotByCol(c) :=
      RegEnable(
        snapshotByCol(c - 1),
        false.B,
        run
      )
  }


  // ==========================================================================
  // [B] MXU -> accumulation buffers
  // ==========================================================================

  for (c <- 0 until numCols) {

    accum_buffer(c).io.in_scalar :=
      io.in_vec(c)

    accum_buffer(c).io.accum_en :=
      accumEnByCol(c)

    accum_buffer(c).io.first_tile :=
      firstByCol(c)

    accum_buffer(c).io.snapshot :=
      snapshotByCol(c)

    accum_buffer(c).io.stall :=
      io.stall
  }


  // ==========================================================================
  // [C] Flat row-major output
  //
  // Read the same M-row from all N-column pong buffers.
  //
  // cycle 0:
  //   Y[0][0:15]
  //
  // cycle 1:
  //   Y[1][0:15]
  //
  // ...
  // ==========================================================================

  val readPtrBits =
    math.max(
      1,
      log2Ceil(numLines)
    )

  val read_ptr =
    RegInit(
      0.U(readPtrBits.W)
    )

  when(run) {

    when(io.accum_stream_en) {

      when(
        read_ptr ===
        (numLines - 1).U
      ) {

        read_ptr :=
          0.U

      }.otherwise {

        read_ptr :=
          read_ptr + 1.U
      }

    }.otherwise {

      read_ptr :=
        0.U
    }
  }


  for (c <- 0 until numCols) {

    io.out_vec(c) :=
      Mux(
        io.accum_stream_en && run,
        accum_buffer(c)
          .io
          .out_pong_array(read_ptr),
        0.S(dataBits.W)
      )

    io.out_valid(c) :=
      io.accum_stream_en &&
      run
  }


  val allAlerts =
    accum_buffer.map(
      _.io.accum_alert
    )

  io.accum_alert :=
    VecInit(allAlerts)
      .asUInt
      .orR
}