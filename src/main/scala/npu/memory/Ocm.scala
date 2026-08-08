package npu.core.memory

import chisel3._
import chisel3.util._

// ====================================================================
// [0] 공통 상수 및 FSM State 정의
// ====================================================================
object OcmState {
  val S_IDLE    = 0.U(3.W)
  val S_PRELOAD = 1.U(3.W)
  val S_READY   = 2.U(3.W)
  val S_WORKING = 3.U(3.W)
  val S_STALL   = 4.U(3.W)
}

// 4KB 뱅크 기준 카운트 (16Byte 소비 시 256회)
object OcmConst {
  val BANK_MAX_READS = 255.U(8.W) 
  val WATERMARK      = 8.U(8.W)
}

// 공통 I/O 인터페이스 Trait
class OcmCommonIo extends Bundle {
  val soft_reset         = Input(Bool())
  val enable             = Input(Bool())
  val shoot              = Input(Bool())
  
  val dma_data_valid     = Input(Bool())
  val dma_data_last      = Input(Bool())
  val dma_write_ready    = Input(Bool())

  val ready              = Output(Bool())
  val hungry             = Output(Bool())
  val impending          = Output(Bool())
  val working            = Output(Bool())
}

// ====================================================================
// [1] 비대칭 대역폭 Ping-Pong SRAM (64B Write / 16B Read)
// ====================================================================
class AsymmetricPingPongSRAM(
  val writeWidthBytes: Int = 64, // DMA Write: 64 Byte (512-bit) / cycle
  val readWidthBytes: Int = 16,  // CU Read: 16 Byte (128-bit) / cycle
  val capacityBytes: Int = 4096  // Bank당 용량: 4 KB
) extends Module {
  
  val writeWidth = writeWidthBytes * 8 // 512 bits
  val readWidth  = readWidthBytes * 8  // 128 bits
  
  // 대역폭 비율 (Ratio) = 4
  val ratio = writeWidth / readWidth 
  
  // 깊이(Depth) 계산
  val writeDepth = capacityBytes / writeWidthBytes // 64 (0 ~ 63)
  val readDepth  = capacityBytes / readWidthBytes  // 256 (0 ~ 255)

  val io = IO(new Bundle {
    // ----------------------------------------------------
    // Ping Bank Ports
    // ----------------------------------------------------
    val ping_wr_en   = Input(Bool())
    val ping_wr_addr = Input(UInt(log2Ceil(writeDepth).W)) // 6 bits
    val ping_wr_data = Input(UInt(writeWidth.W))           // 512 bits
    
    val ping_rd_en   = Input(Bool())
    val ping_rd_addr = Input(UInt(log2Ceil(readDepth).W))  // 8 bits
    val ping_rd_data = Output(UInt(readWidth.W))           // 128 bits

    // ----------------------------------------------------
    // Pong Bank Ports
    // ----------------------------------------------------
    val pong_wr_en   = Input(Bool())
    val pong_wr_addr = Input(UInt(log2Ceil(writeDepth).W)) // 6 bits
    val pong_wr_data = Input(UInt(writeWidth.W))           // 512 bits
    
    val pong_rd_en   = Input(Bool())
    val pong_rd_addr = Input(UInt(log2Ceil(readDepth).W))  // 8 bits
    val pong_rd_data = Output(UInt(readWidth.W))           // 128 bits
  })

  // ----------------------------------------------------------------
  // Helper Method: 서브 뱅크 생성 및 라우팅 로직
  // ----------------------------------------------------------------
  def buildAsymmetricBank(
    wr_en: Bool, wr_addr: UInt, wr_data: UInt,
    rd_en: Bool, rd_addr: UInt
  ): UInt = {
    // 4개의 서브 뱅크(SyncReadMem) 생성: 각각 128-bit 너비, 깊이 64
    val sub_banks = Seq.fill(ratio)(SyncReadMem(writeDepth, UInt(readWidth.W)))
    val rd_data_vec = Wire(Vec(ratio, UInt(readWidth.W)))

    // 상위 비트: 서브 뱅크의 공통 어드레스 (rd_addr >> 2)
    val word_addr = rd_addr(log2Ceil(readDepth) - 1, log2Ceil(ratio)) 
    
    for (i <- 0 until ratio) {
      // Write: 512-bit 데이터를 128-bit씩 4조각으로 잘라서 4개 뱅크에 동시에 씀
      val slice_data = wr_data((i + 1) * readWidth - 1, i * readWidth)
      when(wr_en) {
        sub_banks(i).write(wr_addr, slice_data)
      }
      // Read: 공통 word_addr를 주고 데이터를 읽어옴 (1클럭 지연 발생)
      rd_data_vec(i) := sub_banks(i).read(word_addr, rd_en)
    }

    // 하위 비트: 4개의 서브 뱅크 중 어떤 것을 선택할지 결정 (rd_addr의 LSB 2 bits)
    val bank_sel = rd_addr(log2Ceil(ratio) - 1, 0)
    
    // SyncReadMem은 1클럭 딜레이가 있으므로, 선택 신호(bank_sel)도 똑같이 1클럭 지연시킴
    val bank_sel_d1 = RegEnable(bank_sel, 0.U, rd_en)

    // 최종 128-bit 데이터 출력 MUX
    rd_data_vec(bank_sel_d1)
  }

  // 인스턴스화 및 포트 연결
  io.ping_rd_data := buildAsymmetricBank(io.ping_wr_en, io.ping_wr_addr, io.ping_wr_data, io.ping_rd_en, io.ping_rd_addr)
  io.pong_rd_data := buildAsymmetricBank(io.pong_wr_en, io.pong_wr_addr, io.pong_wr_data, io.pong_rd_en, io.pong_rd_addr)
}

