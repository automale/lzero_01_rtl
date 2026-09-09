package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class GPALUUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "GPALUUnit"

  it should "preserve signed results and align saturation, control, bubbles and stalls" in {
    test(new GPALUUnit()) { dut =>
      val random = new Random(0xA108)
      case class Lane(raw: Int, narrow: Int, clipped: Boolean, valid: Boolean)
      var pipe = List.fill(3)(Option.empty[Seq[Lane]])
      val edges = Seq(-128, -127, -65, -1, 0, 1, 2, 63, 126, 127)
      for (cycle <- 0 until 850) {
        val stall = cycle < 820 && (cycle % 17 == 3 || cycle % 17 == 4 || random.nextInt(11) == 0)
        val mode = cycle % 4 // reserved mode 3 retains BYPASS behavior
        val shift = if (cycle < 400) 0 else Seq(0, 1, 2, 7, 8, 15, 31)(cycle % 7)
        dut.io.mode.poke(mode.U)
        dut.io.out_shift.poke(shift.U)
        dut.io.stall.poke(stall.B)
        val beat = (0 until 16).map { lane =>
          val a = if (cycle < 400) edges((cycle / 4 + lane) % edges.size) else random.nextInt(256) - 128
          val b = if (cycle < 400) edges((cycle / 40 + lane) % edges.size) else random.nextInt(256) - 128
          val valid = cycle < 820 && random.nextInt(5) != 0
          dut.io.in_vec_a(lane).poke((a & 255).U)
          dut.io.in_vec_b(lane).poke((b & 255).U)
          dut.io.in_valid(lane).poke(valid.B)
          val raw = mode match { case 1 => a + b; case 2 => a * b; case _ => a }
          val scaled = raw >> shift
          Lane(raw, math.max(-128, math.min(127, scaled)), scaled < -128 || scaled > 127, valid)
        }
        if (stall) {
          for (i <- 0 until 16) dut.io.out_valid(i).expect(false.B)
          dut.io.alu_alert.expect(false.B)
        } else {
          val prior = pipe.last
          for (i <- 0 until 16) {
            val valid = prior.exists(_(i).valid)
            dut.io.out_valid(i).expect(valid.B)
            if (valid) {
              dut.io.out_wide(i).expect(prior.get(i).raw.S)
              dut.io.out_vec(i).expect((prior.get(i).narrow & 255).U)
            }
          }
          dut.io.alu_alert.expect(prior.exists(_.exists(x => x.valid && x.clipped)).B)
          pipe = Some(beat) :: pipe.take(2)
        }
        dut.clock.step()
      }
      for (i <- 0 until 16) dut.io.out_valid(i).expect(false.B)
    }
  }

  it should "allow full-width output without invalid add bit slices" in {
    test(new GPALUCore(inBits = 8, outBits = 16)) { dut =>
      dut.io.in_a.poke(128.U)
      dut.io.in_b.poke(128.U)
      dut.io.mode.poke(GPALUMode.MUL)
      dut.io.out_shift.poke(0.U)
      dut.io.in_valid.poke(true.B)
      dut.io.stall.poke(false.B)
      dut.clock.step(3)
      dut.io.out_wide.expect(16384.S)
      dut.io.out_res.expect(16384.U)
      dut.io.overflow.expect(false.B)
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.io.out_valid.expect(false.B)
      dut.io.overflow.expect(false.B)
    }
  }
}
