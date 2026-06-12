# 0011 — Saturating uptake + storage maintenance (no eat-forever)

## Context
The linear intake law (ADR 0010) had a degenerate optimum, exposed by a 20k-tick
seed-7 run: unit 99 wired a DELAY self-loop with gain > 1, its harvest signal
diverged geometrically, and because intake was proportional to raw harvest
magnitude, it monopolized the reservoir and hoarded 121k energy — then coasted
forever, since nothing costs a unit for what it holds. Real cells can't do this:
uptake saturates (finite transporters) and mass costs maintenance, so a glutton
implodes. The owner asked for physics where "eat everything forever" is not an
option. Two latent problems also lurked: the shared `demandTotal` could overflow
to ∞ (one near-overflow unit starves everyone), and energy storage was unbounded.

## Decision
Add **two uniform thermodynamic laws** (contract-v1 §5 amended, new §6a):

1. **Saturating uptake.** `demand = INTAKE_MAX·h/(HALF_SATURATION+h)`,
   `h = max(0, harvestOutput)`. Caps the *rate* of eating at `INTAKE_MAX`. A
   runaway signal buys no extra intake → no incentive to explode a loop into the
   mouth; demand is bounded → the allocation denominator can't overflow.
   Computed as `INTAKE_MAX/(1 + HALF/h)` to avoid `INTAKE_MAX·h` itself
   overflowing when `h` is a huge finite divergent value.

2. **Storage maintenance.** `maintenance = STORAGE_LEAK_RATE·energy`, charged to
   the sink each tick in the cost phase. Caps the *worth of hoarding*: upkeep
   grows with the store.

Together they yield an emergent per-unit carrying capacity
`E* = (INTAKE_MAX − baseCost)/STORAGE_LEAK_RATE`: above it, upkeep exceeds the
saturated intake ceiling, so the hoard implodes back. Both are uniform (privilege
no behaviour — same standing as structural decay); conservation holds (leak →
sink). Neither alone suffices: saturation without maintenance still permits slow
unbounded accumulation; maintenance without saturation lets a fast enough eater
cover any leak.

Constants (tunable, verified to give a live competitive population, not mass
death or a monopolist): `INTAKE_MAX = 5.0`, `HALF_SATURATION = 1.0`,
`STORAGE_LEAK_RATE = 0.002`. `HARVEST_EFFICIENCY` removed (subsumed).

## Consequence accepted
**Persistence now requires intake** — idle/empty genomes leak to death; nothing
is immortal. This supersedes contract-v0's degenerate-immortality property (and
the `emptyStructureNeverDecays` test, rewritten). The owner judged this a feature:
survival in an open system should be earned, not free.

## Evidence
Seed 7, 20k ticks. Before: 1 survivor (unit 99) hoarding 121,458, frozen after
tick 1061. After: unit 99 implodes and dies at tick 1664; max final energy across
all units 1012 (no hoarders); 19 survivors graze the reservoir to its inflow
floor with deaths continuing to tick 9061 — a competing population with bounded
equilibria. Audit error ~1e-8. Seed-42 baseline: 39 survivors, energies bounded.

## Rejected
- Linear intake kept (the degeneracy itself).
- Saturation only / maintenance only (each leaks; see above).
- Hard energy cap or hard intake clamp (cruder; a wall instead of a self-limiting
  dynamic — less physical than saturation + leak).
- Superlinear maintenance (energy²): a sharper ceiling, but linear leak gives a
  clean exponential relaxation to E* and is simpler; revisit only if needed.

## Deferred
Tuning of the three constants against the eventual reproduction milestone (E* and
the inflow-limited carrying capacity jointly set how many units the world feeds).
