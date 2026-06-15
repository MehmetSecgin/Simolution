# Contract v8 — resource decay (field dissipation)

Delta over [contract-v7](contract-v7.md). Adds a **resource-decay law**: standing
resource in the cell field is no longer permanent — each tick every cell
dissipates a fixed fraction of its resource to the sink. Independent of, and
composable with, the v7 programmable inflow field.

**Status: implemented** (kernel v8, ADR 0036). Always on (not opt-in): it is a law
of the world, not a scenario knob. Changes the baseline (`state-digest
1f97e28f3e369e9b → 52d655ca519aa56b`).

## Motivation

Through v7 the resource field had **no sink of its own**. Diffusion (a Laplacian)
only *redistributes* and conserves the field total; inflow only tops cells *up*
toward `CELL_CAPACITY`; the sole way resource left the field was a unit eating it.
Combined with `CELL_INITIAL = CELL_CAPACITY` (every cell started full), this meant:

* the world started **saturated everywhere**, so founders bloomed on free initial
  stock regardless of the inflow pattern;
* unconsumed resource was **permanent** — a "barren" periphery never actually
  drained, and a moving inflow source (band, ring) **painted** resource that never
  faded.

So the spatial/temporal inflow pattern was largely cosmetic: the field was always
full. Decay makes the pattern *matter* — a cell with no inflow source relaxes to 0,
and a moving source leaves a **fading trail**, creating real, transient scarcity
that selection can act on.

## 1. The decay law

Each tick, in a new **phase 11 (settle-decay)** after diffusion, every cell loses a
fixed fraction of its standing resource to the sink:

```
lost          = resourceField[cell] · RESOURCE_DECAY_RATE
resourceField[cell] -= lost
energySink         += lost
```

* `RESOURCE_DECAY_RATE = 0.02` (≈35-tick half-life). Exponential relaxation: a cell
  with no inflow decays toward 0; a cell with steady inflow `I` settles at the
  fixed point `I / RESOURCE_DECAY_RATE` (capped at `CELL_CAPACITY`).
* `CELL_INITIAL = 0`. The field **starts empty** — only inflow sources hold
  resource, so the map begins dark and fills according to the pattern rather than
  starting pre-loaded.

## 2. Pipeline

The locked pipeline gains one phase at the tail (new mechanics are appended, never
reordered — the core invariant):

```
clear → propagate → evaluate → swap → intake → cost → growth →
reproduction → movement → diffusion → DECAY            (11 phases)
```

Decay runs **after** diffusion so the relaxed (spread) field is what dissipates;
order is fixed for determinism.

## 3. Conservation — unchanged

Decay is **conservative**: the dissipated amount moves from the field to
`energySink`, the audit's existing dissipation term. The closed-system balance
(contract v5 §6) is untouched:

```
creditedInitialEnergy + initialResourceTotal + creditedInitialMass + cumulativeInflow
   == Σ energy + Σ field + Σ mass + sink
```

With `CELL_INITIAL = 0`, `initialResourceTotal = 0`. `energy-audit-error` stays ~0
(verified, ~1e-6 on the baseline). Determinism holds — decay is a fixed-order
arithmetic sweep, no randomness, no wall-clock.

## 4. Consequences (observed)

* The `LOCAL_RESOURCE` sensor is now a **live, time-varying input** even under
  uniform inflow (the field ramps 0 → fixed point), where before it was pinned at
  capacity. Units that read it see genuine signal.
* Worlds are harsher: a population can no longer coast on initial stock, and lean
  patchy/moving sources can drive **extinction** for cold-start random founders
  (the bootstrap problem, now sharper) — honest selective pressure, not a bug.

## 5. What does not change

Everything in v5–v7 (mass, GROW, fission, motility, the inflow field, diffusion,
determinism). Decay adds one field-relaxation phase and two constants; no node
semantics, no per-unit state growth.
