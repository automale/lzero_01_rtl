package npu.core

import chisel3._
import chisel3.util._

// ====================================================================
// [1] 단일 레인 RoPE 코어 (파라미터화 & DFD 적용)
// ====================================================================
class RopeCore(
  val inBits:     Int, // 스트림 입력 비트 폭
  val outBits:    Int, // 스트림 출력 비트 폭
  val indexBits:  Int, // 각도 인덱스 비트 (ZEXT 대상, 1024 엔트리)
  val dataBits:   Int, // LUT 출력(Sin/Cos) 비트 폭 (Q포맷)
  val writeBits:  Int, // LUT 프로그래밍 버스 폭
  val scaleShift: Int  // 곱셈 후 Q포맷 복원을 위한 Shift 양
) extends Module {
  
  val wordsPerBurst = writeBits / dataBits
  val burstAddrBits = log2Ceil((1 << indexBits) / wordsPerBurst)

  val io = IO(new Bundle {
    val in_data   = Input(SInt(inBits.W)) 
    val in_valid  = Input(Bool())
    
    // CPU가 섀도우 레지스터에서 내려주는 10-bit 각도 (ZEXT)
    val angle_idx = Input(UInt(indexBits.W)) 
    
    val rope_en   = Input(Bool()) 
    val stall     = Input(Bool())

    // --- LUT Programming Ports (데이터 버스는 공유, Enable로 타겟 구분) ---
    val lut_cos_wr_en = Input(Bool())
    val lut_sin_wr_en = Input(Bool())
    val lut_wr_addr   = Input(UInt(burstAddrBits.W))
    val lut_wr_data   = Input(Vec(wordsPerBurst, UInt(dataBits.W)))

    val out_data  = Output(SInt(outBits.W))
    val out_valid = Output(Bool())

    // --- Orchestrator 상태 보고용 포트 ---
    val lut_ready  = Output(Bool()) // Sin, Cos 모두 프로그래밍 완료 시 1
    val sync_alert = Output(Bool()) // Read/Write 타이밍 오류 시 1
  })

  val run = !io.stall

  // ====================================================================
  // [1. BRAM LUT 인스턴스화 및 동기화 모니터링]
  // ====================================================================
  val cos_lut = Module(new Universal_Wide_LUT(indexBits, dataBits, writeBits))
  val sin_lut = Module(new Universal_Wide_LUT(indexBits, dataBits, writeBits))

  // Write 매핑 (데이터/주소 버스 공유)
  cos_lut.io.wr_en   := io.lut_cos_wr_en
  cos_lut.io.wr_addr := io.lut_wr_addr
  cos_lut.io.wr_data := io.lut_wr_data

  sin_lut.io.wr_en   := io.lut_sin_wr_en
  sin_lut.io.wr_addr := io.lut_wr_addr
  sin_lut.io.wr_data := io.lut_wr_data

  // Read 매핑 (스트림 진행 중에는 계속 동일한 각도의 Sin/Cos를 뿜어냄)
  cos_lut.io.rd_en   := run
  cos_lut.io.rd_addr := io.angle_idx
  
  sin_lut.io.rd_en   := run
  sin_lut.io.rd_addr := io.angle_idx

  // [Sync Alert 로직]
  val exp_rd_valid = RegNext(run, false.B) // rd_en = run
  val cos_sync_err = (exp_rd_valid =/= cos_lut.io.rd_valid) || (RegNext(io.lut_cos_wr_en, false.B) =/= cos_lut.io.wr_valid)
  val sin_sync_err = (exp_rd_valid =/= sin_lut.io.rd_valid) || (RegNext(io.lut_sin_wr_en, false.B) =/= sin_lut.io.wr_valid)
  
  io.sync_alert := cos_sync_err || sin_sync_err
  io.lut_ready  := cos_lut.io.lut_ready && sin_lut.io.lut_ready

  // 1클럭 딜레이된 LUT 파라미터 캐치
  val param_cos = cos_lut.io.rd_data.asSInt
  val param_sin = sin_lut.io.rd_data.asSInt

  // ====================================================================
  // [2. RoPE 수학 연산 파이프라인 (Stall 적용)]
  // ====================================================================
  val delay_reg = RegEnable(io.in_data, 0.S, io.in_valid && run)
  
  val is_odd_cycle = RegInit(false.B)
  when (io.in_valid && io.rope_en && run) {
    is_odd_cycle := !is_odd_cycle
  } .elsewhen(!io.rope_en && run) {
    is_odd_cycle := false.B
  }

  val x0_hold = RegEnable(io.in_data, 0.S, io.in_valid && !is_odd_cycle && run)

  val mul_A = Mux(is_odd_cycle, delay_reg, x0_hold)
  val mul_B = Mux(is_odd_cycle, param_cos, param_sin)
  val p1 = mul_A * mul_B

  val mul_C = Mux(is_odd_cycle, io.in_data, delay_reg)
  val mul_D = Mux(is_odd_cycle, param_sin, param_cos)
  val p2 = mul_C * mul_D

  val rope_result = Mux(is_odd_cycle, p1 - p2, p1 + p2)
  val scaled_rope = (rope_result >> scaleShift).asSInt 

  // ====================================================================
  // [3. Bypass 및 출력 MUX]
  // ====================================================================
  val normal_delay_valid = RegEnable(io.in_valid, false.B, run)
  val normal_delay_data  = RegEnable(io.in_data, 0.S, run)

  io.out_data  := Mux(io.rope_en, scaled_rope(outBits - 1, 0).asSInt, normal_delay_data(outBits - 1, 0).asSInt) 
  io.out_valid := Mux(io.rope_en, normal_delay_valid, io.in_valid)
}


