package npu.core

import chisel3._
import chisel3.util._

// ====================================================================
// [1] 단일 Unified Transposer Unit (무조건 Row Write -> 선택적 Col/Row Read)
// ====================================================================
class TransposerUnit(
  val numLines: Int, // 16 
  val dataBits: Int  // 8
) extends Module {
  val io = IO(new Bundle {
    val in_vec     = Input(Vec(numLines, UInt(dataBits.W)))
    val wr_en      = Input(Bool())
    val rd_en      = Input(Bool())
    val transpose  = Input(Bool()) // 1: Transpose (Column 읽기), 0: Bypass (Row 읽기)
    
    val out_vec    = Output(Vec(numLines, UInt(dataBits.W)))
    
    val is_full    = Output(Bool())
    val done_write = Output(Bool())
    val done_read  = Output(Bool())
  })

  // 16x16 2D Register Array
  val buffer = RegInit(VecInit(Seq.fill(numLines)(VecInit(Seq.fill(numLines)(0.U(dataBits.W))))))
  
  val wr_ptr = RegInit(0.U(log2Ceil(numLines).W))
  val rd_ptr = RegInit(0.U(log2Ceil(numLines).W))
  val full_reg = RegInit(false.B)

  val is_last_write = (wr_ptr === (numLines - 1).U)
  val is_last_read  = (rd_ptr === (numLines - 1).U)

  io.done_write := io.wr_en && is_last_write && !full_reg
  io.done_read  := io.rd_en && is_last_read && full_reg

  // ====================================================================
  // [WRITE] 무조건 Row 단위로 기록 (Input / Output 공통)
  // ====================================================================
  when(io.wr_en && !full_reg) {
    for (c <- 0 until numLines) {
      buffer(wr_ptr)(c) := io.in_vec(c)
    }
    wr_ptr := wr_ptr + 1.U
    when(is_last_write) { full_reg := true.B }
  }

  // ====================================================================
  // [READ] transpose 플래그에 따라 읽는 방향 결정
  // ====================================================================
  when(io.rd_en && full_reg) {
    rd_ptr := rd_ptr + 1.U
    when(is_last_read) { full_reg := false.B }
  }

  for (i <- 0 until numLines) {
    // transpose == 1 이면 세로(Column) 추출, 0 이면 가로(Row) 추출
    io.out_vec(i) := Mux(io.transpose, buffer(i)(rd_ptr), buffer(rd_ptr)(i))
  }
  
  io.is_full := full_reg
}

// ====================================================================
// [2] Unified Ping-Pong Transposer Top
// ====================================================================
class PingPongTransposer(
  val numLines: Int, 
  val dataBits: Int  
) extends Module {
  val io = IO(new Bundle {
    val in_vec          = Input(Vec(numLines, UInt(dataBits.W)))
    val in_valid        = Input(Bool())
    val transpose       = Input(Bool()) // 외부 제어 플래그
    
    val out_stream_en   = Input(Bool())
    val stall           = Input(Bool()) 
    
    val out_vec         = Output(Vec(numLines, UInt(dataBits.W)))
    val out_valid       = Output(Bool())
    
    val ready           = Output(Bool()) 
    val trans_alert     = Output(Bool()) 
  })

  val run = !io.stall 

  val ping = Module(new TransposerUnit(numLines, dataBits))
  val pong = Module(new TransposerUnit(numLines, dataBits))

  val write_side = RegInit(false.B) 
  val read_side  = RegInit(false.B) 

  // Write Routing
  ping.io.in_vec := io.in_vec
  pong.io.in_vec := io.in_vec
  ping.io.transpose := io.transpose
  pong.io.transpose := io.transpose

  ping.io.wr_en := io.in_valid && run && (write_side === false.B)
  pong.io.wr_en := io.in_valid && run && (write_side === true.B)

  when(ping.io.done_write) { write_side := true.B }
  when(pong.io.done_write) { write_side := false.B }

  // Read Routing
  ping.io.rd_en := io.out_stream_en && run && (read_side === false.B)
  pong.io.rd_en := io.out_stream_en && run && (read_side === true.B)

  when(ping.io.done_read) { read_side := true.B }
  when(pong.io.done_read) { read_side := false.B }

  // Output Mapping
  val current_read_full  = Mux(read_side === false.B, ping.io.is_full, pong.io.is_full)
  val current_write_full = Mux(write_side === false.B, ping.io.is_full, pong.io.is_full)
  
  val is_valid_read = io.out_stream_en && current_read_full && run
  io.out_valid := is_valid_read

  for(i <- 0 until numLines) {
    val selected_data = Mux(read_side === false.B, ping.io.out_vec(i), pong.io.out_vec(i))
    io.out_vec(i) := Mux(is_valid_read, selected_data, 0.U)
  }

  io.ready := !current_write_full
  io.trans_alert := io.in_valid && current_write_full && run
}