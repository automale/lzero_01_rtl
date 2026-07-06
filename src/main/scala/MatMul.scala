import circt.stage.ChiselStage // import first
import chisel3._
import chisel3.util._

// [0] Edge detection utility
object Edge {
  // Rising Edge (0 -> 1)
  def rising(x: Bool): Bool = x & !RegNext(x, false.B)
  
  // Falling Edge (1 -> 0)
  def falling(x: Bool): Bool = !x & RegNext(x, false.B)
  
  // Both Edges (변화 감지)
  def both(x: Bool): Bool = x =/= RegNext(x, false.B)
}

// [1] 하드웨어 모듈 정의 weight stationary
// A x W = C
class MacUnit extends Module {
  val io = IO(new Bundle {
    val in_x      = Input(UInt(8.W)) // input mat
    val in_y      = Input(UInt(16.W)) // output mat init vaa(bias)

    val in_w      = Input(UInt(8.W))
    val set_w     = Input(Bool())
    val clear_w   = Input(Bool())

    val out_in    = Output(UInt(8.W))
    val out_mac   = Output(UInt(16.W))
    val out_set_w = Output(Bool())
  })

  val weight = RegInit(0.U(8.W))

  when (io.clear_w) {
    weight := 0.U
  } .elsewhen (io.set_w) {
    weight := io.in_w
  }

  io.out_mac  := RegNext((io.in_x * weight) + io.in_y)
  io.out_in   := RegNext(io.in_x)
  io.out_set_w := RegNext(io.set_w, false.B)
}

// [2] systolic array 16 by 16
class MXU_16 extends Module {
  // input output
  val io = IO(new Bundle{
    val in_X    = Input(Vec(16, UInt(8.W)))
    val in_W    = Input(Vec(16, Vec(16, UInt(8.W))))
    val set_W   = Input(Vec(16, Bool()))
    val clear_W = Input(Bool())
    val out_MAC = Output(Vec(16, UInt(16.W)))
  })

  val macs = Seq.fill(16, 16)(Module(new MacUnit()))

  // weight, weight set chain wiring
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.in_w  := io.in_W(r)(c)
      if(c == 0){ macs(r)(c).io.set_w := io.set_W(r)}
      else { macs(r)(c).io.set_w := macs(r)(c-1).io.out_set_w }
    }
  }

  // weight clearing wiring
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      macs(r)(c).io.clear_w := io.clear_W
    }
  }

  // wiring mac units
  for ( r <- 0 until 16 ){
    for ( c <- 0 until 16 ){
      if (c == 0) { macs(r)(0).io.in_x := io.in_X(r) }
      else { macs(r)(c).io.in_x := macs(r)(c-1).io.out_in }

      if (r == 0) { macs(r)(c).io.in_y  := 0.U }
      else { macs(r)(c).io.in_y := macs(r-1)(c).io.out_mac }
    }
  }

  // wiring MatMulUnit's output to each of macUnit
  for (c <- 0 until 16) {
    io.out_MAC(c)      := macs(15)(c).io.out_mac // output wiring
  }

}

// [3] data orchestrator를 위한 사이즈 16의 shift buffer
class Orch_inbuffer_16 extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(16, UInt(8.W)))
    val load_enable = Input(Bool())
    val shft_enable = Input(Bool())
    val out_scalar  = Output(UInt(8.W))
  })
  val shift_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.load_enable ){
    shift_reg := io.in_vector
  } .elsewhen ( io.shft_enable ){
    for ( i <- 0 until 15 ){
      shift_reg(i) := shift_reg(i+1)
    }
    shift_reg(15) := 0.U
  }

  when ( io.shft_enable ) {
    io.out_scalar := shift_reg(0)
  } .otherwise {
    io.out_scalar := 0.U
  }
}

// [4] data orchestrator를 위한 사이즈 16의 shadow weight buffer
class Shadow_wbuffer_16 extends Module {
  val io = IO(new Bundle{
    val in_vector   = Input(Vec(16, UInt(8.W)))
    val load_enable = Input(Bool())
    val out_enable  = Input(Bool())
    val out_vector  = Output(Vec(16, UInt(8.W)))
  })
  val shadow_weight_reg = RegInit(VecInit(Seq.fill(16)(0.U(8.W))))

