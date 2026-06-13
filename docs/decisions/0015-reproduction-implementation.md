# 0015 — Reproduction implementation (the deferred forks, decided)

## Context
Contract-v2 + ADR 0014 locked the reproduction *design* and left the
implementation forks open (the 0010-analogue for the intake milestone). This ADR
records how the build realises it and decides those forks. Binding spec is still
contract-v2; this records *how*.

## Decisions

**Substrate +2 meaningful nodes.** `Sensor.SELF_ENERGY` (emits
`min(1, energy/SELF_ENERGY_SCALE)`, bounded transduction of own charge) and
`Action.REPRODUCE` (accumulator passthrough, like ACTION_Y/HARVEST).
`NodeLayout.TOTAL` 11 → 13. Existing node indices unchanged (additions at each
block's tail), so explicit genomes still wire to the same nodes; only the larger
`TYPE_COUNT`s shift GeneDecoder's modulo wrap.

**Per-slot rewritable connection store.** The global packed `CompiledConnection[]`
(built once, genome `int[]` discarded) became a per-slot store: the kernel owns
the raw `genes` (flat `int[]`, `maxUnits × maxGenes`) + per-slot `geneCount`, and
`compileSlot()` decodes a slot's region into a single flat `connections[]` (sum
connections first in gene order, then mul, matching the old per-unit ordering so
the pre-reproduction baseline stayed byte-identical). `compileSlot` runs at
construction and at every birth. `unitCount` is now the slot capacity `maxUnits`;
population is derived (`energy > 0`), never stored. Constructor:
`Kernel(int[][] initialGenomes, int maxUnits, int maxGenes)` — every call site
updated, no compat shim.

**Phase 7 (settle-reproduction), read post-cost.** A new additive phase after
settle-cost. Only units alive after paying this tick's bill can reproduce; the
REPRODUCE output is read from `outputsPrev` (the swapped current tick), as harvest
already is. The child is installed with its node state zeroed, so it is inert and
first acts next tick — no same-tick grandchildren, no ordering paradox.

**Deterministic slot allocation.** Parents scanned ascending; each birth claims
the lowest free (`energy ≤ 0`) slot via a monotonic per-tick cursor (O(maxUnits)
per tick, not per birth). Corpses freed this tick are reusable. Same seed → same
genealogy and slot reuse.

**Mutation.** Point mutation only (fixed length this milestone): each gene bit
flips with `MUTATION_RATE_PER_BIT`, drawn from `Noise.mutationUniform(seed,
childSlot, birthTick, index)` — domain-separated from the RAND sensor by a third
hash dimension. Mutation-safe encoding means no flip can be rejected. Keyed by
`KernelConfig.RANDOM_SEED` (0L, as RAND is), so a genome set's evolution is
reproducible.

**Halt-on-full, not deny.** An exhausted pool throws (deterministic, loud
diagnostic naming the tick, cap, and lineage). Denying a birth would be a smuggled
population cap (contract §5). `maxUnits` is a memory bound; `--max-units` defaults
to `4 × --units` but real runs set it above the observed peak (~8× founders with
BUILD_COST, ADR 0016) or the run halts and is rerun larger.

**Constants (world-harshness / anchors, set once — not outcome-tuned).**
`REPRODUCE_MAX = 200`, `REPRODUCE_HALF_SATURATION = 1.0`, `REPRODUCE_YIELD = 0.7`,
`SELF_ENERGY_SCALE = 2000`, `MUTATION_RATE_PER_BIT = 0.001`. (The fixed per-birth
`BUILD_COST` is ADR 0016.)

**Observability split (report-v6).** The kernel exposes per-slot `lineageId` /
`generation` tags, `birthsTotal`, `maxGeneration`, and `creditedInitialEnergy` in
the snapshot. The observer's founder-scoped per-unit arrays stay sized to the
initial population (their activity/regime metrics describe the founders); the
reproduction aggregates (births, peak/final population, distinct lineages,
generations) are computed over *all* slots as running, bounded counters. The audit
baseline uses `creditedInitialEnergy` (= `INITIAL_ENERGY × seeded`), so
conservation holds across births (children's energy is transferred, not created).

## Rejected
- Storing the full pedigree in memory (unbounded in ticks; offline only, §8).
- Kernel-side independent activity re-derivation for born children — the observer
  can't know dynamic wiring; founder-scoped per-unit metrics + kernel-reported
  aggregates instead (full population-wide activity metrics deferred).
- Two separate sum/mul arrays sized to capacity (doubles footprint); one flat
  array with per-slot sum-then-mul sub-ranges is leaner and preserves ordering.

## Deferred
Variable-length genomes (indel/duplication operators + replication-cost-∝-length —
the next sub-milestone); biomass as a distinct variable (the faithful home for
reproduction's time/size physics, ADR 0016 deferral); full population-wide
activity metrics for born units; primordial soup; sexual reproduction.
</content>
