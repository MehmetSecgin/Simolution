# Simolution

Artificial-life experiment: emergent survival under honest thermodynamics. The kernel defines physical laws (energy, decay, signal propagation) and never meanings, goals, or fitness. Lifelike strategies must emerge from evolution, never be coded in.

Current milestone: **Kernel v0.1** — a deterministic VM for evolving signal graphs. Genome = list of 32-bit genes, one gene = one weighted connection between nodes of a fixed substrate. No energy, mutation, reproduction, or environment yet; those arrive as additive phases later.

## Commands

```sh
./gradlew run     # demo: 4-gene feedback genome, 10 ticks, trace + report
./gradlew test    # JUnit 5; includes spec-conformance tests
./gradlew build   # full verification

# population run with deterministic report (schema: docs/specs/report-v4.md)
./gradlew run --args="--units 100 --ticks 1000 --seed 42"
# flags: --units N --ticks T --seed S --genes G --trace --out <file>
# --out also writes <base>.units.csv (per-unit metrics) and <base>.wiring.csv (per-unit signature)

# visualize a run as a self-contained HTML dashboard (stdlib python, no deps)
python3 tools/visualize.py runs/baseline.txt   # writes runs/baseline.html
```

**Baseline workflow — mandatory before kernel-behavior changes**: regenerate
`runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --out runs/baseline.txt`
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
    │   ├── Kernel            tick loop: clear → propagate → evaluate → swap → settle-energy (5 phases); whole population in one flat array set
    │   ├── KernelSnapshot    read view of tick + outputs + delay memory + energy + sink
    │   └── Noise             stateless counter-based RNG: sample(seed, unit, tick)
    └── logging
        └── ConsoleTableLogger renders snapshots as a per-tick trace table
com.simolution.sim            run harness + observer (laws stay in kernel, interpretation here)
├── RunConfig                 CLI args: --units --ticks --seed --genes --trace --out
├── GenomeFactory             seeded random genomes; stateless hash like RAND noise
├── StructuralAnalyzer/Stats  wiring-derived stats: junk load, reachability, weights
├── DynamicsObserver/Summary  re-derives propagation from snapshots; activity, dormancy, regimes, digest
├── RunReport                 byte-deterministic report-v4 text (aggregates + distributions)
├── UnitCsvReport             per-unit .units.csv sidecar (one row per unit)
└── WiringReport              per-unit .wiring.csv sidecar (one row per connection — the signature; docs/specs/report-v4.md)
```

Gene bit layout (32 bits): `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. SrcType 0=sensor 1=internal; DstType 0=internal 1=action. IDs wrap modulo TYPE_COUNT, so **every random int is a legal gene**. Weight: signed int16 × (4.0 / 32767), linear, unclamped.

## Docs — read before changing kernel behavior

Binding (implementations MUST conform):

- [docs/contract/contract-v0.md](docs/contract/contract-v0.md) — the closed-system laws: time, energy, decay, dormancy, death, prohibitions
- [docs/contract/contract-v1.md](docs/contract/contract-v1.md) — open-system laws (energy intake); supersedes v0 §3/§10. **Design locked, not yet implemented** — the next build target
- [docs/specs/v0-1/kernel-v0.1-vertical-slice.md](docs/specs/v0-1/kernel-v0.1-vertical-slice.md) — canonical v0.1 reference: substrate, encoding, execution phases
- [docs/specs/v0-1/kernel-v0.1-cache.md](docs/specs/v0-1/kernel-v0.1-cache.md) — what may be precomputed (structure-only) vs never cached (runtime state)
- [docs/nodes/delay.md](docs/nodes/delay.md) — DELAY node semantics
- [docs/specs/report-v4.md](docs/specs/report-v4.md) — run report + per-unit CSV + wiring CSV schema; every metric defined with formula and spec cross-reference

Historical / non-binding:

- [docs/specs/v0/kernel-v0-vertical-slice.md](docs/specs/v0/kernel-v0-vertical-slice.md) — superseded by v0.1 slice
- [docs/notes/kernel-v0.1-planning.md](docs/notes/kernel-v0.1-planning.md) — planning conversation; module breakdown rationale

If code and a binding spec disagree, the spec wins. If a change requires the spec to change, update the spec in the same PR and say so.

## Invariants — do not break

- **No semantics in the kernel.** Nodes carry no meaning; the kernel never interprets, rewards, or privileges behavior. If a behavior feels "obvious" while coding, you are probably smuggling semantics.
- **Determinism.** Fixed seed, no wall-clock, no unseeded randomness, fixed evaluation order. Same genome + seed → identical tick history, always.
- **Tick pipeline is locked:** clear accumulators → propagate → evaluate → swap → settle energy. New mechanics become additional phases, never modifications of existing ones.
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
4. **Energy intake** — next build target; design locked in contract-v1 + ADR 0009 (RESOURCE depletable global pool + HARVEST, uniform transduction, acuity emergent from weights, well-mixed/location-free)
5. Reproduction + mutation (evolution proper) — only after intake
6. Later: CROWDING/EMIT + quorum, perceptual fidelity, space
