package npu.core

import chisel3._
import chisel3.util._
import npu.utils.Universal_Wide_LUT

// Norm Mode Definition
object NormMode {
  val BYPASS    = 0.U(2.W)
  val RMSNORM   = 1.U(2.W)
  val LAYERNORM = 2.U(2.W)
  val SOFTMAX   = 3.U(2.W)
}

// ====================================================================
// [1] UniversalNormLine (Ping-Pong Accumulation & Dynamic Pipeline)
// ====================================================================
class UniversalNormLine(
  val writeBits: Int = 128, 
  val inBits: Int = 8,
  val outBits: Int = 8,
  val vectorSize: Int = 4096,
  val indexBits: Int = 8,
  val dataBits: Int = 16
) extends Module {
  
  val shiftAmount = log2Ceil(vectorSize)
  val sumBits     = inBits + shiftAmount
  val sqSumBits   = (inBits * 2) + shiftAmount
  val accBits     = sqSumBits.max(32)

  val io = IO(new Bundle {
    val phase1_in              = Input(UInt(inBits.W))
    val phase1_valid           = Input(Bool())
    val is_last_tile           = Input(Bool())
    val phase1_param_update_in = Input(Bool())
    
    val phase1_out             = Output(UInt(inBits.W))
    val phase1_out_valid       = Output(Bool())
    val phase1_param_update_out= Output(Bool())

    val phase2_in              = Input(UInt(inBits.W))
    val phase2_valid           = Input(Bool())
    val phase2_param_update_in = Input(Bool()) 
    
    val phase2_out             = Output(UInt(outBits.W))
    val phase2_out_valid       = Output(Bool())
    val phase2_param_update_out= Output(Bool())

    val mode_sel               = Input(UInt(2.W))
    val lane_reduce_en         = Input(Bool())
    val clr_acc                = Input(Bool())
    val stall                  = Input(Bool())
    val phase2_req             = Output(Bool()) 

    val to_sum_tree_sum1       = Output(UInt(accBits.W))
    val to_sum_tree_sum2       = Output(UInt(accBits.W))
    val local_max              = Output(UInt(inBits.W))
    
    val global_sum1            = Input(UInt(accBits.W))
    val global_sum2            = Input(UInt(accBits.W))
    val global_max             = Input(UInt(inBits.W))

    val exp_wr_en              = Input(Bool())
    val exp_wr_addr            = Input(UInt(log2Ceil((1 << indexBits) / (writeBits / dataBits)).W))
    val exp_wr_data            = Input(Vec(writeBits / dataBits, UInt(dataBits.W)))
    val scale_wr_en            = Input(Bool())
    val scale_wr_addr          = Input(UInt(log2Ceil((1 << indexBits) / (writeBits / dataBits)).W))
    val scale_wr_data          = Input(Vec(writeBits / dataBits, UInt(dataBits.W)))

    val lut_ready              = Output(Bool())
  })

  val run = !io.stall
  val is_bypass = io.mode_sel === NormMode.BYPASS
  val is_global_softmax = io.lane_reduce_en && (io.mode_sel === NormMode.SOFTMAX)

  // ====================================================================
  // [1-A] BYPASS 0-Cycle Shortcut
  // ====================================================================
  io.phase1_out              := io.phase1_in
  io.phase1_out_valid        := io.phase1_valid
  io.phase1_param_update_out := io.phase1_param_update_in

  val norm_applied_data = WireInit(0.U(outBits.W))
  val phase2_valid_d1   = RegEnable(io.phase2_valid, false.B, run) 
  val phase2_param_d1   = RegEnable(io.phase2_param_update_in, false.B, run)

  io.phase2_out              := Mux(is_bypass, io.phase1_in, norm_applied_data)
  io.phase2_out_valid        := Mux(is_bypass, io.phase1_valid, phase2_valid_d1)
  io.phase2_param_update_out := Mux(is_bypass, io.phase1_param_update_in, phase2_param_d1)

  // ====================================================================
  // [1-B] Phase 1: Ping-Pong Accumulation 
  // ====================================================================
  val acc_ptr = RegInit(0.U(1.W))
  val sum1_regs = RegInit(VecInit(Seq.fill(2)(0.U(accBits.W))))
  val sum2_regs = RegInit(VecInit(Seq.fill(2)(0.U(accBits.W))))
  val max_regs  = RegInit(VecInit(Seq.fill(2)(0.U(inBits.W))))

  val tile_done = io.is_last_tile && io.phase1_valid && !is_bypass
  
  when (tile_done && run) {
    acc_ptr := ~acc_ptr // 타일 종료 시 뱅크 스위칭
  }

  val is_new_max = io.phase1_in > max_regs(acc_ptr)

  when (io.clr_acc && run) {
    sum1_regs(acc_ptr) := 0.U; sum2_regs(acc_ptr) := 0.U; max_regs(acc_ptr) := 0.U
  } .elsewhen (io.phase1_valid && run && !is_bypass) {
    sum1_regs(acc_ptr) := sum1_regs(acc_ptr) + io.phase1_in
    val sq_val = io.phase1_in * io.phase1_in
    when (is_new_max) { max_regs(acc_ptr) := io.phase1_in }
    
    sum2_regs(acc_ptr) := MuxLookup(io.mode_sel, sum2_regs(acc_ptr))(Seq(
      NormMode.RMSNORM   -> (sum2_regs(acc_ptr) + sq_val),
      NormMode.LAYERNORM -> (sum2_regs(acc_ptr) + sq_val),
      NormMode.SOFTMAX   -> sum2_regs(acc_ptr) 
    ))
  }

  // 동결된 이전 뱅크(Locked Bank) 데이터를 Tree와 LUT로 전달
  val locked_ptr  = ~acc_ptr
  val locked_max  = max_regs(locked_ptr)
  val locked_sum1 = sum1_regs(locked_ptr)
  val locked_sum2 = sum2_regs(locked_ptr)

  io.local_max := locked_max
  io.to_sum_tree_sum1 := locked_sum1

  // ====================================================================
  // [1-C] Dynamic Pipeline Triggers 
  // ====================================================================
  val t1_trigger  = RegNext(tile_done, false.B)
  val t5_trigger  = ShiftRegister(tile_done, 5, false.B, run) 
  val t6_trigger  = RegNext(t5_trigger, false.B)              
  val t7_trigger  = RegNext(t6_trigger, false.B)              
  val t12_trigger = ShiftRegister(t7_trigger, 5, false.B, run)

  // ====================================================================
  // [1-D] Softmax Local Sum Correction
  // ====================================================================
  val diff_old    = Mux(is_new_max, max_regs(acc_ptr) - io.phase1_in, 0.U) 
  val diff_norm   = Mux(is_new_max, 0.U, io.phase1_in - max_regs(acc_ptr)) 
  val phase1_addr = Mux(is_new_max, diff_old, diff_norm)
  
  val correction_addr = io.global_max - locked_max 
  
  val exp_lut = Module(new Universal_Wide_LUT(indexBits, dataBits, writeBits))
  exp_lut.io.wr_en   := io.exp_wr_en
  exp_lut.io.wr_addr := io.exp_wr_addr
  exp_lut.io.wr_data := io.exp_wr_data
  exp_lut.io.rd_en   := run
  
  exp_lut.io.rd_addr := Mux(t5_trigger && is_global_softmax, correction_addr,
                          Mux(io.phase1_valid, phase1_addr, io.phase2_in))

  val correction_factor = exp_lut.io.rd_data
  val corrected_sum2_wire = (locked_sum2 * correction_factor)(accBits - 1, 0)
  val corrected_sum2_reg  = RegEnable(corrected_sum2_wire, 0.U(accBits.W), t6_trigger && run)

  io.to_sum_tree_sum2 := Mux(is_global_softmax, corrected_sum2_reg, locked_sum2)

  // ====================================================================
  // [1-E] Scale LUT Request & Latch
  // ====================================================================
  val active_sum1 = Mux(io.lane_reduce_en, io.global_sum1, locked_sum1)
  val active_sum2 = Mux(io.lane_reduce_en, io.global_sum2, Mux(is_global_softmax, corrected_sum2_reg, locked_sum2))

  val mu_wire = active_sum1 >> shiftAmount.U
  val var_algebraic = (active_sum2 >> shiftAmount.U) - (mu_wire * mu_wire)

  val final_reduction = MuxLookup(io.mode_sel, active_sum2)(Seq(
    NormMode.RMSNORM   -> (active_sum2 >> shiftAmount.U),
    NormMode.LAYERNORM -> var_algebraic,
    NormMode.SOFTMAX   -> active_sum2
  ))

  val lz_count = PriorityEncoder(final_reduction.asBools.reverse)
  val msb_idx  = (accBits - 1).U - lz_count
  val mantissa = (final_reduction << lz_count)(accBits - 1, accBits - indexBits)

  val do_lookup = Mux(!io.lane_reduce_en, t1_trigger,
                    Mux(!is_global_softmax, t5_trigger, t12_trigger))
  
  io.phase2_req := do_lookup

  val scale_lut = Module(new Universal_Wide_LUT(indexBits, dataBits, writeBits))
  scale_lut.io.wr_en   := io.scale_wr_en
  scale_lut.io.wr_addr := io.scale_wr_addr
  scale_lut.io.wr_data := io.scale_wr_data
  scale_lut.io.rd_en   := run
  scale_lut.io.rd_addr := mantissa 
  
  io.lut_ready := exp_lut.io.lut_ready && scale_lut.io.lut_ready

  val scale_factor_wire = scale_lut.io.rd_data
  val scale_factor_reg  = RegEnable(scale_factor_wire, 0.U(dataBits.W), RegNext(do_lookup, false.B) && run)

  // ====================================================================
  // [1-F] Phase 2: Application
  // ====================================================================
  val phase2_in_d1 = RegEnable(io.phase2_in, 0.U(inBits.W), run)
  
  val apply_target = Mux(io.mode_sel === NormMode.SOFTMAX, exp_lut.io.rd_data, phase2_in_d1)
  val mult_res = apply_target * scale_factor_reg
  val norm_res = (mult_res >> (msb_idx >> 1.U)).asUInt
  
  val maxValue = ((1 << outBits) - 1).U
  norm_applied_data := Mux(norm_res > maxValue, maxValue, norm_res(outBits - 1, 0))
}

