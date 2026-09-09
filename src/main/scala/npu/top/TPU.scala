package npu.top

import chisel3._
import chisel3.util._
import npu.core._

class TPU_top(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32,
  val tileMetaQueueDepth: Int = 4
) extends Module {

  require(numRows > 0)
  require(numCols > 0)
  require(tileMetaQueueDepth > 0)

  val io = IO(new Bundle {
    val in_input = Input(Vec(numRows, SInt(inBits.W)))
    val input_valid = Input(Bool())
    val input_tile_start = Input(Bool())

    val in_weight = Input(Vec(numRows, SInt(inBits.W)))
    val weight_valid = Input(Bool())
    val clear_W = Input(Bool())

    val intermNum = Input(UInt(32.W))
    val outColNum = Input(UInt(32.W))

    val stall = Input(Bool())

    val out_accum = Output(Vec(numCols, SInt(accBits.W)))
    val out_valid = Output(Vec(numCols, Bool()))

    val out_meta = Output(new TPUStreamMeta)
    val fusion_req = Output(Bool())

    val fatal_alert = Output(Bool())
  })

  val orchestrator =
    Module(new DataOrchUnit(
      numRows = numRows,
      numCols = numCols,
      dataBits = inBits
    ))

  val mxu =
    Module(new MXU(
      numRows = numRows,
      numCols = numCols,
      inBits = inBits,
      accBits = accBits
    ))

  val timer =
    Module(new ComputeTimer(tileSize = numRows))

  val accumulator =
    Module(new TileAccumulator(
      numCols = numCols,
      numLines = numRows,
      dataBits = accBits
    ))

  val tileCtrlQueue =
    Module(new Queue(new TPUTileCtrl, tileMetaQueueDepth))

  orchestrator.io.stall := io.stall
  mxu.io.stall := io.stall
  timer.io.stall := io.stall
  accumulator.io.stall := io.stall

  orchestrator.io.in_input := io.in_input
  orchestrator.io.input_valid := io.input_valid
  orchestrator.io.input_tile_start := io.input_tile_start
  orchestrator.io.in_weight := io.in_weight
  orchestrator.io.weight_valid := io.weight_valid

  mxu.io.in_X := orchestrator.io.mxu_input
  mxu.io.in_valid := orchestrator.io.mxu_input_valid
  mxu.io.in_shadow_W := orchestrator.io.mxu_weight
  mxu.io.weight_update := orchestrator.io.weight_update_row
  mxu.io.clear_W := io.clear_W

  timer.io.row0_valid := mxu.io.out_valid(0)
  timer.io.intermNum := io.intermNum
  timer.io.outColNum := io.outColNum

  accumulator.io.in_vec := mxu.io.out_MAC
  accumulator.io.accum_en := mxu.io.out_valid(0)
  accumulator.io.accum_first := timer.io.accum_first
  accumulator.io.accum_snapshot := timer.io.output_tile_done

  val generatedTileCtrl = Wire(new TPUTileCtrl)
  generatedTileCtrl.row_change_update := timer.io.row_change_update
  generatedTileCtrl.fusion_req := timer.io.fusion_req

  tileCtrlQueue.io.enq.valid := timer.io.output_tile_done
  tileCtrlQueue.io.enq.bits := generatedTileCtrl

  io.out_accum := accumulator.io.out_vec
  io.out_valid := accumulator.io.out_valid

  val snapshotDone = accumulator.io.snapshot_done
  val outputTileStart = accumulator.io.out_tile_start

  io.fusion_req :=
    snapshotDone &&
    tileCtrlQueue.io.deq.valid &&
    tileCtrlQueue.io.deq.bits.fusion_req

  io.out_meta.row_change_update :=
    outputTileStart &&
    tileCtrlQueue.io.deq.valid &&
    tileCtrlQueue.io.deq.bits.row_change_update

  tileCtrlQueue.io.deq.ready := outputTileStart

  val tileMetaOverflow =
    timer.io.output_tile_done &&
    !tileCtrlQueue.io.enq.ready

  val tileMetaUnderflow =
    (snapshotDone || outputTileStart) &&
    !tileCtrlQueue.io.deq.valid

  io.fatal_alert :=
    orchestrator.io.orch_alert ||
    mxu.io.mxu_alert ||
    accumulator.io.accum_alert ||
    tileMetaOverflow ||
    tileMetaUnderflow
}
