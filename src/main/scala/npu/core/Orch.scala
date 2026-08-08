package npu.core
import chisel3._
import chisel3.util._

// [1] data orchestrator를 위한 shift buffer
class Orch_inbuffer (
  val vectorSize: Int, // 16 
  val dataBits: Int    // 8
) extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(vectorSize, UInt(dataBits.W)))
    val load_enable = Input(Bool())
    val shft_enable = Input(Bool())
    
    val stall       = Input(Bool()) // 파이프라인 정지 신호
    
    val out_scalar  = Output(UInt(dataBits.W))
    val orch_alert  = Output(Bool()) 
  })
  
  val run = !io.stall
  val shift_reg = RegInit(VecInit(Seq.fill(vectorSize)(0.U(dataBits.W))))

  // [1] Shift Register State Update Logic
  when (run) {
    when (io.load_enable && io.shft_enable) {
      // [의도 반영] Load와 Shift 동시 발생: 0번째는 즉시 방출되므로 1번째부터 레지스터에 담음
      for (i <- 0 until vectorSize - 1) {
        shift_reg(i) := io.in_vector(i+1)
      }
      shift_reg(vectorSize - 1) := 0.U
      
    } .elsewhen (io.load_enable) {
      // 단순 Load: 벡터 전체를 레지스터에 온전히 담음
      shift_reg := io.in_vector
      
    } .elsewhen (io.shft_enable) {
      // 단순 Shift: 기존 레지스터 값들을 한 칸씩 이동
      for (i <- 0 until vectorSize - 1) {
        shift_reg(i) := shift_reg(i+1)
      }
      shift_reg(vectorSize - 1) := 0.U
    }
  }

  // [2] Output Combinational MUX Logic
  // Load+Shift 동시 발생 시 -> in_vector(0)를 즉시 Bypass하여 방출
  // Shift만 발생 시 -> shift_reg(0) 방출
  // 그 외 -> 0.U 방출
  val current_out = Mux(io.load_enable && io.shft_enable, io.in_vector(0),
                      Mux(io.shft_enable, shift_reg(0), 
                      0.U(dataBits.W)))

  // Stall 시에는 방출을 막아 하위 MAC Unit의 불필요한 연산(토글) 방지
  io.out_scalar := Mux(run, current_out, 0.U(dataBits.W))
  
  // [3] Debug Alert
  io.orch_alert := false.B
}

// [2] data orchestrator를 위한 사이즈 16의 shadow weight buffer
class Shadow_wbuffer(
  val vectorSize: Int, // 16 
  val dataBits: Int    // 8
) extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(vectorSize, UInt(dataBits.W)))
    val load_enable = Input(Bool())
    val out_enable  = Input(Bool())
    
    val stall       = Input(Bool())
    
    val out_vector  = Output(Vec(vectorSize, UInt(dataBits.W)))
    val orch_alert  = Output(Bool())
  })
  
  val run = !io.stall
  val shadow_weight_reg = RegInit(VecInit(Seq.fill(vectorSize)(0.U(dataBits.W))))

  when ( run && io.load_enable ){
    shadow_weight_reg := io.in_vector
  } 
  
  io.out_vector := Mux(io.out_enable, shadow_weight_reg, VecInit(Seq.fill(16)(0.U(8.W))))

  io.orch_alert := false.B
}

// [3] data orchestration unit
class DataOrchUnit(
  val numRows: Int,  // 16
  val numCols: Int,  // 16
  val dataBits: Int, // 8
) extends Module {
  val io = IO(new Bundle{
    val in_input    = Input(Vec(numCols, UInt(dataBits.W)))
    val in_weight   = Input(Vec(numCols, UInt(dataBits.W)))

    val feed_enable = Input(Bool())
    val load_enable = Input(Bool())
    
    val stall       = Input(Bool())

    val mxu_input   = Output(Vec(numRows, UInt(dataBits.W)))
    val mxu_weight  = Output(Vec(numRows, Vec(numCols, UInt(dataBits.W))))
    val feed_row    = Output(Vec(numRows, Bool()))
    
    val orch_alert  = Output(Bool())
  })

  val run = !io.stall

  val input_buff         = Seq.fill(numRows)(Module(new Orch_inbuffer(numCols, dataBits)))
  val shadow_weight_buff = Seq.fill(numRows)(Module(new Shadow_wbuffer(numCols, dataBits)))
  
  val control_chain = RegInit(VecInit(Seq.fill(numRows)(false.B)))

  // control chain wiring
  when (run) {
    control_chain(0) := io.feed_enable
    for (r <- 1 until numRows) {
      control_chain(r) := control_chain(r-1)
    }
  }

  for(r <- 0 until numRows) {
    // 1. Data Irrigation (BRAM -> Buffer)
    input_buff(r).io.in_vector := io.in_input
    shadow_weight_buff(r).io.in_vector := io.in_weight

    // 2. Control Wiring (Stall 및 파동 제어 신호 할당)
    input_buff(r).io.stall       := io.stall
    input_buff(r).io.shft_enable := control_chain(r)
    input_buff(r).io.load_enable := control_chain(r) && io.load_enable
    
    shadow_weight_buff(r).io.stall       := io.stall
    shadow_weight_buff(r).io.out_enable  := control_chain(r)
    shadow_weight_buff(r).io.load_enable := control_chain(r) && io.load_enable

    // 3. Output Wiring (Buffer -> MXU)
    io.feed_row(r)  := control_chain(r)
    io.mxu_input(r) := input_buff(r).io.out_scalar
    io.mxu_weight(r):= shadow_weight_buff(r).io.out_vector
  }

  // [debug] Aggregation
  val in_alerts = input_buff.map(_.io.orch_alert)
  val w_alerts  = shadow_weight_buff.map(_.io.orch_alert)
  
  io.orch_alert := VecInit(in_alerts ++ w_alerts).asUInt.orR
}
