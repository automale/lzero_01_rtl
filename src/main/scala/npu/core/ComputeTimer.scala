package npu.core

import chisel3._
import chisel3.util._

// ============================================================================
// Metadata aligned with the final flat TPU output stream.
//
// quant_param_update:
//   Quant parameter context change.
//   Policy is independently configurable.
//
// row_change_update:
//   RoPE logical-position parameter change.
//   One pulse per completed output row.
// ============================================================================
class TPUStreamMeta extends Bundle {
  val quant_param_update = Bool()
  val row_change_update  = Bool()
}

// Per-output-tile control which must survive until the accumulator
// physically begins streaming that tile.
class TPUTileCtrl extends Bundle {
  val quant_param_update = Bool()
  val fusion_req         = Bool()
}

class ComputeTimer(
  val tileSize: Int = 16
) extends Module {

  require(tileSize > 0)

  val io = IO(new Bundle {
    // MXU column-0 output progress
    val row0_valid = Input(Bool())
    val stall      = Input(Bool())

    // Number of K tiles accumulated into one output tile
    val intermNum = Input(UInt(32.W))

    // ------------------------------------------------------------------------
    // Quant parameter update policy
    //
    // Unit: completed output tiles
    //
    // 0 -> automatic update disabled
    // 1 -> every output tile
    // 2 -> every 2 output tiles
    // ...
    //
    // Completely independent from RoPE.
    // ------------------------------------------------------------------------
    val quantParamTilePeriod = Input(UInt(32.W))

    // Accumulator control
    val accum_first      = Output(Bool())
    val output_tile_done = Output(Bool())

    // Dynamic metadata
    val quant_param_update = Output(Bool())
    val row_change_update  = Output(Bool())

    // Lookahead control
    val fusion_req = Output(Bool())
  })

  val run  = !io.stall
  val fire = io.row0_valid && run

  val intermSafe =
    Mux(io.intermNum === 0.U, 1.U, io.intermNum)

  val rowBits =
    math.max(1, log2Ceil(tileSize))

  // ==========================================================================
  // Output row progress inside one K tile
  // ==========================================================================
  val outputRowCounter =
    RegInit(0.U(rowBits.W))

  // ==========================================================================
  // K-tile accumulation progress
  // ==========================================================================
  val kTileCounter =
    RegInit(0.U(32.W))

  // ==========================================================================
  // Quant parameter schedule
  //
  // Explicitly independent from row / RoPE scheduling.
  // ==========================================================================
  val quantParamTileCountdown =
    RegInit(0.U(32.W))

  // ==========================================================================
  // Fusion schedule
  //
  // First event: output tile 15
  // Repeat     : every 32 output tiles
  // ==========================================================================
  val fusionTileCountdown =
    RegInit(15.U(32.W))

  val lastRow =
    outputRowCounter === (tileSize - 1).U

  val lastKTile =
    kTileCounter === (intermSafe - 1.U)

  io.accum_first :=
    kTileCounter === 0.U

  // --------------------------------------------------------------------------
  // A logical output row is complete only when it belongs to the final K tile.
  //
  // This is the RoPE parameter boundary.
  //
  // Every final output row receives a new logical-position parameter.
  // --------------------------------------------------------------------------
  val outputRowDone =
    fire &&
    lastKTile

  io.row_change_update :=
    outputRowDone

  // --------------------------------------------------------------------------
  // Output tile completion
  // --------------------------------------------------------------------------
  val outputTileDone =
    outputRowDone &&
    lastRow

  io.output_tile_done :=
    outputTileDone

  // --------------------------------------------------------------------------
  // Quant update
  //
  // Completely independent from RoPE and from old colTileCounter.
  // --------------------------------------------------------------------------
  val quantAutoUpdateEnabled =
    io.quantParamTilePeriod =/= 0.U

  io.quant_param_update :=
    outputTileDone &&
    quantAutoUpdateEnabled &&
    (quantParamTileCountdown === 0.U)

  // --------------------------------------------------------------------------
  // Fusion event
  // --------------------------------------------------------------------------
  io.fusion_req :=
    outputTileDone &&
    (fusionTileCountdown === 0.U)

  // ==========================================================================
  // Logical progress
  // ==========================================================================
  when(fire) {
    when(lastRow) {
      outputRowCounter := 0.U

      when(lastKTile) {
        kTileCounter := 0.U
      }.otherwise {
        kTileCounter := kTileCounter + 1.U
      }
    }.otherwise {
      outputRowCounter := outputRowCounter + 1.U
    }
  }

  // ==========================================================================
  // Per-output-tile control progress
  // ==========================================================================
  when(outputTileDone) {

    // Quant schedule
    when(quantAutoUpdateEnabled) {
      when(quantParamTileCountdown === 0.U) {
        quantParamTileCountdown :=
          io.quantParamTilePeriod - 1.U
      }.otherwise {
        quantParamTileCountdown :=
          quantParamTileCountdown - 1.U
      }
    }.otherwise {
      quantParamTileCountdown := 0.U
    }

    // Fusion schedule
    when(fusionTileCountdown === 0.U) {
      fusionTileCountdown := 31.U
    }.otherwise {
      fusionTileCountdown :=
        fusionTileCountdown - 1.U
    }
  }
}