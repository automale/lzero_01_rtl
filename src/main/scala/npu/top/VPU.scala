package npu.top

import chisel3._
import chisel3.core._

class VPU_Stage1(
  // line number
  val numLines: Int = 16,

  // data width
  val alu_inBits: Int = 32,
  val alu_outBits: Int = 32,
  val quant_inBits: Int = 32,
  val quant_outBits: Int = 8,

  // lut
  val writeBits: Int = 256,
  val quant_indexBits: Int = 10
) extends Module {

  val lut_entry_num = writeBits / quant_outBits
  
  val io = IO(new Bundle {
    // 1. 외부 Router에서 인가되는 32-bit Data Ports
    val operand1     = Input(Vec(numLines, UInt(32.W)))
    val operand2     = Input(Vec(numLines, UInt(32.W)))
    val in_valid     = Input(Vec(numLines, Bool()))

    // 2. Stage 1 출력 (VB 또는 Router로 향함)
    val out_vec      = Output(Vec(numLines, UInt(8.W)))
    val out_valid    = Output(Vec(numLines, Bool()))

    // 3. 제어 및 상태 신호
    val alu_mode     = Input(UInt(2.W)) // 0:Bypass, 1:Add, 2:Mul
    val qa_en        = Input(Bool())    // 1:QuantAct 가동, 0:Bypass
    val stall        = Input(Bool())

    // 4. QuantAct 전용 파라미터 및 LUT 포트 (8-bit Data)
    val param_wr_en  = Input(Bool())
    val param_addr   = Input(UInt(log2Ceil(numLines).max(1).W))
    val param_quant  = Input(UInt(32.W)) 
    val param_update = Input(Bool())

    val quant_lut_wr_en = Input(Bool())
    val lut_wr_addr     = Input(UInt(8.W))
    val lut_wr_data     = Input(Vec(lut_entry_num, UInt(quant_outBits.W)))

    // 5. DFD (Design for Debug)
    val lut_ready    = Output(Bool())
    val fatal_alert  = Output(Bool())
  })

  // ====================================================================
  // [1] 유닛 인스턴스화
  // ====================================================================
  val gpalu = Module(new GPALUUnit(
    numLines = numLines,  
    inBits = alu_inBits, 
    outBits = alu_outBits))

  val quant = Module(new QuantActUnit(
    numLines = numLines, 
    writeBits = writeBits, 
    indexBits = quant_indexBits, 
    inBits = quant_inBits, 
    outBits = quant_outBits))

  // ====================================================================
  // [2] 제어 신호 및 파라미터 매핑
  // ====================================================================
  gpalu.io.mode  := io.alu_mode
  gpalu.io.stall := io.stall

  quant.io.act_en := io.qa_en
  quant.io.stall  := io.stall
  
  quant.io.param_wr_en   := io.param_wr_en
  quant.io.param_wr_addr := io.param_addr
  quant.io.param_in      := io.param_quant
  quant.io.param_update  := io.param_update

  quant.io.lut_wr_en   := io.quant_lut_wr_en
  quant.io.lut_wr_addr := io.lut_wr_addr
  quant.io.lut_wr_data := io.lut_wr_data

  // ====================================================================
  // [3] 데이터 패스 연결 (Waterfall)
  // ====================================================================
  gpalu.io.in_vec_a := io.operand1
  gpalu.io.in_vec_b := io.operand2
  gpalu.io.in_valid := io.in_valid

  quant.io.in_vec   := gpalu.io.out_vec
  quant.io.in_valid := gpalu.io.out_valid

  io.out_vec   := quant.io.out_vec
  io.out_valid := quant.io.out_valid

  // ====================================================================
  // [4] 에러 및 상태 취합
  // ====================================================================
  io.lut_ready   := quant.io.lut_ready
  io.fatal_alert := gpalu.io.alu_alert || quant.io.sync_alert
}

