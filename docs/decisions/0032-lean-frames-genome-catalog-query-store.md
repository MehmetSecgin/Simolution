# 0032 — Lean every-tick frames + genome catalog + queryable lineage store

**Status:** accepted (direction) · **Date:** 2026-06-15 · **Spec:** [report-v13](../specs/report-v13.md)

## Context

A run wrote ~84 MB across 8+ files (96 % `map.txt`) and a CSV zoo no inspection workflow
reads. The owner: "we don't need THIS much data, we're being weird." Root cause: persisting
re-derivable trajectories of a **deterministic** sim, and re-emitting each unit's full genome
+ 24 node-output floats in **every** map frame.

Measuring the runs settled the encoding. (The `.timeseries.csv` `births` column is
**cumulative** — an early read of it as per-tick gave a bogus "~2000 births/tick"; the real
per-tick rate is its diff.) Truth: a fast colonization **boom** (up to ~187 births/tick for a
few dozen ticks) into a near-static plateau; **births-total is only ~1–2 k per run**, standing
population swings (peak ~1020). So a **full** every-tick frame is cheap (~0.7 MB; per-row cost
fell ~13× once genome→catalog id and node floats were dropped), and lineage is small (~1–2 k
edges) but naturally **queryable**.

The owner's stated inspection needs: every unit every tick (incl. which action fired), each
unit's genome at the current tick, evolution per lineage, and arbitrary "what happened to who"
queries — live or after — with **no JVM** to view. The owner proposed an OO + database model.

## Decisions

1. **Lean every-tick frame stream** (`<base>.frames`): per living unit
   `slot x y mass action genomeId`, no genome bytes, no node-output floats. Every tick by
   default (sampling/​windowing knobs retained). Streamed live, tailed serverlessly (v12).
2. **Genome catalog** (`<base>.catalog`): global write-once `genomeId → genes`; frames carry
   the id. Globalises report-v11's per-slot dedup. Living distinct genomes are few → small.
3. **Queryable lineage/births store as gzipped CSV, queried by DuckDB** (`BirthLogWriter`,
   always-on when `--out`). Keep **every** birth edge (no pruning; ~1–2 k/run, pruning is a
   `WHERE`). Gzipped CSV (a few KB) keeps the toolchain **dependency-free** — no Java Parquet
   writer (doctrine forbids the dep) — while DuckDB reads `.csv.gz` directly and can `COPY` to
   Parquet on demand. The "what happened to who" surface (ancestry via recursive CTE, no JVM).
4. **Take the DB, reject the OO.** A queryable store gives every requested query **without**
   object organisms. The kernel stays flat-array, pure, history-free (core invariant +
   memory doctrine). Objects/queries live in the harness layer, exactly where report-v8 put
   the wall — no kernel event hook, byte-identical when sinks are off.
5. **Retire the CSV zoo** (`units`/`population`/`wiring`/`popwiring`/`lineage.csv`,
   `events.jsonl`, `metrics.csv`) — all re-derivable, none on the inspection path. Keep the
   2 KB `.txt` report and (optionally) `.timeseries.csv` always-on.
6. **Checkpoints stay opt-in** (`--observe`, report-v8): the rare full reconstruction; the
   only path needing the JVM replayer.

## Why

Determinism is the compression: the recoverable canonical state is manifest + checkpoints;
frames/catalog/Parquet are derived **views**, now lean enough to stream live and small enough
to keep. Fixes the concept (don't persist a deterministic function's whole output), and the
84 MB dissolves as a side effect. Kernel doctrine (data-oriented, constant-in-ticks memory)
is preserved precisely by putting the DB in the observability layer, not the kernel.

## Rejected

- **OO organisms in the kernel** — breaks "data-oriented, not OO organisms" + the
  no-per-tick-allocation / constant-in-ticks memory doctrine. The DB delivers the queries
  without it.
- **Delta-encoded frames** — would be marginally *smaller* (per-tick churn is low outside the
  boom), but needs client-side accumulation from a keyframe to land on any tick; full frames
  give O(1) random seek (the scrub workflow) + a trivial parser, and the file is already
  sub-MB, so delta's complexity buys nothing. Chosen for seek + simplicity, not size.
- **Replay-canonical (no live frame file)** — drops live-watch (owner wants it) and
  reintroduces a JVM dependency to view. Stream a lean file instead; it's ~1 MB now.
- **JSONL events (report-v8)** as the lineage store — fatter per row and awkward for "walk the
  ancestry" SQL; a flat CSV the recursive CTE reads directly is leaner, and gzip + DuckDB give
  the columnar win without the Parquet-writer dependency.

## Status note

Direction accepted in a design conversation; implementation pending. Sequence in the spec's
deferred section (close cyclic-replay gap only if checkpoint-export of cyclic frames is later
wanted — not a v13 blocker).
