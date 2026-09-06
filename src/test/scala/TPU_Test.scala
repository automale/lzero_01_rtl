package npu.top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class TPUTopTest
    extends AnyFlatSpec
    with ChiselScalatestTester {

  behavior of "Autonomous TPU_top"

  private def runScenario(
    dut: TPU_top,
    dim: Int,
    numOutputs: Int,
    numKTiles: Int,
    colNum: Int,
    normPhaseLoad: Int,
    seed: Long,
    stallFn: Int => Boolean,
    requireNoBubble: Boolean
  ): Unit = {

    val totalTiles = numOutputs * numKTiles
    val rng = new Random(seed)

    val aTiles =
      Array.tabulate(numOutputs, numKTiles, dim, dim) {
        (_, _, _, _) => rng.nextInt(31) - 15
      }

    val wTiles =
      Array.tabulate(numOutputs, numKTiles, dim, dim) {
        (_, _, _, _) => rng.nextInt(31) - 15
      }

    val golden =
      Array.ofDim[Int](numOutputs, dim, dim)

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
        sum +=
          aTiles(y)(q)(m)(k) *
          wTiles(y)(q)(n)(k)
      }
      golden(y)(m)(n) = sum
    }

    def tileToYQ(globalTile: Int): (Int, Int) =
      (
        globalTile / numKTiles,
        globalTile % numKTiles
      )

    def driveInputZero(): Unit =
      for (k <- 0 until dim)
        dut.io.in_input(k).poke(0.S(8.W))

    def driveWeightZero(): Unit =
      for (k <- 0 until dim)
        dut.io.in_weight(k).poke(0.S(8.W))

    def driveInputRow(globalTile: Int, m: Int): Unit = {
      val (y, q) = tileToYQ(globalTile)
      for (k <- 0 until dim)
        dut.io.in_input(k)
          .poke(aTiles(y)(q)(m)(k).S(8.W))
    }

    def driveWeightRow(globalTile: Int, n: Int): Unit = {
      val (y, q) = tileToYQ(globalTile)
      for (k <- 0 until dim)
        dut.io.in_weight(k)
          .poke(wTiles(y)(q)(n)(k).S(8.W))
    }

    def expectedParamUpdate(y: Int): Boolean =
      (y % colNum) == 0

    def expectedNormChange(y: Int): Boolean =
      (y % (normPhaseLoad + 1)) == 0

    def expectedFusionChange(y: Int): Boolean =
      y >= 15 &&
      ((y - 15) % 32) == 0

    // ----------------------------------------------------------------------
    // Initial configuration
    // ----------------------------------------------------------------------

    driveInputZero()
    driveWeightZero()

    dut.io.input_valid.poke(false.B)
    dut.io.input_tile_start.poke(false.B)
    dut.io.weight_valid.poke(false.B)

    dut.io.intermNum.poke(numKTiles.U(32.W))
    dut.io.colNum.poke(colNum.U(32.W))
    dut.io.norm_phase_load.poke(normPhaseLoad.U(32.W))

    dut.io.stall.poke(false.B)
    dut.io.clear_W.poke(true.B)

    dut.clock.step(1)
    dut.io.clear_W.poke(false.B)

    // ----------------------------------------------------------------------
    // Initial W0 preload
    // ----------------------------------------------------------------------

    for (n <- 0 until dim) {
      driveInputZero()
      dut.io.input_valid.poke(false.B)
      dut.io.input_tile_start.poke(false.B)

      driveWeightRow(globalTile = 0, n = n)
      dut.io.weight_valid.poke(true.B)

      dut.io.stall.poke(false.B)
      dut.io.fatal_alert.expect(false.B)

      dut.clock.step(1)
    }

    dut.io.weight_valid.poke(false.B)

    // ----------------------------------------------------------------------
    // Autonomous execution
    // ----------------------------------------------------------------------

    val totalComputeCycles =
      totalTiles * dim

    val expectedOutputRows =
      numOutputs * dim

    var logicalCycle = 0
    var physicalCycle = 0
    var outputRowsSeen = 0
    var streamStarted = false

    while (
      outputRowsSeen < expectedOutputRows &&
      physicalCycle < 100000
    ) {

      val stallNow =
        stallFn(physicalCycle)

      // --------------------------------------------------------------------
      // A stream
      // --------------------------------------------------------------------

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

      // --------------------------------------------------------------------
      // Rolling W preload
      //
      // W1 : cycles 15..30
      // W2 : cycles 31..46
      // ...
      // --------------------------------------------------------------------

      val relativeWeightCycle =
        logicalCycle - (dim - 1)

      if (relativeWeightCycle >= 0) {
        val nextGlobalTile =
          1 + relativeWeightCycle / dim

        val n =
          relativeWeightCycle % dim

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

      // --------------------------------------------------------------------
      // Output scoreboard
      // --------------------------------------------------------------------

      val valid0 =
        dut.io.out_valid(0)
          .peek()
          .litToBoolean

      if (stallNow) {
        for (n <- 0 until dim)
          dut.io.out_valid(n).expect(false.B)

        dut.io.out_meta.param_update.expect(false.B)
        dut.io.out_meta.fusion_change.expect(false.B)
        dut.io.out_meta.norm_phase_change.expect(false.B)

      } else if (valid0) {

        val y =
          outputRowsSeen / dim

        val m =
          outputRowsSeen % dim

        for (n <- 0 until dim) {
          dut.io.out_valid(n).expect(true.B)
          dut.io.out_accum(n).expect(
            golden(y)(m)(n).S(32.W)
          )
        }

        val firstRow =
          m == 0

        dut.io.out_meta.param_update.expect(
          (
            firstRow &&
            expectedParamUpdate(y)
          ).B
        )

        dut.io.out_meta.fusion_change.expect(
          (
            firstRow &&
            expectedFusionChange(y)
          ).B
        )

        dut.io.out_meta.norm_phase_change.expect(
          (
            firstRow &&
            expectedNormChange(y)
          ).B
        )

        outputRowsSeen += 1
        streamStarted = true

      } else {

        for (n <- 0 until dim)
          dut.io.out_valid(n).expect(false.B)

        dut.io.out_meta.param_update.expect(false.B)
        dut.io.out_meta.fusion_change.expect(false.B)
        dut.io.out_meta.norm_phase_change.expect(false.B)

        if (
          requireNoBubble &&
          streamStarted &&
          outputRowsSeen < expectedOutputRows
        ) {
          fail(
            s"Unexpected output bubble: " +
            s"physical=$physicalCycle logical=$logicalCycle " +
            s"rowsSeen=$outputRowsSeen"
          )
        }
      }

      dut.io.fatal_alert.expect(false.B)

      dut.clock.step(1)

      if (!stallNow)
        logicalCycle += 1

      physicalCycle += 1
    }

    assert(
      outputRowsSeen == expectedOutputRows,
      s"Expected $expectedOutputRows output rows, " +
      s"observed $outputRowsSeen"
    )
  }

  it should "stream intermNum=1 output tiles with zero-bubble true ping-pong handoff and correct metadata" in {
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
        numOutputs = 20,
        numKTiles = 1,
        colNum = 4,
        normPhaseLoad = 2,
        seed = 0x10012002L,
        stallFn = _ => false,
        requireNoBubble = true
      )
    }
  }

  it should "correctly accumulate multiple K tiles with autonomous ComputeTimer control" in {
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
        numOutputs = 4,
        numKTiles = 3,
        colNum = 2,
        normPhaseLoad = 1,
        seed = 0x30042006L,
        stallFn = _ => false,
        requireNoBubble = false
      )
    }
  }

  it should "preserve true ping-pong output ordering and metadata across global stalls" in {
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
        numOutputs = 8,
        numKTiles = 1,
        colNum = 4,
        normPhaseLoad = 2,
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