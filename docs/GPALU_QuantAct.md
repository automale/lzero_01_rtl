# GPALU INT8 and QuantAct routing contract

Based on `feat/tpu-rocc-driver` at `bbae62ca41c01ce84af0c161d6861e235678fcfd`.
This change prepares the two building blocks. `VPU.scala` and `Comp.scala` are
older integration sketches and are not migrated or validated by this change.
The production VPU1 router, operand buffering and descriptor wiring remain a
separate integration step. Route tests use a test-only harness.

## Supported composition

| Operation | Route | Requirement |
|---|---|---|
| GEMM with activation and residual/gate | TPU INT32 -> QuantAct -> GPALU | Fixed order. Align the second INT8 operand with QuantAct output. |
| f(X + Y) | GPALU wide -> QuantAct | quant_en=false only if the sum is already in LUT input integer units. |
| f(X * Y) | GPALU wide -> QuantAct | Usually quant_en=true to convert product scale to the LUT input scale. |
| f(X) + f(Y), f(X) * f(Y) | Prepare activation results, then GPALU | One QuantAct needs separate passes and storage of the first result. A route mux alone cannot evaluate two activations simultaneously. |
| X + Y, X * Y | GPALU narrow output | Compiler aligns scales; out_shift handles power-of-two rescaling. |
| f(X) | QuantAct activation-only | quant_en=false, act_en=true. |

Reordering quantization is a quantized numerical contract, not an algebraically
exact transformation of a float model. GEMM -> QuantAct -> GPALU has intermediate
INT8 rounding/clipping. Calibrate/train against that order.

## GPALU

Defaults: 16 lanes, signed INT8 inputs and signed INT8 terminal output, carried
as UInt two's-complement bits. Both operands of a beat must be ready together.
The unit accepts per-lane valid masks; it has no independent A/B handshake.

* BYPASS=0, ADD=1, MUL=2; reserved 3 behaves as BYPASS.
* `out_wide`: exact signed 16-bit result (9-bit sum sign-extended to 16 bits).
  It is **not** shifted or clipped. Feed this into the post-ALU QuantAct input,
  sign-extended to 32 bits. Never feed `out_vec` into this route if the full
  pre-activation sum/product is required.
* `out_vec`: signed saturation of `out_wide >> out_shift` to [-128,127].
  `out_shift` is 0..31, sampled per input beat, and uses arithmetic right shift
  (round toward negative infinity). It is not an arbitrary multiplier.
* `alu_alert`: at least one valid narrow result clipped. This is nonfatal status,
  and is irrelevant if only the wide path is selected. It is aligned with output
  data and valid, including multiplication.
* Latency: 3 enabled rising edges. Throughput: one vector per enabled edge.
  Global stall freezes data/control/valid registers and masks output-valid and
  alert; no result is consumed until stall is released.

ADD assumes both operands have the same real scale and zero point zero. MUL
produces scale Sx*Sy. Terminal MUL can use out_shift only when the desired scale
ratio is a power of two. Arbitrary rescaling requires QuantAct or preprocessing;
nonzero zero points must be removed before GPALU. These are compiler contracts,
not implicitly implemented scale alignment.

## QuantAct numerical ABI

The packed parameter remains 32 bits:
`[31:16] unsigned multiplier, [15:13] reserved, [12:8] shift, [7:0] signed input zero point`.
The zero point is subtracted from the input; it is not an output zero point.

With quant_en=true, let P=(input-zp)*multiplier and S=shift.
With quant_en=false, let P=input and S=0; qparams/QB are not used.

* act_en=false: output = sat_INT8(P >> S).
* act_en=true: F=indexBits-outBits (default 2),
  address=clamp(((P << F) >> S) + 2^(indexBits-1), 0, 2^indexBits-1).
  Output is the programmed LUT byte interpreted as signed INT8 downstream.
* All shifts round toward negative infinity. There is no rounding-to-nearest.
* The default 1024-entry LUT covers [-128,127.75] in quarter-unit increments:
  address 0=-128, 512=0, 1023=127.75. The compiler supplies the real-world scale
  of these units when generating the table.
* quant_en=false/act_en=true is activation-only on an integer in those units.
  For a product at a different scale, leave quant_en=true and supply its requant
  parameter, even though GEMM is absent.
* quant_en=false/act_en=false is a saturating signed pass-through, not truncation.

This deliberately changes the old unsigned zero-clipped numeric ABI. Regenerate
activation LUTs; do not reuse tables indexed from only nonnegative inputs.
Signed INT8 activations now survive the linear path. Shifts 0 and 1 also have
correct scaling (the old max(shift-2,0) path silently lost a factor of 4 or 2).

Latency remains 4 enabled edges; activation and linear results have identical
latency. An issued synchronous LUT response is retained across a global stall.
Control is captured per beat. LUT programming must complete before activation
traffic, and tables must not be rewritten while activation requests are in flight.

PER_MATRIX still broadcasts one parameter; PER_CHANNEL still uses 16 parameters
per 64-byte QB line and swaps every 16 accepted all-valid rows. A vector tail
must be padded to the established 16-lane/16-row schedule; QuantAct still flags
partial-lane beats. quant_en/param_mode and route are operation-level controls
when using QB. Drain the pipeline, soft_reset parameter scheduling and re-prime
before changing that schedule; soft_reset is not a data-pipeline flush.
Linear operations do not require LUT readiness. Activation-only never fetches QB.

## Future VPU1 wiring

Select QuantAct -> GPALU for GEMM. For standalone vectors select either order
before issuing the operation and keep the selection until all outputs drain.
Use a common global stall. Delay/queue operand B, its valid and ALU controls by
QuantAct's four enabled edges in the forward route. Fetching B only when the
QuantAct result is available is also valid if the buffer interface guarantees it.
Post-ALU route uses out_wide, not the saturated terminal port. Preserve operation
metadata through both pipelines, and do not construct an active feedback loop.

## Verification

`make gpalu-quant-test` runs GPALU unit tests, the existing QB schedule regression,
signed QuantAct tests and both route harnesses. Tests include extreme signed
values, shifts 0/1/31, per-beat controls, bubbles, long stalls, exact wide results,
narrow clipping and second-operand alignment. This is module/route simulation;
it does not claim full SoC compilation, synthesis timing or model accuracy.
