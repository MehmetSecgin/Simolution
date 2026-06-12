# Kernel v2 Contract — Reproduction & Mutation (evolution proper)

## 0. Scope

Kernel v2 closes the evolutionary loop. v1 opened the system (energy intake);
units could persist but the population was fixed — a sorted-then-frozen set, not
an evolving one. v2 adds the two missing pieces: **reproduction** (units beget
units) and **mutation** (offspring differ from parents). With a replicator, a
source of variation, and the v1 selection pressure (finite energy, death), the
population now *evolves* — heritable strategies rise and fall on their own.

It **supersedes contract-v1 §11's "reproduction is out of v1"** and the fixed-population
assumption throughout v0/v1. Every other law — time, decay, the metabolic bill
(basal + activity + maintenance), intake, dormancy, death-as-`energy ≤ 0`,
conservation, no-semantics — still holds unchanged.

This contract is **binding**. If code and this contract disagree, the contract
wins.

---

## 1. The governing principle (no semantics, restated for reproduction)

Reproduction is the most dangerous feature yet for the "no semantics in the
kernel" invariant. The instant the kernel decides *who* reproduces, *when*, or
*how much*, it has written a fitness function — the precise thing this project
forbids. Evolution must not be steered; it must be *allowed*.

> **The kernel offers a reproduction mechanism uniformly and never invokes it.
> Whether, when, and how strongly a unit reproduces is computed by its own
> genome, exactly like harvesting. All differential reproductive success must
> emerge from genome and behaviour — never assigned, rewarded, or thresholded by
> the kernel.**

A bacterium divides because its own chemistry drove it to; physics never decided
that dividing was good. The kernel grants the *ability* to reproduce and the
*conservation law* it obeys, nothing more.

---

## 2. Reproduction is an effector, not a kernel rule (binding design line)

* Reproduction is driven by a dedicated **`REPRODUCE` action node**, a new
  meaningful effector alongside `ACTION_Y` and `HARVEST`. The world reads its
  output each tick and converts it to a reproduction *commitment* by a fixed,
  uniform law (§4).
* The kernel **never auto-divides** a unit at an energy threshold, an age, or any
  condition of its own. A threshold-triggered division would be the kernel
  declaring "enough energy → you should breed" — a smuggled goal. Rejected.
* Consequence: **the drive to reproduce must itself evolve.** A genome that never
  wires `REPRODUCE` positively leaves no offspring and its lineage ends. This is
  selection, not decree — and it is the whole point.
* This is, admittedly, the kernel coding an *ability* the way it codes `HARVEST`:
  a concession that a usable reproduction primitive must exist for evolution to
  have anything to vary. The meaning (when/how much to use it) stays entirely in
  the genome; the kernel supplies only the uniform physics.

---

## 3. SELF_ENERGY — sensing one's own charge (new sensor)

* A new meaningful sensor **`SELF_ENERGY`** lets a unit read its own current
  energy level. Without it, a genome cannot condition reproduction (or anything)
  on "do I have enough to afford this" — it would fire blind.
* This is **transduction, not perception** (contract-v1 §2): a raw
  measurement-at-a-point of the unit's own state, scaled to an O(1) range so the
  connection weight remains the only gain knob (same treatment as `RESOURCE`).
  No relationship, history, or comparison is computed in the kernel — a circuit
  that compares `SELF_ENERGY` against a `RESOURCE` trend is the genome's to build.
* Scaling: `SELF_ENERGY` emits `energy / SELF_ENERGY_SCALE`, clamped to a bounded
  range so a hoarding unit's signal cannot dominate all wiring (same concern that
  bounded the `RESOURCE` sensor in ADR 0010). `SELF_ENERGY_SCALE` is a uniform
  scale anchor, identical for every unit.

---

## 4. Parental investment and conservation (binding)

* Each tick, the world reads the alive unit's `REPRODUCE` output and converts it
  to an energy **commitment** by a fixed, uniform, **saturating** law — the same
  shape as harvest demand, for the same anti-divergence reason:

  `drive = max(0, reproduceOutput)` (you cannot un-reproduce; non-finite → 0)
  `commit = REPRODUCE_MAX × drive / (REPRODUCE_HALF + drive)`, clamped to the
  parent's available energy.

* `max(0, …)` and saturation mean a runaway signal buys no extra offspring
  investment — there is no payoff to exploding a loop into the reproduction
  channel, mirroring the §5 uptake law of v1.
* **Energy is conserved across a birth.** The child's starting energy comes
  entirely from the parent; the kernel creates none:

  `parent.energy -= commit`
  `child.energy   = REPRODUCE_YIELD × commit`
  `(1 − REPRODUCE_YIELD) × commit → sink`

  `REPRODUCE_YIELD ∈ (0,1]` is the reproduction efficiency (Pirt's growth-yield
  analogue; the lost fraction is the thermodynamic cost of building a new unit).
  It is a uniform world-harshness parameter, not a per-unit talent.
