import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random // 스칼라의 난수 생성기

// 하드웨어 테스트를 위한 기본 클래스 상속
class MacUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MacUnit"

  // 테스트 케이스 시작
  it should "correctly load weight, multiply, and pass input/partial sum" in {
    // 칩(MacUnit)을 시뮬레이터 위에 올립니다. (dut: Device Under Test의 약자)
    test(new MacUnit()) { dut =>
      
      // ----------------------------------------------------
      // [1단계] 초기화: 남아있을지 모르는 쓰레기값을 0으로 밉니다.
      // ----------------------------------------------------
      dut.io.clear_w.poke(true.B)  // clear_w 핀에 전기(1)를 찌릅니다.
      dut.clock.step(1)            // 클럭을 1번 "딱!" 뛰게 합니다. (이때 weight가 0으로 변함)
      dut.io.clear_w.poke(false.B) // clear_w 핀의 전기를 끕니다.

      // ----------------------------------------------------
      // [2단계] Weight 장전: Weight 레지스터에 5를 넣습니다.
      // ----------------------------------------------------
      dut.io.set_w.poke(true.B)
      dut.io.in_w.poke(5.U)
      dut.clock.step(1)            // 클럭이 뛰는 순간, weight 레지스터에 5가 들어갑니다.
      dut.io.set_w.poke(false.B)   // 장전 끝

      // ----------------------------------------------------
      // [3단계] 연산 테스트 1 (클럭 없이 즉시 계산되는 조합 로직)
      // ----------------------------------------------------
      // 입력: A = 2, 위에서 내려오는 부분합 C = 10
      dut.io.in_a.poke(2.U)
      dut.io.in_c.poke(10.U)
      dut.clock.step(1)

      // 예상: (A * W) + C = (2 * 5) + 10 = 20
      // MAC 연산과 in_a 통과 로직은 레지스터(순차 로직)를 거치지 않는 '조합 로직'이므로, 
      // 클럭(step)을 뛰게 하지 않아도 poke 하자마자 전기가 흘러서 바로 결과를 알 수 있습니다.
      dut.io.out_mac.expect(20.U) 
      dut.io.out_in.expect(2.U)   // A값(2)이 오른쪽으로 잘 넘어가는지도 확인

      dut.clock.step(1) // 다음 테스트를 위해 1클럭 넘김

      // ----------------------------------------------------
      // [4단계] 연산 테스트 2
      // ----------------------------------------------------
      // 입력: A = 3, 위에서 내려오는 부분합 C = 0
      dut.io.in_a.poke(3.U)
      dut.io.in_c.poke(0.U)
      dut.clock.step(1)

      // 예상: (3 * 5) + 0 = 15 (Weight 5는 계속 유지되어야 함)
      dut.io.out_mac.expect(15.U)
      dut.io.out_in.expect(3.U)
    }
  }
}

class MatMulUnit_16Test extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MatMulUnit_16"
  // test case start
  it should "correctly (1)load 256 weights at once, (2)multiply two matrix successfully" in {
    test(new MatMulUnit_16()){ dut =>

      val sw_weight       = Array.tabulate(16, 16) { (_, _) => Random.nextInt(10) }
      val sw_input_temp   = Array.tabulate(16, 16) { (_, _) => Random.nextInt(10) }
      val sw_input        = Array.fill(31, 16)(0) 
      // input data skew setting
      for (c <- 0 until 16){
        for (r <- 0 until 16){
          sw_input(r+c)(c) = sw_input_temp(r)(c)
        }
      }
      // expected output caculation
      val expected_output = Array.fill(31, 16)(0)
      for (r <- 0 until 16) {
        for (c <- 0 until 16) {
          for (k <- 0 until 16){
            expected_output(r+c)(c) += sw_input_temp(r)(k) * sw_weight(k)(c)
          }
        }
      }

      // initialize possible non zero weight value in reg to 0
      {
        dut.io.clear_W.poke(true.B)
        dut.clock.step(1)
        dut.io.clear_W.poke(false.B)
      }

      // weight load
      {
        for (r <- 0 until 16){
          for (c <- 0 until 16){
            dut.io.in_W(r)(c).poke(sw_weight(r)(c).U)
          }
        }
        dut.io.set_W.poke(true.B)
        dut.clock.step(1)
        dut.io.set_W.poke(false.B)
      }

      // mat A skewed input
      val total_cycles = 50
      val actual_output = Array.fill(total_cycles, 16)(0)
      val latency = 16
      for ( t <- 0 until total_cycles ){
        // feeding input
        for ( c <- 0 until 16 ){
          val val_in = if (t < 31) sw_input(t)(c) else 0
          dut.io.in_A(c).poke(val_in.U)
        }
        // validating
        for ( c <- 0 until 16 ){
          if (t >= latency && t < latency + 31) {
            val time_index = t - latency
            dut.io.out_MAC(c).expect( expected_output(time_index)(c).U )
            actual_output(time_index)(c) = dut.io.out_MAC(c).peek().litValue.toInt
          } 
          else {
            // 유효한 데이터가 안 나올 타이밍(Bubble)에는 0이 나오는지 깐깐하게 검사합니다.
            dut.io.out_MAC(c).expect(0.U)
          }
        }
        dut.clock.step(1)
      }

      // print out skewed matrix
      println("===== Skewed Output =====")
      for ( t <- 0 until 31 ) {
        // 결과가 0이 아닐 때만 출력해 보면, 대각선으로 데이터가 흘러나오는 장관을 볼 수 있습니다.
        val row_data = actual_output(t)  
        if (row_data.exists(_ != 0)) {
          val str = row_data.mkString("\t")
          val clock_ = t + latency
          println(s"Clock $clock_ : \t $str")
        }
      }

    }
  }
}