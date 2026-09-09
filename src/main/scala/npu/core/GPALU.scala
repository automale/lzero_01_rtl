package npu.core

import chisel3._
import chisel3.util._

object GPALUMode {
  val BYPASS = 0.U(2.W)
  val ADD    = 1.U(2.W)
  val MUL    = 2.U(2.W)
}

// Signed operands carried as two's-complement UInt bit patterns.
// Three enabled edges of latency; one vector per enabled cycle.
// out_wide preserves the exact result for ALU -> QuantAct/Activation.
// out_res is sat_signed(result >> out_shift) for terminal ALU operations.
// ADD requires equal operand scales; zero points must already be removed.
class GPALUCore(val inBits: Int = 8, val outBits: Int = 8) extends Module {
  require(inBits >= 2 && outBits >= 2 && outBits <= 2 * inBits)
  private val wideBits = 2 * inBits
  val io = IO(new Bundle {
    val in_a = Input(UInt(inBits.W))
    val in_b = Input(UInt(inBits.W))
    val in_valid = Input(Bool())
    val mode = Input(UInt(2.W))
    val out_shift = Input(UInt(5.W))
    val stall = Input(Bool())
    val out_res = Output(UInt(outBits.W))
    val out_wide = Output(SInt(wideBits.W))
    val out_valid = Output(Bool())
    val overflow = Output(Bool())
  })

  val run = !io.stall
  val s1A = RegEnable(io.in_a.asSInt, run)
  val s1B = RegEnable(io.in_b.asSInt, run)
  val s1Mode = RegEnable(io.mode, run)
  val s1Shift = RegEnable(io.out_shift, run)
  val s1Valid = RegEnable(io.in_valid, false.B, run)

  val result = Wire(SInt(wideBits.W))
  result := MuxLookup(s1Mode, s1A.pad(wideBits))(Seq(
    GPALUMode.ADD -> (s1A +& s1B).pad(wideBits),
    GPALUMode.MUL -> (s1A * s1B)
  ))
  val s2Result = RegEnable(result, run)
  val s2Shift = RegEnable(s1Shift, run)
  val s2Valid = RegEnable(s1Valid, false.B, run)

  val shifted = s2Result >> s2Shift
  val minOut = (-(BigInt(1) << (outBits - 1))).S(wideBits.W)
  val maxOut = ((BigInt(1) << (outBits - 1)) - 1).S(wideBits.W)
  val clipped = shifted < minOut || shifted > maxOut
  val saturated = Mux(shifted < minOut, minOut, Mux(shifted > maxOut, maxOut, shifted))

  io.out_wide := RegEnable(s2Result, run)
  io.out_res := RegEnable(saturated.asUInt(outBits - 1, 0), run)
  val outValid = RegEnable(s2Valid, false.B, run)
  val outClipped = RegEnable(clipped, false.B, run)
  io.out_valid := outValid && run
  io.overflow := outClipped && io.out_valid
}

class GPALUUnit(
  val numLines: Int = 16,
  val inBits: Int = 8,
  val outBits: Int = 8
) extends Module {
  require(numLines > 0)
  val io = IO(new Bundle {
    val in_vec_a = Input(Vec(numLines, UInt(inBits.W)))
    val in_vec_b = Input(Vec(numLines, UInt(inBits.W)))
    val in_valid = Input(Vec(numLines, Bool()))
    val mode = Input(UInt(2.W))
    val out_shift = Input(UInt(5.W))
    val stall = Input(Bool())
    val out_vec = Output(Vec(numLines, UInt(outBits.W)))
    val out_wide = Output(Vec(numLines, SInt((2 * inBits).W)))
    val out_valid = Output(Vec(numLines, Bool()))
    // Nonfatal clipping status for the narrow output, aligned with out_valid.
    val alu_alert = Output(Bool())
  })
  val cores = Seq.fill(numLines)(Module(new GPALUCore(inBits, outBits)))
  for (r <- 0 until numLines) {
    cores(r).io.in_a := io.in_vec_a(r)
    cores(r).io.in_b := io.in_vec_b(r)
    cores(r).io.in_valid := io.in_valid(r)
    cores(r).io.mode := io.mode
    cores(r).io.out_shift := io.out_shift
    cores(r).io.stall := io.stall
    io.out_vec(r) := cores(r).io.out_res
    io.out_wide(r) := cores(r).io.out_wide
    io.out_valid(r) := cores(r).io.out_valid
  }
  io.alu_alert := VecInit(cores.map(_.io.overflow)).asUInt.orR
}