// ====================================================================
// [1] UB / WB Controller (Front-end: Push 패러다임)
// ====================================================================
class UbWbController(val is_ub: Boolean) extends Module {
  val io = IO(new Bundle {
    val ctrl = new OcmCommonIo()
    
    // Configs
    val compact_mode  = if (is_ub) Some(Input(Bool())) else None
    val lut_prog_mode = if (!is_ub) Some(Input(Bool())) else None
    
    // SRAM Interface
    val rd_addr       = Output(UInt(8.W))
    val rd_en         = Output(Bool())
    val active_bank   = Output(UInt(1.W)) // 0: Ping, 1: Pong
  })

  val state = RegInit(OcmState.S_IDLE)
  
  val active_bank = RegInit(0.U(1.W))
  val read_ptr    = RegInit(0.U(8.W))
  val pong_valid  = RegInit(false.B) // 다음 뱅크 준비 상태
  val compact_cnt = RegInit(0.U(4.W)) // UB 1/16 speed용

  // FSM Logic
  switch(state) {
    is(OcmState.S_IDLE) {
      when(io.ctrl.enable && !io.ctrl.soft_reset) { state := OcmState.S_PRELOAD }
    }
    is(OcmState.S_PRELOAD) {
      when(pong_valid) { state := OcmState.S_READY }
    }
    is(OcmState.S_READY) {
      when(io.ctrl.shoot) { state := OcmState.S_WORKING }
    }
    is(OcmState.S_WORKING) {
      val ptr_advance = if (is_ub) (!io.compact_mode.get || compact_cnt === 15.U) else true.B
      
      when(read_ptr === OcmConst.BANK_MAX_READS && ptr_advance) {
        when(pong_valid) {
          active_bank := ~active_bank
          read_ptr := 0.U
          pong_valid := false.B // 뱅크 스위칭 시 다음 뱅크는 비워짐
        }.otherwise {
          state := OcmState.S_STALL
        }
      }
    }
    is(OcmState.S_STALL) {
      when(pong_valid) { 
        state := OcmState.S_WORKING 
        active_bank := ~active_bank
        read_ptr := 0.U
        pong_valid := false.B
      }
    }
  }

  when(io.ctrl.soft_reset) {
    state := OcmState.S_IDLE
    active_bank := 0.U
    read_ptr := 0.U
    pong_valid := false.B
  }

  // Pointer & Compact Counter Logic
  val is_working = (state === OcmState.S_WORKING)
  val advance_cond = if (is_ub) (!io.compact_mode.get || compact_cnt === 15.U) else true.B
  
  when(is_working) {
    if (is_ub) {
      when(io.compact_mode.get) { compact_cnt := compact_cnt + 1.U }
    }
    when(advance_cond) { read_ptr := read_ptr + 1.U }
  }

  // DMA Write 감지 (배경에서 핑퐁 뱅크를 채움)
  when(io.ctrl.dma_data_last) { pong_valid := true.B }

  // Signal Extraction
  val active_bank_cnt = OcmConst.BANK_MAX_READS - read_ptr
  
  io.ctrl.working   := is_working
  io.ctrl.ready     := (state === OcmState.S_READY) || !io.ctrl.enable
  io.ctrl.hungry    := (state === OcmState.S_WORKING || state === OcmState.S_PRELOAD) && !pong_valid
  io.ctrl.impending := is_working && (active_bank_cnt <= OcmConst.WATERMARK) && !pong_valid

