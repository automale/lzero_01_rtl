FIRRTL version 1.2.0
circuit MacUnit :
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
