package npu.top

import chisel3._
import chisel3.util._
import npu.core._

class TPU_top(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32,
  val tileMetaQueueDepth: Int = 4,
  val rowMetaQueueDepth: Int = 32
) extends Module {

  require(numRows > 0)
  require(numCols > 0)
  require(tileMetaQueueDepth > 0)

  // K=1 continuous operation에서 output stream이 시작되기 전에
  // 다음 tile의 row metadata까지 들어오기 때문에 32-entry 권장.
  require(rowMetaQueueDepth >= 32)

  val io = IO(new Bundle {
    // A[M,K]
    val in_input =
      Input(Vec(numRows, SInt(inBits.W)))

    val input_valid =
      Input(Bool())

    val input_tile_start =
      Input(Bool())

    // W[N,K]
    val in_weight =
      Input(Vec(numRows, SInt(inBits.W)))

    val weight_valid =
      Input(Bool())

    val clear_W =
      Input(Bool())

    // Configuration
    val intermNum =
      Input(UInt(32.W))

    val quantParamTilePeriod =
      Input(UInt(32.W))

    // Global stall
    val stall =
      Input(Bool())

    // Flat accumulator output
    val out_accum =
      Output(Vec(numCols, SInt(accBits.W)))

    val out_valid =
      Output(Vec(numCols, Bool()))

    // Data-aligned metadata
    val out_meta =
      Output(new TPUStreamMeta)

    // VB lookahead request
    val fusion_req =
      Output(Bool())

    val fatal_alert =
      Output(Bool())
  })

  // ==========================================================================
  // Modules
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

  // --------------------------------------------------------------------------
  // Tile metadata
  //
  // quant_param_update + fusion_req are output-tile-level events.
  // --------------------------------------------------------------------------
  val tileCtrlQueue =
    Module(
      new Queue(
        new TPUTileCtrl,
        tileMetaQueueDepth
      )
    )

  // --------------------------------------------------------------------------
  // Row metadata
  //
  // RoPE logical-position update occurs once per output row.
  //
  // K=1 continuous schedule can build ~31 outstanding row tags before the
  // first accumulator output begins, therefore depth >= 32.
  // --------------------------------------------------------------------------
  val ropeRowMetaQueue =
    Module(
      new Queue(
        Bool(),
        rowMetaQueueDepth
      )
    )

  // ==========================================================================
  // Stall
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
  // Input -> Orchestrator
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
  // MXU -> ComputeTimer
  // ==========================================================================
  timer.io.row0_valid :=
    mxu.io.out_valid(0)

  timer.io.intermNum :=
    io.intermNum

  timer.io.quantParamTilePeriod :=
    io.quantParamTilePeriod

  // ==========================================================================
  // MXU / ComputeTimer -> Accumulator
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
  // Tile-level metadata enqueue
  // ==========================================================================
  val generatedTileCtrl =
    Wire(new TPUTileCtrl)

  generatedTileCtrl.quant_param_update :=
    timer.io.quant_param_update

  generatedTileCtrl.fusion_req :=
    timer.io.fusion_req

  tileCtrlQueue.io.enq.valid :=
    timer.io.output_tile_done

  tileCtrlQueue.io.enq.bits :=
    generatedTileCtrl

  // ==========================================================================
  // Row-level RoPE metadata enqueue
  // ==========================================================================
  ropeRowMetaQueue.io.enq.valid :=
    timer.io.rope_param_update

  ropeRowMetaQueue.io.enq.bits :=
    timer.io.rope_param_update

  // ==========================================================================
  // Accumulator output
  // ==========================================================================
  io.out_accum :=
    accumulator.io.out_vec

  io.out_valid :=
    accumulator.io.out_valid

  val snapshotDone =
    accumulator.io.snapshot_done

  val outputTileStart =
    accumulator.io.out_tile_start

  val outputRowValid =
    accumulator.io.out_valid(0)

  // ==========================================================================
  // Fusion lookahead
  //
  // snapshot_done
  //     ↓
  // fusion_req
  //     ↓ 1 accepted cycle
  // output row0
  // ==========================================================================
  io.fusion_req :=
    snapshotDone &&
    tileCtrlQueue.io.deq.valid &&
    tileCtrlQueue.io.deq.bits.fusion_req

  // ==========================================================================
  // Quant metadata
  //
  // Quant update is an output-tile-level event and is attached to row0.
  // ==========================================================================
  io.out_meta.quant_param_update :=
    outputTileStart &&
    tileCtrlQueue.io.deq.valid &&
    tileCtrlQueue.io.deq.bits.quant_param_update

  tileCtrlQueue.io.deq.ready :=
    outputTileStart

  // ==========================================================================
  // RoPE metadata
  //
  // Exactly one row-tag is consumed for every flat output row.
  // ==========================================================================
  io.out_meta.rope_param_update :=
    outputRowValid &&
    ropeRowMetaQueue.io.deq.valid &&
    ropeRowMetaQueue.io.deq.bits

  ropeRowMetaQueue.io.deq.ready :=
    outputRowValid

  // ==========================================================================
  // DFD
  // ==========================================================================
  val tileMetaOverflow =
    timer.io.output_tile_done &&
    !tileCtrlQueue.io.enq.ready

  val tileMetaUnderflow =
    (snapshotDone || outputTileStart) &&
    !tileCtrlQueue.io.deq.valid

  val rowMetaOverflow =
    timer.io.rope_param_update &&
    !ropeRowMetaQueue.io.enq.ready

  val rowMetaUnderflow =
    outputRowValid &&
    !ropeRowMetaQueue.io.deq.valid

  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert ||
    accumulator.io.accum_alert ||
    tileMetaOverflow ||
    tileMetaUnderflow ||
    rowMetaOverflow ||
    rowMetaUnderflow
}