  when ( io.load_enable ){
    shadow_weight_reg := io.in_vector
  } 
  
  io.out_vector := Mux(io.out_enable, shadow_weight_reg, VecInit(Seq.fill(16)(0.U(8.W))))

}

// [5] data orchestration unit
class DataOrchUnit_16 extends Module {
  val io = IO(new Bundle{
    val in_input    = Input(Vec(16, UInt(8.W)))
    val in_weight   = Input(Vec(16, UInt(8.W)))

    val feed_enable = Input(Bool())
    val load_enable = Input(Bool())

    val mxu_input   = Output(Vec(16, UInt(8.W)))
    val mxu_weight  = Output(Vec(16, Vec(16, UInt(8.W))))
    val feed_row   = Output(Vec(16, Bool()))
  })

  val input_buff          = Seq.fill(16)(Module(new Orch_inbuffer_16()))
  val shadow_weight_buff  = Seq.fill(16)(Module(new Shadow_wbuffer_16()))
  val control_chain       = RegInit(VecInit(Seq.fill(16)(false.B)))

  // control chain wiring
  control_chain(0) := io.feed_enable
  for (r <- 1 until 16) {
    control_chain(r) := control_chain(r-1)
  }

  // input wiring
  for( r<-0 until 16 ){
    // irrigate input & weight vector from BRAM memory to each buffer
    input_buff(r).io.in_vector := io.in_input
    shadow_weight_buff(r).io.in_vector := io.in_weight

    // control chain wiring
    input_buff(r).io.shft_enable  := control_chain(r)
    input_buff(r).io.load_enable  := control_chain(r) & io.load_enable
    shadow_weight_buff(r).io.out_enable  := control_chain(r)
    shadow_weight_buff(r).io.load_enable  := control_chain(r) & io.load_enable
  }

  // output wiring
  for (r <- 0 until 16) {
    io.feed_row(r) := control_chain(r)
    // mxu input wiring
    io.mxu_input(r) := input_buff(r).io.out_scalar
    // shadow weight buffer to output wiring 
    // ** will be wired to MXU_16's in_W
    for (c <- 0 until 16) {
      io.mxu_weight(r)(c) := shadow_weight_buff(r).io.out_vector(c)
    }
  }
}

// [6] Accumulation Shift Buffer for 16x16 Tile Accumulation
class Accum_buffer_16 extends Module {
  val io = IO(new Bundle{
    val in_scalar   = Input(UInt(16.W))
    val add_enable  = Input(Bool())
    val stream_out  = Input(Bool())
    val clear       = Input(Bool())
    val out_scalar  = Output(UInt(32.W))
  })

  val shift_reg = RegInit(VecInit(Seq.fill(16)(0.U(32.W))))
  val current_sum = shift_reg(0) + io.in_scalar.zext.asUInt

  when ( io.clear ){
    for ( i <- 0 until 16 ){
      shift_reg(i) := 0.U
    }
  } .elsewhen ( io.stream_out ){
    for ( i <- 0 until 15 ){
      shift_reg(i) := shift_reg(i+1)
    }
    shift_reg(15) := 0.U
  } .elsewhen ( io.add_enable ){
    shift_reg(15) := current_sum 
    for (i <- 0 until 15) {
      shift_reg(i) := shift_reg(i + 1)
    }
  }
  
  io.out_scalar := Mux(io.stream_out, current_sum, 0.U)
}

// [7] 16x16 2D 타일 누산기 (256 Words x 32-bit Buffer)
class TileAccumulator_16 extends Module {
  val io = IO(new Bundle {
    // 1. TPU MXU 16-bit vector input
    val in_vec      = Input(Vec(16, UInt(16.W))) 
    
    // 2. control signals for accumulation (start of post processing)
    val clear             = Input(Bool()) // clear accumulation buffer
    val accum_en          = Input(Bool()) // accumulation start signal
    val accum_stream_en   = Input(Bool()) // accumulation ends proceed to VPU (row0)

    // 3. output for quantization + activation unit (data + control)
    val out_vec     = Output(Vec(16, UInt(32.W)))
    val en_vec      = Output(Vec(16, Bool())) // output valid signal for VPU
  })

