package npu.core

import chisel3._

class MxuOrchUnit(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32
) extends Module {

  val io = IO(new Bundle {

    // A[m][0:K-1]
    val in_input =
      Input(Vec(numRows, SInt(inBits.W)))

    val input_valid =
      Input(Bool())

    // First external row of each new A tile
    val input_tile_start =
      Input(Bool())

    // W[n][0:K-1]
    val in_weight =
      Input(Vec(numRows, SInt(inBits.W)))

    val weight_valid =
      Input(Bool())

    val clear_W =
      Input(Bool())

    val stall =
      Input(Bool())

    // Raw skewed MXU output
    val out_MAC =
      Output(Vec(numCols, SInt(accBits.W)))

    val fatal_alert =
      Output(Bool())
  })

  val orchestrator =
    Module(
      new DataOrchUnit(
        numRows = numRows,
        numCols = numCols,
        dataBits = inBits
      )
    )

  val mxu =
    Module(
      new MXU(
        numRows = numRows,
        numCols = numCols,
        inBits = inBits,
        accBits = accBits
      )
    )

  // --------------------------------------------------------------------------
  // External -> Orchestrator
  // --------------------------------------------------------------------------

  orchestrator.io.in_input :=
    io.in_input

  orchestrator.io.input_valid :=
    io.input_valid

  orchestrator.io.input_tile_start :=
    io.input_tile_start

  orchestrator.io.in_weight :=
    io.in_weight

  orchestrator.io.weight_valid :=
    io.weight_valid

  orchestrator.io.stall :=
    io.stall

  // --------------------------------------------------------------------------
  // Orchestrator -> MXU
  // --------------------------------------------------------------------------

  mxu.io.in_X :=
    orchestrator.io.mxu_input

  mxu.io.in_shadow_W :=
    orchestrator.io.mxu_weight

  mxu.io.weight_update :=
    orchestrator.io.weight_update_row

  mxu.io.clear_W :=
    io.clear_W

  mxu.io.stall :=
    io.stall

  // --------------------------------------------------------------------------
  // Output
  // --------------------------------------------------------------------------

  io.out_MAC :=
    mxu.io.out_MAC

  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert
}