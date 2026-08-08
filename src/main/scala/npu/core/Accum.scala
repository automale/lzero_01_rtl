package npu.core
import chisel3._
import chisel3.util._

// [1] Accumulation Shift Buffer for Tile Accumulation
class Accum_buffer(
  val numLines: Int, // 16
  val inBits: Int,   // 16
  val outBits: Int   // 32
) extends Module {
  val io = IO(new Bundle{
    val in_scalar   = Input(UInt(inBits.W))
    val accum_en    = Input(Bool()) 
    val first_tile  = Input(Bool()) 
    val snapshot    = Input(Bool()) 
    
    val stall       = Input(Bool()) 
    
    val out_pong_array = Output(Vec(numLines, UInt(outBits.W)))
    val accum_alert    = Output(Bool()) 
  })

  val run = !io.stall

  val ping_reg = RegInit(VecInit(Seq.fill(numLines)(0.U(outBits.W))))
  val pong_reg = RegInit(VecInit(Seq.fill(numLines)(0.U(outBits.W))))

  // 새 값이 reg(15)로 들어가므로, 이전 누적값은 reg(0)에 도착해 있음
  val addend           = Mux(io.first_tile, 0.U(outBits.W), ping_reg(0))
  val current_sum_full = addend +& io.in_scalar
  val current_sum      = current_sum_full(outBits - 1, 0)
  val overflow         = current_sum_full(outBits)

  when (run) {
    // =======================================================
    // [PING]: 새 값을 reg(15)에 넣고, reg(0) 방향으로 시프트
    // 16사이클 뒤: reg(0) = Row 0 ... reg(15) = Row 15
    // =======================================================
    when (io.accum_en) {
      ping_reg(numLines - 1) := current_sum
      for (i <- 0 until numLines - 1) {
        ping_reg(i) := ping_reg(i + 1)
      }
    }

    // =======================================================
    // [PONG]: 정렬된 상태 그대로 스냅샷 복사
    // =======================================================
    when (io.snapshot && io.accum_en) {
      pong_reg(numLines - 1) := current_sum
      for (i <- 0 until numLines - 1) {
        pong_reg(i) := ping_reg(i + 1)
      }
    }
  }
  
  io.out_pong_array := pong_reg
  io.accum_alert := io.accum_en && overflow && run
}

// [2] 16x16 2D 타일 누산기 (Ping-Pong & De-skew 기능 탑재)
class TileAccumulator(
  val numCols: Int,  // 16
  val numLines: Int, // 16
  val inBits: Int,   // 16
  val outBits: Int   // 32
) extends Module {
  val io = IO(new Bundle {
    // 1. TPU MXU vector input (Skewed)
    val in_vec          = Input(Vec(numCols, UInt(inBits.W))) 
    
    // 2. Skewed Control Signals (FSM에서 0번 Col에 인가)
    val accum_en        = Input(Bool()) // 누적 진행
    val accum_first     = Input(Bool()) // K-차원 첫 타일 여부
    val accum_snapshot  = Input(Bool()) // 타일 완료 스냅샷 펄스
    
    // 3. Flat Control Signal (VPU 배출용 브로드캐스트)
    val accum_stream_en = Input(Bool()) // 16사이클 동안 1 유지
    
    val stall           = Input(Bool()) 

    // 4. Output for VPU (Flat & Row-Major)
    val out_vec         = Output(Vec(numCols, UInt(outBits.W)))
    val out_valid       = Output(Vec(numCols, Bool())) 
    
    val accum_alert     = Output(Bool())
  })

  val run = !io.stall
  val accum_buffer = Seq.fill(numCols)(Module(new Accum_buffer(
    numLines = numLines, 
    inBits = inBits, 
    outBits = outBits)))

  // ====================================================================
  // [1] Skewed Delay Chains (TPU 출력 타이밍 동기화)
  // ====================================================================
  val en_delay       = RegInit(VecInit(Seq.fill(numCols)(false.B))) 
  val first_delay    = RegInit(VecInit(Seq.fill(numCols)(false.B))) 
  val snapshot_delay = RegInit(VecInit(Seq.fill(numCols)(false.B))) 

  when (run) {
    en_delay(0)       := io.accum_en
    first_delay(0)    := io.accum_first
    snapshot_delay(0) := io.accum_snapshot
    
    for (c <- 1 until numCols) {
      en_delay(c)       := en_delay(c-1)
      first_delay(c)    := first_delay(c-1)
      snapshot_delay(c) := snapshot_delay(c-1)
    }
  }

  // ====================================================================
  // [2] Accumulator 입력 맵핑
  // ====================================================================
  for(c <- 0 until numCols) {
    accum_buffer(c).io.in_scalar  := io.in_vec(c)
    accum_buffer(c).io.accum_en   := en_delay(c)
    accum_buffer(c).io.first_tile := first_delay(c)
    accum_buffer(c).io.snapshot   := snapshot_delay(c)
    accum_buffer(c).io.stall      := io.stall
  }

  // ====================================================================
  // [3] Corner-Turning / De-skewing MUX 로직
  // ====================================================================
  // stream_en이 켜지면 0번 Row부터 15번 Row까지 순차적으로 인덱스를 증가시킴
  val read_ptr = RegInit(0.U(log2Ceil(numLines).W))
  
  when (run) {
    when (io.accum_stream_en) {
      read_ptr := read_ptr + 1.U
    } .otherwise {
      read_ptr := 0.U
    }
  }

  // 16개 버퍼 배열에서 동일한 read_ptr 층(Row)을 횡단하며 긁어옴
  for (c <- 0 until numCols) {
    io.out_vec(c)   := Mux(io.accum_stream_en && run, accum_buffer(c).io.out_pong_array(read_ptr), 0.U)
    io.out_valid(c) := io.accum_stream_en && run
  }

  // ====================================================================
  // [4] 에러 신호 통합
  // ====================================================================
  val all_alerts = accum_buffer.map(_.io.accum_alert)
  io.accum_alert := VecInit(all_alerts).asUInt.orR
}