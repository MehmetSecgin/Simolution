# 0033 — lean frame rows + chunked-zstd frames + gzipped catalog

**Status:** accepted · 2026-06-15 · supersedes the report-v13 frame/catalog containers
**Spec:** [report-v14](../specs/report-v14.md) · **Audit:** [report-v13 design audit](../notes/report-v13-design-audit-findings.md)

## Context

A first-principles audit of report-v13's storage (working hypothesis: "we store more than the
problem needs") found, measured on the cyclic run (~1480 living units/tick, 3000 ticks): `.frames`
112 MB and `.catalog` 9.15 MB, **both uncompressed ASCII**, holding 31× / 20× redundancy. Tick-to-
tick churn: `cell` 6.5 %, `genomeId` 2.8 %, `mass` 14.6 %, `action` 33.4 %. The owner fixed two
requirements: per-tick resolution is **mandatory** ("watch it tick by tick"), and for seeking he
needs only **map + unit location + genome/lineage** — not per-unit mass or action.

## Decision

1. **Lean frame row** `u <cell> <slot> <genomeId>` — drop `mass` and `action` (the two high-entropy
   fields, 15 %/33 % churn). Keep `genomeId` (2.8 % churn, ~free compressed; direct genome colour).
2. **Chunked-zstd frame stream.** Group frames into 256-frame chunks, zstd-19 each, append to
   `<base>.frames.zst`; `<base>.frames.idx` indexes **chunks** (`firstTick lastTick off len count`).
   Multi-frame chunks give zstd the cross-frame window to reach the full ratio; per-chunk Range +
   decode keeps browser RAM O(one chunk). Live tail = seal a chunk per 256 frames + flush idx;
   follow-live lags ≤256 ticks. Browser decode via `fzstd` (~8 KB, inlined).
3. **Gzip the catalog** whole-file (`<base>.catalog.gz`), **drop `<base>.catalog.idx`** and the
   per-genome Range path; the viewer whole-loads + native-gunzips it once (~0.5 MB → 9 MB RAM).
4. **`zstd-jni`** added as a writer-side dependency for Java encoding.

## Why

- `mass`/`action` are exactly the redundancy zstd can't help (high churn) and aren't needed to seek
  → dropping them is the largest single win (112 → 82 MB raw, and unblocks compression).
- gzip's 32 KB window can't span frames (2.9× whole-file, no better chunked); only zstd's large
  window reaches 31×. Per-tick + live + max-compression jointly *require* chunking.
- Net on the cyclic run: **128.6 MB → ~5 MB (~26×)**, every tick preserved; one index sidecar gone;
  catalog 19× smaller. The index + Range server stay — now justified by per-tick seek, not bigness.

## Rejected

- **Sampling** (audit's first recommendation): owner needs every tick — vetoed.
- **gzip frames**: browser-native, zero-dep, but window too small (→ ~32 MB, 10× worse than zstd).
- **Diff-genome catalog**: ~10× shrink but needs a parent anchor, and 49 % of births are
  parent-unattributed; gzip reaches the same size with no reconstruction code.
- **SQLite / duckdb-wasm single file**: a Java columnar/DB *writer* dep + ~1 MB browser weight; the
  gzip+zstd path is smaller in moving parts and adds no Java DB dep.
- **Pure replay-canonical**: smallest on disk but needs the JVM for *every* view and loses
  live-watch + no-JVM glance. Kept only as the opt-in `--observe` backstop for exact mass/action.

## Trades given up

- Per-unit `mass`/`action` in the scrubber (no colour-by-mass/action); aggregate mass stays in
  `.txt`/`.timeseries.csv`, per-unit recoverable only via `--observe` replay. (Owner approved.)
- One small browser dep (`fzstd`, decompress-only). Chunk-sealing machinery in the writer +
  chunk-decode in the viewer — the price of max compression *with* per-tick seek *and* live tail.
