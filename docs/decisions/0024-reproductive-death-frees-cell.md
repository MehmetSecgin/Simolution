# 0024 — Reproductive death frees the cell (occupancy symmetry)

## Context
`settleReproduction` (phase 7) lets a parent commit energy down to **exactly
zero**: `commit = min(drive, energy − BUILD_COST)`, then
`energy[parent] −= commit + BUILD_COST`. When `drive ≥ energy − BUILD_COST` the
`min` picks `energy − BUILD_COST` *exactly*, so the parent lands on `0.0`. This is
not a floating-point edge — it is the common case for any low-energy, high-drive
unit (drive saturates toward `REPRODUCE_MAX = 200`, `BUILD_COST = 10`). Such a
parent is now dead (`energy ≤ 0`, the derived death predicate) but its cell was
**not** freed: only `settleCost` (phase 6) cleared `cellOccupant` on death, and
phase 6 runs *before* phase 7 and skips already-dead units next tick. The corpse
held its cell until its slot was reused — blocking births and moves into that
cell, and making `cellOccupant` no longer a pure function of living positions.

So death-frees-cell was implemented for **metabolic** death only; **reproductive**
death silently leaked an occupied-but-dead cell. contract-v3 §5 states the law
without a carve-out: *"Energetic death still frees a cell … so the grid breathes."*
The asymmetry was a contract violation, not a feature.

## Decision
Free the cell on reproductive death too. In `settleReproduction`, immediately
after the parent's energy is debited:

```java
energy[parent] -= commit + KernelConfig.BUILD_COST;
if (energy[parent] <= 0.0) {
    cellOccupant[position[parent]] = -1;
}
```

This mirrors `settleCost` exactly (energy change → death check → vacate cell), so
death frees the cell uniformly regardless of cause. The freed cell is visible to
later parents in the same phase-7 scan (a front can advance into it immediately)
and to phase-8 movement, which is the intended "grid breathes" dynamics.

Terminal, semelparous reproduction — invest everything in one final child and die
— stays **allowed**. Death in this world is honest `energy ≤ 0`; a unit spending
itself to zero for one offspring is a legitimate strategy, and evolution may keep
or discard it. We fixed the bookkeeping bug, not the biology.

## Why
- **Restores the contract invariant.** `cellOccupant` is again a pure function of
  living positions; "death frees a cell" holds for every cause of death.
- **Removes a silent space leak.** Reproductive corpses no longer sterilize cells
  until slot reuse, so available reproduction/movement space is honest.
- **No new semantics, no kernel cost.** One comparison + one array write inside an
  existing phase, only on the death tick. No allocation, no new state, hot loop
  untouched.

## Rejected
- **Forbid spend-to-zero (require the parent to survive reproduction).** Would
  make `cellOccupant` correct by construction (reproduction never yields a corpse),
  but it smuggles a semantic — *"reproduction is non-fatal"* — turning death into a
  metabolic-only event and outlawing a real life-history strategy (semelparity).
  The kernel must not privilege a behavior; we keep death cause-agnostic and just
  free the cell.
- **Free dead cells in a separate sweep.** A dedicated reap phase would re-walk all
  units to do what the two energy-debiting phases can each do inline at O(1). More
  code, another phase, no benefit.

## Consequence
Kernel dynamics change: cells emptied by reproductive death become available the
same tick (to later phase-7 births and to phase-8 moves) instead of staying blocked
until slot reuse. `state-digest` folds `cellOccupant`/`position`, so the baseline
shifts from the first tick a terminal reproduction occurs and every line downstream
differs. Determinism re-verified (stable digest on rerun). See regenerated
`runs/baseline.txt`.
