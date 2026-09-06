package npu.core

import chisel3._
import chisel3.util._


// ============================================================================
// [1] One lane of input skew
//
// lane k -> k-cycle delay
//
// data, valid, weight_update tag all experience identical timing.
// ============================================================================
class InputSkewLane(
  val delayCycles: Int,
  val dataBits: Int = 8
) extends Module {

  require(delayCycles >= 0)

  val io = IO(new Bundle {
    val in_data =
      Input(SInt(dataBits.W))

    val in_valid =
      Input(Bool())

    val in_tile_start =
      Input(Bool())

    val stall =
      Input(Bool())

    val out_data =
      Output(SInt(dataBits.W))

    val out_valid =
      Output(Bool())

    val out_weight_update =
      Output(Bool())
  })

  val run = !io.stall

  if (delayCycles == 0) {

    io.out_data :=
      Mux(
        run && io.in_valid,
        io.in_data,
        0.S(dataBits.W)
      )

    io.out_valid :=
      run && io.in_valid

    io.out_weight_update :=
      run &&
      io.in_valid &&
      io.in_tile_start

  } else {

    val dataPipe =
      RegInit(
        VecInit(
          Seq.fill(delayCycles)(
            0.S(dataBits.W)
          )
        )
      )

    val validPipe =
      RegInit(
        VecInit(
          Seq.fill(delayCycles)(
            false.B
          )
        )
      )

    val updatePipe =
      RegInit(
        VecInit(
          Seq.fill(delayCycles)(
            false.B
          )
        )
      )

    when(run) {

      dataPipe(0) :=
        Mux(
          io.in_valid,
          io.in_data,
          0.S(dataBits.W)
        )

      validPipe(0) := io.in_valid

      updatePipe(0) := io.in_valid && io.in_tile_start

      for (i <- 1 until delayCycles) {
        dataPipe(i) := dataPipe(i - 1)

        validPipe(i) := validPipe(i - 1)

        updatePipe(i) := updatePipe(i - 1)
      }
    }

    io.out_data :=
      Mux(
        run && validPipe(delayCycles - 1),
        dataPipe(delayCycles - 1),
        0.S(dataBits.W)
      )

    io.out_valid := run && validPipe(delayCycles - 1)

    io.out_weight_update := run && updatePipe(delayCycles - 1)
  }
}


// ============================================================================
// [2] Data Orchestrator
//
// Input:
//
//   one INT8 A row / cycle
//
//       in_input(k) = A[m][k]
//
// Output:
//
//       lane k delayed by k cycles
//
//
// Weight:
//
//   one INT8 W row / cycle
//
//       beat n = W[n][0:K-1]
//
// Shadow storage:
//
//       shadowWeight(n)(k) = W[n][k]
//
// MXU:
//
//       PE[k][n].shadow_w = W[n][k]
// ============================================================================
class DataOrchUnit(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val dataBits: Int = 8
) extends Module {

  require(numRows > 0)
  require(numCols > 0)

  val io = IO(new Bundle {

    // A[m][0:K-1]
    val in_input =
      Input(Vec(numRows, SInt(dataBits.W)))

    val input_valid =
      Input(Bool())

    // Asserted only for the first A row of a new tile
    val input_tile_start =
      Input(Bool())

    // W[n][0:K-1]
    val in_weight =
      Input(Vec(numRows, SInt(dataBits.W)))

    val weight_valid =
      Input(Bool())

    val stall =
      Input(Bool())

    // To MXU
    val mxu_input =
      Output(Vec(numRows, SInt(dataBits.W)))

    val mxu_input_valid =
      Output(Vec(numRows, Bool()))

    // mxu_weight(k)(n) = W[n][k]
    val mxu_weight =
      Output(
        Vec(
          numRows,
          Vec(numCols, SInt(dataBits.W))
        )
      )

    val weight_update_row =
      Output(Vec(numRows, Bool()))

    val orch_alert =
      Output(Bool())
  })

  val run = !io.stall

  // ==========================================================================
  // Input skew
  // ==========================================================================

  val skewLanes =
    Seq.tabulate(numRows) { k =>
      Module(
        new InputSkewLane(
          delayCycles = k,
          dataBits = dataBits
        )
      )
    }

  for (k <- 0 until numRows) {

    val lane = skewLanes(k)

    lane.io.in_data := io.in_input(k)

    lane.io.in_valid := io.input_valid

    lane.io.in_tile_start := io.input_tile_start

    lane.io.stall := io.stall

    io.mxu_input(k) := lane.io.out_data

    io.mxu_input_valid(k) := lane.io.out_valid

    io.weight_update_row(k) := lane.io.out_weight_update
  }

  // ==========================================================================
  // Shadow Weight
  //
  // first index  : N / MXU column
  // second index : K / MXU row
  //
  // shadowWeight(n)(k) = W[n][k]
  // ==========================================================================

  val shadowWeight =
    RegInit(
      VecInit(
        Seq.fill(numCols)(
          VecInit(
            Seq.fill(numRows)(
              0.S(dataBits.W)
            )
          )
        )
      )
    )

  val weightIdxBits = math.max( 1, log2Ceil(numCols) )

  val weightLoadIdx =
    RegInit(
      0.U(weightIdxBits.W)
    )

  when(run && io.weight_valid) {

    shadowWeight(weightLoadIdx) := io.in_weight

    when( weightLoadIdx === (numCols - 1).U ) {
      weightLoadIdx := 0.U
    }.otherwise {
      weightLoadIdx := weightLoadIdx + 1.U
    }
  }

  // ==========================================================================
  // Shadow -> MXU mapping
  //
  // PE[k][n] requires W[n][k]
  // ==========================================================================
  for (k <- 0 until numRows) {
    for (n <- 0 until numCols) {
      io.mxu_weight(k)(n) := shadowWeight(n)(k)
    }
  }

  io.orch_alert :=
    false.B
}