* **No kernel minimum viable child.** If a genome commits too little, the child
  is born under the basal floor and dies next tick — wasted energy, selected
  against. The kernel sets no floor on `commit`; viability is the genome's
  problem, discovered by selection. (A child born at any `commit > 0` is still a
  birth and still consumes a slot — see §5.)
* The **amount** invested is genome-controlled (via the saturating output), so
  parental-investment strategy — many cheap offspring vs few expensive ones (r/K
  selection) — is itself heritable and emergent, never coded.

---

## 5. The slot pool and halt-on-full (memory bound, never a regulator)

Memory discipline forbids unbounded population (footprint must stay constant in
tick count). So the world has a fixed pool of **`MAX_UNITS` slots**. A living unit
occupies a slot; death frees it (§6); a birth claims a free one.

* **`MAX_UNITS` is purely a memory allocation bound — never a population
  regulator.** Denying a birth because the pool is full would be the kernel
  imposing a carrying capacity: a smuggled population-control semantic (a
  violation of the §12 tuning discipline). The only honest population control is
  **energetic** — units starve and die (v1 §6a). A slot wall must never silently
  distort that dynamic.
* Therefore: **a birth is never refused.** If a unit reproduces and no slot is
  free, the **simulation halts** — deterministically, with a loud diagnostic
  (`tick T: MAX_UNITS=N exhausted, lineage L birth blocked — raise MAX_UNITS and
  rerun`). The run is then redone with a larger pool. Same seed → same halt tick,
  or never.
* `MAX_UNITS` is set generously above the expected **energetic** carrying
  capacity (`inflow ÷ per-capita need`), so in a livable world the halt never
  fires — it is a safety assertion that the arena was sized large enough, not a
  knob that shapes which life appears or how much of it. If it fires often, the
  arena was under-sized (operator fix), or the world's harshness lets population
  diverge (a physics finding) — either way, never patched by capping births.

---

## 6. Death reclaims a slot

* Death is unchanged as a **derived predicate**: a unit is dead iff
  `energy ≤ 0` (contract-v0 §9; no alive flag is ever stored). All hot-loop
  phases already skip dead units.
* v2 adds one consequence: **a dead unit's slot returns to the free pool** and may
  be claimed by a future birth. Slot allocation is deterministic — a birth claims
  the lowest-indexed free slot — so identical seeds reuse slots identically.