  io.rd_addr     := read_ptr
  io.rd_en       := is_working
  io.active_bank := active_bank
}

// ====================================================================
// [2] NB Controller (Mid-end: Pull & Pinned 패러다임)
// ====================================================================
class NbController extends Module {
  val io = IO(new Bundle {
    val ctrl          = new OcmCommonIo()
    val spill_to_dram = Input(Bool())
    val phase2_req    = Input(Bool()) // From CU
    
    val rd_addr       = Output(UInt(8.W))
    val rd_en         = Output(Bool())
    val active_bank   = Output(UInt(1.W))
  })

  val state       = RegInit(OcmState.S_IDLE)
  val read_ptr    = RegInit(0.U(8.W))
  val pong_valid  = RegInit(false.B)

  val is_working = (state === OcmState.S_WORKING)

  switch(state) {
    is(OcmState.S_IDLE) {
      when(io.ctrl.enable && !io.ctrl.soft_reset) { state := OcmState.S_PRELOAD }
    }
    is(OcmState.S_PRELOAD) {
      when(pong_valid) { state := OcmState.S_READY }
    }
    is(OcmState.S_READY) {
      // shoot을 기다리지 않고, phase2_req(Pull)가 들어오면 동작 시작
      when(io.phase2_req) { 
        state := OcmState.S_WORKING
        read_ptr := 0.U // Pinned 모드: 항상 Ping 뱅크(주소 0)부터 다시 읽음
      }
    }
    is(OcmState.S_WORKING) {
      read_ptr := read_ptr + 1.U
      when(read_ptr === OcmConst.BANK_MAX_READS) {
        when(io.spill_to_dram) {
           // Spill 모드일 때는 Ping-Pong 스위칭 필요 (코드 간략화)
           state := OcmState.S_IDLE 
        }.otherwise {
           // Zero-DRAM 모드: 1개 타일 끝. 다음 phase2_req 대기
           state := OcmState.S_READY 
        }
      }
    }
  }

  when(io.ctrl.dma_data_last) { pong_valid := true.B }
  when(io.ctrl.soft_reset) { state := OcmState.S_IDLE; pong_valid := false.B }

  val active_bank_cnt = OcmConst.BANK_MAX_READS - read_ptr

  io.ctrl.working   := is_working
  io.ctrl.ready     := (state === OcmState.S_READY) || !io.ctrl.enable
  // Spill 모드가 아닐 때(Zero-DRAM)는 추가 DMA 프리페치를 요구하지 않음
  io.ctrl.hungry    := is_working && !pong_valid && io.spill_to_dram
  io.ctrl.impending := is_working && (active_bank_cnt <= OcmConst.WATERMARK) && !pong_valid && io.spill_to_dram

  io.rd_addr     := read_ptr
  io.rd_en       := is_working
  io.active_bank := 0.U // Zero-DRAM Pinned 모드 고정 (Spill 확장 시 토글 로직 추가)
}

// ====================================================================
// [3] PB Controller (Parameter: Dual-Tracking Pull 패러다임)
// ====================================================================
class PbController extends Module {
  val io = IO(new Bundle {
    val ctrl       = new OcmCommonIo()
    val req_quant  = Input(Bool())
    val req_angle  = Input(Bool())
    
    // Base 셋업 (컴파일러가 내려줌)
    val max_quant  = Input(UInt(8.W))
    val max_angle  = Input(UInt(8.W))
    
    val addr_quant = Output(UInt(8.W))
    val addr_angle = Output(UInt(8.W))
    val active_bank= Output(UInt(1.W))
  })

  val state       = RegInit(OcmState.S_IDLE)
  val active_bank = RegInit(0.U(1.W))
  val ptr_quant   = RegInit(0.U(8.W))
  val ptr_angle   = RegInit(0.U(8.W))
  val pong_valid  = RegInit(false.B)

  val quant_done = (ptr_quant === io.max_quant)
  val angle_done = (ptr_angle === io.max_angle)
  val bank_done  = quant_done && angle_done
  val is_working = (state === OcmState.S_WORKING)

