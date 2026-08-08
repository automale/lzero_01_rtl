package npu.core
import chisel3._
import chisel3.util._

// [1] 하드웨어 모듈 정의 weight stationary
// A x W = C
class MacUnit(
  val inBits: Int,  // 8
  val accBits: Int  // 16
) extends Module {
  val io = IO(new Bundle {
    val in_x      = Input(UInt(inBits.W)) 
    val in_y      = Input(UInt(accBits.W)) 
    val in_w      = Input(UInt(inBits.W))
    
    val set_w     = Input(Bool())
    val clear_w   = Input(Bool())
    val stall     = Input(Bool())

    val out_in    = Output(UInt(inBits.W))
    val out_mac   = Output(UInt(accBits.W))
    val out_set_w = Output(Bool())

    val mac_alert = Output(Bool()) // 누적 과정에서 오버플로우 발생 감지
  })

  val run = !io.stall

  // 1. Weight Register (Stall 시 상태 유지)
  val weight = RegInit(0.U(inBits.W))

  when(run) {
    when (io.clear_w) {
      weight := 0.U
    } .elsewhen (io.set_w) {
      weight := io.in_w
    }
  }

  // 2. MAC Calculation
  val mul_res = io.in_x * weight
  // 오버플로우 감지를 위해 폭이 1 확장된 덧셈(+&) 수행
  val add_res = mul_res +& io.in_y 

  // 3. Pipeline Registers (Stall 제어)
  io.out_mac   := RegEnable(add_res(accBits - 1, 0), run)
  io.out_in    := RegEnable(io.in_x, run)
  io.out_set_w := RegEnable(io.set_w, false.B, run)

  // 4. Debug Alert: 덧셈 결과가 accBits를 초과하면(Carry 비트 발생) High
  io.mac_alert := add_res(accBits)
}

// [2] systolic array 16 by 16
class MXU(
  val numRows: Int, // 16
  val numCols: Int, // 16
  val inBits: Int,  // 8
  val accBits: Int  // 16
) extends Module {
  
  val io = IO(new Bundle{
    val in_X    = Input(Vec(numRows, UInt(inBits.W)))
    val in_W    = Input(Vec(numRows, Vec(numCols, UInt(inBits.W))))
    val set_W   = Input(Vec(numRows, Bool()))
    val clear_W = Input(Bool())
    
    val stall   = Input(Bool())

    val out_MAC = Output(Vec(numCols, UInt(accBits.W)))
    
    val mxu_alert = Output(Bool()) // 하나라도 MAC 연산 중 오버플로우가 나면 High
  })

  // 2차원 MAC Unit 배열 인스턴스화
  val macs = Seq.fill(numRows, numCols)(Module(new MacUnit(inBits, accBits)))

  for ( r <- 0 until numRows ){
    for ( c <- 0 until numCols ){
      
      // 공통 제어 신호 할당
      macs(r)(c).io.in_w    := io.in_W(r)(c)
      macs(r)(c).io.clear_w := io.clear_W
      macs(r)(c).io.stall   := io.stall

      // 1. Weight Set Chain (가로 방향 딜레이)
      if(c == 0){ 
        macs(r)(c).io.set_w := io.set_W(r)
      } else { 
        macs(r)(c).io.set_w := macs(r)(c-1).io.out_set_w 
      }

      // 2. Input X Chain (가로 방향 데이터 전파)
      if (c == 0) { 
        macs(r)(0).io.in_x := io.in_X(r) 
      } else { 
        macs(r)(c).io.in_x := macs(r)(c-1).io.out_in 
      }

      // 3. Input Y Chain (세로 방향 누적 합 전파)
      if (r == 0) { 
        macs(r)(c).io.in_y  := 0.U 
      } else { 
        macs(r)(c).io.in_y := macs(r-1)(c).io.out_mac 
      }
    }
  }

  // 4. Output Wiring (맨 아래 Row의 결과를 출력으로 맵핑)
  for (c <- 0 until numCols) {
    io.out_MAC(c) := macs(numRows - 1)(c).io.out_mac
  }

  // [debug] (Aggregation)
  val all_alerts = macs.flatten.map(_.io.mac_alert)
  io.mxu_alert := VecInit(all_alerts).asUInt.orR
}