* On reuse, the slot is **fully reset**: outputs and delay memory zeroed (the
  corpse's frozen state from v0 §9 is wiped), the child's genome installed and
  compiled into the slot's connection region, `harvestConnCount` / `mulInDegree`
  recomputed, energy and lineage tags set. No stale state from the previous
  occupant survives.

---

## 7. Mortality is economic only (no coded aging)

* The kernel adds **no senescence, no lifespan cap, no age-dependent cost.** A
  coded clock would be the kernel deciding how long life lasts — a semantic knob
  and an outcome target (§12).
* "Living forever" remains *possible* but is disfavoured by the economy, not
  forbidden by fiat: persistence already requires continuous intake (v1 §6a), and
  in a reproducing population a non-reproducing immortal is out-competed for the
  finite reservoir by lineages that fill slots and draw the pool down. Individual
  immortality is thus rare and evolutionarily inert without any aging law.
* If empirically a "do-nothing immortal" comes to dominate (the v1 eat-forever
  exploit's analogue), that is a signal to examine the physics — not a license to
  add an aging knob to suppress it.

---

## 8. Lineage tracking (bounded in state, full pedigree offline)

* A full ancestry tree grows with births, i.e. with tick count — **forbidden as
  in-memory state** (memory doctrine). The kernel/observer must never accumulate
  a per-birth trajectory in RAM.
* In per-unit state, each unit carries small **fixed-size tags**: a `lineageId`
  (the root ancestor's id, inherited unchanged) and a `generation` counter
  (parent's + 1). These are O(units), constant per unit — within budget. They let
  the observer compute running aggregates (generation depth, lineage diversity)
  without storing history.
* The **pedigree itself** (who begat whom) is reconstructed *offline* from an
  opt-in birth/death **event log written to disk** (like `--trace`), never held in
  memory. Bounded-RAM by construction.

---

## 9. Mutation — point mutation now, variable length provisioned

* **A gene is always 32 bits.** Genome length — the *number* of genes — is the
  axis that may vary; the bit-width of one gene never changes (you add or remove
  whole genes, never resize one). These are independent.
* **This milestone's only operator is point mutation.** A child's genome is the
  parent's genome with each bit flipped independently at a small fixed probability
  (`MUTATION_RATE_PER_BIT`). Under point mutation alone, gene *count* is unchanged
  parent-to-child — but it is **not fixed by the storage model** (see below).
  "Replacing part of a gene" is just a point mutation (flipping the weight, dst,
  or src bits of a word) — it needs no separate operator.
* The encoding is already **mutation-safe** (contract-v0/AGENTS.md): every 32-bit
  int decodes to a legal gene, so any bit-flip yields a valid connection — no
  mutation can ever be rejected. Structural no-ops (a gene wiring into junk) are
  fine.
* Mutation is **deterministic**: the bit-flip decisions for a birth are drawn from
  the counter-based RNG keyed by `(seed, childSlot, birthTick, geneIndex)` — a
  birth is uniquely identified by its slot and tick (at most one birth per slot
  per tick), and the key is domain-separated from the `RAND` sensor's noise. Same
  seed → identical mutations, always.
* **Storage is provisioned for variable length from the start** (§12): each slot
  holds up to `MAX_GENES` genes plus a per-slot `geneCount ≤ MAX_GENES`. All
  initial genomes start at `G` genes (`G ≤ MAX_GENES`), and point mutation leaves
  `geneCount` at `G`, so the spare capacity sits unused this milestone. This costs
  only the larger array sizing now and makes variable length a **pure
  mutation-operator addition later** — zero storage rework.
* **Variable-length operators** (gene insertion / deletion / duplication, so genome
  size itself evolves and transporter count — and thus harvest capacity, ADR 0012
  — can grow open-endedly) are the explicit **next sub-milestone** (§15), with
  their own ADR. That ADR owns: the `MAX_GENES` cap behaviour (halt-on-exceed like
  `MAX_UNITS`, §5, vs a soft physical genome-size limit), a **replication cost ∝
  genome length** (a new world-harshness term so longer genomes cost more to copy
  — the natural pressure that bounds bloat and keeps the cap from binding), and the
  mutation-RNG re-keying (operation-counter instead of `geneIndex`, since indels
  shift indices).

---

## 10. Abiogenesis — no seeded ancestor, no primordial soup

* The initial population is **random genomes only** (the existing `GenomeFactory`
  draw). The kernel does **not** seed a hand-written self-replicating ancestor,
  and the world does **not** spontaneously inject new random genomes over time.
  Replication must arise from the random draw alone.
* **Accepted consequence — the t=0 lottery.** Because mutation only operates *at
  reproduction*, there is **no ongoing variation until the first replicator
  exists**: the starting population is a fixed random sample that simply decays
  unless one of its genomes already happens to wire a `REPRODUCE` circuit, driven
  positively, funded by enough `HARVEST`, before the basal floor kills it. Once
  one ignites, mutation takes over and evolution runs. Until then, nothing
  explores.
* This makes spontaneous ignition **rare** in a small substrate — most seeds will
  go extinct. That is judged a **feature, not a bug**: reproduction is an
  extraordinarily rare event in nature, and an honest extinction is a real result.
  The intended workflow is to **scan seeds** for ones whose initial draw produces
  units wiring the `REPRODUCE` action, then study those runs. Scaling the lottery
  (more units / genes / ticks) and a "primordial soup" re-draw source were
  explicitly **rejected** for this milestone — they would dilute the honesty of
  the result.

---

## 11. Tick pipeline (additive)

Reproduction is a **new additive phase 7**; phases 1–6 (clear → propagate →
evaluate → swap → settle-intake → settle-cost) are unchanged (the pipeline is
locked; new mechanics only append).

```
1 clear → 2 propagate → 3 evaluate → 4 swap → 5 settle-intake → 6 settle-cost → 7 settle-reproduction
```

* **Phase 7 reads the just-computed `REPRODUCE` output** (from `outputsPrev`, the
  swapped current tick) of each unit that is still alive after paying this tick's
  bill, computes its `commit`, transfers energy, claims a free slot, and installs
  the child.
* **The child does not act this tick.** It is born after all phases run; its slot
  is initialized now, and it first propagates on the *next* tick (its `energy > 0`
  brings it into phases 2–3 from then on). This removes any ordering paradox
  about a unit born and acting in the same tick.
* **Determinism under simultaneous births.** Parents are processed in ascending
  slot order; each claims the lowest free slot. This is deterministic (an
  identical seed produces identical slot assignments). Births are independent —
  each draws only from its own parent's energy — so no order-dependent shared
  resource is involved (unlike intake's pooled reservoir).

---

## 12. Architecture change: connection storage (needs an implementation ADR)

v0/v1 stored all wiring as a **single global `CompiledConnection[]`, packed and
sorted by unit, with contiguous per-unit `[start,end)` ranges**, built once at
construction; the raw genome `int[]` was discarded after compile. That model
assumes the population's wiring is fixed forever — which reproduction breaks at
the root.

v2 requires the connection store to become **per-slot and rewritable at birth**,
and it is sized for variable genome length from the start (§9) so that later is
free:

* Each slot owns a fixed **capacity** region for up to `MAX_GENES` connections,
  preallocated as `MAX_UNITS × MAX_GENES` for both the sum and mul connection
  arrays, with a per-slot **`geneCount ≤ MAX_GENES`** giving the live extent. The
  hot loop already indexes per-unit `[start,end)` ranges, so a variable live count
  per slot needs no runtime change — only the count varies. A birth **overwrites**
  its slot's region — no growth, no per-tick or per-birth heap allocation beyond
  reuse of existing buffers.
* Per-slot raw genome `int[]` is now **retained** (`MAX_UNITS × MAX_GENES` ints,
  `geneCount` of them live) so a child can be formed by copy-then-mutate.
* `harvestConnCount` and `mulInDegree` are recomputed for a slot **at birth**
  (per-birth work, bounded — not per-tick).
* `unitCount` becomes the slot capacity `MAX_UNITS`; **population is derived**
  (count of `energy > 0`), never stored. This fits the existing `energy ≤ 0`
  skip exactly: empty/dead slots cost one branch each, as corpses already do.

This grows the per-footprint connection store from `unitCount × G` to
`MAX_UNITS × MAX_GENES` — a real footprint increase that the implementation ADR
must justify against the memory budget (per AGENTS.md). `MAX_GENES` is a memory /
scale anchor (headroom for genome growth, §16), set above `G`; under this
milestone's point-mutation-only operator the spare capacity is unused, but
provisioning it now means the variable-length sub-milestone (§15) adds only
mutation operators, never a storage rework. It changes no existing constant and no
existing phase; it is additive structure plus a storage-model change, decided once
in its own ADR.

---

## 13. Conservation and audit (extended)

* Energy now flows **reservoir → units → {other units (births), sink}**. A birth
  moves `commit` out of the parent: `REPRODUCE_YIELD × commit` to the child,
  the remainder to the sink. No energy is created.
* The audit invariant generalises but stays exact (FP noise only):

  `INITIAL_unitEnergy × N₀ + RESOURCE_INITIAL + cumulativeInflow`
  `   == Σ unitEnergy (all live slots) + reservoir + sink`

  Inter-unit transfers (parent→child) net to zero inside `Σ unitEnergy`; only the
  yield loss reaches the sink. The `energy-audit-error` line must stay ~0.

---

## 14. Invariants retained

Determinism (same seed → identical history, including births, mutations, and slot
reuse), memory discipline (footprint linear in `MAX_UNITS`, constant in ticks; no
per-tick allocation or decoding in the hot loop; per-birth compile reuses
preallocated buffers), memory-only-via-DELAY, mutation-safe encoding, death as a
derived predicate (`energy ≤ 0`, no alive flag), and no-semantics-in-the-kernel
all still hold.

---

## 15. Future, explicitly out of v2

**Immediate next sub-milestone — variable-length genomes:** gene insertion /
deletion / duplication operators so genome size itself evolves and transporter
count (hence harvest capacity) can grow open-endedly. The storage is already
provisioned for it (§9, §12); this sub-milestone adds only the operators plus its
own ADR for the `MAX_GENES` cap behaviour and a replication-cost-∝-length term.

**Later milestones, each its own ADR:** a primordial-soup re-draw source; sexual
reproduction / recombination (v2 is asexual — one parent, copy + point mutation);
CROWDING / EMIT and quorum sensing; perceptual fidelity / noise; genome-encoded
efficiency traits; biomass as a variable separate from energy (Pirt's split, the
natural home for a richer growth term); and space (every global scalar → a local
field).

---

## 16. The tuning discipline still binds (contract-v1 §12)

The new constants sort into the same three kinds, and only world-harshness may be
set (once), never tuned to an outcome:

* **Scale anchors** — `SELF_ENERGY_SCALE`, `REPRODUCE_HALF` (set once).
* **World-harshness** — `REPRODUCE_MAX` (max investment rate), `REPRODUCE_YIELD`
  (build efficiency), `MUTATION_RATE_PER_BIT` (variation strength). These define
  how costly and how faithful reproduction is; set once to make an evolvable
  world, never tuned to hit a target generation count, lineage diversity, or
  population size.
* **`MAX_UNITS`** and **`MAX_GENES`** are memory bounds (scale/safety anchors),
  explicitly **not** regulators — `MAX_UNITS` does not cap population (§5) and
  `MAX_GENES` is headroom for genome growth, not a tuned complexity ceiling. The
  pressure that keeps genome length bounded is the future replication-cost term
  (§15), not the cap.
* **Outcome targets** — generations reached, number of lineages, time-to-ignition,
  population size, survivor count — **MUST NEVER be tuned.** They are the results
  evolution must produce on its own under whatever livable physics we fixed.
</content>
</invoke>
