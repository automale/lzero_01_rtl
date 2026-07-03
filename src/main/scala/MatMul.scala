import circt.stage.ChiselStage // import first
import chisel3._
import chisel3.util._

// [1] 하드웨어 모듈 정의 weight stationary
// A x W = C
class MacUnit extends Module {
  val io = IO(new Bundle {
    val in_x      = Input(UInt(8.W)) // input mat
    val in_y      = Input(UInt(16.W)) // output mat init vaa(bias)

    val in_w      = Input(UInt(8.W))
    val set_w     = Input(Bool())
    val clear_w   = Input(Bool())

    val out_in    = Output(UInt(8.W))
    val out_mac   = Output(UInt(16.W))
    val out_set_w = Output(Bool())
  })

  val weight = RegInit(0.U(8.W))

  when (io.clear_w) {
    weight := 0.U
  } .elsewhen (io.set_w) {
    weight := io.in_w
  }

  io.out_mac  := RegNext((io.in_x * weight) + io.in_y)
  io.out_in   := RegNext(io.in_x)
  io.out_set_w := RegNext(io.set_w, false.B)
}

// [2] systolic array 16 by 16
class MXU_16 extends Module {
  // input output
  val io = IO(new Bundle{
    val in_X    = Input(Vec(16, UInt(8.W)))
    val in_W    = Input(Vec(16, Vec(16, UInt(8.W))))
    val set_W   = Input(Vec(16, Bool()))
    val clear_W = Input(Bool())
    val out_MAC = Output(Vec(16, UInt(16.W)))
  })

  val macs = Seq.fill(16, 16)(Module(new MacUnit()))

  // weight, weight set chain wiring
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.in_w  := io.in_W(r)(c)
      if(c == 0){ macs(r)(c).io.set_w := io.set_W(r)}
      else { macs(r)(c).io.set_w := macs(r)(c-1).io.out_set_w }
    }
  }

  // weight clearing wiring
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.clear_w := io.clear_W
    }
  }

  // wiring mac units
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      if (c == 0) { macs(r)(0).io.in_x := io.in_X(r) }
      else { macs(r)(c).io.in_x := macs(r)(c-1).io.out_in }

      if (r == 0) { macs(r)(c).io.in_y  := 0.U }
      else { macs(r)(c).io.in_y := macs(r-1)(c).io.out_mac }
    }
  }

  // wiring MatMulUnit's output to each of macUnit
  for (c <- 0 until 16) {
    io.out_MAC(c)      := macs(15)(c).io.out_mac // output wiring
  }

}

// [3] data orchestrator를 위한 사이즈 16의 shift buffer
class Orch_inbuffer_16 extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(16, UInt(8.W)))
    val load_enable = Input(Bool())
    val shft_enable = Input(Bool())
    val out_scalar  = Output(UInt(8.W))
  })
  val shift_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.load_enable ){
    shift_reg := io.in_vector
  } .elsewhen ( io.shft_enable ){
    for ( i <- 0 until 15 ){
      shift_reg(i) := shift_reg(i+1)
    }
    shift_reg(15) := 0.U
  }

  when ( io.shft_enable ) {
    io.out_scalar := shift_reg(0)
  } .otherwise {
    io.out_scalar := 0.U
  }
}

// [4] data orchestrator를 위한 사이즈 16의 shadow weight buffer
class Shadow_wbuffer_16 extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(16, UInt(8.W)))
    val load_enable = Input(Bool())
    val out_enable  = Input(Bool())
    val out_vector  = Output(Vec(16, UInt(8.W)))
  })
  val shadow_weight_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.load_enable ){
    shadow_weight_reg := io.in_vector
  } 
  
  io.out_vector := Mux(io.out_enable, shadow_weight_reg, VecInit(Seq.fill(16)(0.U(8.W))))

}