  // 16 Row x 16 Col, 32-bit 2D register buffer (1KB)
  val accum_buffer = Seq.fill(16)(Module(new Accum_buffer_16()))

  // control chain for accumulation enable and done signal
  val en_delay_chain = RegInit(VecInit(Seq.fill(16)(false.B))) 
  val done_delay_chain = RegInit(VecInit(Seq.fill(16)(false.B)))

  // control chain wiring for accumulation enable and done signal
  en_delay_chain(0)   := io.accum_en
  done_delay_chain(0) := io.accum_stream_en
  for (r <- 1 until 16) {
    en_delay_chain(r)   := en_delay_chain(r-1)
    done_delay_chain(r) := done_delay_chain(r-1)
  }

  // accumulation buffer wiring
  for( r <- 0 until 16 ){
    // input wiring
    accum_buffer(r).io.in_scalar  := io.in_vec(r)
    accum_buffer(r).io.add_enable := en_delay_chain(r)
    accum_buffer(r).io.stream_out := done_delay_chain(r)
    accum_buffer(r).io.clear      := io.clear
    // output wiring
    io.out_vec(r) := accum_buffer(r).io.out_scalar
    io.en_vec(r)  := done_delay_chain(r)
  }
}

// [8] Tensor processing unit
// Orchestrator + 16x16 Systolic Array
class TPU_top extends Module {
  val io = IO(new Bundle {
    // 1. from BRAM/DMA input data 
    val in_input    = Input(Vec(16, UInt(8.W)))
    val in_weight   = Input(Vec(16, UInt(8.W)))

    // 2. control signals
    val feed_enable       = Input(Bool())
    val load_enable       = Input(Bool())
    val clear_W           = Input(Bool())
    val accum_start       = Input(Bool())
    val accum_stream_en   = Input(Bool())

    // 3. skewed vectorized output to VPU (32-bit x 16)
    val out_accum   = Output(Vec(16, UInt(32.W)))
    val vpu_enable  = Output(Vec(16, Bool()))
  })

  // module instantiation
  val orchestrator = Module(new DataOrchUnit_16())
  val mxu          = Module(new MXU_16())
  val accumulator  = Module(new TileAccumulator_16())
  
  // [A] outer IO <-> Orchestrator
  orchestrator.io.in_input    := io.in_input
  orchestrator.io.in_weight   := io.in_weight
  orchestrator.io.feed_enable := io.feed_enable
  orchestrator.io.load_enable := io.load_enable

  // [B] Orchestrator <-> MXU (Systolic Array)
  // 1. input matrix (skewed) vector input wiring (Skewed Input -> MXU Input)
  mxu.io.in_X := orchestrator.io.mxu_input
  // 2. shadow weight to weight 2d matrix wiring (Shadow Buffer -> MXU Weight)
  mxu.io.in_W := orchestrator.io.mxu_weight
  // 3. global clear 
  mxu.io.clear_W := io.clear_W
  // 4. set weight row enable wiring (Orchestrator -> MXU)
  mxu.io.set_W := orchestrator.io.feed_row

  // [C] MXU <-> Accumulator
  accumulator.io.in_vec           := mxu.io.out_MAC
  accumulator.io.clear            := io.clear_W
  accumulator.io.accum_en         := io.accum_start
  accumulator.io.accum_stream_en  := io.accum_stream_en

  // [D] MXU <-> final output wiring ... will be wired to VPU input
  io.out_accum  := accumulator.io.out_vec
  io.vpu_enable := accumulator.io.en_vec
}

// [9] 256-Entry 0-Cycle Universal Activation LUT
class ActivationLUT_256 extends Module {
  val io = IO(new Bundle {
    // 1. LUT programming (DRAM -> DMA -> LUT loading interface)
    val wr_en   = Input(Bool())
    val wr_addr = Input(UInt(8.W)) // 0 ~ 255 address
    val wr_data = Input(UInt(8.W)) // pre-computed 8-bit activation value

    // 2. real time 0-Cycle read port (QuantAct -> LUT)
    val rd_addr = Input(UInt(8.W)) // quantized 8-bit index
    val rd_data = Output(UInt(8.W))
  })

