import circt.stage.ChiselStage // <--- 컴파일러가 헷갈리지 않게 제일 먼저 데려옵니다!
import chisel3._
import chisel3.util._

// [1] 하드웨어 모듈 정의 (아래 코드는 기존과 완벽히 동일합니다)
class MacUnit extends Module {
  val io = IO(new Bundle {
    val in_a    = Input(UInt(8.W))
    val in_b    = Input(UInt(8.W))
    val clear   = Input(Bool())
    val out_mac = Output(UInt(16.W))
  })

  val accReg = RegInit(0.U(16.W))
  val mulResult = io.in_a * io.in_b
  
  when(io.clear) {
    accReg := 0.U
  } .otherwise {
    accReg := accReg + mulResult
  }

  io.out_mac := accReg
}

// [2] Chisel 6.x 문법에 맞춘 실행 객체
object MacUnitMain extends App {
  println("🚀 Generating SystemVerilog code for MacUnit (Chisel 6.7.0 + CIRCT)...")
  
  ChiselStage.emitSystemVerilogFile(
    new MacUnit(),
    Array("--target-dir", "generated")
  )
}