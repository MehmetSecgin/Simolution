# report-v11 — compact map frames (genome dedup + output rounding)

Delta over [report-v10](report-v10.md). The spatial map sidecar `<base>.map.txt`
(report-v7/v9/v10) had grown enormous: a long run was hundreds of MB and **over half
of it was redundant**. Measured on `runs/survivor.map.txt` (226 MB, ~3000 units ×
120 frames), a single `u` line averaged ≈679 bytes split as:

| part | bytes | share | nature |
|------|-------|-------|--------|
| header (`cell slot lineage gen energy mass`) | 25 | 4% | needed |
| **genome** (raw genes) | 353 | 52% | **redundant** — re-emitted every frame though it only changes at birth |
| **node outputs** (`NodeLayout.TOTAL` doubles) | 301 | 44% | **over-precise** — full `double` (`-0.4094014230944101`) for a glow viz |

report-v11 removes both wastes. The kernel stays pure; this is all in `MapFrameWriter`
(writer) and `LiveServer` (reader). The text report, CSV sidecars, and observability
layer are unchanged; the text report `schema:` line stays `report-v10` — this is a
sidecar-format delta only.

## Why
A genome **only changes at birth**, so repeating it on every sampled frame is pure
redundancy. Node outputs feed only the viewer's "which connections carry signal"
glow, which needs a few significant figures, not 17. Cutting both shrinks the file
~80% (≈679 → ≈140 B/unit/frame; survivor 226 MB → ≈45 MB) with **no loss the viewer
can see** and **no change to what either consumer renders**.

## Frame format (`<base>.map.txt`, supersedes report-v10's `u` line)
```
world <W>
sample-every <K>
t <tick>
r <W·W digits 0-9>                              # resource heatmap (unchanged)
u <cell> <slot> <lineage> <generation> <energyRounded> <massRounded> <genome> <nodeOutput×NodeLayout.TOTAL>
... (one u line per living unit)
```
`<genome>` is one of:
- **`* <geneCount> <gene×geneCount>`** — the genome is being *defined* for this slot:
  it differs from the last genome emitted for this slot (a birth, or the slot's first
  appearance in the file).
- **`^`** — the genome is *unchanged* since this slot's last definition; the reader
  carries it forward.

**Field positions 0–5 are unchanged** (`cell slot lineage gen energy mass`). The genome
marker moves into position 6 (where report-v10 had a bare `geneCount`). Because
`tools/mapviz.py` reads only fields 0, 2 and 5 (cell, lineage, mass), it is unaffected.

Node outputs are **rounded to ≤4 decimal places**, trailing zeros stripped, exact zero
written as `0` (e.g. `-0.4094014230944101` → `-0.4094`, `0.0050` → `0.005`, `0.0` → `0`).
Non-finite outputs are written as `NaN` (the readers already map that to JSON `null`).

## Carry-forward semantics (reader contract)
A reader maintains **genome-per-slot** state: on `*` it stores the gene list for that
slot; on `^` it reuses the stored list. Slot reuse at a birth always changes the genome
(a new child), so the writer emits `*` then — there is no stale carry. The writer keeps
the symmetric state (last genome emitted per slot, O(slots), bounded and constant in
tick count) and emits `^` exactly when the current genome equals it.

`LiveServer` resolves carries **at parse time** (it already reads the whole file per
request), so the JSON it serves — `{cell,slot,lineage,gen,energy,mass,genes:[...],o:[...]}`
per unit — is **byte-for-byte the report-v10 shape**. The circuit inspector in
`live.html` needs no change: it still receives the full gene list and per-tick outputs
for every unit, every frame.

## Window sampling companion (`--map-from` / `--map-to`)
Orthogonal size lever shipped alongside (commit `ac17dc3`): instead of downsampling the whole
run to `--map-frames` frames, `--map-from N [--map-to M]` writes a frame **every tick**
in `[N, M]` and nothing outside, for dense tick-by-tick inspection of one phase without
paying for the entire run. The per-frame **format is identical** in both modes; window
mode only selects which ticks emit a frame, and `sample-every` reports the effective
stride (`1` in window mode). The dedup + rounding of this spec apply to both.

## Determinism / memory
Unchanged doctrine. The writer's dedup cache is O(slots) — bounded by `W²`, constant in
tick count; frames are still built and flushed one at a time (O(1) RAM across ticks).
Output rounding is display-only and lossy by design; it never touches kernel state, and
deterministic replay (report-v8) reconstructs ticks from checkpoints, not from frames,
so full fidelity is preserved where it matters. No kernel changes; runs with frames off
stay byte-identical.
