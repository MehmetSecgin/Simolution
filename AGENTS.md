# Simolution

Artificial-life experiment: emergent survival under honest thermodynamics. The kernel defines physical laws (energy, decay, signal propagation) and never meanings, goals, or fitness. Lifelike strategies must emerge from evolution, never be coded in.

Current milestone: **Kernel v0.1** — a deterministic VM for evolving signal graphs. Genome = list of 32-bit genes, one gene = one weighted connection between nodes of a fixed substrate. No energy, mutation, reproduction, or environment yet; those arrive as additive phases later.

## Commands

```sh
./gradlew run     # demo: 4-gene feedback genome, 10 ticks, table output
./gradlew test    # JUnit 5; includes spec-conformance tests
./gradlew build   # full verification
```

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
    │   ├── Kernel            tick loop: clear → propagate → evaluate → swap (double-buffered)
    │   └── KernelSnapshot    read view of tick + outputs + delay memory
    └── logging
        └── ConsoleTableLogger renders snapshots; presentation stays out of runtime
```

Gene bit layout (32 bits): `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. SrcType 0=sensor 1=internal; DstType 0=internal 1=action. IDs wrap modulo TYPE_COUNT, so **every random int is a legal gene**. Weight: signed int16 × (4.0 / 32767), linear, unclamped.

## Docs — read before changing kernel behavior

Binding (implementations MUST conform):

- [docs/contract/contract-v0.md](docs/contract/contract-v0.md) — the laws: time, energy, decay, dormancy, death, prohibitions
- [docs/specs/v0-1/kernel-v0.1-vertical-slice.md](docs/specs/v0-1/kernel-v0.1-vertical-slice.md) — canonical v0.1 reference: substrate, encoding, execution phases
- [docs/specs/v0-1/kernel-v0.1-cache.md](docs/specs/v0-1/kernel-v0.1-cache.md) — what may be precomputed (structure-only) vs never cached (runtime state)
- [docs/nodes/delay.md](docs/nodes/delay.md) — DELAY node semantics

Historical / non-binding:

- [docs/specs/v0/kernel-v0-vertical-slice.md](docs/specs/v0/kernel-v0-vertical-slice.md) — superseded by v0.1 slice
- [docs/notes/kernel-v0.1-planning.md](docs/notes/kernel-v0.1-planning.md) — planning conversation; module breakdown rationale

If code and a binding spec disagree, the spec wins. If a change requires the spec to change, update the spec in the same PR and say so.

## Invariants — do not break

- **No semantics in the kernel.** Nodes carry no meaning; the kernel never interprets, rewards, or privileges behavior. If a behavior feels "obvious" while coding, you are probably smuggling semantics.
- **Determinism.** Fixed seed, no wall-clock, no unseeded randomness, fixed evaluation order. Same genome + seed → identical tick history, always.
- **Tick pipeline is locked:** clear accumulators → propagate → evaluate → swap. New mechanics (energy, decay) become additional phases, never modifications of existing ones.
- **No per-tick allocation or decoding.** Genome decoding happens once in GenomeCompiler. Hot loop touches flat arrays only.
- **Memory only via DELAY.** No instantaneous feedback; propagation reads previous-tick outputs only.
- **Data-oriented, not OO organisms.** Flat arrays + indices + phases. No object graphs between nodes.
- **Mutation-safe encoding.** Any 32-bit int must remain decodable. Never add validation that can reject a gene outright (structural no-ops are fine).

## Conventions

- Conventional Commits (`feat:`, `refactor(kernel):`, ...).
- Tests mirror main package layout; spec-conformance tests compare kernel output against an independent reference model (see KernelVerticalSliceTest).
- Known intentional deviations from spec, tracked for later: MUL is pass-through (spec says product of two strongest inputs); evaluation hardcodes single unit (base = 0) though layout/config support UNIT_COUNT > 1.

## Roadmap (from specs, in order)

1. Multi-unit evaluation (base = unitIndex × NodeLayout.TOTAL)
2. True MUL semantics
3. Energy accounting: structural decay + activity cost (contract §4, §6)
4. Then, and only then: mutation, reproduction, environment, selection
