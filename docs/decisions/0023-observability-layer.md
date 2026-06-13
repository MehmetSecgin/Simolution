# 0023 — Observability: replay-based time-travel + decoupled logs, DuckDB/Parquet query

## Context
Runs are explored today via end-of-run report + sidecars + the spatial scrub
viewer. Missing: *"jump to any tick and see the whole world there"* and *"ask
arbitrary questions of an already-finished run"* — Grafana-style observability.
Naively this means storing per-tick state, which collides head-on with the memory
doctrine (footprint constant in tick count, no trajectory in RAM). Owner wants
this capability; deps are acceptable *for logs* but the sim must still build and
run with no log reader present.

## Decision
Build an **optional observability layer** in the sim, on four pillars (full schema
in [report-v8](../specs/report-v8.md)):

1. **Determinism is the time machine.** Store a **manifest** (seed, founder
   genomes/cells, config hash) and **replay** to tick T; accelerate jumps with
   periodic full-state **checkpoints** (`--checkpoint-every C`). Replay gives full
   fidelity (per-node outputs, delay memory, live circuits) that no sample holds.
2. **Logging is a decoupled, optional sink — no kernel hook.** Kernel stays pure
   (logs nothing, no event callback); the sim owns a `RunLog` sink and derives all
   events from snapshot deltas, plus exact mutation bits by diffing child vs parent
   genes (move never rewrites genes, ADR 0020). No sink → run byte-identical, zero
   extra allocation.
3. **Tiered capture** (owner's choice): events (birth/death/extinction) logged
   **exactly**; per-tick aggregates every tick; lattice sampled (existing
   `map.txt`); per-unit detail recovered by **replay**, not streamed.
4. **JVM writes text, the reader owns Parquet + query** (owner's choice): sim emits
   typed CSV/JSONL only — no new JVM dependency. DuckDB (tooling) reads them and
   materializes Parquet on demand. **Grafana is an optional later front-end over
   DuckDB/Parquet, not the storage of record.**

New kernel surface (pure mechanics, no law change): **state save/restore only** for
checkpoints — no event hook. Manifest stores founder genomes **verbatim** (not
regenerated from `genomeSeed`), since `configHash` covers KernelConfig constants but
not `GenomeFactory` code; founding state is data, like `founderCells`.

## Why
- **Respects the memory doctrine** — all history on disk, streamed like
  `MapFrameWriter`; in-RAM footprint unchanged. Determinism turns "store the past"
  into "recompute the past," which is the cheapest possible store.
- **Sim stays featherweight** — the heavy analytic dep (DuckDB/Parquet/Grafana)
  lives entirely in tooling. The build that must run has zero new deps.
- **No lock-in** — Parquet is the universal substrate; choosing or dropping Grafana
  later costs nothing. DuckDB delivers ~90% of "Grafana for runs" (arbitrary SQL,
  time-travel via replay, scrub via existing viewers) with no infra.
- **Full fidelity where it matters** — replay reconstructs per-node state and
  evolved circuits at any T, which a metrics scrape fundamentally cannot.

## Rejected
- **Grafana + Prometheus/Loki as the foundation.** Built for *live scraping* of a
  running process, retention-limited, and can't render the lattice or circuits
  anyway — it would only cover aggregate panels at the cost of a whole docker
  stack and push pipeline. Kept as an optional face over the real substrate.
- **Persist full per-tick snapshots.** Violates the memory/disk-cost intent (GB-scale
  for long runs) and is redundant with deterministic replay.
- **Parquet writer in the JVM (parquet-mr/Arrow).** Heavy dependency added to the
  sim build; pushed to the query layer instead (JVM writes CSV/JSONL).
- **Pure replay, no checkpoints.** Zero storage but O(T) per jump; checkpoints make
  late-tick inspection O(C). Checkpointing is opt-out (`--checkpoint-every 0`).

## Status
Design only (report-v8). Implementation deferred — sim-side sink + manifest (with
verbatim founder genomes) + replay/checkpoint (save/restore only) + event log to
follow, behind `--observe` (off by default, tests and baseline untouched).
