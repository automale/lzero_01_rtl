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
    numOutputs: Int,
    numKTiles: Int,
    quantParamTilePeriod: Int,
    seed: Long,
    stallFn: Int => Boolean,
    requireNoBubble: Boolean
  ): Unit = {

    val totalTiles =
      numOutputs * numKTiles

    val rng =
      new Random(seed)

    // A[outputTile][kTile][m][k]
    val aTiles =
      Array.tabulate(
        numOutputs,
        numKTiles,
        dim,
        dim
      ) {
        (_, _, _, _) =>
          rng.nextInt(31) - 15
      }

    // W[outputTile][kTile][n][k]
    val wTiles =
      Array.tabulate(
        numOutputs,
        numKTiles,
        dim,
        dim
      ) {
        (_, _, _, _) =>
          rng.nextInt(31) - 15
      }

    // Golden Y[outputTile][m][n]
    val golden =
      Array.ofDim[Int](
        numOutputs,
        dim,
        dim
      )

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

      golden(y)(m)(n) =
        sum
    }

    def tileToYQ(
      globalTile: Int
    ): (Int, Int) =
      (
        globalTile / numKTiles,
        globalTile % numKTiles
      )

    def driveInputZero(): Unit = {
      for(k <- 0 until dim) {
        dut.io.in_input(k)
          .poke(0.S(8.W))
      }
    }

    def driveWeightZero(): Unit = {
      for(k <- 0 until dim) {
        dut.io.in_weight(k)
          .poke(0.S(8.W))
      }
    }

    def driveInputRow(
      globalTile: Int,
      m: Int
    ): Unit = {

      val (y, q) =
        tileToYQ(globalTile)

      for(k <- 0 until dim) {
        dut.io.in_input(k)
          .poke(
            aTiles(y)(q)(m)(k)
              .S(8.W)
          )
      }
    }

    def driveWeightRow(
      globalTile: Int,
      n: Int
    ): Unit = {

      val (y, q) =
        tileToYQ(globalTile)

      for(k <- 0 until dim) {
        dut.io.in_weight(k)
          .poke(
            wTiles(y)(q)(n)(k)
              .S(8.W)
          )
      }
    }

    def expectedQuant(
      outputTile: Int
    ): Boolean = {

      quantParamTilePeriod > 0 &&
      (
        outputTile %
        quantParamTilePeriod
      ) == 0
    }

    def expectedFusion(
      outputTile: Int
    ): Boolean = {

      outputTile >= 15 &&
      (
        (outputTile - 15) %
        32
      ) == 0
    }

    // ========================================================================
    // Reset / initial configuration
    // ========================================================================
    driveInputZero()
    driveWeightZero()

    dut.io.input_valid
      .poke(false.B)

    dut.io.input_tile_start
      .poke(false.B)

    dut.io.weight_valid
      .poke(false.B)

    dut.io.intermNum
      .poke(numKTiles.U)

    dut.io.quantParamTilePeriod
      .poke(quantParamTilePeriod.U)

    dut.io.stall
      .poke(false.B)

    dut.io.clear_W
      .poke(true.B)

    dut.clock.step()

    dut.io.clear_W
      .poke(false.B)

    // ========================================================================
    // Initial W0 preload
    // ========================================================================
    for(n <- 0 until dim) {

      driveInputZero()
      driveWeightRow(0, n)

      dut.io.input_valid
        .poke(false.B)

      dut.io.input_tile_start
        .poke(false.B)

      dut.io.weight_valid
        .poke(true.B)

      dut.io.stall
        .poke(false.B)

      dut.io.fatal_alert
        .expect(false.B)

      dut.clock.step()
    }

    dut.io.weight_valid
      .poke(false.B)

    val totalComputeCycles =
      totalTiles * dim

    val expectedOutputRows =
      numOutputs * dim

    var logicalCycle =
      0

    var physicalCycle =
      0

    var outputRowsSeen =
      0

    var streamStarted =
      false

    var fusionFromPreviousAcceptedCycle =
      false

    var quantPulseCount =
      0

    var ropePulseCount =
      0

    var fusionPulseCount =
      0

    // ========================================================================
    // Main simulation
    // ========================================================================
    while(
      outputRowsSeen <
      expectedOutputRows &&
      physicalCycle <
      100000
    ) {

      val stallNow =
        stallFn(physicalCycle)

      // ----------------------------------------------------------------------
      // Input scheduling
      // ----------------------------------------------------------------------
      if(
        logicalCycle <
        totalComputeCycles
      ) {

        val globalTile =
          logicalCycle / dim

        val m =
          logicalCycle % dim

        driveInputRow(
          globalTile,
          m
        )

        dut.io.input_valid
          .poke(true.B)

        dut.io.input_tile_start
          .poke((m == 0).B)

      } else {

        driveInputZero()

        dut.io.input_valid
          .poke(false.B)

        dut.io.input_tile_start
          .poke(false.B)
      }

      // ----------------------------------------------------------------------
      // Rolling weight schedule
      //
      // W1: logical cycle 15..30
      // W2: logical cycle 31..46
      // ...
      // ----------------------------------------------------------------------
      val relativeWeightCycle =
        logicalCycle -
        (dim - 1)

      if(
        relativeWeightCycle >= 0
      ) {

        val nextGlobalTile =
          1 +
          relativeWeightCycle / dim

        val n =
          relativeWeightCycle % dim

        if(
          nextGlobalTile <
          totalTiles
        ) {

          driveWeightRow(
            nextGlobalTile,
            n
          )

          dut.io.weight_valid
            .poke(true.B)

        } else {

          driveWeightZero()

          dut.io.weight_valid
            .poke(false.B)
        }

      } else {

        driveWeightZero()

        dut.io.weight_valid
          .poke(false.B)
      }

      dut.io.stall
        .poke(stallNow.B)

      // ----------------------------------------------------------------------
      // Observe only TPU_top public outputs
      // ----------------------------------------------------------------------
      val valid =
        dut.io.out_valid(0)
          .peek()
          .litToBoolean

      val quantNow =
        dut.io.out_meta
          .quant_param_update
          .peek()
          .litToBoolean

      val ropeNow =
        dut.io.out_meta
          .row_change_update
          .peek()
          .litToBoolean

      val fusionNow =
        dut.io.fusion_req
          .peek()
          .litToBoolean

      // ======================================================================
      // Stall contract
      // ======================================================================
      if(stallNow) {

        for(n <- 0 until dim) {
          dut.io.out_valid(n)
            .expect(false.B)
        }

        dut.io.out_meta
          .quant_param_update
          .expect(false.B)

        dut.io.out_meta
          .row_change_update
          .expect(false.B)

        dut.io.fusion_req
          .expect(false.B)

      } else {

        // ====================================================================
        // Valid output row
        // ====================================================================
        if(valid) {

          val outputTile =
            outputRowsSeen / dim

          val row =
            outputRowsSeen % dim

          // --------------------------------------------------------------
          // All lanes must be valid and data-correct.
          // --------------------------------------------------------------
          for(n <- 0 until dim) {

            dut.io.out_valid(n)
              .expect(true.B)

            dut.io.out_accum(n)
              .expect(
                golden(
                  outputTile
                )(row)(n).S(32.W)
              )
          }

          // --------------------------------------------------------------
          // RoPE
          //
          // EVERY completed flat output row changes logical position
          // context, therefore update must be asserted on every valid row.
          // --------------------------------------------------------------
          assert(
            ropeNow,
            s"row_change_update missing: physical=$physicalCycle tile=$outputTile row=$row"
          )

          ropePulseCount += 1

          // --------------------------------------------------------------
          // Quant
          //
          // Independent policy.
          // Only row0 of the selected output tile carries the update pulse.
          // --------------------------------------------------------------
          val expectedQuantNow =
            row == 0 &&
            expectedQuant(outputTile)

          assert(
            quantNow ==
            expectedQuantNow,
            s"quant_param_update mismatch: physical=$physicalCycle tile=$outputTile row=$row expected=$expectedQuantNow actual=$quantNow"
          )

          if(quantNow) {
            quantPulseCount += 1
          }

          // --------------------------------------------------------------
          // Fusion
          //
          // fusion_req must have appeared exactly one ACCEPTED cycle before
          // the corresponding tile row0.
          // --------------------------------------------------------------
          if(row == 0) {

            val expectedFusionNow =
              expectedFusion(outputTile)

            assert(
              fusionFromPreviousAcceptedCycle ==
              expectedFusionNow,
              s"fusion_req timing mismatch: tile=$outputTile expectedPrevious=$expectedFusionNow actualPrevious=$fusionFromPreviousAcceptedCycle"
            )
          }

          outputRowsSeen += 1
          streamStarted = true

        } else {

          // No row => no data-aligned metadata.
          assert(
            !quantNow,
            s"quant_param_update asserted without output valid at physical=$physicalCycle"
          )

          assert(
            !ropeNow,
            s"row_change_update asserted without output valid at physical=$physicalCycle"
          )

          if(
            requireNoBubble &&
            streamStarted &&
            outputRowsSeen <
            expectedOutputRows
          ) {

            fail(
              s"Unexpected output bubble: physical=$physicalCycle logical=$logicalCycle rowsSeen=$outputRowsSeen"
            )
          }
        }

        if(fusionNow) {
          fusionPulseCount += 1
        }

        fusionFromPreviousAcceptedCycle =
          fusionNow
      }

      dut.io.fatal_alert
        .expect(false.B)

      dut.clock.step()

      if(!stallNow) {
        logicalCycle += 1
      }

      physicalCycle += 1
    }

    // ========================================================================
    // Final checks
    // ========================================================================
    assert(
      outputRowsSeen ==
      expectedOutputRows,
      s"Expected $expectedOutputRows output rows, got $outputRowsSeen"
    )

    // RoPE = every output row
    assert(
      ropePulseCount ==
      expectedOutputRows,
      s"Expected $expectedOutputRows row_change_update pulses, got $ropePulseCount"
    )

    val expectedQuantCount =
      if(quantParamTilePeriod == 0) {
        0
      } else {
        (0 until numOutputs)
          .count(
            _ % quantParamTilePeriod == 0
          )
      }

    assert(
      quantPulseCount ==
      expectedQuantCount,
      s"Expected $expectedQuantCount quant_param_update pulses, got $quantPulseCount"
    )

    val expectedFusionCount =
      (0 until numOutputs)
        .count(expectedFusion)

    assert(
      fusionPulseCount ==
      expectedFusionCount,
      s"Expected $expectedFusionCount fusion_req pulses, got $fusionPulseCount"
    )
  }

  // ==========================================================================
  // 1. K=1 zero-bubble + Quant/RoPE complete separation
  // ==========================================================================
  it should
    "stream continuously while keeping Quant and RoPE metadata fully independent" in {

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

        // Quant only once per 4 output tiles.
        // RoPE still updates on EVERY output row.
        quantParamTilePeriod = 4,

        seed = 0x10012002L,
        stallFn = _ => false,
        requireNoBubble = true
      )
    }
  }

  // ==========================================================================
  // 2. Multi-K accumulation
  // ==========================================================================
  it should
    "preserve independent metadata under multi-K accumulation" in {

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
        numKTiles = 3,
        quantParamTilePeriod = 2,
        seed = 0x30042006L,
        stallFn = _ => false,
        requireNoBubble = false
      )
    }
  }

  // ==========================================================================
  // 3. Quant automatic update disabled + stalls
  //
  // This is a useful proof that RoPE has no dependency on Quant scheduling.
  // ==========================================================================
  it should
    "keep RoPE row updates alive when Quant auto-update is disabled and stalls occur" in {

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

        // Quant completely disabled.
        quantParamTilePeriod = 0,

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