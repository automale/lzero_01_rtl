package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable

class NormUnitOnlineTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "UniversalNormUnit ONLINE mode"

  // --------------------------------------------------------------------------
  // Test configuration
  // --------------------------------------------------------------------------
  private val NumLines     = 16
  private val InBits       = 8
  private val OutBits      = 8
  private val IndexBits    = 8
  private val DataBits     = 16
  private val WriteBits    = 256
  private val LutFracBits  = 14
  private val InvBits      = 24
  private val InvFracBits  = 20
  private val MaxVectorLen = 64
  private val MetadataDepth = 256

  // MaxVectorLen=64 keeps DUT accBits at the minimum 32-bit setting.
  private val AccBits =
    math.max(32, 2 * InBits + log2CeilScala(MaxVectorLen) + 3)

  private val OneQ =
    1L << LutFracBits

  private val InvSqrt2Q =
    math.round((1.0 / math.sqrt(2.0)) * OneQ.toDouble)

  private val ScaleBits =
    DataBits + LutFracBits + 6

  private val WordsPerBurst =
    WriteBits / DataBits

  private val NumLutEntries =
    1 << IndexBits

  private val NumBursts =
    NumLutEntries / WordsPerBurst

  private def log2CeilScala(x: Int): Int = {
    require(x > 0)
    32 - Integer.numberOfLeadingZeros(x - 1)
  }

  private def mask(bits: Int): BigInt =
    (BigInt(1) << bits) - 1

  private def asUInt8(x: Int): Int =
    x & 0xff

  private def signedFromUInt8(x: Int): Int = {
    val u = x & 0xff
    if ((u & 0x80) != 0) u - 256 else u
  }

  private def satSigned8ToBits(x: BigInt): Int = {
    val clipped =
      if (x > 127) 127
      else if (x < -128) -128
      else x.toInt

    clipped & 0xff
  }

  // --------------------------------------------------------------------------
  // LUT generation
  //
  // The test programs the exact tables used by the software golden model.
  // Therefore the test checks datapath/control correctness without depending
  // on an external LUT image.
  // --------------------------------------------------------------------------
  private def expTable(): Vector[Int] = {
    Vector.tabulate(NumLutEntries) { delta =>
      val raw =
        math.round(math.exp(-delta.toDouble) * OneQ.toDouble)

      math.max(
        0L,
        math.min(
          (1L << DataBits) - 1L,
          raw
        )
      ).toInt
    }
  }

  // normalizedIndex() places a non-zero normalized integer in [128,255]
  // when IndexBits=8. idx/128 therefore represents mantissa [1,2).
  private def mantissaFromIndex(idx: Int): Double = {
    val clamped =
      math.max(1 << (IndexBits - 1), idx)

    clamped.toDouble /
      (1 << (IndexBits - 1)).toDouble
  }

  private def rsqrtScaleTable(): Vector[Int] = {
    Vector.tabulate(NumLutEntries) { idx =>
      val m = mantissaFromIndex(idx)

      val raw =
        math.round(
          (1.0 / math.sqrt(m)) *
          OneQ.toDouble
        )

      math.max(
        0L,
        math.min(
          (1L << DataBits) - 1L,
          raw
        )
      ).toInt
    }
  }

  private def reciprocalScaleTable(): Vector[Int] = {
    Vector.tabulate(NumLutEntries) { idx =>
      val m = mantissaFromIndex(idx)

      val raw =
        math.round(
          (1.0 / m) *
          OneQ.toDouble
        )

      math.max(
        0L,
        math.min(
          (1L << DataBits) - 1L,
          raw
        )
      ).toInt
    }
  }

  // --------------------------------------------------------------------------
  // Exact fixed-point helpers matching NormUnit.scala
  // --------------------------------------------------------------------------
  private def normalizedIndex(xIn: BigInt): (Int, Int) = {
    val x =
      xIn & mask(AccBits)

    if (x == 0) {
      (0, 0)
    } else {
      val exp =
        x.bitLength - 1

      val lz =
        (AccBits - 1) - exp

      val shifted =
        (x << lz) &
        mask(AccBits)

      val idx =
        (
          shifted >>
          (AccBits - IndexBits)
        ).toInt &
        ((1 << IndexBits) - 1)

      (idx, exp)
    }
  }

  private def adjustRsqrtScale(
    raw: BigInt,
    exp: Int
  ): BigInt = {

    val oddCorrected =
      if ((exp & 1) != 0) {
        (
          raw *
          BigInt(InvSqrt2Q)
        ) >> LutFracBits
      } else {
        raw
      }

    (
      oddCorrected >>
      (exp >> 1)
    ) & mask(ScaleBits)
  }

  private def adjustRecipScale(
    raw: BigInt,
    signedExp: Int
  ): BigInt = {

    val shifted =
      if (signedExp >= 0) {
        raw >> signedExp
      } else {
        raw << (-signedExp)
      }

    shifted & mask(ScaleBits)
  }

  private def invNQ(n: Int): BigInt = {
    require(n > 0)

    BigInt(
      math.round(
        (1.0 / n.toDouble) *
        (1L << InvFracBits).toDouble
      )
    )
  }

  private def meanSigned(
    sum: BigInt,
    invN: BigInt
  ): BigInt = {
    (sum * invN) >> InvFracBits
  }

  private def meanUnsigned(
    sum: BigInt,
    invN: BigInt
  ): BigInt = {
    (sum * invN) >> InvFracBits
  }

  private def scaleFromRmsOrLayerStat(
    stat: BigInt,
    scaleTable: Vector[Int]
  ): BigInt = {

    val (idx, exp) =
      normalizedIndex(stat)

    adjustRsqrtScale(
      BigInt(scaleTable(idx)),
      exp
    )
  }

  private def scaleFromSoftmaxSum(
    sumQ: BigInt,
    scaleTable: Vector[Int]
  ): BigInt = {

    val (idx, expInt) =
      normalizedIndex(sumQ)

    val realExp =
      expInt - LutFracBits

    adjustRecipScale(
      BigInt(scaleTable(idx)),
      realExp
    )
  }

  private def goldenRms(
    row: Vector[Vector[Int]],
    epsilon: BigInt,
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val n =
      row.length

    val invN =
      invNQ(n)

    Vector.tabulate(n, NumLines) { (beat, lane) =>
      val laneVec =
        row.map(_(lane))

      val sqSum =
        laneVec.foldLeft(BigInt(0)) { (acc, x) =>
          acc + BigInt(x) * BigInt(x)
        }

      val meanSq =
        meanUnsigned(
          sqSum,
          invN
        )

      val stat =
        meanSq + epsilon

      val scale =
        scaleFromRmsOrLayerStat(
          stat,
          scaleTable
        )

      val product =
        BigInt(row(beat)(lane)) *
        scale

      val scaled =
        product >> LutFracBits

      satSigned8ToBits(scaled)
    }
  }

  private def goldenLayer(
    row: Vector[Vector[Int]],
    epsilon: BigInt,
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val n =
      row.length

    val invN =
      invNQ(n)

    val laneMean =
      Vector.tabulate(NumLines) { lane =>
        val sum =
          row.foldLeft(BigInt(0)) {
            (acc, beat) =>
              acc + BigInt(beat(lane))
          }

        meanSigned(
          sum,
          invN
        )
      }

    val laneScale =
      Vector.tabulate(NumLines) { lane =>
        val sqSum =
          row.foldLeft(BigInt(0)) {
            (acc, beat) =>
              val x =
                BigInt(beat(lane))

              acc + x * x
          }

        val ex2 =
          meanUnsigned(
            sqSum,
            invN
          )

        val mean =
          laneMean(lane)

        val meanSq =
          mean * mean

        val variance =
          if (ex2 >= meanSq) {
            ex2 - meanSq
          } else {
            BigInt(0)
          }

        scaleFromRmsOrLayerStat(
          variance + epsilon,
          scaleTable
        )
      }

    Vector.tabulate(n, NumLines) {
      (beat, lane) =>

        val centered =
          BigInt(row(beat)(lane)) -
          laneMean(lane)

        val product =
          centered *
          laneScale(lane)

        val scaled =
          product >> LutFracBits

        satSigned8ToBits(scaled)
    }
  }

  private case class SoftState(
    max: Int,
    sumQ: BigInt
  )

  // Explicit Milakov/Gimelshein recurrence.
  private def softmaxMilakovState(
    laneVec: Vector[Int],
    expLut: Vector[Int]
  ): SoftState = {

    require(laneVec.nonEmpty)

    var m =
      laneVec.head

    var d =
      BigInt(OneQ)

    laneVec.tail.foreach { x =>

      if (x > m) {
        val delta =
          math.min(
            NumLutEntries - 1,
            x - m
          )

        val factor =
          BigInt(expLut(delta))

        d =
          (
            (d * factor) >>
            LutFracBits
          ) +
          BigInt(OneQ)

        m = x

      } else {
        val delta =
          math.min(
            NumLutEntries - 1,
            m - x
          )

        val factor =
          BigInt(expLut(delta))

        d =
          d + factor
      }
    }

    SoftState(
      max = m,
      sumQ = d
    )
  }

  private def goldenSoftmax(
    row: Vector[Vector[Int]],
    expLut: Vector[Int],
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val n =
      row.length

    val state =
      Vector.tabulate(NumLines) { lane =>
        softmaxMilakovState(
          row.map(_(lane)),
          expLut
        )
      }

    val scales =
      state.map { st =>
        scaleFromSoftmaxSum(
          st.sumQ,
          scaleTable
        )
      }

    val outputShift =
      math.max(
        0,
        2 * LutFracBits - OutBits
      )

    Vector.tabulate(n, NumLines) {
      (beat, lane) =>

        val x =
          row(beat)(lane)

        val delta =
          math.min(
            NumLutEntries - 1,
            math.abs(
              state(lane).max - x
            )
          )

        val expQ =
          BigInt(
            expLut(delta)
          )

        val product =
          expQ *
          scales(lane)

        val shifted =
          product >>
          outputShift

        math.min(
          255,
          shifted.toInt
        )
    }
  }

  // --------------------------------------------------------------------------
  // Stimulus
  //
  // A "row" here means one complete ONLINE normalization context.
  // Each lane owns its independent vector across BeatsPerRow accepted beats.
  // --------------------------------------------------------------------------
  private val BeatsPerRow =
    4

  private val NumRows =
    6

  private def rmsRows(): Vector[Vector[Vector[Int]]] = {
    Vector.tabulate(NumRows) { row =>
      Vector.tabulate(BeatsPerRow) { _ =>
        Vector.tabulate(NumLines) { lane =>
          val mag =
            1 << ((lane + row) % 3)

          val sign =
            if (((lane + row) & 1) == 0) 1
            else -1

          sign * mag
        }
      }
    }
  }

  private def layerRows(): Vector[Vector[Vector[Int]]] = {
    Vector.tabulate(NumRows) { row =>
      Vector.tabulate(BeatsPerRow) { beat =>
        Vector.tabulate(NumLines) { lane =>
          val amp =
            1 << ((lane + row) % 3)

          val offset =
            ((lane + 2 * row) % 5) - 2

          val pattern =
            beat match {
              case 0 => -amp
              case 1 => -amp
              case 2 =>  amp
              case _ =>  amp
            }

          offset + pattern
        }
      }
    }
  }

  private def softmaxRows(): Vector[Vector[Vector[Int]]] = {
    val asc  = Vector(0, 1, 2, 3)
    val desc = Vector(3, 2, 1, 0)
    val zig1 = Vector(0, 2, 1, 3)
    val zig2 = Vector(3, 1, 2, 0)

    val shapes =
      Vector(
        asc,
        desc,
        zig1,
        zig2
      )

    Vector.tabulate(NumRows) { row =>
      Vector.tabulate(BeatsPerRow) { beat =>
        Vector.tabulate(NumLines) { lane =>
          val shape =
            shapes(
              (lane + row) %
              shapes.length
            )

          val offset =
            ((lane + row) % 3) - 1

          shape(beat) +
          offset
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // DUT helpers
  // --------------------------------------------------------------------------
  private def pokeAllInvalid(
    dut: UniversalNormUnit
  ): Unit = {

    for (i <- 0 until NumLines) {
      dut.io.phase1_in_vec(i)
        .poke(0.U)

      dut.io.phase1_valid_vec(i)
        .poke(false.B)

      dut.io.phase2_in_vec(i)
        .poke(0.U)

      dut.io.phase2_valid_vec(i)
        .poke(false.B)
    }

    dut.io.phase1_rope_param_update_in
      .poke(false.B)
  }

  private def drivePhase1Beat(
    dut: UniversalNormUnit,
    beat: Vector[Int],
    isLast: Boolean
  ): Unit = {

    for (i <- 0 until NumLines) {
      dut.io.phase1_in_vec(i)
        .poke(asUInt8(beat(i)).U)

      dut.io.phase1_valid_vec(i)
        .poke(true.B)
    }

    dut.io.phase1_rope_param_update_in
      .poke(isLast.B)
  }

  private def drivePhase2Beat(
    dut: UniversalNormUnit,
    beat: Vector[Int]
  ): Unit = {

    for (i <- 0 until NumLines) {
      dut.io.phase2_in_vec(i)
        .poke(asUInt8(beat(i)).U)

      dut.io.phase2_valid_vec(i)
        .poke(true.B)
    }
  }

  private def programLut(
    dut: UniversalNormUnit,
    table: Vector[Int],
    isExp: Boolean
  ): Unit = {

    require(
      table.length ==
      NumLutEntries
    )

    dut.io.lut_is_exp
      .poke(isExp.B)

    for (burst <- 0 until NumBursts) {
      dut.io.lut_wr_en
        .poke(true.B)

      dut.io.lut_wr_addr
        .poke(burst.U)

      for (word <- 0 until WordsPerBurst) {
        val idx =
          burst *
          WordsPerBurst +
          word

        dut.io.lut_wr_data(word)
          .poke(table(idx).U)
      }

      dut.clock.step()
    }

    dut.io.lut_wr_en
      .poke(false.B)

    // Universal_Wide_LUT's programmed flag is set from a delayed final-write
    // acknowledgement, so leave enough clocks for lut_ready to settle.
    dut.clock.step(3)
  }

  private def configureAndProgram(
    dut: UniversalNormUnit,
    mode: Int,
    expLut: Option[Vector[Int]],
    scaleLut: Vector[Int],
    epsilon: BigInt = 0
  ): Unit = {

    pokeAllInvalid(dut)

    dut.io.mode_sel
      .poke(mode.U)

    dut.io.layout_mode
      .poke(NormLayoutMode.ONLINE)

    dut.io.logical_vector_length
      .poke(BeatsPerRow.U)

    dut.io.inv_vector_length
      .poke(invNQ(BeatsPerRow).U)

    dut.io.epsilon
      .poke(epsilon.U)

    dut.io.stall
      .poke(false.B)

    dut.io.clr_acc
      .poke(false.B)

    dut.io.lut_wr_en
      .poke(false.B)

    dut.io.lut_is_exp
      .poke(false.B)

    dut.io.lut_wr_addr
      .poke(0.U)

    for (i <- 0 until WordsPerBurst) {
      dut.io.lut_wr_data(i)
        .poke(0.U)
    }

    expLut.foreach { table =>
      programLut(
        dut,
        table,
        isExp = true
      )
    }

    programLut(
      dut,
      scaleLut,
      isExp = false
    )

    dut.io.lut_ready
      .expect(true.B)

    // Clear statistics, stream counters, and metadata pointers without
    // disturbing programmed LUT contents.
    dut.io.clr_acc
      .poke(true.B)

    dut.clock.step()

    dut.io.clr_acc
      .poke(false.B)

    dut.io.sync_alert
      .expect(false.B)
  }

  private case class BeatId(
    row: Int,
    beat: Int
  )

  // --------------------------------------------------------------------------
  // Cycle-accurate ONLINE stream test.
  //
  // Phase 1 is driven continuously:
  //
  //   row0 P1 -> row1 P1 -> row2 P1 -> ...
  //
  // Whenever phase2_req is observed, the testbench acts as a 1-cycle-latency
  // NB and starts replaying that row exactly one clock later.
  //
  // This creates the intended overlap:
  //
  //   Phase1(row N+1) || Phase2(row N)
  // --------------------------------------------------------------------------
  private def runOnlineScenario(
    dut: UniversalNormUnit,
    modeName: String,
    mode: Int,
    rows: Vector[Vector[Vector[Int]]],
    golden: Vector[Vector[Vector[Int]]],
    softmaxMode: Boolean
  ): Unit = {

    require(
      rows.length ==
      golden.length
    )

    val completedAwaitingRequest =
      mutable.Queue[Int]()

    val phase1CompletionCycle =
      mutable.Map[Int, Int]()

    val requestCycle =
      mutable.Map[Int, Int]()

    // NB drive schedule:
    // cycle -> physical Phase-2 beat
    val phase2Drive =
      mutable.Map[Int, BeatId]()

    // Expected Normalizer output:
    // Phase-2 datapath adds one further cycle.
    val expectedOutput =
      mutable.Map[Int, BeatId]()

    var p1FlatIndex =
      0

    var requestCount =
      0

    var outputBeatCount =
      0

    var overlapCount =
      0

    var crossRowOverlapCount =
      0

    var boundaryOverlapCount =
      0

    var cycle =
      0

    val totalP1Beats =
      rows.length *
      BeatsPerRow

    val totalExpectedOutputs =
      totalP1Beats

    val maxCycles =
      500

    while (
      outputBeatCount <
      totalExpectedOutputs &&
      cycle <
      maxCycles
    ) {

      // ------------------------------------------------------------
      // Defaults for current cycle
      // ------------------------------------------------------------
      pokeAllInvalid(dut)

      // ------------------------------------------------------------
      // Continuous Phase 1
      // ------------------------------------------------------------
      val p1BeatNow =
        if (p1FlatIndex < totalP1Beats) {
          val row =
            p1FlatIndex /
            BeatsPerRow

          val beat =
            p1FlatIndex %
            BeatsPerRow

          val id =
            BeatId(
              row,
              beat
            )

          drivePhase1Beat(
            dut,
            rows(row)(beat),
            isLast =
              beat ==
              BeatsPerRow - 1
          )

          Some(id)

        } else {
          None
        }

      p1BeatNow.foreach { id =>
        val isLast =
          id.beat ==
          BeatsPerRow - 1

        dut.io.phase1_vector_done
          .expect(isLast.B)

        for (lane <- 0 until NumLines) {
          dut.io.phase1_out_valid_vec(lane)
            .expect(true.B)

          dut.io.phase1_out_vec(lane)
            .expect(
              asUInt8(
                rows(id.row)(id.beat)(lane)
              ).U
            )
        }

        if (isLast) {
          completedAwaitingRequest
            .enqueue(id.row)

          phase1CompletionCycle(id.row) =
            cycle
        }

        p1FlatIndex += 1
      }

      if (p1BeatNow.isEmpty) {
        dut.io.phase1_vector_done
          .expect(false.B)

        for (lane <- 0 until NumLines) {
          dut.io.phase1_out_valid_vec(lane)
            .expect(false.B)
        }
      }

      // ------------------------------------------------------------
      // NB Phase 2 data scheduled from prior phase2_req
      // ------------------------------------------------------------
      val p2BeatNow =
        phase2Drive.get(cycle)

      p2BeatNow.foreach { id =>
        drivePhase2Beat(
          dut,
          rows(id.row)(id.beat)
        )
      }

      // ------------------------------------------------------------
      // Explicit interleaving checks
      // ------------------------------------------------------------
      for {
        p1 <- p1BeatNow
        p2 <- p2BeatNow
      } {
        overlapCount += 1

        if (p1.row > p2.row) {
          crossRowOverlapCount += 1
        }

        if (
          p1.beat ==
          BeatsPerRow - 1 &&
          p2.beat ==
          BeatsPerRow - 1
        ) {
          boundaryOverlapCount += 1
        }
      }

      // ------------------------------------------------------------
      // Check output corresponding to previous accepted Phase-2 beat
      // ------------------------------------------------------------
      expectedOutput.get(cycle) match {

        case Some(id) =>
          for (lane <- 0 until NumLines) {
            dut.io.phase2_out_valid_vec(lane)
              .expect(true.B)

            dut.io.phase2_out_vec(lane)
              .expect(
                golden(id.row)(id.beat)(lane).U
              )
          }

          val expectedMeta =
            id.beat ==
            BeatsPerRow - 1

          dut.io.phase2_rope_param_update_out
            .expect(expectedMeta.B)

          outputBeatCount += 1

        case None =>
          for (lane <- 0 until NumLines) {
            dut.io.phase2_out_valid_vec(lane)
              .expect(false.B)
          }

          dut.io.phase2_rope_param_update_out
            .expect(false.B)
      }

      // ------------------------------------------------------------
      // phase2_req
      // ------------------------------------------------------------
      val req =
        dut.io.phase2_req
          .peek()
          .litToBoolean

      if (req) {
        assert(
          completedAwaitingRequest.nonEmpty,
          s"[$modeName] phase2_req at cycle $cycle without a completed Phase-1 row"
        )

        val row =
          completedAwaitingRequest.dequeue()

        assert(
          !requestCycle.contains(row),
          s"[$modeName] duplicate phase2_req for row $row"
        )

        requestCycle(row) =
          cycle

        requestCount += 1

        val completedAt =
          phase1CompletionCycle(row)

        if (softmaxMode) {
          // With BeatsPerRow=4, the last Milakov request returns in one
          // cycle and softFinalReady launches the scale lookup one cycle later.
          assert(
            cycle ==
            completedAt + 2,
            s"[$modeName] row $row phase2_req expected at ${completedAt + 2}, got $cycle"
          )
        } else {
          // RMS/Layer statistics are complete on the boundary beat itself.
          assert(
            cycle ==
            completedAt,
            s"[$modeName] row $row phase2_req expected at $completedAt, got $cycle"
          )
        }

        // Emulate Normalizer Buffer:
        // request at t -> first data at t+1.
        for (beat <- 0 until BeatsPerRow) {
          val driveCycle =
            cycle +
            1 +
            beat

          assert(
            !phase2Drive.contains(driveCycle),
            s"[$modeName] NB schedule collision at cycle $driveCycle"
          )

          phase2Drive(driveCycle) =
            BeatId(
              row,
              beat
            )

          // Norm Phase 2 itself has one-cycle output latency.
          expectedOutput(
            driveCycle + 1
          ) =
            BeatId(
              row,
              beat
            )
        }
      }

      // RMS/Layer phase2_req must be exactly the Phase-1 row-boundary event.
      if (!softmaxMode) {
        val p1LastNow =
          p1BeatNow.exists(
            _.beat ==
            BeatsPerRow - 1
          )

        assert(
          req == p1LastNow,
          s"[$modeName] cycle $cycle: phase2_req=$req, phase1Last=$p1LastNow"
        )
      }

      // The one-cycle-NB contract implies that whenever this cycle has the
      // first Phase-2 beat of a row, the request was on the previous cycle.
      p2BeatNow.foreach { id =>
        if (id.beat == 0) {
          assert(
            requestCycle(id.row) ==
            cycle - 1,
            s"[$modeName] row ${id.row}: Phase2 first beat not exactly one cycle after phase2_req"
          )
        }
      }

      dut.io.sync_alert
        .expect(false.B)

      dut.clock.step()

      cycle += 1
    }

    assert(
      cycle < maxCycles,
      s"[$modeName] timeout after $maxCycles cycles"
    )

    assert(
      requestCount ==
      rows.length,
      s"[$modeName] expected ${rows.length} phase2_req pulses, got $requestCount"
    )

    assert(
      outputBeatCount ==
      totalExpectedOutputs,
      s"[$modeName] expected $totalExpectedOutputs output beats, got $outputBeatCount"
    )

    assert(
      completedAwaitingRequest.isEmpty,
      s"[$modeName] completed rows remained without phase2_req: $completedAwaitingRequest"
    )

    assert(
      overlapCount > 0,
      s"[$modeName] Phase1 and Phase2 never overlapped"
    )

    assert(
      crossRowOverlapCount > 0,
      s"[$modeName] no Phase1(row N+1) / Phase2(row N) interleaving observed"
    )

    // RMS/Layer should reach the strongest zero-bubble boundary overlap:
    //
    //   P1 row N+1 last beat || P2 row N last beat
    //
    // Softmax has extra Milakov-finalization latency, so its useful proof is
    // crossRowOverlapCount rather than same-cycle boundary overlap.
    if (!softmaxMode) {
      assert(
        boundaryOverlapCount > 0,
        s"[$modeName] no simultaneous P1/P2 row-boundary overlap observed"
      )
    }

    // One extra idle clock lets the final metadata dequeue settle visibly.
    pokeAllInvalid(dut)

    dut.clock.step()

    dut.io.metadata_count
      .expect(0.U)

    dut.io.sync_alert
      .expect(false.B)

    println(
      s"[$modeName] PASS: requests=$requestCount, outputs=$outputBeatCount, " +
      s"overlap=$overlapCount, crossRowOverlap=$crossRowOverlapCount, " +
      s"boundaryOverlap=$boundaryOverlapCount"
    )
  }

  // ==========================================================================
  // RMSNorm ONLINE
  // ==========================================================================
  it should
    "run RMSNorm online with exact phase transition and row-interleaved Phase1/Phase2" in {

    val scale =
      rsqrtScaleTable()

    val rows =
      rmsRows()

    val golden =
      rows.map { row =>
        goldenRms(
          row,
          epsilon = 0,
          scaleTable = scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines = NumLines,
        writeBits = WriteBits,
        inBits = InBits,
        outBits = OutBits,
        vectorSize = MaxVectorLen,
        indexBits = IndexBits,
        dataBits = DataBits,
        lutFracBits = LutFracBits,
        invBits = InvBits,
        invFracBits = InvFracBits,
        metadataDepth = MetadataDepth
      )
    ) { dut =>

      configureAndProgram(
        dut,
        mode = 1,
        expLut = None,
        scaleLut = scale
      )

      runOnlineScenario(
        dut,
        modeName = "RMSNorm-ONLINE",
        mode = 1,
        rows = rows,
        golden = golden,
        softmaxMode = false
      )
    }
  }

  // ==========================================================================
  // LayerNorm ONLINE
  // ==========================================================================
  it should
    "run LayerNorm online with exact phase transition and row-interleaved Phase1/Phase2" in {

    val scale =
      rsqrtScaleTable()

    val rows =
      layerRows()

    val golden =
      rows.map { row =>
        goldenLayer(
          row,
          epsilon = 0,
          scaleTable = scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines = NumLines,
        writeBits = WriteBits,
        inBits = InBits,
        outBits = OutBits,
        vectorSize = MaxVectorLen,
        indexBits = IndexBits,
        dataBits = DataBits,
        lutFracBits = LutFracBits,
        invBits = InvBits,
        invFracBits = InvFracBits,
        metadataDepth = MetadataDepth
      )
    ) { dut =>

      configureAndProgram(
        dut,
        mode = 2,
        expLut = None,
        scaleLut = scale
      )

      runOnlineScenario(
        dut,
        modeName = "LayerNorm-ONLINE",
        mode = 2,
        rows = rows,
        golden = golden,
        softmaxMode = false
      )
    }
  }

  // ==========================================================================
  // Softmax ONLINE
  //
  // Even/odd/zig-zag lanes force both Milakov branches:
  //   - running maximum changes
  //   - running maximum stays unchanged
  //
  // This verifies that Phase-1 Softmax can continue on the next row while
  // Phase-2 of the prior row is simultaneously using the separate apply Exp LUT.
  // ==========================================================================
  it should
    "run Milakov online Softmax while interleaving next-row Phase1 with prior-row Phase2" in {

    val exp =
      expTable()

    val scale =
      reciprocalScaleTable()

    val rows =
      softmaxRows()

    val golden =
      rows.map { row =>
        goldenSoftmax(
          row,
          expLut = exp,
          scaleTable = scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines = NumLines,
        writeBits = WriteBits,
        inBits = InBits,
        outBits = OutBits,
        vectorSize = MaxVectorLen,
        indexBits = IndexBits,
        dataBits = DataBits,
        lutFracBits = LutFracBits,
        invBits = InvBits,
        invFracBits = InvFracBits,
        metadataDepth = MetadataDepth
      )
    ) { dut =>

      configureAndProgram(
        dut,
        mode = 3,
        expLut = Some(exp),
        scaleLut = scale
      )

      runOnlineScenario(
        dut,
        modeName = "Softmax-ONLINE",
        mode = 3,
        rows = rows,
        golden = golden,
        softmaxMode = true
      )
    }
  }
}