  switch(state) {
    is(OcmState.S_IDLE)    { when(io.ctrl.enable && !io.ctrl.soft_reset) { state := OcmState.S_PRELOAD } }
    is(OcmState.S_PRELOAD) { when(pong_valid) { state := OcmState.S_READY } }
    is(OcmState.S_READY)   { when(io.ctrl.shoot) { state := OcmState.S_WORKING } }
    is(OcmState.S_WORKING) {
      when(bank_done) {
        when(pong_valid) {
          active_bank := ~active_bank
          ptr_quant := 0.U; ptr_angle := 0.U; pong_valid := false.B
        }.otherwise { state := OcmState.S_STALL }
      }
    }
    is(OcmState.S_STALL) {
      when(pong_valid) {
        state := OcmState.S_WORKING
        active_bank := ~active_bank
        ptr_quant := 0.U; ptr_angle := 0.U; pong_valid := false.B
      }
    }
  }

  when(is_working) {
    when(io.req_quant && !quant_done) { ptr_quant := ptr_quant + 1.U }
    when(io.req_angle && !angle_done) { ptr_angle := ptr_angle + 1.U }
  }

  when(io.ctrl.dma_data_last) { pong_valid := true.B }
  when(io.ctrl.soft_reset) { state := OcmState.S_IDLE; pong_valid := false.B }

  val quant_left = io.max_quant - ptr_quant
  val angle_left = io.max_angle - ptr_angle

  io.ctrl.working := is_working
  io.ctrl.ready   := (state === OcmState.S_READY) || !io.ctrl.enable
  
  // 핵심: 두 파라미터가 모두(AND) 소진되어야 뱅크를 비워줌
  io.ctrl.hungry  := (state === OcmState.S_WORKING || state === OcmState.S_PRELOAD) && bank_done && !pong_valid
  // 핵심: 둘 중 하나라도(OR) 8클럭 이하로 남으면 위험 신호
  io.ctrl.impending := is_working && ((quant_left <= OcmConst.WATERMARK) || (angle_left <= OcmConst.WATERMARK)) && !pong_valid

  io.addr_quant  := ptr_quant
  io.addr_angle  := ptr_angle
  io.active_bank := active_bank
}

// ====================================================================
// [4] VPU Buffer Controller (Back-end: In-band Pull 패러다임)
// ====================================================================
class VbController extends Module {
  val io = IO(new Bundle {
    val ctrl          = new OcmCommonIo()
    val fusion_req_in = Input(Bool()) // In-band 태그 (VPU 진입 1클럭 전)
    
    val rd_addr       = Output(UInt(8.W))
    val rd_en         = Output(Bool())
    val active_bank   = Output(UInt(1.W))
  })

  val state       = RegInit(OcmState.S_IDLE)
  val active_bank = RegInit(0.U(1.W))
  val read_ptr    = RegInit(0.U(8.W))
  val pong_valid  = RegInit(false.B)

  val is_working = (state === OcmState.S_WORKING)

  switch(state) {
    is(OcmState.S_IDLE)    { when(io.ctrl.enable && !io.ctrl.soft_reset) { state := OcmState.S_PRELOAD } }
    is(OcmState.S_PRELOAD) { when(pong_valid) { state := OcmState.S_READY } }
    is(OcmState.S_READY)   { when(io.fusion_req_in) { state := OcmState.S_WORKING } } // shoot 대신 In-band tag 사용
    is(OcmState.S_WORKING) {
      when(io.fusion_req_in) {
        read_ptr := read_ptr + 1.U
        when(read_ptr === OcmConst.BANK_MAX_READS) {
          when(pong_valid) {
            active_bank := ~active_bank
            read_ptr := 0.U; pong_valid := false.B
          }.otherwise { state := OcmState.S_STALL }
        }
      }
    }
    is(OcmState.S_STALL) {
      when(pong_valid) {
        state := OcmState.S_WORKING
        active_bank := ~active_bank
        read_ptr := 0.U; pong_valid := false.B
      }
    }
  }

  when(io.ctrl.dma_data_last) { pong_valid := true.B }
  when(io.ctrl.soft_reset) { state := OcmState.S_IDLE; pong_valid := false.B }

  val active_bank_cnt = OcmConst.BANK_MAX_READS - read_ptr

  io.ctrl.working   := is_working
  io.ctrl.ready     := (state === OcmState.S_READY) || !io.ctrl.enable
  io.ctrl.hungry    := (state === OcmState.S_WORKING || state === OcmState.S_PRELOAD) && !pong_valid
  io.ctrl.impending := is_working && (active_bank_cnt <= OcmConst.WATERMARK) && !pong_valid

  io.rd_addr     := read_ptr
  // 1-Cycle Lookahead: 외부에서 fusion_req_in을 찌르면 즉시 rd_en 활성화
  io.rd_en       := is_working && io.fusion_req_in
  io.active_bank := active_bank
}