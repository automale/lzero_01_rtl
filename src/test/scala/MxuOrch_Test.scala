package npu.core

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random


class MxuOrchUnitTest
    extends AnyFlatSpec
    with ChiselScalatestTester {

  behavior of "MxuOrchUnit"

  type TileSet = Array[Array[Array[Int]]]


  // ==========================================================================
  // Utility: Golden GEMM
  //
  // A[t][m][k]
  // W[t][n][k]
  //
  // Y[t][m][n] = sum_k A[t][m][k] * W[t][n][k]
  // ==========================================================================
  private def makeGolden(
    aTiles: TileSet,
    wTiles: TileSet
  ): TileSet = {

    val numTiles = aTiles.length
    val dim      = aTiles(0).length

    val expected =
      Array.ofDim[Int](
        numTiles,
        dim,
        dim
      )

    for {
      t <- 0 until numTiles
      m <- 0 until dim
      n <- 0 until dim
    } {

      var sum = 0

      for (k <- 0 until dim) {
        sum +=
          aTiles(t)(m)(k) *
          wTiles(t)(n)(k)
      }

      expected(t)(m)(n) = sum
    }

    expected
  }


  // ==========================================================================
  // Utility: Random INT8 tile generator
  // ==========================================================================
  private def randomTiles(
    numTiles: Int,
    dim: Int,
    rng: Random,
    minValue: Int,
    maxValue: Int
  ): TileSet = {

    require(minValue >= -128)
    require(maxValue <= 127)
    require(minValue <= maxValue)

    Array.tabulate(
      numTiles,
      dim,
      dim
    ) { (_, _, _) =>

      minValue +
        rng.nextInt(
          maxValue - minValue + 1
        )
    }
  }


  // ==========================================================================
  // Common Test Runner
  //
  // stallAtPhysicalCycle:
  //
  //   physical cycle -> stall?
  //
  // Logical compute time advances ONLY when stall == false.
  //
  // Therefore during stall:
  //
  //   - same A beat is held
  //   - same W beat is held
  //   - input skew freezes
  //   - shadow weight load freezes
  //   - weight_update wave freezes
  //   - MXU pipeline freezes
  // ==========================================================================
  private def runCase(
    dut: MxuOrchUnit,
    aTiles: TileSet,
    wTiles: TileSet,
    stallAtPhysicalCycle: Int => Boolean = _ => false
  ): Unit = {

    val numTiles = aTiles.length
    val dim      = aTiles(0).length

    require(numTiles > 0)
    require(wTiles.length == numTiles)

    val expected =
      makeGolden(
        aTiles,
        wTiles
      )


    // ========================================================================
    // Drive helpers
    // ========================================================================

    def driveInputZero(): Unit = {
      for (k <- 0 until dim) {
        dut.io.in_input(k)
          .poke(0.S(8.W))
      }
    }


    def driveWeightZero(): Unit = {
      for (k <- 0 until dim) {
        dut.io.in_weight(k)
          .poke(0.S(8.W))
      }
    }


    def driveInputRow(
      tile: Int,
      m: Int
    ): Unit = {

      for (k <- 0 until dim) {

        dut.io.in_input(k)
          .poke(
            aTiles(tile)(m)(k).S(8.W)
          )
      }
    }


    def driveWeightRow(
      tile: Int,
      n: Int
    ): Unit = {

      for (k <- 0 until dim) {

        dut.io.in_weight(k)
          .poke(
            wTiles(tile)(n)(k).S(8.W)
          )
      }
    }


    // ========================================================================
    // Initial state
    // ========================================================================

    dut.io.input_valid
      .poke(false.B)

    dut.io.input_tile_start
      .poke(false.B)

    dut.io.weight_valid
      .poke(false.B)

    dut.io.clear_W
      .poke(true.B)

    dut.io.stall
      .poke(false.B)

    driveInputZero()
    driveWeightZero()

    dut.clock.step(1)

    dut.io.clear_W
      .poke(false.B)


    // physicalCycle counts actual clock edges,
    // including stall cycles.
    var physicalCycle = 0


    // ========================================================================
    // Phase 1
    //
    // Preload W0.
    //
    // Stall is also allowed here so that shadow-weight loading itself is
    // tested for correct freeze semantics.
    // ========================================================================

    var preloadN = 0

    while (preloadN < dim) {

      val stallNow =
        stallAtPhysicalCycle(
          physicalCycle
        )

      driveInputZero()

      dut.io.input_valid
        .poke(false.B)

      dut.io.input_tile_start
        .poke(false.B)

      driveWeightRow(
        tile = 0,
        n    = preloadN
      )

      dut.io.weight_valid
        .poke(true.B)

      dut.io.stall
        .poke(stallNow.B)


      // Save output state so stall can also be checked
      // as a true pipeline freeze.
      val beforeStall =
        if (stallNow) {
          (0 until dim).map { n =>
            dut.io.out_MAC(n)
              .peek()
              .litValue
          }
        } else {
          Seq.empty[BigInt]
        }


      dut.clock.step(1)


      if (stallNow) {

        val afterStall =
          (0 until dim).map { n =>
            dut.io.out_MAC(n)
              .peek()
              .litValue
          }

        assert(
          afterStall == beforeStall,
          s"MXU output changed during stall at physical cycle $physicalCycle"
        )

      } else {

        // Weight beat was actually consumed.
        preloadN += 1
      }


      dut.io.fatal_alert
        .expect(false.B)

      physicalCycle += 1

      assert(
        physicalCycle < 10000,
        "Test appears to be stuck during initial weight preload."
      )
    }


    dut.io.weight_valid
      .poke(false.B)


    // ========================================================================
    // Phase 2
    //
    // Continuous A tiles:
    //
    // logical:
    //
    //   A0 :  0 .. 15
    //   A1 : 16 .. 31
    //   A2 : 32 .. 47
    //   ...
    //
    //
    // Rolling next-weight preload:
    //
    //   W1 : 15 .. 30
    //   W2 : 31 .. 46
    //   W3 : 47 .. 62
    //
    // Generic:
    //
    //   W(next) begins at:
    //
    //     tileStart(next) - 1
    //
    // This follows the trailing edge of the current weight_update wave.
    // ========================================================================

    val inputLogicalCycles =
      numTiles * dim

    // Enough time to drain:
    //
    // input skew        : dim - 1
    // MXU horizontal    : dim - 1
    //
    // A little extra margin is harmless.
    val drainLogicalCycles =
      2 * dim

    val totalLogicalCycles =
      inputLogicalCycles +
      drainLogicalCycles


    // logicalCycle advances only when stall == false.
    var logicalCycle = 0


    while (
      logicalCycle < totalLogicalCycles
    ) {

      val stallNow =
        stallAtPhysicalCycle(
          physicalCycle
        )


      // ======================================================================
      // Input stream
      // ======================================================================

      if (
        logicalCycle < inputLogicalCycles
      ) {

        val tile =
          logicalCycle / dim

        val m =
          logicalCycle % dim

        driveInputRow(
          tile = tile,
          m    = m
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


      // ======================================================================
      // Rolling shadow-weight preload
      //
      // logicalCycle = dim-1:
      //
      //   begin W1 column 0
      //
      // logicalCycle = 2*dim-1:
      //
      //   begin W2 column 0
      //
      // etc.
      // ======================================================================

      val relativeWeightCycle =
        logicalCycle -
        (dim - 1)


      if (
        relativeWeightCycle >= 0
      ) {

        val weightTile =
          1 +
          relativeWeightCycle / dim

        val n =
          relativeWeightCycle % dim


        if (
          weightTile < numTiles
        ) {

          driveWeightRow(
            tile = weightTile,
            n    = n
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


      // ======================================================================
      // Stall
      // ======================================================================

      dut.io.stall
        .poke(stallNow.B)


      val beforeStall =
        if (stallNow) {

          (0 until dim).map { n =>

            dut.io.out_MAC(n)
              .peek()
              .litValue
          }

        } else {

          Seq.empty[BigInt]
        }


      // ======================================================================
      // Clock
      // ======================================================================

      dut.clock.step(1)


      // ======================================================================
      // Stall verification
      // ======================================================================

      if (stallNow) {

        val afterStall =
          (0 until dim).map { n =>

            dut.io.out_MAC(n)
              .peek()
              .litValue
          }

        assert(
          afterStall == beforeStall,
          s"""
             |MXU output changed while stall was asserted.
             |
             |physicalCycle = $physicalCycle
             |logicalCycle  = $logicalCycle
             |""".stripMargin
        )


      } else {

        // ====================================================================
        // Golden scoreboard
        //
        // Raw MXU timing:
        //
        // outputCycle =
        //
        //   tile * dim
        //   + (dim - 1)
        //   + m
        //   + n
        //
        //
        // For each physical output column n:
        //
        // streamIndex =
        //
        //   logicalCycle
        //   - ((dim - 1) + n)
        //
        // streamIndex maps directly to:
        //
        //   tile = streamIndex / dim
        //   row  = streamIndex % dim
        // ====================================================================

        for (
          n <- 0 until dim
        ) {

          val streamIndex =
            logicalCycle -
            ((dim - 1) + n)


          if (
            streamIndex >= 0 &&
            streamIndex <
              numTiles * dim
          ) {

            val tile =
              streamIndex / dim

            val m =
              streamIndex % dim

            val exp =
              expected(tile)(m)(n)


            dut.io.out_MAC(n)
              .expect(
                exp.S(32.W),
                s"""
                   |GEMM mismatch
                   |
                   |physicalCycle = $physicalCycle
                   |logicalCycle  = $logicalCycle
                   |
                   |tile = $tile
                   |m    = $m
                   |n    = $n
                   |
                   |expected = $exp
                   |""".stripMargin
              )
          }
        }


        // Pipeline time advances only here.
        logicalCycle += 1
      }


      dut.io.fatal_alert
        .expect(false.B)


      physicalCycle += 1


      assert(
        physicalCycle < 100000,
        "Test exceeded maximum physical cycle count."
      )
    }


    // ========================================================================
    // Finish
    // ========================================================================

    dut.io.input_valid
      .poke(false.B)

    dut.io.input_tile_start
      .poke(false.B)

    dut.io.weight_valid
      .poke(false.B)

    dut.io.stall
      .poke(false.B)

    driveInputZero()
    driveWeightZero()
  }


  // ==========================================================================
  // TEST 1
  //
  // Existing baseline:
  //
  // 3 continuous 16x16 tiles
  // small random signed values
  // ==========================================================================
  it should "compute three consecutive signed INT8 GEMM tiles" in {

    val dim      = 16
    val numTiles = 3

    val rng =
      new Random(
        0x12345678L
      )

    val aTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -7,
        7
      )

    val wTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -7,
        7
      )


    test(
      new MxuOrchUnit(
        numRows = dim,
        numCols = dim,
        inBits  = 8,
        accBits = 32
      )
    ) { dut =>

      runCase(
        dut,
        aTiles,
        wTiles
      )
    }
  }


  // ==========================================================================
  // TEST 2
  //
  // Signed INT8 corner cases
  //
  // Explicitly exercises:
  //
  //   -128
  //   -127
  //   -1
  //    0
  //    1
  //    126
  //    127
  //
  // This catches:
  //
  //   UInt/SInt mistakes
  //   sign extension mistakes
  //   INT8 multiply interpretation mistakes
  // ==========================================================================
  it should "correctly handle signed INT8 corner values" in {

    val dim      = 16
    val numTiles = 3

    val cornerValues =
      Array(
        -128,
        -127,
        -1,
        0,
        1,
        126,
        127
      )


    val aTiles =
      Array.tabulate(
        numTiles,
        dim,
        dim
      ) { (t, m, k) =>

        cornerValues(
          (
            t * 5 +
            m * 3 +
            k
          ) % cornerValues.length
        )
      }


    val wTiles =
      Array.tabulate(
        numTiles,
        dim,
        dim
      ) { (t, n, k) =>

        cornerValues(
          (
            t * 2 +
            n * 5 +
            k * 3 +
            1
          ) % cornerValues.length
        )
      }


    test(
      new MxuOrchUnit(
        numRows = dim,
        numCols = dim,
        inBits  = 8,
        accBits = 32
      )
    ) { dut =>

      runCase(
        dut,
        aTiles,
        wTiles
      )
    }
  }


  // ==========================================================================
  // TEST 3
  //
  // Global stall test
  //
  // Inserts deterministic stalls during:
  //
  //   - initial W0 preload
  //   - input skew
  //   - weight_update propagation
  //   - rolling shadow preload
  //   - MXU computation
  //
  // Includes occasional 2-cycle consecutive stalls.
  // ==========================================================================
  it should "preserve GEMM correctness across pipeline stalls" in {

    val dim      = 16
    val numTiles = 3

    val rng =
      new Random(
        0x55aa7711L
      )

    val aTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -32,
        31
      )

    val wTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -32,
        31
      )


    // Deterministic / reproducible stall pattern.
    //
    // Examples:
    //   physical 5,6 -> consecutive stall
    //   physical 24,25 -> consecutive stall
    //   etc.
    def stallPattern(
      physicalCycle: Int
    ): Boolean = {

      val pairStall =
        (physicalCycle % 19 == 5) ||
        (physicalCycle % 19 == 6)

      val singleStall =
        physicalCycle % 23 == 11

      pairStall ||
      singleStall
    }


    test(
      new MxuOrchUnit(
        numRows = dim,
        numCols = dim,
        inBits  = 8,
        accBits = 32
      )
    ) { dut =>

      runCase(
        dut,
        aTiles,
        wTiles,
        stallPattern
      )
    }
  }


  // ==========================================================================
  // TEST 4
  //
  // Long continuous stream
  //
  // 8 independent A/W tiles.
  //
  // This repeatedly exercises:
  //
  //   weightLoadIdx:
  //       0 -> 15 -> 0 -> ...
  //
  // and repeated:
  //
  //   shadow refill
  //   diagonal weight update
  //   tile boundary transition
  //
  // Full INT8 range is used.
  // ==========================================================================
  it should "compute eight consecutive full-range INT8 tiles without bubbles" in {

    val dim      = 16
    val numTiles = 8

    val rng =
      new Random(
        0x7eadbeefL
      )

    val aTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -128,
        127
      )

    val wTiles =
      randomTiles(
        numTiles,
        dim,
        rng,
        -128,
        127
      )


    test(
      new MxuOrchUnit(
        numRows = dim,
        numCols = dim,
        inBits  = 8,
        accBits = 32
      )
    ) { dut =>

      runCase(
        dut,
        aTiles,
        wTiles
      )
    }
  }
}