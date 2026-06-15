# Handoff — redesign run inspection & data

**Date:** 2026-06-15 · **Status:** ⛳ DECIDED 2026-06-15 — see
[report-v13](../specs/report-v13.md) + [ADR 0032](../decisions/0032-lean-frames-genome-catalog-query-store.md).
Implementation pending. The framing below stands; the direction chosen differs from the
doc's original "Direction A recommended" — read the decision note next.
**For:** the owner + whoever picks this up next session.

## DECISION (supersedes the "Recommended direction" framing below)

Measurement settled it. (The `.timeseries.csv` `births` column is **cumulative** — read as
per-tick it looked like "~2000 births/tick"; the real per-tick rate is its diff.) Truth: a
colonization **boom** (up to ~187 births/tick briefly) into a near-static plateau; **births-
total is only ~1–2 k per run**, standing pop swings (peak ~1020). So:

- **Full every-tick frames chosen** — cheap (~0.7 MB, per-row cost down ~13× via catalog +
  no node floats) and O(1) seekable. Delta would be marginally smaller but needs keyframe
  accumulation; not worth it on a sub-MB file. A lean row `cell slot mass action genomeId`.
- **Genome catalog** (global write-once `genomeId→genes`) dedups genomes out of the frames.
- **Lineage = small but naturally queryable** → a **gzipped-CSV births store queried by DuckDB**
  (dep-free; DuckDB reads `.csv.gz`, can `COPY` to Parquet). Keep every birth; pruning is a `WHERE`.
- **Kernel stays flat-array / data-oriented — DB yes, OO organisms no** (owner proposed OO; the
  query store delivers "what happened to who" without breaking the core invariant).
- **Retire the CSV zoo**; keep the 2 KB `.txt` + (optionally) `.timeseries.csv`. Checkpoints
  stay opt-in (`--observe`).
- **Frames stream live** (serverless tail, report-v12) so live-watch + after-scrub share one
  file with no JVM.

Everything below is the original problem framing that led here — still accurate, kept for context.

## TL;DR

A run writes **8 files (~84 MB, 96 % of it `map.txt`)** by default, up to **12** with
`--observe`, across **13 writer classes**, **4 Python tools**, and **9 accreted report
schemas (v4–v12)**. Much of it overlaps or is re-derivable. The owner's read — "we don't
need THIS much data, we're being weird" — is correct.

**The weirdness has one root cause:** the simulation is **deterministic** (same seed +
config → identical history, an invariant), yet we **persist trajectories we could
regenerate**. Storing derived data from a deterministic source is the anti-pattern.
report-v8 already built the cure (`manifest + checkpoints → recompute any tick`) but
**added it alongside** the other outputs instead of making it the source of truth.

**Recommended direction:** make **replay-key + sparse checkpoints the only canonical
artifact**; treat the report, CSVs, map frames, and timeseries as **derived views,
materialized on demand** — and crucially, materialize only the *small window you actually
want to look at*, then view it with the (already serverless) viewer. The 84 MB whole-run
`map.txt` stops existing.

## Current state — what a run emits

Measured on the seed-100 cyclic run (300 units, 8000 ticks, world 100, 200 map frames):

