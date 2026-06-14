# report-v9 — genome-carrying map frames + circuit inspector

Delta over [report-v7](report-v7.md). The spatial map sidecar `<base>.map.txt` and
the live viewer (`--serve`) gain enough per-unit detail to inspect an evolved
**genome's circuit** directly from the frame stream — **no reconstruction, no extra
sink**. The kernel stays pure; this is all in `MapFrameWriter` / `LiveServer` /
`live.html`. The text report, CSV sidecars, and observability layer (report-v8) are
unchanged.

## Why
Inspecting a unit's circuit at a past tick previously meant either re-simulating
(report-v8 replay — expensive per click) or losing the wiring (lean frames). Since a
**genome only changes at birth** and node outputs are already in the snapshot, the
cheapest path is to **store them in the frame**: the viewer then groups living units
by genome (distinct genomes alive), decodes any genome to its circuit, and lights up
the connections carrying signal — entirely client-side.

## Frame format (`<base>.map.txt`, supersedes report-v7's `o` line)
```
world <W>
sample-every <K>
t <tick>
r <W·W digits 0-9>                          # resource heatmap (unchanged)
u <cell> <slot> <lineage> <generation> <energyRounded> <geneCount> <gene×geneCount> <nodeOutput×NodeLayout.TOTAL>
... (one u line per living unit; replaces the sparse o line)
```
After the `geneCount` raw genes come the unit's `NodeLayout.TOTAL` node output values
for that tick (non-finite → JSON `null` downstream). Genomes repeat each frame
(simplest; they only change at birth) — for long runs, sample fewer frames. Still
streamed + flushed per frame (O(1) RAM; memory doctrine intact).

## Live server (`--serve`)
`GET /frames?since=N` → `{world, sampleEvery, total, done, frames:[{t, r,
units:[{cell,slot,lineage,gen,energy,genes:[...],o:[...]}]}]}`. The trailing
in-progress frame is withheld while live. **No reconstruction endpoints** — the
viewer computes everything from the frame list.

## Viewer (`src/main/resources/live.html`)
- **alive distinct genomes** panel — living units grouped by exact gene-array equality.
- **genome circuit** — a **force-directed** node graph (sensors / internals / actions
  by colour, deterministic settle on select, disconnected sub-circuits drift apart),
  curved sign-coloured weighted edges, arrowheads, self-loops, ×N dosage badges,
  junk filter.
- **hover a node** → isolates its wiring and reveals the **actual weight** on each
  connection; **per-tick active glow** marks connections carrying signal that tick
  (`output[src]·weight ≠ 0`).
- `tools/mapviz.py` parses the `u` lines for the offline heatmap player.
