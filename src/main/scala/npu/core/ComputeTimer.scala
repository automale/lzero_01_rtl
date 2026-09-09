package npu.core

import chisel3._
import chisel3.util._

class TPUStreamMeta extends Bundle {
  val row_change_update = Bool()
}

class TPUTileCtrl extends Bundle {
  val row_change_update = Bool()
  val fusion_req        = Bool()
}

class ComputeTimer(
  val tileSize: Int = 16
) extends Module {

  require(tileSize > 0)

  val io = IO(new Bundle {
    val row0_valid = Input(Bool())
    val stall      = Input(Bool())

    val intermNum = Input(UInt(32.W))
    val outColNum = Input(UInt(32.W))

    val accum_first      = Output(Bool())
    val output_tile_done = Output(Bool())

    val row_change_update = Output(Bool())
    val fusion_req        = Output(Bool())
  })

  val run  = !io.stall
  val fire = io.row0_valid && run

  val intermSafe = Mux(io.intermNum === 0.U, 1.U, io.intermNum)
  val outColSafe = Mux(io.outColNum === 0.U, 1.U, io.outColNum)

  private val rowBits = math.max(1, log2Ceil(tileSize))

  val outputRowCounter = RegInit(0.U(rowBits.W))
  val kTileCounter     = RegInit(0.U(32.W))
  val nTileCounter     = RegInit(0.U(32.W))

  val fusionTileCountdown = RegInit(15.U(32.W))

  val lastRow   = outputRowCounter === (tileSize - 1).U
  val lastKTile = kTileCounter === (intermSafe - 1.U)
  val lastNTile = nTileCounter === (outColSafe - 1.U)

  io.accum_first := kTileCounter === 0.U

  val outputRowDone =
    fire && lastKTile

  val outputTileDone =
    outputRowDone && lastRow

  io.output_tile_done := outputTileDone

  // Current output tile is the first N tile of a new 16-row M group.
  io.row_change_update :=
    outputTileDone && (nTileCounter === 0.U)

  io.fusion_req :=
    outputTileDone && (fusionTileCountdown === 0.U)

  when(fire) {
    when(lastRow) {
      outputRowCounter := 0.U

      when(lastKTile) {
        kTileCounter := 0.U
      }.otherwise {
        kTileCounter := kTileCounter + 1.U
      }
    }.otherwise {
      outputRowCounter := outputRowCounter + 1.U
    }
  }

  when(outputTileDone) {
    when(lastNTile) {
      nTileCounter := 0.U
    }.otherwise {
      nTileCounter := nTileCounter + 1.U
    }

    when(fusionTileCountdown === 0.U) {
      fusionTileCountdown := 31.U
    }.otherwise {
      fusionTileCountdown := fusionTileCountdown - 1.U
    }
  }
}
