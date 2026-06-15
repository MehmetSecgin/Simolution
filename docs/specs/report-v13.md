# report-v13 — lean every-tick frames + genome catalog + queryable lineage (DuckDB/Parquet)

**Status:** implemented 2026-06-15 (branch `kernel-v5-biomass`) · **Kernel:** v5 (unchanged)
**ADR:** [0032](../decisions/0032-lean-frames-genome-catalog-query-store.md)
**Delta over:** [report-v12](report-v12.md) (frame format + viewer) and [report-v8](report-v8.md) (observability layer)

## Why

A run emitted 8 files (~84 MB, 96 % `map.txt`) by default, across a CSV zoo that no
inspection workflow actually reads. Root cause: the simulation is **deterministic**, yet we
persisted re-derivable trajectories — and the `map.txt` `u` line re-emitted each unit's full
32-gene genome **and** 24 node-output floats **every frame**, when the genome changes only at
birth and the node floats are detail the viewer does not use.

Measured the committed runs (`baseline`, `beefy2`). The `births` column in
`.timeseries.csv` is **cumulative** (= `births-total`); the per-tick rate is its diff:

| run | ticks | avg living pop | max pop | births-total | max births/tick | avg births/tick |
|-----|------:|---------------:|--------:|-------------:|----------------:|----------------:|
| baseline | 1000 | ~33 | 1020 | **1 364** | 187 | 1.36 |
| beefy2 | 1500 | ~56 | 693 | **2 084** | 154 | 1.39 |

The shape: a fast colonization **boom** (tens–hundreds of births/tick for a few dozen ticks)
into a long, near-static plateau (≈0 births/tick) — population swings (peak ~1020, often
crashing toward extinction), but total births over a whole run are only **~1–2 k**.
Consequences:

- **Full frame every tick is cheap.** Per-row cost fell ~13× (genome → catalog id, node
  outputs dropped), so a lean row over a whole run ≈ **~0.7 MB**, at *full* per-tick
  resolution — smaller than today's sampled 3.7 MB *and* finer.
- **Full frame chosen over delta — for seek + simplicity, not size.** A delta stream would
  in fact be marginally *smaller* here (per-tick churn is low outside the boom), but it needs
  client-side accumulation from a keyframe to land on any tick; full frames give **O(1) random
  seek** (the scrub workflow) and a trivial parser, and the file is already tiny. Delta's
  complexity buys nothing on a sub-MB file.
- **Lineage is small but the right home is still a query store.** Only ~1–2 k birth edges per
  run, but "walk this unit's ancestry / who descended from founder N" is naturally SQL, not a
  scan — and the store stays trivial (gzipped CSV ≈ a few KB; DuckDB reads it directly).

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
small even though births burst into the hundreds per tick during colonization.

### 3. Lineage / births store — `<base>.births.csv.gz`, queried by DuckDB

The canonical "what happened to who" store: one row per birth, written by `BirthLogWriter`,
**always-on** when `--out` is set. Every birth edge is kept (no pruning — only ~1–2 k per run;
pruning is a `WHERE`). Written as **gzipped CSV** (a few KB), which keeps the toolchain
**dependency-free** — no Java Parquet writer (the performance doctrine forbids the heavy dep)
— while DuckDB reads `.csv.gz` directly and can `COPY … TO 'x.parquet'` if a columnar copy is
ever wanted. Header + columns:

| col | type | meaning |
|-----|------|---------|
| `tick` | int | when born |
| `child_slot` | int | child's slot (= cell at birth) |
| `lineage` | long | founder-rooted lineage id (kernel-exact) |
| `generation` | int | child's generation (kernel-exact) |
| `parent_slot` | int | parent's slot, or `-1` if the Moore-neighbour match is ambiguous |
| `child_genome_id` | int | → catalog |
| `parent_genome_id` | int | → catalog, or `-1` if parent unknown |
| `mutated` | int | `1` child genome ≠ parent, `0` identical, `-1` parent unknown |

Births are **derived** from snapshot deltas + the shared catalog ids (exactly as
`EventLogWriter` derives them — no new kernel coupling; `mutated` is exact because catalog ids
are content-addressed). Queried with **DuckDB**, no JVM.

