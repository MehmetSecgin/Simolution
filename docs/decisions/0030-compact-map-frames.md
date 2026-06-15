# 0030 — Compact map frames: genome dedup + output rounding

**Status:** accepted · **Date:** 2026-06-15 · **Spec:** [report-v11](../specs/report-v11.md)

## Context

The `.map.txt` sidecar (report-v9 added genomes, report-v10 added mass) had become
huge — `runs/survivor.map.txt` is 226 MB. Profiling one `u` line (≈679 B) showed
~96% of it is two avoidable wastes: the **genome** (353 B, 52%) is re-emitted every
frame though it only changes at birth, and the **node outputs** (301 B, 44%) are
written at full `double` precision for a viewer that only needs them to glow edges.
report-v9 already flagged the genome repeat as a known cost ("for long runs, sample
fewer frames"); the owner asked to stop storing the unnecessary info instead.

## Decisions

1. **Genome dedup per slot, marker `^` vs `* <count> <genes>`.** The writer keeps the
   last genome emitted per slot (`int[] lastGenes`, O(slots) — bounded by `W²`,
   constant in tick count, doctrine-safe) and emits `^` when unchanged, the full genes
   only on change (birth / first sighting). A long-lived unit's genes are written once,
   not once per frame.
2. **Reader carries forward; JSON shape unchanged.** `LiveServer` resolves `^` at parse
   time (it reads the whole file per request anyway), so the JSON it serves and the
   `live.html` circuit inspector are untouched. Slot reuse at birth always changes the
   genome → `*` → no stale carry.
3. **Fields 0–5 keep their position.** The genome marker goes in field 6 (where a bare
   `geneCount` was). `tools/mapviz.py` reads only fields 0/2/5 (cell/lineage/mass), so
   it needs **zero changes** — verified against the format.
4. **Outputs rounded to ≤4 decimals, allocation-free.** A helper rebuilds the fraction
   from the integer remainder (no `String.format`, no FP tail), strips trailing zeros,
   writes exact zero as `0` and non-finite as `NaN` (readers already map `NaN`→`null`).
   Lossy by design and display-only: replay reconstructs from checkpoints, not frames.
5. **Sidecar-only; text report `schema:` stays report-v10.** Nothing in the kernel or
   the run report changes; frames-off runs stay byte-identical.

Net: ≈679 → ≈140 B/unit/frame (~80%); survivor 226 MB → ≈45 MB. Composes with the
windowed sampler (commit `ac17dc3`, `--map-from`/`--map-to`): dedup+rounding shrink each
frame, window mode picks which ticks emit one.

## Rejected

- **Gzip the file** — would shrink it, but breaks the live server's plain-text tail and
  mapviz's line parse; doesn't remove the redundancy, just hides it.
- **Genome dictionary keyed by content (all genomes ever seen)** — grows with cumulative
  distinct genomes = unbounded in tick count, violating the memory doctrine. Per-slot
  last-genome is O(slots) and catches the same redundancy (genome only changes at birth).
- **Drop node outputs from the persisted frame** — would shrink more, but `live.html`'s
  per-tick edge glow reads them when scrubbing a finished run via `--serve`; rounding
  keeps the feature at ~⅓ the bytes.
