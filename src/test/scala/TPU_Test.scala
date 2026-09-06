package npu.top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class TPUTopTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Autonomous TPU_top"

  private def runScenario(
    dut: TPU_top,
    dim: Int,
    numOutputs: Int,
    numKTiles: Int,
    colNum: Int,
    seed: Long,
    stallFn: Int => Boolean,
    requireNoBubble: Boolean
  ): Unit = {
    val totalTiles = numOutputs * numKTiles
    val rng = new Random(seed)

    // A[y][q][m][k], W[y][q][n][k]
    val aTiles = Array.tabulate(numOutputs, numKTiles, dim, dim) {
      (_, _, _, _) => rng.nextInt(31) - 15
    }
    val wTiles = Array.tabulate(numOutputs, numKTiles, dim, dim) {
      (_, _, _, _) => rng.nextInt(31) - 15
    }

    // Golden Y[y][m][n]
    val golden = Array.ofDim[Int](numOutputs, dim, dim)
    for {
      y <- 0 until numOutputs
      m <- 0 until dim
      n <- 0 until dim
    } {
      var sum = 0
      for {
        q <- 0 until numKTiles
        k <- 0 until dim
      } {
        sum += aTiles(y)(q)(m)(k) * wTiles(y)(q)(n)(k)
      }
      golden(y)(m)(n) = sum
    }

    def tileToYQ(globalTile: Int): (Int, Int) =
      (globalTile / numKTiles, globalTile % numKTiles)

    def driveInputZero(): Unit =
      for (k <- 0 until dim) dut.io.in_input(k).poke(0.S(8.W))

    def driveWeightZero(): Unit =
      for (k <- 0 until dim) dut.io.in_weight(k).poke(0.S(8.W))

    def driveInputRow(globalTile: Int, m: Int): Unit = {
      val (y, q) = tileToYQ(globalTile)
      for (k <- 0 until dim)
        dut.io.in_input(k).poke(aTiles(y)(q)(m)(k).S(8.W))
    }

    def driveWeightRow(globalTile: Int, n: Int): Unit = {
      val (y, q) = tileToYQ(globalTile)
      for (k <- 0 until dim)
        dut.io.in_weight(k).poke(wTiles(y)(q)(n)(k).S(8.W))
    }

    def expectedParamUpdate(y: Int): Boolean =
      (y % colNum) == 0

    // fusionCounter initial=15, reload=31
    // => Y15, Y47, Y79, ...
    def expectedFusion(y: Int): Boolean =
      y >= 15 && ((y - 15) % 32 == 0)

    // Initial state
    driveInputZero()
    driveWeightZero()
    dut.io.input_valid.poke(false.B)
    dut.io.input_tile_start.poke(false.B)
    dut.io.weight_valid.poke(false.B)
    dut.io.intermNum.poke(numKTiles.U(32.W))
    dut.io.colNum.poke(colNum.U(32.W))
    dut.io.stall.poke(false.B)
    dut.io.clear_W.poke(true.B)

    dut.clock.step()
    dut.io.clear_W.poke(false.B)

    // Initial W0 preload
    for (n <- 0 until dim) {
      driveInputZero()
      driveWeightRow(0, n)
      dut.io.input_valid.poke(false.B)
      dut.io.input_tile_start.poke(false.B)
      dut.io.weight_valid.poke(true.B)
      dut.io.stall.poke(false.B)
      dut.io.fatal_alert.expect(false.B)
      dut.clock.step()
    }
    dut.io.weight_valid.poke(false.B)

    val totalComputeCycles = totalTiles * dim
    val expectedOutputRows = numOutputs * dim

    var logicalCycle = 0
    var physicalCycle = 0
    var outputRowsSeen = 0
    var streamStarted = false

    // fusion_req seen on previous accepted cycle.
    var fusionDueForRow0 = false
    var fusionPulseCount = 0

    while (outputRowsSeen < expectedOutputRows && physicalCycle < 100000) {
      val stallNow = stallFn(physicalCycle)

      // A stream: logical schedule does not advance during stall.
      if (logicalCycle < totalComputeCycles) {
        val globalTile = logicalCycle / dim
        val m = logicalCycle % dim
        driveInputRow(globalTile, m)
        dut.io.input_valid.poke(true.B)
        dut.io.input_tile_start.poke((m == 0).B)
      } else {
        driveInputZero()
        dut.io.input_valid.poke(false.B)
        dut.io.input_tile_start.poke(false.B)
      }

      // Rolling weight load:
      // W1 = logical 15..30
      // W2 = logical 31..46
      // ...
      val relativeWeightCycle = logicalCycle - (dim - 1)

      if (relativeWeightCycle >= 0) {
        val nextGlobalTile = 1 + relativeWeightCycle / dim
        val n = relativeWeightCycle % dim

        if (nextGlobalTile < totalTiles) {
          driveWeightRow(nextGlobalTile, n)
          dut.io.weight_valid.poke(true.B)
        } else {
          driveWeightZero()
          dut.io.weight_valid.poke(false.B)
        }
      } else {
        driveWeightZero()
        dut.io.weight_valid.poke(false.B)
      }

      dut.io.stall.poke(stallNow.B)

      val valid0 = dut.io.out_valid(0).peek().litToBoolean
      val paramNow = dut.io.out_meta.param_update.peek().litToBoolean
      val fusionNow = dut.io.out_meta.fusion_req.peek().litToBoolean

      if (stallNow) {
        // Visible output is invalid during stall.
        for (n <- 0 until dim) dut.io.out_valid(n).expect(false.B)
        dut.io.out_meta.param_update.expect(false.B)
        dut.io.out_meta.fusion_req.expect(false.B)
      } else {
        if (valid0) {
          val y = outputRowsSeen / dim
          val m = outputRowsSeen % dim

          for (n <- 0 until dim) {
            dut.io.out_valid(n).expect(true.B)
            dut.io.out_accum(n).expect(golden(y)(m)(n).S(32.W))
          }

          // param_update must travel WITH row0.
          val expectedParam =
            (m == 0) && expectedParamUpdate(y)

          assert(
            paramNow == expectedParam,
            s"param_update mismatch: physical=$physicalCycle y=$y m=$m " +
            s"expected=$expectedParam actual=$paramNow"
          )

          // fusion_req must have appeared exactly one accepted cycle BEFORE row0.
          if (m == 0) {
            val expectedFusionForTile = expectedFusion(y)

            assert(
              fusionDueForRow0 == expectedFusionForTile,
              s"fusion_req timing mismatch at Y$y row0: " +
              s"expectedPreviousCycle=$expectedFusionForTile " +
              s"actualPreviousCycle=$fusionDueForRow0"
            )
          }

          outputRowsSeen += 1
          streamStarted = true
        } else {
          // param_update may never exist without a row0.
          assert(
            !paramNow,
            s"param_update asserted without valid output at physical=$physicalCycle"
          )

          if (requireNoBubble && streamStarted && outputRowsSeen < expectedOutputRows) {
            fail(
              s"Unexpected logical output bubble: physical=$physicalCycle " +
              s"logical=$logicalCycle rowsSeen=$outputRowsSeen"
            )
          }
        }

        if (fusionNow) {
          fusionPulseCount += 1
        }

        // For the next accepted cycle.
        fusionDueForRow0 = fusionNow
      }

      dut.io.fatal_alert.expect(false.B)
      dut.clock.step()

      if (!stallNow) logicalCycle += 1
      physicalCycle += 1
    }

    assert(
      outputRowsSeen == expectedOutputRows,
      s"Expected $expectedOutputRows output rows, got $outputRowsSeen"
    )

    val expectedFusionCount =
      (0 until numOutputs).count(expectedFusion)

    assert(
      fusionPulseCount == expectedFusionCount,
      s"Expected $expectedFusionCount fusion_req pulses, got $fusionPulseCount"
    )
  }

  it should "stream intermNum=1 tiles with zero-bubble ping-pong and exact metadata timing" in {
    val dim = 16

    test(new TPU_top(
      numRows = dim,
      numCols = dim,
      inBits = 8,
      accBits = 32
    )) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numOutputs = 20,   // includes Y15 fusion event
        numKTiles = 1,
        colNum = 4,
        seed = 0x10012002L,
        stallFn = _ => false,
        requireNoBubble = true
      )
    }
  }

  it should "accumulate multiple K tiles under autonomous ComputeTimer control" in {
    val dim = 16

    test(new TPU_top(
      numRows = dim,
      numCols = dim,
      inBits = 8,
      accBits = 32
    )) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numOutputs = 6,
        numKTiles = 3,
        colNum = 3,
        seed = 0x30042006L,
        stallFn = _ => false,
        requireNoBubble = false
      )
    }
  }

  it should "preserve data and metadata timing across global stalls" in {
    val dim = 16

    test(new TPU_top(
      numRows = dim,
      numCols = dim,
      inBits = 8,
      accBits = 32
    )) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numOutputs = 20,
        numKTiles = 1,
        colNum = 4,
        seed = 0x55aa77ccL,
        stallFn = p =>
          (p % 19 == 5) ||
          (p % 19 == 6) ||
          (p % 37 == 11),
        requireNoBubble = true
      )
    }
  }
}