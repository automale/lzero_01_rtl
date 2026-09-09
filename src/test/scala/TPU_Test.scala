package npu.top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class TPUTopTest
  extends AnyFlatSpec
  with ChiselScalatestTester {

  behavior of "TPU_top"

  private def runScenario(
    dut: TPU_top,
    dim: Int,
    numMGroups: Int,
    outColNum: Int,
    numKTiles: Int,
    seed: Long,
    stallFn: Int => Boolean,
    requireNoBubble: Boolean
  ): Unit = {

    require(dim == 16)
    require(numMGroups > 0)
    require(outColNum > 0)
    require(numKTiles > 0)

    val totalOutputTiles = numMGroups * outColNum
    val totalComputeTiles = totalOutputTiles * numKTiles
    val totalComputeCycles = totalComputeTiles * dim
    val expectedOutputRows = totalOutputTiles * dim

    val rng = new Random(seed)

    // A[mGroup][kTile][m][k]
    // Same A tile is reused across all N tiles in the same M group.
    val aTiles =
      Array.tabulate(numMGroups, numKTiles, dim, dim) {
        (_, _, _, _) => rng.nextInt(31) - 15
      }

    // W[nTile][kTile][n][k]
    // Same W tile is reused across M groups.
    val wTiles =
      Array.tabulate(outColNum, numKTiles, dim, dim) {
        (_, _, _, _) => rng.nextInt(31) - 15
      }

    // Golden Y[mGroup][nTile][m][n]
    val golden = Array.ofDim[Int](numMGroups, outColNum, dim, dim)

    for {
      mg <- 0 until numMGroups
      nt <- 0 until outColNum
      m  <- 0 until dim
      n  <- 0 until dim
    } {
      var sum = 0
      for {
        q <- 0 until numKTiles
        k <- 0 until dim
      } {
        sum += aTiles(mg)(q)(m)(k) * wTiles(nt)(q)(n)(k)
      }
      golden(mg)(nt)(m)(n) = sum
    }

    // Compute stream order:
    //   output tile: M-group -> N-tile
    //   inside output tile: K-tile
    def decodeComputeTile(globalComputeTile: Int): (Int, Int, Int) = {
      val outputTile = globalComputeTile / numKTiles
      val q = globalComputeTile % numKTiles
      val mg = outputTile / outColNum
      val nt = outputTile % outColNum
      (mg, nt, q)
    }

    def decodeOutputTile(outputTile: Int): (Int, Int) =
      (outputTile / outColNum, outputTile % outColNum)

    def driveInputZero(): Unit =
      for (k <- 0 until dim) dut.io.in_input(k).poke(0.S(8.W))

    def driveWeightZero(): Unit =
      for (k <- 0 until dim) dut.io.in_weight(k).poke(0.S(8.W))

    def driveInputRow(globalComputeTile: Int, m: Int): Unit = {
      val (mg, _, q) = decodeComputeTile(globalComputeTile)
      for (k <- 0 until dim) {
        dut.io.in_input(k).poke(aTiles(mg)(q)(m)(k).S(8.W))
      }
    }

    def driveWeightRow(globalComputeTile: Int, n: Int): Unit = {
      val (_, nt, q) = decodeComputeTile(globalComputeTile)
      for (k <- 0 until dim) {
        dut.io.in_weight(k).poke(wTiles(nt)(q)(n)(k).S(8.W))
      }
    }

    def expectedFusion(outputTile: Int): Boolean =
      outputTile >= 15 && ((outputTile - 15) % 32 == 0)

    // Reset / configuration
    driveInputZero()
    driveWeightZero()

    dut.io.input_valid.poke(false.B)
    dut.io.input_tile_start.poke(false.B)
    dut.io.weight_valid.poke(false.B)

    dut.io.intermNum.poke(numKTiles.U)
    dut.io.outColNum.poke(outColNum.U)

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

    var logicalCycle = 0
    var physicalCycle = 0
    var outputRowsSeen = 0
    var streamStarted = false

    var fusionFromPreviousAcceptedCycle = false
    var rowChangePulseCount = 0
    var fusionPulseCount = 0

    while (
      outputRowsSeen < expectedOutputRows &&
      physicalCycle < 100000
    ) {

      val stallNow = stallFn(physicalCycle)

      // Input schedule
      if (logicalCycle < totalComputeCycles) {
        val globalComputeTile = logicalCycle / dim
        val m = logicalCycle % dim

        driveInputRow(globalComputeTile, m)
        dut.io.input_valid.poke(true.B)
        dut.io.input_tile_start.poke((m == 0).B)
      } else {
        driveInputZero()
        dut.io.input_valid.poke(false.B)
        dut.io.input_tile_start.poke(false.B)
      }

      // Rolling W preload:
      // W1 = logical cycle 15..30, W2 = 31..46, ...
      val relativeWeightCycle = logicalCycle - (dim - 1)

      if (relativeWeightCycle >= 0) {
        val nextGlobalComputeTile = 1 + relativeWeightCycle / dim
        val n = relativeWeightCycle % dim

        if (nextGlobalComputeTile < totalComputeTiles) {
          driveWeightRow(nextGlobalComputeTile, n)
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

      val valid = dut.io.out_valid(0).peek().litToBoolean
      val rowChangeNow =
        dut.io.out_meta.row_change_update.peek().litToBoolean
      val fusionNow =
        dut.io.fusion_req.peek().litToBoolean

      if (stallNow) {
        for (n <- 0 until dim) dut.io.out_valid(n).expect(false.B)
        dut.io.out_meta.row_change_update.expect(false.B)
        dut.io.fusion_req.expect(false.B)
      } else {
        if (valid) {
          val outputTile = outputRowsSeen / dim
          val row = outputRowsSeen % dim
          val (mg, nt) = decodeOutputTile(outputTile)

          for (n <- 0 until dim) {
            dut.io.out_valid(n).expect(true.B)
            dut.io.out_accum(n).expect(golden(mg)(nt)(row)(n).S(32.W))
          }

          // row_change_update = row0 of N-tile0 only.
          val expectedRowChange =
            row == 0 && nt == 0

          assert(
            rowChangeNow == expectedRowChange,
            s"row_change_update mismatch: physical=$physicalCycle " +
            s"outputTile=$outputTile mGroup=$mg nTile=$nt row=$row " +
            s"expected=$expectedRowChange actual=$rowChangeNow"
          )

          if (rowChangeNow) rowChangePulseCount += 1

          // fusion_req must be one accepted cycle before selected tile row0.
          if (row == 0) {
            val expectedFusionNow = expectedFusion(outputTile)

            assert(
              fusionFromPreviousAcceptedCycle == expectedFusionNow,
              s"fusion_req timing mismatch: outputTile=$outputTile " +
              s"expectedPrevious=$expectedFusionNow " +
              s"actualPrevious=$fusionFromPreviousAcceptedCycle"
            )
          }

          outputRowsSeen += 1
          streamStarted = true
        } else {
          assert(
            !rowChangeNow,
            s"row_change_update asserted without output valid at physical=$physicalCycle"
          )

          if (
            requireNoBubble &&
            streamStarted &&
            outputRowsSeen < expectedOutputRows
          ) {
            fail(
              s"Unexpected output bubble: physical=$physicalCycle " +
              s"logical=$logicalCycle rowsSeen=$outputRowsSeen"
            )
          }
        }

        if (fusionNow) fusionPulseCount += 1
        fusionFromPreviousAcceptedCycle = fusionNow
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

    assert(
      rowChangePulseCount == numMGroups,
      s"Expected $numMGroups row_change_update pulses, got $rowChangePulseCount"
    )

    val expectedFusionCount =
      (0 until totalOutputTiles).count(expectedFusion)

    assert(
      fusionPulseCount == expectedFusionCount,
      s"Expected $expectedFusionCount fusion_req pulses, got $fusionPulseCount"
    )
  }

  it should "stream K=1 continuously and assert row_change only at each new M-group" in {
    val dim = 16

    test(
      new TPU_top(
        numRows = dim,
        numCols = dim,
        inBits = 8,
        accBits = 32
      )
    ) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numMGroups = 3,
        outColNum = 8, // N=128 equivalent
        numKTiles = 1,
        seed = 0x10012002L,
        stallFn = _ => false,
        requireNoBubble = true
      )
    }
  }

  it should "preserve N-tile and M-group metadata under multi-K accumulation" in {
    val dim = 16

    test(
      new TPU_top(
        numRows = dim,
        numCols = dim,
        inBits = 8,
        accBits = 32
      )
    ) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numMGroups = 2,
        outColNum = 4,
        numKTiles = 3,
        seed = 0x30042006L,
        stallFn = _ => false,
        requireNoBubble = false
      )
    }
  }

  it should "hold row-change and tile progress correctly across global stalls" in {
    val dim = 16

    test(
      new TPU_top(
        numRows = dim,
        numCols = dim,
        inBits = 8,
        accBits = 32
      )
    ) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numMGroups = 3,
        outColNum = 8,
        numKTiles = 1,
        seed = 0x55aa77ccL,
        stallFn = p =>
          (p % 19 == 5) ||
          (p % 19 == 6) ||
          (p % 37 == 11),
        requireNoBubble = true
      )
    }
  }

  it should "assert row_change on every tile when outColNum is one" in {
    val dim = 16

    test(
      new TPU_top(
        numRows = dim,
        numCols = dim,
        inBits = 8,
        accBits = 32
      )
    ) { dut =>
      runScenario(
        dut = dut,
        dim = dim,
        numMGroups = 6,
        outColNum = 1,
        numKTiles = 1,
        seed = 0x0c0ffeeL,
        stallFn = _ => false,
        requireNoBubble = true
      )
    }
  }
}
