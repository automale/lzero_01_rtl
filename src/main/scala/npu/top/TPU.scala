package npu.top

import chisel3._
import npu.core._


// ============================================================================
// TPU
//
// INT8 A / INT8 W
//        ↓
// Orchestrator
//        ↓
// 16x16 Weight-Stationary MXU
//        ↓
// INT32 partial tile
//        ↓
// TileAccumulator
//        ↓
// INT32 accumulated / de-skewed tile
// ============================================================================
class TPU_top(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32
) extends Module {

  val io = IO(new Bundle {

    // ------------------------------------------------------------------------
    // A[M,K] input row
    // ------------------------------------------------------------------------

    val in_input =
      Input(
        Vec(
          numRows,
          SInt(inBits.W)
        )
      )

    val input_valid = Input(Bool())

    val input_tile_start = Input(Bool())


    // ------------------------------------------------------------------------
    // W[N,K] row
    // ------------------------------------------------------------------------

    val in_weight =
      Input(
        Vec(
          numRows,
          SInt(inBits.W)
        )
      )

    val weight_valid = Input(Bool())

    val clear_W = Input(Bool())


    // ------------------------------------------------------------------------
    // Accumulator control
    //
    // Timing reference = MXU output column 0
    // ------------------------------------------------------------------------

    val accum_en = Input(Bool())

    val accum_first = Input(Bool())

    val accum_snapshot = Input(Bool())

    val accum_stream_en = Input(Bool())


    val stall = Input(Bool())


    // ------------------------------------------------------------------------
    // VPU output
    // ------------------------------------------------------------------------

    val out_accum =
      Output(
        Vec(
          numCols,
          SInt(accBits.W)
        )
      )

    val out_valid =
      Output(
        Vec(
          numCols,
          Bool()
        )
      )

    val fatal_alert = Output(Bool())
  })


  // ==========================================================================
  // Submodules
  // ==========================================================================

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

  val accumulator =
    Module(
      new TileAccumulator(
        numCols = numCols,
        numLines = numRows,
        dataBits = accBits
      )
    )


  // ==========================================================================
  // Global stall
  // ==========================================================================

  orchestrator.io.stall := io.stall

  mxu.io.stall := io.stall

  accumulator.io.stall := io.stall


  // ==========================================================================
  // OCM -> Orchestrator
  // ==========================================================================

  orchestrator.io.in_input := io.in_input

  orchestrator.io.input_valid := io.input_valid

  orchestrator.io.input_tile_start := io.input_tile_start

  orchestrator.io.in_weight := io.in_weight

  orchestrator.io.weight_valid := io.weight_valid


  // ==========================================================================
  // Orchestrator -> MXU
  // ==========================================================================

  mxu.io.in_X := orchestrator.io.mxu_input

  mxu.io.in_shadow_W := orchestrator.io.mxu_weight

  mxu.io.weight_update := orchestrator.io.weight_update_row

  mxu.io.clear_W := io.clear_W

  mxu.io.in_valid := orchestrator.io.mxu_input_valid


  // ==========================================================================
  // MXU -> Accumulator
  // ==========================================================================

  accumulator.io.in_vec := mxu.io.out_MAC

  accumulator.io.accum_en := io.accum_en

  accumulator.io.accum_first := io.accum_first

  accumulator.io.accum_snapshot := io.accum_snapshot

  accumulator.io.accum_stream_en := io.accum_stream_en


  // ==========================================================================
  // Accumulator -> VPU
  // ==========================================================================

  io.out_accum := accumulator.io.out_vec

  io.out_valid := accumulator.io.out_valid


  // ==========================================================================
  // Alert
  // ==========================================================================

  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert ||
    accumulator.io.accum_alert
}