class VPU_Stage2(
  val numLines:     Int = 16,
  val norm_inBits:  Int = 8,
  val norm_outBits: Int = 8,
  val rope_inBits:  Int = 8,
  val rope_outBits: Int = 8,
  val vectorSize:   Int = 4096,
  // lut
  val writeBits: Int = 256,
  val norm_indexBits: Int = 8,
  val rope_indexBits: Int = 10,
  val norm_lut_dataBits: Int = 16,
  val rope_lut_dataBits: Int = 16,
) extends Module {
  val lut_entry_num = writeBits / norm_lut_dataBits

  val io = IO(new Bundle {
    // 1. 외부 Router(VB/NB)에서 인가되는 8-bit Data Ports
    val in_vec            = Input(Vec(numLines, UInt(8.W)))
    val in_valid          = Input(Vec(numLines, Bool()))

    // =======================================================
    // 2. Stage 2 출력 포트 분리 (Demux 적용)
    // =======================================================
    // Phase 1 결과물: NB에 저장하기 위해 외부 Router로 빠짐
    val out_norm_p1       = Output(Vec(numLines, UInt(8.W)))
    val out_norm_p1_valid = Output(Vec(numLines, Bool()))

    // Phase 2 완료 데이터: RoPE를 거친 최종 결과물로 IBB로 향함
    val out_rope          = Output(Vec(numLines, UInt(8.W)))
    val out_rope_valid    = Output(Vec(numLines, Bool()))

    // 3. 제어 및 상태 신호
    val norm_mode    = Input(UInt(2.W)) // 0:Bypass, 1:RMS, 2:Layer, 3:Softmax
    val norm_phase   = Input(Bool())    // 0:Phase 1(NB저장), 1:Phase 2(스케일 적용)
    val clr_acc      = Input(Bool())
    val rope_en      = Input(Bool())
    val stall        = Input(Bool())

    // 4. RoPE 전용 파라미터 포트
    val param_wr_en  = Input(Bool())
    val param_addr   = Input(UInt(log2Ceil(numLines).max(1).W))
    val param_rope   = Input(UInt(16.W)) 
    val param_update = Input(Bool())

    // 5. 공유 LUT 프로그래밍 포트
    val norm_lut_wr_en  = Input(Bool())
    val norm_lut_is_exp = Input(Bool())

    val rope_cos_wr_en  = Input(Bool())
    val rope_sin_wr_en  = Input(Bool())
    
    val lut_wr_addr     = Input(UInt(8.W)) 
    val lut_wr_data     = Input(Vec(lut_entry_num, UInt(norm_lut_dataBits.W))) 

    // 6. DFD
    val lut_ready    = Output(Bool())
    val fatal_alert  = Output(Bool())
  })

  // ====================================================================
  // [1] 유닛 인스턴스화
  // ====================================================================
  val norm = Module(new UniversalNormUnit(
    numLines = numLines, 
    writeBits = writeBits, 
    inBits = norm_inBits, 
    outBits = norm_outBits, 
    vectorSize = vectorSize, 
    indexBits = norm_indexBits, 
    dataBits = norm_lut_dataBits))

  val rope = Module(new RopeUnit(
    numLines = numLines, 
    inBits = rope_inBits, 
    outBits = rope_outBits, 
    indexBits = rope_indexBits, 
    dataBits = rope_lut_dataBits, 
    writeBits = writeBits))

  // ====================================================================
  // [2] 제어 신호 및 파라미터 매핑
  // ====================================================================
  norm.io.stall     := io.stall
  norm.io.mode_sel  := io.norm_mode
  norm.io.phase_sel := io.norm_phase
  norm.io.clr_acc   := io.clr_acc
  
  norm.io.lut_wr_en   := io.norm_lut_wr_en
  norm.io.lut_is_exp  := io.norm_lut_is_exp
  norm.io.lut_wr_addr := io.lut_wr_addr
  norm.io.lut_wr_data := io.lut_wr_data

  rope.io.stall        := io.stall
  rope.io.rope_en      := io.rope_en
  rope.io.param_wr_en  := io.param_wr_en
  rope.io.param_wr_addr:= io.param_addr
  rope.io.param_in     := io.param_rope
  rope.io.param_update := io.param_update

  rope.io.lut_cos_wr_en := io.rope_cos_wr_en
  rope.io.lut_sin_wr_en := io.rope_sin_wr_en
  rope.io.lut_wr_addr   := io.lut_wr_addr
  rope.io.lut_wr_data   := io.lut_wr_data

  // ====================================================================
  // [3] Data Path & DEMUX (Phase 1 vs Phase 2 분기)
  // ====================================================================
  norm.io.in_vec   := io.in_vec
  norm.io.in_valid := io.in_valid

  // 1. Demux 조건 생성 (라우팅 분기점)
  val is_bypass = io.norm_mode === NormMode.BYPASS
  val to_rope   = io.norm_phase || is_bypass   // Phase 2 이거나 Bypass 모드일 때 RoPE로 진입
  val to_nb     = !io.norm_phase && !is_bypass // Phase 1 일 때는 NB(라우터) 방향으로만 배출

  // 2. Norm 출력 -> NB (Phase 1 전용 출력 포트로 분기)
  io.out_norm_p1 := norm.io.out_vec
  for (r <- 0 until numLines) {
    io.out_norm_p1_valid(r) := norm.io.out_valid(r) && to_nb
  }

  // 3. Norm 출력 -> RoPE (Phase 2 또는 Bypass 모드일 때만 진입)
  for (r <- 0 until numLines) {
    rope.io.in_vec(r) := norm.io.out_vec(r).asSInt
    rope.io.in_valid(r) := norm.io.out_valid(r) && to_rope
  }

  // 4. RoPE 최종 출력 -> Sequencer
  for (r <- 0 until numLines) {
    io.out_rope(r) := rope.io.out_vec(r).asUInt
  }
  io.out_rope_valid := rope.io.out_valid

  // ====================================================================
  // [4] 에러 및 상태 취합
  // ====================================================================
  io.lut_ready   := norm.io.lut_ready && rope.io.lut_ready
  io.fatal_alert := norm.io.sync_alert || rope.io.sync_alert
}