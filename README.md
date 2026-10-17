# Simolution

> **Artificial life under honest thermodynamics.** The kernel defines physical laws — energy, decay, diffusion, signal propagation — and *never* meanings, goals, or fitness. Lifelike strategies must **evolve**; they are never coded in.

<p align="center">
  <img src="docs/images/bloom.gif" width="460" alt="Every tick of a run: founders bloom, entropy culls them, survivors cluster around the breathing resource">
</p>

<p align="center"><sub>Every tick of one real run (seed 100): 400 scattered founders <b>bloom to ~3,000</b>, entropy and competition <b>cull them in waves</b>, and survivors persist around the breathing resource disk pulsing in at centre. No script — just physics and selection. Each colour is one genome lineage.</sub></p>

Simolution is a small, deterministic virtual machine for *evolving* signal-graph organisms on a 2D toroidal grid. Each organism is a genome — a list of 32-bit genes, each gene one weighted wire between nodes of a fixed substrate. The kernel ticks physics forward; mutation rewires the genomes; survival does the rest.

The defining rule is what the kernel **refuses** to do:

> **No semantics in the kernel.** Nodes carry no meaning. The kernel never interprets, rewards, or privileges a behaviour. There is no fitness function, no objective, no "good move." A cell that senses food and moves toward it does so only because that wiring out-survived the wiring that didn't. If a behaviour looks purposeful, it *emerged* — that is the entire experiment.

Everything a unit can do — eat, grow, hoard, move, reproduce — is an effector its genome may or may not drive. Whether it *should* is never answered by the code, only by the grave.

## A genome, decoded

Here is one organism's evolved circuit, drawn by the built-in viewer (junk wiring hidden):

<p align="center">
  <img src="docs/images/circuit.png" width="720" alt="Evolved genome circuit: sensors to internals to actions, signed weighted edges">
</p>

Signal flows left → right: **teal sensors → purple logic nodes → gold effectors**, with green/red edges for excitatory/inhibitory weights. Read what evolution wired here:

- **`LOCAL_RESOURCE → MOVE_W` (+1.15)** and **`LOCAL_RESOURCE → REPRODUCE` (+0.05)** — an emergent sense-and-respond reflex: the food sensor *directly* drives movement and breeding. Nobody wrote "move toward food." Selection did.
- **`DELAY ↔ CLAMP` (+1.08 / −1.68)** — a feedback loop through the one-tick memory node: a tiny evolved oscillator/integrator.
- Most of the 32 genes are **junk** — silent wiring carrying no signal. Hidden here, it is the raw material the next mutation might switch on.

## How it works

**Substrate.** Every unit shares the same fixed node layout (`NodeLayout.TOTAL = 24`):

| Sensors | Internals | Actions |
|---|---|---|
| `CONST`, `RAND`, `LOCAL_RESOURCE`, `SELF_ENERGY`, `SELF_MASS` | `ADD`, `MUL`, `CLAMP`, `DELAY`, `THRESH` | `ACTION_Y`, `HARVEST`, `REPRODUCE`, `MOVE_N/S/E/W`, `GROW` |

A genome is just which nodes are wired to which, and how strongly.

**Gene encoding (32 bits).** `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. IDs wrap modulo the node count and the weight is a linear signed scale, so **every random 32-bit integer is a legal gene** — mutation can never produce an un-decodable genome.

**The tick** is an ordered list of 12 declared phases, the same every step, with no per-tick allocation and memory only through `DELAY` (no instantaneous feedback). Each phase declares a shape (per-unit, per-cell, claim, serial) that fixes what it may touch and how it splits across threads:

```
clear → propagate → evaluate → swap
      → intake → inflow → cost → growth → reproduction → movement → diffusion → decay
```

Reproduction and movement are **claim** phases: every unit proposes in parallel from the start-of-phase world, then a fixed-order resolve picks winners (lowest slot wins a contested cell, no same-tick chains).

**Energy is conserved.** It is never created, only moved — into a cell from inflow, into a unit by harvesting, out to a sink by metabolism, growth loss, motility, and death. An audit invariant (`energy-audit-error ≈ 0`) holds every tick.

**Biomass & size.** A unit has a heritable `mass` (crystallized energy). Maintenance scales with mass, harvest scales with `mass^α` (a surface-area law), and a heavier body moves at higher cost — so growth is a real, evolvable trade-off. Reproduction is **symmetric binary fission**: mass and energy split 50/50, with no viability gate — the size-control checkpoint has to *emerge* via the `SELF_MASS` sensor.

**Space & motility.** The W×W grid *is* the population: one unit per cell. Each cell holds its own resource field, replenished by inflow (uniform, or a programmable pulsing/orbiting/noise field), spread by diffusion, and dissipated by decay so nothing stays free forever. Units sense only their own cell and take one Moore step per tick — **chemotaxis is emergent**, never coded.

**Evolution.** At each birth the child genome is copied with per-bit point mutation (drawn by skip sampling and keyed by the parent), then per-gene deletion and tandem duplication, so genome *length itself is heritable*. Duplicating a harvest transporter raises intake capacity (gene dosage); replication costs energy that scales with the child's length.

## Quickstart

Requires **JDK 26** (`.sdkmanrc` pins `26.0.1.0+8-open`).

```sh
./gradlew run     # demo: a 4-gene feedback genome, 10 ticks, trace + report
./gradlew test    # JUnit 5 + spec-conformance tests against an independent reference model
./gradlew build   # full verification

