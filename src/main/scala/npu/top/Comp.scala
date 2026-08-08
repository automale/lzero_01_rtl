package npu.top

import chisel3._
import chisel3.util._
import npu.core._

// [Top] 3대 연산 유닛(TPU, VPU1, VPU2) + Input/Output Transposer를 엮는 Compute Core Complex
class ComputeUnit(val numLines: Int = 16) extends Module {
  val io = IO(new Bundle {
    // =======================================================
    // 1. OCM Data Inputs (128-bit / 16x8-bit)
    // =======================================================
    val ub_in         = Input(Vec(numLines, UInt(8.W)))
    val wb_in         = Input(Vec(numLines, UInt(8.W)))
    val vb_in         = Input(Vec(numLines, UInt(8.W)))
    val nb_in         = Input(Vec(numLines, UInt(8.W)))
    val pb_in         = Input(UInt(256.W)) // 파라미터 버스

    val ub_valid      = Input(Bool())
    val wb_valid      = Input(Bool())
    val vb_valid      = Input(Bool())
    val nb_valid      = Input(Bool())

    // =======================================================
    // 2. Transposer Control Signals (New!)
    // =======================================================
    val ub_transpose_en = Input(Bool()) // 1: Transpose (Col Read), 0: Bypass (Row Read)
    val wb_transpose_en = Input(Bool()) // 1: Transpose (Col Read), 0: Bypass (Row Read)
    
    val ub_stream_en    = Input(Bool()) // DMA/Control이 UB Transposer에서 읽어갈 때
    val wb_stream_en    = Input(Bool()) // DMA/Control이 WB Transposer에서 읽어갈 때
    val comp_stream_en  = Input(Bool()) // DMA/Control이 Out Transposer에서 읽어갈 때

    val ub_trans_ready  = Output(Bool())
    val wb_trans_ready  = Output(Bool())
    val comp_trans_ready= Output(Bool()) // Transposer가 새로운 타일을 받을 수 있는지 여부

    // =======================================================
    // 3. OCM / DMA Data Outputs (128-bit / 16x8-bit)
    // =======================================================
    val vb_out        = Output(Vec(numLines, UInt(8.W)))
    val nb_out        = Output(Vec(numLines, UInt(8.W)))
    val compute_out   = Output(Vec(numLines, UInt(8.W))) // 최종 배출구

    val vb_out_valid  = Output(Bool())
    val nb_out_valid  = Output(Bool())
    val compute_out_valid = Output(Bool())

    // =======================================================
    // 4. Routing MUX / DEMUX Selectors
    // =======================================================
    val sel_vpu1_op1  = Input(Bool())    // 0: ub_data(zext), 1: TPU out
    val sel_vpu1_op2  = Input(Bool())    // 0: wb_data(zext), 1: vb_in(zext)
    val demux_vpu1_out= Input(UInt(2.W)) // 0: None, 1: vb_out, 2: vpu2_in, 3: compute_out
    val sel_vpu2_in   = Input(UInt(2.W)) // 0: nb_in, 1: ub_data, 2: vpu1_out
    
    val sel_comp_in   = Input(Bool())    // 0: VPU2 out, 1: VPU1 out(Demux에서 옴)
    val transpose     = Input(Bool())    // 1: Bypass(원래 다이어그램 스펙), 0: Transpose

    // =======================================================
    // 5. TPU & VPU Control Signals
    // =======================================================
    val tpu_feed_en      = Input(Bool())
    val tpu_load_en      = Input(Bool())
    val tpu_clear_w      = Input(Bool())
    val tpu_accum_en     = Input(Bool())
    val tpu_accum_first  = Input(Bool())
    val tpu_accum_snap   = Input(Bool())
    val tpu_accum_stream = Input(Bool())

    val vpu1_alu_mode   = Input(UInt(2.W))
    val vpu1_qa_en      = Input(Bool())
    val vpu2_norm_mode  = Input(UInt(2.W))
    val vpu2_norm_phase = Input(Bool())
    val vpu2_clr_acc    = Input(Bool())
    val vpu2_rope_en    = Input(Bool())

    // =======================================================
    // 6. Parameter & LUT Control
    // =======================================================
    val param_wr_en     = Input(Bool())
    val param_addr      = Input(UInt(log2Ceil(numLines).max(1).W))
    val param_update    = Input(Bool())
    val quant_lut_wr_en = Input(Bool())
    val norm_lut_wr_en  = Input(Bool())
    val norm_lut_is_exp = Input(Bool())
    val rope_cos_wr_en  = Input(Bool())
    val rope_sin_wr_en  = Input(Bool())
    val lut_wr_addr     = Input(UInt(8.W))

    // =======================================================
    // 7. Global Controls & DFD
    // =======================================================
    val stall         = Input(Bool())
    val lut_ready     = Output(Bool())
    val fatal_alert   = Output(Bool())
  })

