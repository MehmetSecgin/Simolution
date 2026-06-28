# 0037 — Single-precision signal state (double → float)

## Context

The performance doctrine lists "double → float for state" as a deferred
optimization, gated on an ADR. The dominant runtime footprint is the four
per-node arrays the doctrine names explicitly — `outputsPrev`, `outputsNext`,
`accumulators`, `delayMemory` — sized `maxUnits × NodeLayout.TOTAL`
(`4 × TOTAL × 8` bytes/unit). On a 100×100 world these are ~7.7 MB; everything
else (the per-unit energy economy) is an order of magnitude smaller.

The hard constraint is the closed-system audit: `energy-audit-error ≈ 0`. The
accumulators `energySink` and `cumulativeInflow` grow monotonically over a run
(tens of thousands of ticks); in `float` (~7 significant digits) a sink near
1e7 silently swallows a +1.0 charge and the audit diverges.

## Decision

Split the state by role:

- **Signal domain → `float`**: `outputsPrev`, `outputsNext`, `accumulators`,
  `delayMemory`, `mulTop1`, `mulTop2`, the matching `KernelSnapshot.outputs` /
  `delayMemory` (passed by reference — snapshot stays zero-copy), the
  `DynamicsObserver.prevOutputs` / `prevDelay` mirrors, and the checkpoint
  serialization of `outputsPrev` / `delayMemory` (`writeFloat`/`readFloat`).
  These hold bounded values (CLAMP ∈ [-1,1], THRESH ±1, weights ±4) that are
  cleared every tick or persist a single tick — no cross-tick accumulation, so
  no precision drift.
- **Economy domain → stays `double`**: `energy`, `mass`, `damage`,
  `resourceField`, `energySink`, `cumulativeInflow`, and all credited-total
  scalars. Every energy transaction (intake, cost, growth, fission split,
  movement, decay, death) remains a byte-exact double transfer, so the audit
  invariant is untouched.

`CompiledConnection.weight` stays `double` — it is structure, not state, and
its conversion belongs to the separate SoA ADR. The propagate multiply is
therefore `(float)(outputsPrev[src] * weight)`: one float widened, computed in
double, narrowed once.

## Why

- Captures essentially the entire advertised memory win — the per-node budget
  halves, `4 × TOTAL × 8` → `4 × TOTAL × 4` (768 → 384 bytes/unit, ~3.8 MB on a
  100×100 world) — while leaving the audit exact.
- Signals are precision-insensitive (bounded, non-accumulating); the energy
  economy, where conservation lives, is not. Putting the boundary there is
  principled, not a compromise.
- Determinism holds: Java mandates strict IEEE-754 for `float` (strictfp is the
  language default since JDK 17), so single-precision ops are reproducible
  across platforms exactly as `double` was.

## What was rejected

- **All state → float** (incl. the economy): would blow up `energy-audit-error`
  via monotone-accumulator precision loss. Rejected outright.
- **Loosening the conformance test to a float tolerance**: a double reference
  drifts against a single-precision *dynamical system* over its feedback loop,
  so a fixed tolerance is both fragile and dishonest. Instead the reference
  model in `KernelVerticalSliceTest` was ported to mirror the kernel's float
  accumulation order exactly, keeping the check bit-exact (`delta 0`).
- **Converting `CompiledConnection.weight` too**: deferred — it is the SoA
  optimization's concern. Until then this change is a memory + cache-density
  win (denser per-node arrays, 2× values per cache line), not a raw-FLOPs/SIMD
  win, since the propagate multiply still runs in double.

## Consequences

- The `state-digest` and the whole baseline change: single-precision signals
  feed the saturating harvest/grow/move/reproduce decisions, so trajectories,
  death timing, and lineages all shift. Expected — re-baselined in the same
  commit. `energy-audit-error` stays at double FP-noise level (~1e-6).
- The checkpoint binary format changes (`outputsPrev`/`delayMemory` are now
  `float`); `.ckpt` files are per-run artifacts and the `Replayer` is the only
  reader, so there is no compatibility surface. Replay exactness holds (the
  float round-trip is lossless; save + tick-forward reproduces history).