// ====================================================================
// [2] UniversalNormUnit (16 Lane 통합 & 5-Cycle Tree 3개 & FIFO)
// ====================================================================
class UniversalNormUnit(
  val numLines: Int = 16,
  val writeBits: Int = 128, 
  val inBits: Int = 8,
  val outBits: Int = 8,
  val vectorSize: Int = 4096,
  val indexBits: Int = 8,
  val dataBits: Int = 16
) extends Module {
  
  val burstAddrBits = log2Ceil((1 << indexBits) / (writeBits / dataBits)) 
  val accBits = ((inBits * 2) + log2Ceil(vectorSize)).max(32)

  val io = IO(new Bundle {
    val phase1_in_vec          = Input(Vec(numLines, UInt(inBits.W)))
    val phase1_valid_vec       = Input(Vec(numLines, Bool()))
    val is_last_tile           = Input(Bool())
    val phase1_param_update_in = Input(Bool())

    val phase1_out_vec         = Output(Vec(numLines, UInt(inBits.W)))
    val phase1_out_valid_vec   = Output(Vec(numLines, Bool()))
    val phase1_param_update_out= Output(Bool())

    val phase2_in_vec          = Input(Vec(numLines, UInt(inBits.W)))
    val phase2_valid_vec       = Input(Vec(numLines, Bool()))
    
    val phase2_out_vec         = Output(Vec(numLines, UInt(outBits.W)))
    val phase2_out_valid_vec   = Output(Vec(numLines, Bool()))
    val phase2_param_update_out= Output(Bool())

    val mode_sel               = Input(UInt(2.W))
    val lane_reduce_en         = Input(Bool())
    val clr_acc                = Input(Bool())
    val stall                  = Input(Bool())
    val phase2_req             = Output(Bool()) 

    val lut_wr_en              = Input(Bool())
    val lut_is_exp             = Input(Bool())
    val lut_wr_addr            = Input(UInt(burstAddrBits.W)) 
    val lut_wr_data            = Input(Vec(writeBits / dataBits, UInt(dataBits.W)))

    val lut_ready              = Output(Bool()) 
  })

  val run = !io.stall

  // ====================================================================
  // [2-A] Metadata FIFO (블랙박스화)
  // ====================================================================
  val param_fifo = Module(new Queue(Bool(), 16)) 
  val is_bypass = io.mode_sel === NormMode.BYPASS
  
  param_fifo.io.enq.valid := io.phase1_valid_vec(0) && io.is_last_tile && !is_bypass && run
  param_fifo.io.enq.bits  := io.phase1_param_update_in
  
  val phase2_valid_d1 = RegNext(io.phase2_valid_vec(0), false.B)
  val phase2_start_edge = io.phase2_valid_vec(0) && !phase2_valid_d1
  
  param_fifo.io.deq.ready := phase2_start_edge && run
  val current_phase2_param = RegEnable(param_fifo.io.deq.bits, false.B, phase2_start_edge && run)
  
  io.phase1_param_update_out := io.phase1_param_update_in

  // ====================================================================
  // [2-B] 16-Lane 인스턴스화
  // ====================================================================
  val lines = Seq.fill(numLines)(Module(new UniversalNormLine(
    writeBits = writeBits, inBits = inBits, outBits = outBits,
    vectorSize = vectorSize, indexBits = indexBits, dataBits = dataBits
  )))

  // ====================================================================
  // [2-C] 5-Cycle Reduction Trees (Max, Sum1, Sum2)
  // ====================================================================
  def makeSumTree(in: Vec[UInt]): UInt = {
    val l1 = RegEnable(VecInit((0 until 8).map(i => in(2*i) + in(2*i+1))), run)
    val l2 = RegEnable(VecInit((0 until 4).map(i => l1(2*i) + l1(2*i+1))), run)
    val l3 = RegEnable(VecInit((0 until 2).map(i => l2(2*i) + l2(2*i+1))), run)
    val l4 = RegEnable(l3(0) + l3(1), run)
    RegEnable(l4, run) // Stage 5 (Global)
  }

  def makeMaxTree(in: Vec[UInt]): UInt = {
    def max(a: UInt, b: UInt) = Mux(a > b, a, b)
    val l1 = RegEnable(VecInit((0 until 8).map(i => max(in(2*i), in(2*i+1)))), run)
    val l2 = RegEnable(VecInit((0 until 4).map(i => max(l1(2*i), l1(2*i+1)))), run)
    val l3 = RegEnable(VecInit((0 until 2).map(i => max(l2(2*i), l2(2*i+1)))), run)
    val l4 = RegEnable(max(l3(0), l3(1)), run)
    RegEnable(l4, run) // Stage 5 (Global)
  }

  val local_sum1 = Wire(Vec(16, UInt(accBits.W)))
  val local_sum2 = Wire(Vec(16, UInt(accBits.W)))
  val local_max  = Wire(Vec(16, UInt(inBits.W)))
  
  for (i <- 0 until numLines) {
    local_sum1(i) := lines(i).io.to_sum_tree_sum1
    local_sum2(i) := lines(i).io.to_sum_tree_sum2
    local_max(i)  := lines(i).io.local_max
  }

  val global_sum1 = makeSumTree(local_sum1)
  val global_sum2 = makeSumTree(local_sum2)
  val global_max  = makeMaxTree(local_max)

  // ====================================================================
  // [2-D] 포트 매핑 (Routing)
  // ====================================================================
  for (r <- 0 until numLines) {
    lines(r).io.phase1_in              := io.phase1_in_vec(r)
    lines(r).io.phase1_valid           := io.phase1_valid_vec(r)
    lines(r).io.is_last_tile           := io.is_last_tile
    lines(r).io.phase1_param_update_in := io.phase1_param_update_in
    
    lines(r).io.phase2_in              := io.phase2_in_vec(r)
    lines(r).io.phase2_valid           := io.phase2_valid_vec(r)
    lines(r).io.phase2_param_update_in := current_phase2_param
    
    lines(r).io.mode_sel               := io.mode_sel
    lines(r).io.lane_reduce_en         := io.lane_reduce_en
    lines(r).io.clr_acc                := io.clr_acc
    lines(r).io.stall                  := io.stall
    
    lines(r).io.global_sum1            := global_sum1
    lines(r).io.global_sum2            := global_sum2
    lines(r).io.global_max             := global_max

    lines(r).io.exp_wr_en              := io.lut_wr_en && io.lut_is_exp
    lines(r).io.exp_wr_addr            := io.lut_wr_addr
    lines(r).io.exp_wr_data            := io.lut_wr_data
    lines(r).io.scale_wr_en            := io.lut_wr_en && !io.lut_is_exp
    lines(r).io.scale_wr_addr          := io.lut_wr_addr
    lines(r).io.scale_wr_data          := io.lut_wr_data

    io.phase1_out_vec(r)               := lines(r).io.phase1_out
    io.phase1_out_valid_vec(r)         := lines(r).io.phase1_out_valid
    io.phase2_out_vec(r)               := lines(r).io.phase2_out
    io.phase2_out_valid_vec(r)         := lines(r).io.phase2_out_valid
  }

  // Phase2 Req는 0번 레인 대표 사용
  io.phase2_req := lines(0).io.phase2_req
  io.phase2_param_update_out := lines(0).io.phase2_param_update_out
  io.lut_ready  := lines(0).io.lut_ready
}