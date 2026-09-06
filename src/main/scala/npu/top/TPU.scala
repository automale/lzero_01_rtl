package npu.top

import chisel3._
import chisel3.util._
import npu.core._

// ============================================================================
// Autonomous TPU
//
// Input / Weight
//      │
//      ▼
// DataOrch
//      │
//      ▼
//     MXU ───────────────► ComputeTimer
//      │                       │
//      │                  accum_first
//      │                  output_tile_done
//      ▼                       │
// TileAccumulator ◄────────────┘
//      │
//      │ flat INT32 vector + dynamic metadata
//      ▼
//     VPU
//
// Dynamic metadata:
//   param_update
//   fusion_req
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
    // A[M,K]
    val in_input = Input(Vec(numRows, SInt(inBits.W)))
    val input_valid = Input(Bool())
    val input_tile_start = Input(Bool())

    // W[N,K]
    val in_weight = Input(Vec(numRows, SInt(inBits.W)))
    val weight_valid = Input(Bool())
    val clear_W = Input(Bool())

    // Operation configuration
    val intermNum = Input(UInt(32.W))
    val colNum = Input(UInt(32.W))

    // Global control
    val stall = Input(Bool())

    // Flat TPU output
    val out_accum = Output(Vec(numCols, SInt(accBits.W)))
    val out_valid = Output(Vec(numCols, Bool()))

    // Dynamic in-band metadata
    val out_meta = Output(new TPUStreamMeta)

    // DFD
    val fatal_alert = Output(Bool())
  })

  // ==========================================================================
  // Submodules
  // ==========================================================================
  val orchestrator = Module(new DataOrchUnit(
    numRows = numRows,
    numCols = numCols,
    dataBits = inBits
  ))

  val mxu = Module(new MXU(
    numRows = numRows,
    numCols = numCols,
    inBits = inBits,
    accBits = accBits
  ))

  val timer = Module(new ComputeTimer(
    tileSize = numRows
  ))

  val accumulator = Module(new TileAccumulator(
    numCols = numCols,
    numLines = numRows,
    dataBits = accBits
  ))

  val metaQueue = Module(new Queue(
    new TPUStreamMeta,
    metaQueueDepth
  ))

  // ==========================================================================
  // Global stall
  // ==========================================================================
  orchestrator.io.stall := io.stall
  mxu.io.stall := io.stall
  timer.io.stall := io.stall
  accumulator.io.stall := io.stall

  // ==========================================================================
  // Input -> Orchestrator
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
  mxu.io.in_valid := orchestrator.io.mxu_input_valid
  mxu.io.in_shadow_W := orchestrator.io.mxu_weight
  mxu.io.weight_update := orchestrator.io.weight_update_row
  mxu.io.clear_W := io.clear_W

  // ==========================================================================
  // MXU -> ComputeTimer
  //
  // Column 0 is the logical timing reference.
  // ==========================================================================
  timer.io.row0_valid := mxu.io.out_valid(0)
  timer.io.intermNum := io.intermNum
  timer.io.colNum := io.colNum

  // ==========================================================================
  // MXU / ComputeTimer -> Accumulator
  //
  // accum_en:
  //   MXU column0 valid itself is the accumulation enable.
  //
  // accum_first / accum_snapshot:
  //   logical timing comes from ComputeTimer.
  //
  // Physical column skew and output streaming are handled completely inside
  // TileAccumulator.
  // ==========================================================================
  accumulator.io.in_vec := mxu.io.out_MAC
  accumulator.io.accum_en := mxu.io.out_valid(0)
  accumulator.io.accum_first := timer.io.accum_first
  accumulator.io.accum_snapshot := timer.io.output_tile_done

  // ==========================================================================
  // Metadata generation
  //
  // Metadata is generated when the final K tile / final row reaches MXU col0.
  // The actual flat output begins later, after the final physical accumulator
  // column has committed the completed bank.
  // Therefore metadata is queued until out_tile_start.
  // ==========================================================================
  val generatedMeta = Wire(new TPUStreamMeta)

  generatedMeta.param_update := timer.io.param_update
  generatedMeta.fusion_req := timer.io.fusion_req
  generatedMeta.norm_phase_change := timer.io.norm_phase_change

  metaQueue.io.enq.valid := timer.io.output_tile_done
  metaQueue.io.enq.bits := generatedMeta

  // ==========================================================================
  // Accumulator -> TPU output
  //
  // TileAccumulator internally performs:
  //
  //   bank0 / bank1 role swapping
  //   de-skew
  //   automatic 16-row streaming
  //   zero-bubble handoff when intermNum == 1
  // ==========================================================================
  io.out_accum := accumulator.io.out_vec
  io.out_valid := accumulator.io.out_valid

  // ==========================================================================
  // Metadata attachment
  //
  // Metadata is attached only to row0 of each completed output tile.
  //
  //   row0  : data + metadata
  //   row1  : data
  //   ...
  //   row15 : data
  //
  // out_tile_start is stall-aware because it is generated by TileAccumulator.
  // ==========================================================================
  val outputTileStart = accumulator.io.out_tile_start

  metaQueue.io.deq.ready := outputTileStart

  io.out_meta.param_update :=
    outputTileStart &&
    metaQueue.io.deq.valid &&
    metaQueue.io.deq.bits.param_update

  io.out_meta.fusion_req :=
    outputTileStart &&
    metaQueue.io.deq.valid &&
    metaQueue.io.deq.bits.fusion_req

  io.out_meta.norm_phase_change :=
    outputTileStart &&
    metaQueue.io.deq.valid &&
    metaQueue.io.deq.bits.norm_phase_change

  // ==========================================================================
  // DFD
  // ==========================================================================
  val metaOverflow =
    timer.io.output_tile_done &&
    !metaQueue.io.enq.ready

  val metaUnderflow =
    outputTileStart &&
    !metaQueue.io.deq.valid

  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert ||
    accumulator.io.accum_alert ||
    metaOverflow ||
    metaUnderflow
}