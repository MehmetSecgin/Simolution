# 0039: Phase shapes and a parallel runner with fixed chunks

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Design:** [engine-v6-design](../notes/engine-v6-design.md) §5.3, §5.8 · **Supersedes in part:** [0025](0025-parallel-tick-analysis.md)

## Context

The v5 tick was 11 hand-ordered calls in one class, all serial. ADR 0025 rejected work stealing because reduction order would depend on scheduling.

## Decision

- The tick is an ordered `List<Phase>`. Each phase declares a `Shape`: `PER_UNIT`, `PER_CELL`, `CLAIM` or `SERIAL`, which fixes what it may read and write.
- `ParallelRunner` owns a fixed pool of persistent threads. A split phase is cut into chunks of 4096 (`Chunks.SIZE`, an engine constant); threads claim chunks from an atomic counter. Chunk boundaries depend on the extent only, never on the thread count.
- Scalar totals (sink, inflow) go into per-chunk partials and are summed in ascending chunk index in `finish`.
- The phase barrier is one volatile generation write plus spin-then-park waits, and allocates nothing (`CyclicBarrier` and `Phaser` allocate per crossing).

## Why

Results never depend on which thread ran a chunk, so thread count is a run setting and not part of the run's identity. The 0025 objection was about reduction order, which fixed chunks and ordered merges remove. Adding, splitting or reordering a phase is an edit to one list.

## Rejected

- Chunk size derived from the thread count: breaks determinism across thread counts.
- A parallel stream or fork-join pool: unordered floating-point reductions and per-call allocation.
- Locking shared totals: contended and order-dependent.
