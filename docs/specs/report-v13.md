# report-v13 — lean every-tick frames + genome catalog + queryable lineage (DuckDB/Parquet)

**Status:** design (accepted direction, not yet implemented) · **Date:** 2026-06-15
**Kernel:** v5 (unchanged) · **ADR:** [0032](../decisions/0032-lean-frames-genome-catalog-query-store.md)
**Delta over:** [report-v12](report-v12.md) (frame format + viewer) and [report-v8](report-v8.md) (observability layer)

## Why

A run emitted 8 files (~84 MB, 96 % `map.txt`) by default, across a CSV zoo that no
inspection workflow actually reads. Root cause: the simulation is **deterministic**, yet we
persisted re-derivable trajectories — and the `map.txt` `u` line re-emitted each unit's full
32-gene genome **and** 24 node-output floats **every frame**, when the genome changes only at
birth and the node floats are detail the viewer does not use.

Measured churn (committed runs `beefy2`, `baseline`) reframed the whole problem:

| run | ticks | avg living pop | max pop | total births | births/tick |
|-----|------:|---------------:|--------:|-------------:|------------:|
| beefy2 | 1500 | ~56 | 693 | 3 044 919 | ~2030 |
| baseline | 1000 | ~33 | 1020 | 1 331 170 | ~1331 |

Standing population is **tiny** (33–56) while births/tick are **huge** (~97 % of births die
childless within a tick or two — boom/bust under cyclic inflow). Consequences:

- **Full frame every tick is cheap.** ~33–56 living rows/tick. A lean row
  (`slot x y mass action genomeId` ≈ 25 B) over a whole run ≈ **~1 MB**, at *full* per-tick
  resolution — smaller than today's sampled 3.7 MB *and* finer.
- **Delta encoding loses.** Per-tick churn (births + deaths ≈ 4000 events) ≫ standing pop
  (~56). Delta only wins on stable populations; this sim turns over completely every tick.
- **The cost moved to lineage.** 3 M birth edges is the heavy, derivable thing — and the
  right home for it is a **columnar query store**, not a flat dump (3 M small-int rows in
  Parquet ≈ a few MB; "walk this lineage" is then SQL, not a scan).

## What the owner actually inspects (the requirements this spec serves)

1. **Every unit, every tick** — not sampled. Position, mass, and which action fired.
2. **Each unit's genome at the current tick** — click a unit → its genome → its circuit.
3. **Evolution per lineage** — the genome history of a line from founder to now.
4. **"What happened to who"** — arbitrary queries (ancestors, descendants, dominant genome
   at tick T) answerable **during or after** the run.
5. Live-watch **and** after-scrub off **one** streamed file, **no JVM reconstruction** to view.
6. Heavy full-state reconstruction is **rare** → opt-in only.

## Artifacts

The wall from report-v8 holds: **kernel stays pure and data-oriented** (flat arrays, no
history, no OO organism graph, byte-identical when sinks are off); everything below is the
harness/observability layer, derived from snapshots with **no kernel event hook**.

### 1. Frame stream — `<base>.frames` (replaces the heavy `map.txt` `u` line)

One header block, then per sampled tick a `t` line, the resource field, and one **lean** line
per living unit:

```
world 100
node-layout 24
t 5
r <resource field, RLE-compressed — see §Resource>
u <slot> <x> <y> <mass> <action> <genomeId>
...
end <finalTick>          # written by close(), report-v12 trailer semantics
```

- `u` fields are **fixed-position** and lean: `slot x y mass action genomeId`.
  - `mass` rounded to ≤3 decimals (report-v11 rounding doctrine).
  - `action` = the effector that fired this tick (`H`arvest / `G`row / `R`eproduce /
    `N`/`S`/`E`/`W` move / `.` none), derived in the observer from the post-evaluate snapshot —
    **not** a kernel concept.
  - `genomeId` = index into the catalog (§2). **No genome bytes on the `u` line.**
- **No node-output floats.** ("Viewer does not need extreme details.")
- **Every tick by default** (`--map-frames` becomes a *sampling* knob, default = every tick;
  `--map-from/--map-to` still windows it). Cheap because pop is tiny.
- Streamed live during the run, O(slots) writer state; tail over HTTP Range (report-v12).

### 2. Genome catalog — `<base>.catalog` (new; global dedup)

Append-only, write-once per **distinct genome ever seen**:

```
g <genomeId> <geneCount> <gene0> <gene1> ...
```

The writer keeps a `Map<genomeKey,int>` (genomeKey = hash of the int[]); on a birth whose
child genome is new, append a `g` line and assign the next id. Frames reference the id. The
viewer joins `u.genomeId → catalog → genes → circuit` client-side. This globalises
report-v11's per-slot dedup: living distinct genomes are few (dozens), so the catalog is
small even though births are millions.

