# 0016 — Fixed per-birth build cost (no reproduction spam)

## Context
The reproduction mechanism (ADR 0015) had a degenerate gen-0 optimum, exposed by
the first runs: random founders that happen to wire REPRODUCE positively divide
indiscriminately. Because a birth only transferred energy (with a proportional
yield loss) and was instantaneous and unlimited, a founder could subdivide its
inherited energy bank into ever-tinier offspring, each still occupying a slot for
a tick. Seed 42, 100 founders, 1000 ticks: **55,766 births, peak population 6,408,
gen 95**, the founders' bank mostly dissipated to the sink — a boom-bust that
self-limits only by mass starvation, and needs an absurd slot pool to even run.

This is the reproduction analogue of the v1 "eat-forever" degeneracy (ADR 0011).
The owner asked what *real biology* restrains reproduction; the answer is three
physics we lacked: (1) building a body costs energy with a fixed quantum, (2) it
takes time (the cell cycle), (3) offspring have a minimum viable size. The root is
that we collapsed *biomass* and *energy* into one variable (flagged for exactly
this in ADR 0013's deferral).

## Decision
Add a **fixed per-birth build cost** `BUILD_COST` (= 10.0), charged to the sink on
every birth, on top of the proportional yield loss. This captures physics #1 (the
irreducible biosynthesis overhead of assembling a unit) and is the reproduction
analogue of the metabolic `BASAL_COST`. The two cost models are now symmetric:

    metabolic bill   = BASAL_COST (fixed) + activity + maintenance (∝ energy)
    reproduction bill = BUILD_COST (fixed) + (1 − REPRODUCE_YIELD)·commit (∝ commit)

Per birth: `parent -= commit + BUILD_COST`; `child = REPRODUCE_YIELD · commit`;
`sink += (commit − child) + BUILD_COST`. A parent must afford `BUILD_COST` on top
of its commitment or it does not reproduce (an energy constraint — not a denied
birth, not a slot cap). Energy is still conserved (the build cost flows to the
sink); the audit balances.

`BUILD_COST` is a **world-harshness** constant (the cost of building a cell), set
once like `BASAL_COST` — *not* tuned to a population target. It makes spamming
tiny offspring net-lethal (each birth burns the fixed cost regardless of how small
the child) and bounds total births by the energy in the system
(`≈ (initial + inflow) / BUILD_COST`), so the population can no longer explode.

## Evidence (constants set for a livable world, not a survivor count)
Seed 42, 100 founders, 1000 ticks, with `BUILD_COST = 10`:
**4,950 births** (11× fewer), **peak population 833** (was 6,408), gen 35, final
population 31 across 10 lineages; audit error 1.2e-9. Seeds 1/7/13 all bounded
(peaks 540/942/523, gens 37/51/86), deterministic, demo unaffected (0 births).
A boom-bust still occurs but is bounded and livable — selection now runs on a
system that does not need an absurd slot pool.

## Why this is right, not just a knob
Three of the reproduction physics were missing; this adds the one with the
simplest honest form (a fixed cost), exactly mirroring how `BASAL_COST` fixed the
metabolic side. It changes no existing constant and is defined over the
conserved/flowing quantity (energy), so it survives representation changes. It
does **not** add a minimum-viable-child rule (the contract forbids that) — tiny
offspring are still *allowed*, just unprofitable, so viability stays emergent.

## Rejected
- Lowering `REPRODUCE_MAX` only (softens the boom but doesn't stop count-explosion
  via tiny offspring; closer to outcome-tuning than a new law).
- A hard population cap / denying births (smuggled carrying-capacity semantic).
- A coded minimum offspring size (the kernel deciding viability — contract §4).

## Deferred (the remaining reproduction physics — next milestone)
**Biomass as a distinct variable** (ADR 0013's deferred gift): size grown
gradually from energy (Pirt, lossy) so building takes *time* (physics #2),
maintenance scaling with size, and division *partitioning* biomass so a minimum
viable offspring size is *emergent* (physics #3). BUILD_COST is the fixed-cost
down payment on that fuller model; biomass is where time-and-size physics
genuinely live.
</content>