| artifact | size | what it is | re-derivable from seed+config? |
|----------|------|-----------|-------------------------------|
| `.txt` | 2 KB | report-v10 text aggregates (human-facing) | yes (it's a summary of a replay) |
| `.units.csv` | 53 KB | founder-indexed per-unit final state | yes |
| `.population.csv` | 67 KB | final **living** per-unit | yes — and overlaps `.units.csv` |
| `.wiring.csv` | 455 KB | **founder** compiled connections (9600 rows) | yes — it's just founder genomes decoded (already in manifest) |
| `.popwiring.csv` | 2.7 MB | **evolved living** connections | yes — decode living genomes on demand |
| `.lineage.csv` | 7.5 KB | per-lineage rollup | yes |
| `.timeseries.csv` | 56 KB | per-tick aggregates | yes |
| `.map.txt` | **84 MB** | spatial frames: genome + 24 node outputs per living unit per sampled frame | yes |
| `.obs/manifest.json` | tiny | replay key (founder genomes + config hash) | — (this IS the source) |
| `.obs/ckpt/<t>.ckpt` | bounded | full-state checkpoints every C ticks | — (this IS the source) |
| `.obs/events.jsonl` | varies | births/deaths/mutations (derived from snapshot deltas) | yes |
| `.obs/metrics.csv` | varies | per-tick aggregates | yes — **dup of `.timeseries.csv`** |

Tools: `visualize.py` (.txt → dashboard), `mapviz.py` (map → baked HTML), `timetravel.py`
(obs snapshot → HTML), `journey.py` (story), plus `live.html` (the rich viewer).

### Redundancy map (the "weird")
- **Two wiring dumps**: `.wiring.csv` (founder, compiled) + `.popwiring.csv` (evolved, live). 3.2 MB combined, both just genomes-decoded.
- **Two per-unit dumps**: `.units.csv` (founder-indexed) + `.population.csv` (final living).
- **Two per-tick dumps**: `.timeseries.csv` + `.obs/metrics.csv`.
- **map.txt** carries genome+outputs *per unit per frame* — the single biggest cost, and entirely a function of the replayable state.
- **9 schema versions** (v4–v12) accreted; each added a surface, none retired one.

## The principle — determinism is the compression

Invariants (do not break): *fixed seed, no wall-clock, fixed evaluation order → identical
tick history, always.* That means **the entire trajectory is a pure function of
`(founder genomes, config, seed)`**. report-v8 (ADR 0023) exploited this for time-travel:
store the replay key + periodic checkpoints, recompute any tick on demand, sink-off runs
stay byte-identical.

So the canonical artifact of a run *should be* that replay bundle. Everything else — the
report, every CSV, the map frames, the timeseries — is a **view** of a replay. Persisting
them is caching a pure function's output to disk, forever, at 84 MB a run.

### The tension to resolve (don't undo report-v12)

We *just* made the viewer **serverless** (report-v12, ADR 0031): `live.html` reads a flat
`.map.txt` with no JVM. "Derive frames on demand" naively means "run the JVM replayer to
feed the viewer" → that reintroduces a server.

**Resolution (recommended):** replay **materializes a small flat frame-window** to disk,
then the serverless viewer reads it. CLI shape:
```
./gradlew run --args="--replay foo.obs --frames 6000-6160 --map-out window.map.txt"
python3 tools/mapviz.py window.map.txt    # or just serve it — viewer unchanged
```
You store **tiny** (manifest + checkpoints), and only ever materialize the **few hundred
frames you actually want to inspect** — never the whole-run 84 MB dump. The viewer stays
serverless; the giant file disappears. Best of both.

## Redesign directions (owner picks)

- **A — Replay-canonical (recommended).** Canonical store = `manifest + checkpoints`
  (promote `--observe` from optional to the default/only persistence). Report, CSVs, map
  windows = derived on demand via the replayer. `map.txt` whole-run persistence killed;
  replaced by on-demand windowed export. Biggest data win; leans entirely on determinism.
- **B — Lean-persist.** Keep persisting eagerly, but **collapse** the overlaps: one wiring
  view, one per-unit view, one per-tick view; trim `map.txt` further (drop node outputs
  unless asked; RLE the `r` field). Smaller change, keeps current "everything on disk"
  model, doesn't fix the root anti-pattern.
- **C — Hybrid.** Replay-canonical for the heavy/derivable stuff (map, wiring, per-unit),
  but keep the **2 KB `.txt` report** and maybe the **56 KB `.timeseries.csv`** always-on
  (they're tiny, human-facing, and the cheap "what happened" glance). Probably the
  pragmatic sweet spot.

## Per-artifact recommendation (keep / merge / derive / kill)

| artifact | recommendation |
|----------|----------------|
| `.txt` report | **KEEP** — 2 KB, human-facing, the one always-on summary. Consolidate its schema lineage (see below). |
| `.timeseries.csv` | **KEEP or DERIVE** — tiny; the one cheap full-run trajectory. If kept, **delete `.obs/metrics.csv`** (dup). |
| `.units.csv` + `.population.csv` | **MERGE → one "final units" view, DERIVED** from the end-state replay. |
| `.wiring.csv` + `.popwiring.csv` | **DERIVE on demand** — both are genomes-decoded; the manifest already has founders, living genomes come from a replay. Kill the 3.2 MB eager dumps. |
| `.lineage.csv` | **FOLD into the report** or derive. |
| `.map.txt` (84 MB) | **KILL whole-run persistence.** Materialize small windowed frame-files on demand via replay; view serverless (report-v12 viewer unchanged). |
| `.obs/manifest + ckpt` | **KEEP — promote to canonical** (the source of truth). |
| `.obs/events.jsonl` | **DERIVE on demand** from replay deltas (already how it's computed; just don't persist eagerly). |

## Open questions for the owner

1. **Interactive scrub latency.** On-demand frame materialization replays from the nearest
   checkpoint forward. For a 200-frame window that's cheap; for scrubbing arbitrary ticks
   it's replay+cache. Acceptable, or do you want precomputed frames for snappiness on some
   runs?
2. **Live watching.** Watching a run *as it ticks* — keep streaming a **lean** live frame
   file during the run (small, the run's happening anyway), or drop live-watch and inspect
   after via replay? (report-v12's live tail assumes a streamed file.)
3. **Checkpoint cadence.** How far back do you actually look? That sets `--checkpoint-every`
   and the storage/replay-cost trade.
4. **Cyclic-inflow replay gap.** `--resource-cycle` + replay is currently **guarded off**
   (manifest doesn't carry the inflow pattern, ADR 0029). If replay becomes canonical, this
   MUST close — the manifest must serialize `InflowConfig`. Non-negotiable for direction A/C.
5. **Keep the rich circuit viewer?** (Assumed yes — it's the good part. It just gets fed by
   on-demand windows instead of an 84 MB dump.)
6. **Tools.** `visualize.py`, `journey.py`, `timetravel.py` — keep all? `journey.py` (story)
   and `visualize.py` (.txt dashboard) may be one-offs. Consolidate to: one rich spatial
   viewer (`live.html`) + one replayer CLI?

## Suggested sequence (if direction A/C chosen)

1. **Close the cyclic-replay gap** — serialize `InflowConfig` into `RunManifest`; drop the
   guard. (Prereq for replay-canonical.)
2. **Replayer → windowed frame export** — `--replay X --frames T0-T1 --map-out f.map.txt`
   emitting the report-v11/v12 frame format. Viewer needs zero change.
3. **Collapse CSVs** — one per-unit view, one wiring view, one per-tick view; delete the
   dups (`metrics.csv` vs `timeseries.csv`, `wiring` vs `popwiring`, `units` vs `population`).
4. **One report spec** — fold v4–v12 into a single current schema doc; mark the rest
   historical. (Schemas describe surfaces; if surfaces collapse, so do schemas.)
5. **Make `map.txt` non-default** — stop the eager 84 MB write; produce it only via the
   windowed export. Keep `--map-frames`/`--map-from/--to` as the export knobs.
6. **Trim what remains** if still heavy — drop node outputs from frames unless requested,
   RLE the `r` field.

## Don't-break list

- **Determinism, memory doctrine (footprint linear in units, constant in ticks), kernel
  purity (no semantics in the kernel).** The whole redesign *depends* on determinism — it's
  load-bearing now, not just an invariant.
- **Uncommitted work on `kernel-v5-biomass`:** report-v11 (compact frames — genome dedup +
  output rounding) and report-v12 (serverless viewer — `LiveServer`/`--serve` deleted, fixed
  v5 node names, `end` trailer) are **shipped + verified but NOT committed**. Decide:
  commit them first (they're net wins and the viewer change is reused by this redesign), or
  fold into the redesign branch. Recommendation: **commit both** — report-v12's serverless
  viewer is exactly the front-end this redesign wants; report-v11's frame compaction is
  still useful for the windowed exports.
- The 64-test suite is green at the v11/v12 tip; keep it that way.

## Why this is the right framing

The owner didn't ask "make the files smaller" — they said "we're being weird." The weird
part isn't the size, it's **persisting the output of a deterministic function**. Fix the
concept (replay is the source; views are derived and ephemeral) and the size problem
dissolves as a side effect: ~84 MB/run → a small checkpoint bundle + whatever tiny window
you choose to look at.