// [5] data orchestration unit
class DataOrchUnit_16 extends Module {
  val io = IO(new Bundle{
    val in_input    = Input(Vec(16, UInt(8.W)))
    val in_weight   = Input(Vec(16, UInt(8.W)))

    val feed_enable = Input(Bool())
    val load_enable = Input(Bool())

    val mxu_input   = Output(Vec(16, UInt(8.W)))
    val mxu_weight  = Output(Vec(16, Vec(16, UInt(8.W))))
    val feed_row   = Output(Vec(16, Bool()))
  })

  val input_buff          = Seq.fill(16)(Module(new Orch_inbuffer_16()))
  val shadow_weight_buff  = Seq.fill(16)(Module(new Shadow_wbuffer_16()))
  val control_chain       = RegInit(VecInit(Seq.fill(16)(false.B)))

  // control chain wiring
  control_chain(0) := io.feed_enable
  for (r <- 1 until 16) {
    control_chain(r) := control_chain(r-1)
  }

  // input wiring
  for( r<-0 until 16 ){
    // irrigate input & weight vector from BRAM memory to each buffer
    input_buff(r).io.in_vector := io.in_input
    shadow_weight_buff(r).io.in_vector := io.in_weight

    // control chain wiring
    input_buff(r).io.shft_enable  := control_chain(r)
    input_buff(r).io.load_enable  := control_chain(r) & io.load_enable
    shadow_weight_buff(r).io.out_enable  := control_chain(r)
    shadow_weight_buff(r).io.load_enable  := control_chain(r) & io.load_enable
  }

  // output wiring
  for (r <- 0 until 16) {
    io.feed_row(r) := control_chain(r)
    // mxu input wiring
    io.mxu_input(r) := input_buff(r).io.out_scalar
    // shadow weight buffer to output wiring 
    // ** will be wired to MXU_16's in_W
    for (c <- 0 until 16) {
      io.mxu_weight(r)(c) := shadow_weight_buff(r).io.out_vector(c)
    }
  }
}

// [6] Tensor processing unit
// Orchestrator + 16x16 Systolic Array
class TPU_top extends Module {
  val io = IO(new Bundle {
    // 1. from BRAM/DMA input data 
    val in_input    = Input(Vec(16, UInt(8.W)))
    val in_weight   = Input(Vec(16, UInt(8.W)))

    // 2. control signals
    val feed_enable = Input(Bool())
    val load_enable = Input(Bool())
    val clear_W     = Input(Bool())

    // 3. skewed vectorized output
    val out_MAC     = Output(Vec(16, UInt(16.W)))
  })

  // module instantiation
  val orchestrator = Module(new DataOrchUnit_16())
  val mxu          = Module(new MXU_16())

  
  // [A] outer IO <-> Orchestrator
  orchestrator.io.in_input    := io.in_input
  orchestrator.io.in_weight   := io.in_weight
  orchestrator.io.feed_enable := io.feed_enable
  orchestrator.io.load_enable := io.load_enable

  // [B] Orchestrator <-> MXU (Systolic Array)
  // 1. input matrix (skewed) vector input wiring (Skewed Input -> MXU Input)
  mxu.io.in_X := orchestrator.io.mxu_input

  // 2. shadow weight to weight 2d matrix wiring (Shadow Buffer -> MXU Weight)
  mxu.io.in_W := orchestrator.io.mxu_weight

  // 3. global clear 
  mxu.io.clear_W := io.clear_W

  // 4. set weight row enable wiring (Orchestrator -> MXU)
  mxu.io.set_W := orchestrator.io.feed_row

  // [C] MXU <-> final output wiring ... will be wired to VPU input
  io.out_MAC := mxu.io.out_MAC
}

// [7] Chisel 6.x 실행 객체 (오타 수정 및 TPU_top 추가)
object TPU_Main extends App {
  println("TPU Accelerator SystemVerilog extracting...")
  
  // [수정] 오타(.t_16) 제거 및 최상위 Wrapper(TPU_top) SV 추출 추가!
  ChiselStage.emitSystemVerilogFile(new MacUnit(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new MXU_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new DataOrchUnit_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new TPU_top(), Array("--target-dir", "generated"))
  
  println("Extract SV Finished! Please check 'generated' directory")
}