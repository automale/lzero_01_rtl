FIRRTL version 1.2.0
circuit MatMulUnit_16 :
  module MacUnit : @[src/main/scala/MatMul.scala 7:7]
    input clock : Clock @[src/main/scala/MatMul.scala 7:7]
    input reset : UInt<1> @[src/main/scala/MatMul.scala 7:7]
    input io_in_a : UInt<8> @[src/main/scala/MatMul.scala 8:14]
    input io_in_c : UInt<16> @[src/main/scala/MatMul.scala 8:14]
    input io_set_w : UInt<1> @[src/main/scala/MatMul.scala 8:14]
    input io_clear_w : UInt<1> @[src/main/scala/MatMul.scala 8:14]
    input io_in_w : UInt<8> @[src/main/scala/MatMul.scala 8:14]
    output io_out_in : UInt<8> @[src/main/scala/MatMul.scala 8:14]
    output io_out_mac : UInt<16> @[src/main/scala/MatMul.scala 8:14]

    reg weight : UInt<8>, clock with :
      reset => (UInt<1>("h0"), weight) @[src/main/scala/MatMul.scala 20:23]
    node _GEN_0 = mux(io_set_w, io_in_w, weight) @[src/main/scala/MatMul.scala 24:26 25:12 20:23]
    node _GEN_1 = mux(io_clear_w, UInt<1>("h0"), _GEN_0) @[src/main/scala/MatMul.scala 22:21 23:12]
    node _io_out_mac_T = mul(io_in_a, weight) @[src/main/scala/MatMul.scala 28:35]
    node _io_out_mac_T_1 = add(_io_out_mac_T, io_in_c) @[src/main/scala/MatMul.scala 28:45]
    node _io_out_mac_T_2 = tail(_io_out_mac_T_1, 1) @[src/main/scala/MatMul.scala 28:45]
    reg io_out_mac_REG : UInt<16>, clock with :
      reset => (UInt<1>("h0"), io_out_mac_REG) @[src/main/scala/MatMul.scala 28:25]
    reg io_out_in_REG : UInt<8>, clock with :
      reset => (UInt<1>("h0"), io_out_in_REG) @[src/main/scala/MatMul.scala 29:25]
    io_out_in <= io_out_in_REG @[src/main/scala/MatMul.scala 29:15]
    io_out_mac <= io_out_mac_REG @[src/main/scala/MatMul.scala 28:15]
    weight <= mux(reset, UInt<8>("h0"), _GEN_1) @[src/main/scala/MatMul.scala 20:{23,23}]
    io_out_mac_REG <= _io_out_mac_T_2 @[src/main/scala/MatMul.scala 28:25]
    io_out_in_REG <= io_in_a @[src/main/scala/MatMul.scala 29:25]

  module MatMulUnit_16 : @[src/main/scala/MatMul.scala 33:7]
    input clock : Clock @[src/main/scala/MatMul.scala 33:7]
    input reset : UInt<1> @[src/main/scala/MatMul.scala 33:7]
    input io_in_A_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_A_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_set_W : UInt<1> @[src/main/scala/MatMul.scala 35:14]
    input io_clear_W : UInt<1> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_0_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_1_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_2_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_3_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_4_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_5_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_6_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_7_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_8_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_9_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_10_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_11_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_12_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_13_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_14_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_0 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_1 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_2 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_3 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_4 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_5 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_6 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_7 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_8 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_9 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_10 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_11 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_12 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_13 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_14 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    input io_in_W_15_15 : UInt<8> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_0 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_1 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_2 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_3 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_4 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_5 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_6 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_7 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_8 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_9 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_10 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_11 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_12 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_13 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_14 : UInt<16> @[src/main/scala/MatMul.scala 35:14]
    output io_out_MAC_15 : UInt<16> @[src/main/scala/MatMul.scala 35:14]

    inst macs_0_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_0_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_1_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_2_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_3_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_4_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_5_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_6_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_7_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_8_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_9_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_10_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_11_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_12_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_13_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_14_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_0 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_1 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_2 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_3 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_4 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_5 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_6 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_7 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_8 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_9 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_10 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_11 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_12 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_13 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_14 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    inst macs_15_15 of MacUnit @[src/main/scala/MatMul.scala 45:37]
    io_out_MAC_0 <= macs_15_0.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_1 <= macs_15_1.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_2 <= macs_15_2.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_3 <= macs_15_3.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_4 <= macs_15_4.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_5 <= macs_15_5.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_6 <= macs_15_6.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_7 <= macs_15_7.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_8 <= macs_15_8.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_9 <= macs_15_9.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_10 <= macs_15_10.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_11 <= macs_15_11.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_12 <= macs_15_12.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_13 <= macs_15_13.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_14 <= macs_15_14.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    io_out_MAC_15 <= macs_15_15.io_out_mac @[src/main/scala/MatMul.scala 76:25]
    macs_0_0.clock <= clock
    macs_0_0.reset <= reset
    macs_0_0.io_in_a <= io_in_A_0 @[src/main/scala/MatMul.scala 65:40]
    macs_0_0.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_0.io_in_w <= io_in_W_0_0 @[src/main/scala/MatMul.scala 50:27]
    macs_0_1.clock <= clock
    macs_0_1.reset <= reset
    macs_0_1.io_in_a <= macs_0_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_1.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_1.io_in_w <= io_in_W_0_1 @[src/main/scala/MatMul.scala 50:27]
    macs_0_2.clock <= clock
    macs_0_2.reset <= reset
    macs_0_2.io_in_a <= macs_0_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_2.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_2.io_in_w <= io_in_W_0_2 @[src/main/scala/MatMul.scala 50:27]
    macs_0_3.clock <= clock
    macs_0_3.reset <= reset
    macs_0_3.io_in_a <= macs_0_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_3.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_3.io_in_w <= io_in_W_0_3 @[src/main/scala/MatMul.scala 50:27]
    macs_0_4.clock <= clock
    macs_0_4.reset <= reset
    macs_0_4.io_in_a <= macs_0_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_4.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_4.io_in_w <= io_in_W_0_4 @[src/main/scala/MatMul.scala 50:27]
    macs_0_5.clock <= clock
    macs_0_5.reset <= reset
    macs_0_5.io_in_a <= macs_0_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_5.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_5.io_in_w <= io_in_W_0_5 @[src/main/scala/MatMul.scala 50:27]
    macs_0_6.clock <= clock
    macs_0_6.reset <= reset
    macs_0_6.io_in_a <= macs_0_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_6.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_6.io_in_w <= io_in_W_0_6 @[src/main/scala/MatMul.scala 50:27]
    macs_0_7.clock <= clock
    macs_0_7.reset <= reset
    macs_0_7.io_in_a <= macs_0_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_7.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_7.io_in_w <= io_in_W_0_7 @[src/main/scala/MatMul.scala 50:27]
    macs_0_8.clock <= clock
    macs_0_8.reset <= reset
    macs_0_8.io_in_a <= macs_0_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_8.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_8.io_in_w <= io_in_W_0_8 @[src/main/scala/MatMul.scala 50:27]
    macs_0_9.clock <= clock
    macs_0_9.reset <= reset
    macs_0_9.io_in_a <= macs_0_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_9.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_9.io_in_w <= io_in_W_0_9 @[src/main/scala/MatMul.scala 50:27]
    macs_0_10.clock <= clock
    macs_0_10.reset <= reset
    macs_0_10.io_in_a <= macs_0_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_10.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_10.io_in_w <= io_in_W_0_10 @[src/main/scala/MatMul.scala 50:27]
    macs_0_11.clock <= clock
    macs_0_11.reset <= reset
    macs_0_11.io_in_a <= macs_0_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_11.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_11.io_in_w <= io_in_W_0_11 @[src/main/scala/MatMul.scala 50:27]
    macs_0_12.clock <= clock
    macs_0_12.reset <= reset
    macs_0_12.io_in_a <= macs_0_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_12.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_12.io_in_w <= io_in_W_0_12 @[src/main/scala/MatMul.scala 50:27]
    macs_0_13.clock <= clock
    macs_0_13.reset <= reset
    macs_0_13.io_in_a <= macs_0_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_13.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_13.io_in_w <= io_in_W_0_13 @[src/main/scala/MatMul.scala 50:27]
    macs_0_14.clock <= clock
    macs_0_14.reset <= reset
    macs_0_14.io_in_a <= macs_0_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_14.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_14.io_in_w <= io_in_W_0_14 @[src/main/scala/MatMul.scala 50:27]
    macs_0_15.clock <= clock
    macs_0_15.reset <= reset
    macs_0_15.io_in_a <= macs_0_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_0_15.io_in_c <= UInt<16>("h0") @[src/main/scala/MatMul.scala 75:25]
    macs_0_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_0_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_0_15.io_in_w <= io_in_W_0_15 @[src/main/scala/MatMul.scala 50:27]
    macs_1_0.clock <= clock
    macs_1_0.reset <= reset
    macs_1_0.io_in_a <= io_in_A_1 @[src/main/scala/MatMul.scala 65:40]
    macs_1_0.io_in_c <= macs_0_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_0.io_in_w <= io_in_W_1_0 @[src/main/scala/MatMul.scala 50:27]
    macs_1_1.clock <= clock
    macs_1_1.reset <= reset
    macs_1_1.io_in_a <= macs_1_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_1.io_in_c <= macs_0_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_1.io_in_w <= io_in_W_1_1 @[src/main/scala/MatMul.scala 50:27]
    macs_1_2.clock <= clock
    macs_1_2.reset <= reset
    macs_1_2.io_in_a <= macs_1_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_2.io_in_c <= macs_0_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_2.io_in_w <= io_in_W_1_2 @[src/main/scala/MatMul.scala 50:27]
    macs_1_3.clock <= clock
    macs_1_3.reset <= reset
    macs_1_3.io_in_a <= macs_1_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_3.io_in_c <= macs_0_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_3.io_in_w <= io_in_W_1_3 @[src/main/scala/MatMul.scala 50:27]
    macs_1_4.clock <= clock
    macs_1_4.reset <= reset
    macs_1_4.io_in_a <= macs_1_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_4.io_in_c <= macs_0_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_4.io_in_w <= io_in_W_1_4 @[src/main/scala/MatMul.scala 50:27]
    macs_1_5.clock <= clock
    macs_1_5.reset <= reset
    macs_1_5.io_in_a <= macs_1_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_5.io_in_c <= macs_0_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_5.io_in_w <= io_in_W_1_5 @[src/main/scala/MatMul.scala 50:27]
    macs_1_6.clock <= clock
    macs_1_6.reset <= reset
    macs_1_6.io_in_a <= macs_1_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_6.io_in_c <= macs_0_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_6.io_in_w <= io_in_W_1_6 @[src/main/scala/MatMul.scala 50:27]
    macs_1_7.clock <= clock
    macs_1_7.reset <= reset
    macs_1_7.io_in_a <= macs_1_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_7.io_in_c <= macs_0_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_7.io_in_w <= io_in_W_1_7 @[src/main/scala/MatMul.scala 50:27]
    macs_1_8.clock <= clock
    macs_1_8.reset <= reset
    macs_1_8.io_in_a <= macs_1_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_8.io_in_c <= macs_0_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_8.io_in_w <= io_in_W_1_8 @[src/main/scala/MatMul.scala 50:27]
    macs_1_9.clock <= clock
    macs_1_9.reset <= reset
    macs_1_9.io_in_a <= macs_1_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_9.io_in_c <= macs_0_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_9.io_in_w <= io_in_W_1_9 @[src/main/scala/MatMul.scala 50:27]
    macs_1_10.clock <= clock
    macs_1_10.reset <= reset
    macs_1_10.io_in_a <= macs_1_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_10.io_in_c <= macs_0_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_10.io_in_w <= io_in_W_1_10 @[src/main/scala/MatMul.scala 50:27]
    macs_1_11.clock <= clock
    macs_1_11.reset <= reset
    macs_1_11.io_in_a <= macs_1_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_11.io_in_c <= macs_0_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_11.io_in_w <= io_in_W_1_11 @[src/main/scala/MatMul.scala 50:27]
    macs_1_12.clock <= clock
    macs_1_12.reset <= reset
    macs_1_12.io_in_a <= macs_1_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_12.io_in_c <= macs_0_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_12.io_in_w <= io_in_W_1_12 @[src/main/scala/MatMul.scala 50:27]
    macs_1_13.clock <= clock
    macs_1_13.reset <= reset
    macs_1_13.io_in_a <= macs_1_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_13.io_in_c <= macs_0_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_13.io_in_w <= io_in_W_1_13 @[src/main/scala/MatMul.scala 50:27]
    macs_1_14.clock <= clock
    macs_1_14.reset <= reset
    macs_1_14.io_in_a <= macs_1_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_14.io_in_c <= macs_0_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_14.io_in_w <= io_in_W_1_14 @[src/main/scala/MatMul.scala 50:27]
    macs_1_15.clock <= clock
    macs_1_15.reset <= reset
    macs_1_15.io_in_a <= macs_1_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_1_15.io_in_c <= macs_0_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_1_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_1_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_1_15.io_in_w <= io_in_W_1_15 @[src/main/scala/MatMul.scala 50:27]
    macs_2_0.clock <= clock
    macs_2_0.reset <= reset
    macs_2_0.io_in_a <= io_in_A_2 @[src/main/scala/MatMul.scala 65:40]
    macs_2_0.io_in_c <= macs_1_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_0.io_in_w <= io_in_W_2_0 @[src/main/scala/MatMul.scala 50:27]
    macs_2_1.clock <= clock
    macs_2_1.reset <= reset
    macs_2_1.io_in_a <= macs_2_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_1.io_in_c <= macs_1_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_1.io_in_w <= io_in_W_2_1 @[src/main/scala/MatMul.scala 50:27]
    macs_2_2.clock <= clock
    macs_2_2.reset <= reset
    macs_2_2.io_in_a <= macs_2_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_2.io_in_c <= macs_1_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_2.io_in_w <= io_in_W_2_2 @[src/main/scala/MatMul.scala 50:27]
    macs_2_3.clock <= clock
    macs_2_3.reset <= reset
    macs_2_3.io_in_a <= macs_2_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_3.io_in_c <= macs_1_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_3.io_in_w <= io_in_W_2_3 @[src/main/scala/MatMul.scala 50:27]
    macs_2_4.clock <= clock
    macs_2_4.reset <= reset
    macs_2_4.io_in_a <= macs_2_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_4.io_in_c <= macs_1_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_4.io_in_w <= io_in_W_2_4 @[src/main/scala/MatMul.scala 50:27]
    macs_2_5.clock <= clock
    macs_2_5.reset <= reset
    macs_2_5.io_in_a <= macs_2_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_5.io_in_c <= macs_1_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_5.io_in_w <= io_in_W_2_5 @[src/main/scala/MatMul.scala 50:27]
    macs_2_6.clock <= clock
    macs_2_6.reset <= reset
    macs_2_6.io_in_a <= macs_2_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_6.io_in_c <= macs_1_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_6.io_in_w <= io_in_W_2_6 @[src/main/scala/MatMul.scala 50:27]
    macs_2_7.clock <= clock
    macs_2_7.reset <= reset
    macs_2_7.io_in_a <= macs_2_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_7.io_in_c <= macs_1_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_7.io_in_w <= io_in_W_2_7 @[src/main/scala/MatMul.scala 50:27]
    macs_2_8.clock <= clock
    macs_2_8.reset <= reset
    macs_2_8.io_in_a <= macs_2_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_8.io_in_c <= macs_1_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_8.io_in_w <= io_in_W_2_8 @[src/main/scala/MatMul.scala 50:27]
    macs_2_9.clock <= clock
    macs_2_9.reset <= reset
    macs_2_9.io_in_a <= macs_2_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_9.io_in_c <= macs_1_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_9.io_in_w <= io_in_W_2_9 @[src/main/scala/MatMul.scala 50:27]
    macs_2_10.clock <= clock
    macs_2_10.reset <= reset
    macs_2_10.io_in_a <= macs_2_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_10.io_in_c <= macs_1_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_10.io_in_w <= io_in_W_2_10 @[src/main/scala/MatMul.scala 50:27]
    macs_2_11.clock <= clock
    macs_2_11.reset <= reset
    macs_2_11.io_in_a <= macs_2_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_11.io_in_c <= macs_1_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_11.io_in_w <= io_in_W_2_11 @[src/main/scala/MatMul.scala 50:27]
    macs_2_12.clock <= clock
    macs_2_12.reset <= reset
    macs_2_12.io_in_a <= macs_2_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_12.io_in_c <= macs_1_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_12.io_in_w <= io_in_W_2_12 @[src/main/scala/MatMul.scala 50:27]
    macs_2_13.clock <= clock
    macs_2_13.reset <= reset
    macs_2_13.io_in_a <= macs_2_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_13.io_in_c <= macs_1_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_13.io_in_w <= io_in_W_2_13 @[src/main/scala/MatMul.scala 50:27]
    macs_2_14.clock <= clock
    macs_2_14.reset <= reset
    macs_2_14.io_in_a <= macs_2_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_14.io_in_c <= macs_1_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_14.io_in_w <= io_in_W_2_14 @[src/main/scala/MatMul.scala 50:27]
    macs_2_15.clock <= clock
    macs_2_15.reset <= reset
    macs_2_15.io_in_a <= macs_2_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_2_15.io_in_c <= macs_1_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_2_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_2_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_2_15.io_in_w <= io_in_W_2_15 @[src/main/scala/MatMul.scala 50:27]
    macs_3_0.clock <= clock
    macs_3_0.reset <= reset
    macs_3_0.io_in_a <= io_in_A_3 @[src/main/scala/MatMul.scala 65:40]
    macs_3_0.io_in_c <= macs_2_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_0.io_in_w <= io_in_W_3_0 @[src/main/scala/MatMul.scala 50:27]
    macs_3_1.clock <= clock
    macs_3_1.reset <= reset
    macs_3_1.io_in_a <= macs_3_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_1.io_in_c <= macs_2_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_1.io_in_w <= io_in_W_3_1 @[src/main/scala/MatMul.scala 50:27]
    macs_3_2.clock <= clock
    macs_3_2.reset <= reset
    macs_3_2.io_in_a <= macs_3_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_2.io_in_c <= macs_2_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_2.io_in_w <= io_in_W_3_2 @[src/main/scala/MatMul.scala 50:27]
    macs_3_3.clock <= clock
    macs_3_3.reset <= reset
    macs_3_3.io_in_a <= macs_3_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_3.io_in_c <= macs_2_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_3.io_in_w <= io_in_W_3_3 @[src/main/scala/MatMul.scala 50:27]
    macs_3_4.clock <= clock
    macs_3_4.reset <= reset
    macs_3_4.io_in_a <= macs_3_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_4.io_in_c <= macs_2_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_4.io_in_w <= io_in_W_3_4 @[src/main/scala/MatMul.scala 50:27]
    macs_3_5.clock <= clock
    macs_3_5.reset <= reset
    macs_3_5.io_in_a <= macs_3_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_5.io_in_c <= macs_2_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_5.io_in_w <= io_in_W_3_5 @[src/main/scala/MatMul.scala 50:27]
    macs_3_6.clock <= clock
    macs_3_6.reset <= reset
    macs_3_6.io_in_a <= macs_3_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_6.io_in_c <= macs_2_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_6.io_in_w <= io_in_W_3_6 @[src/main/scala/MatMul.scala 50:27]
    macs_3_7.clock <= clock
    macs_3_7.reset <= reset
    macs_3_7.io_in_a <= macs_3_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_7.io_in_c <= macs_2_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_7.io_in_w <= io_in_W_3_7 @[src/main/scala/MatMul.scala 50:27]
    macs_3_8.clock <= clock
    macs_3_8.reset <= reset
    macs_3_8.io_in_a <= macs_3_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_8.io_in_c <= macs_2_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_8.io_in_w <= io_in_W_3_8 @[src/main/scala/MatMul.scala 50:27]
    macs_3_9.clock <= clock
    macs_3_9.reset <= reset
    macs_3_9.io_in_a <= macs_3_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_9.io_in_c <= macs_2_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_9.io_in_w <= io_in_W_3_9 @[src/main/scala/MatMul.scala 50:27]
    macs_3_10.clock <= clock
    macs_3_10.reset <= reset
    macs_3_10.io_in_a <= macs_3_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_10.io_in_c <= macs_2_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_10.io_in_w <= io_in_W_3_10 @[src/main/scala/MatMul.scala 50:27]
    macs_3_11.clock <= clock
    macs_3_11.reset <= reset
    macs_3_11.io_in_a <= macs_3_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_11.io_in_c <= macs_2_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_11.io_in_w <= io_in_W_3_11 @[src/main/scala/MatMul.scala 50:27]
    macs_3_12.clock <= clock
    macs_3_12.reset <= reset
    macs_3_12.io_in_a <= macs_3_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_12.io_in_c <= macs_2_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_12.io_in_w <= io_in_W_3_12 @[src/main/scala/MatMul.scala 50:27]
    macs_3_13.clock <= clock
    macs_3_13.reset <= reset
    macs_3_13.io_in_a <= macs_3_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_13.io_in_c <= macs_2_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_13.io_in_w <= io_in_W_3_13 @[src/main/scala/MatMul.scala 50:27]
    macs_3_14.clock <= clock
    macs_3_14.reset <= reset
    macs_3_14.io_in_a <= macs_3_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_14.io_in_c <= macs_2_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_14.io_in_w <= io_in_W_3_14 @[src/main/scala/MatMul.scala 50:27]
    macs_3_15.clock <= clock
    macs_3_15.reset <= reset
    macs_3_15.io_in_a <= macs_3_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_3_15.io_in_c <= macs_2_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_3_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_3_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_3_15.io_in_w <= io_in_W_3_15 @[src/main/scala/MatMul.scala 50:27]
    macs_4_0.clock <= clock
    macs_4_0.reset <= reset
    macs_4_0.io_in_a <= io_in_A_4 @[src/main/scala/MatMul.scala 65:40]
    macs_4_0.io_in_c <= macs_3_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_0.io_in_w <= io_in_W_4_0 @[src/main/scala/MatMul.scala 50:27]
    macs_4_1.clock <= clock
    macs_4_1.reset <= reset
    macs_4_1.io_in_a <= macs_4_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_1.io_in_c <= macs_3_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_1.io_in_w <= io_in_W_4_1 @[src/main/scala/MatMul.scala 50:27]
    macs_4_2.clock <= clock
    macs_4_2.reset <= reset
    macs_4_2.io_in_a <= macs_4_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_2.io_in_c <= macs_3_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_2.io_in_w <= io_in_W_4_2 @[src/main/scala/MatMul.scala 50:27]
    macs_4_3.clock <= clock
    macs_4_3.reset <= reset
    macs_4_3.io_in_a <= macs_4_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_3.io_in_c <= macs_3_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_3.io_in_w <= io_in_W_4_3 @[src/main/scala/MatMul.scala 50:27]
    macs_4_4.clock <= clock
    macs_4_4.reset <= reset
    macs_4_4.io_in_a <= macs_4_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_4.io_in_c <= macs_3_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_4.io_in_w <= io_in_W_4_4 @[src/main/scala/MatMul.scala 50:27]
    macs_4_5.clock <= clock
    macs_4_5.reset <= reset
    macs_4_5.io_in_a <= macs_4_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_5.io_in_c <= macs_3_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_5.io_in_w <= io_in_W_4_5 @[src/main/scala/MatMul.scala 50:27]
    macs_4_6.clock <= clock
    macs_4_6.reset <= reset
    macs_4_6.io_in_a <= macs_4_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_6.io_in_c <= macs_3_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_6.io_in_w <= io_in_W_4_6 @[src/main/scala/MatMul.scala 50:27]
    macs_4_7.clock <= clock
    macs_4_7.reset <= reset
    macs_4_7.io_in_a <= macs_4_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_7.io_in_c <= macs_3_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_7.io_in_w <= io_in_W_4_7 @[src/main/scala/MatMul.scala 50:27]
    macs_4_8.clock <= clock
    macs_4_8.reset <= reset
    macs_4_8.io_in_a <= macs_4_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_8.io_in_c <= macs_3_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_8.io_in_w <= io_in_W_4_8 @[src/main/scala/MatMul.scala 50:27]
    macs_4_9.clock <= clock
    macs_4_9.reset <= reset
    macs_4_9.io_in_a <= macs_4_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_9.io_in_c <= macs_3_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_9.io_in_w <= io_in_W_4_9 @[src/main/scala/MatMul.scala 50:27]
    macs_4_10.clock <= clock
    macs_4_10.reset <= reset
    macs_4_10.io_in_a <= macs_4_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_10.io_in_c <= macs_3_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_10.io_in_w <= io_in_W_4_10 @[src/main/scala/MatMul.scala 50:27]
    macs_4_11.clock <= clock
    macs_4_11.reset <= reset
    macs_4_11.io_in_a <= macs_4_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_11.io_in_c <= macs_3_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_11.io_in_w <= io_in_W_4_11 @[src/main/scala/MatMul.scala 50:27]
    macs_4_12.clock <= clock
    macs_4_12.reset <= reset
    macs_4_12.io_in_a <= macs_4_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_12.io_in_c <= macs_3_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_12.io_in_w <= io_in_W_4_12 @[src/main/scala/MatMul.scala 50:27]
    macs_4_13.clock <= clock
    macs_4_13.reset <= reset
    macs_4_13.io_in_a <= macs_4_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_13.io_in_c <= macs_3_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_13.io_in_w <= io_in_W_4_13 @[src/main/scala/MatMul.scala 50:27]
    macs_4_14.clock <= clock
    macs_4_14.reset <= reset
    macs_4_14.io_in_a <= macs_4_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_14.io_in_c <= macs_3_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_14.io_in_w <= io_in_W_4_14 @[src/main/scala/MatMul.scala 50:27]
    macs_4_15.clock <= clock
    macs_4_15.reset <= reset
    macs_4_15.io_in_a <= macs_4_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_4_15.io_in_c <= macs_3_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_4_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_4_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_4_15.io_in_w <= io_in_W_4_15 @[src/main/scala/MatMul.scala 50:27]
    macs_5_0.clock <= clock
    macs_5_0.reset <= reset
    macs_5_0.io_in_a <= io_in_A_5 @[src/main/scala/MatMul.scala 65:40]
    macs_5_0.io_in_c <= macs_4_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_0.io_in_w <= io_in_W_5_0 @[src/main/scala/MatMul.scala 50:27]
    macs_5_1.clock <= clock
    macs_5_1.reset <= reset
    macs_5_1.io_in_a <= macs_5_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_1.io_in_c <= macs_4_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_1.io_in_w <= io_in_W_5_1 @[src/main/scala/MatMul.scala 50:27]
    macs_5_2.clock <= clock
    macs_5_2.reset <= reset
    macs_5_2.io_in_a <= macs_5_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_2.io_in_c <= macs_4_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_2.io_in_w <= io_in_W_5_2 @[src/main/scala/MatMul.scala 50:27]
    macs_5_3.clock <= clock
    macs_5_3.reset <= reset
    macs_5_3.io_in_a <= macs_5_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_3.io_in_c <= macs_4_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_3.io_in_w <= io_in_W_5_3 @[src/main/scala/MatMul.scala 50:27]
    macs_5_4.clock <= clock
    macs_5_4.reset <= reset
    macs_5_4.io_in_a <= macs_5_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_4.io_in_c <= macs_4_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_4.io_in_w <= io_in_W_5_4 @[src/main/scala/MatMul.scala 50:27]
    macs_5_5.clock <= clock
    macs_5_5.reset <= reset
    macs_5_5.io_in_a <= macs_5_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_5.io_in_c <= macs_4_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_5.io_in_w <= io_in_W_5_5 @[src/main/scala/MatMul.scala 50:27]
    macs_5_6.clock <= clock
    macs_5_6.reset <= reset
    macs_5_6.io_in_a <= macs_5_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_6.io_in_c <= macs_4_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_6.io_in_w <= io_in_W_5_6 @[src/main/scala/MatMul.scala 50:27]
    macs_5_7.clock <= clock
    macs_5_7.reset <= reset
    macs_5_7.io_in_a <= macs_5_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_7.io_in_c <= macs_4_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_7.io_in_w <= io_in_W_5_7 @[src/main/scala/MatMul.scala 50:27]
    macs_5_8.clock <= clock
    macs_5_8.reset <= reset
    macs_5_8.io_in_a <= macs_5_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_8.io_in_c <= macs_4_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_8.io_in_w <= io_in_W_5_8 @[src/main/scala/MatMul.scala 50:27]
    macs_5_9.clock <= clock
    macs_5_9.reset <= reset
    macs_5_9.io_in_a <= macs_5_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_9.io_in_c <= macs_4_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_9.io_in_w <= io_in_W_5_9 @[src/main/scala/MatMul.scala 50:27]
    macs_5_10.clock <= clock
    macs_5_10.reset <= reset
    macs_5_10.io_in_a <= macs_5_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_10.io_in_c <= macs_4_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_10.io_in_w <= io_in_W_5_10 @[src/main/scala/MatMul.scala 50:27]
    macs_5_11.clock <= clock
    macs_5_11.reset <= reset
    macs_5_11.io_in_a <= macs_5_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_11.io_in_c <= macs_4_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_11.io_in_w <= io_in_W_5_11 @[src/main/scala/MatMul.scala 50:27]
    macs_5_12.clock <= clock
    macs_5_12.reset <= reset
    macs_5_12.io_in_a <= macs_5_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_12.io_in_c <= macs_4_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_12.io_in_w <= io_in_W_5_12 @[src/main/scala/MatMul.scala 50:27]
    macs_5_13.clock <= clock
    macs_5_13.reset <= reset
    macs_5_13.io_in_a <= macs_5_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_13.io_in_c <= macs_4_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_13.io_in_w <= io_in_W_5_13 @[src/main/scala/MatMul.scala 50:27]
    macs_5_14.clock <= clock
    macs_5_14.reset <= reset
    macs_5_14.io_in_a <= macs_5_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_14.io_in_c <= macs_4_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_14.io_in_w <= io_in_W_5_14 @[src/main/scala/MatMul.scala 50:27]
    macs_5_15.clock <= clock
    macs_5_15.reset <= reset
    macs_5_15.io_in_a <= macs_5_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_5_15.io_in_c <= macs_4_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_5_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_5_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_5_15.io_in_w <= io_in_W_5_15 @[src/main/scala/MatMul.scala 50:27]
    macs_6_0.clock <= clock
    macs_6_0.reset <= reset
    macs_6_0.io_in_a <= io_in_A_6 @[src/main/scala/MatMul.scala 65:40]
    macs_6_0.io_in_c <= macs_5_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_0.io_in_w <= io_in_W_6_0 @[src/main/scala/MatMul.scala 50:27]
    macs_6_1.clock <= clock
    macs_6_1.reset <= reset
    macs_6_1.io_in_a <= macs_6_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_1.io_in_c <= macs_5_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_1.io_in_w <= io_in_W_6_1 @[src/main/scala/MatMul.scala 50:27]
    macs_6_2.clock <= clock
    macs_6_2.reset <= reset
    macs_6_2.io_in_a <= macs_6_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_2.io_in_c <= macs_5_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_2.io_in_w <= io_in_W_6_2 @[src/main/scala/MatMul.scala 50:27]
    macs_6_3.clock <= clock
    macs_6_3.reset <= reset
    macs_6_3.io_in_a <= macs_6_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_3.io_in_c <= macs_5_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_3.io_in_w <= io_in_W_6_3 @[src/main/scala/MatMul.scala 50:27]
    macs_6_4.clock <= clock
    macs_6_4.reset <= reset
    macs_6_4.io_in_a <= macs_6_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_4.io_in_c <= macs_5_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_4.io_in_w <= io_in_W_6_4 @[src/main/scala/MatMul.scala 50:27]
    macs_6_5.clock <= clock
    macs_6_5.reset <= reset
    macs_6_5.io_in_a <= macs_6_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_5.io_in_c <= macs_5_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_5.io_in_w <= io_in_W_6_5 @[src/main/scala/MatMul.scala 50:27]
    macs_6_6.clock <= clock
    macs_6_6.reset <= reset
    macs_6_6.io_in_a <= macs_6_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_6.io_in_c <= macs_5_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_6.io_in_w <= io_in_W_6_6 @[src/main/scala/MatMul.scala 50:27]
    macs_6_7.clock <= clock
    macs_6_7.reset <= reset
    macs_6_7.io_in_a <= macs_6_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_7.io_in_c <= macs_5_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_7.io_in_w <= io_in_W_6_7 @[src/main/scala/MatMul.scala 50:27]
    macs_6_8.clock <= clock
    macs_6_8.reset <= reset
    macs_6_8.io_in_a <= macs_6_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_8.io_in_c <= macs_5_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_8.io_in_w <= io_in_W_6_8 @[src/main/scala/MatMul.scala 50:27]
    macs_6_9.clock <= clock
    macs_6_9.reset <= reset
    macs_6_9.io_in_a <= macs_6_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_9.io_in_c <= macs_5_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_9.io_in_w <= io_in_W_6_9 @[src/main/scala/MatMul.scala 50:27]
    macs_6_10.clock <= clock
    macs_6_10.reset <= reset
    macs_6_10.io_in_a <= macs_6_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_10.io_in_c <= macs_5_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_10.io_in_w <= io_in_W_6_10 @[src/main/scala/MatMul.scala 50:27]
    macs_6_11.clock <= clock
    macs_6_11.reset <= reset
    macs_6_11.io_in_a <= macs_6_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_11.io_in_c <= macs_5_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_11.io_in_w <= io_in_W_6_11 @[src/main/scala/MatMul.scala 50:27]
    macs_6_12.clock <= clock
    macs_6_12.reset <= reset
    macs_6_12.io_in_a <= macs_6_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_12.io_in_c <= macs_5_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_12.io_in_w <= io_in_W_6_12 @[src/main/scala/MatMul.scala 50:27]
    macs_6_13.clock <= clock
    macs_6_13.reset <= reset
    macs_6_13.io_in_a <= macs_6_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_13.io_in_c <= macs_5_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_13.io_in_w <= io_in_W_6_13 @[src/main/scala/MatMul.scala 50:27]
    macs_6_14.clock <= clock
    macs_6_14.reset <= reset
    macs_6_14.io_in_a <= macs_6_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_14.io_in_c <= macs_5_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_14.io_in_w <= io_in_W_6_14 @[src/main/scala/MatMul.scala 50:27]
    macs_6_15.clock <= clock
    macs_6_15.reset <= reset
    macs_6_15.io_in_a <= macs_6_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_6_15.io_in_c <= macs_5_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_6_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_6_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_6_15.io_in_w <= io_in_W_6_15 @[src/main/scala/MatMul.scala 50:27]
    macs_7_0.clock <= clock
    macs_7_0.reset <= reset
    macs_7_0.io_in_a <= io_in_A_7 @[src/main/scala/MatMul.scala 65:40]
    macs_7_0.io_in_c <= macs_6_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_0.io_in_w <= io_in_W_7_0 @[src/main/scala/MatMul.scala 50:27]
    macs_7_1.clock <= clock
    macs_7_1.reset <= reset
    macs_7_1.io_in_a <= macs_7_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_1.io_in_c <= macs_6_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_1.io_in_w <= io_in_W_7_1 @[src/main/scala/MatMul.scala 50:27]
    macs_7_2.clock <= clock
    macs_7_2.reset <= reset
    macs_7_2.io_in_a <= macs_7_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_2.io_in_c <= macs_6_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_2.io_in_w <= io_in_W_7_2 @[src/main/scala/MatMul.scala 50:27]
    macs_7_3.clock <= clock
    macs_7_3.reset <= reset
    macs_7_3.io_in_a <= macs_7_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_3.io_in_c <= macs_6_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_3.io_in_w <= io_in_W_7_3 @[src/main/scala/MatMul.scala 50:27]
    macs_7_4.clock <= clock
    macs_7_4.reset <= reset
    macs_7_4.io_in_a <= macs_7_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_4.io_in_c <= macs_6_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_4.io_in_w <= io_in_W_7_4 @[src/main/scala/MatMul.scala 50:27]
    macs_7_5.clock <= clock
    macs_7_5.reset <= reset
    macs_7_5.io_in_a <= macs_7_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_5.io_in_c <= macs_6_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_5.io_in_w <= io_in_W_7_5 @[src/main/scala/MatMul.scala 50:27]
    macs_7_6.clock <= clock
    macs_7_6.reset <= reset
    macs_7_6.io_in_a <= macs_7_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_6.io_in_c <= macs_6_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_6.io_in_w <= io_in_W_7_6 @[src/main/scala/MatMul.scala 50:27]
    macs_7_7.clock <= clock
    macs_7_7.reset <= reset
    macs_7_7.io_in_a <= macs_7_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_7.io_in_c <= macs_6_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_7.io_in_w <= io_in_W_7_7 @[src/main/scala/MatMul.scala 50:27]
    macs_7_8.clock <= clock
    macs_7_8.reset <= reset
    macs_7_8.io_in_a <= macs_7_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_8.io_in_c <= macs_6_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_8.io_in_w <= io_in_W_7_8 @[src/main/scala/MatMul.scala 50:27]
    macs_7_9.clock <= clock
    macs_7_9.reset <= reset
    macs_7_9.io_in_a <= macs_7_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_9.io_in_c <= macs_6_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_9.io_in_w <= io_in_W_7_9 @[src/main/scala/MatMul.scala 50:27]
    macs_7_10.clock <= clock
    macs_7_10.reset <= reset
    macs_7_10.io_in_a <= macs_7_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_10.io_in_c <= macs_6_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_10.io_in_w <= io_in_W_7_10 @[src/main/scala/MatMul.scala 50:27]
    macs_7_11.clock <= clock
    macs_7_11.reset <= reset
    macs_7_11.io_in_a <= macs_7_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_11.io_in_c <= macs_6_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_11.io_in_w <= io_in_W_7_11 @[src/main/scala/MatMul.scala 50:27]
    macs_7_12.clock <= clock
    macs_7_12.reset <= reset
    macs_7_12.io_in_a <= macs_7_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_12.io_in_c <= macs_6_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_12.io_in_w <= io_in_W_7_12 @[src/main/scala/MatMul.scala 50:27]
    macs_7_13.clock <= clock
    macs_7_13.reset <= reset
    macs_7_13.io_in_a <= macs_7_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_13.io_in_c <= macs_6_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_13.io_in_w <= io_in_W_7_13 @[src/main/scala/MatMul.scala 50:27]
    macs_7_14.clock <= clock
    macs_7_14.reset <= reset
    macs_7_14.io_in_a <= macs_7_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_14.io_in_c <= macs_6_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_14.io_in_w <= io_in_W_7_14 @[src/main/scala/MatMul.scala 50:27]
    macs_7_15.clock <= clock
    macs_7_15.reset <= reset
    macs_7_15.io_in_a <= macs_7_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_7_15.io_in_c <= macs_6_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_7_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_7_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_7_15.io_in_w <= io_in_W_7_15 @[src/main/scala/MatMul.scala 50:27]
    macs_8_0.clock <= clock
    macs_8_0.reset <= reset
    macs_8_0.io_in_a <= io_in_A_8 @[src/main/scala/MatMul.scala 65:40]
    macs_8_0.io_in_c <= macs_7_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_0.io_in_w <= io_in_W_8_0 @[src/main/scala/MatMul.scala 50:27]
    macs_8_1.clock <= clock
    macs_8_1.reset <= reset
    macs_8_1.io_in_a <= macs_8_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_1.io_in_c <= macs_7_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_1.io_in_w <= io_in_W_8_1 @[src/main/scala/MatMul.scala 50:27]
    macs_8_2.clock <= clock
    macs_8_2.reset <= reset
    macs_8_2.io_in_a <= macs_8_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_2.io_in_c <= macs_7_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_2.io_in_w <= io_in_W_8_2 @[src/main/scala/MatMul.scala 50:27]
    macs_8_3.clock <= clock
    macs_8_3.reset <= reset
    macs_8_3.io_in_a <= macs_8_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_3.io_in_c <= macs_7_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_3.io_in_w <= io_in_W_8_3 @[src/main/scala/MatMul.scala 50:27]
    macs_8_4.clock <= clock
    macs_8_4.reset <= reset
    macs_8_4.io_in_a <= macs_8_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_4.io_in_c <= macs_7_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_4.io_in_w <= io_in_W_8_4 @[src/main/scala/MatMul.scala 50:27]
    macs_8_5.clock <= clock
    macs_8_5.reset <= reset
    macs_8_5.io_in_a <= macs_8_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_5.io_in_c <= macs_7_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_5.io_in_w <= io_in_W_8_5 @[src/main/scala/MatMul.scala 50:27]
    macs_8_6.clock <= clock
    macs_8_6.reset <= reset
    macs_8_6.io_in_a <= macs_8_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_6.io_in_c <= macs_7_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_6.io_in_w <= io_in_W_8_6 @[src/main/scala/MatMul.scala 50:27]
    macs_8_7.clock <= clock
    macs_8_7.reset <= reset
    macs_8_7.io_in_a <= macs_8_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_7.io_in_c <= macs_7_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_7.io_in_w <= io_in_W_8_7 @[src/main/scala/MatMul.scala 50:27]
    macs_8_8.clock <= clock
    macs_8_8.reset <= reset
    macs_8_8.io_in_a <= macs_8_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_8.io_in_c <= macs_7_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_8.io_in_w <= io_in_W_8_8 @[src/main/scala/MatMul.scala 50:27]
    macs_8_9.clock <= clock
    macs_8_9.reset <= reset
    macs_8_9.io_in_a <= macs_8_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_9.io_in_c <= macs_7_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_9.io_in_w <= io_in_W_8_9 @[src/main/scala/MatMul.scala 50:27]
    macs_8_10.clock <= clock
    macs_8_10.reset <= reset
    macs_8_10.io_in_a <= macs_8_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_10.io_in_c <= macs_7_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_10.io_in_w <= io_in_W_8_10 @[src/main/scala/MatMul.scala 50:27]
    macs_8_11.clock <= clock
    macs_8_11.reset <= reset
    macs_8_11.io_in_a <= macs_8_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_11.io_in_c <= macs_7_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_11.io_in_w <= io_in_W_8_11 @[src/main/scala/MatMul.scala 50:27]
    macs_8_12.clock <= clock
    macs_8_12.reset <= reset
    macs_8_12.io_in_a <= macs_8_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_12.io_in_c <= macs_7_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_12.io_in_w <= io_in_W_8_12 @[src/main/scala/MatMul.scala 50:27]
    macs_8_13.clock <= clock
    macs_8_13.reset <= reset
    macs_8_13.io_in_a <= macs_8_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_13.io_in_c <= macs_7_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_13.io_in_w <= io_in_W_8_13 @[src/main/scala/MatMul.scala 50:27]
    macs_8_14.clock <= clock
    macs_8_14.reset <= reset
    macs_8_14.io_in_a <= macs_8_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_14.io_in_c <= macs_7_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_14.io_in_w <= io_in_W_8_14 @[src/main/scala/MatMul.scala 50:27]
    macs_8_15.clock <= clock
    macs_8_15.reset <= reset
    macs_8_15.io_in_a <= macs_8_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_8_15.io_in_c <= macs_7_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_8_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_8_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_8_15.io_in_w <= io_in_W_8_15 @[src/main/scala/MatMul.scala 50:27]
    macs_9_0.clock <= clock
    macs_9_0.reset <= reset
    macs_9_0.io_in_a <= io_in_A_9 @[src/main/scala/MatMul.scala 65:40]
    macs_9_0.io_in_c <= macs_8_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_0.io_in_w <= io_in_W_9_0 @[src/main/scala/MatMul.scala 50:27]
    macs_9_1.clock <= clock
    macs_9_1.reset <= reset
    macs_9_1.io_in_a <= macs_9_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_1.io_in_c <= macs_8_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_1.io_in_w <= io_in_W_9_1 @[src/main/scala/MatMul.scala 50:27]
    macs_9_2.clock <= clock
    macs_9_2.reset <= reset
    macs_9_2.io_in_a <= macs_9_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_2.io_in_c <= macs_8_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_2.io_in_w <= io_in_W_9_2 @[src/main/scala/MatMul.scala 50:27]
    macs_9_3.clock <= clock
    macs_9_3.reset <= reset
    macs_9_3.io_in_a <= macs_9_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_3.io_in_c <= macs_8_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_3.io_in_w <= io_in_W_9_3 @[src/main/scala/MatMul.scala 50:27]
    macs_9_4.clock <= clock
    macs_9_4.reset <= reset
    macs_9_4.io_in_a <= macs_9_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_4.io_in_c <= macs_8_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_4.io_in_w <= io_in_W_9_4 @[src/main/scala/MatMul.scala 50:27]
    macs_9_5.clock <= clock
    macs_9_5.reset <= reset
    macs_9_5.io_in_a <= macs_9_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_5.io_in_c <= macs_8_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_5.io_in_w <= io_in_W_9_5 @[src/main/scala/MatMul.scala 50:27]
    macs_9_6.clock <= clock
    macs_9_6.reset <= reset
    macs_9_6.io_in_a <= macs_9_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_6.io_in_c <= macs_8_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_6.io_in_w <= io_in_W_9_6 @[src/main/scala/MatMul.scala 50:27]
    macs_9_7.clock <= clock
    macs_9_7.reset <= reset
    macs_9_7.io_in_a <= macs_9_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_7.io_in_c <= macs_8_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_7.io_in_w <= io_in_W_9_7 @[src/main/scala/MatMul.scala 50:27]
    macs_9_8.clock <= clock
    macs_9_8.reset <= reset
    macs_9_8.io_in_a <= macs_9_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_8.io_in_c <= macs_8_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_8.io_in_w <= io_in_W_9_8 @[src/main/scala/MatMul.scala 50:27]
    macs_9_9.clock <= clock
    macs_9_9.reset <= reset
    macs_9_9.io_in_a <= macs_9_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_9.io_in_c <= macs_8_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_9.io_in_w <= io_in_W_9_9 @[src/main/scala/MatMul.scala 50:27]
    macs_9_10.clock <= clock
    macs_9_10.reset <= reset
    macs_9_10.io_in_a <= macs_9_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_10.io_in_c <= macs_8_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_10.io_in_w <= io_in_W_9_10 @[src/main/scala/MatMul.scala 50:27]
    macs_9_11.clock <= clock
    macs_9_11.reset <= reset
    macs_9_11.io_in_a <= macs_9_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_11.io_in_c <= macs_8_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_11.io_in_w <= io_in_W_9_11 @[src/main/scala/MatMul.scala 50:27]
    macs_9_12.clock <= clock
    macs_9_12.reset <= reset
    macs_9_12.io_in_a <= macs_9_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_12.io_in_c <= macs_8_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_12.io_in_w <= io_in_W_9_12 @[src/main/scala/MatMul.scala 50:27]
    macs_9_13.clock <= clock
    macs_9_13.reset <= reset
    macs_9_13.io_in_a <= macs_9_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_13.io_in_c <= macs_8_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_13.io_in_w <= io_in_W_9_13 @[src/main/scala/MatMul.scala 50:27]
    macs_9_14.clock <= clock
    macs_9_14.reset <= reset
    macs_9_14.io_in_a <= macs_9_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_14.io_in_c <= macs_8_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_14.io_in_w <= io_in_W_9_14 @[src/main/scala/MatMul.scala 50:27]
    macs_9_15.clock <= clock
    macs_9_15.reset <= reset
    macs_9_15.io_in_a <= macs_9_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_9_15.io_in_c <= macs_8_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_9_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_9_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_9_15.io_in_w <= io_in_W_9_15 @[src/main/scala/MatMul.scala 50:27]
    macs_10_0.clock <= clock
    macs_10_0.reset <= reset
    macs_10_0.io_in_a <= io_in_A_10 @[src/main/scala/MatMul.scala 65:40]
    macs_10_0.io_in_c <= macs_9_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_0.io_in_w <= io_in_W_10_0 @[src/main/scala/MatMul.scala 50:27]
    macs_10_1.clock <= clock
    macs_10_1.reset <= reset
    macs_10_1.io_in_a <= macs_10_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_1.io_in_c <= macs_9_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_1.io_in_w <= io_in_W_10_1 @[src/main/scala/MatMul.scala 50:27]
    macs_10_2.clock <= clock
    macs_10_2.reset <= reset
    macs_10_2.io_in_a <= macs_10_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_2.io_in_c <= macs_9_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_2.io_in_w <= io_in_W_10_2 @[src/main/scala/MatMul.scala 50:27]
    macs_10_3.clock <= clock
    macs_10_3.reset <= reset
    macs_10_3.io_in_a <= macs_10_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_3.io_in_c <= macs_9_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_3.io_in_w <= io_in_W_10_3 @[src/main/scala/MatMul.scala 50:27]
    macs_10_4.clock <= clock
    macs_10_4.reset <= reset
    macs_10_4.io_in_a <= macs_10_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_4.io_in_c <= macs_9_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_4.io_in_w <= io_in_W_10_4 @[src/main/scala/MatMul.scala 50:27]
    macs_10_5.clock <= clock
    macs_10_5.reset <= reset
    macs_10_5.io_in_a <= macs_10_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_5.io_in_c <= macs_9_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_5.io_in_w <= io_in_W_10_5 @[src/main/scala/MatMul.scala 50:27]
    macs_10_6.clock <= clock
    macs_10_6.reset <= reset
    macs_10_6.io_in_a <= macs_10_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_6.io_in_c <= macs_9_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_6.io_in_w <= io_in_W_10_6 @[src/main/scala/MatMul.scala 50:27]
    macs_10_7.clock <= clock
    macs_10_7.reset <= reset
    macs_10_7.io_in_a <= macs_10_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_7.io_in_c <= macs_9_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_7.io_in_w <= io_in_W_10_7 @[src/main/scala/MatMul.scala 50:27]
    macs_10_8.clock <= clock
    macs_10_8.reset <= reset
    macs_10_8.io_in_a <= macs_10_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_8.io_in_c <= macs_9_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_8.io_in_w <= io_in_W_10_8 @[src/main/scala/MatMul.scala 50:27]
    macs_10_9.clock <= clock
    macs_10_9.reset <= reset
    macs_10_9.io_in_a <= macs_10_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_9.io_in_c <= macs_9_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_9.io_in_w <= io_in_W_10_9 @[src/main/scala/MatMul.scala 50:27]
    macs_10_10.clock <= clock
    macs_10_10.reset <= reset
    macs_10_10.io_in_a <= macs_10_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_10.io_in_c <= macs_9_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_10.io_in_w <= io_in_W_10_10 @[src/main/scala/MatMul.scala 50:27]
    macs_10_11.clock <= clock
    macs_10_11.reset <= reset
    macs_10_11.io_in_a <= macs_10_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_11.io_in_c <= macs_9_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_11.io_in_w <= io_in_W_10_11 @[src/main/scala/MatMul.scala 50:27]
    macs_10_12.clock <= clock
    macs_10_12.reset <= reset
    macs_10_12.io_in_a <= macs_10_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_12.io_in_c <= macs_9_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_12.io_in_w <= io_in_W_10_12 @[src/main/scala/MatMul.scala 50:27]
    macs_10_13.clock <= clock
    macs_10_13.reset <= reset
    macs_10_13.io_in_a <= macs_10_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_13.io_in_c <= macs_9_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_13.io_in_w <= io_in_W_10_13 @[src/main/scala/MatMul.scala 50:27]
    macs_10_14.clock <= clock
    macs_10_14.reset <= reset
    macs_10_14.io_in_a <= macs_10_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_14.io_in_c <= macs_9_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_14.io_in_w <= io_in_W_10_14 @[src/main/scala/MatMul.scala 50:27]
    macs_10_15.clock <= clock
    macs_10_15.reset <= reset
    macs_10_15.io_in_a <= macs_10_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_10_15.io_in_c <= macs_9_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_10_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_10_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_10_15.io_in_w <= io_in_W_10_15 @[src/main/scala/MatMul.scala 50:27]
    macs_11_0.clock <= clock
    macs_11_0.reset <= reset
    macs_11_0.io_in_a <= io_in_A_11 @[src/main/scala/MatMul.scala 65:40]
    macs_11_0.io_in_c <= macs_10_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_0.io_in_w <= io_in_W_11_0 @[src/main/scala/MatMul.scala 50:27]
    macs_11_1.clock <= clock
    macs_11_1.reset <= reset
    macs_11_1.io_in_a <= macs_11_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_1.io_in_c <= macs_10_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_1.io_in_w <= io_in_W_11_1 @[src/main/scala/MatMul.scala 50:27]
    macs_11_2.clock <= clock
    macs_11_2.reset <= reset
    macs_11_2.io_in_a <= macs_11_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_2.io_in_c <= macs_10_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_2.io_in_w <= io_in_W_11_2 @[src/main/scala/MatMul.scala 50:27]
    macs_11_3.clock <= clock
    macs_11_3.reset <= reset
    macs_11_3.io_in_a <= macs_11_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_3.io_in_c <= macs_10_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_3.io_in_w <= io_in_W_11_3 @[src/main/scala/MatMul.scala 50:27]
    macs_11_4.clock <= clock
    macs_11_4.reset <= reset
    macs_11_4.io_in_a <= macs_11_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_4.io_in_c <= macs_10_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_4.io_in_w <= io_in_W_11_4 @[src/main/scala/MatMul.scala 50:27]
    macs_11_5.clock <= clock
    macs_11_5.reset <= reset
    macs_11_5.io_in_a <= macs_11_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_5.io_in_c <= macs_10_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_5.io_in_w <= io_in_W_11_5 @[src/main/scala/MatMul.scala 50:27]
    macs_11_6.clock <= clock
    macs_11_6.reset <= reset
    macs_11_6.io_in_a <= macs_11_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_6.io_in_c <= macs_10_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_6.io_in_w <= io_in_W_11_6 @[src/main/scala/MatMul.scala 50:27]
    macs_11_7.clock <= clock
    macs_11_7.reset <= reset
    macs_11_7.io_in_a <= macs_11_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_7.io_in_c <= macs_10_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_7.io_in_w <= io_in_W_11_7 @[src/main/scala/MatMul.scala 50:27]
    macs_11_8.clock <= clock
    macs_11_8.reset <= reset
    macs_11_8.io_in_a <= macs_11_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_8.io_in_c <= macs_10_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_8.io_in_w <= io_in_W_11_8 @[src/main/scala/MatMul.scala 50:27]
    macs_11_9.clock <= clock
    macs_11_9.reset <= reset
    macs_11_9.io_in_a <= macs_11_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_9.io_in_c <= macs_10_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_9.io_in_w <= io_in_W_11_9 @[src/main/scala/MatMul.scala 50:27]
    macs_11_10.clock <= clock
    macs_11_10.reset <= reset
    macs_11_10.io_in_a <= macs_11_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_10.io_in_c <= macs_10_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_10.io_in_w <= io_in_W_11_10 @[src/main/scala/MatMul.scala 50:27]
    macs_11_11.clock <= clock
    macs_11_11.reset <= reset
    macs_11_11.io_in_a <= macs_11_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_11.io_in_c <= macs_10_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_11.io_in_w <= io_in_W_11_11 @[src/main/scala/MatMul.scala 50:27]
    macs_11_12.clock <= clock
    macs_11_12.reset <= reset
    macs_11_12.io_in_a <= macs_11_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_12.io_in_c <= macs_10_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_12.io_in_w <= io_in_W_11_12 @[src/main/scala/MatMul.scala 50:27]
    macs_11_13.clock <= clock
    macs_11_13.reset <= reset
    macs_11_13.io_in_a <= macs_11_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_13.io_in_c <= macs_10_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_13.io_in_w <= io_in_W_11_13 @[src/main/scala/MatMul.scala 50:27]
    macs_11_14.clock <= clock
    macs_11_14.reset <= reset
    macs_11_14.io_in_a <= macs_11_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_14.io_in_c <= macs_10_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_14.io_in_w <= io_in_W_11_14 @[src/main/scala/MatMul.scala 50:27]
    macs_11_15.clock <= clock
    macs_11_15.reset <= reset
    macs_11_15.io_in_a <= macs_11_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_11_15.io_in_c <= macs_10_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_11_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_11_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_11_15.io_in_w <= io_in_W_11_15 @[src/main/scala/MatMul.scala 50:27]
    macs_12_0.clock <= clock
    macs_12_0.reset <= reset
    macs_12_0.io_in_a <= io_in_A_12 @[src/main/scala/MatMul.scala 65:40]
    macs_12_0.io_in_c <= macs_11_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_0.io_in_w <= io_in_W_12_0 @[src/main/scala/MatMul.scala 50:27]
    macs_12_1.clock <= clock
    macs_12_1.reset <= reset
    macs_12_1.io_in_a <= macs_12_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_1.io_in_c <= macs_11_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_1.io_in_w <= io_in_W_12_1 @[src/main/scala/MatMul.scala 50:27]
    macs_12_2.clock <= clock
    macs_12_2.reset <= reset
    macs_12_2.io_in_a <= macs_12_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_2.io_in_c <= macs_11_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_2.io_in_w <= io_in_W_12_2 @[src/main/scala/MatMul.scala 50:27]
    macs_12_3.clock <= clock
    macs_12_3.reset <= reset
    macs_12_3.io_in_a <= macs_12_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_3.io_in_c <= macs_11_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_3.io_in_w <= io_in_W_12_3 @[src/main/scala/MatMul.scala 50:27]
    macs_12_4.clock <= clock
    macs_12_4.reset <= reset
    macs_12_4.io_in_a <= macs_12_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_4.io_in_c <= macs_11_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_4.io_in_w <= io_in_W_12_4 @[src/main/scala/MatMul.scala 50:27]
    macs_12_5.clock <= clock
    macs_12_5.reset <= reset
    macs_12_5.io_in_a <= macs_12_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_5.io_in_c <= macs_11_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_5.io_in_w <= io_in_W_12_5 @[src/main/scala/MatMul.scala 50:27]
    macs_12_6.clock <= clock
    macs_12_6.reset <= reset
    macs_12_6.io_in_a <= macs_12_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_6.io_in_c <= macs_11_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_6.io_in_w <= io_in_W_12_6 @[src/main/scala/MatMul.scala 50:27]
    macs_12_7.clock <= clock
    macs_12_7.reset <= reset
    macs_12_7.io_in_a <= macs_12_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_7.io_in_c <= macs_11_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_7.io_in_w <= io_in_W_12_7 @[src/main/scala/MatMul.scala 50:27]
    macs_12_8.clock <= clock
    macs_12_8.reset <= reset
    macs_12_8.io_in_a <= macs_12_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_8.io_in_c <= macs_11_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_8.io_in_w <= io_in_W_12_8 @[src/main/scala/MatMul.scala 50:27]
    macs_12_9.clock <= clock
    macs_12_9.reset <= reset
    macs_12_9.io_in_a <= macs_12_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_9.io_in_c <= macs_11_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_9.io_in_w <= io_in_W_12_9 @[src/main/scala/MatMul.scala 50:27]
    macs_12_10.clock <= clock
    macs_12_10.reset <= reset
    macs_12_10.io_in_a <= macs_12_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_10.io_in_c <= macs_11_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_10.io_in_w <= io_in_W_12_10 @[src/main/scala/MatMul.scala 50:27]
    macs_12_11.clock <= clock
    macs_12_11.reset <= reset
    macs_12_11.io_in_a <= macs_12_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_11.io_in_c <= macs_11_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_11.io_in_w <= io_in_W_12_11 @[src/main/scala/MatMul.scala 50:27]
    macs_12_12.clock <= clock
    macs_12_12.reset <= reset
    macs_12_12.io_in_a <= macs_12_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_12.io_in_c <= macs_11_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_12.io_in_w <= io_in_W_12_12 @[src/main/scala/MatMul.scala 50:27]
    macs_12_13.clock <= clock
    macs_12_13.reset <= reset
    macs_12_13.io_in_a <= macs_12_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_13.io_in_c <= macs_11_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_13.io_in_w <= io_in_W_12_13 @[src/main/scala/MatMul.scala 50:27]
    macs_12_14.clock <= clock
    macs_12_14.reset <= reset
    macs_12_14.io_in_a <= macs_12_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_14.io_in_c <= macs_11_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_14.io_in_w <= io_in_W_12_14 @[src/main/scala/MatMul.scala 50:27]
    macs_12_15.clock <= clock
    macs_12_15.reset <= reset
    macs_12_15.io_in_a <= macs_12_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_12_15.io_in_c <= macs_11_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_12_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_12_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_12_15.io_in_w <= io_in_W_12_15 @[src/main/scala/MatMul.scala 50:27]
    macs_13_0.clock <= clock
    macs_13_0.reset <= reset
    macs_13_0.io_in_a <= io_in_A_13 @[src/main/scala/MatMul.scala 65:40]
    macs_13_0.io_in_c <= macs_12_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_0.io_in_w <= io_in_W_13_0 @[src/main/scala/MatMul.scala 50:27]
    macs_13_1.clock <= clock
    macs_13_1.reset <= reset
    macs_13_1.io_in_a <= macs_13_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_1.io_in_c <= macs_12_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_1.io_in_w <= io_in_W_13_1 @[src/main/scala/MatMul.scala 50:27]
    macs_13_2.clock <= clock
    macs_13_2.reset <= reset
    macs_13_2.io_in_a <= macs_13_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_2.io_in_c <= macs_12_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_2.io_in_w <= io_in_W_13_2 @[src/main/scala/MatMul.scala 50:27]
    macs_13_3.clock <= clock
    macs_13_3.reset <= reset
    macs_13_3.io_in_a <= macs_13_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_3.io_in_c <= macs_12_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_3.io_in_w <= io_in_W_13_3 @[src/main/scala/MatMul.scala 50:27]
    macs_13_4.clock <= clock
    macs_13_4.reset <= reset
    macs_13_4.io_in_a <= macs_13_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_4.io_in_c <= macs_12_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_4.io_in_w <= io_in_W_13_4 @[src/main/scala/MatMul.scala 50:27]
    macs_13_5.clock <= clock
    macs_13_5.reset <= reset
    macs_13_5.io_in_a <= macs_13_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_5.io_in_c <= macs_12_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_5.io_in_w <= io_in_W_13_5 @[src/main/scala/MatMul.scala 50:27]
    macs_13_6.clock <= clock
    macs_13_6.reset <= reset
    macs_13_6.io_in_a <= macs_13_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_6.io_in_c <= macs_12_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_6.io_in_w <= io_in_W_13_6 @[src/main/scala/MatMul.scala 50:27]
    macs_13_7.clock <= clock
    macs_13_7.reset <= reset
    macs_13_7.io_in_a <= macs_13_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_7.io_in_c <= macs_12_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_7.io_in_w <= io_in_W_13_7 @[src/main/scala/MatMul.scala 50:27]
    macs_13_8.clock <= clock
    macs_13_8.reset <= reset
    macs_13_8.io_in_a <= macs_13_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_8.io_in_c <= macs_12_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_8.io_in_w <= io_in_W_13_8 @[src/main/scala/MatMul.scala 50:27]
    macs_13_9.clock <= clock
    macs_13_9.reset <= reset
    macs_13_9.io_in_a <= macs_13_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_9.io_in_c <= macs_12_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_9.io_in_w <= io_in_W_13_9 @[src/main/scala/MatMul.scala 50:27]
    macs_13_10.clock <= clock
    macs_13_10.reset <= reset
    macs_13_10.io_in_a <= macs_13_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_10.io_in_c <= macs_12_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_10.io_in_w <= io_in_W_13_10 @[src/main/scala/MatMul.scala 50:27]
    macs_13_11.clock <= clock
    macs_13_11.reset <= reset
    macs_13_11.io_in_a <= macs_13_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_11.io_in_c <= macs_12_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_11.io_in_w <= io_in_W_13_11 @[src/main/scala/MatMul.scala 50:27]
    macs_13_12.clock <= clock
    macs_13_12.reset <= reset
    macs_13_12.io_in_a <= macs_13_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_12.io_in_c <= macs_12_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_12.io_in_w <= io_in_W_13_12 @[src/main/scala/MatMul.scala 50:27]
    macs_13_13.clock <= clock
    macs_13_13.reset <= reset
    macs_13_13.io_in_a <= macs_13_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_13.io_in_c <= macs_12_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_13.io_in_w <= io_in_W_13_13 @[src/main/scala/MatMul.scala 50:27]
    macs_13_14.clock <= clock
    macs_13_14.reset <= reset
    macs_13_14.io_in_a <= macs_13_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_14.io_in_c <= macs_12_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_14.io_in_w <= io_in_W_13_14 @[src/main/scala/MatMul.scala 50:27]
    macs_13_15.clock <= clock
    macs_13_15.reset <= reset
    macs_13_15.io_in_a <= macs_13_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_13_15.io_in_c <= macs_12_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_13_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_13_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_13_15.io_in_w <= io_in_W_13_15 @[src/main/scala/MatMul.scala 50:27]
    macs_14_0.clock <= clock
    macs_14_0.reset <= reset
    macs_14_0.io_in_a <= io_in_A_14 @[src/main/scala/MatMul.scala 65:40]
    macs_14_0.io_in_c <= macs_13_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_0.io_in_w <= io_in_W_14_0 @[src/main/scala/MatMul.scala 50:27]
    macs_14_1.clock <= clock
    macs_14_1.reset <= reset
    macs_14_1.io_in_a <= macs_14_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_1.io_in_c <= macs_13_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_1.io_in_w <= io_in_W_14_1 @[src/main/scala/MatMul.scala 50:27]
    macs_14_2.clock <= clock
    macs_14_2.reset <= reset
    macs_14_2.io_in_a <= macs_14_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_2.io_in_c <= macs_13_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_2.io_in_w <= io_in_W_14_2 @[src/main/scala/MatMul.scala 50:27]
    macs_14_3.clock <= clock
    macs_14_3.reset <= reset
    macs_14_3.io_in_a <= macs_14_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_3.io_in_c <= macs_13_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_3.io_in_w <= io_in_W_14_3 @[src/main/scala/MatMul.scala 50:27]
    macs_14_4.clock <= clock
    macs_14_4.reset <= reset
    macs_14_4.io_in_a <= macs_14_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_4.io_in_c <= macs_13_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_4.io_in_w <= io_in_W_14_4 @[src/main/scala/MatMul.scala 50:27]
    macs_14_5.clock <= clock
    macs_14_5.reset <= reset
    macs_14_5.io_in_a <= macs_14_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_5.io_in_c <= macs_13_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_5.io_in_w <= io_in_W_14_5 @[src/main/scala/MatMul.scala 50:27]
    macs_14_6.clock <= clock
    macs_14_6.reset <= reset
    macs_14_6.io_in_a <= macs_14_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_6.io_in_c <= macs_13_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_6.io_in_w <= io_in_W_14_6 @[src/main/scala/MatMul.scala 50:27]
    macs_14_7.clock <= clock
    macs_14_7.reset <= reset
    macs_14_7.io_in_a <= macs_14_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_7.io_in_c <= macs_13_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_7.io_in_w <= io_in_W_14_7 @[src/main/scala/MatMul.scala 50:27]
    macs_14_8.clock <= clock
    macs_14_8.reset <= reset
    macs_14_8.io_in_a <= macs_14_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_8.io_in_c <= macs_13_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_8.io_in_w <= io_in_W_14_8 @[src/main/scala/MatMul.scala 50:27]
    macs_14_9.clock <= clock
    macs_14_9.reset <= reset
    macs_14_9.io_in_a <= macs_14_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_9.io_in_c <= macs_13_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_9.io_in_w <= io_in_W_14_9 @[src/main/scala/MatMul.scala 50:27]
    macs_14_10.clock <= clock
    macs_14_10.reset <= reset
    macs_14_10.io_in_a <= macs_14_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_10.io_in_c <= macs_13_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_10.io_in_w <= io_in_W_14_10 @[src/main/scala/MatMul.scala 50:27]
    macs_14_11.clock <= clock
    macs_14_11.reset <= reset
    macs_14_11.io_in_a <= macs_14_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_11.io_in_c <= macs_13_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_11.io_in_w <= io_in_W_14_11 @[src/main/scala/MatMul.scala 50:27]
    macs_14_12.clock <= clock
    macs_14_12.reset <= reset
    macs_14_12.io_in_a <= macs_14_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_12.io_in_c <= macs_13_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_12.io_in_w <= io_in_W_14_12 @[src/main/scala/MatMul.scala 50:27]
    macs_14_13.clock <= clock
    macs_14_13.reset <= reset
    macs_14_13.io_in_a <= macs_14_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_13.io_in_c <= macs_13_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_13.io_in_w <= io_in_W_14_13 @[src/main/scala/MatMul.scala 50:27]
    macs_14_14.clock <= clock
    macs_14_14.reset <= reset
    macs_14_14.io_in_a <= macs_14_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_14.io_in_c <= macs_13_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_14.io_in_w <= io_in_W_14_14 @[src/main/scala/MatMul.scala 50:27]
    macs_14_15.clock <= clock
    macs_14_15.reset <= reset
    macs_14_15.io_in_a <= macs_14_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_14_15.io_in_c <= macs_13_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_14_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_14_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_14_15.io_in_w <= io_in_W_14_15 @[src/main/scala/MatMul.scala 50:27]
    macs_15_0.clock <= clock
    macs_15_0.reset <= reset
    macs_15_0.io_in_a <= io_in_A_15 @[src/main/scala/MatMul.scala 65:40]
    macs_15_0.io_in_c <= macs_14_0.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_0.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_0.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_0.io_in_w <= io_in_W_15_0 @[src/main/scala/MatMul.scala 50:27]
    macs_15_1.clock <= clock
    macs_15_1.reset <= reset
    macs_15_1.io_in_a <= macs_15_0.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_1.io_in_c <= macs_14_1.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_1.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_1.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_1.io_in_w <= io_in_W_15_1 @[src/main/scala/MatMul.scala 50:27]
    macs_15_2.clock <= clock
    macs_15_2.reset <= reset
    macs_15_2.io_in_a <= macs_15_1.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_2.io_in_c <= macs_14_2.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_2.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_2.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_2.io_in_w <= io_in_W_15_2 @[src/main/scala/MatMul.scala 50:27]
    macs_15_3.clock <= clock
    macs_15_3.reset <= reset
    macs_15_3.io_in_a <= macs_15_2.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_3.io_in_c <= macs_14_3.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_3.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_3.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_3.io_in_w <= io_in_W_15_3 @[src/main/scala/MatMul.scala 50:27]
    macs_15_4.clock <= clock
    macs_15_4.reset <= reset
    macs_15_4.io_in_a <= macs_15_3.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_4.io_in_c <= macs_14_4.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_4.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_4.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_4.io_in_w <= io_in_W_15_4 @[src/main/scala/MatMul.scala 50:27]
    macs_15_5.clock <= clock
    macs_15_5.reset <= reset
    macs_15_5.io_in_a <= macs_15_4.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_5.io_in_c <= macs_14_5.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_5.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_5.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_5.io_in_w <= io_in_W_15_5 @[src/main/scala/MatMul.scala 50:27]
    macs_15_6.clock <= clock
    macs_15_6.reset <= reset
    macs_15_6.io_in_a <= macs_15_5.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_6.io_in_c <= macs_14_6.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_6.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_6.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_6.io_in_w <= io_in_W_15_6 @[src/main/scala/MatMul.scala 50:27]
    macs_15_7.clock <= clock
    macs_15_7.reset <= reset
    macs_15_7.io_in_a <= macs_15_6.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_7.io_in_c <= macs_14_7.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_7.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_7.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_7.io_in_w <= io_in_W_15_7 @[src/main/scala/MatMul.scala 50:27]
    macs_15_8.clock <= clock
    macs_15_8.reset <= reset
    macs_15_8.io_in_a <= macs_15_7.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_8.io_in_c <= macs_14_8.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_8.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_8.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_8.io_in_w <= io_in_W_15_8 @[src/main/scala/MatMul.scala 50:27]
    macs_15_9.clock <= clock
    macs_15_9.reset <= reset
    macs_15_9.io_in_a <= macs_15_8.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_9.io_in_c <= macs_14_9.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_9.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_9.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_9.io_in_w <= io_in_W_15_9 @[src/main/scala/MatMul.scala 50:27]
    macs_15_10.clock <= clock
    macs_15_10.reset <= reset
    macs_15_10.io_in_a <= macs_15_9.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_10.io_in_c <= macs_14_10.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_10.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_10.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_10.io_in_w <= io_in_W_15_10 @[src/main/scala/MatMul.scala 50:27]
    macs_15_11.clock <= clock
    macs_15_11.reset <= reset
    macs_15_11.io_in_a <= macs_15_10.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_11.io_in_c <= macs_14_11.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_11.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_11.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_11.io_in_w <= io_in_W_15_11 @[src/main/scala/MatMul.scala 50:27]
    macs_15_12.clock <= clock
    macs_15_12.reset <= reset
    macs_15_12.io_in_a <= macs_15_11.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_12.io_in_c <= macs_14_12.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_12.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_12.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_12.io_in_w <= io_in_W_15_12 @[src/main/scala/MatMul.scala 50:27]
    macs_15_13.clock <= clock
    macs_15_13.reset <= reset
    macs_15_13.io_in_a <= macs_15_12.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_13.io_in_c <= macs_14_13.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_13.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_13.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_13.io_in_w <= io_in_W_15_13 @[src/main/scala/MatMul.scala 50:27]
    macs_15_14.clock <= clock
    macs_15_14.reset <= reset
    macs_15_14.io_in_a <= macs_15_13.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_14.io_in_c <= macs_14_14.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_14.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_14.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_14.io_in_w <= io_in_W_15_14 @[src/main/scala/MatMul.scala 50:27]
    macs_15_15.clock <= clock
    macs_15_15.reset <= reset
    macs_15_15.io_in_a <= macs_15_14.io_out_in @[src/main/scala/MatMul.scala 66:33]
    macs_15_15.io_in_c <= macs_14_15.io_out_mac @[src/main/scala/MatMul.scala 69:33]
    macs_15_15.io_set_w <= io_set_W @[src/main/scala/MatMul.scala 51:27]
    macs_15_15.io_clear_w <= io_clear_W @[src/main/scala/MatMul.scala 58:29]
    macs_15_15.io_in_w <= io_in_W_15_15 @[src/main/scala/MatMul.scala 50:27]
