# report-v10 — surface biomass (mass) in reports, CSVs, and the map viewers

Delta over [report-v9](report-v9.md). Kernel v5 (contract-v5) added `mass` as a
distinct per-unit state variable; v9 and earlier exposed energy but not mass, so a
biomass run could not be *observed*. report-v10 threads mass through every existing
observation surface — no new files, only new columns/lines/fields — so size can be
read, diffed, and watched. Kernel stays pure; this is all in the harness + viewers.

## Run report (`<base>.txt`)

- `schema:` bumped `report-v6` → `report-v10`; `kernel:` line now `v5`.
- New **`## mass`** section, after `## energy`, reported over the **full living
  population** (descendants included — *not* the founder-indexed per-unit arrays,
  which go stale once slots are reused by births):
  - `initial-mass-total` — `Σ` founder mass (`INITIAL_MASS · founders`).
  - `final-mass-total` — `Σ` mass of the living at end (corpses dissipate mass to
    the sink, contract-v5 §6, so they contribute 0).
  - `final-mass-mean` — `final-mass-total / final-population`.
  - `final-mass-max` — largest mass among the living over the whole slot pool.

## Per-unit CSV (`<base>.units.csv`)

- New trailing column **`final_mass`** — each unit's mass at end (0 for corpses).
  Column order is otherwise unchanged, so existing parsers keep working if they key
  by header.

## Per-tick metrics (`<base>.obs/metrics.csv`, `--observe`)

- New trailing column **`mass_total`** — `Σ` living mass at that tick, a streamed
  O(1)/tick aggregate like the others. Lets a DuckDB query plot total biomass over
  time alongside population and energy.

## Spatial map sidecar (`<base>.map.txt`) and viewers

- The `u` line gains a **`mass`** field (rounded to 3 decimals) immediately after
  `energy`, before `geneCount`:

  ```
  u <cell> <slot> <lineage> <generation> <energyRounded> <massRounded> <geneCount> <gene×geneCount> <nodeOutput×NodeLayout.TOTAL>
  ```

  Both readers were updated in lock-step: `tools/mapviz.py` (offline) and
  `LiveServer` (which serializes the line to JSON for `live.html`, now with a
  `"mass"` field per unit).
- **`tools/mapviz.py`** and **`live.html`** gain a **"colour by mass"** toggle:
  units are coloured blue (small) → red (large), normalized to the run's maximum
  mass; the default colouring stays lineage. Both show **mass mean / max** of the
  current frame, and the live viewer's unit-detail pane gains a **mass** pill.

## Determinism / memory

Unchanged. All additions are running aggregates or per-unit fields bounded by unit
count, never by ticks (memory doctrine). The report stays byte-deterministic; the
`state-digest` already folds mass (kernel v5). No kernel changes.
