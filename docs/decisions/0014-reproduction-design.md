# 0014 — Reproduction & mutation design (locked before implementation)

## Context
Planning the evolution-proper milestone (kernel v2): reproduction + mutation, the
roadmap's phase 5, taken only after intake (v1) gave the energy reproduction
spends. This ADR records the design decisions reached in discussion; the binding
spec is `docs/contract/contract-v2.md`. No code yet (mirrors ADR 0009 → contract-v1).

## Decisions

**Reproduction is an effector, never a kernel rule.** A new `REPRODUCE` action
node; the world reads it and converts output to an energy commitment by a uniform
saturating law. The kernel never auto-divides at a threshold/age — that would be a
smuggled fitness function. The *drive* to reproduce must itself evolve; a genome
that never wires `REPRODUCE` ends its lineage. A usable primitive must exist for
evolution to vary, so coding the ability (as with `HARVEST`) is an accepted
concession; the meaning stays in the genome.

**SELF_ENERGY sensor added.** Raw transduction of the unit's own charge, scaled to
O(1) (weight stays the only gain knob). Without it a genome cannot condition
reproduction on affordability. Not perception — no relationship/history computed
in the kernel. Substrate grows by two meaningful nodes (REPRODUCE + SELF_ENERGY),
`NodeLayout.TOTAL` 11 → 13.

**Conservation across birth; genome-controlled investment.**
`commit = REPRODUCE_MAX·drive/(REPRODUCE_HALF+drive)` (saturating, like uptake — a
runaway signal buys no extra offspring), clamped to parent energy;
`child = REPRODUCE_YIELD·commit`, remainder → sink (Pirt build cost). The amount is
the genome's choice, so r/K strategy is heritable and emergent. No kernel
minimum-viable child — too small a commit dies next tick, selected against.

**Halt-on-full, never deny a birth.** `MAX_UNITS` is a memory bound, not a
population regulator: refusing a birth would impose a carrying capacity (smuggled
semantic, contract §12). The only honest population control is energetic
starvation. So an exhausted pool **halts the simulation** deterministically with a
loud diagnostic; the run is redone larger. The energetic carrying capacity should
keep the halt from firing — it is a safety assertion the arena was sized big
enough.

**No coded aging.** Mortality is economic only (`energy ≤ 0`). Immortality stays
possible but is disfavoured by intake-dependence + slot competition, so it is rare
and evolutionarily inert without a lifespan knob.

**Death frees a slot.** Death is still the derived `energy ≤ 0`; v2 adds slot
reclamation. A birth claims the lowest-indexed free slot (deterministic); reuse
fully resets the slot (outputs/delay zeroed, genome installed + recompiled, counts
recomputed, lineage/energy set).

**Lineage bounded in state, pedigree offline.** Per-unit `lineageId` + `generation`
tags only (O(units), constant per unit); the full ancestry tree grows with births
(= ticks) so it lives only in an opt-in disk event-log, never in RAM.

**Abiogenesis — no seeded ancestor, no primordial soup.** Initial population is the
random `GenomeFactory` draw; replication must arise from it. Accepted consequence:
mutation fires only at reproduction, so there is no variation until the first
replicator — a one-shot t=0 lottery, ignition rare, most seeds extinct. Judged a
feature (reproduction is rare in nature; honest extinction is a real result). The
workflow is to scan seeds for ignition and study those runs.

**Point mutation now; variable length provisioned.** A gene is always 32 bits
(count is the variable axis, not width). This milestone's only operator is
per-bit point mutation (`MUTATION_RATE_PER_BIT`), deterministic via the
counter-RNG keyed `(seed, childSlot, birthTick, geneIndex)`. Storage is sized for
variable length from the start — `MAX_UNITS × MAX_GENES` + per-slot `geneCount`,
which the hot loop's existing `[start,end)` ranges already tolerate — so the
variable-length sub-milestone adds only operators, never a storage rework.

**Connection storage rework (the structural change).** v0/v1 stored all wiring as
one global packed `CompiledConnection[]` sorted by unit, built once, genome `int[]`
discarded. Reproduction breaks that: wiring appears at runtime. v2 makes the store
per-slot and rewritable at birth, retains per-slot raw genome `int[]`, recomputes
`harvestConnCount`/`mulInDegree` at birth, and makes `unitCount → MAX_UNITS` with
population derived (count of `energy > 0`). New tick **phase 7 settle-reproduction**
(child acts next tick; ascending-slot order for determinism). Footprint grows
`unitCount×G → MAX_UNITS×MAX_GENES` — justified in the implementation ADR.

## Rejected
- Kernel-threshold / age-triggered division (smuggled fitness function).
- Denying births at the slot cap (smuggled carrying-capacity regulator; halt instead).
- Coded senescence / lifespan cap (kernel deciding longevity; an outcome target).
- Seeded self-replicating ancestor and primordial-soup re-draw (dilute the honesty
  of the abiogenesis result; owner chose pure rarity).
- Full pedigree in memory (unbounded in ticks; violates memory doctrine).
- Fixed-`G` storage stride (would force a rewrite for variable length; provision
  `MAX_GENES` now instead).
- Sexual reproduction / recombination (v2 is asexual: one parent, copy + mutate).

## Deferred to the implementation ADR (decide when built)
Exact phase-7 read point (post-cost survivors only — leaning yes); SoA layout for
per-slot sum/mul regions; values of `MAX_UNITS`, `MAX_GENES`, `G`, `REPRODUCE_MAX`,
`REPRODUCE_HALF`, `REPRODUCE_YIELD`, `SELF_ENERGY_SCALE`, `MUTATION_RATE_PER_BIT`
(world-harshness/anchors — set once livable, never tuned to an outcome, contract
§16); report-v6 schema (births/deaths/generation/lineage aggregates).

## Deferred to later milestones
Variable-length genomes (indel/duplication operators + replication-cost-∝-length
term — the immediate next sub-milestone); primordial soup; sexual reproduction;
CROWDING/EMIT + quorum; perceptual fidelity/noise; genome-encoded efficiency;
biomass as its own variable; space.
</content>
