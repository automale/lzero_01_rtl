package npu.top

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.Random


class TPUTopTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  
  behavior of "TPU_top"

    it should "produce Y0 Y1 Y2 consecutively while overlapping pong streaming with next ping accumulation" in {

    val dim =
        16

    val numOutputs =
        3

    val numKTiles =
        2

    val totalTiles =
        numOutputs * numKTiles

    val rng =
        new Random(
        0x55aa1234L
        )


    // ==========================================================================
    // A[y][q][m][k]
    //
    // y : output tile
    // q : K tile
    // m : output row
    // k : reduction dimension
    // ==========================================================================

    val aTiles =
        Array.tabulate(
        numOutputs,
        numKTiles,
        dim,
        dim
        ) { (_, _, _, _) =>

        rng.nextInt(31) - 15
        }


    // ==========================================================================
    // W[y][q][n][k]
    // ==========================================================================

    val wTiles =
        Array.tabulate(
        numOutputs,
        numKTiles,
        dim,
        dim
        ) { (_, _, _, _) =>

        rng.nextInt(31) - 15
        }


    // ==========================================================================
    // Golden
    //
    // Y[y][m][n]
    //
    //   = sum_q sum_k
    //
    //       A[y][q][m][k]
    //       *
    //       W[y][q][n][k]
    // ==========================================================================

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

        var sum =
        0

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


    test(
        new TPU_top(
        numRows = dim,
        numCols = dim,
        inBits = 8,
        accBits = 32
        )
    ) { dut =>


        // ========================================================================
        // Helpers
        // ========================================================================

        def driveInputZero(): Unit = {

        for (
            k <- 0 until dim
        ) {

            dut.io.in_input(k)
            .poke(
                0.S(8.W)
            )
        }
        }


        def driveWeightZero(): Unit = {

        for (
            k <- 0 until dim
        ) {

            dut.io.in_weight(k)
            .poke(
                0.S(8.W)
            )
        }
        }


        def tileToYQ(
        globalTile: Int
        ): (Int, Int) = {

        val y =
            globalTile / numKTiles

        val q =
            globalTile % numKTiles

        (y, q)
        }


        def driveInputRow(
        globalTile: Int,
        m: Int
        ): Unit = {

        val (y, q) =
            tileToYQ(globalTile)

        for (
            k <- 0 until dim
        ) {

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

        for (
            k <- 0 until dim
        ) {

            dut.io.in_weight(k)
            .poke(
                wTiles(y)(q)(n)(k)
                .S(8.W)
            )
        }
        }


        // ========================================================================
        // Initial state
        // ========================================================================

        driveInputZero()
        driveWeightZero()


        dut.io.input_valid
        .poke(false.B)

        dut.io.input_tile_start
        .poke(false.B)

        dut.io.weight_valid
        .poke(false.B)


        dut.io.accum_en
        .poke(false.B)

        dut.io.accum_first
        .poke(false.B)

        dut.io.accum_snapshot
        .poke(false.B)

        dut.io.accum_stream_en
        .poke(false.B)


        dut.io.stall
        .poke(false.B)


        dut.io.clear_W
        .poke(true.B)


        dut.clock.step(1)


        dut.io.clear_W
        .poke(false.B)


        // ========================================================================
        // Initial preload:
        //
        // global W tile 0
        // ========================================================================

        for (
        n <- 0 until dim
        ) {

        driveWeightRow(
            globalTile = 0,
            n = n
        )

        dut.io.weight_valid
            .poke(true.B)

        dut.clock.step(1)
        }


        dut.io.weight_valid
        .poke(false.B)


        // ========================================================================
        // Timing functions
        // ========================================================================
        //
        // MXU column0 result is captured by accumulator:
        //
        //   captureCycle =
        //
        //       globalTile * dim
        //       + dim
        //       + m
        //
        //
        // because MXU output is registered and accumulator captures it
        // on the following edge.
        // ========================================================================


        def snapshotCol0Cycle(
        outputTile: Int
        ): Int = {

        val lastGlobalTile =
            (outputTile + 1) *
            numKTiles -
            1

        // last row m = dim-1
        lastGlobalTile * dim +
        dim +
        (dim - 1)
        }


        // Last column receives snapshot dim-1 cycles later.
        def snapshotAllColumnsDone(
        outputTile: Int
        ): Int = {

        snapshotCol0Cycle(
            outputTile
        ) +
        (dim - 1)
        }


        // Start reading pong on the next cycle.
        def streamStartCycle(
        outputTile: Int
        ): Int = {

        snapshotAllColumnsDone(
            outputTile
        ) + 1
        }


        // ========================================================================
        // Print expected scheduling for debug
        // ========================================================================

        for (
        y <- 0 until numOutputs
        ) {

        println(
            s"""
            |Output Y$y:
            |  snapshot col0 = ${snapshotCol0Cycle(y)}
            |  snapshot done = ${snapshotAllColumnsDone(y)}
            |  stream start  = ${streamStartCycle(y)}
            |""".stripMargin
        )
        }


        // Final cycle must include entire Y2 stream.
        val finalCycle =
        streamStartCycle(
            numOutputs - 1
        ) +
        dim -
        1


        // ========================================================================
        // Main pipeline
        // ========================================================================

        for (
        cycle <- 0 to finalCycle
        ) {


        // ======================================================================
        // [1] INPUT
        //
        // Six tiles are supplied continuously:
        //
        // tile0 :  0..15
        // tile1 : 16..31
        // ...
        // tile5 : 80..95
        // ======================================================================

        if (
            cycle <
            totalTiles * dim
        ) {

            val globalTile =
            cycle / dim

            val m =
            cycle % dim


            driveInputRow(
            globalTile = globalTile,
            m = m
            )


            dut.io.input_valid
            .poke(true.B)


            dut.io.input_tile_start
            .poke(
                (m == 0).B
            )

        } else {

            driveInputZero()


            dut.io.input_valid
            .poke(false.B)


            dut.io.input_tile_start
            .poke(false.B)
        }


        // ======================================================================
        // [2] Rolling weight preload
        //
        // Same single-shadow schedule already validated:
        //
        // W1 starts cycle 15
        // W2 starts cycle 31
        // W3 starts cycle 47
        // ...
        // ======================================================================

        val relativeWeightCycle =
            cycle -
            (dim - 1)


        if (
            relativeWeightCycle >= 0
        ) {

            val globalWeightTile =
            1 +
            relativeWeightCycle / dim

            val n =
            relativeWeightCycle % dim


            if (
            globalWeightTile <
            totalTiles
            ) {

            driveWeightRow(
                globalTile =
                globalWeightTile,

                n = n
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
        // [3] Accumulator input validity
        //
        // Column0 produces a continuous stream from:
        //
        // cycle 16
        //
        // through:
        //
        // totalTiles*16 + 15
        // ======================================================================

        val firstCapture =
            dim

        val lastCapture =
            totalTiles * dim +
            dim -
            1


        val accumActive =
            cycle >= firstCapture &&
            cycle <= lastCapture


        dut.io.accum_en
            .poke(
            accumActive.B
            )


        // ======================================================================
        // [4] accum_first
        //
        // Determine which global K tile is currently being captured.
        //
        // captureStreamIndex:
        //
        //   0..15  -> global tile 0
        //   16..31 -> global tile 1
        //   ...
        //
        // First K tile of each output:
        //
        //   global tile 0
        //   global tile 2
        //   global tile 4
        // ======================================================================

        val captureStreamIndex =
            cycle -
            dim


        val isFirstKTile = {

            if (
            captureStreamIndex >= 0 &&
            captureStreamIndex <
            totalTiles * dim
            ) {

            val globalTile =
                captureStreamIndex /
                dim

            (
                globalTile %
                numKTiles
            ) == 0

            } else {

            false
            }
        }


        dut.io.accum_first
            .poke(
            isFirstKTile.B
            )


        // ======================================================================
        // [5] snapshot
        //
        // Snapshot at:
        //
        // Y0 completion
        // Y1 completion
        // Y2 completion
        // ======================================================================

        val snapshotNow =
            (0 until numOutputs)
            .exists { y =>

                cycle ==
                snapshotCol0Cycle(y)
            }


        dut.io.accum_snapshot
            .poke(
            snapshotNow.B
            )


        // ======================================================================
        // [6] Pong streaming
        //
        // Find whether current cycle belongs to one of:
        //
        // Y0 stream
        // Y1 stream
        // Y2 stream
        // ======================================================================

        var activeOutput =
            -1

        var activeRow =
            -1


        for (
            y <- 0 until numOutputs
        ) {

            val start =
            streamStartCycle(y)

            val end =
            start +
            dim -
            1


            if (
            cycle >= start &&
            cycle <= end
            ) {

            activeOutput =
                y

            activeRow =
                cycle - start
            }
        }


        val streamActive =
            activeOutput >= 0


        dut.io.accum_stream_en
            .poke(
            streamActive.B
            )


        dut.io.stall
            .poke(false.B)


        // ======================================================================
        // [7] CHECK PONG BEFORE CLOCK EDGE
        //
        // out_vec is combinationally read from pong using read_ptr.
        // ======================================================================

        if (
            streamActive
        ) {

            for (
            n <- 0 until dim
            ) {

            dut.io.out_valid(n)
                .expect(
                true.B
                )


            dut.io.out_accum(n)
                .expect(
                golden(
                    activeOutput
                )(
                    activeRow
                )(
                    n
                ).S(32.W),

                s"""
                    |Ping/Pong output mismatch
                    |
                    |cycle  = $cycle
                    |Y      = $activeOutput
                    |row    = $activeRow
                    |column = $n
                    |
                    |expected =
                    |${golden(activeOutput)(activeRow)(n)}
                    |""".stripMargin
                )
            }

        } else {

            for (
            n <- 0 until dim
            ) {

            dut.io.out_valid(n)
                .expect(
                false.B
                )
            }
        }


        dut.io.fatal_alert
            .expect(false.B)

        


        // ======================================================================
        // Clock
        // ======================================================================

        dut.clock.step(1)
        }
        }
    }
    it should "preserve Y0 Y1 Y2 ping-pong correctness across global stalls" in {

  val dim         = 16
  val numOutputs  = 3
  val numKTiles   = 2
  val totalTiles  = numOutputs * numKTiles

  val rng =
    new Random(0x6a5b4c3dL)

  val aTiles =
    Array.tabulate(
      numOutputs,
      numKTiles,
      dim,
      dim
    ) { (_, _, _, _) =>
      rng.nextInt(31) - 15
    }

  val wTiles =
    Array.tabulate(
      numOutputs,
      numKTiles,
      dim,
      dim
    ) { (_, _, _, _) =>
      rng.nextInt(31) - 15
    }

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

    golden(y)(m)(n) = sum
  }

  test(
    new TPU_top(
      numRows = dim,
      numCols = dim,
      inBits  = 8,
      accBits = 32
    )
  ) { dut =>

    def driveInputZero(): Unit = {
      for (k <- 0 until dim) {
        dut.io.in_input(k).poke(0.S(8.W))
      }
    }

    def driveWeightZero(): Unit = {
      for (k <- 0 until dim) {
        dut.io.in_weight(k).poke(0.S(8.W))
      }
    }

    def tileToYQ(globalTile: Int): (Int, Int) = {
      val y = globalTile / numKTiles
      val q = globalTile % numKTiles
      (y, q)
    }

    def driveInputRow(
      globalTile: Int,
      m: Int
    ): Unit = {

      val (y, q) =
        tileToYQ(globalTile)

      for (k <- 0 until dim) {
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

      for (k <- 0 until dim) {
        dut.io.in_weight(k)
          .poke(
            wTiles(y)(q)(n)(k)
              .S(8.W)
          )
      }
    }

    def snapshotCol0Cycle(
      outputTile: Int
    ): Int = {

      val lastGlobalTile =
        (outputTile + 1) *
        numKTiles -
        1

      lastGlobalTile * dim +
      dim +
      (dim - 1)
    }

    def snapshotAllColumnsDone(
      outputTile: Int
    ): Int = {

      snapshotCol0Cycle(outputTile) +
      (dim - 1)
    }

    def streamStartCycle(
      outputTile: Int
    ): Int = {

      snapshotAllColumnsDone(outputTile) + 1
    }

    // ------------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------------

    driveInputZero()
    driveWeightZero()

    dut.io.input_valid.poke(false.B)
    dut.io.input_tile_start.poke(false.B)
    dut.io.weight_valid.poke(false.B)

    dut.io.accum_en.poke(false.B)
    dut.io.accum_first.poke(false.B)
    dut.io.accum_snapshot.poke(false.B)
    dut.io.accum_stream_en.poke(false.B)

    dut.io.stall.poke(false.B)
    dut.io.clear_W.poke(true.B)

    dut.clock.step(1)

    dut.io.clear_W.poke(false.B)

    // ========================================================================
    // Initial W0 preload with stall support
    // ========================================================================

    var physicalCycle = 0
    var preloadN      = 0

    def stallPattern(p: Int): Boolean = {

      // repeated 2-cycle stalls
      val pair =
        (p % 23 == 7) ||
        (p % 23 == 8)

      // additional single stall
      val single =
        p % 31 == 17

      pair || single
    }

    while (preloadN < dim) {

      val stallNow =
        stallPattern(physicalCycle)

      driveInputZero()

      dut.io.input_valid.poke(false.B)
      dut.io.input_tile_start.poke(false.B)

      driveWeightRow(
        globalTile = 0,
        n = preloadN
      )

      dut.io.weight_valid.poke(true.B)

      dut.io.accum_en.poke(false.B)
      dut.io.accum_first.poke(false.B)
      dut.io.accum_snapshot.poke(false.B)
      dut.io.accum_stream_en.poke(false.B)

      dut.io.stall.poke(stallNow.B)

      val beforeOut =
        (0 until dim).map { n =>
          dut.io.out_accum(n)
            .peek()
            .litValue
        }

      val beforeValid =
        (0 until dim).map { n =>
          dut.io.out_valid(n)
            .peek()
            .litToBoolean
        }

      dut.clock.step(1)

      if (stallNow) {

        val afterOut =
          (0 until dim).map { n =>
            dut.io.out_accum(n)
              .peek()
              .litValue
          }

        val afterValid =
          (0 until dim).map { n =>
            dut.io.out_valid(n)
              .peek()
              .litToBoolean
          }

        assert(
          afterOut == beforeOut,
          s"Output changed during preload stall at physicalCycle=$physicalCycle"
        )

        assert(
          afterValid == beforeValid,
          s"out_valid changed during preload stall at physicalCycle=$physicalCycle"
        )

      } else {

        preloadN += 1
      }

      dut.io.fatal_alert.expect(false.B)

      physicalCycle += 1
    }

    dut.io.weight_valid.poke(false.B)

    // ========================================================================
    // Logical timing
    // ========================================================================

    val finalLogicalCycle =
      streamStartCycle(
        numOutputs - 1
      ) +
      dim -
      1

    var logicalCycle = 0

    while (
      logicalCycle <= finalLogicalCycle
    ) {

      val stallNow =
        stallPattern(physicalCycle)

      // ======================================================================
      // INPUT
      // ======================================================================

      if (
        logicalCycle <
        totalTiles * dim
      ) {

        val globalTile =
          logicalCycle / dim

        val m =
          logicalCycle % dim

        driveInputRow(
          globalTile = globalTile,
          m = m
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
      // WEIGHT PRELOAD
      // ======================================================================

      val relativeWeightCycle =
        logicalCycle -
        (dim - 1)

      if (
        relativeWeightCycle >= 0
      ) {

        val globalWeightTile =
          1 +
          relativeWeightCycle / dim

        val n =
          relativeWeightCycle % dim

        if (
          globalWeightTile <
          totalTiles
        ) {

          driveWeightRow(
            globalTile =
              globalWeightTile,
            n = n
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
      // ACCUM CONTROL
      // ======================================================================

      val firstCapture =
        dim

      val lastCapture =
        totalTiles * dim +
        dim -
        1

      val accumActive =
        logicalCycle >= firstCapture &&
        logicalCycle <= lastCapture

      dut.io.accum_en
        .poke(accumActive.B)

      val captureStreamIndex =
        logicalCycle -
        dim

      val isFirstKTile =
        if (
          captureStreamIndex >= 0 &&
          captureStreamIndex <
            totalTiles * dim
        ) {

          val globalTile =
            captureStreamIndex / dim

          (
            globalTile %
            numKTiles
          ) == 0

        } else {

          false
        }

      dut.io.accum_first
        .poke(isFirstKTile.B)

      val snapshotNow =
        (0 until numOutputs)
          .exists { y =>
            logicalCycle ==
            snapshotCol0Cycle(y)
          }

      dut.io.accum_snapshot
        .poke(snapshotNow.B)

      // ======================================================================
      // OUTPUT STREAM
      // ======================================================================

      var activeOutput = -1
      var activeRow    = -1

      for (
        y <- 0 until numOutputs
      ) {

        val start =
          streamStartCycle(y)

        val end =
          start +
          dim -
          1

        if (
          logicalCycle >= start &&
          logicalCycle <= end
        ) {

          activeOutput = y
          activeRow =
            logicalCycle - start
        }
      }

      val streamActive =
        activeOutput >= 0

      dut.io.accum_stream_en
        .poke(streamActive.B)

      // ======================================================================
      // Apply global stall
      // ======================================================================

      dut.io.stall
        .poke(stallNow.B)

      // Save externally visible state before edge.
      val beforeOut =
        (0 until dim).map { n =>
          dut.io.out_accum(n)
            .peek()
            .litValue
        }

      val beforeValid =
        (0 until dim).map { n =>
          dut.io.out_valid(n)
            .peek()
            .litToBoolean
        }

      // ======================================================================
      // Check current logical output BEFORE stepping
      // ======================================================================

      if (
        streamActive &&
        !stallNow
      ) {

        for (
          n <- 0 until dim
        ) {

          dut.io.out_valid(n)
            .expect(true.B)

          dut.io.out_accum(n)
            .expect(
              golden(
                activeOutput
              )(
                activeRow
              )(
                n
              ).S(32.W),

              s"""
                 |TPU stall test mismatch
                 |
                 |physicalCycle = $physicalCycle
                 |logicalCycle  = $logicalCycle
                 |
                 |Y      = $activeOutput
                 |row    = $activeRow
                 |column = $n
                 |
                 |expected =
                 |${golden(activeOutput)(activeRow)(n)}
                 |""".stripMargin
            )
        }

      } else if (!stallNow) {

        for (
          n <- 0 until dim
        ) {
          dut.io.out_valid(n)
            .expect(false.B)
        }
      }

      dut.io.fatal_alert
        .expect(false.B)

      // ======================================================================
      // Clock
      // ======================================================================

      dut.clock.step(1)

      // ======================================================================
      // Stall verification
      //
      // Entire externally-visible TPU state must remain frozen.
      // ======================================================================

      if (stallNow) {

        val afterOut =
          (0 until dim).map { n =>
            dut.io.out_accum(n)
              .peek()
              .litValue
          }

        val afterValid =
          (0 until dim).map { n =>
            dut.io.out_valid(n)
              .peek()
              .litToBoolean
          }

        assert(
          afterOut == beforeOut,
          s"""
             |out_accum changed during stall
             |
             |physicalCycle = $physicalCycle
             |logicalCycle  = $logicalCycle
             |""".stripMargin
        )

        assert(
          afterValid == beforeValid,
          s"""
             |out_valid changed during stall
             |
             |physicalCycle = $physicalCycle
             |logicalCycle  = $logicalCycle
             |""".stripMargin
        )

      } else {

        // Only non-stalled clocks advance architectural time.
        logicalCycle += 1
      }

      physicalCycle += 1

      assert(
        physicalCycle < 100000,
        "TPU stall test exceeded maximum cycle count."
      )
    }
  }
}
}