**Two query spines, by reliability.** `lineage` + `generation` are kernel-exact, so the
owner's "evolution per lineage" is robust — the genome history of a line is
`SELECT DISTINCT generation, child_genome_id, min(tick) FROM births WHERE lineage=L
GROUP BY 1,2 ORDER BY 1`, then diff the catalog genes hop to hop. `parent_slot` is best-effort
(the Moore-neighbour heuristic; on the baseline ~half of boom-era births are ambiguous → `-1`
because clustered same-lineage same-generation siblings can't be told apart) — fine for
exact-parent CTEs when unique, but the lineage/generation spine is the dependable one.

> One honest limit (shared with `EventLogWriter`): a birth + death of the *same* slot within a
> single tick is invisible to once-per-tick snapshot deltas. The kernel's `births-total` is the
> ground truth; the store matched it to the row on the baseline (1363 vs 1364), so this case is
> negligible in practice, not a silent gap.

Deaths/lifespan and a per-tick metrics table are natural future additions to the same store
(report-v8's `metrics.csv` content); not built in this pass — the births edges are the lineage
backbone the owner asked for.

### 4. Checkpoints — `<base>.obs/ckpt/<t>.ckpt` (report-v8, unchanged, **off by default**)

Full-state reconstruction for the rare case. `--observe` + `--checkpoint-every`. Sink-off
runs stay byte-identical. This is the only path that needs the JVM replayer.

## Retired

Deleted as eager always-on outputs (all re-derivable; none on the inspection path).
Renderer classes + their tests removed, not just the call sites (no-shim rule):

- `<base>.units.csv` (`UnitCsvReport`), `<base>.population.csv` (`PopulationReport`) — derive
  a "final units" view from the end-state frame / a replay when wanted.
- `<base>.wiring.csv` + `<base>.popwiring.csv` (`WiringReport`) — genomes-decoded; the catalog
  + a decoder give this on demand.
- `<base>.lineage.csv` (`LineageReport`) — superseded by the queryable births store (§3).
  (`DynamicsObserver.lineageSummary()` is left in place but now unused — removing it means
  unwinding its per-tick backing arrays, a separate refactor.)
- The heavy `map.txt` `u` line (genome-per-frame + node floats) → §1 lean frame.

The `--observe` sinks (`events.jsonl`, `metrics.csv`, checkpoints) are **unchanged** and stay
opt-in (report-v8); only the always-on eager writes above were cut.

**Kept always-on** (tiny, human-facing, no reconstruction): `<base>.txt` report (the 2 KB
"what happened" glance) and `<base>.timeseries.csv` (the one cheap full-run trajectory; or
read the `metrics` Parquet instead and drop it).

## Resource field

Today's `r` line is a per-cell dump (the giant repeated-digit lines). Under uniform inflow it
is constant; under cyclic inflow it is a smooth radial gradient. **RLE-compress** it (or, for
cyclic, store `InflowConfig` + tick and let the viewer recompute the analytic field). Either
removes most of the non-`u` bytes.

## Index sidecars + on-demand viewing

Every-tick is cheap on a *small* population (baseline crashed to 5 → 678 KB) but not on a
sustained one: the cyclic seed-100 run holds ~1600 units → **117 MB at 3000 ticks, 342 MB at
8000**, with a 9–30 MB catalog (tens of thousands of distinct genomes). Loading that wholesale
pins the browser near 1 GB. So the writer emits **byte-offset indexes** and the viewer fetches
on demand:

- `<base>.frames.idx` — `<tick> <byteOffset> <byteLen>` per frame (+ an `end` trailer).
- `<base>.catalog.idx` — `<gid> <count> <byteOffset> <byteLen>` per genome (the `count` lets the
  viewer list genomes by gene-count without fetching their genes).

All content is ASCII, so char length == byte length; the writers keep an O(1) byte cursor.

**Range, not whole-file.** The viewer loads only the tiny indexes (baseline 14 KB + 6 KB;
cyclic 58 KB + 502 KB), then HTTP-**Range**-fetches *one frame* on scrub and *one genome* on
inspect. Frame cache capped (120); genomes resolved lazily. Browser RAM is O(window) regardless
of run length — measured **7 MB** on the 117 MB run, with only 1.5 MB transferred for 38 frames
viewed.

**This needs a Range-capable static server.** Python's stdlib `http.server` *ignores* `Range`
and returns the whole file (`200`) — which silently defeated even report-v12's "tail via Range"
(it re-downloaded everything each poll). `scripts/serve.py` is a ~25-line stdlib subclass that
answers `206 Partial Content`: a **generic static server** (no app endpoints), not the bespoke
`LiveServer` report-v12 deleted. `serve-live.sh` uses it. This is the honest correction to
report-v12's "any static server" claim.

## Viewer

`live.html` parses everything client-side, two modes:

- **embed** — `window.SIMOLUTION_EMBED` baked by `tools/mapviz.py` (offline `file://`, small runs
  only — everything in RAM).
- **indexed** — served run: the index + on-demand Range fetch above. Auto-detected.

Both gain: colour by **genome / mass / action** (action from the new field, with a legend),
genome grouping by **catalog id** (exact), and the force-directed circuit inspector fed by
`catalog[genomeId]` (structural — report-v13 dropped per-tick node outputs, so no signal-glow).

`tools/mapviz.py` is the offline **baker** (inlines frames + catalog) — for small runs; large
runs are inspected served (indexed).

**In-viewer lineage walk** (over the births store, client-side — no DuckDB): "trace selected"
fetches `<base>.births.csv.gz` and gunzips it in-browser (`DecompressionStream`), maps the
selected unit to its lineage, and renders the line's **evolution**. A lineage is a wide bush,
not a chain — ~half of births are the unattributed fission half (`parent_slot=-1`) — so the
walk anchors on the kernel-exact `lineage`/`generation`/`mutated` fields, not on chasing
`parent_slot`. It shows a **generation timeline** (≤ maxGen rows): per generation, distinct-
genome and mutation counts, expandable to the `#parent → #child` mutation transitions (capped);
clicking any genome renders its circuit + a gene diff vs its parent. Births load lazily on first
trace (3.6 MB uncompressed on the cyclic run). Embed/offline mode shows a notice unless births
are baked.

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
