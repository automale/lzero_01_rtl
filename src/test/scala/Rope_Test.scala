package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.mutable

class RopeUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "RopeUnit"

  private val NumLines = 16
  private val NumPairs = 8
  private val IndexBits = 10
  private val TrigBits = 16
  private val TrigFracBits = 14
  private val WriteBits = 256
  private val FreqBits = 16
  private val LutEntries = 1 << IndexBits
  private val LutWordsPerBurst = WriteBits / TrigBits
  private val Scale = 1 << TrigFracBits

  private def toUInt16(x: Int): Int = x & 0xffff

  private def satSigned8(x: BigInt): Int = {
    if (x > 127) 127
    else if (x < -128) -128
    else x.toInt
  }

  private val cosLut: Array[Int] = Array.tabulate(LutEntries) { i =>
    val angle = 2.0 * math.Pi * i.toDouble / LutEntries.toDouble
    val raw = math.round(math.cos(angle) * Scale.toDouble).toInt
    math.max(-32768, math.min(32767, raw))
  }

  private val sinLut: Array[Int] = Array.tabulate(LutEntries) { i =>
    val angle = 2.0 * math.Pi * i.toDouble / LutEntries.toDouble
    val raw = math.round(math.sin(angle) * Scale.toDouble).toInt
    math.max(-32768, math.min(32767, raw))
  }

  private def ropePairGolden(xEven: Int, xOdd: Int, m: Int, theta: Int): (Int, Int) = {
    val phase = (BigInt(m) * BigInt(theta)) & 0xffff
    val idx = (phase >> (FreqBits - IndexBits)).toInt
    val c = BigInt(cosLut(idx))
    val s = BigInt(sinLut(idx))
    val even = (BigInt(xEven) * c - BigInt(xOdd) * s) >> TrigFracBits
    val odd  = (BigInt(xEven) * s + BigInt(xOdd) * c) >> TrigFracBits
    (satSigned8(even), satSigned8(odd))
  }

  private def pokeIdle(dut: RopeUnit): Unit = {
    for (i <- 0 until NumLines) {
      dut.io.in_vec(i).poke(0.S)
      dut.io.in_valid(i).poke(false.B)
    }
    for (i <- 0 until 32) dut.io.freq_line_in(i).poke(0.U)
    dut.io.rope_en.poke(false.B)
    dut.io.stall.poke(false.B)
    dut.io.soft_reset.poke(false.B)
    dut.io.row_change_update.poke(false.B)
    dut.io.position_init.poke(false.B)
    dut.io.base_m_in.poke(0.U)
    dut.io.freq_line_valid.poke(false.B)
    dut.io.lut_cos_wr_en.poke(false.B)
    dut.io.lut_sin_wr_en.poke(false.B)
    dut.io.lut_wr_addr.poke(0.U)
    for (w <- 0 until LutWordsPerBurst) dut.io.lut_wr_data(w).poke(0.U)
  }

  private def programTrigLut(dut: RopeUnit, isCos: Boolean, table: Array[Int]): Unit = {
    require(table.length == LutEntries)
    for (burst <- 0 until LutEntries / LutWordsPerBurst) {
      dut.io.lut_cos_wr_en.poke(isCos.B)
      dut.io.lut_sin_wr_en.poke((!isCos).B)
      dut.io.lut_wr_addr.poke(burst.U)
      for (w <- 0 until LutWordsPerBurst) {
        val idx = burst * LutWordsPerBurst + w
        dut.io.lut_wr_data(w).poke(toUInt16(table(idx)).U(TrigBits.W))
      }
      dut.clock.step()
      dut.io.sync_alert.expect(false.B)
    }
    dut.io.lut_cos_wr_en.poke(false.B)
    dut.io.lut_sin_wr_en.poke(false.B)
    dut.clock.step()
    dut.io.sync_alert.expect(false.B)
  }

  private def programAllTrigLuts(dut: RopeUnit): Unit = {
    programTrigLut(dut, isCos = true, cosLut)
    programTrigLut(dut, isCos = false, sinLut)
    var guard = 0
    while (!dut.io.lut_ready.peek().litToBoolean && guard < 8) {
      dut.clock.step()
      dut.io.sync_alert.expect(false.B)
      guard += 1
    }
    dut.io.lut_ready.expect(true.B)
  }

  it should "bypass data and valid combinationally when RoPE is disabled" in {
    test(new RopeUnit()) { dut =>
      pokeIdle(dut)
      dut.io.rope_en.poke(false.B)
      for (lane <- 0 until NumLines) {
        val x = lane - 8
        dut.io.in_vec(lane).poke(x.S(8.W))
        dut.io.in_valid(lane).poke(true.B)
        dut.io.out_vec(lane).expect(x.S(8.W))
        dut.io.out_valid(lane).expect(true.B)
      }
      dut.io.freq_req_line.expect(false.B)
      dut.io.sync_alert.expect(false.B)
    }
  }

  it should "generate contiguous m internally, consume 8 frequencies per tile, and swap 64B FB lines every four tiles" in {
    test(new RopeUnit(
      numLines = NumLines,
      inBits = 8,
      outBits = 8,
      indexBits = IndexBits,
      trigBits = TrigBits,
      writeBits = WriteBits,
      trigFracBits = TrigFracBits,
      freqBits = FreqBits,
      mBits = 32
    )) { dut =>
      pokeIdle(dut)
      dut.io.soft_reset.poke(true.B)
      dut.clock.step()
      dut.io.soft_reset.poke(false.B)
      programAllTrigLuts(dut)

      val baseM = 5
      dut.io.position_init.poke(true.B)
      dut.io.base_m_in.poke(baseM.U)
      dut.clock.step()
      dut.io.position_init.poke(false.B)

      val freqLine0 = Array.tabulate(32)(i => (257 + i * 131) & 0xffff)
      val freqLine1 = Array.tabulate(32)(i => (503 + i * 173) & 0xffff)
      val fbLines = Array(freqLine0, freqLine1)
      var nextFbLine = 0
      var pendingResponse: Option[Array[Int]] = None
      var requestCount = 0

      dut.io.rope_en.poke(true.B)

      def drivePendingResponse(): Unit = pendingResponse match {
        case Some(line) =>
          dut.io.freq_line_valid.poke(true.B)
          for (i <- 0 until 32) dut.io.freq_line_in(i).poke(line(i).U(FreqBits.W))
        case None =>
          dut.io.freq_line_valid.poke(false.B)
          for (i <- 0 until 32) dut.io.freq_line_in(i).poke(0.U)
      }

      def sampleRequest(): Option[Array[Int]] = {
        if (dut.io.freq_req_line.peek().litToBoolean) {
          val line = fbLines(nextFbLine)
          nextFbLine = (nextFbLine + 1) % fbLines.length
          requestCount += 1
          Some(line)
        } else None
      }

      var prefetchGuard = 0
      while (!dut.io.prefetch_ready.peek().litToBoolean && prefetchGuard < 24) {
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
      assert(requestCount == 1, s"Expected one initial FB request, got $requestCount")

      val expected = mutable.Queue[Array[Int]]()
      val nTilesPerMGroup = 8
      val mGroups = 2
      val totalTiles = nTilesPerMGroup * mGroups
      val totalAcceptedRows = totalTiles * 16
      var acceptedRows = 0
      var physicalCycle = 0

      while ((acceptedRows < totalAcceptedRows || expected.nonEmpty) && physicalCycle < 1000) {
        drivePendingResponse()
        val stallNow =
          (physicalCycle % 41 == 13) ||
          (physicalCycle % 41 == 14) ||
          (physicalCycle % 67 == 22)
        dut.io.stall.poke(stallNow.B)
        val driveInput = acceptedRows < totalAcceptedRows

        if (driveInput) {
          val tile = acceptedRows / 16
          val row = acceptedRows % 16
          val mGroup = tile / nTilesPerMGroup
          val nTile = tile % nTilesPerMGroup
          val line = fbLines(nTile / 4)
          val group = nTile % 4
          val m = baseM + mGroup * 16 + row
          val gold = Array.ofDim[Int](NumLines)

          for (pair <- 0 until NumPairs) {
            val theta = line(group * NumPairs + pair)
            val xEven = 9 + ((tile + row + pair * 3) % 19)
            val xOdd = -11 + ((tile * 2 + row + pair * 5) % 17)
            dut.io.in_vec(2 * pair).poke(xEven.S(8.W))
            dut.io.in_vec(2 * pair + 1).poke(xOdd.S(8.W))
            val (yEven, yOdd) = ropePairGolden(xEven, xOdd, m, theta)
            gold(2 * pair) = yEven
            gold(2 * pair + 1) = yOdd
          }

          for (lane <- 0 until NumLines) dut.io.in_valid(lane).poke(true.B)
          val rowChange = (row == 0) && (nTile == 0)
          dut.io.row_change_update.poke(rowChange.B)
          if (!stallNow) expected.enqueue(gold)
        } else {
          dut.io.row_change_update.poke(false.B)
          for (lane <- 0 until NumLines) {
            dut.io.in_valid(lane).poke(false.B)
            dut.io.in_vec(lane).poke(0.S)
          }
        }

        if (!stallNow && dut.io.out_valid(0).peek().litToBoolean) {
          assert(expected.nonEmpty, "RoPE output with empty scoreboard")
          val gold = expected.dequeue()
          for (lane <- 0 until NumLines) {
            dut.io.out_valid(lane).expect(true.B)
            dut.io.out_vec(lane).expect(gold(lane).S(8.W))
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

      assert(acceptedRows == totalAcceptedRows, s"Accepted $acceptedRows / $totalAcceptedRows rows")
      assert(expected.isEmpty, s"${expected.size} RoPE outputs left in scoreboard")
      assert(requestCount >= 5, s"Expected >=5 FB requests, got $requestCount")
    }
  }

  it should "raise DFD when normal RoPE traffic starts before position/frequency preload" in {
    test(new RopeUnit()) { dut =>
      pokeIdle(dut)
      dut.io.rope_en.poke(true.B)
      for (lane <- 0 until NumLines) {
        dut.io.in_valid(lane).poke(true.B)
        dut.io.in_vec(lane).poke(1.S)
      }
      dut.io.row_change_update.poke(true.B)
      dut.io.sync_alert.expect(true.B)
    }
  }
}
