package npu.core

import chisel3._
import chisel3.util._

// ============================================================================
// [1] Weight-Stationary MAC PE
//
// Input X      : INT8
// Weight       : INT8
// Product      : INT16
// Partial Sum  : INT32
//
// X and weight_update propagate horizontally.
// Partial sum propagates vertically.
// ============================================================================
class MacUnit(
  val inBits: Int = 8,
  val accBits: Int = 32
) extends Module {

  val io = IO(new Bundle {
    val in_x = Input(SInt(inBits.W))
    val in_y = Input(SInt(accBits.W))

    val in_valid = Input(Bool())

    // Next tile weight, continuously supplied by shadow-weight storage
    val in_shadow_w = Input(SInt(inBits.W))

    // Travels with the first input element of a new tile
    val weight_update = Input(Bool())

    val clear_w = Input(Bool())
    val stall   = Input(Bool())

    val out_in            = Output(SInt(inBits.W))
    val out_mac           = Output(SInt(accBits.W))
    val out_weight_update = Output(Bool())
    val out_valid         = Output(Bool())

    val mac_alert = Output(Bool())
  })

  val run = !io.stall

  // --------------------------------------------------------------------------
  // valid signal propagation
  // --------------------------------------------------------------------------
  val outValidReg = RegInit(false.B)
  
  when(run) {
    outValidReg :=io.in_valid
  }
  
  io.out_valid := outValidReg

  // --------------------------------------------------------------------------
  // Active stationary weight
  // --------------------------------------------------------------------------
  val active_weight = RegInit(0.S(inBits.W))

  when(run) {
    when(io.clear_w) {
      active_weight := 0.S
    }.elsewhen(io.weight_update) {
      active_weight := io.in_shadow_w
    }
  }

  // --------------------------------------------------------------------------
  // The first input of the new tile arrives in the SAME cycle as
  // weight_update.
  //
  // Therefore that MAC operation must use the shadow weight immediately,
  // rather than waiting one more cycle for active_weight to update.
  // --------------------------------------------------------------------------
  val mac_weight = Mux(
    io.weight_update,
    io.in_shadow_w,
    active_weight
  )

  // INT8 x INT8 -> INT16
  val mul_res = io.in_x * mac_weight

  // Explicit sign extension INT16 -> INT32
  val mul_ext = Wire(SInt(accBits.W))
  mul_ext := mul_res

  // INT32 + INT32 -> INT33 for overflow detection
  val add_full = io.in_y +& mul_ext

  // Keep architectural INT32 result
  val add_result = add_full(accBits - 1, 0).asSInt

  // Signed overflow:
  // if the extra sign bit differs from the retained sign bit.
  val signed_overflow =
    add_full(accBits) =/= add_full(accBits - 1)

  // --------------------------------------------------------------------------
  // Pipeline registers
  // --------------------------------------------------------------------------
  val outInReg =
    RegInit(0.S(inBits.W))

  val outMacReg =
    RegInit(0.S(accBits.W))

  val outWeightUpdateReg =
    RegInit(false.B)

  when(run) {
    outInReg            := io.in_x
    outMacReg           := add_result
    outWeightUpdateReg  := io.weight_update
  }

  io.out_in            := outInReg
  io.out_mac           := outMacReg
  io.out_weight_update := outWeightUpdateReg

  io.mac_alert :=
    run && signed_overflow
}


// ============================================================================
// [2] 16x16 Weight-Stationary MXU
//
// PE coordinate:
//
//   PE[k][n]
//
// k = reduction dimension K
// n = output dimension N
//
// PE[k][n].weight = W[n][k]
//
// X          : left -> right
// PSUM       : top  -> bottom
// weight_update : left -> right
// ============================================================================
class MXU(
  val numRows: Int = 16,
  val numCols: Int = 16,
  val inBits: Int = 8,
  val accBits: Int = 32
) extends Module {

  val io = IO(new Bundle {

    // Already systolically skewed by DataOrchUnit
    val in_X =
      Input(Vec(numRows, SInt(inBits.W)))

    // in_shadow_W(k)(n) = W[n][k]
    val in_shadow_W =
      Input(Vec(
        numRows,
        Vec(numCols, SInt(inBits.W))
      ))

    val in_valid = Input(Vec(numRows, Bool()))

    // One initial update tag per physical MXU row
    val weight_update = Input(Vec(numRows, Bool()))

    val clear_W = Input(Bool())

    val stall = Input(Bool())

    // Raw skewed MXU result
    val out_MAC = Output(Vec(numCols, SInt(accBits.W)))

    val out_valid = Output(Vec(numCols, Bool()))

    val mxu_alert = Output(Bool())
  })

  val macs =
    Seq.fill(numRows, numCols)(
      Module(new MacUnit(inBits, accBits))
    )

  for (r <- 0 until numRows) {
    for (c <- 0 until numCols) {

      val pe = macs(r)(c)

      pe.io.in_shadow_w :=
        io.in_shadow_W(r)(c)

      pe.io.clear_w :=
        io.clear_W

      pe.io.stall :=
        io.stall

      // ----------------------------------------------------------------------
      // X: horizontal propagation
      // ----------------------------------------------------------------------
      if (c == 0) {
        pe.io.in_x := io.in_X(r)
      } else {
        pe.io.in_x :=
          macs(r)(c - 1).io.out_in
      }

      // ----------------------------------------------------------------------
      // valid : horizontal propagation
      // ----------------------------------------------------------------------
      if (c == 0) {
        pe.io.in_valid := io.in_valid(r)
      } else {
        pe.io.in_valid := macs(r)(c - 1).io.out_valid
      }

      // ----------------------------------------------------------------------
      // Weight update: horizontal propagation
      //
      // DataOrch already applies r-cycle skew.
      // MXU adds c-cycle propagation.
      //
      // PE[r][c] update timing = tile_start + r + c
      // ----------------------------------------------------------------------
      if (c == 0) {
        pe.io.weight_update :=
          io.weight_update(r)
      } else {
        pe.io.weight_update :=
          macs(r)(c - 1).io.out_weight_update
      }

      // ----------------------------------------------------------------------
      // PSUM: vertical propagation
      // ----------------------------------------------------------------------
      if (r == 0) {
        pe.io.in_y :=
          0.S(accBits.W)
      } else {
        pe.io.in_y :=
          macs(r - 1)(c).io.out_mac
      }
    }
  }

  for (c <- 0 until numCols) {
    io.out_MAC(c) := macs(numRows - 1)(c).io.out_mac
    io.out_valid(c) := macs(numRows - 1)(c).io.out_valid
  }

  val allAlerts =
    macs.flatten.map(_.io.mac_alert)

  io.mxu_alert :=
    VecInit(allAlerts).asUInt.orR
}