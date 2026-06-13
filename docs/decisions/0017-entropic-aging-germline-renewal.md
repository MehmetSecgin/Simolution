# 0017 — Entropic aging + germline renewal (no immortality, evolution unfreezes)

## Context
Contract-v2 §7 bet that without a coded aging law, immortality would be "rare and
evolutionarily inert." The reproduction runs falsified it. A unit that harvests at
least its metabolic bill sits at equilibrium `E*` forever (energy never crosses 0).
Once the reservoir is drained to its inflow floor, offspring mostly starve, so
reproducing (costly: `commit + BUILD_COST`, 30% yield loss) is locally *worse* than
just persisting. Evolution therefore converges on **non-reproducing immortal
harvesters** and freezes — seed 10 evolved a lineage to generation 146, then the
winner stopped reproducing and persisted forever. The opposite of inert.

This is the third anti-degeneracy moment, after eat-forever (ADR 0011) and
reproduction-spam (ADR 0016): observe a degenerate optimum, add a uniform
thermodynamic law that closes it.

## The biology (single-celled organisms)
Nothing lives forever by eating. Yeast ages **chronologically** (a non-dividing
cell loses viability over days/weeks) and **replicatively** (a mother buds ~20–30
daughters then dies). Damage — oxidized/aggregated proteins, failing mitochondria,
ERCs — accumulates; at division it is **segregated asymmetrically** so the daughter
is rejuvenated and the mother keeps the burden. *E. coli* ages the same way (old-pole
inheritance). The lineage is immortal; the individual is not; reproduction is *how*
the lineage outruns entropy (disposable-soma theory).

## Decision
Add a per-unit `damage` scalar = the unit's **lifetime energy dissipated to the
sink** (its entropy production):

* grows by the metabolic charge each tick (settle-cost) and by the loss dissipated
  per birth (`(1−YIELD)·commit + BUILD_COST`, settle-reproduction);
* adds an aging term to the bill: `charge += damage · AGING_COST`. The aging charge
  is itself dissipated, so damage feeds back → upkeep accelerates with age
  (**Gompertz**);
* offspring are born with `damage = 0` (germline renewal / asymmetric segregation);
  the parent keeps its damage.

Death is the **unchanged** derived predicate `energy ≤ 0` — aging just raises upkeep
until a bounded harvest (Vmax) can no longer cover it. No new death rule, no coded
lifespan. `AGING_COST = 0.0005`.

## Why this is principled, not a knob
`AGING_COST` is a **uniform conversion** (entropy → cost), identical for everyone —
the same kind of constant as `COST_PER_PROPAGATION`. It is *not* a per-unit lifespan:
because damage tracks each unit's own dissipation, a frugal/efficient cell ages
slowly and lives long (caloric restriction → longevity, real), a busy/hoarding/fecund
one ages fast. Lifespan **emerges** from each genome's work/harvest/reproduction
balance; the kernel never sets a death tick. Both real modes fall out of one law:
chronological aging (metabolic dissipation) kills the pure persister, replicative
aging (reproduction dissipation) gives a finite bud count. Conservation holds (aging
charge → sink). This supersedes contract-v2 §7's no-aging stance, on evidence.

## Companion tuning: mutation fidelity
Forcing turnover exposed a second effect: with units no longer able to freeze, every
lineage must reproduce continuously and is exposed to mutational load each generation.
At `MUTATION_RATE_PER_BIT = 0.001` (~1 flip/genome/birth) many seeds suffered
**error catastrophe** (Eigen) — deleterious flips outran selection → extinction.
Lowered to **0.0003** (~0.3 flips/genome/birth): high-fidelity copying, as real
replication uses to dodge meltdown. Both are world-harshness, set once.

## Evidence (livable world, not tuned for a count)
Seed 7, 30 founders, 20k ticks, `AGING_COST=0.0005`, `MUTATION=0.0003`: a single
lineage reproduces **continuously to generation 1757** (35,789 births), peak
population only 222 (aging bounds it — no explosion), 24 survivors all at gen
~1751–1757. No freeze, no immortal. Extinction across seeds is seed-dependent and
noisy (founder lottery, the embraced abiogenesis stance, contract §10) — where life
takes hold it now evolves indefinitely instead of stalling.

## Rejected
- Keep "no aging" (the freeze degeneracy itself).
- A flat `AGING_RATE` per tick (every cell ages identically — assigns lifespan, the
  thing §12/§7 forbid; owner flagged this directly).
- A coded lifespan cap / death tick (kernel deciding longevity).
- A stochastic mortality hazard (breaks the clean derived `energy ≤ 0` death; needs
  RNG; death-by-dice less physical than entropic senescence).
- Active repair now (immortality leak if perfect; the disposable-soma point is that
  repair is imperfect) — possible emergent future.

## Deferred
Biomass as a distinct variable remains the fuller home for aging (damage = degraded
biomass fraction; repair = turnover) — `damage` here is the scalar down payment, to
be reinterpreted, not discarded, when biomass lands.
</content>
