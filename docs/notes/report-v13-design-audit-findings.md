# report-v13 data-design audit — findings

> **Implemented 2026-06-15 as report-v14** ([spec](../specs/report-v14.md), [ADR 0033](../decisions/0033-lean-zstd-frames.md)).
> Owner locked per-tick + chose zstd + drop mass/action. Result on the cyclic run: **128.6 MB → 4.2 MB (~31×)**,
> baseline 890 KB → 147 KB, every tick preserved, kernel digest unchanged. See the REVISION section below for the
> as-built design; the original A–D analysis is kept as the reasoning record.


Adversarial re-derivation of the run-inspection / storage design. Verdict up front:
**the owner is right — the design stores ~26× more than the problem needs and carries ~2× the
moving parts, and the biggest cause is a requirement ("every unit, every tick") that no
inspection task actually needs at whole-run scale.** Recommend end-state **B-lean** (sampled +
compressed frames, gzipped catalog, no index sidecars, no Range server, one viewer mode), with
opt-in replay as the exact-window backstop.

## Measured ground truth (verified against the two real runs)

`baseline` = uniform, 100 units, 1000 ticks, world 100 (pop crashed to ~5).
`live` = cyclic, 300 units, 3000 ticks, world 100, seed 100 (sustained ~1480 units).

| file | baseline | live | live notes |
|------|---------:|-----:|-----------|
| `.txt` | 1.9 KB | 1.9 KB | human glance |
| `.timeseries.csv` | 49.8 KB | 55.6 KB | per-tick aggregates |
| `.births.csv.gz` | 8.8 KB | **872 KB** | 117 503 rows |
| `.catalog` | 131 KB | **9.15 MB** | 24 579 distinct genomes, ~35 genes ea, ~372 B/genome |
| `.catalog.idx` | 6.1 KB | 490 KB | |
| `.frames` | 678 KB | **112 MB** | 3000 frames, **4 436 900** u-rows |
| `.frames.idx` | 15 KB | 57 KB | |
| **total** | **890 KB** | **128.6 MB** | |

Facts that decide the audit (all re-measured, the misread ones corrected):

1. **"Pop is tiny → every-tick is cheap" is false on the run that matters.** live averages
   `4 436 900 / 3000 ≈ 1480` living units/tick. The spec's "tiny" (avg ~33) was the *crashed
   baseline*. Every-tick-full on a sustained population is **112 MB**, not "sub-MB." The whole
   "the file is already tiny, delta buys nothing, indexing handles bigness" chain rests on the
   wrong run.
2. **genomeId is 97% redundant per-tick.** It changes only at birth: ~117 k changes across
   4.44 M rows = **2.6%**. The other 97.4% re-emit the slot's unchanged id.
3. **The catalog is near-duplicate data.** 24 579 genomes, but only **12 474** births ever
   introduced a *changed* genome (mutated=1); 47 120 were identical-to-parent, 57 908 (49.3%)
   are the unattributed fission half. Genomes differ from a sibling by ~1 gene → catalog
   **gzip 19.7×** (9.15 MB → 487 KB), **zstd-19 32×** (→ 296 KB).
4. **Frames are uncompressed and hugely redundant.** zstd-19 **19.3×** (112 MB → 6.08 MB);
   gzip only 2.9× (small window misses the cross-frame repetition). They are plain ASCII today.
5. **Resource line ≈ 10.6 MB** of live frames (3530 B/frame × 3000), already RLE'd.
6. **Sampling is already wired and free.** `--map-frames` → `sampleEvery`; the *default is 0 =
   every tick*. Changing the default samples at zero new code.
7. **`.obs/metrics.csv` is off by default** (no `.obs` dir on either run); `.timeseries.csv`
   is the always-on trajectory. Not a live duplication today.

## Decision ledger

