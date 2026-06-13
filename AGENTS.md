# Simolution

Artificial-life experiment: emergent survival under honest thermodynamics. The kernel defines physical laws (energy, decay, signal propagation) and never meanings, goals, or fitness. Lifelike strategies must emerge from evolution, never be coded in.

Current milestone: **Kernel v3 (space + motility)** — a deterministic VM for *evolving* signal graphs living on a **2D toroidal lattice**, where units now also **move**. Genome = list of 32-bit genes, one gene = one weighted connection between nodes of a fixed substrate. Energy accounting (decay, activity cost, aging, death), reproduction + point mutation carry over from v2. v3 makes the world a *place*: each cell holds its own resource (uniform inflow, spread by diffusion); a unit occupies one cell (`position[slot]`, exclusive via `cellOccupant`), senses and harvests **only its own cell** (`LOCAL_RESOURCE` replaced the old global `RESOURCE` sensor), and offspring are placed in a free **Moore neighbour** (no free neighbour → no birth; the grid is the slot pool, so v2's `MAX_UNITS` halt-on-full is retired). Motility adds four `MOVE_N/S/E/W` effectors (Fork B): each tick a unit may take one Moore step (`dx` from E−W, `dy` from N−S, deadzone `θ`), paying `MOVE_COST`; blocked if the target is occupied; **chemotaxis is emergent** via `LOCAL_RESOURCE` + `DELAY`. The slot is a unit's permanent storage identity, so moving never recompiles/allocates (ADR 0020). Substrate grew with the 4 effectors (`NodeLayout.TOTAL` = 22: sensors 6, internals 8, actions 8). Next: a spatial map + live viewer (report-v7), then variable-length genomes and biomass.

## Commands

```sh
./gradlew run     # demo: 4-gene feedback genome, 10 ticks, trace + report
./gradlew test    # JUnit 5; includes spec-conformance tests
./gradlew build   # full verification

# population run with deterministic report (schema: docs/specs/report-v6.md)
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100"
# flags: --units N --ticks T --seed S --genes G --world W --max-genes G2 --map-frames N --serve PORT --trace --out <file>
# --world W = lattice side; the W×W grid IS the slot pool (one unit per cell,
#   contract-v3 §2/§5). Population is bounded physically by W² and energetically
#   by starvation — there is no halt-on-full anymore; a birth with no free
#   neighbour cell just doesn't happen. Founders (--units N) must fit: N ≤ W².
#   Default if omitted: W = ceil(sqrt(units·4)). --out also writes the sidecars.

# visualize a run as a self-contained HTML dashboard (stdlib python, no deps)
python3 tools/visualize.py runs/baseline.txt   # writes runs/baseline.html

# watch the spatial map (report-v7): scrub-player of the lattice over time —
# resource heatmap + units coloured by lineage, play/pause/slider. The run writes
# a sampled <base>.map.txt sidecar whenever --out is set (--map-frames N, default
# 120; 0 disables). Then:
python3 tools/mapviz.py runs/baseline.map.txt   # writes runs/baseline.map.html

# watch a run LIVE while it ticks (report-v7 live mode): the run starts an
# in-JVM HTTP server that tails the streamed .map.txt; open the URL to follow the
# lattice in real time, then scrub/replay after it ends (Ctrl-C to stop serving).
./gradlew run --args="--units 200 --ticks 40000 --seed 99 --world 130 --out runs/live.txt --serve 8090"
# then open http://localhost:8090  (--serve requires --out; frames stay on disk, O(1) RAM)
```

**Baseline workflow — mandatory before kernel-behavior changes**: regenerate
`runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --world 100 --out runs/baseline.txt`
after the change and diff it (both it and `runs/baseline.units.csv` are committed).
Output is byte-deterministic, so every changed line was caused by your change; the
`state-digest` line catches drift below display rounding. Explain the diff (or its
absence) when delivering.

Requires JDK 25 (`.sdkmanrc` pins `25.0.1-oracle`; `sdk env` activates it).

## Architecture

```
com.simolution
├── Main                      demo entry point
└── kernel
    ├── config.KernelConfig   constants: weight scaling, seed, unit/junk counts
    ├── layout
    │   ├── NodeLayout        flat index space: [sensors | internals | actions], offsets, junk padding
    │   ├── NodeType          SENSOR / INTERNAL / ACTION
    │   └── CompiledConnection {srcAbsIdx, dstAbsIdx, weight}
    ├── genome
    │   ├── GeneBuilder       fluent 32-bit gene packing (manual/test use only)
    │   ├── GeneDecoder       gene → CompiledConnection; modulo ID wrap; weight scaling
    │   └── GenomeCompiler    int[] genes → CompiledConnection[] (precompute, never per-tick)
    ├── runtime
    │   ├── Kernel            tick loop: clear → propagate → evaluate → swap → settle-intake → settle-cost → settle-reproduction → settle-movement → settle-diffusion (9 phases); W×W toroidal lattice, slot = permanent storage identity, cell = position[slot] (cellOccupant enforces one-per-cell), per-cell resource field (double-buffered), per-cell intake, spatial birth into a free Moore neighbour, MOVE_N/S/E/W motility, per-slot genes recompiled at birth (never on move), population derived (energy>0)
    │   ├── KernelSnapshot    read view of tick + outputs + delay memory + energy + sink
    │   └── Noise             stateless counter-based RNG: sample(seed, unit, tick)
    └── logging
        └── ConsoleTableLogger renders snapshots as a per-tick trace table
com.simolution.sim            run harness + observer (laws stay in kernel, interpretation here)
├── RunConfig                 CLI args: --units --ticks --seed --genes --trace --out
├── GenomeFactory             seeded random genomes; stateless hash like RAND noise
├── StructuralAnalyzer/Stats  wiring-derived stats: junk load, reachability, weights
├── DynamicsObserver/Summary  re-derives propagation from snapshots; activity, dormancy, regimes, digest
├── RunReport                 byte-deterministic report-v6 text (aggregates + distributions + reproduction)
├── MapFrameWriter            streams sampled spatial frames to <base>.map.txt (report-v7); O(1) RAM
├── LiveServer                --serve PORT: in-JVM HTTP server tailing .map.txt for the live viewer (report-v7)
├── UnitCsvReport             per-unit .units.csv sidecar (one row per unit)
└── WiringReport              per-unit .wiring.csv sidecar (one row per connection — the signature; docs/specs/report-v5.md)
```

Gene bit layout (32 bits): `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. SrcType 0=sensor 1=internal; DstType 0=internal 1=action. IDs wrap modulo TYPE_COUNT, so **every random int is a legal gene**. Weight: signed int16 × (4.0 / 32767), linear, unclamped.

## Docs — read before changing kernel behavior

Binding (implementations MUST conform):

- [docs/contract/contract-v0.md](docs/contract/contract-v0.md) — the closed-system laws: time, energy, decay, dormancy, death, prohibitions
- [docs/contract/contract-v1.md](docs/contract/contract-v1.md) — open-system laws (energy intake); supersedes v0 §3/§10. Implemented (ADR 0010–0013)
- [docs/contract/contract-v2.md](docs/contract/contract-v2.md) — reproduction + mutation + aging laws (REPRODUCE effector, SELF_ENERGY, halt-on-full, abiogenesis, per-slot storage, BUILD_COST, entropic aging + germline renewal). Implemented (ADR 0014–0017); supersedes v1 §11
- [docs/contract/contract-v3.md](docs/contract/contract-v3.md) — space + motility: 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` local sensing, one unit per cell (slot=identity, cell=`position[slot]`), spatial birth (free Moore neighbour), `MOVE_N/S/E/W` motility (phase 8). Implemented (ADR 0018 geometry, 0019 economics, 0020 motility); supersedes v1 §2–§5 (global RESOURCE) and v2 §5 (halt-on-full)
- [docs/specs/v0-1/kernel-v0.1-vertical-slice.md](docs/specs/v0-1/kernel-v0.1-vertical-slice.md) — canonical v0.1 reference: substrate, encoding, execution phases
- [docs/specs/v0-1/kernel-v0.1-cache.md](docs/specs/v0-1/kernel-v0.1-cache.md) — what may be precomputed (structure-only) vs never cached (runtime state)
- [docs/nodes/delay.md](docs/nodes/delay.md) — DELAY node semantics
- [docs/specs/report-v6.md](docs/specs/report-v6.md) — run report + per-unit CSV + wiring CSV schema (reproduction section: births, generations, population, lineages); delta over [report-v5.md](docs/specs/report-v5.md)
- [docs/specs/report-v7.md](docs/specs/report-v7.md) — spatial map sidecar `<base>.map.txt` (resource field + per-cell lineage, sampled, streamed) + `tools/mapviz.py` HTML scrub player; delta over report-v6

Historical / non-binding:

- [docs/specs/v0/kernel-v0-vertical-slice.md](docs/specs/v0/kernel-v0-vertical-slice.md) — superseded by v0.1 slice
- [docs/notes/kernel-v0.1-planning.md](docs/notes/kernel-v0.1-planning.md) — planning conversation; module breakdown rationale

If code and a binding spec disagree, the spec wins. If a change requires the spec to change, update the spec in the same PR and say so.

## Invariants — do not break

- **No semantics in the kernel.** Nodes carry no meaning; the kernel never interprets, rewards, or privileges behavior. If a behavior feels "obvious" while coding, you are probably smuggling semantics.
- **Determinism.** Fixed seed, no wall-clock, no unseeded randomness, fixed evaluation order. Same genome + seed → identical tick history, always.
- **Tick pipeline is locked:** clear accumulators → propagate → evaluate → swap → settle intake → settle cost → settle reproduction → settle movement → settle diffusion. New mechanics become additional phases (reproduction = phase 7, movement = phase 8, diffusion = phase 9), never reorderings of existing ones.
- **Energy is conserved.** Never created, only moved to the sink; charges clamp to available energy. The `energy-audit-error` line must stay ~0 (FP noise only). Death is `energy ≤ 0`, derived — never store an alive flag (contract §9).
- **No per-tick allocation or decoding.** Genome decoding happens once in GenomeCompiler. Hot loop touches flat arrays only.
- **Memory only via DELAY.** No instantaneous feedback; propagation reads previous-tick outputs only.
- **Data-oriented, not OO organisms.** Flat arrays + indices + phases. No object graphs between nodes.
- **Mutation-safe encoding.** Any 32-bit int must remain decodable. Never add validation that can reject a gene outright (structural no-ops are fine).

## Performance doctrine

The kernel must stay small enough to embed in a game loop later — think microcontroller-class budgets, not server-class.

- Primitives and flat arrays only in runtime state. No boxing, no collections, no streams, no lambdas in the hot path.
- Zero allocation and zero decoding inside `tick()`. Anything structure-derived is precomputed once (see cache spec).
- Memory budget is a feature: per-unit state is currently 4 × NodeLayout.TOTAL × 8 bytes. Any change that grows per-unit or per-connection footprint needs an ADR justifying it.
- **Memory discipline (core value).** Total footprint must be linear in units and CONSTANT in tick count. Concretely:
  - No unbounded collections anywhere in a loop. Nothing may accumulate per-tick history — observers and future instruments keep running aggregates (O(units)), never trajectories. If a feature seems to need history, it needs a bounded ring buffer and an ADR.
  - Observers preallocate every buffer in their constructor; `observe()`-style per-tick methods allocate nothing.
  - Any new allocation in a per-tick path needs an ADR. Known accepted exception: `Kernel.snapshot()` allocates one small view object per call — fine at game framerates, to be replaced with a reusable view if profiling ever shows GC pressure.
  - Boxing, varargs, streams, and string building stay out of per-tick paths (trace logging is exempt: opt-in, small runs only).
- Known future optimizations, deliberately deferred (each needs an ADR when taken): CompiledConnection object array → structure-of-arrays; double → float for state; reusable snapshot view.

## Decision records

Every non-obvious decision gets a short ADR in `docs/decisions/NNNN-slug.md` — 5–15 lines: context, decision, why, what was rejected. The owner reads ADRs to stay in full command of the project; write them for him, not for posterity. No decision is too small if a future reader might ask "why is it like this?"

## Working mode

The owner wants autonomous implementation but full understanding. So: small single-purpose commits, an ADR per real decision, and every delivered change explained in plain language (what changed, why, what was traded away). Never bundle an unexplained judgment call into a big diff.

## Conventions

- Conventional Commits (`feat:`, `refactor(kernel):`, ...).
- Javadoc is welcome here (project-local exception to any global no-comment rule): the owner wants deep understanding, so document the non-obvious — bit layouts, numeric tricks, invariant reasoning, spec cross-references. Skip prose on self-explanatory code; never narrate what a line does.
- Tests mirror main package layout; spec-conformance tests compare kernel output against an independent reference model (see KernelVerticalSliceTest).
- No known deviations from binding specs. MUL semantics (in-degree 0 → 0, 1 → pass-through, ≥2 → product of two strongest) per ADR 0005.

## Branching

Solo local repo: no remote, no `main` trunk, no PR flow. One long-lived branch **per milestone**, named after it (`kernel-v0.1`, `kernel-v1-energy-intake`, …). Start each new milestone branch off the previous milestone's tip; leave the old branch frozen as a marker of that finished slice. Do not create `main` or open PRs unless a remote is added later.

## Roadmap (from specs, in order)

1. ~~Multi-unit evaluation~~ done (ADR 0002, 0003)
2. ~~True MUL semantics~~ done (ADR 0005)
3. ~~Energy accounting: structural decay + activity cost~~ done (ADR 0006)
4. ~~Energy intake~~ done (ADR 0009 design, ADR 0010 implementation): RESOURCE depletable global pool + HARVEST action, intake before cost, emergent acuity, open-system audit (reservoir + units + sink + inflow)
5. ~~Reproduction + mutation (evolution proper)~~ done (ADR 0014 design, 0015 implementation, 0016 BUILD_COST anti-degeneracy): REPRODUCE effector + SELF_ENERGY sensor, per-slot rewritable store, phase-7 settle-reproduction, point mutation, halt-on-full, fixed per-birth build cost. Next within evolution: variable-length genomes (indels), then biomass as a distinct variable (time/size physics)
6. ~~Space (basic)~~ done (ADR 0018 geometry, 0019 economics): 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` replaces global RESOURCE, one-pixel-one-unit, spatial birth into a free Moore neighbour, halt-on-full retired. Sessile. Yields coexisting lineages (vs the old monoculture).
7. ~~Motility~~ done (ADR 0020): `MOVE_N/S/E/W` effectors (Fork B), one Moore step/tick, `MOVE_COST`, deadzone, blocked-if-occupied; position decoupled from slot (no per-move recompile/alloc); chemotaxis emergent via LOCAL_RESOURCE + DELAY.
8. ~~Spatial map + scrub viewer + live viewer~~ done (report-v7): `MapFrameWriter` streams a sampled `<base>.map.txt` (resource field + per-cell lineage, O(1) RAM); `tools/mapviz.py` renders a self-contained HTML scrub player; `--serve PORT` starts an in-JVM `LiveServer` (`com.sun.net.httpserver`, no deps) that tails the file so a run can be watched live (`src/main/resources/live.html`, follow-live + scrub).
9. Later: CROWDING/EMIT + quorum, multi-resource + stoichiometry, fluctuating/patchy inflow, variable-length genomes (indels), biomass as a distinct variable (Pirt).