  // ====================================================================
  // [1] Sub-Units 인스턴스화 (Transposer 통합)
  // ====================================================================
  val tpu            = Module(new TPU_top(numRows = numLines, numCols = numLines, inBits = 8, accBits = 16, outBits = 32))
  val vpu1           = Module(new VPU_Stage1(numLines = numLines))
  val vpu2           = Module(new VPU_Stage2(numLines = numLines))
  
  val ub_transposer  = Module(new PingPongTransposer(numLines = numLines, dataBits = 8))
  val wb_transposer  = Module(new PingPongTransposer(numLines = numLines, dataBits = 8))
  val out_transposer = Module(new PingPongTransposer(numLines = numLines, dataBits = 8))

  // --- Zext Helper (8-bit -> 32-bit) ---
  def zext8to32(in: Vec[UInt]): Vec[UInt] = {
    val out = Wire(Vec(numLines, UInt(32.W)))
    for (i <- 0 until numLines) { out(i) := Cat(0.U(24.W), in(i)) }
    out
  }

  // ====================================================================
  // [2] Parameter Buffer 슬라이싱
  // ====================================================================
  val pb_as_8x32  = io.pb_in.asTypeOf(Vec(32, UInt(8.W)))
  val pb_as_16x16 = io.pb_in.asTypeOf(Vec(16, UInt(16.W)))

  vpu1.io.param_quant := io.pb_in(31, 0)
  vpu1.io.lut_wr_data := pb_as_8x32
  vpu2.io.param_rope  := io.pb_in(15, 0)
  vpu2.io.lut_wr_data := pb_as_16x16

  // ====================================================================
  // [3] Control 브로드캐스팅 및 Transposer 맵핑
  // ====================================================================
  tpu.io.stall            := io.stall
  vpu1.io.stall           := io.stall
  vpu2.io.stall           := io.stall
  
  ub_transposer.io.stall  := io.stall
  wb_transposer.io.stall  := io.stall
  out_transposer.io.stall := io.stall

  tpu.io.feed_enable      := io.tpu_feed_en
  tpu.io.load_enable      := io.tpu_load_en
  tpu.io.clear_W          := io.tpu_clear_w
  tpu.io.accum_en         := io.tpu_accum_en
  tpu.io.accum_first      := io.tpu_accum_first
  tpu.io.accum_snapshot   := io.tpu_accum_snap
  tpu.io.accum_stream_en  := io.tpu_accum_stream

  vpu1.io.alu_mode        := io.vpu1_alu_mode
  vpu1.io.qa_en           := io.vpu1_qa_en
  vpu1.io.param_wr_en     := io.param_wr_en
  vpu1.io.param_addr      := io.param_addr
  vpu1.io.param_update    := io.param_update
  vpu1.io.quant_lut_wr_en := io.quant_lut_wr_en
  vpu1.io.lut_wr_addr     := io.lut_wr_addr

  vpu2.io.norm_mode       := io.vpu2_norm_mode
  vpu2.io.norm_phase      := io.vpu2_norm_phase
  vpu2.io.clr_acc         := io.vpu2_clr_acc
  vpu2.io.rope_en         := io.vpu2_rope_en
  vpu2.io.param_wr_en     := io.param_wr_en
  vpu2.io.param_addr      := io.param_addr
  vpu2.io.param_update    := io.param_update
  vpu2.io.norm_lut_wr_en  := io.norm_lut_wr_en
  vpu2.io.norm_lut_is_exp := io.norm_lut_is_exp
  vpu2.io.rope_cos_wr_en  := io.rope_cos_wr_en
  vpu2.io.rope_sin_wr_en  := io.rope_sin_wr_en
  vpu2.io.lut_wr_addr     := io.lut_wr_addr

  // ====================================================================
  // [4] Data Path Routing (Input Transposers)
  // ====================================================================
  
  // --- [UB Transposer Muxing] ---
  ub_transposer.io.in_vec        := io.ub_in
  ub_transposer.io.in_valid      := io.ub_valid
  ub_transposer.io.transpose     := io.ub_transpose_en
  ub_transposer.io.out_stream_en := io.ub_stream_en
  io.ub_trans_ready              := ub_transposer.io.ready
  
  val ub_data  = ub_transposer.io.out_vec
  val ub_valid = ub_transposer.io.out_valid

