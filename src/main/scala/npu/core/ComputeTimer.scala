package npu.core

import chisel3._
import chisel3.util._


// ============================================================================
// Compute Timer
//
// Advances ONLY when row0_valid is high.
//
// row0_valid:
//   one valid output row from MXU column 0
//
// Every 16 accepted rows:
//   one K tile has completed.
//
// Every intermNum K tiles:
//   one output tile has completed.
//
// Therefore the timer is naturally stall-safe:
// if the datapath does not advance, row0_valid must not advance either.
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
    // Number of K tiles accumulated into one output tile
    val intermNum = Input(UInt(32.W))
    // Number of output-column tiles in one logical row group
    val colNum = Input(UInt(32.W))
    // Normalizer phase scheduling load value
    val norm_phase_load = Input(UInt(32.W))


    // ------------------------------------------------------------------------
    // Accumulator control
    // ------------------------------------------------------------------------
    // High during all 16 rows of the first K tile.
    val accum_first = Output(Bool())
    // One cycle pulse on every 16th accepted row.
    val k_tile_done = Output(Bool())
    // One cycle pulse on the last row of the final K tile.
    //
    // This can directly become accumulator snapshot for column 0.
    val output_tile_done = Output(Bool())


    // ------------------------------------------------------------------------
    // Tile metadata
    //
    // These values describe the output tile currently being completed.
    // ------------------------------------------------------------------------
    val param_update = Output(Bool())
    val fusion_change = Output(Bool())
    val norm_phase_change = Output(Bool())
  })

  val rowBits = math.max( 1, log2Ceil(tileSize) )

  // ==========================================================================
  // Row counter
  // ==========================================================================
  val rowCounter = RegInit(0.U(rowBits.W))


  // ==========================================================================
  // K-tile counter
  //
  // 0 = first K tile
  // ==========================================================================
  val kTileCounter = RegInit(0.U(32.W))


  // ==========================================================================
  // Output-column tile counter
  //
  // 0 means first output tile in the current row group.
  //
  // This corresponds naturally to param_update.
  // ==========================================================================
  val colTileCounter =RegInit(0.U(32.W))

  // ==========================================================================
  // Fusion counter
  //
  // Notion spec:
  //
  //   start = 15
  //   reload = 31
  // ==========================================================================
  val fusionCounter = RegInit(15.U(32.W))


  // ==========================================================================
  // Normalizer phase counter
  //
  // Notion spec:
  //
  //   start = 0
  //   reload = norm_phase_load
  // ==========================================================================
  val normCounter = RegInit(0.U(32.W))

  // Protect against illegal zero dimensions.
  val intermSafe = Mux(io.intermNum === 0.U, 1.U, io.intermNum)

  val colSafe = Mux(io.colNum === 0.U, 1.U, io.colNum)

  // ==========================================================================
  // Current state flags
  // ==========================================================================
  io.accum_first := kTileCounter === 0.U

  val lastRow = rowCounter === (tileSize - 1).U

  val lastKTile = kTileCounter === (intermSafe - 1.U)

  io.k_tile_done := io.row0_valid && lastRow

  io.output_tile_done := io.row0_valid && lastRow && lastKTile

  // --------------------------------------------------------------------------
  // Metadata describes the tile BEFORE counters advance.
  // --------------------------------------------------------------------------
  io.param_update := colTileCounter === 0.U

  io.fusion_change := fusionCounter === 0.U

  io.norm_phase_change := normCounter === 0.U

  // ==========================================================================
  // State update
  // ==========================================================================
  when(io.row0_valid) {
    when(lastRow) {
      rowCounter := 0.U

      // ======================================================================
      // K tile transition
      // ======================================================================
      when(lastKTile) {
        kTileCounter := 0.U

        // ====================================================================
        // Completed one final output tile
        // ====================================================================
        when( colTileCounter === (colSafe - 1.U) ) {
          colTileCounter := 0.U
        }.otherwise {
          colTileCounter := colTileCounter + 1.U
        }

        // --------------------------------------------------------------------
        // Fusion counter
        // --------------------------------------------------------------------
        when( fusionCounter === 0.U ) {
          fusionCounter := 31.U
        }.otherwise {
          fusionCounter := fusionCounter - 1.U
        }


        // --------------------------------------------------------------------
        // Normalizer phase counter
        // --------------------------------------------------------------------

        when( normCounter === 0.U ) {
          normCounter := io.norm_phase_load
        }.otherwise {
          normCounter := normCounter - 1.U
        }

      }.otherwise {
        kTileCounter := kTileCounter + 1.U
      }

    }.otherwise {
      rowCounter := rowCounter + 1.U
    }
  }
}