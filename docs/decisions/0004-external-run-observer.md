# 0004 — Run statistics via external observer, deterministic report-v1

## Context
The owner wants a deterministic before/after record of every kernel change: run, report, diff. Statistics need per-connection signals, but the kernel only exposes snapshots.

## Decision
`sim` package outside the kernel. `DynamicsObserver` re-derives the propagation phase from the previous snapshot's outputs plus the compiled wiring — identical arithmetic, identical inputs, so observed signals are exactly what flowed. Report format `report-v1` (docs/specs/report-v1.md) is byte-deterministic; a 64-bit state digest folds the full trajectory so any behavioral drift is visible even below display rounding. Wall-clock goes to console only.

## Why
Kernel stays law-only (contract v0 §12: interpretation is external) and pays zero cost for being observed. Observation cost is paid observer-side, off the hot path. Determinism makes report diffs causal: same seed, any changed line was caused by the code change.

## Notable sub-decisions
- RAND's own output is excluded from fixed-point detection (it changes every tick by construction) but included in the digest.
- Non-finite state (double overflow from explosive feedback — the kernel never clamps, per spec) classifies as divergent via an explicit flag; NaN evades numeric cutoffs (`NaN > x` is false), which produced wrong classifications and NaN-poisoned aggregates on first run. Magnitude-based fractions were replaced with count-based ones for the same reason.
- Genome generation salts the run seed so genomes and RAND noise are decorrelated streams of one seed.

## Rejected
- Counters inside the kernel: pollutes laws, costs the hot loop, violates the doctrine.
- Magnitude-weighted junk fraction: NaN/Infinity-poisoned by divergent units; counts align better with the future activity-cost law anyway (contract v0 §6: cost ∝ work performed).