  // --- [WB Transposer Muxing] ---
  wb_transposer.io.in_vec        := io.wb_in
  wb_transposer.io.in_valid      := io.wb_valid
  wb_transposer.io.transpose     := io.wb_transpose_en
  wb_transposer.io.out_stream_en := io.wb_stream_en
  io.wb_trans_ready              := wb_transposer.io.ready

  val wb_data  = wb_transposer.io.out_vec
  val wb_valid = wb_transposer.io.out_valid

  // --- [TPU Inputs] ---
  tpu.io.in_input  := ub_data
  tpu.io.in_weight := wb_data

  // --- [VPU1 Input MUXing] ---
  vpu1.io.operand1 := Mux(io.sel_vpu1_op1, tpu.io.out_accum, zext8to32(ub_data))
  for (i <- 0 until numLines) {
    vpu1.io.in_valid(i) := Mux(io.sel_vpu1_op1, tpu.io.out_valid(i), ub_valid)
  }
  vpu1.io.operand2 := zext8to32(Mux(io.sel_vpu1_op2, io.vb_in, wb_data))

  // --- [VPU1 Output DEMUXing] ---
  val vpu1_out_data  = vpu1.io.out_vec
  val vpu1_out_valid = vpu1.io.out_valid

  val en_to_vb = io.demux_vpu1_out === 1.U
  io.vb_out := Mux(en_to_vb, vpu1_out_data, VecInit(Seq.fill(numLines)(0.U(8.W))))
  io.vb_out_valid := en_to_vb && vpu1_out_valid(0)

  val en_to_vpu2 = io.demux_vpu1_out === 2.U
  val vpu1_to_vpu2_data = Mux(en_to_vpu2, vpu1_out_data, VecInit(Seq.fill(numLines)(0.U(8.W))))
  val vpu1_to_vpu2_valid = en_to_vpu2 && vpu1_out_valid(0)

  val en_to_comp = io.demux_vpu1_out === 3.U
  val vpu1_to_comp_data = Mux(en_to_comp, vpu1_out_data, VecInit(Seq.fill(numLines)(0.U(8.W))))
  val vpu1_to_comp_valid = en_to_comp && vpu1_out_valid(0)

  // --- [VPU2 Input 3-Way MUXing] ---
  vpu2.io.in_vec := MuxLookup(io.sel_vpu2_in, io.nb_in)(Seq(
    0.U -> io.nb_in,
    1.U -> ub_data,
    2.U -> vpu1_to_vpu2_data
  ))
  val vpu2_valid_mux = MuxLookup(io.sel_vpu2_in, io.nb_valid)(Seq(
    0.U -> io.nb_valid,
    1.U -> ub_valid,
    2.U -> vpu1_to_vpu2_valid
  ))
  for (i <- 0 until numLines) { vpu2.io.in_valid(i) := vpu2_valid_mux }

  // --- [VPU2 Output -> NB (Phase 1)] ---
  io.nb_out       := vpu2.io.out_norm_p1
  io.nb_out_valid := vpu2.io.out_norm_p1_valid(0)

  // ====================================================================
  // [5] Output Transposer MUXing (코너 터닝 통과 vs 우회 통합)
  // ====================================================================
  
  // 1. Pre-Transposer MUX (VPU1 vs VPU2 선택)
  val pre_trans_data  = Mux(io.sel_comp_in, vpu1_to_comp_data, vpu2.io.out_rope)
  val pre_trans_valid = Mux(io.sel_comp_in, vpu1_to_comp_valid, vpu2.io.out_rope_valid(0))

  // 2. Transposer 입력 및 Invert 매핑
  out_transposer.io.in_vec        := pre_trans_data
  out_transposer.io.in_valid      := pre_trans_valid
  
  // io.transpose가 1(Bypass)이면 내부 모듈의 transpose 핀에는 0(Row Read)이 들어가도록 반전
  out_transposer.io.transpose     := !io.transpose 
  out_transposer.io.out_stream_en := io.comp_stream_en

  // 3. Final Compute Out (단일 모듈로 통합되어 더 이상 Mux가 필요 없음)
  io.compute_out       := out_transposer.io.out_vec
  io.compute_out_valid := out_transposer.io.out_valid
  io.comp_trans_ready  := out_transposer.io.ready

  // ====================================================================
  // [6] DFD (디버그 및 에러 상태 취합)
  // ====================================================================
  io.lut_ready   := vpu1.io.lut_ready && vpu2.io.lut_ready
  
  val trans_alerts = ub_transposer.io.trans_alert || wb_transposer.io.trans_alert || out_transposer.io.trans_alert
  io.fatal_alert := tpu.io.fatal_alert || vpu1.io.fatal_alert || vpu2.io.fatal_alert || trans_alerts
}