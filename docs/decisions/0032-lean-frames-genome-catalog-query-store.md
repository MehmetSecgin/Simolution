# 0032 — Lean every-tick frames + genome catalog + queryable lineage store

**Status:** accepted (direction) · **Date:** 2026-06-15 · **Spec:** [report-v13](../specs/report-v13.md)

## Context

A run wrote ~84 MB across 8+ files (96 % `map.txt`) and a CSV zoo no inspection workflow
reads. The owner: "we don't need THIS much data, we're being weird." Root cause: persisting
re-derivable trajectories of a **deterministic** sim, and re-emitting each unit's full genome
+ 24 node-output floats in **every** map frame.

Measuring churn reframed it: avg living population is tiny (33–56) but births/tick are huge
(~1300–2030; ~97 % of births die childless almost immediately). So a **full** every-tick
frame is cheap (~1 MB at full resolution), **delta encoding loses** (churn ≫ standing pop),
and the genuinely heavy, derivable thing is the **lineage** (3 M birth edges).

The owner's stated inspection needs: every unit every tick (incl. which action fired), each
unit's genome at the current tick, evolution per lineage, and arbitrary "what happened to who"
queries — live or after — with **no JVM** to view. The owner proposed an OO + database model.

## Decisions

1. **Lean every-tick frame stream** (`<base>.frames`): per living unit
   `slot x y mass action genomeId`, no genome bytes, no node-output floats. Every tick by
   default (sampling/​windowing knobs retained). Streamed live, tailed serverlessly (v12).
2. **Genome catalog** (`<base>.catalog`): global write-once `genomeId → genes`; frames carry
   the id. Globalises report-v11's per-slot dedup. Living distinct genomes are few → small.
3. **Queryable lineage/event store in Parquet, queried by DuckDB** — promote report-v8's
   already-decoupled sinks to canonical. Keep **every** birth edge (no pruning); columnar
   compression makes 3 M rows ≈ a few MB and pruning becomes a `WHERE`. This is the "what
   happened to who" surface (ancestors/descendants/dominant-genome via SQL, no JVM).
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
- **Delta-encoded frames** — churn (births+deaths ≈ 4000/tick) ≫ standing pop (~56); delta
  would be larger than full frames and add reconstruction complexity for negative gain.
- **Replay-canonical (no live frame file)** — drops live-watch (owner wants it) and
  reintroduces a JVM dependency to view. Stream a lean file instead; it's ~1 MB now.
- **JSONL events (report-v8)** as the lineage store — row-oriented, not built for "walk the
  ancestry" analytics at 3 M rows; Parquet/DuckDB is the spec's existing tool.

## Status note

Direction accepted in a design conversation; implementation pending. Sequence in the spec's
deferred section (close cyclic-replay gap only if checkpoint-export of cyclic frames is later
wanted — not a v13 blocker).
