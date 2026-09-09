package npu.core

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable

// Test-only wiring of both future VPU1 routes. This is not the VPU1 router.
class GPALUQuantRouteHarness(quantFirst: Boolean, multiply: Boolean) extends Module {
  val io = IO(new Bundle {
    val a = Input(SInt(32.W))
    val b = Input(UInt(8.W))
    val valid = Input(Bool())
    val stall = Input(Bool())
    val wr = Input(Bool())
    val addr = Input(UInt(5.W))
    val data = Input(Vec(32, UInt(8.W)))
    val out = Output(UInt(8.W))
    val out_valid = Output(Bool())
    val alert = Output(Bool())
  })
  val q = Module(new QuantActCore(256, 10, 32, 8))
  val a = Module(new GPALUCore())
  q.io.stall := io.stall
  q.io.lut_wr_en := io.wr
  q.io.lut_wr_addr := io.addr
  q.io.lut_wr_data := io.data
  q.io.act_en := true.B
  // Post-ADD demonstrates activation-only; post-MUL rescales the exact product.
  q.io.quant_en := (quantFirst || multiply).B
  q.io.param := (if (multiply && !quantFirst) 0x10700 else 0x10000).U
  a.io.stall := io.stall
  a.io.mode := (if (multiply) GPALUMode.MUL else GPALUMode.ADD)
  a.io.out_shift := (if (multiply) 7 else 0).U
  if (quantFirst) {
    q.io.in_mac := io.a
    q.io.in_valid := io.valid
    a.io.in_a := q.io.out_qact
    a.io.in_b := ShiftRegister(io.b, 4, !io.stall)
    a.io.in_valid := q.io.out_valid
    io.out := a.io.out_res
    io.out_valid := a.io.out_valid
  } else {
    a.io.in_a := io.a.asUInt(7, 0)
    a.io.in_b := io.b
    a.io.in_valid := io.valid
    q.io.in_mac := a.io.out_wide.pad(32)
    q.io.in_valid := a.io.out_valid
    io.out := q.io.out_qact
    io.out_valid := q.io.out_valid
  }
  io.alert := q.io.sync_alert
}

class GPALUQuantRouteTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "GPALU / QuantAct route contracts"
  for (quantFirst <- Seq(true, false); multiply <- Seq(false, true)) {
    it should s"preserve operands across stalls with quantFirst=$quantFirst multiply=$multiply" in {
      test(new GPALUQuantRouteHarness(quantFirst, multiply)) { dut =>
        dut.io.a.poke(0.S)
        dut.io.b.poke(0.U)
        dut.io.valid.poke(false.B)
        dut.io.stall.poke(false.B)
        // A nonlinear signed LUT chosen to expose clipping-before-activation bugs.
        val lut = Array.tabulate(1024)(i => (((i * 13) ^ (i >> 2)) & 255))
        for (burst <- 0 until 32) {
          dut.io.wr.poke(true.B)
          dut.io.addr.poke(burst.U)
          for (i <- 0 until 32) dut.io.data(i).poke(lut(burst * 32 + i).U)
          dut.clock.step()
        }
        dut.io.wr.poke(false.B)
        val expected = mutable.Queue[Int]()
        def signed(x: Int): Int = if (x >= 128) x - 256 else x
        def activation(fixed: Int): Int = lut(math.max(0, math.min(1023, fixed + 512)))
        var accepted = 0
        for (cycle <- 0 until 230) {
          val stall = cycle < 200 && cycle % 13 >= 4 && cycle % 13 <= 7
          val valid = accepted < 100 && cycle % 9 != 0
          val x = if (quantFirst) (accepted * 29 % 700) - 350 else (accepted * 29 % 256) - 128
          val y = (accepted * 71 % 256) - 128
          dut.io.a.poke(x.S)
          dut.io.b.poke((y & 255).U)
          dut.io.valid.poke(valid.B)
          dut.io.stall.poke(stall.B)
          if (valid && !stall) {
            val gold = if (quantFirst) {
              val ax = signed(activation(x * 4))
              val raw = if (multiply) (ax * y) >> 7 else ax + y
              math.max(-128, math.min(127, raw)) & 255
            } else {
              val raw = if (multiply) x * y else x + y
              activation(if (multiply) (raw * 4) >> 7 else raw * 4)
            }
            expected.enqueue(gold)
            accepted += 1
          }
          if (stall) dut.io.out_valid.expect(false.B)
          else if (dut.io.out_valid.peek().litToBoolean) {
            assert(expected.nonEmpty)
            dut.io.out.expect(expected.dequeue().U)
          }
          dut.io.alert.expect(false.B)
          dut.clock.step()
        }
        assert(accepted == 100 && expected.isEmpty)
        dut.io.out_valid.expect(false.B)
      }
    }
  }
}