### 1. Every-tick full frames — **REPLACE (sample by default)**
No requirement needs tick-by-tick over the *whole* run. Visual scrub is smooth at ~300 frames;
lineage/genome history is birth-driven (births log + catalog), independent of frame rate.
**Evidence:** every-tick = 112 MB on live; the 6 stated requirements are all served by
sampled-overview + (rare) on-demand exact window. **Alternative:** default `--map-frames` to
~300–500 target frames; keep `--map-from/--map-to` for a tick-exact window when wanted; full
fidelity over any window via opt-in `--observe` replay (already exists, req #6 says heavy
reconstruction is rare/opt-in — consistent). **Bytes:** live frames 112 MB → ~10 MB raw (~3.5 MB
gz). **Complexity delta:** −0 code (knob exists), and it *cascades* to kill items 4 & 9 below.

### 2. Frame row fields `mass`+`action`+`genomeId` — **SIMPLIFY**
- `genomeId`: **drop from per-tick rows.** 97.4% redundant; derive each slot's current genome
  by carrying forward from its last birth (births log already has `tick,child_slot,child_genome_id`).
  Viewer keeps an O(slots) "current genome per slot" map. **Bytes:** removes ~1 field × 4.44 M
  rows. **Complexity:** +small viewer state, −1 column. *Optional* — gzip already crushes a
  constant column, so only do this if staying uncompressed.
- `mass`: keep but already rounded ≤3 dec; fine.
- `action`: keep (sole consumer = colour-by-action; it is *derivable* from genome+state but only
  via full re-evaluation, not worth the recompute). 1 char, cheap.

### 3. Catalog as full genomes vs diff-genome — **SIMPLIFY (gzip), not REPLACE (diff)**
Diff-against-parent (end-state B) would shrink the catalog ~10× but needs a parent anchor for
every genome — and **49.3% of births have no attributable parent** (`-1`), so the diff would
fall back to nearest-neighbour search or keyframes: real code for a marginal win over gzip.
**Evidence:** `gzip` already gets 9.15 MB → **487 KB (19.7×)**, zstd → 296 KB (32×), at *zero*
new code. Diff-genome lands ~300 KB with a reconstruction walk + parent-resolution heuristic.
**Verdict:** ship `.catalog.gz`; **kill the diff-genome idea** — gzip wins on simplicity at
equal size.

### 4. Two index sidecars + Range server — **KILL** (consequence of item 1)
They exist *only* because every-tick-full made files too big to whole-load. Sample (item 1) →
frames ~3.5 MB gz → the viewer whole-loads (the indexed path measured 7 MB heap; a 3.5 MB whole
load is less). Then `.frames.idx`, `.catalog.idx`, `scripts/serve.py` (67 lines), and the
HTTP-Range code path all delete. **Bytes:** −547 KB idx (live). **Complexity:** −2 files,
−1 bespoke server, −1 viewer code path, restores the report-v12 "any static server / `file://`"
promise that Range broke.

### 5. `.births.csv.gz` keeps every edge incl. 49% `-1` — **KEEP**
Don't prune the fission halves: the `-1` rows still carry **kernel-exact `lineage`,
`generation`, `mutated`** — the dependable query spine. Only `parent_slot`/`parent_genome_id`
are `-1`. Pruning them would drop real lineage nodes. **Evidence:** 872 KB gz for 117 k rows is
cheap and is the *canonical* lineage store. **Keep as-is.**

### 6. Resource field per frame — **SIMPLIFY (don't drop)**
~10.6 MB on live; under cyclic it's analytic-ish but **diffusion is path-dependent**, so the
viewer can't recompute it from `InflowConfig` without re-running diffusion. Sampling (item 1)
already cuts it 10× (→ ~1 MB), and gzip crushes the repeated RLE runs further. **Verdict:** keep
it in the frame, let sampling + compression handle it; do **not** add a viewer-side diffusion
recompute (new code, fragile).