  // 256 Words x 8-bit async read memory (FPGA LUTRAM)
  val lut_mem = Mem(256, UInt(8.W))

  // write LUT
  when (io.wr_en) {
    lut_mem(io.wr_addr) := io.wr_data
  }

  // read LUT
  io.rd_data := lut_mem(io.rd_addr)
}

// [10] LUT integrated single Row QuantAct core 10bit quant -> 8bit activation
class QuantActCore_10Bit extends Module {
  val io = IO(new Bundle {
    val in_mac   = Input(UInt(32.W))
    val param    = Input(UInt(32.W)) // [31:16] M, [12:8] S, [7:0] ZP
    val act_en   = Input(Bool())     // true: 10b LUT activated(SiLU etc...), false: 8b linear quantization
    
    // 10-bit BRAM LUT loading port (1KB)
    val lut_wr_en   = Input(Bool())
    val lut_wr_addr = Input(UInt(10.W)) // 0 ~ 1023 주소
    val lut_wr_data = Input(UInt(8.W))

    val out_qact    = Output(UInt(8.W))
  })

  // 1. floating point scaling (10-bit index)
  val zp    = io.param(7, 0).asSInt
  val shift = io.param(12, 8).asUInt
  val mult  = io.param(31, 16).asUInt

  val sub_res   = io.in_mac.asSInt.pad(33) - zp.pad(33)
  val mult_res  = sub_res * mult.zext.asSInt
  
  // 10-bit resolution (0~1023)
  val shift_res_10b   = (mult_res >> (shift - 2.U)).asSInt 
  val clamped_idx_10b = Mux(shift_res_10b < 0.S, 0.U(10.W), Mux(shift_res_10b > 1023.S, 1023.U(10.W), shift_res_10b(9, 0).asUInt))

  // 2. FPGA BRAM 1024-Entry LUT pass
  val act_lut_bram = Mem(1024, UInt(8.W))
  when (io.lut_wr_en) {
    act_lut_bram(io.lut_wr_addr) := io.lut_wr_data
  }
  val lut_out = act_lut_bram(clamped_idx_10b)

  // 3. [0-Cost Bit-Slicing Bypass] 10-bit -> 8-bit linear quantization
  val linear_8b_out = clamped_idx_10b(9, 2)

  // 4. output will be exported in 1cycle delay
  io.out_qact := Mux(RegNext(io.act_en), lut_out, RegNext(linear_8b_out))
}

// [11] 16 Row Universal VPU QuantActUnit
class QuantActUnit_16_LUT extends Module {
  val io = IO(new Bundle {
    val in_vec            = Input(Vec(16, UInt(32.W)))
    val in_valid          = Input(Vec(16, Bool()))
    val activation_enable = Input(Bool()) // LUT activation enable

    // ZP, S, M loading port
    val param_wr_en   = Input(Bool())
    val param_wr_addr = Input(UInt(4.W))
    val param_in      = Input(UInt(32.W))

    // 16개 코어의 LUT를 동시에 프로그래밍하는 브로드캐스트 포트!
    val lut_wr_en     = Input(Bool())
    val lut_wr_addr   = Input(UInt(8.W))
    val lut_wr_data   = Input(UInt(8.W))

    val lut_en        = Input(Bool()) // VPU 활성화 모드 제어

    val out_vec       = Output(Vec(16, UInt(8.W)))
    val out_valid     = Output(Vec(16, Bool()))
  })

  val param_regs = RegInit(VecInit(Seq.fill(16)(0.U(32.W))))
  when (io.param_wr_en) {
    param_regs(io.param_wr_addr) := io.param_in
  }

  val cores = Seq.fill(16)(Module(new QuantActCore_10Bit()))

  for (r <- 0 until 16) {
    cores(r).io.in_mac            := io.in_vec(r)
    cores(r).io.param             := param_regs(r)
    cores(r).io.lut_en            := io.lut_en
    cores(r).io.activation_enable := io.activation_enable

    // LUT Broadcast Writing
    cores(r).io.lut_wr_en   := io.lut_wr_en
    cores(r).io.lut_wr_addr := io.lut_wr_addr
    cores(r).io.lut_wr_data := io.lut_wr_data

    io.out_vec(r)   := cores(r).io.out_qact
    io.out_valid(r) := ShiftRegister(io.in_valid(r), 3, false.B)
  }
}

