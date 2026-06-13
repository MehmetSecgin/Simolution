# Simolution — the journey (facts)

Factual timeline. Raw material for a write-up; no editorializing. Every number
below is taken from a committed run report, an ADR, or `KernelConfig`. Sources
named inline.

## What it is

A deterministic VM for evolving signal-graph "units." The kernel defines only
physics — energy, decay, signal propagation, space — and never goals, fitness,
or meaning. Any lifelike behavior has to emerge from random genomes under those
laws; none of it is coded.

- **Genome** = a list of 32-bit ints. One gene = one weighted connection on a
  fixed node substrate. Bit layout: `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`.
  IDs wrap modulo the node-type count, so **every 32-bit int is a legal gene** —
  no encoding can reject a mutation. Weight = signed int16 × (4.0 / 32767), linear, unclamped.
- **Determinism**: fixed seed, no wall-clock, fixed evaluation order. Same genome
  set + seed → byte-identical tick history, always. Reports are byte-deterministic
  so any diff is caused by the change that produced it.
- **Energy is conserved**: never created, only moved to a sink. The
  `energy-audit-error` line stays at floating-point noise (~1e-9). Death is the
  derived predicate `energy ≤ 0` — there is no stored "alive" flag.

## Timeline at a glance

| Milestone | When | Substrate | Tick phases | Headline observed result |
|-----------|------|-----------|-------------|--------------------------|
| Skeleton | 2026-01-04 | sensors+internals+1 action | 4 | the VM compiles and runs a genome |
| v0 closed system | 2026-06-11 → 06-12 | + energy/death | 5 | structures persist for different durations, then all die |
| v1 energy intake | 2026-06-12 | + RESOURCE, HARVEST | 6 | population can persist; first degeneracy (hoarding) found + closed |
| v2 reproduction | 2026-06-13 | + SELF_ENERGY, REPRODUCE | 7 | evolution to generation 1757; two more degeneracies found + closed |
| v3 space + motility | 2026-06-13 → (working tree) | + LOCAL_RESOURCE, MOVE_N/S/E/W | 9 | monoculture (1 lineage) → 6 coexisting lineages |

The four January commits are a skeleton. The whole working system was built in a
~3-day run, 2026-06-11 to 06-13.

---

## Skeleton — 2026-01-04

| Commit | Subject | What it did |
|--------|---------|-------------|
| `25ea4ba` | initial project structure and gradle | empty project + build |
| `3dc6dbc` | project specifications and contract docs | contract-v0 + v0.1 vertical-slice spec written before code |
| `a1891b9` | kernel core: genome, layout, runtime | the VM: 32-bit gene decode, flat node arrays, single-unit tick loop |
| `aacef3b` | main application entry point | demo: a hand-built feedback genome, traced for a few ticks |

Spec was written before the kernel. The contract fixes the laws; code conforms
to it, not the reverse.

---

## v0 — closed system — 2026-06-11 → 06-12

The world is a closed box: no intake, no reproduction, no space. Energy only
drains, via structural decay (entropy, charged every tick) and activity cost
(work done). Purpose stated in contract-v0: truthful energy accounting under
fixed laws — a baseline in persistence under entropy, not yet a life simulator.