### 3. Lineage / event store — Parquet, queried by DuckDB (promotes report-v8's sinks)

Births/deaths/mutations are written to **Parquet** (not the `.obs/events.jsonl` of v8), the
canonical "what happened to who" store. Every birth edge is kept (no pruning) — columnar
compression makes 3 M rows ≈ a few MB, and pruning becomes a query (`WHERE survived_ticks > K`).

`births` table (one row per birth):

| col | type | meaning |
|-----|------|---------|
| `tick` | int | when born |
| `child_slot` | int | child's slot/cell |
| `parent_slot` | int | parent's slot |
| `child_genome_id` | int | → catalog |
| `parent_genome_id` | int | → catalog |
| `mutated` | bool | child genome ≠ parent (point/indel) |

`deaths` table: `tick`, `slot`, `genome_id`, `cause` (derived: starvation vs reproductive).
`metrics` table: per-tick aggregates (the report-v8 `metrics.csv` content, now Parquet).

All three are derived in the harness from snapshot deltas + child↔parent gene diff (exactly
as `EventLogWriter` already computes them — no new kernel coupling). Queried with **DuckDB**,
no JVM: ancestors of X = recursive CTE over `births`; dominant genome at T = join frames∪catalog.

### 4. Checkpoints — `<base>.obs/ckpt/<t>.ckpt` (report-v8, unchanged, **off by default**)

Full-state reconstruction for the rare case. `--observe` + `--checkpoint-every`. Sink-off
runs stay byte-identical. This is the only path that needs the JVM replayer.

## Retired

Deleted as eager always-on outputs (all re-derivable; none on the inspection path):

- `<base>.units.csv`, `<base>.population.csv` — merge → derive a "final units" view from the
  end-state frame / a replay when wanted.
- `<base>.wiring.csv`, `<base>.popwiring.csv` — genomes-decoded; the catalog + a decoder give
  this on demand.
- `<base>.lineage.csv` — folded into the `births` Parquet table (richer + queryable).
- `<base>.obs/events.jsonl`, `<base>.obs/metrics.csv` — replaced by the Parquet tables;
  `metrics.csv` was a dup of `.timeseries.csv` anyway.
- The heavy `map.txt` `u` line (genome-per-frame + node floats) → §1 lean frame.

**Kept always-on** (tiny, human-facing, no reconstruction): `<base>.txt` report (the 2 KB
"what happened" glance) and `<base>.timeseries.csv` (the one cheap full-run trajectory; or
read the `metrics` Parquet instead and drop it).

## Resource field

Today's `r` line is a per-cell dump (the giant repeated-digit lines). Under uniform inflow it
is constant; under cyclic inflow it is a smooth radial gradient. **RLE-compress** it (or, for
cyclic, store `InflowConfig` + tick and let the viewer recompute the analytic field). Either
removes most of the non-`u` bytes.

## Viewer

`live.html` (report-v12, serverless, client-side parser) gains:

- Parse the lean `u` line (6 fixed fields) + the `catalog` file (fetched alongside, or baked).
- Colour-by-mass (report-v10) and colour-by-action (new, from the `action` field).
- Circuit inspector unchanged — fed by `catalog[genomeId]` instead of inline genome bytes.
- **Lineage view** (new): query the `births` Parquet via **DuckDB-WASM** client-side (or a
  pre-baked lineage JSON for offline) → walk a unit's ancestry, show the genome at each hop
  (catalog diff) = "entire history of its lineage."

`tools/mapviz.py` stays the offline **baker** (inlines frames + catalog + a lineage extract).

## Determinism & doctrine

- Kernel untouched → baseline byte-identical; `state-digest` unchanged.
- Memory doctrine intact: frame/catalog writers keep O(slots) running state, no per-tick
  history in the kernel; the Parquet store is in the harness, streamed, O(1)/tick to append.
- The whole design leans on determinism (report-v8): the canonical *recoverable* state is
  manifest + checkpoints; frames/catalog/Parquet are derived **views** materialised for
  inspection, now lean enough to keep streamed live.

## Open / deferred

- **Cyclic-inflow replay gap** (ADR 0029): `--resource-cycle` + replay is guarded off because
  `RunManifest` doesn't serialise `InflowConfig`. If checkpoint-replay export of frames is
  ever wanted for cyclic runs, this must close. Not a blocker for v13 (frames stream live).
- **DuckDB-WASM in the viewer** vs a pre-baked lineage JSON — pick during implementation by
  front-end weight.
- Per-unit Parquet *frame* table (trajectory queries) — optional; the flat `.frames` already
  serves the visual path. Add only if "query a unit's path" is wanted in SQL.