// [12] 16 Row Universal VPU Norm Line
class UniversalNormLine extends Module {
  val io = IO(new Bundle {
    // Phase 1: QuantAct에서 스트리밍되는 실시간 데이터 (URAM Write와 동시 진입)
    val stream_in     = Input(UInt(8.W))
    val stream_valid  = Input(Bool())
    
    // Phase 2: URAM Read로 다시 꺼낸 후처리용 원본 데이터
    val uram_rd_data  = Input(UInt(8.W))
    val uram_rd_valid = Input(Bool())

    // 제어 신호
    val mode_sel      = Input(UInt(2.W)) // 0: RMSNorm, 1: LayerNorm, 2: Softmax
    val clr_acc       = Input(Bool())

    // 스칼라 LUT 로딩 포트
    val lut_wr_en     = Input(Bool())
    val lut_wr_addr   = Input(UInt(8.W))
    val lut_wr_data   = Input(UInt(16.W))

    // 최종 후처리 완료 출력 (Transposer로 직행)
    val out_data      = Output(UInt(8.W))
    val out_valid     = Output(Bool())
  })

  // ====================================================================
  // [Phase 1] On-the-Fly 실시간 통계량 누적기 (4096 클럭 동안 완결)
  // ====================================================================
  val sum1_reg = RegInit(0.U(32.W)) // LayerNorm Sum(x) mean computation
  val sum2_reg = RegInit(0.U(32.W)) // RMSNorm SqSum, LayerNorm SqSum, Softmax ExpSum
  val max_reg  = RegInit(0.U(8.W))  // Softmax's Max value register

  // 1. Softmax : 256-Entry Exp LUT (e^-x, e^Delta compensation)
  val exp_lut = Mem(256, UInt(16.W))

  // 2. Online Softmax real time compensate (Milakov Algorithm)
  val is_new_max = io.stream_in > max_reg
  val diff_old   = Mux(is_new_max, max_reg - io.stream_in, 0.U) // 음수 차이 Delta
  val diff_norm  = Mux(is_new_max, 0.U, io.stream_in - max_reg) // 일반적인 x - max

  val exp_val    = exp_lut(diff_norm)
  val scale_old  = exp_lut(diff_old) // Max가 바뀔 때 기존 Sum을 보정할 스케일 factor

  when (io.clr_acc) {
    sum1_reg := 0.U
    sum2_reg := 0.U
    max_reg  := 0.U
  } .elsewhen (io.stream_valid) {
    // [A] LayerNorm / RMSNorm 누적
    sum1_reg := sum1_reg + io.stream_in.zext.asUInt
    val sq_val = io.stream_in * io.stream_in
    
    // [B] Online Softmax 누적 및 Max 갱신 보정!
    val next_softmax_sum = Mux(is_new_max, 
      ((sum2_reg * scale_old) >> 8).asUInt + 1.U, // 기존 Sum 보정 + e^0(1)
      sum2_reg + exp_val.zext.asUInt              // 기존 Max 유지, e^(x-max) 누적
    )

    when (is_new_max) { max_reg := io.stream_in }

    sum2_reg := MuxLookup(io.mode_sel, sq_val)(Seq( \
      0.U -> (sum2_reg + sq_val), \
      1.U -> (sum2_reg + sq_val), \
      2.U -> next_softmax_sum \
    ))
  }

  // LayerNorm 분산 보정: Var = E[X^2] - (E[X])^2
  val mu_wire = sum1_reg >> 12
  val var_algebraic = (sum2_reg >> 12) - (mu_wire * mu_wire)

  val final_reduction = MuxLookup(io.mode_sel, sum2_reg)(Seq( \
    0.U -> (sum2_reg >> 12), \
    1.U -> var_algebraic, \
    2.U -> sum2_reg \
  ))

