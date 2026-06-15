# 0036 — Resource decay (field dissipation)

**Status:** accepted · **Date:** 2026-06-16 · **Milestone:** kernel v8 · **Spec:** [contract-v8](../contract/contract-v8.md)

## Context

The owner noticed, while watching a moving-band/ring run, that the map "always
starts with resource" and that a moving source leaves a permanent painted trail.
Root cause (confirmed in code): the resource field had **no sink**. Diffusion is a
conservative Laplacian (redistributes, never removes); inflow only tops cells up to
`CELL_CAPACITY`; the only outflow was units eating. With `CELL_INITIAL =
CELL_CAPACITY` the world also started fully saturated. Net effect: the inflow
pattern was cosmetic — the field was always full, founders bloomed on free initial
stock, and "barren" regions never drained.

## Decision

Add a **resource-decay law** (always on — a law, not a scenario knob):

1. New **phase 11 (settle-decay)** after diffusion: each cell loses
   `field · RESOURCE_DECAY_RATE` to `energySink` every tick.
   `RESOURCE_DECAY_RATE = 0.02` (≈35-tick half-life).
2. `CELL_INITIAL = CELL_CAPACITY → 0`: the field starts empty, so the map fills
   from the inflow pattern instead of starting pre-loaded.

Exponential relaxation: a cell with no inflow decays to 0; with steady inflow `I`
it settles at `I / rate` (capped). A moving source now leaves a fading trail, and
barren zones are genuinely barren — the inflow pattern finally drives selection.

## Why

- **Conservation preserved.** Decayed resource goes to the sink (the audit's
  existing dissipation term), so the closed-system balance (contract v5 §6) is
  untouched — `energy-audit-error` stays ~0.
- **Appended, not reordered.** New mechanics become new tail phases — the locked
  pipeline invariant. Decay after diffusion dissipates the spread field.
- **No new semantics or per-unit state.** One O(cells) sweep, two constants.

## Consequences

- Baseline digest moves `1f97e28f3e369e9b → 52d655ca519aa56b` (expected, kernel
  law). Gross baseline dynamics similar (peak-pop 1016→976, final 5→3, max-gen 5).
- `LOCAL_RESOURCE` is now a live time-varying input even under uniform inflow
  (field ramps to its fixed point) — this flipped two DynamicsObserver fixed-point
  tests to BOUNDED (the field is no longer a static input; tests updated).
- Lean patchy/moving worlds can now drive cold-start **extinction** (a ring+noise
  seed-7 run went extinct ~tick 2628). Honest pressure, not a bug — survivable
  worlds need a baseline trickle or generous source.
- `IndelTest` retargeted to a food-rich sustaining population: random founders no
  longer survive the harsher world long enough to exercise the (already 10×-lower,
  ADR 0035) indel operators, so it now uses generous inflow to test the indel
  *mechanism*, not survival.

## Rejected

- **Opt-in decay** — it is a physical law; making it a flag would let the
  unphysical "permanent resource" world persist and split behavior.
- **Decay to nothing (not the sink)** — would destroy energy and break the audit.
  Routing to the sink keeps conservation exact.
- **Keeping `CELL_INITIAL = capacity`** — the owner explicitly wanted the map to
  not start pre-loaded; empty start makes the inflow pattern legible from tick 0.
