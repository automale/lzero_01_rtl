package npu.core

import chisel3._
import chisel3.util._
import npu.utils.Universal_Wide_LUT

// [1] QuantAct Core (4-Stage Pipeline)
class QuantActCore (
  val writeBits: Int, // 256
  val indexBits: Int, // 10
  val inBits: Int,    // 32
  val outBits: Int    // 8
) extends Module {
  
  val numEntries = 1 << indexBits
  val wordsPerBurst = writeBits / outBits
  val burstAddrBits = log2Ceil(numEntries / wordsPerBurst)

  val io = IO(new Bundle {
    val in_mac      = Input(UInt(inBits.W))
    val param       = Input(UInt(inBits.W)) // [31:16] M, [12:8] S, [7:0] ZP
    val act_en      = Input(Bool())     
    
    val stall       = Input(Bool())

    // --- LUT Programming Port ---
    val lut_wr_en   = Input(Bool())
    val lut_wr_addr = Input(UInt(burstAddrBits.W))
    val lut_wr_data = Input(Vec(wordsPerBurst, UInt(outBits.W)))

    val out_qact    = Output(UInt(outBits.W))

    // --- Orchestrator 상태 보고용 포트 ---
    val lut_ready   = Output(Bool()) // 프로그래밍 완료 신호
    val sync_alert  = Output(Bool()) // Read/Write 타이밍 오류 발생 시 High
  })

  val run = !io.stall

  // 파라미터 분리
  val zp    = io.param(7, 0).asSInt
  val shift = io.param(12, 8).asUInt
  val mult  = io.param(31, 16).asUInt

  // -----------------------------------------------------
  // [Stage 1] Subtraction (영점 조정)
  // -----------------------------------------------------
  val s1_sub    = RegEnable(io.in_mac.asSInt.pad(33) - zp.pad(33), run)
  val s1_mult   = RegEnable(mult, run)
  val s1_shift  = RegEnable(shift, run)
  val s1_act_en = RegEnable(io.act_en, run)

  // -----------------------------------------------------
  // [Stage 2] Multiplication (스케일링 곱셈)
  // -----------------------------------------------------
  val s2_mult_res = RegEnable(s1_sub * s1_mult.zext.asSInt, run)
  val s2_shift    = RegEnable(s1_shift, run)
  val s2_act_en   = RegEnable(s1_act_en, run)

  // -----------------------------------------------------
  // [Stage 3] Shift & Clamping (10-bit 인덱스 생성)
  // -----------------------------------------------------
  val max_idx = (1 << indexBits) - 1 
  
  val shift_res   = (s2_mult_res >> (s2_shift - 2.U)).asSInt 
  val clamped_idx = Mux(shift_res < 0.S, 0.U(indexBits.W), 
                        Mux(shift_res > max_idx.S, max_idx.U(indexBits.W), 
                        shift_res(indexBits - 1, 0).asUInt)) 
                        
  val s3_idx           = RegEnable(clamped_idx, run)
  val s3_linear_bypass = RegEnable(clamped_idx(indexBits - 1, indexBits - outBits), run) 
  val s3_act_en        = RegEnable(s2_act_en, run)

  // -----------------------------------------------------
  // [Stage 4] BRAM LUT Read & Handshake Monitoring
  // -----------------------------------------------------
  val act_lut = Module(new Universal_Wide_LUT(
    indexBits = indexBits, 
    outBits  = outBits, 
    writeBits = writeBits
  ))
  
  // LUT Programming 입력
  act_lut.io.wr_en   := io.lut_wr_en
  act_lut.io.wr_addr := io.lut_wr_addr
  act_lut.io.wr_data := io.lut_wr_data
  
  // LUT Read 입력 (파이프라인이 멈추면 Read도 멈춤)
  act_lut.io.rd_en   := run 
  act_lut.io.rd_addr := s3_idx 

  // --- Sync 모니터링 로직 ---
  
  // 1. Read Sync Check: rd_en이 나간 1클럭 뒤에 정확히 rd_valid가 뜨는지 비교
  val expected_rd_valid = RegNext(act_lut.io.rd_en, false.B)
  val rd_sync_err       = expected_rd_valid =/= act_lut.io.rd_valid

  // 2. Write Sync Check: wr_en이 나간 1클럭 뒤에 정확히 wr_valid가 뜨는지 비교
  val expected_wr_valid = RegNext(act_lut.io.wr_en, false.B)
  val wr_sync_err       = expected_wr_valid =/= act_lut.io.wr_valid

  // 하나라도 타이밍이 어긋나면 외부(Control)로 Alert 발송
  io.sync_alert := rd_sync_err || wr_sync_err
  
  // 상태 신호 Bypass
  io.lut_ready  := act_lut.io.lut_ready

  // -----------------------------------------------------
  // [최종 MUX]
  // -----------------------------------------------------
  val s4_linear_bypass = RegEnable(s3_linear_bypass, run)
  val s4_act_en        = RegEnable(s3_act_en, run)

  io.out_qact := Mux(s4_act_en, act_lut.io.rd_data, s4_linear_bypass)
}

