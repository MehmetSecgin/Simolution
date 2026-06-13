# Kernel v2 — handover & next steps

Snapshot for whoever (owner or agent) picks this up next. Branch
`kernel-v2-reproduction`. Non-binding notes; the binding laws are
`docs/contract/contract-v2.md` and the ADRs.

## Where we are

Kernel v2 is complete and working: a deterministic VM where signal-graph units
**harvest, reproduce, mutate, and age**, and the population **evolves**. Confirmed:
a single lineage reproduced continuously to **generation 1757** (seed 7, 30
founders, 20k ticks), evolving into a legible harvest specialist (tripled
transporters, wired RESOURCE→HARVEST, added feedback, kept breeding, dropped
SELF_ENERGY) — all emergent, energy conserved (audit ~1e-9), deterministic.

Three degeneracies were found by running and each closed with a uniform law:
1. eat-forever hoarding → saturating uptake + storage maintenance (ADR 0011, v1)
2. reproduction spam / population explosion → fixed `BUILD_COST` (ADR 0016)
3. freeze-eat-persist (immortal non-reproducers freeze evolution) → entropic aging
   + germline renewal (ADR 0017)

## The current constants (all world-harshness / anchors — set once, never
## outcome-tuned; see contract §12/§16)

`KernelConfig`:
- energy/metabolism: `INITIAL_ENERGY=1000`, `BASAL_COST=0.5`,
  `COST_PER_PROPAGATION=0.06`, `STORAGE_LEAK_RATE=0.002`
- intake: `HARVEST_CAPACITY_PER_CONNECTION=1.5`, `HARVEST_HALF_SATURATION=1.0`,
  `RESOURCE_CAPACITY=100000`, `RESOURCE_INFLOW=50`
- sensing: `SELF_ENERGY_SCALE=2000`
- reproduction: `REPRODUCE_MAX=200`, `REPRODUCE_HALF_SATURATION=1.0`,
  `REPRODUCE_YIELD=0.7`, `BUILD_COST=10`
- variation + mortality: `MUTATION_RATE_PER_BIT=0.0003`, `AGING_COST=0.0005`

Tuning rule: change these only to make the world *livable / evolvable*, once —
never to hit a survivor count, lifespan, or generation target.

## Next idea on deck — differentiated mutation rates (owner's)

Today every gene bit flips at one rate (`MUTATION_RATE_PER_BIT`). But the 32-bit
gene splits cleanly into two *kinds* of change (see AGENTS.md / GeneDecoder):

```
[ SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16 ]
  bit 31      24-30     23          16-22     0-15
  \________ structure (rewires the graph) _______/  \__ weight (tunes gain) __/
```

- **Weight bits (0–15)** — quantitative tuning of an existing connection. Small,
  near-continuous effect. Mostly safe.
- **Structure bits (16–31)** — repoint a connection to a different node. Large,
  discrete, graph-rewiring. Mostly disruptive (the likely main driver of the
  error-catastrophe meltdown we saw).

Biology backs splitting them: most heritable variation is quantitative tuning;
topological/regulatory rewiring is rarer and bigger. Proposal: replace the single
rate with `MUTATION_RATE_WEIGHT` (high, lively annealing of weights) and
`MUTATION_RATE_STRUCT` (low, punctuated innovation). Expected payoff: smoother
evolvability *and* lower meltdown risk — protect topology while keeping weight
search fast.

Implementation (small, contained):
- `Kernel.mutate()` — pick the rate by bit index: bits 0–15 use the weight rate,
  bits 16–31 use the struct rate. Determinism keying unchanged (still per-bit
  index in the counter RNG).
- Two constants instead of one; an ADR; regenerate baseline; diff.
- Watch: does it raise sustained generation depth / lower extinction at fixed
  aging? Sweep as before (recompile per value, or finally add a `--mutation-*`
  CLI — see "papercuts").

## Other deferred next steps (roughly in order; contract §15)

