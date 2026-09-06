package npu.top

import chisel3._
import chisel3.util._

import npu.core._


// ============================================================================
// Autonomous TPU
//
//                 ┌──────────────┐
// Input / Weight ─► Orchestrator │
//                 └──────┬───────┘
//                        │
//                        ▼
//                 ┌──────────────┐
//                 │     MXU      │
//                 └──────┬───────┘
//                        │
//                        │ out_valid(0)
//                        ├──────────────────────┐
//                        │                      │
//                        ▼                      ▼
//                 ┌──────────────┐      ┌──────────────┐
//                 │ Accumulator  │◄─────│ ComputeTimer │
//                 └──────┬───────┘      └──────────────┘
//                        │
//                        │ flat INT32 vector
//                        │
//                        │ + dynamic metadata
//                        ▼
//                       VPU
//
// Static VPU control is NOT carried as metadata.
//
// Dynamic metadata:
//   param_update
//   fusion_change
//   norm_phase_change
// ============================================================================
class TPU_top(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32,
  val metaQueueDepth: Int = 4
) extends Module {

  require(numRows > 0)
  require(numCols > 0)
  require(metaQueueDepth > 0)


  val io = IO(new Bundle {

    // ========================================================================
    // A[M,K]
    // ========================================================================

    val in_input =
      Input(
        Vec(
          numRows,
          SInt(inBits.W)
        )
      )

    val input_valid =
      Input(Bool())

    val input_tile_start =
      Input(Bool())


    // ========================================================================
    // W[N,K]
    // ========================================================================

    val in_weight =
      Input(
        Vec(
          numRows,
          SInt(inBits.W)
        )
      )

    val weight_valid =
      Input(Bool())

    val clear_W =
      Input(Bool())


    // ========================================================================
    // Operation dimensions
    //
    // These are configuration values.
    //
    // They remain constant during one TPU macro-operation.
    // ========================================================================

    val intermNum =
      Input(UInt(32.W))

    val colNum =
      Input(UInt(32.W))

    val norm_phase_load =
      Input(UInt(32.W))


    // ========================================================================
    // Global control
    // ========================================================================

    val stall =
      Input(Bool())


    // ========================================================================
    // Flat TPU output
    //
    // One row vector per cycle:
    //
    //   Y[m][0:15]
    // ========================================================================

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


    // ========================================================================
    // Dynamic in-band metadata
    //
    // Metadata pulses are attached ONLY to row 0 of each output tile.
    // ========================================================================

    val out_meta =
      Output(
        new TPUStreamMeta
      )


    // ========================================================================
    // DFD
    // ========================================================================

    val fatal_alert =
      Output(Bool())
  })


  val run =
    !io.stall


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


  val timer =
    Module(
      new ComputeTimer(
        tileSize = numRows
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

  orchestrator.io.stall :=
    io.stall

  mxu.io.stall :=
    io.stall

  timer.io.stall :=
    io.stall

  accumulator.io.stall :=
    io.stall


  // ==========================================================================
  // OCM -> Orchestrator
  // ==========================================================================

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


  // ==========================================================================
  // Orchestrator -> MXU
  // ==========================================================================

  mxu.io.in_X :=
    orchestrator.io.mxu_input

  mxu.io.in_valid :=
    orchestrator.io.mxu_input_valid


  mxu.io.in_shadow_W :=
    orchestrator.io.mxu_weight

  mxu.io.weight_update :=
    orchestrator.io.weight_update_row

  mxu.io.clear_W :=
    io.clear_W


  // ==========================================================================
  // MXU -> Compute Timer
  //
  // Column 0 is the logical timing reference.
  //
  // MXU out_valid is frozen during stall.
  // Timer additionally receives stall, so one held valid item is counted
  // exactly once.
  // ==========================================================================

  timer.io.row0_valid :=
    mxu.io.out_valid(0)

  timer.io.intermNum :=
    io.intermNum

  timer.io.colNum :=
    io.colNum

  timer.io.norm_phase_load :=
    io.norm_phase_load


  // ==========================================================================
  // MXU -> Accumulator
  //
  // accum_en:
  //
  //   Actual MXU data validity itself is the accumulation enable.
  //
  // Logical K-tile timing comes from ComputeTimer.
  //
  // Physical column skew is handled inside TileAccumulator.
  // ==========================================================================

  accumulator.io.in_vec :=
    mxu.io.out_MAC


  accumulator.io.accum_en :=
    mxu.io.out_valid(0)


  accumulator.io.accum_first :=
    timer.io.accum_first


  accumulator.io.accum_snapshot :=
    timer.io.output_tile_done


  // ==========================================================================
  // Metadata queue
  //
  // ComputeTimer generates metadata when column 0 completes an output tile.
  //
  // Actual flat output becomes available later, after column 15 snapshot
  // completion.
  //
  // Therefore metadata must be preserved between those two physical events.
  // ==========================================================================

  val metaQueue =
    Module(
      new Queue(
        new TPUStreamMeta,
        metaQueueDepth
      )
    )


  val generatedMeta =
    Wire(
      new TPUStreamMeta
    )


  generatedMeta.param_update :=
    timer.io.param_update

  generatedMeta.fusion_change :=
    timer.io.fusion_change

  generatedMeta.norm_phase_change :=
    timer.io.norm_phase_change


  metaQueue.io.enq.valid :=
    timer.io.output_tile_done

  metaQueue.io.enq.bits :=
    generatedMeta


  // ==========================================================================
  // Automatic accumulator output streaming
  //
  // snapshot_done:
  //
  //   final physical column commits pong on this edge
  //
  // After that edge:
  //
  //   read row0
  //   read row1
  //   ...
  //   read row15
  // ==========================================================================

  val streamRowBits =
    math.max(
      1,
      log2Ceil(numRows)
    )


  val streamActive =
    RegInit(false.B)


  val streamRow =
    RegInit(
      0.U(streamRowBits.W)
    )


  val activeMeta =
    RegInit(
      0.U.asTypeOf(
        new TPUStreamMeta
      )
    )


  // --------------------------------------------------------------------------
  // Metadata dequeue
  //
  // Pop exactly when a completed accumulator tile is about to begin
  // de-skewed streaming.
  // --------------------------------------------------------------------------

  metaQueue.io.deq.ready :=
    run &&
    !streamActive &&
    accumulator.io.snapshot_done


  when(run) {

    when(!streamActive) {

      when(
        accumulator.io.snapshot_done &&
        metaQueue.io.deq.valid
      ) {

        activeMeta :=
          metaQueue.io.deq.bits

        streamActive :=
          true.B

        streamRow :=
          0.U
      }

    }.otherwise {

      when(
        streamRow ===
        (numRows - 1).U
      ) {

        streamActive :=
          false.B

        streamRow :=
          0.U

      }.otherwise {

        streamRow :=
          streamRow + 1.U
      }
    }
  }


  accumulator.io.accum_stream_en :=
    streamActive


  // ==========================================================================
  // Accumulator -> VPU
  // ==========================================================================

  io.out_accum :=
    accumulator.io.out_vec

  io.out_valid :=
    accumulator.io.out_valid


  // ==========================================================================
  // Metadata attachment
  //
  // Event metadata is emitted only with the FIRST vector of the output tile.
  //
  // Example:
  //
  //   Y0 row0 + param_update
  //   Y0 row1
  //   ...
  //   Y0 row15
  //
  // ==========================================================================

  val firstOutputRow =
    run &&
    streamActive &&
    (streamRow === 0.U)


  io.out_meta.param_update :=
    firstOutputRow &&
    activeMeta.param_update


  io.out_meta.fusion_change :=
    firstOutputRow &&
    activeMeta.fusion_change


  io.out_meta.norm_phase_change :=
    firstOutputRow &&
    activeMeta.norm_phase_change


  // ==========================================================================
  // DFD
  // ==========================================================================

  // Metadata generated faster than downstream can consume.
  val metaOverflow =
    timer.io.output_tile_done &&
    !metaQueue.io.enq.ready


  // Accumulator completed a tile but its metadata does not exist.
  val metaUnderflow =
    accumulator.io.snapshot_done &&
    !streamActive &&
    !metaQueue.io.deq.valid


  // IMPORTANT:
  //
  // Current accumulator has one working ping bank and one output pong bank.
  //
  // Therefore a new pong snapshot must NOT overwrite pong while the previous
  // output tile is still being streamed.
  //
  // This can happen for extremely short accumulation intervals such as
  // intermNum == 1.
  //
  // Keep this as a fatal condition until the accumulator is upgraded to
  // true dual-role ping/pong bank swapping.
  val pongOverwriteHazard =
    accumulator.io.snapshot_done &&
    streamActive


  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert ||
    accumulator.io.accum_alert ||
    metaOverflow ||
    metaUnderflow ||
    pongOverwriteHazard
}