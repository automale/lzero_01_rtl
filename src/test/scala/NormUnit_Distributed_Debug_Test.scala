package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable
import scala.util.Random

class NormUnitDistributedDebugTest
  extends AnyFlatSpec
  with ChiselScalatestTester {

  behavior of "UniversalNormUnit DISTRIBUTED mode"

  // ==========================================================================
  // Test configuration
  // ==========================================================================
  private val NumLines       = 16
  private val InBits         = 8
  private val OutBits        = 8
  private val IndexBits      = 6   // 64 entries: faster simulation than 256
  private val DataBits       = 16
  private val WriteBits      = 256
  private val LutFracBits    = 14
  private val InvBits        = 24
  private val InvFracBits    = 20
  private val MaxVectorLen   = 64
  private val MetadataDepth  = 512

  private val AccBits =
    math.max(
      32,
      2 * InBits +
      log2CeilScala(MaxVectorLen) +
      3
    )

  private val OneQ =
    1L << LutFracBits

  private val InvSqrt2Q =
    math.round(
      (1.0 / math.sqrt(2.0)) *
      OneQ.toDouble
    )

  private val ScaleBits =
    DataBits +
    LutFracBits +
    6

  private val WordsPerBurst =
    WriteBits /
    DataBits

  private val NumLutEntries =
    1 << IndexBits

  private val NumBursts =
    NumLutEntries /
    WordsPerBurst

  private def log2CeilScala(
    x: Int
  ): Int = {
    require(x > 0)
    32 -
    Integer.numberOfLeadingZeros(
      x - 1
    )
  }

  private def mask(
    bits: Int
  ): BigInt =
    (BigInt(1) << bits) - 1

  private def asUInt8(
    x: Int
  ): Int =
    x & 0xff

  private def satSigned8ToBits(
    x: BigInt
  ): Int = {

    val clipped =
      if (x > 127) {
        127
      } else if (x < -128) {
        -128
      } else {
        x.toInt
      }

    clipped & 0xff
  }

  // ==========================================================================
  // LUT generation
  // ==========================================================================
  private def expTable():
    Vector[Int] = {

    Vector.tabulate(
      NumLutEntries
    ) { delta =>

      val raw =
        math.round(
          math.exp(
            -delta.toDouble
          ) *
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

  private def mantissaFromIndex(
    idx: Int
  ): Double = {

    val clamped =
      math.max(
        1 << (IndexBits - 1),
        idx
      )

    clamped.toDouble /
    (1 << (IndexBits - 1)).toDouble
  }

  private def rsqrtScaleTable():
    Vector[Int] = {

    Vector.tabulate(
      NumLutEntries
    ) { idx =>

      val m =
        mantissaFromIndex(idx)

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

  private def reciprocalScaleTable():
    Vector[Int] = {

    Vector.tabulate(
      NumLutEntries
    ) { idx =>

      val m =
        mantissaFromIndex(idx)

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

  // ==========================================================================
  // Exact fixed-point helpers matching NormUnit.scala
  // ==========================================================================
  private def normalizedIndex(
    xIn: BigInt
  ): (Int, Int) = {

    val x =
      xIn &
      mask(AccBits)

    if (x == 0) {
      (0, 0)
    } else {
      val exp =
        x.bitLength - 1

      val lz =
        (AccBits - 1) -
        exp

      val shifted =
        (x << lz) &
        mask(AccBits)

      val idx =
        (
          shifted >>
          (AccBits - IndexBits)
        ).toInt &
        ((1 << IndexBits) - 1)

      (
        idx,
        exp
      )
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
    ) &
    mask(ScaleBits)
  }

  private def adjustRecipScale(
    raw: BigInt,
    signedExp: Int
  ): BigInt = {

    val shifted =
      if (signedExp >= 0) {
        raw >>
        signedExp
      } else {
        raw <<
        (-signedExp)
      }

    shifted &
    mask(ScaleBits)
  }

  private def invNQ(
    n: Int
  ): BigInt = {

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
  ): BigInt =
    (sum * invN) >>
    InvFracBits

  private def meanUnsigned(
    sum: BigInt,
    invN: BigInt
  ): BigInt =
    (sum * invN) >>
    InvFracBits

  private def scaleFromRmsOrLayerStat(
    stat: BigInt,
    scaleTable: Vector[Int]
  ): BigInt = {

    val (
      idx,
      exp
    ) =
      normalizedIndex(stat)

    adjustRsqrtScale(
      BigInt(
        scaleTable(idx)
      ),
      exp
    )
  }

  private def scaleFromSoftmaxSum(
    sumQ: BigInt,
    scaleTable: Vector[Int]
  ): BigInt = {

    val (
      idx,
      expInt
    ) =
      normalizedIndex(sumQ)

    val realExp =
      expInt -
      LutFracBits

    adjustRecipScale(
      BigInt(
        scaleTable(idx)
      ),
      realExp
    )
  }

  // ==========================================================================
  // Distributed physical/logical vector representation
  // ==========================================================================
  private case class DistVector(
    logicalLength: Int,
    physicalBeats: Vector[Vector[Int]],
    metadata: Vector[Boolean]
  ) {
    val beats: Int =
      physicalBeats.length

    require(
      beats ==
      (logicalLength + NumLines - 1) /
      NumLines
    )

    require(
      physicalBeats.forall(
        _.length == NumLines
      )
    )

    require(
      metadata.length ==
      beats
    )

    def logicalFlat:
      Vector[Int] = {

      physicalBeats
        .flatten
        .take(
          logicalLength
        )
    }
  }

  private def buildVector(
    logicalLength: Int,
    vectorId: Int,
    valueFn: (Int, Int) => Int
  ): DistVector = {

    val beats =
      (
        logicalLength +
        NumLines -
        1
      ) /
      NumLines

    val physical =
      Vector.tabulate(
        beats
      ) { beat =>

        Vector.tabulate(
          NumLines
        ) { lane =>

          val element =
            beat *
            NumLines +
            lane

          if (
            element <
            logicalLength
          ) {
            valueFn(
              vectorId,
              element
            )
          } else {
            // Physical matrix padding.
            0
          }
        }
      }

    // Deliberately unrelated to vector boundary.
    // Distributed phase1_last must NOT depend on this pattern.
    val metadata =
      Vector.tabulate(
        beats
      ) { beat =>
        (
          (
            vectorId * 5 +
            beat * 3 +
            1
          ) %
          7
        ) <
        3
      }

    DistVector(
      logicalLength =
        logicalLength,
      physicalBeats =
        physical,
      metadata =
        metadata
    )
  }

  private def makeRmsVectors(
    logicalLength: Int,
    count: Int
  ): Vector[DistVector] = {

    Vector.tabulate(
      count
    ) { v =>

      buildVector(
        logicalLength,
        v,
        (vec, elem) => {
          val mag =
            1 +
            (
              (
                elem +
                vec
              ) %
              4
            )

          if (
            (
              elem +
              2 * vec
            ) %
            2 ==
            0
          ) {
            mag
          } else {
            -mag
          }
        }
      )
    }
  }

  private def makeLayerVectors(
    logicalLength: Int,
    count: Int
  ): Vector[DistVector] = {

    Vector.tabulate(
      count
    ) { v =>

      buildVector(
        logicalLength,
        v,
        (vec, elem) => {
          val base =
            (
              (
                elem * 3 +
                vec * 2
              ) %
              9
            ) -
            4

          // Non-zero vector-dependent mean.
          base +
          (
            (vec % 3) -
            1
          )
        }
      )
    }
  }

  private def makeSoftmaxVectors(
    logicalLength: Int,
    count: Int
  ): Vector[DistVector] = {

    Vector.tabulate(
      count
    ) { v =>

      buildVector(
        logicalLength,
        v,
        (vec, elem) => {
          val beat =
            elem /
            NumLines

          val lane =
            elem %
            NumLines

          val base =
            1 +
            (
              (
                lane +
                vec
              ) %
              3
            )

          // Across successive beats, half the lanes increase their running max
          // and half do not. This exercises both Milakov branches.
          val delta =
            beat match {
              case 0 =>
                0

              case 1 =>
                if ((lane & 1) == 0) {
                  1
                } else {
                  -1
                }

              case _ =>
                (
                  (
                    lane +
                    beat +
                    vec
                  ) %
                  3
                ) -
                1
            }

          math.max(
            0,
            base +
            delta
          )
        }
      )
    }
  }

  // ==========================================================================
  // Golden: distributed RMSNorm / LayerNorm
  //
  // Statistics use exactly logicalLength elements.
  // Phase2 applies the final context to ALL physical elements, including
  // zero-padding lanes.
  // ==========================================================================
  private def goldenRms(
    vector: DistVector,
    epsilon: BigInt,
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val logical =
      vector.logicalFlat

    val invN =
      invNQ(
        vector.logicalLength
      )

    val sqSum =
      logical.foldLeft(
        BigInt(0)
      ) {
        (acc, x) =>
          acc +
          BigInt(x) *
          BigInt(x)
      }

    val meanSq =
      meanUnsigned(
        sqSum,
        invN
      )

    val stat =
      meanSq +
      epsilon

    val scale =
      scaleFromRmsOrLayerStat(
        stat,
        scaleTable
      )

    vector.physicalBeats.map {
      beat =>
        beat.map {
          x =>

            val product =
              BigInt(x) *
              scale

            val scaled =
              product >>
              LutFracBits

            satSigned8ToBits(
              scaled
            )
        }
    }
  }

  private def goldenLayer(
    vector: DistVector,
    epsilon: BigInt,
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val logical =
      vector.logicalFlat

    val invN =
      invNQ(
        vector.logicalLength
      )

    val sum =
      logical.foldLeft(
        BigInt(0)
      ) {
        (acc, x) =>
          acc +
          BigInt(x)
      }

    val sqSum =
      logical.foldLeft(
        BigInt(0)
      ) {
        (acc, x) =>
          acc +
          BigInt(x) *
          BigInt(x)
      }

    val mean =
      meanSigned(
        sum,
        invN
      )

    val ex2 =
      meanUnsigned(
        sqSum,
        invN
      )

    val meanSq =
      mean *
      mean

    val variance =
      if (
        ex2 >=
        meanSq
      ) {
        ex2 -
        meanSq
      } else {
        BigInt(0)
      }

    val scale =
      scaleFromRmsOrLayerStat(
        variance +
        epsilon,
        scaleTable
      )

    vector.physicalBeats.map {
      beat =>
        beat.map {
          x =>

            val centered =
              BigInt(x) -
              mean

            val product =
              centered *
              scale

            val scaled =
              product >>
              LutFracBits

            satSigned8ToBits(
              scaled
            )
        }
    }
  }

  // ==========================================================================
  // Golden: distributed Milakov Softmax
  //
  // 1) local Milakov state per physical lane
  // 2) global max
  // 3) local-sum correction to global max
  // 4) corrected global sum
  // 5) reciprocal Scale LUT
  // 6) Phase2 applies to every PHYSICAL element, padding included
  // ==========================================================================
  private case class SoftState(
    max: Int,
    sumQ: BigInt
  )

  private def localMilakovState(
    values: Vector[Int],
    expLut: Vector[Int]
  ): SoftState = {

    require(
      values.nonEmpty
    )

    var m =
      values.head

    var d =
      BigInt(OneQ)

    values.tail.foreach {
      x =>

        if (
          x >
          m
        ) {
          val delta =
            math.min(
              NumLutEntries - 1,
              x - m
            )

          val factor =
            BigInt(
              expLut(delta)
            )

          d =
            (
              (
                d *
                factor
              ) >>
              LutFracBits
            ) +
            BigInt(OneQ)

          m =
            x

        } else {
          val delta =
            math.min(
              NumLutEntries - 1,
              m - x
            )

          val factor =
            BigInt(
              expLut(delta)
            )

          d =
            d +
            factor
        }
    }

    SoftState(
      max =
        m,
      sumQ =
        d
    )
  }

  private def goldenSoftmaxDistributed(
    vector: DistVector,
    expLut: Vector[Int],
    scaleTable: Vector[Int]
  ): Vector[Vector[Int]] = {

    val localStates =
      Vector.tabulate(
        NumLines
      ) { lane =>

        val laneValues =
          Vector.tabulate(
            vector.beats
          ) { beat =>
            val element =
              beat *
              NumLines +
              lane

            if (
              element <
              vector.logicalLength
            ) {
              Some(
                vector
                  .physicalBeats(beat)(lane)
              )
            } else {
              None
            }
          }.flatten

        localMilakovState(
          laneValues,
          expLut
        )
      }

    val globalMax =
      localStates
        .map(_.max)
        .max

    val correctedLocal =
      localStates.map {
        st =>

          val delta =
            math.min(
              NumLutEntries - 1,
              globalMax -
              st.max
            )

          val factor =
            BigInt(
              expLut(delta)
            )

          (
            (
              st.sumQ *
              factor
            ) >>
            LutFracBits
          )
      }

    val globalSumQ =
      correctedLocal.foldLeft(
        BigInt(0)
      )(
        _ + _
      )

    val scale =
      scaleFromSoftmaxSum(
        globalSumQ,
        scaleTable
      )

    val outputShift =
      math.max(
        0,
        2 *
        LutFracBits -
        OutBits
      )

    vector.physicalBeats.map {
      beat =>
        beat.map {
          x =>

            // Mirrors deltaToIndex(globalMax, x), including padding.
            val delta =
              math.min(
                NumLutEntries - 1,
                math.abs(
                  globalMax -
                  x
                )
              )

            val expQ =
              BigInt(
                expLut(delta)
              )

            val product =
              expQ *
              scale

            val shifted =
              product >>
              outputShift

            math.min(
              255,
              shifted.toInt
            )
        }
    }
  }

  private case class SoftGoldenContext(
    globalMax: Int,
    globalSumQ: BigInt,
    scale: BigInt
  )

  private def goldenSoftmaxContext(
    vector: DistVector,
    expLut: Vector[Int],
    scaleTable: Vector[Int]
  ): SoftGoldenContext = {

    val localStates =
      Vector.tabulate(NumLines) { lane =>
        val laneValues =
          Vector.tabulate(vector.beats) { beat =>
            val element = beat * NumLines + lane
            if (element < vector.logicalLength) {
              Some(vector.physicalBeats(beat)(lane))
            } else {
              None
            }
          }.flatten

        localMilakovState(laneValues, expLut)
      }

    val globalMax = localStates.map(_.max).max

    val correctedLocal = localStates.map { st =>
      val delta = math.min(NumLutEntries - 1, globalMax - st.max)
      val factor = BigInt(expLut(delta))
      ((st.sumQ * factor) >> LutFracBits)
    }

    val globalSumQ = correctedLocal.foldLeft(BigInt(0))(_ + _)
    val scale = scaleFromSoftmaxSum(globalSumQ, scaleTable)

    SoftGoldenContext(globalMax, globalSumQ, scale)
  }

  // ==========================================================================
  // DUT IO helpers
  // ==========================================================================
  private def pokeAllInvalid(
    dut: UniversalNormUnit
  ): Unit = {

    for (
      i <-
      0 until
      NumLines
    ) {
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
    vector: DistVector,
    beat: Int
  ): Unit = {

    for (
      lane <-
      0 until
      NumLines
    ) {
      dut.io.phase1_in_vec(lane)
        .poke(
          asUInt8(
            vector
              .physicalBeats(beat)(lane)
          ).U
        )

      dut.io.phase1_valid_vec(lane)
        .poke(true.B)
    }

    dut.io.phase1_rope_param_update_in
      .poke(
        vector
          .metadata(beat)
          .B
      )
  }

  private def drivePhase2Beat(
    dut: UniversalNormUnit,
    vector: DistVector,
    beat: Int
  ): Unit = {

    for (
      lane <-
      0 until
      NumLines
    ) {
      dut.io.phase2_in_vec(lane)
        .poke(
          asUInt8(
            vector
              .physicalBeats(beat)(lane)
          ).U
        )

      dut.io.phase2_valid_vec(lane)
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

    for (
      burst <-
      0 until
      NumBursts
    ) {
      dut.io.lut_wr_en
        .poke(true.B)

      dut.io.lut_wr_addr
        .poke(burst.U)

      for (
        word <-
        0 until
        WordsPerBurst
      ) {
        val idx =
          burst *
          WordsPerBurst +
          word

        dut.io.lut_wr_data(word)
          .poke(
            table(idx).U
          )
      }

      dut.clock.step()
    }

    dut.io.lut_wr_en
      .poke(false.B)

    dut.clock.step(3)
  }

  private def configureAndProgram(
    dut: UniversalNormUnit,
    mode: Int,
    logicalLength: Int,
    expLut: Option[Vector[Int]],
    scaleLut: Vector[Int],
    epsilon: BigInt = 0
  ): Unit = {

    pokeAllInvalid(dut)

    dut.io.mode_sel
      .poke(mode.U)

    dut.io.layout_mode
      .poke(
        NormLayoutMode
          .DISTRIBUTED
      )

    dut.io.logical_vector_length
      .poke(
        logicalLength.U
      )

    dut.io.inv_vector_length
      .poke(
        invNQ(
          logicalLength
        ).U
      )

    dut.io.epsilon
      .poke(
        epsilon.U
      )

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

    for (
      i <-
      0 until
      WordsPerBurst
    ) {
      dut.io.lut_wr_data(i)
        .poke(0.U)
    }

    expLut.foreach {
      table =>
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

    // Clear only runtime state/FIFO. LUT contents remain programmed.
    dut.io.clr_acc
      .poke(true.B)

    dut.clock.step()

    dut.io.clr_acc
      .poke(false.B)

    dut.io.sync_alert
      .expect(false.B)
  }

  // ==========================================================================
  // Cycle-level distributed stream model
  // ==========================================================================
  private case class BeatId(
    vector: Int,
    beat: Int
  )

  private case class NbRequest(
    vector: Int,
    readyCycle: Int
  )

  private case class ExpectedOut(
    id: BeatId,
    data: Vector[Int],
    metadata: Boolean,
    acceptedCycle: Int
  )

  private def runDistributedScenario(
    dut: UniversalNormUnit,
    modeName: String,
    mode: Int,
    vectors: Vector[DistVector],
    golden: Vector[Vector[Vector[Int]]],
    stressStall: Boolean,
    softmaxMode: Boolean
  ): Unit = {

    require(
      vectors.nonEmpty
    )

    require(
      vectors.length ==
      golden.length
    )

    val beatsPerVector =
      vectors.head.beats

    require(
      vectors.forall(
        _.beats ==
        beatsPerVector
      )
    )

    val totalP1Beats =
      vectors.length *
      beatsPerVector

    val totalP2Beats =
      totalP1Beats

    // Vector completed in Phase1 but has not emitted phase2_req yet.
    val completedAwaitingRequest =
      mutable.Queue[Int]()

    val completionCycle =
      mutable.Map[Int, Int]()

    val requestCycle =
      mutable.Map[Int, Int]()

    val requestLatency =
      mutable.ArrayBuffer[Int]()

    // NB model.
    val nbRequestQueue =
      mutable.Queue[NbRequest]()

    var nbActive:
      Option[BeatId] =
      None

    // Accepted Phase2 beats awaiting output.
    val expectedOutQueue =
      mutable.Queue[ExpectedOut]()

    var p1FlatIndex =
      0

    var cycle =
      0

    var requestCount =
      0

    var outputCount =
      0

    var overlapCount =
      0

    var crossVectorOverlapCount =
      0

    var sameCycleEnqDeqCount =
      0

    var stallCount =
      0

    // ------------------------------------------------------------------------
    // Targeted stall bookkeeping
    // ------------------------------------------------------------------------
    val stalledP1Middle =
      mutable.Set[(Int, Int)]()

    val stalledP1Last =
      mutable.Set[(Int, Int)]()

    val stalledP2First =
      mutable.Set[Int]()

    var postRequestStallRemaining =
      0

    val rng =
      new Random(
        0x51a11L +
        mode.toLong * 0x100L +
        vectors.head.logicalLength.toLong
      )

    // Activate an NB request once its one-cycle latency has elapsed.
    def refreshNbActive(
      now: Int
    ): Unit = {

      if (
        nbActive.isEmpty &&
        nbRequestQueue.nonEmpty &&
        nbRequestQueue.front.readyCycle <= now
      ) {
        val req =
          nbRequestQueue.dequeue()

        nbActive =
          Some(
            BeatId(
              req.vector,
              0
            )
          )
      }
    }

    val maxCycles =
      if (stressStall) {
        5000
      } else {
        2000
      }

    while (
      outputCount <
      totalP2Beats &&
      cycle <
      maxCycles
    ) {

      refreshNbActive(
        cycle
      )

      val p1Id =
        if (
          p1FlatIndex <
          totalP1Beats
        ) {
          Some(
            BeatId(
              vector =
                p1FlatIndex /
                beatsPerVector,
              beat =
                p1FlatIndex %
                beatsPerVector
            )
          )
        } else {
          None
        }

      val p2Id =
        nbActive

      // ----------------------------------------------------------------------
      // Stall policy
      //
      // Targeted:
      //   - Phase1 middle beat
      //   - Phase1 last beat
      //   - first Phase2 beat
      //   - immediately after phase2_req
      //
      // Plus sparse deterministic random stalls.
      // ----------------------------------------------------------------------
      var stallNow =
        false

      if (stressStall) {

        if (
          postRequestStallRemaining >
          0
        ) {
          stallNow = true
          postRequestStallRemaining -= 1
        }

        p1Id.foreach {
          id =>

            val key =
              (
                id.vector,
                id.beat
              )

            val middleBeat =
              beatsPerVector >= 3 &&
              id.beat == 1

            if (
              !stallNow &&
              middleBeat &&
              !stalledP1Middle
                .contains(key) &&
              id.vector < 3
            ) {
              stallNow = true

              stalledP1Middle +=
                key
            }

            if (
              !stallNow &&
              id.beat ==
              beatsPerVector - 1 &&
              !stalledP1Last
                .contains(key) &&
              id.vector < 4
            ) {
              stallNow = true

              stalledP1Last +=
                key
            }
        }

        p2Id.foreach {
          id =>

            if (
              !stallNow &&
              id.beat == 0 &&
              !stalledP2First
                .contains(id.vector) &&
              id.vector < 4
            ) {
              stallNow = true

              stalledP2First +=
                id.vector
            }
        }

        // Sparse random one-cycle stall after the targeted cases have priority.
        if (
          !stallNow &&
          cycle > 10 &&
          rng.nextInt(29) == 0
        ) {
          stallNow = true
        }
      }

      if (stallNow) {
        stallCount += 1
      }

      pokeAllInvalid(dut)

      dut.io.stall
        .poke(stallNow.B)

      // ----------------------------------------------------------------------
      // Drive Phase1.
      // Data/valid remain presented even during stall; the DUT must not accept.
      // ----------------------------------------------------------------------
      p1Id.foreach {
        id =>

          drivePhase1Beat(
            dut,
            vectors(id.vector),
            id.beat
          )
      }

      // ----------------------------------------------------------------------
      // Drive NB Phase2.
      // NB holds the current beat during global stall.
      // ----------------------------------------------------------------------
      p2Id.foreach {
        id =>

          drivePhase2Beat(
            dut,
            vectors(id.vector),
            id.beat
          )
      }

      // ----------------------------------------------------------------------
      // Output scoreboard BEFORE this edge.
      // ----------------------------------------------------------------------
      val visibleOut =
        dut.io
          .phase2_out_valid_vec(0)
          .peek()
          .litToBoolean

      if (stallNow) {
        for (
          lane <-
          0 until
          NumLines
        ) {
          dut.io
            .phase2_out_valid_vec(lane)
            .expect(false.B)
        }

        dut.io
          .phase2_rope_param_update_out
          .expect(false.B)

      } else if (visibleOut) {

        assert(
          expectedOutQueue.nonEmpty,
          s"[$modeName] output at cycle $cycle without an accepted Phase2 beat"
        )

        val exp =
          expectedOutQueue.dequeue()

        assert(
          cycle >=
          exp.acceptedCycle + 1,
          s"[$modeName] output appeared too early for ${exp.id}: accepted=${exp.acceptedCycle}, output=$cycle"
        )

        if (!stressStall) {
          assert(
            cycle ==
            exp.acceptedCycle + 1,
            s"[$modeName] no-stall Phase2 latency must be exactly 1 cycle for ${exp.id}"
          )
        }

        for (
          lane <-
          0 until
          NumLines
        ) {
          dut.io
            .phase2_out_valid_vec(lane)
            .expect(true.B)

          val actual =
            dut.io
              .phase2_out_vec(lane)
              .peek()
              .litValue
              .toInt

          val expected =
            exp.data(lane)

          if (actual != expected) {
            val activeScale =
              dut.globalScaleReg
                .peek()
                .litValue

            val activeMax =
              dut.globalSoftMaxReg
                .peek()
                .litValue

            val pendingMax =
              dut.globalPendingSoftMaxReg
                .peek()
                .litValue

            val p2ExpData =
              dut.phase2ExpLuts(lane).io.rd_data
                .peek()
                .litValue

            val scaleRdValid =
              dut.scaleLuts(0).io.rd_valid
                .peek()
                .litToBoolean

            val scaleRdData =
              dut.scaleLuts(0).io.rd_data
                .peek()
                .litValue

            val ctxCount =
              dut.softContextFifo.io.count
                .peek()
                .litValue

            fail(
              s"""[$modeName] NUMERIC MISMATCH
                 |cycle             = $cycle
                 |vector            = ${exp.id.vector}
                 |beat              = ${exp.id.beat}
                 |lane              = $lane
                 |phase2AcceptedAt  = ${exp.acceptedCycle}
                 |stallNow          = $stallNow
                 |expected          = $expected
                 |actual            = $actual
                 |input             = ${vectors(exp.id.vector).physicalBeats(exp.id.beat)(lane)}
                 |activeScale       = $activeScale
                 |activeGlobalMax   = $activeMax
                 |pendingGlobalMax  = $pendingMax
                 |phase2ExpData     = $p2ExpData
                 |scaleRdValid      = $scaleRdValid
                 |scaleRdData       = $scaleRdData
                 |softCtxFifoCount  = $ctxCount
                 |requestCycle      = ${requestCycle.get(exp.id.vector)}
                 |""".stripMargin
            )
          }
        }

        dut.io
          .phase2_rope_param_update_out
          .expect(
            exp.metadata.B
          )

        outputCount += 1

      } else {
        for (
          lane <-
          0 until
          NumLines
        ) {
          dut.io
            .phase2_out_valid_vec(lane)
            .expect(false.B)
        }

        dut.io
          .phase2_rope_param_update_out
          .expect(false.B)
      }

      // ----------------------------------------------------------------------
      // Current accepted Phase1 beat.
      // ----------------------------------------------------------------------
      val p1Accepted =
        p1Id.nonEmpty &&
        !stallNow

      val p2Accepted =
        p2Id.nonEmpty &&
        !stallNow

      p1Id.foreach {
        id =>

          val isLast =
            id.beat ==
            beatsPerVector - 1

          dut.io
            .phase1_vector_done
            .expect(
              (
                isLast &&
                !stallNow
              ).B
            )

          if (!stallNow) {
            for (
              lane <-
              0 until
              NumLines
            ) {
              dut.io
                .phase1_out_valid_vec(lane)
                .expect(true.B)

              dut.io
                .phase1_out_vec(lane)
                .expect(
                  asUInt8(
                    vectors(id.vector)
                      .physicalBeats(id.beat)(lane)
                  ).U
                )
            }

            if (isLast) {
              completedAwaitingRequest
                .enqueue(
                  id.vector
                )

              completionCycle(id.vector) =
                cycle
            }

            p1FlatIndex += 1
          } else {
            for (
              lane <-
              0 until
              NumLines
            ) {
              dut.io
                .phase1_out_valid_vec(lane)
                .expect(false.B)
            }
          }
      }

      if (p1Id.isEmpty) {
        dut.io
          .phase1_vector_done
          .expect(false.B)
      }

      // ----------------------------------------------------------------------
      // Current accepted Phase2 beat.
      // ----------------------------------------------------------------------
      p2Id.foreach {
        id =>

          if (!stallNow) {
            val vec =
              vectors(id.vector)

            if (softmaxMode && id.beat == 0) {
              // These values must already be active before the first Phase2
              // beat is accepted. If this fails, the bug is context timing,
              // not the final multiply/rounding stage.
              val expTbl = expTable()
              val scaleTbl = reciprocalScaleTable()
              val g = goldenSoftmaxContext(vec, expTbl, scaleTbl)

              val rtlMaxRaw =
                dut.globalSoftMaxReg
                  .peek()
                  .litValue

              val rtlMax =
                if (rtlMaxRaw >= (BigInt(1) << (InBits - 1))) {
                  (rtlMaxRaw - (BigInt(1) << InBits)).toInt
                } else {
                  rtlMaxRaw.toInt
                }

              val rtlScale =
                dut.globalScaleReg
                  .peek()
                  .litValue

              assert(
                rtlMax == g.globalMax,
                s"[$modeName] vector ${id.vector} first Phase2 beat: active global max mismatch, expected=${g.globalMax}, actual=$rtlMax, cycle=$cycle"
              )

              assert(
                rtlScale == g.scale,
                s"[$modeName] vector ${id.vector} first Phase2 beat: active scale mismatch, expected=${g.scale}, actual=$rtlScale, globalSumQ=${g.globalSumQ}, cycle=$cycle"
              )
            }

            expectedOutQueue.enqueue(
              ExpectedOut(
                id =
                  id,
                data =
                  golden(id.vector)(id.beat),
                metadata =
                  vec.metadata(id.beat),
                acceptedCycle =
                  cycle
              )
            )

            if (
              id.beat ==
              beatsPerVector - 1
            ) {
              nbActive =
                None
            } else {
              nbActive =
                Some(
                  id.copy(
                    beat =
                      id.beat + 1
                  )
                )
            }
          }
      }

      // ----------------------------------------------------------------------
      // P1/P2 interleaving metrics.
      // ----------------------------------------------------------------------
      if (
        p1Accepted &&
        p2Accepted
      ) {
        overlapCount += 1

        val p1v =
          p1Id.get.vector

        val p2v =
          p2Id.get.vector

        if (
          p1v >
          p2v
        ) {
          crossVectorOverlapCount += 1
        }

        sameCycleEnqDeqCount += 1
      }

      // ----------------------------------------------------------------------
      // phase2_req:
      //   Scale-LUT read starts now.
      //   NB first beat becomes eligible exactly next wall-clock cycle and is
      //   held if that cycle is stalled.
      // ----------------------------------------------------------------------
      val req =
        dut.io
          .phase2_req
          .peek()
          .litToBoolean

      if (stallNow) {
        assert(
          !req,
          s"[$modeName] phase2_req must be suppressed while stalled at cycle $cycle"
        )
      }

      if (req) {
        assert(
          completedAwaitingRequest.nonEmpty,
          s"[$modeName] phase2_req at cycle $cycle with no completed Phase1 vector"
        )

        val vector =
          completedAwaitingRequest
            .dequeue()

        assert(
          !requestCycle
            .contains(vector),
          s"[$modeName] duplicate phase2_req for vector $vector"
        )

        requestCycle(vector) =
          cycle

        requestLatency +=
          cycle -
          completionCycle(vector)

        requestCount += 1

        nbRequestQueue.enqueue(
          NbRequest(
            vector =
              vector,
            readyCycle =
              cycle + 1
          )
        )

        // Explicitly stress the Scale-LUT/NB transition after the first two
        // requests. The first NB beat must survive until resume.
        if (
          stressStall &&
          vector < 2
        ) {
          postRequestStallRemaining =
            math.max(
              postRequestStallRemaining,
              2
            )
        }
      }

      // If a first Phase2 beat is accepted, its request must have happened
      // earlier and it cannot precede request+1.
      p2Id.foreach {
        id =>

          if (
            !stallNow &&
            id.beat == 0
          ) {
            assert(
              requestCycle
                .contains(id.vector),
              s"[$modeName] Phase2 vector ${id.vector} started before phase2_req"
            )

            assert(
              cycle >=
              requestCycle(id.vector) + 1,
              s"[$modeName] Phase2 vector ${id.vector} started too early"
            )

            if (!stressStall) {
              assert(
                cycle ==
                requestCycle(id.vector) + 1,
                s"[$modeName] no-stall NB latency must be exactly one cycle"
              )
            }
          }
      }

      dut.io
        .sync_alert
        .expect(false.B)

      dut.clock.step()

      cycle += 1
    }

    assert(
      cycle <
      maxCycles,
      s"[$modeName] timeout after $maxCycles cycles"
    )

    assert(
      requestCount ==
      vectors.length,
      s"[$modeName] expected ${vectors.length} phase2_req pulses, got $requestCount"
    )

    assert(
      outputCount ==
      totalP2Beats,
      s"[$modeName] expected $totalP2Beats output beats, got $outputCount"
    )

    assert(
      completedAwaitingRequest.isEmpty,
      s"[$modeName] completed vectors remained without phase2_req"
    )

    assert(
      nbRequestQueue.isEmpty,
      s"[$modeName] NB request queue not empty at end"
    )

    assert(
      nbActive.isEmpty,
      s"[$modeName] NB still active at end"
    )

    assert(
      expectedOutQueue.isEmpty,
      s"[$modeName] expected-output queue not empty at end"
    )

    assert(
      overlapCount > 0,
      s"[$modeName] Phase1 and Phase2 never overlapped"
    )

    assert(
      crossVectorOverlapCount > 0,
      s"[$modeName] no Phase1(vector N+1) / Phase2(vector N) interleaving observed"
    )

    assert(
      sameCycleEnqDeqCount > 0,
      s"[$modeName] metadata FIFO never exercised simultaneous enqueue/dequeue"
    )

    if (!stressStall) {
      assert(
        requestLatency.nonEmpty
      )

      // For a fixed mode/vector length the pipeline latency must be deterministic.
      assert(
        requestLatency.distinct.length == 1,
        s"[$modeName] non-deterministic phase1Last->phase2_req latency: ${requestLatency.mkString(",")}"
      )

      if (!softmaxMode) {
        assert(
          requestLatency.head == 4,
          s"[$modeName] distributed RMS/Layer reduction latency expected 4 cycles, got ${requestLatency.head}"
        )
      }
    } else {
      assert(
        stallCount > 0,
        s"[$modeName] stress test did not inject stalls"
      )
    }

    // Drain observation point.
    pokeAllInvalid(dut)

    dut.io.stall
      .poke(false.B)

    dut.clock.step(2)

    dut.io
      .metadata_count
      .expect(0.U)

    dut.io
      .sync_alert
      .expect(false.B)

    println(
      s"[$modeName] PASS: requests=$requestCount, outputs=$outputCount, " +
      s"overlap=$overlapCount, crossVector=$crossVectorOverlapCount, " +
      s"fifoEnqDeq=$sameCycleEnqDeqCount, stalls=$stallCount, " +
      s"reqLatency=${requestLatency.distinct.mkString("/")}"
    )
  }

  // ==========================================================================
  // 1. Distributed RMSNorm, aligned N=32
  // ==========================================================================
  it should
    "run aligned distributed RMSNorm with 16-lane integration and P1/P2 interleaving" in {

    val logicalLength =
      32

    val scale =
      rsqrtScaleTable()

    val vectors =
      makeRmsVectors(
        logicalLength =
          logicalLength,
        count =
          10
      )

    val golden =
      vectors.map {
        goldenRms(
          _,
          epsilon =
            0,
          scaleTable =
            scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines =
          NumLines,
        writeBits =
          WriteBits,
        inBits =
          InBits,
        outBits =
          OutBits,
        vectorSize =
          MaxVectorLen,
        indexBits =
          IndexBits,
        dataBits =
          DataBits,
        lutFracBits =
          LutFracBits,
        invBits =
          InvBits,
        invFracBits =
          InvFracBits,
        metadataDepth =
          MetadataDepth
      )
    ) {
      dut =>

        configureAndProgram(
          dut,
          mode =
            1,
          logicalLength =
            logicalLength,
          expLut =
            None,
          scaleLut =
            scale
        )

        runDistributedScenario(
          dut,
          modeName =
            "RMSNorm-DISTRIBUTED-N32",
          mode =
            1,
          vectors =
            vectors,
          golden =
            golden,
          stressStall =
            false,
          softmaxMode =
            false
        )
    }
  }

  // ==========================================================================
  // 2. Distributed LayerNorm, padded N=30 -> physical 32
  //
  // Phase1 stats: only 30 logical elements.
  // Phase2 output: all 32 physical elements, including the two padding zeros.
  // ==========================================================================
  it should
    "run padded distributed LayerNorm using N=30 statistics while still processing all 32 Phase2 elements" in {

    val logicalLength =
      30

    val scale =
      rsqrtScaleTable()

    val vectors =
      makeLayerVectors(
        logicalLength =
          logicalLength,
        count =
          10
      )

    val golden =
      vectors.map {
        goldenLayer(
          _,
          epsilon =
            0,
          scaleTable =
            scale
        )
      }

    // Padding outputs should not be force-masked.
    val hasNonZeroPaddingOutput =
      golden.exists {
        vectorOut =>
          val last =
            vectorOut.last

          last(14) != 0 ||
          last(15) != 0
      }

    assert(
      hasNonZeroPaddingOutput,
      "LayerNorm padding stimulus failed to produce a non-zero processed padding output"
    )

    test(
      new UniversalNormUnit(
        numLines =
          NumLines,
        writeBits =
          WriteBits,
        inBits =
          InBits,
        outBits =
          OutBits,
        vectorSize =
          MaxVectorLen,
        indexBits =
          IndexBits,
        dataBits =
          DataBits,
        lutFracBits =
          LutFracBits,
        invBits =
          InvBits,
        invFracBits =
          InvFracBits,
        metadataDepth =
          MetadataDepth
      )
    ) {
      dut =>

        configureAndProgram(
          dut,
          mode =
            2,
          logicalLength =
            logicalLength,
          expLut =
            None,
          scaleLut =
            scale
        )

        runDistributedScenario(
          dut,
          modeName =
            "LayerNorm-DISTRIBUTED-N30-PAD32",
          mode =
            2,
          vectors =
            vectors,
          golden =
            golden,
          stressStall =
            false,
          softmaxMode =
            false
        )
    }
  }

  // ==========================================================================
  // 3. Distributed Softmax, padded N=30 -> physical 32
  //
  // Verifies:
  //   local Milakov -> global max correction -> global corrected sum
  //   while padding is excluded from Phase1 statistics.
  // ==========================================================================
  it should
    "run padded distributed Milakov Softmax with global max correction and P1/P2 interleaving" in {

    val logicalLength =
      30

    val exp =
      expTable()

    val scale =
      reciprocalScaleTable()

    val vectors =
      makeSoftmaxVectors(
        logicalLength =
          logicalLength,
        count =
          12
      )

    val golden =
      vectors.map {
        goldenSoftmaxDistributed(
          _,
          expLut =
            exp,
          scaleTable =
            scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines =
          NumLines,
        writeBits =
          WriteBits,
        inBits =
          InBits,
        outBits =
          OutBits,
        vectorSize =
          MaxVectorLen,
        indexBits =
          IndexBits,
        dataBits =
          DataBits,
        lutFracBits =
          LutFracBits,
        invBits =
          InvBits,
        invFracBits =
          InvFracBits,
        metadataDepth =
          MetadataDepth
      )
    ) {
      dut =>

        configureAndProgram(
          dut,
          mode =
            3,
          logicalLength =
            logicalLength,
          expLut =
            Some(exp),
          scaleLut =
            scale
        )

        runDistributedScenario(
          dut,
          modeName =
            "Softmax-DISTRIBUTED-N30-PAD32",
          mode =
            3,
          vectors =
            vectors,
          golden =
            golden,
          stressStall =
            false,
          softmaxMode =
            true
        )
    }
  }

  // ==========================================================================
  // 4. LayerNorm targeted + random stall stress
  //
  // N=48 gives 3 physical beats so a true middle beat exists.
  // ==========================================================================
  it should
    "preserve distributed LayerNorm state, metadata ordering, and NB transition across targeted and random stalls" in {

    val logicalLength =
      48

    val scale =
      rsqrtScaleTable()

    val vectors =
      makeLayerVectors(
        logicalLength =
          logicalLength,
        count =
          20
      )

    val golden =
      vectors.map {
        goldenLayer(
          _,
          epsilon =
            0,
          scaleTable =
            scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines =
          NumLines,
        writeBits =
          WriteBits,
        inBits =
          InBits,
        outBits =
          OutBits,
        vectorSize =
          MaxVectorLen,
        indexBits =
          IndexBits,
        dataBits =
          DataBits,
        lutFracBits =
          LutFracBits,
        invBits =
          InvBits,
        invFracBits =
          InvFracBits,
        metadataDepth =
          MetadataDepth
      )
    ) {
      dut =>

        configureAndProgram(
          dut,
          mode =
            2,
          logicalLength =
            logicalLength,
          expLut =
            None,
          scaleLut =
            scale
        )

        runDistributedScenario(
          dut,
          modeName =
            "LayerNorm-DISTRIBUTED-STALL",
          mode =
            2,
          vectors =
            vectors,
          golden =
            golden,
          stressStall =
            true,
          softmaxMode =
            false
        )
    }
  }

  // ==========================================================================
  // 5. Softmax targeted + random stall stress
  //
  // This is intentionally aggressive. It can expose an apply-Exp-LUT conflict
  // if distributed global correction overlaps prior-vector Phase2 Softmax.
  // sync_alert is required to remain low; if this test fails on applyExpConflict,
  // the architecture needs a separate correction Exp-LUT bank or scheduling.
  // ==========================================================================
  it should
    "preserve distributed Softmax correction and Phase2 state across targeted and random stalls" in {

    val logicalLength =
      48

    val exp =
      expTable()

    val scale =
      reciprocalScaleTable()

    val vectors =
      makeSoftmaxVectors(
        logicalLength =
          logicalLength,
        count =
          8
      )

    val golden =
      vectors.map {
        goldenSoftmaxDistributed(
          _,
          expLut =
            exp,
          scaleTable =
            scale
        )
      }

    test(
      new UniversalNormUnit(
        numLines =
          NumLines,
        writeBits =
          WriteBits,
        inBits =
          InBits,
        outBits =
          OutBits,
        vectorSize =
          MaxVectorLen,
        indexBits =
          IndexBits,
        dataBits =
          DataBits,
        lutFracBits =
          LutFracBits,
        invBits =
          InvBits,
        invFracBits =
          InvFracBits,
        metadataDepth =
          MetadataDepth
      )
    ) {
      dut =>

        configureAndProgram(
          dut,
          mode =
            3,
          logicalLength =
            logicalLength,
          expLut =
            Some(exp),
          scaleLut =
            scale
        )

        runDistributedScenario(
          dut,
          modeName =
            "Softmax-DISTRIBUTED-STALL",
          mode =
            3,
          vectors =
            vectors,
          golden =
            golden,
          stressStall =
            true,
          softmaxMode =
            true
        )
    }
  }
}
