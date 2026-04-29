import circt.stage.ChiselStage // <--- 컴파일러가 헷갈리지 않게 제일 먼저 데려옵니다!
import chisel3._
import chisel3.util._

// [1] 하드웨어 모듈 정의 weight stationary
// A x W = C
class MacUnit extends Module {
  val io = IO(new Bundle {
    val in_a      = Input(UInt(8.W)) // input mat
    val in_c      = Input(UInt(16.W)) // output mat init vaa(bias)

    val set_w     = Input(Bool())
    val clear_w   = Input(Bool()) 
    val in_w      = Input(UInt(8.W))

    val out_in    = Output(UInt(8.W))
    val out_mac   = Output(UInt(16.W))
  })

  val weight = RegInit(0.U(8.W))

  when (io.clear_w) {
    weight := 0.U
  } .elsewhen (io.set_w) {
    weight := io.in_w
  }

  io.out_mac := (io.in_a * weight) + io.in_c
  io.out_in := io.in_a
}

// [2] systolic array 16 by 16
class MatMulUnit_16 extends Module {
  // input output
  val io = IO(new Bundle{
    val in_A    = Input(Vec(16, UInt(8.W)))

    val set_W   = Input(Bool())
    val clear_W = Input(Bool())
    val in_W    = Input(Vec(16, Vec(16, UInt(8.W))))

    val out_MAC = Output(Vec(16, UInt(16.W)))
  })

  val macs = Seq.fill(16, 16)(Module(new MacUnit()))

  
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.in_w := io.in_W(r)(c)
      macs(r)(c).io.set_w := io.set_W
    }
  }

  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.clear_w := io.clear_W
    }
  }
  

  for (r <- 0 until 16) {
    macs(r)(0).io.in_a := io.in_A(r)
  }

  for (c <- 0 until 16) {
    macs(0)(c).io.in_c := 0.U
    io.out_MAC(c) := macs(15)(c).io.out_mac
  }

  for ( r <- 0 until 15 ){
    for ( c <- 0 until 15 ){
      macs(r)(c+1).io.in_a := macs(r)(c).io.out_in
      macs(r+1)(c).io.in_c := macs(r)(c).io.out_mac
    }
  }

}

// [3] data orchestrator를 위한 사이즈 16의 buffer
class Orch_buffer_16 extends Module {
  val io = IO(new Bundle{
    val in                = Input(Vec(16, UInt(8.W)))
    val load_enable       = Input(Bool())
    val sync_enable       = Input(Bool())
    val next_sync_enable  = Output(Bool())
    val out               = Output(UInt(8.W))
  })
  val shift_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.load_enable ){
    shift_reg := io.in
  } .elsewhen ( io.sync_enable ){
    for ( i <- 0 until 15 ){
      shift_reg(i) := shift_reg(i+1)
    }
    shift_reg(15) := 0.U
  }

  when ( io.sync_enalbe ) {
    io.out := shift_reg(0)
  } .otherwise {
    io.out := 0.U
  }

  next_sync_enable := sync_enable
}

// [4] data orchestration unit
class DataOrchUnit_16 extends Module {
  val io = IO(new Bundle{
    val in_mat      = Input(Vec(16, Vec(16, UInt(8.W))))
    val feed_enable = Input(Bool())
    val load_enable = Input(Bool())
    val skew_vec    = Output(Vec(16, UInt(8.W)))
  })

  val d_orch = Seq.fill(16)(Module(new Orch_buffer_16()))
  val feed_reg = RegInit(VecInit(Seq.fill(16)(false.B)))
 
  for( r<-0 until 16 ){
    d_orch(r).io.sync_enable := feed_reg(r)
    d_orch(r).io.load_enable := io.load_enable
    d_orch(r).io.in := io.in_mat(r)
    io.skew_vec(r) := d_orch(r).io.out
  }

  feed_reg(0) := io.feed_enable
  for ( i<-0 until 15 ){
    feed_reg(i+1) := feed_reg(i) 
  }
  
}

// [4] Chisel 6.x 문법에 맞춘 실행 객체
object MacUnitMain extends App {
  println("🚀 Generating SystemVerilog code for MacUnit (Chisel 6.7.0 + CIRCT)...")
  
  ChiselStage.emitSystemVerilogFile(
    new MacUnit(),
    Array("--target-dir", "generated")
  )
}