// ====================================================================
// [2] N-Row Universal RoPE Unit (섀도우 레지스터 & 다중 코어 통합)
// ====================================================================
class RopeUnit(
  val numLines:  Int,
  val inBits:    Int,
  val outBits:   Int,
  val indexBits: Int,
  val dataBits:  Int,
  val writeBits: Int
) extends Module {
  
  val wordsPerBurst = writeBits / dataBits
  val burstAddrBits = log2Ceil((1 << indexBits) / wordsPerBurst)
  val paramAddrBits = log2Ceil(numLines).max(1)

  val io = IO(new Bundle {
    val in_vec      = Input(Vec(numLines, SInt(inBits.W)))
    val in_valid    = Input(Vec(numLines, Bool()))
    
    val rope_en     = Input(Bool())
    val stall       = Input(Bool())

    // --- 각도 인덱스 파라미터 Loading Ports (Background) ---
    val param_wr_en   = Input(Bool())
    val param_wr_addr = Input(UInt(paramAddrBits.W))
    val param_in      = Input(UInt(16.W)) // 10-bit ZEXT to 16-bit
    
    val param_update  = Input(Bool()) // Context Switch Trigger

    // --- LUT Programming Ports (Broadcast) ---
    val lut_cos_wr_en = Input(Bool())
    val lut_sin_wr_en = Input(Bool())
    val lut_wr_addr   = Input(UInt(burstAddrBits.W))
    val lut_wr_data   = Input(Vec(wordsPerBurst, UInt(dataBits.W)))

    val out_vec       = Output(Vec(numLines, SInt(outBits.W)))
    val out_valid     = Output(Vec(numLines, Bool()))

    // --- 상태 통합 포트 ---
    val lut_ready     = Output(Bool())
    val sync_alert    = Output(Bool())
  })

  // ====================================================================
  // [1] Shadow & Active 각도 파라미터 레지스터 (10-bit 추출)
  // ====================================================================
  val shadow_angle_regs = RegInit(VecInit(Seq.fill(numLines)(0.U(indexBits.W))))
  when (io.param_wr_en) {
    shadow_angle_regs(io.param_wr_addr) := io.param_in(indexBits - 1, 0)
  }

  val active_angle_regs = RegInit(VecInit(Seq.fill(numLines)(0.U(indexBits.W))))
  when (io.param_update) {
    active_angle_regs := shadow_angle_regs
  }

  // ====================================================================
  // [2] 코어 인스턴스화 및 매핑
  // ====================================================================
  val cores = Seq.fill(numLines)(Module(new RopeCore(
    inBits, 
    outBits, 
    indexBits, 
    dataBits, 
    writeBits)))

  for (r <- 0 until numLines) {
    cores(r).io.in_data       := io.in_vec(r)
    cores(r).io.in_valid      := io.in_valid(r)
    cores(r).io.angle_idx     := active_angle_regs(r)
    
    cores(r).io.rope_en       := io.rope_en
    cores(r).io.stall         := io.stall

    cores(r).io.lut_cos_wr_en := io.lut_cos_wr_en
    cores(r).io.lut_sin_wr_en := io.lut_sin_wr_en
    cores(r).io.lut_wr_addr   := io.lut_wr_addr
    cores(r).io.lut_wr_data   := io.lut_wr_data

    io.out_vec(r)             := cores(r).io.out_data
    io.out_valid(r)           := cores(r).io.out_valid
  }

  // ====================================================================
  // [3] 상태 모니터링 취합 (Aggregation)
  // ====================================================================
  // 하나라도 오류가 발생하면 Alert 발생
  io.sync_alert := VecInit(cores.map(_.io.sync_alert)).asUInt.orR
  
  // 모든 브로드캐스팅이 동일하므로 대표 코어(0번)의 상태 출력
  io.lut_ready  := cores(0).io.lut_ready
}