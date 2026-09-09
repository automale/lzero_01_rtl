package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable

class QuantActUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "QuantActUnit"

  private val NumLines = 16
  private val IndexBits = 10
  private val OutBits = 8
  private val WriteBits = 256
  private val LutEntries = 1 << IndexBits
  private val LutWordsPerBurst = WriteBits / OutBits

  private def packParam(mult: Int, shift: Int, zp: Int): BigInt = {
    require(mult >= 0 && mult <= 0xffff)
    require(shift >= 0 && shift <= 31)
    require(zp >= -128 && zp <= 127)
    (BigInt(mult) << 16) | (BigInt(shift) << 8) | BigInt(zp & 0xff)
  }

  private def signed8(x: Int): Int = {
    val y = x & 0xff
    if (y >= 128) y - 256 else y
  }

  private def quantGolden(in: BigInt, param: BigInt, actEn: Boolean, lut: Array[Int]): Int = {
    val zp = signed8((param & 0xff).toInt)
    val shift = ((param >> 8) & 0x1f).toInt
    val mult = ((param >> 16) & 0xffff).toInt
    val sub = in - BigInt(zp)
    val product = sub * BigInt(mult)
    if (actEn) {
      val fixed = (product << (IndexBits - OutBits)) >> shift
      val addr = (fixed + LutEntries / 2).max(BigInt(0)).min(BigInt(LutEntries - 1)).toInt
      lut(addr)
    } else {
      (product >> shift).max(BigInt(-128)).min(BigInt(127)).toInt & 0xff
    }
  }

  private def pokeIdle(dut: QuantActUnit): Unit = {
    for (i <- 0 until NumLines) {
      dut.io.in_vec(i).poke(0.S)
      dut.io.in_valid(i).poke(false.B)
      dut.io.qparam_line_in(i).poke(0.U)
    }
    dut.io.param_mode.poke(0.U)
    dut.io.matrix_param.poke(0.U)
    dut.io.quant_en.poke(true.B)
    dut.io.act_en.poke(false.B)
    dut.io.stall.poke(false.B)
    dut.io.soft_reset.poke(false.B)
    dut.io.qparam_line_valid.poke(false.B)
    dut.io.lut_wr_en.poke(false.B)
    dut.io.lut_wr_addr.poke(0.U)
    for (w <- 0 until LutWordsPerBurst) dut.io.lut_wr_data(w).poke(0.U)
  }

  private def programActivationLut(dut: QuantActUnit, lut: Array[Int]): Unit = {
    require(lut.length == LutEntries)
    for (burst <- 0 until LutEntries / LutWordsPerBurst) {
      dut.io.lut_wr_en.poke(true.B)
      dut.io.lut_wr_addr.poke(burst.U)
      for (w <- 0 until LutWordsPerBurst) {
        val idx = burst * LutWordsPerBurst + w
        dut.io.lut_wr_data(w).poke(lut(idx).U(OutBits.W))
      }
      dut.clock.step()
      dut.io.sync_alert.expect(false.B)
    }
    dut.io.lut_wr_en.poke(false.B)
    var guard = 0
    while (!dut.io.lut_ready.peek().litToBoolean && guard < 8) {
      dut.clock.step()
      dut.io.sync_alert.expect(false.B)
      guard += 1
    }
    dut.io.lut_ready.expect(true.B)
  }

  private def drain(dut: QuantActUnit, expected: mutable.Queue[Array[Int]], maxCycles: Int = 32): Unit = {
    var guard = 0
    while (expected.nonEmpty && guard < maxCycles) {
      if (dut.io.out_valid(0).peek().litToBoolean) {
        val gold = expected.dequeue()
        for (i <- 0 until NumLines) {
          dut.io.out_valid(i).expect(true.B)
          dut.io.out_vec(i).expect(gold(i).U(OutBits.W))
        }
      }
      dut.io.sync_alert.expect(false.B)
      dut.clock.step()
      guard += 1
    }
    assert(expected.isEmpty, s"Timed out with ${expected.size} QuantAct outputs pending")
  }

  it should "broadcast one PER_MATRIX parameter and support activation LUT / linear bypass" in {
    test(new QuantActUnit(numLines = NumLines, writeBits = WriteBits, indexBits = IndexBits, inBits = 32, outBits = OutBits)) { dut =>
      pokeIdle(dut)
      dut.io.soft_reset.poke(true.B)
      dut.clock.step()
      dut.io.soft_reset.poke(false.B)

      val lut = Array.tabulate(LutEntries)(i => (i * 29 + 11) & 0xff)
      programActivationLut(dut, lut)

      val matrixParam = packParam(mult = 3, shift = 4, zp = -5)
      dut.io.param_mode.poke(0.U)
      dut.io.matrix_param.poke(matrixParam.U(32.W))
      dut.io.qparam_req_line.expect(false.B)
      dut.io.prefetch_ready.expect(true.B)

      for (actEn <- Seq(false, true)) {
        dut.io.act_en.poke(actEn.B)
        val expected = mutable.Queue[Array[Int]]()

        for (row <- 0 until 6) {
          val gold = Array.ofDim[Int](NumLines)
          for (lane <- 0 until NumLines) {
            val x = BigInt(-150 + row * 37 + lane * 19)
            dut.io.in_vec(lane).poke(x.S(32.W))
            dut.io.in_valid(lane).poke(true.B)
            gold(lane) = quantGolden(x, matrixParam, actEn, lut)
          }
          expected.enqueue(gold)
          dut.io.qparam_req_line.expect(false.B)
          dut.io.sync_alert.expect(false.B)
          if (dut.io.out_valid(0).peek().litToBoolean) {
            val prior = expected.dequeue()
            for (lane <- 0 until NumLines) {
              dut.io.out_valid(lane).expect(true.B)
              dut.io.out_vec(lane).expect(prior(lane).U(OutBits.W))
            }
          }
          dut.clock.step()
        }

        for (lane <- 0 until NumLines) {
          dut.io.in_valid(lane).poke(false.B)
          dut.io.in_vec(lane).poke(0.S)
        }
        drain(dut, expected)
      }
    }
  }

  it should "prefetch 64B PER_CHANNEL lines, swap shadow to active every 16 accepted rows, and tolerate stalls" in {
    test(new QuantActUnit(numLines = NumLines, writeBits = WriteBits, indexBits = IndexBits, inBits = 32, outBits = OutBits)) { dut =>
      pokeIdle(dut)
      dut.io.soft_reset.poke(true.B)
      dut.clock.step()
      dut.io.soft_reset.poke(false.B)

      val lut = Array.tabulate(LutEntries)(i => i & 0xff)
      programActivationLut(dut, lut)
      dut.io.param_mode.poke(1.U)
      dut.io.act_en.poke(false.B)

      val lineA = Array.tabulate(NumLines) { lane =>
        packParam(1 + (lane % 4), 3 + (lane % 3), lane - 8)
      }
      val lineB = Array.tabulate(NumLines) { lane =>
        packParam(5 + (lane % 5), 4 + (lane % 2), 7 - lane)
      }
      val qbLines = Array(lineA, lineB)
      var nextQbLine = 0
      var pendingResponse: Option[Array[BigInt]] = None
      var requestCount = 0

      def drivePendingResponse(): Unit = pendingResponse match {
        case Some(line) =>
          dut.io.qparam_line_valid.poke(true.B)
          for (lane <- 0 until NumLines) dut.io.qparam_line_in(lane).poke(line(lane).U(32.W))
        case None =>
          dut.io.qparam_line_valid.poke(false.B)
          for (lane <- 0 until NumLines) dut.io.qparam_line_in(lane).poke(0.U)
      }

      def sampleRequest(): Option[Array[BigInt]] = {
        if (dut.io.qparam_req_line.peek().litToBoolean) {
          val line = qbLines(nextQbLine)
          nextQbLine = (nextQbLine + 1) % qbLines.length
          requestCount += 1
          Some(line)
        } else None
      }

      var prefetchGuard = 0
      while (!dut.io.prefetch_ready.peek().litToBoolean && prefetchGuard < 16) {
        drivePendingResponse()
        for (lane <- 0 until NumLines) {
          dut.io.in_valid(lane).poke(false.B)
          dut.io.in_vec(lane).poke(0.S)
        }
        val next = sampleRequest()
        dut.io.sync_alert.expect(false.B)
        dut.clock.step()
        pendingResponse = next
        prefetchGuard += 1
      }
      dut.io.prefetch_ready.expect(true.B)
      assert(requestCount == 1, s"Expected one initial QB request, got $requestCount")

      val expected = mutable.Queue[Array[Int]]()
      val totalAcceptedRows = 3 * 16
      var acceptedRows = 0
      var physicalCycle = 0

      while ((acceptedRows < totalAcceptedRows || expected.nonEmpty) && physicalCycle < 300) {
        drivePendingResponse()
        val stallNow = (physicalCycle % 17 == 6) || (physicalCycle % 29 == 11)
        dut.io.stall.poke(stallNow.B)
        val driveInput = acceptedRows < totalAcceptedRows

        if (driveInput) {
          val tile = acceptedRows / 16
          val row = acceptedRows % 16
          val params = qbLines(tile % qbLines.length)
          val gold = Array.ofDim[Int](NumLines)
          for (lane <- 0 until NumLines) {
            val x = BigInt(-180 + tile * 31 + row * 7 + lane * 5)
            dut.io.in_vec(lane).poke(x.S(32.W))
            dut.io.in_valid(lane).poke(true.B)
            gold(lane) = quantGolden(x, params(lane), actEn = false, lut)
          }
          if (!stallNow) expected.enqueue(gold)
        } else {
          for (lane <- 0 until NumLines) {
            dut.io.in_vec(lane).poke(0.S)
            dut.io.in_valid(lane).poke(false.B)
          }
        }

        if (!stallNow && dut.io.out_valid(0).peek().litToBoolean) {
          assert(expected.nonEmpty, "QuantAct output with empty scoreboard")
          val gold = expected.dequeue()
          for (lane <- 0 until NumLines) {
            dut.io.out_valid(lane).expect(true.B)
            dut.io.out_vec(lane).expect(gold(lane).U(OutBits.W))
          }
        }

        val next = sampleRequest()
        if (stallNow) for (lane <- 0 until NumLines) dut.io.out_valid(lane).expect(false.B)
        dut.io.sync_alert.expect(false.B)
        dut.clock.step()
        pendingResponse = next
        if (driveInput && !stallNow) acceptedRows += 1
        physicalCycle += 1
      }

      assert(acceptedRows == totalAcceptedRows)
      assert(expected.isEmpty)
      assert(requestCount >= 4, s"Expected >=4 QB requests, got $requestCount")
    }
  }

  it should "support signed extrema, activation-only, bypass and per-beat controls through long stalls" in {
    test(new QuantActUnit()) { dut =>
      pokeIdle(dut)
      // Linear quantization must not require a programmed activation table.
      dut.io.prefetch_ready.expect(true.B)
      val lut = Array.tabulate(LutEntries)(i => ((i * 37) ^ (i >> 3)) & 0xff)
      programActivationLut(dut, lut)
      val expected = mutable.Queue[Array[Int]]()
      val values = Seq(BigInt(Int.MinValue), BigInt(-16384), BigInt(-129), BigInt(-128),
        BigInt(-1), BigInt(0), BigInt(1), BigInt(127), BigInt(128), BigInt(16384), BigInt(Int.MaxValue))
      var accepted = 0
      for (cycle <- 0 until 260) {
        val stalled = cycle < 220 && (cycle % 19 >= 4 && cycle % 19 <= 8)
        val valid = accepted < 120 && cycle % 7 != 0
        val quant = accepted % 4 < 2
        val act = accepted % 2 == 0
        val param = packParam(Seq(0, 1, 3, 65535)((accepted / 4) % 4), Seq(0, 1, 2, 7, 31)((accepted / 16) % 5), -7)
        dut.io.stall.poke(stalled.B)
        dut.io.quant_en.poke(quant.B)
        dut.io.act_en.poke(act.B)
        // Activation-only/bypass must not fetch QB even if param_mode is channel.
        dut.io.param_mode.poke((if (quant) 0 else 1).U)
        dut.io.matrix_param.poke(param.U)
        dut.io.qparam_req_line.expect(false.B)
        val row = Array.tabulate(NumLines) { i =>
          val x = values((accepted + i) % values.size)
          dut.io.in_vec(i).poke(x.S)
          dut.io.in_valid(i).poke(valid.B)
          quantGolden(x, if (quant) param else packParam(1, 0, 0), act, lut)
        }
        if (!stalled && valid) { expected.enqueue(row); accepted += 1 }
        if (stalled) dut.io.out_valid(0).expect(false.B)
        else if (dut.io.out_valid(0).peek().litToBoolean) {
          assert(expected.nonEmpty)
          val gold = expected.dequeue()
          for (i <- 0 until NumLines) {
            dut.io.out_valid(i).expect(true.B)
            dut.io.out_vec(i).expect(gold(i).U)
          }
        }
        dut.io.sync_alert.expect(false.B)
        dut.clock.step()
      }
      assert(accepted == 120)
      assert(expected.isEmpty)
      dut.io.out_valid(0).expect(false.B)
    }
  }

  it should "flag a partial-lane valid beat" in {
    test(new QuantActUnit()) { dut =>
      pokeIdle(dut)
      dut.io.param_mode.poke(0.U)
      dut.io.matrix_param.poke(packParam(1, 2, 0).U)
      for (lane <- 0 until NumLines) dut.io.in_valid(lane).poke((lane != 15).B)
      dut.io.sync_alert.expect(true.B)
    }
  }
}
