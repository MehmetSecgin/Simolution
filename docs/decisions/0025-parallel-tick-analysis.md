# 0025 — Parallelizing the tick: feasibility analysis (deferred)

## Context
The kernel is single-threaded: ~485 ticks/s sparse (W=100), ~226 ticks/s dense
(W=130) on an M2 Pro, ~4M cell-ticks/s per core. Cost per tick ≈ `W²` (full-grid
sweeps) + `population · genes` (propagation). On a 10-core machine 9 cores sit
idle. This records whether — and how — a single run can be parallelized **without
breaking determinism** (same seed → byte-identical history, the core invariant),
so the work is scoped before anyone takes it. No code change yet.

## Per-phase verdict
The data-oriented layout helps: the heavy phases have no cross-unit writes.

| phase | parallel? | reason |
|---|---|---|
| 1 clear | yes, trivial | `Arrays.fill`, order-free |
| 2 propagate | **embarrassingly** | each unit writes only its own node block (`dst ∈ [unit·TOTAL, +TOTAL)`); no shared accumulator cells |
| 3 evaluate | yes | per-unit; `Noise.sample(seed,unit,tick)` is pure |
| 4 swap | trivial | pointer flip |
| 5 intake | per-element yes | one unit per cell → debits its own cell; **but** `cumulativeInflow` is a scalar reduction |
| 6 cost | per-element yes | per-unit energy/damage/own-cell-free; **but** `energySink` is a scalar reduction |
| 7 reproduction | **no — serial by design** | ascending parent scan, child installed immediately, later parents see the cell taken; `freeSlotCursor` monotone |
| 8 movement | **no — serial by design** | ascending scan, immediate occupancy, ties resolved by lowest slot, cell vacated this tick reusable this tick |
| 9 diffusion | **embarrassingly** | double-buffered Laplacian — reads old buffer, writes new, every cell independent |

Phases 2, 3, 9 are the bulk of the cost and parallelize cleanly with no
restructure.

## The real constraint: floating-point determinism
FP addition is non-associative, so any reduction whose order changes (Java
parallel-stream `.sum()`, work-stealing) produces bit-different totals and breaks
byte-identical replay. Two consequences:
- The scalar sinks (`energySink`, `cumulativeInflow`) must use a **fixed-order**
  reduction: static slot-range partition → per-thread partials → combine in
  thread-index order. Cheap.
- Per-unit/per-cell accumulation stays bit-identical because each element is summed
  by one thread in the same gene/cell order as serial.

## Decision (when taken)
Parallelize phases 1–3, 5, 6, 9 over a **static, deterministic** slot/cell range
partition with a fixed thread count; keep the locked phase barriers; combine the
two scalar sinks via fixed-order partial reduction. Leave phases 7 & 8 serial
(cheap today, and inherently ordered). If they later bottleneck, rewrite them as
**gather-intents → resolve-deterministically** (collect every birth/move request,
apply in fixed slot order) — a semantics-preserving change needing its own ADR +
baseline regen. Any implementation must pass the existing determinism tests
(state-digest unchanged) and regenerate the baseline to prove byte-identity.

## Why deferred
Not needed yet — runs are interactive at current world sizes, and `--observe` adds
~0.5%. Threads add complexity (partition bookkeeping, fixed reductions) that only
pays off on big worlds or million-tick runs. The bigger single-core win is likely
cheaper and lower-risk: `CompiledConnection[]` → struct-of-arrays (kills pointer
chasing in propagate) and `double → float` for node state (halves sweep bandwidth)
— both already-listed deferred optimizations.

## Rejected
- **Parallel streams / work-stealing for the sinks** — non-deterministic FP order,
  breaks replay. Disqualified outright.
- **Parallelizing phases 7/8 as-is** — their results depend on scan order by
  design; naive threading changes the genealogy and geography.
- **Multi-run parallelism instead** (run N seeds concurrently) — trivially safe and
  may be the better first lever for throughput-of-experiments, but it does not speed
  up a single long run, which is the question here.

## Status
Deferred — analysis only, no code. Next perf step is more likely SoA + float
(single-core, lower risk). Relates to the performance doctrine in AGENTS.md
(deferred optimizations) and contract-v3 §1 (determinism).
