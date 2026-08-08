package npu.core

import chisel3._
import chisel3.util._

// [15] Sequencer Shift buffer Line
class Sequencer_buffer_16 extends Module {
  val io = IO(new Bundle{
    val in_scalar   = Input(UInt(8.W))
    val in_valid    = Input(Bool())
    val clear       = Input(Bool())
    val out_vec     = Output(Vec(16, UInt(8.W)))
  })

  val shift_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.clear ){
    for ( i <- 0 until 16 ){
      shift_reg(i) := 0.U
    }
  } .elsewhen ( io.in_valid ){
    shift_reg(15) := io.in_scalar
    for ( i <- 0 until 15 ){
      shift_reg(i) := shift_reg(i+1)
    }
  }
  
  io.out_vec := Mux(io.out_enable, shift_reg, Seq.fill(16)(0.U(8.W)))
}

// [16] Sequencer for managing data flow (De-skew / Transpose)
class Sequencer_Single_16 extends Module {
  val io = IO(new Bundle {
    val in_vec    = Input(Vec(16, UInt(8.W)))
    val in_valid  = Input(Bool())
    
    val clear     = Input(Bool())
    val transpose = Input(Bool())

    val out_vec   = Output(Vec(16, UInt(8.W)))
    val out_valid = Output(Bool())
  })

  // 16 buffer instanciation
  val seq_buffers = Seq.fill(16)(Module(new Sequencer_buffer_16()))

  // 1. De-skew buffering horizontal valid signal chain
  val in_valid_chain = RegInit(VecInit(Seq.fill(16)(false.B)))
  
  when(io.clear) {
    in_valid_chain.foreach(_ := false.B)
  } .otherwise {
    in_valid_chain(0) := io.in_valid
    for (i <- 1 until 16) {
      in_valid_chain(i) := in_valid_chain(i-1)
    }
  }

  // 2. optimization : output streaming timing control
  // Normal mode : row by row stream
  val out_valid_normal = ShiftRegister(io.in_valid, 16, false.B, true.B)
  
  // Transpose mode: 15th row(final row) buffered then column by column streaming
  val out_valid_transp = ShiftRegister(in_valid_chain(15), 16, false.B, true.B)

  // valid selection
  val out_valid_stream = Mux(io.transpose, out_valid_transp, out_valid_normal)

  // Row/Col counter (0 ~ 15)
  val out_cnt = RegInit(0.U(4.W))
  when(io.clear) {
    out_cnt := 0.U
  } .elsewhen(out_valid_stream) {
    out_cnt := out_cnt + 1.U
  }

  // 3.input wiring
  for (r <- 0 until 16) {
    seq_buffers(r).io.in_scalar := io.in_vec(r)
    seq_buffers(r).io.in_valid  := in_valid_chain(r)
    seq_buffers(r).io.clear     := io.clear
  }

  // 4. 2D array mapping & output Mux tree
  val all_out_vecs = VecInit(seq_buffers.map(_.io.out_vec)) 
  val out_vec_wire = Wire(Vec(16, UInt(8.W)))
  
  for (i <- 0 until 16) {
    out_vec_wire(i) := Mux(
      io.transpose, 
      all_out_vecs(i)(out_cnt),    // Transpose: Column
      all_out_vecs(out_cnt)(i)     // Normal: Row
    )
  }
  
  // 5. final output
  val zeros     = VecInit(Seq.fill(16)(0.U(8.W)))
  io.out_vec    := Mux(out_valid_stream, out_vec_wire, zeros)
  io.out_valid  := out_valid_stream
}

// [17] Wrapper for Ping-Pong Sequencer
class Sequencer_PingPong_Wrapper extends Module {
  val io = IO(new Bundle {
    val in_vec    = Input(Vec(16, UInt(8.W)))
    val in_valid  = Input(Bool())
    val clear     = Input(Bool())
    val transpose = Input(Bool())

    val out_vec   = Output(Vec(16, UInt(8.W)))
    val out_valid = Output(Bool())
  })

  // 단일 모듈 인스턴스 2개 생성 (Bank A, Bank B)
  val seq_A = Module(new Sequencer_Single_16())
  val seq_B = Module(new Sequencer_Single_16())

  // --- 1. Ping-Pong Write 스위칭 로직 ---
  // in_valid가 들어올 때마다 타일 단위(16사이클)로 Bank를 토글
  val tile_cnt = RegInit(0.U(4.W))
  val bank_sel = RegInit(false.B) // false = Bank A, true = Bank B

  when(io.clear) {
    tile_cnt := 0.U
    bank_sel := false.B
  } .elsewhen(io.in_valid) {
    tile_cnt := tile_cnt + 1.U
    when(tile_cnt === 15.U) {
      bank_sel := ~bank_sel // 16개 데이터(1개 타일)를 다 받으면 뱅크 스위칭
    }
  }

  // 공통 입력 라우팅
  seq_A.io.in_vec := io.in_vec
  seq_B.io.in_vec := io.in_vec
  seq_A.io.clear  := io.clear
  seq_B.io.clear  := io.clear
  seq_A.io.transpose := io.transpose
  seq_B.io.transpose := io.transpose

  // Valid 신호 라우팅 (선택된 뱅크로만 데이터가 들어감)
  seq_A.io.in_valid := io.in_valid && (bank_sel === false.B)
  seq_B.io.in_valid := io.in_valid && (bank_sel === true.B)

  // --- 2. Ping-Pong Read 출력 멀티플렉싱 ---
  // 두 뱅크 중 자기 차례가 되어 out_valid를 올린 쪽의 데이터를 최종 출력
  io.out_valid := seq_A.io.out_valid || seq_B.io.out_valid

  io.out_vec := Mux(
                  seq_A.io.out_valid,
                  seq_A.io.out_vec,
                  Mux(
                    seq_B.io.out_valid, 
                    seq_B.io.out_vec,
                    VecInit(Seq.fill(16)(0.U(8.W)))
                  )
                )
}
