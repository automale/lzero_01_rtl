package npu.top

import circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import npu.core._

// [1] Tensor processing unit
// Orchestrator + MXU + Ping-Pong Accumulator
class TPU_top(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 16,
  val outBits: Int = 32
) extends Module {
  val io = IO(new Bundle {
    // 1. Data Ports (From BRAM/DMA or OCM Router)
    val in_input          = Input(Vec(numCols, UInt(inBits.W)))
    val in_weight         = Input(Vec(numCols, UInt(inBits.W)))

    // 2. Control Signals
    val feed_enable       = Input(Bool())
    val load_enable       = Input(Bool())
    val clear_W           = Input(Bool())
    
    // ---  Ping-Pong Accumulator 전용 컨트롤 신호 노출 ---
    val accum_en          = Input(Bool()) // 누적 진행 여부
    val accum_first       = Input(Bool()) // 첫 타일 플래그 (기존 Clear 역할 대체)
    val accum_snapshot    = Input(Bool()) // 타일 연산 완료 시점 스냅샷 펄스
    val accum_stream_en   = Input(Bool()) // VPU로 데이터 배출 시작
    
    val stall             = Input(Bool())

    // 3. Output to VPU (32-bit Vector)
    val out_accum         = Output(Vec(numCols, UInt(outBits.W)))
    val out_valid         = Output(Vec(numCols, Bool()))
    
    // 4. DFD state alert
    val fatal_alert       = Output(Bool()) 
  })

  // ====================================================================
  // [1] Sub-Units 인스턴스화
  // ====================================================================
  val orchestrator = Module(new DataOrchUnit(numRows, numCols, inBits))
  val mxu          = Module(new MXU(numRows, numCols, inBits, accBits))
  
  // 변경된 파라미터 네이밍(numLines)에 맞추어 인스턴스화
  val accumulator  = Module(new TileAccumulator(
    numCols = numCols, 
    numLines = numRows, 
    inBits = accBits, 
    outBits = outBits
  ))
  
  // ====================================================================
  // [2] Global Control 브로드캐스팅
  // ====================================================================
  orchestrator.io.stall := io.stall
  mxu.io.stall          := io.stall
  accumulator.io.stall  := io.stall

  // ====================================================================
  // [3] Data Path & Local Control Wiring
  // ====================================================================
  // [A] Outer IO <-> Orchestrator
  orchestrator.io.in_input    := io.in_input
  orchestrator.io.in_weight   := io.in_weight
  orchestrator.io.feed_enable := io.feed_enable
  orchestrator.io.load_enable := io.load_enable

  // [B] Orchestrator <-> MXU (Systolic Array)
  mxu.io.in_X      := orchestrator.io.mxu_input
  mxu.io.in_W      := orchestrator.io.mxu_weight
  mxu.io.clear_W   := io.clear_W
  mxu.io.set_W     := orchestrator.io.feed_row

  // [C] MXU <-> Accumulator
  accumulator.io.in_vec          := mxu.io.out_MAC
  
  // Skewed/Flat 컨트롤 신호 1:1 맵핑
  accumulator.io.accum_en        := io.accum_en
  accumulator.io.accum_first     := io.accum_first
  accumulator.io.accum_snapshot  := io.accum_snapshot
  accumulator.io.accum_stream_en := io.accum_stream_en

  // [D] Accumulator <-> Final Output (To VPU)
  io.out_accum  := accumulator.io.out_vec
  io.out_valid  := accumulator.io.out_valid

  // ====================================================================
  // [4] 에러 및 상태 취합 (Aggregation)
  // ====================================================================
  io.fatal_alert := orchestrator.io.orch_alert || mxu.io.mxu_alert || accumulator.io.accum_alert
}