| Commit | Subject | What it did |
|--------|---------|-------------|
| `6f0a17d` | extract console logging from kernel | kernel stays law-only; logging moves out |
| `8f7067f` | application plugin + JUnit 5 | runnable + test harness |
| `cab97f9` / `0ed5adf` | agent guides, performance doctrine, ADRs | working mode, memory discipline, decision-record practice |
| `0a4ee9d` | **multi-unit evaluation** | one Kernel owns the whole population in shared flat arrays; unit `u` lives at `u·TOTAL + offset` (ADR 0002) |
| `17c994b` | **deterministic run harness + report-v1** | external observer re-derives stats from snapshots so the kernel carries zero observation cost (ADR 0004); counter-based RNG `Noise.sample(seed,unit,tick)` makes a unit's values independent of population size and order (ADR 0003) |
| `c8edcf8` | **true MUL semantics** | MUL multiplies its two strongest inputs (in-degree 0→0, 1→pass-through, ≥2→product of top-2 by magnitude); split from the summing accumulator at compile time (ADR 0005) |
| `5e7db49` | **energy accounting, decay, death (phase 5)** | units hold energy; activity cost ∝ non-zero propagations, structural decay ∝ connection count; death-tick charge clamps to available energy; death = `energy ≤ 0`, derived, never a flag (ADR 0006) |
| `8b42312` | per-unit CSV sidecar, report-v3 burn-rate | per-unit detail (lifespan, burn rate, death tick) moves to a `.units.csv` so the report stays readable and history is never stored per-tick (ADR 0007) |
| `7290353` / `663c24f` | **first HTML visualizer + wiring export** | self-contained dashboard; `.wiring.csv` (one row per connection — a unit's signature); per-unit circuit diagrams (ADR 0008) |

**Observed (closed system):** every unit eventually dies; different wiring
persists for different durations; quiet ("dormant") regimes appear on their own
as the emergent absence of self-sustained dynamics — dormancy is never a coded
state. A connectionless unit never decays (costless immortality) — a property
v1 deliberately removed.

---

## v1 — energy intake (open system) — 2026-06-12

First sensor that reads the world (`RESOURCE`, a global depletable pool) and
first effector (`HARVEST`). Energy now flows reservoir → units → sink. Intake is
settled **before** cost (new phase, ahead of the energy charge). Stated risk
(contract-v1): intake is the most dangerous feature for the no-semantics rule —
the kernel must never grant energy *because a unit did something useful*. The
resolution: the pool refills uniformly; a unit with the right wiring captures it,
one without does not. Physics never decided which chemistry is "good."

| Commit | Subject | What it did |
|--------|---------|-------------|
| `de2859c` | lock energy-intake design (contract-v1, ADR 0009) | intake before reproduction; pool == sensor; acuity emergent from weights |
| `4d12194` | **open-system energy intake (kernel intake phase)** | depletable global pool + `RESOURCE` sensor + `HARVEST` action; bounded reservoir; unified scarcity law (ADR 0010) |
| `7191d05` / `aca308b` / `5b4cdbe` | open-system dashboard, pruned circuit viewer, depth layout | economy/harvest/survival views; circuits laid out by signal depth |
| `49191d9` / `d94f567` | **saturating uptake + storage maintenance** | the hoarding fix (below) — ADR 0011 |
| `eb93b43` / `417855c` | tuning discipline + emergent uptake capacity | eating capacity = `CAPACITY_PER_CONNECTION × transporter count`, i.e. heritable; removed the one constant that had been tuned for an outcome (ADR 0012) |
| `ac7e1bd` / `422d2fd` | biological cost model | drop per-connection decay (cells barely pay to *carry* genes, they pay to *express* them); replace with fixed `BASAL_COST = 0.5` per unit (ADR 0013) |

**Degeneracy #1 — eat-forever hoarding (ADR 0011).** With linear intake, a unit
that wired a `DELAY` self-loop with gain > 1 diverged its harvest signal
geometrically, monopolized the pool, and coasted forever because nothing charged
it for what it *held*. Evidence — `runs/long-seed7.txt` (v1, seed 7, 100 units,
20k ticks): 1 survivor of 100; final-energy-total **121,458** — *higher than the
100,000 the run started with* (it pumped the reservoir into one cell); 13 of 100
units terminally divergent.

**Fix.** Two uniform laws: saturating (Michaelis–Menten) uptake —
`demand = INTAKE_MAX·h/(HALF_SAT+h)` — caps how fast you can eat; and storage
maintenance — a leak `STORAGE_LEAK_RATE·energy` — caps how much it is worth to
hoard. Together they produce an emergent per-unit carrying capacity
`E* = (intake_max − cost) / leak`: hoard past it and you starve on your own bulk.
After the fix the diverger dies; no unit ends above ~1000 energy; survivors graze
the pool down to its inflow floor. Consequence: persistence now *requires* intake
(the costless-immortality husk of v0 is gone).

---

## v2 — reproduction + mutation + aging — 2026-06-13

Adds a replicator. `REPRODUCE` effector + `SELF_ENERGY` sensor. With mutation
(per-bit flips, counter-keyed) on top of v1's selection pressure, the population
evolves. The kernel never auto-divides — the drive to reproduce must itself
evolve. **Abiogenesis stance:** no seeded ancestor. Replication has to arise from
the random founder draw alone; most seeds go extinct, and an honest extinction is
treated as a real result.

| Commit | Subject | What it did |
|--------|---------|-------------|
| `356c887` | reproduction + mutation design (contract-v2, ADR 0014) | REPRODUCE as effector; conserved birth; halt-on-full not deny; lineage/generation tags only; point mutation |
| `0a45888` | SELF_ENERGY sensor + REPRODUCE action nodes | the two new meaningful nodes |
| `24ad75a` | per-slot rewritable connection store | each slot owns a mutable connection store so an offspring's mutated genome can be written into a freed slot without re-deriving (contract-v2 §12) |
| `2716483` | **reproduction + mutation, phase 7** | birth as a settle phase after cost (only affordable parents reproduce); deterministic ascending-slot allocation; point mutation via counter-RNG (ADR 0015); + **`BUILD_COST`** (ADR 0016) |
| `8fb35d4` | lineage + time-series exports, repro/lineage views | `.lineage` / `.timeseries` sidecars; reproduction + lineage dashboard sections |
| `c5c52c0` | **entropic aging + germline renewal** | per-unit `damage`, Gompertz feedback, offspring reset to `damage = 0`; evolved-circuit inspection (ADR 0017) |
| `f922f7f` | kernel v2 handover + next steps | snapshot of state, constants, and the next ideas |

**Degeneracy #2 — reproduction spam (ADR 0016).** Founders that happened to wire
`REPRODUCE` positively divided indiscriminately into ever-tinier offspring,
because a birth only moved energy and was instantaneous and unlimited. Evidence —
seed 42, 100 founders, 1000 ticks: **55,766 births, peak population 6,408**,
generation 95; a boom-bust that self-limits only by mass starvation and needs an
absurd slot pool to run.

**Fix.** A fixed per-birth `BUILD_COST = 10.0`, charged to the sink on every birth
(the biosynthesis analogue of `BASAL_COST`). It makes spamming tiny offspring
net-lethal and bounds total births by the energy in the system. After the fix,
same seed: **4,950 births (11× fewer), peak population 833**, generation 35 — a
bounded, livable boom-bust. No population cap and no minimum-child rule were added
(both would smuggle in semantics); tiny offspring are still allowed, just
unprofitable.

**Degeneracy #3 — the freeze (ADR 0017).** Contract-v2 had *bet* that immortality
would be rare and inert, so no aging law was written. The runs falsified the bet.
A unit harvesting at least its bill sits at equilibrium `E*` forever; once the
pool drains to its inflow floor, reproducing (costly, 30% yield loss) is locally
worse than just persisting. Evolution converged on non-reproducing immortal
harvesters and **froze**. Evidence: seed 10 evolved to **generation 146**, then
the winner stopped reproducing and persisted indefinitely.

**Fix.** Entropic aging + germline renewal. Each unit carries `damage` = its
lifetime energy dissipated to the sink; damage feeds back into upkeep
(`charge += damage·AGING_COST`, `AGING_COST = 0.0005`) so cost rises with age
(Gompertz). Offspring are born with `damage = 0` (asymmetric segregation, as in
yeast/E. coli old-pole inheritance) — a lineage escapes entropy only by
reproducing. Lifespan is never coded; it emerges from each genome's
work/harvest/reproduction balance (a frugal cell ages slowly). Death is still the
unchanged `energy ≤ 0`.

**Companion — error catastrophe.** Forcing continuous turnover exposed Eigen's
error threshold: at `MUTATION_RATE_PER_BIT = 0.001` (~1 flip/genome/birth) many
seeds melted down (deleterious flips outran selection → extinction). Lowered to
**0.0003** (~0.3 flips/genome/birth): high-fidelity copying, as real replication
uses.

**Headline observed result.** `runs/evolve.txt` (v2, seed 7, 30 founders, 20k
ticks): a single lineage reproduces continuously to **generation 1757** (35,789
births), peak population only **222** (aging bounds it — no explosion), 24
survivors all near gen 1751–1757. Energy audit error **9.3e-10**, fully
deterministic. The winner evolved (all emergent) into a legible harvest
specialist: tripled its transporters, wired `RESOURCE → HARVEST`, added feedback,
kept breeding, dropped `SELF_ENERGY`.

**The limit this exposed.** Of 30 founders, exactly **1 lineage survived** to the
end — a monoculture. In a well-mixed world a strategy competes against the
average, so the single fittest harvester wins everywhere and the population
converges to one circuit. No niches, no refuges, nowhere for diversity to hide.
(`runs/big.txt`, v2, seed 42, 500 units, 5000 ticks, shows 20 lineages alive — but
that is a mid-bloom snapshot, median death tick 41; over 20k ticks the well-mixed
world still collapses to one.)

---

## v3 — space + motility — 2026-06-13 → (current working-tree frontier)

The world becomes a place. Implemented in the working tree (contract-v3, ADR
0018/0019/0020) and the baseline regenerated to kernel v3; not yet committed on
this branch.

- **Geometry (ADR 0018).** A W×W toroidal lattice. One unit per cell — the
  W×W grid *is* the slot pool, so v2's halt-on-full is retired (a birth with no
  free neighbour cell simply doesn't happen). The torus is edgeless so no location
  is privileged (a walled box would make corners physically special — a coded
  asymmetry).
- **Per-cell economics (ADR 0019).** Each cell has its own resource: uniform
  inflow (`CELL_INFLOW = 1.0`) against the `BASAL_COST = 0.5` floor (livable for
  one frugal harvester, not lavish), capacity `CELL_CAPACITY = 100` (a standing
  crop ~100× the rain → an early bloom that relaxes to the inflow-limited carrying
  capacity), spread by diffusion (`DIFFUSION_RATE = 0.1`, slow enough that local
  depletion still creates gradients). The global `RESOURCE` sensor is replaced by
  `LOCAL_RESOURCE`: a unit senses and harvests **only its own cell**. Offspring are
  placed in a free Moore neighbour.
- **Motility (ADR 0020).** Four directional effectors `MOVE_N/S/E/W` (Fork B).
  Each tick a unit may take one Moore step (`dx` from E−W, `dy` from N−S, deadzone
  `MOVE_DEADZONE = 0.1`), paying `MOVE_COST = 0.2`; blocked if the target cell is
  occupied. Diagonals are emergent (two axes firing at once). The slot is the
  unit's permanent storage identity and the cell is `position[slot]`, so moving
  never recompiles or allocates. **Chemotaxis is emergent**, never rewarded:
  `LOCAL_RESOURCE` + `DELAY` give temporal sensing, so a unit can run-and-tumble
  toward food while blind to the true gradient, the way bacteria forage. Drift,
  run-and-tumble, escape, and sessility are all genome outcomes.

Pipeline is now 9 phases:
`clear → propagate → evaluate → swap → intake → cost → reproduction → movement → diffusion`.

**Headline observed result.** `runs/baseline.txt` (v3, seed 42, 100 units,
100×100 grid, 1000 ticks): **6 distinct lineages alive at the end** (vs the single
surviving lineage of the well-mixed v2 run), 1,228 births, max generation 20, peak
population 292, energy audit error 2.1e-8. Local depletion makes competition local,
so the monoculture can no longer take over everywhere — space is what makes
coexistence durable rather than a transient.

---

## The recurring pattern

Three degeneracies, each **found by running the thing**, each closed by adding one
uniform thermodynamic law — never by tuning toward a desired outcome:

| # | Degeneracy | Observed | Uniform law added | After |
|---|------------|----------|-------------------|-------|
| 1 | eat-forever hoarding | 1 survivor hoards 121,458 energy (>100k start) | saturating uptake + storage maintenance | diverger dies; emergent carrying capacity `E*` |
| 2 | reproduction spam | 55,766 births, peak 6,408 | fixed `BUILD_COST = 10` | 4,950 births, peak 833 |
| 3 | freeze-eat-persist | froze at gen 146 (immortal non-reproducers) | entropic aging + germline renewal | gen 1757, continuous turnover |

And one structural finding: a well-mixed world is inherently a monoculture machine
(gen-1757 single lineage); **space** is what gives diversity somewhere to hide
(1 → 6 coexisting lineages).

The discipline that makes these count as emergence rather than authoring:
constants are sorted into scale anchors, world-harshness (set once), and outcome
targets (never tuned). Every headline number — survivor count, lifespan,
generation depth, lineage count, population size — falls out of the physics, with
energy conserved to ~1e-9 and full determinism.

## Sources

- Contracts: `docs/contract/contract-v0.md` … `contract-v3.md`
- ADRs: `docs/decisions/0001-…` … `0020-motility.md`
- Run reports: `runs/long-seed7.txt` (v1), `runs/evolve.txt` + `runs/big.txt` (v2),
  `runs/baseline.txt` (v3)
- Constants: `src/main/java/com/simolution/kernel/config/KernelConfig.java`
- Substrate: `src/main/java/com/simolution/kernel/layout/NodeLayout.java`
- Handover: `docs/notes/kernel-v2-handover.md`