  // ====================================================================
  // [Bridge] 4096 클럭 완료 직후 스칼라 LUT로 최종 Scale Factor 즉시 추출
  // ====================================================================
  val lz_count = PriorityEncoder(final_reduction.asBools.reverse)
  val msb_idx  = 31.U(5.W) - lz_count
  val mantissa = (final_reduction << lz_count)(31, 24)

  val scalar_lut = Mem(256, UInt(16.W))
  when (io.lut_wr_en) {
    scalar_lut(io.lut_wr_addr) := io.lut_wr_data
  }
  val scale_factor = scalar_lut(mantissa) // 딱 1개의 최종 스케일 값 완공!

  // ====================================================================
  // [Phase 2] VPU_Buffer(URAM)에서 읽어온 데이터에 후처리 스케일 입히기!
  // ====================================================================
  // Softmax는 저장된 원본 데이터와 아까 최종 확정된 max_reg로 e^(x - max_final) 조회
  val phase2_exp = exp_lut(io.uram_rd_data - max_reg)
  val apply_target = Mux(io.mode_sel === 2.U, phase2_exp, io.uram_rd_data.zext.asUInt)

  val mult_res = apply_target * scale_factor
  val norm_res = (mult_res >> (msb_idx >> 1.U)).asUInt
  val clamped  = Mux(norm_res > 255.U, 255.U(8.W), norm_res(7, 0))

  io.out_data  := Mux(io.uram_rd_valid, clamped, 0.U)
  io.out_valid := io.uram_rd_valid
}

// [13] Universal Norm/Softmax Unit
class UniversalNormUnit_16 extends Module {
  val io = IO(new Bundle {
    // 1. VPU_buffer (16 URAM Banks)에서 동시에 내려오는 16행 스트림 (16 Bytes/clk)
    val in_vec      = Input(Vec(16, UInt(8.W)))
    val in_valid    = Input(Vec(16, Bool()))
    
    // 2. 글로벌 파이프라인 제어 신호
    val mode_sel    = Input(Bool()) // 0: RMSNorm, 1: Softmax
    val phase_sel   = Input(Bool()) // 0: Accumulate, 1: Apply Scale
    val clr_acc     = Input(Bool())

    // 3. 16개 라인의 스칼라 LUT를 동시에 프로그래밍하는 브로드캐스트 포트
    val lut_wr_en   = Input(Bool())
    val lut_wr_addr = Input(UInt(8.W))
    val lut_wr_data = Input(UInt(16.W))

    // 4. Transposer (256B Ping-Pong Buffer)로 나가는 16행 정규화 출력
    val out_vec     = Output(Vec(16, UInt(8.W)))
    val out_valid   = Output(Vec(16, Bool()))
  })

  // 16개의 독립적인 Normalizer Line 인스턴스화
  val lines = Seq.fill(16)(Module(new UniversalNormLine()))

  for (r <- 0 until 16) {
    // [핵심] Row r의 라인은 오직 VPU_buffer의 Bank r에서 나오는 데이터만 1:1로 처리!
    lines(r).io.in_data   := io.in_vec(r)
    lines(r).io.in_valid  := io.in_valid(r)
    lines(r).io.mode_sel  := io.mode_sel
    lines(r).io.phase_sel := io.phase_sel
    lines(r).io.clr_acc   := io.clr_acc

    // 16개 라인의 LUT에 동일한 수학 테이블(1/x 또는 1/sqrt)을 단 256클럭만에 동시 복사!
    lines(r).io.lut_wr_en   := io.lut_wr_en
    lines(r).io.lut_wr_addr := io.lut_wr_addr
    lines(r).io.lut_wr_data := io.lut_wr_data

    io.out_vec(r)   := lines(r).io.out_data
    io.out_valid(r) := lines(r).io.out_valid
  }
}

// [N] Chisel 6.x 실행 객체
object TPU_Main extends App {
  println("TPU Accelerator SystemVerilog extracting...")
  
  ChiselStage.emitSystemVerilogFile(new MacUnit(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new MXU_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new DataOrchUnit_16(), Array("--target-dir", "generated"))
  ChiselStage.emitSystemVerilogFile(new TPU_top(), Array("--target-dir", "generated"))
  
  println("Extract SV Finished! Please check 'generated' directory")
}