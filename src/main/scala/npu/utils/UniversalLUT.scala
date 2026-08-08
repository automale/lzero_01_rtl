package npu.utils

import chisel3._
import chisel3.util._

class Universal_Wide_LUT(
  val indexBits: Int, // ex) 10 (1024-Entry)
  val dataBits: Int,  // ex) 8 (8-bit)
  val writeBits: Int  // ex) 256 (32-Byte Wide Bus)
) extends Module {
  
  require(writeBits > dataBits, "Write width must be strictly greater than Data width for this architecture.")

  val numEntries = 1 << indexBits
  val wordsPerBurst = writeBits / dataBits 
  
  val burstAddrBits = log2Ceil(numEntries / wordsPerBurst)
  val colAddrBits   = log2Ceil(wordsPerBurst) // Muxing을 위한 하위 비트 (예: 32개면 5bit)

  val io = IO(new Bundle {
    // 1. 광대역 Write Port
    val wr_en     = Input(Bool())
    val wr_addr   = Input(UInt(burstAddrBits.W)) 
    val wr_data   = Input(Vec(wordsPerBurst, UInt(dataBits.W)))
    val wr_valid  = Output(Bool()) // Write ACK (저장 완료 응답)

    // 2. 실시간 Read Port
    val rd_en     = Input(Bool())
    val rd_addr   = Input(UInt(indexBits.W))
    val rd_data   = Output(UInt(dataBits.W))
    val rd_valid  = Output(Bool()) // Read 데이터 유효 신호

    // 3. Orchestrator를 위한 상태 신호
    val lut_ready = Output(Bool()) // LUT 초기화 완료 여부
  })

  // ====================================================================
  // Wide-Write, Narrow-Read를 지원하는 비대칭 BRAM 구성
  // FPGA BRAM 매핑 'SyncReadMem' 사용 (1 Cycle Delay)
  // ====================================================================
  val lut_mem = SyncReadMem(numEntries / wordsPerBurst, Vec(wordsPerBurst, UInt(dataBits.W)))

  // -----------------------------------------------------
  // Write Logic (1 Cycle, 1 Port 가동)
  // -----------------------------------------------------
  when (io.wr_en) {
    // for문 없이 한 번의 write로 256-bit (Vec) 전체를 하나의 Row에 통째로 씁니다.
    lut_mem.write(io.wr_addr, io.wr_data)
  }
  // Write가 발생한 다음 클럭에 ACK 신호 발송
  io.wr_valid := RegNext(io.wr_en, false.B)

  // -----------------------------------------------------
  // LUT Programming Complete Flag (상태 레지스터)
  // -----------------------------------------------------
  val max_burst_addr = ((numEntries / wordsPerBurst) - 1).U
  val is_programmed  = RegInit(false.B)
  val last_write_ack = RegNext(io.wr_en && (io.wr_addr === max_burst_addr), false.B)

  when (last_write_ack) {
    is_programmed := true.B
  }
  
  // 상태 레지스터 자체를 출력하므로, 한 번 1이 되면 초기화 전까지 영구히 유지됨
  io.lut_ready := is_programmed

  // -----------------------------------------------------
  // Read Logic (1-Cycle Delay BRAM Read + Column Muxing)
  // -----------------------------------------------------
  // 입력된 주소를 Row(BRAM 주소)와 Column(Vec 인덱스)으로 쪼갬
  val rd_row = io.rd_addr(indexBits - 1, colAddrBits) 
  val rd_col = io.rd_addr(colAddrBits - 1, 0)         

  // 1. BRAM에서 해당 Row 전체(256-bit)를 읽어옴 (1클럭 딜레이 발생)
  val rd_vec = lut_mem.read(rd_row, io.rd_en)
  
  // 2. 1클럭 뒤에 튀어나온 데이터와 Muxing을 맞추기 위해, Column 인덱스도 1클럭 지연시킴
  val rd_col_delayed = RegEnable(rd_col, io.rd_en)

  // 3. 1클럭 뒤, 32개의 단어 중 정확한 1개의 단어(8-bit)를 선택하여 출력
  io.rd_data  := rd_vec(rd_col_delayed)
  
  // 4. Read Valid는 rd_en이 들어온 다음 클럭에 High가 됨
  io.rd_valid := RegNext(io.rd_en, false.B)
}
