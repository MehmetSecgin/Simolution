# report-v7 — spatial map sidecar (`<base>.map.txt`) + scrub player

Delta over [report-v6](report-v6.md): adds an optional **spatial map** sidecar so
a run's lattice can be watched over time. The text report, per-unit CSV, wiring
CSV, lineage/timeseries/population sidecars are unchanged. The kernel header now
reads `kernel: v3` with `world-width` / `grid-cells` lines (replacing v2's
`max-units`); the `initial-reservoir` / `final-reservoir` lines now report the
**total standing resource summed over all cells** (contract-v3 §7).

## When it is written
Whenever `--out <file>` is set and `--map-frames N` > 0 (default `N = 120`; `0`
disables). Written **incrementally during the run** by `MapFrameWriter` —
streamed and flushed per sampled frame, never accumulated in memory (memory
doctrine: footprint constant in tick count, the trajectory lives on disk like
`--trace`). Sampling interval `K = max(1, ticks / N)`.

## Format (`<base>.map.txt`, line-oriented)
```
world <W>            # lattice side; grid is W·W cells, cell = y·W + x
sample-every <K>     # one frame every K ticks
t <tick>             # --- one frame ---
r <W·W chars>        # resource field, dense: each cell quantized to a digit
                     #   0–9 = round(9 · cellResource / CELL_CAPACITY), clamped
o <cell:lineage ...> # occupants, sparse: one "cell:lineageId" per living unit
                     #   (a cell is position[slot]; slot identity is irrelevant here)
```
`t`/`r`/`o` repeat per sampled frame. `r` is dense (every cell, for the heatmap);
`o` is sparse (most of the grid is usually empty). Deterministic for a given seed.

## The player (`tools/mapviz.py`, stdlib only)
```sh
python3 tools/mapviz.py runs/foo.map.txt   # -> runs/foo.map.html
```
Inlines all frames and emits a **self-contained** HTML scrub player: a `W×W`
`<canvas>` drawing the resource field as a dark→green heatmap with living units
overlaid, **coloured by lineage** (hue = `lineageId · 137.508° mod 360`).
Controls: play / pause, step, frame slider, speed, resource-layer toggle; live
readout of tick, frame, alive count, distinct lineages. Open by double-click
(scrub), or serve the directory and re-render to follow a long run.

## Live mode (`--serve PORT`)
`LiveServer` (JDK built-in `com.sun.net.httpserver`, no deps) serves the run while
it ticks: `GET /` → the live player (`src/main/resources/live.html`), `GET
/frames?since=N` → JSON `{world, sampleEvery, done, frames:[{t,r,o}]}` parsed
on-demand from the streamed `.map.txt` (only complete frames; a partially-written
trailing frame is never served). The player polls `/frames`, **follows live**
(auto-advancing to the newest frame) with a toggle to scrub history, and switches
to scrub/replay once `done` is true. Requires `--out` (the map file is the source
of truth); server state is zero — frames live on disk, so memory stays O(1) in
tick count. After the run the JVM keeps serving until Ctrl-C.

## Out of scope (deferred)
Per-unit overlays (energy brightness, age, movement trails) — later additions; the
frame schema can carry extra per-cell channels without a format break.
