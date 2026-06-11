# 0003 — RAND uses counter-based noise, not java.util.Random

## Context
RAND node needs per-tick randomness. A single `Random` stream shared across units makes unit u's values depend on population size and evaluation order: adding a unit changes every other unit's history.

## Decision
`Noise.sample(seed, unitIndex, tick)` — a pure splitmix64-based hash, no mutable state. Kernel owns no RNG object.

## Why
- Unit invariance: a unit's RAND stream depends only on (seed, unitIndex, tick), so unit 0 is bit-identical whether the population is 1 or 10,000. Tested in MultiUnitKernelTest.
- Parallel-ready: evaluation order can change or run concurrently without affecting results.
- Zero state, zero allocation: fits the elevator-brain budget; replaces a mutable object with two multiplies and shifts.
- Replay/seek: any tick's noise is computable directly without replaying prior ticks.

## Rejected
- Shared `Random`: population-size-dependent streams, order-dependent, blocks parallelization.
- `Random` per unit: N mutable objects; determinism survives but seek/replay and statelessness lost.