# a real population run — byte-deterministic report + sidecars
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100 --out runs/run.txt"
```

Useful flags: `--units N --ticks T --seed S --world W --genes G --max-genes G2`, `--threads N` (worker threads, default all cores; never changes results), `--config <file.json>` (the full per-run constants, see `examples/baseline.json`), `--digest-every-tick` (debug), plus a patchy, breathing resource world: `--resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10` (or the full `--inflow "<dsl>"`).

## Watch a run

An `--out` run with `--map-frames 0` (every tick; `N` for N frames) streams a compact spatial history (`.frames.zst` + genome `.catalog.gz`). Explore it in the browser:

```sh
# bake a self-contained, offline viewer (opens anywhere, even file://)
python3 tools/mapviz.py runs/run.frames.zst    # -> runs/run.map.html

# or follow a run LIVE while it ticks
bash scripts/serve-live.sh                      # serves runs/ on :8090
```

The viewer is what produced the images above: scrub the lattice (colour by genome), click any cell to inspect its **evolved circuit** as a force-directed graph, and walk a lineage's genome diffs over its `.births.csv.gz` (DuckDB-queryable). `--observe` writes a run directory with self-sufficient versioned checkpoints: seek to any tick (`--replay <dir> --at T`) on any thread count, or branch a run from a tick under changed rates, costs or inflow.

<p align="center">
  <img src="docs/images/lattice.png" width="460" alt="A single tick at peak, full resolution">
</p>

<p align="center"><sub>A single tick at peak, full resolution — <b>3,143 units, 457 distinct genomes</b>. Each colour is one lineage; the patches are territory nobody assigned.</sub></p>

## Determinism: the prime invariant

Same engine version + same config + same seed → **byte-identical state, on any machine and any thread count.** No wall-clock, no unseeded randomness; every draw is a pure function of the run seed, a stream tag and ids. Parallel phases split into fixed 4096-entry chunks that never depend on the thread count, totals are merged in chunk order, and order-dependent phases resolve in a fixed order. Tests run the same world on 1, 2, 4 and 8 threads and compare the digest every tick, and resume from a checkpoint on a different thread count. The signal state is single-precision `float` (the energy economy stays `double` so conservation is exact); kernel sources use `StrictMath` or exact operations only. Results may change between engine versions, never within one.

## Performance

`./gradlew bench --args="--threads N"` runs three fixed worlds (seed 42, observer and time series on every tick, no `--out`) and prints ticks/s. Apple M2 Pro, 12 cores, JDK 26, `-Xmx4g`, median of 3 timed runs. Engine v6 changed the dynamics (claim phases, new mutation keying), so v5 and v6 rows are not one identical workload.

| world | v5, 1 thread | v6, 1 thread | v6, 4 threads |
|---|---|---|---|
| small (64×64, 200 founders) | 1088 ticks/s | 12048 | 11976 |
| dense (200×200, 4000 founders) | 73 | 629 (8.6×) | 890 |
| big (500×500, 20000 founders) | 12 | 90 (7.5×) | 186 (2.07× over 1 thread) |

A 1000×1000 world with 50000 founders runs 100 ticks in 2.8 s (4 threads) inside `-Xmx4g`. The design's stopping rule asked for 2.5× from 1 to 4 threads on the big world; **that one is not met** (2.07×), because the serial claim resolves, the snapshot sums and the harness are about a fifth of the tick. A CX33 sanity run is still to be done. Profile, per-rule results and the exact commands are in [`docs/notes/engine-v6-bench.md`](docs/notes/engine-v6-bench.md).

## Engine architecture

- **State is declared once** (`WorldState` / `FieldSpec`): checkpoint, birth reset, death cleanup and the state digest all walk the same list. Flat primitive arrays, no object per organism.
- **The node layout is generated** from `NodeCatalog` and exported as `<base>.layout.json`; adding a sensor or action is one declaration.
- **Phases are a list with shapes**, run by a fixed-size `ParallelRunner` over fixed chunks with an allocation-free barrier.
- **The resource field is a chemical table** (one chemical today), with inflow, diffusion and decay per chemical.
- **A run is `EngineConfig` (JSON) + seed + founders.** Checkpoints store the full config, the node layout and the state digest, so they resume without side files; `seek` and `branch` build on them.
- Decisions are in [`docs/decisions/`](docs/decisions/) (0038 to 0045) and the binding laws in [`docs/contract/contract-v9.md`](docs/contract/contract-v9.md).

## Milestones

The kernel grew one honest law at a time, each on its own branch:

| | |
|---|---|
| **v0.1** | multi-unit signal-graph VM, energy accounting (decay, activity cost, death) |
| **v1** | energy intake — depletable resource + emergent harvest capacity |
| **v2** | reproduction + point mutation + entropic aging (evolution proper) |
| **v3** | space + motility — toroidal lattice, per-cell resource, `MOVE` effectors |
| **v4** | variable-length genomes — heritable gene count via indels |
| **v5** | biomass: `mass` as crystallized energy, growth, symmetric fission |
| **engine v6** | parallel, declaratively extensible engine: claim phases, per-run config, versioned checkpoints with seek and branch |

Next: sensors beyond the own cell, several resources, secretions and corpses. Each decision is recorded in a short ADR under [`docs/decisions/`](docs/decisions/), and the binding physical laws live in [`docs/contract/`](docs/contract/).

## Lineage & credit

Simolution owes its gene-as-wiring representation to **David Miller's [biosim4](https://github.com/davidrmiller/biosim4)** — the project that proved you could *watch* a population get better at a thing across generations. Simolution forks the philosophy: where biosim4 scores survival against a chosen challenge, Simolution removes the scorer entirely and lets thermodynamics be the only judge. (See [`docs/story/biosim4-tribute.md`](docs/story/biosim4-tribute.md).)

## Docs

[`AGENTS.md`](AGENTS.md) is the deep guide — architecture, invariants, the performance doctrine, and the full spec index. If code and a binding spec ever disagree, the spec wins.

## License

[MIT](LICENSE) — use it, fork it, build on it.
