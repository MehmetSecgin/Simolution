# 0019 — Per-cell resource economics (CELL_CAPACITY, CELL_INFLOW, DIFFUSION_RATE)

## Context
Spatializing the resource (ADR 0018) replaces the three global constants
(`RESOURCE_CAPACITY=100000`, `RESOURCE_INITIAL`, `RESOURCE_INFLOW=50`) with
per-cell physics. New uniform anchors are needed; per contract-v3 §10 they are set
**once for livability**, never tuned to an outcome.

## Decision
```
CELL_CAPACITY  = 100.0   // standing-crop ceiling per cell; also LOCAL_RESOURCE normaliser
CELL_INFLOW    = 1.0     // per-cell per-tick rain, capped at CELL_CAPACITY
DIFFUSION_RATE = 0.1     // 4-neighbour explicit Laplacian coefficient
```
Initial field: every cell full (`= CELL_CAPACITY`), mirroring v1's
`RESOURCE_INITIAL = RESOURCE_CAPACITY` (start rich, let life draw it down).

## Why these values (principled, not outcome-tuned)
* **`CELL_INFLOW = 1.0` vs the metabolic floor.** A unit's irreducible bill is
  `BASAL_COST = 0.5` plus maintenance/activity. One cell raining `1.0/tick` can
  sustain one frugal harvester with margin to bank toward reproduction — so a cell
  is livable for its occupant but not lavish. This is the spatial analogue of
  choosing an inflow that permits, but does not guarantee, persistence.
* **`CELL_CAPACITY = 100.0`.** A standing crop ~100× the per-tick rain gives a
  real early bloom (founders harvest the initial crop fast → reproduce → spread)
  that then relaxes to the inflow-limited carrying capacity as the grid fills —
  bloom-then-saturate, the honest shape. Also the `LOCAL_RESOURCE` normaliser, so
  a full cell reads ~1.0.
* **`DIFFUSION_RATE = 0.1`.** Comfortably inside the explicit 4-neighbour
  stability bound (`< 0.25`); resource spreads to smooth sharp depletion holes
  (so a unit that exhausts its cell can be partly refilled by neighbours) without
  erasing gradients instantly. Slow enough that local depletion still creates the
  gradients that make space matter.

## Status
**Provisional livability settings.** Per contract-v3 §10 they may be reset *once*
if a baseline + seed scan shows the world is unlivable (universal extinction) or
degenerate — never nudged toward a target population, lineage count, or generation
depth. First baseline diff + a seed scan will confirm life persists and evolves.

## Rejected
- Reusing the global magnitudes per-cell (`CAPACITY=100000` would make a single
  cell an effectively infinite larder — no local scarcity, defeats the point).
- Capping the field (not just inflow) at `CELL_CAPACITY` — clamping diffusion
  destroys mass and breaks the energy audit; only inflow admission is capped.
- `DIFFUSION_RATE ≥ 0.25` (explicit-scheme instability / oscillation).