### 7. `.timeseries.csv` vs `.obs/metrics.csv` — **KEEP timeseries, leave metrics opt-in**
No duplication today (`.obs` off by default). `.timeseries.csv` is the one always-on cheap
trajectory. gzip it too (→ ~10 KB) if you want; minor.

### 8. `.txt` report — **KEEP, fix schema label**
Tiny (1.9 KB) and human-facing. But it still prints `schema: report-v10` while frames are v13 —
stale. **Bump the label to report-v13** (one-line fix); no need to consolidate the schema history.

### 9. Two viewer modes (embed baker + indexed served) — **SIMPLIFY to one** (consequence of item 1)
With sampled+compressed frames small enough to whole-load, the "indexed/Range" mode is
unnecessary. Collapse to **one mode: whole-load one `.frames(.gz)` file**, served by any static
server *or* opened from `file://`. `tools/mapviz.py` baker becomes optional (only needed to bake
a single self-contained HTML; the served path no longer differs). **Complexity:** −1 viewer mode,
−auto-detect branch.

### 10. Bespoke text formats vs single SQLite/DuckDB file (sql.js/duckdb-wasm) — **KILL (don't adopt)**
Tempting (one artifact, no `.idx`, no Range server, lib handles seeking), but: a Java SQLite/
DuckDB *writer* is a heavyweight dep the performance doctrine forbids, and sql.js/duckdb-wasm is
~1 MB+ of browser weight against the "simpler" axis. The gzip+sample path already reaches ~5
small files with **zero new deps**, so SQLite's marginal file-count win isn't worth the dep
weight + total writer/viewer rewrite. **Verdict:** keep dependency-free text+gz; revisit only if
SQL trajectory queries become a hard requirement.

### 11. Live-watch as a hard requirement — **KEEP, but it's cheap once sampled**
Sampled frames still stream live (writer appends every `sampleEvery` ticks); tailing a small
growing file works on any static server without Range. Live-watch does **not** force the index/
Range machinery — every-tick-bigness did. So keep live-watch; it costs nothing after item 1.

### 12. "Stream frames" vs "store only manifest+checkpoints, materialize on demand" (D) — **HYBRID**
Pure replay-canonical (D) stores almost nothing but needs the JVM for *every* view and loses
live-watch + no-JVM glance — too much given up. But its strength (materialize an exact window on
demand) is exactly the right backstop for the rare tick-by-tick need. **Verdict:** sampled
streamed frames for the 99% visual/live path (no JVM), opt-in `--observe` replay for the 1%
exact-window path. That's the hybrid below.

### Extras not in the list
- **`.txt` schema lineage** (item 8) — stale label, fix.
- **gzip everything ASCII** — frames, catalog, timeseries are all plain text and all compress
  3–32×. The single highest-leverage, lowest-risk change in the whole audit; the toolchain
  *already* gunzips client-side (births.csv.gz via `DecompressionStream`), so the precedent and
  the browser support exist.

## REVISION — owner locked the requirements (every-tick stays; chose zstd + drop mass/action)

After the first pass the owner fixed two things, which **supersede ledger items 1, 4, 9, 11**:

- **Per-tick is mandatory.** "I want to watch it tick by tick." → **item 1 flips to KEEP**
  (no sampling default). Random seek + live tail stay hard requirements → **item 4 (index/Range)
  and item 11 (live-watch) flip to KEEP**, and the served indexed viewer mode stays (**item 9:
  keep the served mode, embed/offline baker optional**).