1. **Variable-length genomes** (indels / gene duplication). Storage is *already
   provisioned* — per-slot `MAX_GENES` capacity + `geneCount`, hot loop eats
   variable counts — so this is **operators-only**, no storage rework. Needs:
   insert/delete/duplicate mutation operators, a `replication-cost ∝ genome
   length` term (so genomes don't bloat free), and a `MAX_GENES` cap behaviour
   (halt-on-exceed like MAX_UNITS, or soft). This is the big open-ended-complexity
   unlock: duplicate a HARVEST transporter gene → more capacity, unbounded.
2. **Biomass as a distinct variable** (Pirt). The fuller home for aging: `damage`
   becomes "degraded biomass fraction," repair = turnover, growth = energy→biomass
   over time (gives reproduction real *time* cost, the cell cycle). The current
   scalar `damage` is a down payment to be reinterpreted, not discarded.
3. **Lineage time-lapse genomes.** We export founder (gen 0) + final population
   only. To *watch* a circuit morph step-by-step, periodically dump a tracked
   lineage's genome to disk (bounded sampling, like the time series). Then the
   viewer could scrub generations.
4. **Observer parity for descendants.** Per-unit activity/regime metrics are
   founder-scoped; born units only get aggregate + final-snapshot coverage.
5. Later: CROWDING/EMIT + quorum, perceptual noise, environmental dynamics
   (fluctuating inflow / disturbance), space (global scalar → local field).

## How to run / inspect

```sh
# evolving population + all sidecars
./gradlew run --args="--units 30 --ticks 20000 --seed 7 --max-units 20000 --out runs/evolve.txt"
python3 tools/visualize.py runs/evolve.txt        # -> runs/evolve.html
```

Dashboard sections: economy, population dynamics, **Reproduction & evolution**
(pop/energy/births-gen over time), **Lineages** (scatter/bars/survivor table),
per-unit founder circuits (with regime/alive/feeding filters), and **Lineage
evolution** (founder vs evolved-descendant circuits side by side).

Deep-evolver seeds (30 founders, 20k ticks, current constants): seed 7 → gen
1757; many seeds go extinct (founder lottery — embraced, contract §10).

## Papercuts / gotchas

- `--max-units` is a memory bound, NOT a population cap: a birth with no free slot
  **halts** the run (by design). Size it above peak (~7–8× founders during the
  early boom) or it halts and you rerun bigger. Baseline uses `--max-units 10000`.
- Only `runs/baseline.txt` + `runs/baseline.units.csv` are committed; the
  `.lineage` / `.timeseries` / `.population` / `.popwiring` sidecars are
  gitignored (regenerate locally).
- **Mandatory baseline workflow** before any kernel-behaviour change: regen
  `runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --max-units 10000
  --out runs/baseline.txt`, diff, explain. `state-digest` catches sub-display
  drift. Note the digest depends on `--max-units` (empty slots fold as zeros), so
  keep it pinned.
- No CLI override for mutation/aging/reproduction constants yet — experiments mean
  editing `KernelConfig` + rebuild. If sweeping often, add `--mutation` etc. (one
  more `Kernel` constructor param; ~12 call sites to update, no compat shim per the
  global rule).
- Determinism: same genome set + seed → identical history (RAND and mutation both
  key off the fixed `KernelConfig.RANDOM_SEED=0`; the run `--seed` only picks the
  random founder genomes).

## Key files

- `kernel/runtime/Kernel.java` — 7-phase tick; per-slot store; `settleCost`
  (metabolism + aging), `settleReproduction` (phase 7), `mutate`, `installChild`,
  `liveConnections`.
- `kernel/config/KernelConfig.java` — all constants.
- `sim/DynamicsObserver.java` — running aggregates + per-lineage tallies.
- `sim/{RunReport,UnitCsvReport,WiringReport,LineageReport,PopulationReport,TimeSeriesReport}.java`
  — the report + sidecars (schema: `docs/specs/report-v6.md`).
- `tools/visualize.py` — the dashboard.
- ADRs 0014–0017; `docs/contract/contract-v2.md`.
</content>
