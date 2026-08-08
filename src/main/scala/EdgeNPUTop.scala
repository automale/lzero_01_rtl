import circt.stage.ChiselStage // import first
import npu.top._

// [N] Chisel 6.x 실행 객체
object TPU_Main extends App {
  println("TPU Accelerator SystemVerilog extracting...")
  
  ChiselStage.emitSystemVerilogFile(new MacUnit(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new MXU_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new DataOrchUnit_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new TPU_top(), Array("--target-dir", "generated"))
  
  println("Extract SV Finished! Please check 'generated' directory")
}