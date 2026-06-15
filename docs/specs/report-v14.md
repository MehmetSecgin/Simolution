# report-v14 — lean frame rows + chunked-zstd frame stream + gzipped catalog

**Status:** implemented 2026-06-15 (branch `kernel-v5-biomass`) · **Kernel:** v5 (unchanged)
**ADR:** [0033](../decisions/0033-lean-zstd-frames.md)
**Delta over:** [report-v13](report-v13.md) (frame row + frame/catalog containers)
**Audit:** [docs/notes/report-v13-design-audit-findings.md](../notes/report-v13-design-audit-findings.md)

## Why

report-v13's lean `u` line was right in spirit but still stored two things no seek/scrub
workflow needs, and stored everything **uncompressed ASCII**. Measured on the cyclic run
(`--units 300 --ticks 3000 --seed 100 --world 100 --resource-cycle`, ~1480 living units/tick):

- the `.frames` file was **112 MB** and the `.catalog` **9.15 MB**, both plain text;
- the frame held 31× redundancy — `gzip` recovers only 2.9× (32 KB window can't span frames),
  but `zstd-19` recovers **19×** whole-file;
- tick-to-tick churn: `cell` 6.5 %, `genomeId` 2.8 %, **`mass` 14.6 %, `action` 33.4 %**.

`mass` and `action` are the two high-entropy fields, and the owner does not need them to seek:
**"for seeking I only need the map, unit location, and genome/lineage info."** Dropping them and
compressing collapses the run:

| frame content | raw | whole-file zstd-19 |
|---------------|----:|-------------------:|
| v13 `cell slot mass action genomeId` | 112 MB | 6.1 MB |
| **v14 `cell slot genomeId`** | 82 MB | **2.66 MB (44× vs v13)** |

The catalog is near-duplicate genomes (24 579 distinct, ~35 genes each, differing by ~1 gene):
`gzip` alone shrinks it **19.7×** (9.15 MB → 0.49 MB), so it no longer needs an on-demand index.

Per-tick resolution is **kept** (owner: "I want to watch it tick by tick"). The win is the row +
the container, not sampling.

## Frame row — lean

```
u <cell> <slot> <genomeId>
```

`mass` and `action` are **removed**. The viewer colours by **genome** (`genomeId` → catalog →
circuit) and **lineage** (join `slot` → births store), and shows **position** (`cell`). Aggregate
mass stays in `.txt` + `.timeseries.csv`; per-unit mass/action over a window is recoverable via
opt-in `--observe` replay. `genomeId` stays in the row (2.8 % churn → ~free once compressed; keeps
genome colour a direct read, no per-tick births join).

## Frame container — `<base>.frames.zst` + `<base>.frames.idx`

The frame stream is now **binary**: frames are grouped into **chunks of `CHUNK_FRAMES` (256)
frames**, each chunk independently **zstd-19**-compressed and appended to `<base>.frames.zst`.
A chunk decompresses to the familiar text block (`t`/`r`/`u` lines, RLE resource field, v13
semantics). Multi-frame chunks are what let zstd's window span frames and reach the 31×; a single
frame compresses only ~3×.

`<base>.frames.idx` is ASCII, one header line then one line per **chunk**:

```
format v14 world <W> sample-every <K> chunk <CHUNK_FRAMES>
<firstTick> <lastTick> <byteOffset> <byteLen> <frameCount>
...
end <finalTick>
```

- **Seek:** map tick → the chunk whose `[firstTick,lastTick]` contains it; HTTP-Range-fetch
  `[byteOffset, byteOffset+byteLen)`; zstd-decompress; scan its `t` lines to the frame. Browser RAM
  = one decompressed chunk (~7 MB), independent of run length.
- **Live tail:** the writer seals a chunk every `CHUNK_FRAMES` frames and flushes the idx line; a
  viewer polls the idx for new chunk lines and fetches them. The in-progress chunk is invisible
  until sealed → follow-live lags by **≤ `CHUNK_FRAMES` ticks** (negligible at sim speed). No
  separate raw-tail file. `close()` seals the final partial chunk and writes `end <finalTick>`.

Browser decoder: a tiny **decompress-only zstd** (`fzstd`, ~8 KB, inlined into `live.html`).
Java encoder: **`zstd-jni`** (writer-side dep; not in the kernel, doctrine-OK — see ADR 0033).

## Genome catalog — `<base>.catalog.gz`

Whole-file **gzip** of the v13 catalog text (`g <id> <count> <genes>` lines). The
`<base>.catalog.idx` and the per-genome Range path are **removed**: the gzipped catalog is ~0.5 MB,
so the viewer whole-loads it once via the browser-native `DecompressionStream('gzip')` and builds a
`Map<genomeId, genes>`. 9 MB decompressed in RAM is fine (the ~1 GB problem was the *frames*).

## Lineage / births store — `<base>.births.csv.gz`

**Unchanged** from report-v13 (`BirthLogWriter`; gzipped CSV, DuckDB-queryable; `lineage` +
`generation` kernel-exact; ~half of cyclic births are the unattributed fission half, `parent_*=-1`).
The viewer's lineage walk and genome-per-slot resolution read it client-side (gunzip via
`DecompressionStream`).

## Kept / retired

- **Kept always-on:** `<base>.txt` report (schema bumped to **report-v14**) and
  `<base>.timeseries.csv` (the one cheap full-run trajectory). `--observe` sinks unchanged + opt-in.
- **Retired vs v13:** `mass` + `action` frame fields; `firedAction` derivation; `<base>.catalog.idx`
  (+ per-genome Range path); colour-by-mass / colour-by-action in the viewer. The CSV zoo stays
  retired (v13).

## Viewer

`live.html`, two modes (unchanged in spirit):

- **indexed (served):** load `.frames.idx` + whole `.catalog.gz`; Range-fetch + `fzstd`-decode one
  chunk on scrub; colour by genome / lineage / position; structural circuit from `catalog[genomeId]`.
- **embed (offline `file://`):** `tools/mapviz.py` bakes decoded frames + the catalog into the page
  (small runs).

Still served via `scripts/serve.py` (Range-capable static server) — kept, because per-tick random
seek over a large `.frames.zst` needs Range.

## Determinism & doctrine

- Kernel untouched → baseline kernel `state-digest` unchanged; only harness artifact shapes change.
- Memory doctrine intact: the frame writer holds at most one open chunk (`O(CHUNK_FRAMES · slots)`,
  constant in run length) plus the O(slots) per-slot genome cache; the catalog writer holds the
  O(distinct-genomes) dedup map. No per-tick history.
- `zstd-jni` is a writer-side native dep, never invoked from `Kernel`/the tick loop.

## Open / deferred

- `CHUNK_FRAMES` is a constant (256); expose as a flag only if a run needs a different
  seek-granularity / ratio trade.
- Cyclic-inflow replay gap (ADR 0029) unchanged.
- A per-unit Parquet frame table for SQL trajectory queries — still optional (v13 §open).
