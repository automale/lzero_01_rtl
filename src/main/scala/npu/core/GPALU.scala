package npu.core

import chisel3._
import chisel3.util._

// ALU Mode
object GPALUMode {
  val BYPASS = 0.U(2.W)
  val ADD    = 1.U(2.W)
  val MUL    = 2.U(2.W)
}

// ====================================================================
// [1] GPALU Core (파라미터화 적용)
// ====================================================================
class GPALUCore(
  val inBits: Int, // 32
  val outBits: Int // 32
) extends Module {
  val io = IO(new Bundle {
    val in_a     = Input(UInt(inBits.W)) 
    val in_b     = Input(UInt(inBits.W)) 
    val in_valid = Input(Bool())
    
    val mode     = Input(UInt(2.W))  
    val stall    = Input(Bool())

    val out_res   = Output(UInt(outBits.W))
    val out_valid = Output(Bool())
    
    val overflow  = Output(Bool()) 
  })

  val run = !io.stall

  // -----------------------------------------------------
  // [Stage 1] Input Registration
  // -----------------------------------------------------
  val s1_a     = RegEnable(io.in_a, run)
  val s1_b     = RegEnable(io.in_b, run)
  val s1_mode  = RegEnable(io.mode, run)
  val s1_valid = RegEnable(io.in_valid, false.B, run)

  // -----------------------------------------------------
  // [Stage 2] Execution 
  // -----------------------------------------------------
  // 1. Addition (오버플로우 감지를 위해 +& 사용 시 출력은 dataBits + 1 비트가 됨)
  val add_full = s1_a +& s1_b
  
  // 하위 dataBits 추출 (예: 32-bit면 31~0번 비트)
  val s2_add   = RegEnable(add_full(outBits - 1, 0), run) 
  
  // 최상위 비트(Overflow/Carry) 추출 (예: 32-bit면 32번 비트)
  val s2_ovf   = RegEnable(add_full(outBits), run)    

  // 2. Multiplication (최대 2*dataBits 비트가 나오므로 하위 dataBits 추출)
  val mul_full = s1_a * s1_b
  val s2_mul   = RegEnable(mul_full(outBits - 1, 0), run)

  // 3. Bypass
  val s2_bypass = RegEnable(s1_a, run)

  val s2_mode  = RegEnable(s1_mode, run)
  val s2_valid = RegEnable(s1_valid, false.B, run)

  // -----------------------------------------------------
  // [Stage 3] Output Selection & Output Register
  // -----------------------------------------------------
  val out_mux = MuxLookup(s2_mode, s2_bypass)(Seq(
    GPALUMode.ADD -> s2_add,
    GPALUMode.MUL -> s2_mul
  ))

  io.out_res   := RegEnable(out_mux, run)
  io.out_valid := RegEnable(s2_valid, false.B, run)
  
  io.overflow  := (s2_mode === GPALUMode.ADD) && s2_ovf && s2_valid
}

// ====================================================================
// [2] 16 Row GPALU Unit (파라미터화 적용)
// ====================================================================
class GPALUUnit(
  val numLines: Int, // 16
  val inBits: Int,   // 32
  val outBits: Int   // 32
) extends Module {
  val io = IO(new Bundle {
    val in_vec_a  = Input(Vec(numLines, UInt(inBits.W)))
    val in_vec_b  = Input(Vec(numLines, UInt(inBits.W)))
    val in_valid  = Input(Vec(numLines, Bool()))
    
    val mode      = Input(UInt(2.W)) 
    val stall     = Input(Bool())

    val out_vec   = Output(Vec(numLines, UInt(outBits.W)))
    val out_valid = Output(Vec(numLines, Bool()))
    
    val alu_alert = Output(Bool()) 
  })

  val cores = Seq.fill(numLines)(Module(new GPALUCore(inBits = inBits, outBits = outBits)))

  for (r <- 0 until numLines) {
    cores(r).io.in_a     := io.in_vec_a(r)
    cores(r).io.in_b     := io.in_vec_b(r)
    cores(r).io.in_valid := io.in_valid(r)
    cores(r).io.mode     := io.mode
    cores(r).io.stall    := io.stall

    io.out_vec(r)   := cores(r).io.out_res
    io.out_valid(r) := cores(r).io.out_valid
  }

  // 에러 신호 통합 (Aggregation)
  io.alu_alert := VecInit(cores.map(_.io.overflow)).asUInt.orR
}