- **For seeking the owner needs only: the map (resource field), unit location, and genome/lineage
  info.** → per-tick frame drops **`mass` + `action`** (the two fields he doesn't need).
- **Codec choice: zstd** (smallest), accepting a small browser decoder dep over gzip's weaker
  ratio. **Drop mass + action: yes.**

### Why this is the right lever (measured)

Tick-to-tick churn on the cyclic run (1899 matched slots, tick 1500→1501):

| field | % of units changed/tick | per-tick necessity |
|-------|------------------------:|--------------------|
| `cell` (position) | **6.5%** | needed (the thing watched) |
| `genomeId` | **2.8%** | needed (colour/lineage) — keep, ~free compressed |
| `mass` | 14.6% | **dropped** (not needed for seek) |
| `action` | **33.4%** | **dropped** (highest entropy → was the main compression-killer) |

Lean row `u <cell> <slot> <genomeId>` measured on the live run:

| frame content | raw | whole-file gzip | whole-file zstd-19 |
|---------------|----:|----------------:|-------------------:|
| current `cell slot mass action gid` | 112 MB | 41 MB (2.9×) | 6.1 MB (19×) |
| **lean `cell slot gid`** | 82 MB | 19.5 MB (4.2×) | **2.66 MB (31× vs lean, 44× vs current)** |
| pos-only `cell slot` (genome via births join) | 59 MB | 3.8 MB | 2.2 MB |

`genomeId` costs only ~0.45 MB compressed → **keep it in the frame** (direct genome colour +
circuit; no per-tick births join). Lineage still comes from the births store (already loaded).

### The one real complication: zstd's window vs per-tick seek + live append

zstd's 31× needs a **multi-frame window**; a single frame compresses only ~3× (measured 49 KB →
15.8 KB), and gzip can't capture cross-frame at all (32 KB window — 64-frame gzip chunks = 18.8 MB,
no better than whole-file gzip 19.5 MB). So: compress in **chunks of N frames** (N≈256),
zstd-19 each, index by chunk. Seek to tick T → Range-fetch the one chunk, zstd-decompress in the
viewer, locate the frame (scan its `t` lines). RAM = one chunk (~7 MB decompressed). Live tail:
the **open (in-progress) chunk is written raw**; viewer reads the raw tail for follow-live and the
sealed zstd chunks for history. When the open chunk fills N frames it is sealed → zstd + indexed,
a new raw chunk starts. This is the log-plus-compacted-segments pattern; it is the cost of
combining max compression with per-tick seek and live.

Browser decoder: use a tiny decompress-only zstd (e.g. `fzstd`, ~8 KB gzipped — far less than the
~600 KB I first quoted; the "heavy dep" worry is moot for decompress-only). Catalog + births stay
**gzip** (browser-native `DecompressionStream`, and DuckDB reads `.gz`); only frame chunks need zstd.

### Recommended end-state: **lean + chunked-zstd frames, every tick, indexed**

| dimension | (A) current | **recommended** |
|-----------|------------:|----------------:|
| files/run | 7 (+2 idx) | **6** (.txt, .timeseries.csv, .births.csv.gz, .frames.zst, .frames.idx, .catalog.gz) |
| index sidecars | 2 (.frames.idx, .catalog.idx) | **1** (.frames.idx, now per-chunk) — catalog.idx dropped |
| servers | bespoke Range (`serve.py`) | bespoke Range (`serve.py`) — **kept** (per-tick seek needs it) |
| viewer modes | 2 (embed + indexed) | 2 (indexed served + offline baker) — kept; +tiny zstd decoder |
| new deps | 0 | **1 browser-only** (`fzstd` decompress, ~8 KB) — no Java dep |
| per-tick resolution | every tick | **every tick** (unchanged) |
| **baseline total** | **890 KB** | **~140 KB** (~6×) |
| **live total** | **128.6 MB** | **~5 MB** (~26×) |