// [2] 16 Row Universal VPU QuantActUnit
class QuantActUnit (
  val numLines: Int,  // 16 
  val writeBits: Int, // 256
  val indexBits: Int, // 10
  val inBits: Int,    // 32
  val outBits: Int    // 8
) extends Module {
  
  val wordsPerBurst = writeBits / outBits
  val burstAddrBits = log2Ceil((1 << indexBits) / wordsPerBurst)
  val paramAddrBits = log2Ceil(numLines).max(1) 

  val io = IO(new Bundle {
    val in_vec            = Input(Vec(numLines, UInt(inBits.W)))
    val in_valid          = Input(Vec(numLines, Bool()))
    val act_en = Input(Bool())
    
    val stall             = Input(Bool())

    // --- Parameter Loading Ports (Background) ---
    val param_wr_en   = Input(Bool())
    val param_wr_addr = Input(UInt(paramAddrBits.W)) 
    val param_in      = Input(UInt(32.W))

    // --- Parameter Update Port (Foreground Context Switch) ---
    val param_update  = Input(Bool()) // Control이 타일 변경 시 1클럭 High로 쏴줌

    val lut_wr_en     = Input(Bool())
    val lut_wr_addr   = Input(UInt(burstAddrBits.W)) 
    val lut_wr_data   = Input(Vec(wordsPerBurst, UInt(outBits.W)))

    val out_vec       = Output(Vec(numLines, UInt(outBits.W)))
    val out_valid     = Output(Vec(numLines, Bool()))

    // --- Orchestrator 상태 보고용 포트 ---
    val lut_ready     = Output(Bool()) // 프로그래밍 완료 신호
    val sync_alert    = Output(Bool()) // 코어 중 하나라도 에러가 나면 High
  })

  // ====================================================================
  // 1. Shadow Registers (백그라운드에서 DMA/Control이 천천히 채워 넣는 공간)
  // ====================================================================
  val shadow_param_regs = RegInit(VecInit(Seq.fill(numLines)(0.U(32.W))))
  when (io.param_wr_en) {
    shadow_param_regs(io.param_wr_addr) := io.param_in
  }

  // ====================================================================
  // 2. Active Registers (실제 파이프라인이 바라보는 공간)
  // ====================================================================
  val active_param_regs = RegInit(VecInit(Seq.fill(numLines)(0.U(32.W))))
  when (io.param_update) {
    active_param_regs := shadow_param_regs
  }

  // ====================================================================
  // 코어 인스턴스화 및 데이터 패스 매핑
  // ====================================================================
  val cores = Seq.fill(numLines)(Module(new QuantActCore(
    writeBits = writeBits,
    indexBits = indexBits,
    outBits  = outBits
  )))

  val run = !io.stall

  for (r <- 0 until numLines) {
    cores(r).io.in_mac := io.in_vec(r)
    cores(r).io.param  := active_param_regs(r)
    cores(r).io.act_en := io.act_en
    cores(r).io.stall  := io.stall 

    cores(r).io.lut_wr_en   := io.lut_wr_en
    cores(r).io.lut_wr_addr := io.lut_wr_addr
    cores(r).io.lut_wr_data := io.lut_wr_data

    io.out_vec(r) := cores(r).io.out_qact
    
    val valid_s1 = RegEnable(io.in_valid(r), false.B, run)
    val valid_s2 = RegEnable(valid_s1,       false.B, run)
    val valid_s3 = RegEnable(valid_s2,       false.B, run)
    val valid_s4 = RegEnable(valid_s3,       false.B, run)
    
    io.out_valid(r) := valid_s4
  }

  // ====================================================================
  // 상태 및 에러 신호 통합 (Aggregation)
  // ====================================================================
  // 1. sync_alert: 16개 코어 중 단 하나라도 1을 띄우면 최종 출력도 1이 됨 (Bitwise OR Reduction)
  io.sync_alert := VecInit(cores.map(_.io.sync_alert)).asUInt.orR
  
  // 2. lut_ready: 모든 코어가 동일한 브로드캐스트 프로그래밍 신호를 받으므로, 0번 코어의 상태만 대표로 출력
  io.lut_ready := cores(0).io.lut_ready
}