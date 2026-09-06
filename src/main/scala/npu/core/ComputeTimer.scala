package npu.core

import chisel3._
import chisel3.util._


// ============================================================================
// Dynamic metadata travelling with TPU output.
//
// Static VPU controls such as
//
//   alu_mode
//   act_en
//   norm_mode
//   rope_en
//
// are NOT carried here.
//
// They remain constant for one macro-operation and should be held in
// configuration registers.
//
// Only events whose meaning changes with the data stream are carried.
// ============================================================================
class TPUStreamMeta extends Bundle {
  val param_update = Bool()
  val fusion_req = Bool()
}

// ============================================================================
// Compute Timer
//
// Logical datapath sequencer for TPU.
//
// Timing reference:
//
//   MXU output column 0 valid
//
// The timer counts only actually accepted MXU output rows.
//
//                  row0_valid
//                      │
//                      ▼
//               Row Counter (16)
//                      │
//                      ▼
//               K Tile Counter
//                      │
//                      ▼
//              Output Tile Event
//                │      │      │
//                ▼      ▼      ▼
//            param   fusion   norm
//
// Accumulator physical column skew is NOT handled here.
// That belongs to TileAccumulator.
// ============================================================================
class ComputeTimer(
  val tileSize: Int = 16
) extends Module {

  require(tileSize > 0)

  val io = IO(new Bundle {

    // ------------------------------------------------------------------------
    // Datapath progress
    // ------------------------------------------------------------------------
    val row0_valid = Input(Bool())
    val stall = Input(Bool())

    // ------------------------------------------------------------------------
    // Operation dimensions
    //
    // intermNum:
    //   number of K tiles accumulated into one output tile
    //
    // colNum:
    //   number of output-column tiles in one logical output row
    // ------------------------------------------------------------------------
    val intermNum = Input(UInt(32.W))
    val colNum = Input(UInt(32.W))

    // ------------------------------------------------------------------------
    // Accumulator logical control
    // ------------------------------------------------------------------------

    // Level signal.
    //
    // High while the current K tile is the first K tile.
    val accum_first = Output(Bool())

    // One-cycle event referenced to MXU column 0.
    //
    // High when:
    //
    //   final K tile
    //   +
    //   final row
    //
    // reaches MXU output column 0.
    val output_tile_done = Output(Bool())


    // ------------------------------------------------------------------------
    // Dynamic metadata
    //
    // Valid only together with output_tile_done.
    // ------------------------------------------------------------------------
    val param_update = Output(Bool())
    val fusion_req = Output(Bool())
  })


  val run = !io.stall

  val fire = io.row0_valid && run


  // ==========================================================================
  // Safe configuration values
  // ==========================================================================

  val intermSafe =
    Mux( io.intermNum === 0.U, 1.U, io.intermNum )

  val colSafe = Mux( io.colNum === 0.U, 1.U, io.colNum )


  // ==========================================================================
  // Row counter
  //
  // Counts flat output rows from MXU column 0:
  //
  //   0 ... 15
  // ==========================================================================

  val rowBits = math.max( 1, log2Ceil(tileSize) )

  val rowCounter = RegInit(0.U(rowBits.W))


  // ==========================================================================
  // K tile counter
  //
  // 0 = first K tile
  // ==========================================================================

  val kTileCounter = RegInit(0.U(32.W))


  // ==========================================================================
  // Output-column tile counter
  //
  // Used for parameter context change.
  //
  // 0 means:
  //
  //   first output-column tile of a logical output row
  // ==========================================================================
  val colTileCounter = RegInit(0.U(32.W))

  // ==========================================================================
  // Fusion counter
  //
  // Notion Compute Timer spec:
  //
  //   initial = 15
  //   reload  = 31
  // ==========================================================================
  val fusionCounter = RegInit(15.U(32.W))

  // ==========================================================================
  // Current logical state
  // ==========================================================================
  val lastRow = rowCounter === (tileSize - 1).U

  val lastKTile = kTileCounter === (intermSafe - 1.U)

  io.accum_first := kTileCounter === 0.U

  val outputTileDone = fire && lastRow && lastKTile

  io.output_tile_done := outputTileDone


  // ==========================================================================
  // Metadata
  //
  // Generate EVENT bits only when the corresponding output tile completes.
  //
  // These bits will later be queued until the accumulator starts streaming
  // the de-skewed output tile.
  // ==========================================================================
  io.param_update := outputTileDone && (colTileCounter === 0.U)

  io.fusion_req := outputTileDone && (fusionCounter === 0.U)

  io.norm_phase_change := outputTileDone && (normCounter === 0.U)

  // ==========================================================================
  // Counter update
  // ==========================================================================

  when(fire) {

    // ------------------------------------------------------------------------
    // One complete MXU output row accepted
    // ------------------------------------------------------------------------

    when(lastRow) {

      rowCounter := 0.U


      // ----------------------------------------------------------------------
      // One complete K tile accepted
      // ----------------------------------------------------------------------

      when(lastKTile) {

        // Next output tile begins from its first K tile.
        kTileCounter := 0.U


        // ====================================================================
        // Completed one output tile.
        // ====================================================================


        // --------------------------------------------------------------------
        // Output-column counter
        // --------------------------------------------------------------------

        when(colTileCounter === (colSafe - 1.U)) {
          
          colTileCounter := 0.U

        }.otherwise {

          colTileCounter := colTileCounter + 1.U
        
        }


        // --------------------------------------------------------------------
        // Fusion timer
        // --------------------------------------------------------------------

        when(fusionCounter === 0.U) {

          fusionCounter :=31.U

        }.otherwise {

          fusionCounter := fusionCounter - 1.U
        
        }


        // --------------------------------------------------------------------
        // Normalizer phase timer
        // --------------------------------------------------------------------

        when(normCounter === 0.U) {

          normCounter := io.norm_phase_load

        }.otherwise {

          normCounter := normCounter - 1.U
        
        }


      }.otherwise {

        // Next K tile of the same output tile.
        kTileCounter := kTileCounter + 1.U
      
      }


    }.otherwise {

      rowCounter := rowCounter + 1.U
    
    }
  }
}