Live breakdown: frames ~3.5 MB (chunked zstd; whole-file is 2.66 MB, chunks lose a little) +
catalog 0.49 MB (gzip; drop the 490 KB `.catalog.idx`, whole-load+decompress once → 9 MB RAM, fine)
+ births 0.87 MB + timeseries 0.056 MB + txt 0.002 MB ≈ **~4.9 MB**. Browser RAM to inspect:
O(one chunk) ≈ 7 MB (same order as today's indexed 7 MB), at full per-tick fidelity.

Catalog: drop `.catalog.idx` and the per-genome Range path — gzip the whole catalog (9.15 MB →
0.49 MB), whole-load + `DecompressionStream('gzip')` on first genome need, look up by id in a Map.
9 MB decompressed RAM is fine (the 1 GB problem was the *frames*, not the catalog).

### Migration sketch

**Build / change:**
- `MapFrameWriter`: emit lean rows `u <cell> <slot> <genomeId>` (drop `mass`, `action`). Buffer
  frames into N-frame chunks; on seal, zstd-compress the chunk and append; keep the open chunk
  raw for live tail. `.frames.idx` becomes **per-chunk** `<firstTick> <byteOffset> <byteLen>
  <frameCount> <raw|zstd>`. Java-side zstd: `zstd-jni` is a small native dep — *or* shell out /
  precompress; pick during build (it's a writer-side dep, not in the kernel, doctrine-OK if light).
- `GenomeCatalogWriter`: wrap output in `GZIPOutputStream` (`.catalog.gz`); **delete the
  `.catalog.idx` writer** and its byte cursor.
- `RunReport`: bump `schema:` to report-v14.
- `live.html`: fetch a chunk, `fzstd` decode (history) / read raw (live tail), parse lean rows;
  colour by genome (gid) / lineage (births join) / position; **remove colour-by-mass and
  colour-by-action** and their legends. Whole-load `.catalog.gz` via native gunzip.
- `tools/mapviz.py`: baker inlines lean frames + gzipped catalog (offline path).

**Delete:** `.catalog.idx` writer; the per-genome Range path in the viewer; colour-by-mass /
colour-by-action toggles; the `mass`/`action` frame fields.

**Breaks (same PR):**
- `docs/specs/report-v13.md` → **report-v14** spec: lean row, chunked-zstd frames + per-chunk idx,
  catalog gzip (no idx), dropped mass/action, fzstd viewer dep. Spec wins over code — mandatory.
- `AGENTS.md`: report bullet, `MapFrameWriter`/`GenomeCatalogWriter` descriptions, the
  `u <cell> <slot> <mass> <action> <genomeId>` line, colour-by-mass mentions, report-v10/v13 refs.
- ADR `0033-lean-zstd-frames.md`: context (every-tick locked; mass/action not needed for seek;
  31× redundancy unreachable by gzip); decision (lean rows + chunked zstd + catalog gzip + fzstd);
  rejected (sampling — owner needs every tick; gzip — window too small; diff-genome — gzip≈same
  simpler; SQLite/duckdb-wasm — dep weight).
- Tests asserting frame `u`-line field count / `mass`/`action` columns / `.catalog.idx` existence.
- Baseline-diff workflow: regenerate `runs/baseline.*`. **Kernel `state-digest` unchanged** (no
  kernel touch) — only harness artifact shapes change.

### What you give up (veto check)
1. **Per-unit `mass` and `action` in the scrubber** — no colour-by-mass / colour-by-action.
   Aggregate mass stays in `.txt` + `.timeseries.csv`; per-unit mass/action recoverable only via
   opt-in `--observe` replay. (Owner approved.)
2. **One small browser dep** (`fzstd`, ~8 KB, decompress-only) — against the strict zero-dep lean,
   but trivial and not a Java/kernel dep. Bought ~10× over native gzip.
3. **A little writer/viewer machinery** for chunk sealing (raw open chunk + sealed zstd chunks) —
   the price of max compression *and* per-tick seek *and* live tail together.

Net: **~26× smaller on the run that matters (128.6 MB → ~5 MB), every tick preserved**, one index
sidecar dropped, catalog 19× smaller, the two high-churn fields gone — for a tiny browser zstd
decoder and chunk-sealing logic. The index + Range server + served viewer stay because *per-tick
seek demands them* — they are now justified by a real requirement, not by self-inflicted bigness.
