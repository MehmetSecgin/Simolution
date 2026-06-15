# Simolution

Artificial-life experiment: emergent survival under honest thermodynamics. The kernel defines physical laws (energy, decay, signal propagation) and never meanings, goals, or fitness. Lifelike strategies must emerge from evolution, never be coded in.

Current milestone: **Kernel v5 (biomass / Pirt size physics)** — a deterministic VM for *evolving* signal graphs living on a **2D toroidal lattice**, where units **move**, genome *length* is heritable, and each unit now has a *size* (`mass`) it grows and pays for. Genome = list of 32-bit genes, one gene = one weighted connection between nodes of a fixed substrate. Energy accounting (decay, activity cost, aging, death), reproduction + point mutation, space + motility carry over from v2/v3. v3 makes the world a *place*: each cell holds its own resource (uniform inflow, spread by diffusion); a unit occupies one cell (`position[slot]`, exclusive via `cellOccupant`), senses and harvests **only its own cell** (`LOCAL_RESOURCE` replaced the old global `RESOURCE` sensor), and offspring are placed in a free **Moore neighbour** (no free neighbour → no birth; the grid is the slot pool, so v2's `MAX_UNITS` halt-on-full is retired). Motility adds four `MOVE_N/S/E/W` effectors (Fork B): each tick a unit may take one Moore step (`dx` from E−W, `dy` from N−S, deadzone `θ`), paying `MOVE_COST`; blocked if the target is occupied; **chemotaxis is emergent** via `LOCAL_RESOURCE` + `DELAY`. The slot is a unit's permanent storage identity, so moving never recompiles/allocates (ADR 0020). Substrate grew with the 4 motility effectors, then v5's `SELF_MASS` sensor + `GROW` action (`NodeLayout.TOTAL` = 24: sensors 7, internals 8, actions 9). v4 makes genome **length** heritable: at birth, after point mutation, each gene may be **deleted** (`INDEL_RATE_DEL`) then each survivor may spawn a **tandem duplicate** (`INDEL_RATE_DUP`), changing the gene *count* within a soft `MAX_GENES` cap (floor 0 = legal + inert), so complexity itself evolves — duplicating a HARVEST transporter raises intake capacity (gene dosage, ADR 0012). Replication cost now scales with the child's length (`buildCost = BUILD_COST + BUILD_COST_PER_GENE·geneCount`, build-time anti-bloat pressure — **no** per-tick per-gene tax). Determinism holds via a monotone operation-counter RNG key (contract-v4, ADR 0026). v5 adds **biomass**: a unit has a *size* (`mass`) grown via a `GROW` effector (energy→mass, lossy) that scales maintenance (∝ mass — replacing the old energy-as-biomass proxy, `STORAGE_LEAK_RATE` retired), locomotion cost, and harvest (∝ mass^α, the surface-area law); mass *is* crystallized energy (one currency, audit gains a Σmass term). Reproduction becomes **symmetric binary fission** — mass + energy split 50/50, `REPRODUCE` an ungated *costly trigger* so the size-control checkpoint emerges via the new `SELF_MASS` sensor rather than being coded (no `M_DIV_MIN`); growth is phase 7 (pipeline 9→10). Determinism + audit hold (contract-v5, ADR 0028); report-v10 surfaces mass across the report (`## mass`), `.units.csv` (`final_mass`), `metrics.csv` (`mass_total`), and the map viewers (colour-by-mass). Next: CROWDING/EMIT + quorum, multi-resource + stoichiometry.

## Commands

```sh
./gradlew run     # demo: 4-gene feedback genome, 10 ticks, trace + report
./gradlew test    # JUnit 5; includes spec-conformance tests
./gradlew build   # full verification

# population run with deterministic report (schema: docs/specs/report-v6.md)
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100"
# flags: --units N --ticks T --seed S --genes G --world W --max-genes G2 --map-frames N --trace --out <file>
#        --resource-cycle --cycle-period N --cycle-radius N --cycle-peak X  (contract-v6, opt-in)
#        --map-from T0 --map-to T1  (map sampler: a frame EVERY tick only within [T0,T1] — tick-by-tick window of any phase, tiny data vs every-tick over the whole run)

# patchy + cyclic resource (contract-v6): a central disk pulses 0->peak->0 (period in ticks),
# periphery barren, diffusion spreads it; watch the breathing gradient live.
./gradlew run --args="--units 300 --ticks 30000 --seed 42 --world 100 --resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --out runs/cyc.txt"
# --world W = lattice side; the W×W grid IS the slot pool (one unit per cell,
#   contract-v3 §2/§5). Population is bounded physically by W² and energetically
#   by starvation — there is no halt-on-full anymore; a birth with no free
#   neighbour cell just doesn't happen. Founders (--units N) must fit: N ≤ W².
#   Default if omitted: W = ceil(sqrt(units·4)). --out also writes the sidecars.

# visualize a run as a self-contained HTML dashboard (stdlib python, no deps)
python3 tools/visualize.py runs/baseline.txt   # writes runs/baseline.html

# bake the spatial map (report-v13) into a SELF-CONTAINED rich viewer: the same
# genome panel + circuit inspector + colour by genome/mass/action as the live view,
# frames + catalog inlined, opens anywhere (even file://). The run writes lean
# <base>.frames + a <base>.catalog (genome dictionary) whenever --out is set
# (--map-frames N: 0 = EVERY tick [default], N>0 = N target frames, negative = off). Then:
python3 tools/mapviz.py runs/baseline.frames   # writes runs/baseline.map.html (offline, no server)

# observability layer (report-v8): optional, decoupled, off by default. Writes
# <base>.obs/ (manifest.json, events.jsonl, metrics.csv, ckpt/<tick>.ckpt) beside
# --out. Determinism is the time machine: store the replay key + periodic
# checkpoints, recompute any tick on demand. Sink-off runs stay byte-identical.
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100 --out runs/foo.txt --observe --checkpoint-every 2000"
# then reconstruct any tick at full fidelity (lattice + per-unit + evolved circuits):
./gradlew run --args="--replay runs/foo.obs --at 600 --snapshot-out runs/foo.obs/snap-600.txt"
python3 tools/timetravel.py runs/foo.obs/snap-600.txt   # writes snap-600.html
# aggregate queries go straight to DuckDB over events.jsonl / metrics.csv (no JVM dep)

# watch a run LIVE while it ticks (report-v13, serverless): no bespoke server — the
# viewer (live.html) parses the streamed .frames + .catalog client-side and tails the
# growing files over plain HTTP Range. serve-live.sh runs a sim in the background and
# serves runs/ with `python3 -m http.server`; open the URL to follow live, scrub after.
bash scripts/serve-live.sh   # serves runs/ on :8090 (edit its --args to change the run)
# then open http://localhost:8090  (or, for any run: serve runs/ and open <base>.html,
# which tails <base>.frames + <base>.catalog). Frames stay on disk, O(1) RAM; Ctrl-C to stop.

# lineage / "what happened to who": every --out run writes <base>.births.csv.gz
# (tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated).
# Query with DuckDB (no JVM); lineage+generation are kernel-exact, so genome history is:
#   duckdb -c "SELECT DISTINCT generation, child_genome_id FROM 'runs/baseline.births.csv.gz'
#              WHERE lineage=76 ORDER BY 1"   -- then diff catalog genes hop to hop
```

**Baseline workflow — mandatory before kernel-behavior changes**: regenerate
`runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --world 100 --out runs/baseline.txt`
after the change and diff it (`runs/baseline.txt` is the committed baseline; the
per-unit/wiring CSV sidecars were retired in report-v13). Output is byte-deterministic,
so every changed line was caused by your change; the `state-digest` line catches drift
below display rounding. Explain the diff (or its absence) when delivering.

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
    │   ├── Kernel            tick loop: clear → propagate → evaluate → swap → settle-intake → settle-cost → settle-growth → settle-reproduction → settle-movement → settle-diffusion (10 phases); W×W toroidal lattice, slot = permanent storage identity, cell = position[slot] (cellOccupant enforces one-per-cell), per-cell resource field (double-buffered), per-cell intake, spatial birth into a free Moore neighbour, MOVE_N/S/E/W motility, per-slot genes recompiled at birth (point mutation + indels — tandem duplication/deletion move geneCount within the soft maxGenes cap; never on move), per-gene replication build cost (buildCost = BUILD_COST + BUILD_COST_PER_GENE·geneCount); v5 biomass: per-slot mass (crystallized energy), GROW effector (settle-growth) energy→mass, maintenance/harvest(∝mass^α)/move-cost scale with mass, reproduction is symmetric fission (mass+energy split 50/50), death dissipates mass→sink (die helper); population derived (energy>0); saveState/loadState for report-v8 checkpoints (rebuilds structure from genes; mass + cellOccupant stored, not rederived) + connectionsOf(slot) for replay circuit inspection
    │   ├── KernelSnapshot    read view of tick + outputs + delay memory + energy + mass + sink
    │   └── Noise             stateless counter-based RNG: sample(seed, unit, tick)
    └── logging
        └── ConsoleTableLogger renders snapshots as a per-tick trace table
com.simolution.sim            run harness + observer (laws stay in kernel, interpretation here)
├── RunConfig                 CLI args: --units --ticks --seed --genes --trace --out
├── GenomeFactory             seeded random genomes; stateless hash like RAND noise
├── StructuralAnalyzer/Stats  wiring-derived stats: junk load, reachability, weights
├── DynamicsObserver/Summary  re-derives propagation from snapshots; activity, dormancy, regimes, digest
├── RunReport                 byte-deterministic report-v6 text (aggregates + distributions + reproduction)
├── MapFrameWriter            streams lean every-tick spatial frames to <base>.frames (report-v13: `u <cell> <slot> <mass> <action> <genomeId>`, RLE resource field, `end` trailer); O(1) RAM
├── GenomeCatalogWriter       <base>.catalog — global write-once genome dictionary (`g <id> <count> <genes>`); frames carry the id (report-v13)
├── BirthLogWriter            <base>.births.csv.gz — one row per derived birth (lineage/generation kernel-exact, genome ids from the shared catalog); DuckDB-queryable lineage store (report-v13)
├── UnitCsvReport             per-unit .units.csv sidecar (one row per unit)
├── WiringReport              per-unit .wiring.csv sidecar (one row per connection — the signature; docs/specs/report-v5.md)
├── RunManifest/ConfigHash    report-v8 replay key (verbatim founder genomes+cells, KernelConfig fingerprint)
├── EventLogWriter            report-v8 events.jsonl — births/deaths/extinctions derived from snapshot deltas; mutation bits by child↔parent gene diff (no kernel hook)
├── MetricsWriter             report-v8 metrics.csv — per-tick aggregates, streamed, O(1)/tick
├── CheckpointWriter          report-v8 ckpt/<tick>.ckpt — full-state checkpoints via Kernel.saveState every C ticks
├── Replayer                  report-v8 replay: open manifest, seekTo(T) = nearest ckpt + forward ticks, circuitOf(slot); refuses on configHash mismatch
└── SnapshotDump              report-v8 one-shot tick-T state page (lattice + per-unit + evolved circuits) for tools/timetravel.py
```

Gene bit layout (32 bits): `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. SrcType 0=sensor 1=internal; DstType 0=internal 1=action. IDs wrap modulo TYPE_COUNT, so **every random int is a legal gene**. Weight: signed int16 × (4.0 / 32767), linear, unclamped.

## Docs — read before changing kernel behavior

Binding (implementations MUST conform):

- [docs/contract/contract-v0.md](docs/contract/contract-v0.md) — the closed-system laws: time, energy, decay, dormancy, death, prohibitions
- [docs/contract/contract-v1.md](docs/contract/contract-v1.md) — open-system laws (energy intake); supersedes v0 §3/§10. Implemented (ADR 0010–0013)
- [docs/contract/contract-v2.md](docs/contract/contract-v2.md) — reproduction + mutation + aging laws (REPRODUCE effector, SELF_ENERGY, halt-on-full, abiogenesis, per-slot storage, BUILD_COST, entropic aging + germline renewal). Implemented (ADR 0014–0017); supersedes v1 §11
- [docs/contract/contract-v3.md](docs/contract/contract-v3.md) — space + motility: 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` local sensing, one unit per cell (slot=identity, cell=`position[slot]`), spatial birth (free Moore neighbour), `MOVE_N/S/E/W` motility (phase 8). Implemented (ADR 0018 geometry, 0019 economics, 0020 motility); supersedes v1 §2–§5 (global RESOURCE) and v2 §5 (halt-on-full)
- [docs/contract/contract-v4.md](docs/contract/contract-v4.md) — variable-length genomes (indels): heritable gene *count* via per-gene deletion + per-survivor tandem duplication at birth, soft `MAX_GENES` cap (floor 0 = inert), replication cost ∝ child length (`BUILD_COST_PER_GENE`), monotone operation-counter RNG keying. Implemented (ADR 0026); supersedes v2 §9's fixed-length provision (uses the v2 §12 provisioned storage)
- [docs/contract/contract-v5.md](docs/contract/contract-v5.md) — biomass (Pirt size physics): `mass` as a distinct state variable = crystallized energy (one currency, audit gains a Σmass term), `GROW` effector (energy→mass) + `SELF_MASS` sensor, maintenance ∝ mass (replaces the v1 energy proxy, `STORAGE_LEAK_RATE` retired), harvest ∝ mass^α (asserted surface law), move cost ∝ mass, reproduction = **symmetric binary fission** (split mass+energy 50/50, `REPRODUCE` an ungated trigger — no viability gate, size-control checkpoint emergent), growth = phase 7 (pipeline 9→10), death dissipates mass→sink. Implemented (ADR 0028); supersedes contract-v2 reproduction-investment
- [docs/contract/contract-v6.md](docs/contract/contract-v6.md) — patchy + cyclic resource inflow (**opt-in**): a central disk pulses `peak·triangle(tick)` (0→peak→0, period in **ticks** — no wall-clock), **barren periphery**, diffusion spreads it into a breathing radial gradient. Breaks spatial+temporal uniformity → niches (storage/timing/migration) so biomass finally pays. `InflowConfig` + `Kernel.configureInflow` (NOT a ctor param — engine, not DTO → baseline byte-identical), flags `--resource-cycle/--cycle-period/--cycle-radius/--cycle-peak`. Replay of cyclic runs guarded off. Implemented (ADR 0029); supersedes v1 §5 uniform inflow when enabled
- [docs/specs/v0-1/kernel-v0.1-vertical-slice.md](docs/specs/v0-1/kernel-v0.1-vertical-slice.md) — canonical v0.1 reference: substrate, encoding, execution phases
- [docs/specs/v0-1/kernel-v0.1-cache.md](docs/specs/v0-1/kernel-v0.1-cache.md) — what may be precomputed (structure-only) vs never cached (runtime state)
- [docs/nodes/delay.md](docs/nodes/delay.md) — DELAY node semantics
- [docs/specs/report-v6.md](docs/specs/report-v6.md) — run report + per-unit CSV + wiring CSV schema (reproduction section: births, generations, population, lineages); delta over [report-v5.md](docs/specs/report-v5.md)
- [docs/specs/report-v7.md](docs/specs/report-v7.md) — spatial map sidecar `<base>.map.txt` (resource field + per-cell lineage, sampled, streamed) + `tools/mapviz.py` HTML scrub player; delta over report-v6
- [docs/specs/report-v8.md](docs/specs/report-v8.md) — observability layer: deterministic-replay time-travel (manifest + checkpoints), decoupled `events.jsonl`/`metrics.csv` sinks (kernel stays pure — no event hook), DuckDB/Parquet query layer, `tools/timetravel.py`. Implemented (ADR 0023); `--observe` off by default, sink-off runs byte-identical; delta over report-v7
- [docs/specs/report-v9.md](docs/specs/report-v9.md) — genome-carrying map frames + circuit inspector: each `<base>.map.txt` `u` line now carries the unit's genome + per-tick node outputs, so the live viewer groups living units by genome, decodes any genome to a **force-directed circuit** (curved sign/weight edges, self-loops, ×N dosage, junk filter), and on hover isolates a node + shows its **actual weights** with per-tick active-signal glow — all client-side, no reconstruction. Kernel stays pure; delta over report-v7
- [docs/specs/report-v10.md](docs/specs/report-v10.md) — surface biomass (mass): new `## mass` report section (totals + alive distribution), `final_mass` column in `.units.csv`, `mass_total` column in `metrics.csv`, a `mass` field on each map-frame `u` line, and a **colour-by-mass** toggle in `tools/mapviz.py` + `live.html` (blue→red, scaled to run max) with mass mean/max readouts. Report `schema:` → report-v10, `kernel:` → v5. Harness + viewers only; kernel stays pure; delta over report-v9
- [docs/specs/report-v11.md](docs/specs/report-v11.md) — compact map frames: the `<base>.map.txt` `u` line was half redundant (genome re-emitted every frame though it only changes at birth) and half over-precise (full-`double` node outputs). report-v11 **dedups genome per slot** (`* <count> <genes>` define vs `^` carry-forward) and **rounds outputs to ≤4 decimals** (`NaN`→null), shrinking the u-portion ~60–80% (survivor 226 MB → ~45 MB). Fields 0–5 keep their position → `tools/mapviz.py` untouched; `LiveServer` carries genomes forward at parse → JSON + `live.html` untouched (and still reads pre-v11 files). Writer keeps an O(slots) last-genome cache (doctrine intact). Composes with the windowed sampler (`--map-from/--map-to`, commit `ac17dc3`). Harness + viewers only; kernel stays pure; text report `schema:` stays report-v10; delta over report-v10 (ADR 0030)
- [docs/specs/report-v13.md](docs/specs/report-v13.md) — lean inspection redesign: the fat `<base>.map.txt` `u` line (genome-per-frame + 24 node-output floats) becomes a **lean** `<base>.frames` row `u <cell> <slot> <mass> <action> <genomeId>`, with genomes deduped globally into a `<base>.catalog` (`GenomeCatalogWriter`) and the resource field RLE'd — full **every-tick** resolution now cheaper than the old sampled file (baseline 3.78 MB sampled → 678 KB frames + 131 KB catalog). Lineage/"what happened to who" moves to an always-on **`<base>.births.csv.gz`** (`BirthLogWriter`, DuckDB-queryable; lineage+generation kernel-exact, parent best-effort). **Retires the CSV zoo** (`UnitCsvReport`/`PopulationReport`/`WiringReport`/`LineageReport` + their tests deleted). Viewers (`live.html` + `mapviz.py`) rewritten: catalog join, colour by genome/mass/action, structural circuit. Kernel untouched (digest unchanged); `--observe` sinks stay opt-in; delta over report-v12 (ADR 0032)
- [docs/specs/report-v12.md](docs/specs/report-v12.md) — one serverless viewer: collapses the two inspectors (bespoke `LiveServer`+`live.html` rich; `tools/mapviz.py` lite offline) into a single rich `live.html` that parses `.map.txt` **client-side**. Two feeds: tail a growing file over plain HTTP **Range** (follow live via any static server, e.g. `python3 -m http.server` — **no bespoke server**) or `window.SIMOLUTION_EMBED` baked in (offline, `file://`). **Deletes** `LiveServer.java` + `--serve` + the `/frames` endpoint; `mapviz.py` becomes a **baker** (inlines frames into `live.html`); `serve-live.sh` → generic static server; `MapFrameWriter.close()` writes an `end` trailer. Also fixes `live.html`'s stale node layout (v3 `TOTAL=22` → v5 `TOTAL=24`, was mislabeling `SELF_MASS`/`GROW`). Kernel untouched; delta over report-v11 (ADR 0031)

Historical / non-binding:

- [docs/specs/v0/kernel-v0-vertical-slice.md](docs/specs/v0/kernel-v0-vertical-slice.md) — superseded by v0.1 slice
- [docs/notes/kernel-v0.1-planning.md](docs/notes/kernel-v0.1-planning.md) — planning conversation; module breakdown rationale

If code and a binding spec disagree, the spec wins. If a change requires the spec to change, update the spec in the same PR and say so.

## Invariants — do not break

- **No semantics in the kernel.** Nodes carry no meaning; the kernel never interprets, rewards, or privileges behavior. If a behavior feels "obvious" while coding, you are probably smuggling semantics.
- **Determinism.** Fixed seed, no wall-clock, no unseeded randomness, fixed evaluation order. Same genome + seed → identical tick history, always.
- **Tick pipeline is locked:** clear accumulators → propagate → evaluate → swap → settle intake → settle cost → settle growth → settle reproduction → settle movement → settle diffusion. New mechanics become additional phases (growth = phase 7, reproduction = phase 8, movement = phase 9, diffusion = phase 10), never reorderings of existing ones.
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
5. ~~Reproduction + mutation (evolution proper)~~ done (ADR 0014 design, 0015 implementation, 0016 BUILD_COST anti-degeneracy): REPRODUCE effector + SELF_ENERGY sensor, per-slot rewritable store, phase-7 settle-reproduction, point mutation, halt-on-full, fixed per-birth build cost. Variable-length genomes (indels) followed (item 10, done); biomass as a distinct variable (time/size physics) is next within evolution
6. ~~Space (basic)~~ done (ADR 0018 geometry, 0019 economics): 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` replaces global RESOURCE, one-pixel-one-unit, spatial birth into a free Moore neighbour, halt-on-full retired. Sessile. Yields coexisting lineages (vs the old monoculture).
7. ~~Motility~~ done (ADR 0020): `MOVE_N/S/E/W` effectors (Fork B), one Moore step/tick, `MOVE_COST`, deadzone, blocked-if-occupied; position decoupled from slot (no per-move recompile/alloc); chemotaxis emergent via LOCAL_RESOURCE + DELAY.
8. ~~Spatial map + scrub viewer + live viewer~~ done (report-v7): `MapFrameWriter` streams a sampled `<base>.map.txt` (resource field + per-cell lineage, O(1) RAM); `tools/mapviz.py` renders a self-contained HTML scrub player; `--serve PORT` started an in-JVM `LiveServer` (`com.sun.net.httpserver`, no deps) that tailed the file so a run could be watched live (`src/main/resources/live.html`, follow-live + scrub). **report-v12 (ADR 0031) removed the bespoke `LiveServer` + `--serve`**: the viewer now parses `.map.txt` client-side and tails it over a generic static server (or runs fully offline from baked frames).
9. ~~Observability layer~~ done (report-v8, ADR 0023): deterministic-replay time-travel (`RunManifest` + `CheckpointWriter` + `Replayer`, `Kernel.saveState`/`loadState`), decoupled `EventLogWriter`/`MetricsWriter` sinks (kernel stays pure — no event hook; events + mutation bits derived from snapshots/gene-diff), DuckDB/Parquet query layer, `tools/timetravel.py` time-travel page. `--observe` off by default; sink-off runs byte-identical.
10. ~~Variable-length genomes (indels)~~ done (contract-v4, ADR 0026): heritable gene *count* via per-gene deletion + per-survivor tandem duplication at birth (operation-counter RNG keying), soft `MAX_GENES` cap (floor 0 = inert), replication cost ∝ child length (`BUILD_COST_PER_GENE`, build-time anti-bloat — no per-tick per-gene tax). Gene dosage emergent (duplicate a transporter → more harvest capacity).
11. ~~Biomass (Pirt size physics)~~ done (contract-v5, ADR 0028): `mass` as a distinct crystallized-energy variable, `GROW` effector + `SELF_MASS` sensor, maintenance ∝ mass (replaces the energy proxy), harvest ∝ mass^α surface law, move cost ∝ mass, **symmetric binary fission** (split mass+energy 50/50), ungated `REPRODUCE` trigger (size-control checkpoint emergent — no `M_DIV_MIN`), growth phase 7, death dissipates mass→sink.
12. ~~report-v10 (surface mass)~~ done: `## mass` report section, `final_mass` (units.csv) + `mass_total` (metrics.csv) columns, `mass` on map-frame `u` lines, colour-by-mass in `tools/mapviz.py` + `live.html`.
13. ~~Fluctuating/patchy inflow~~ partial — done (contract-v6, ADR 0029): opt-in central pulsing disk (`--resource-cycle`), barren periphery, tick-period triangle wave, diffusion-spread gradient. Remaining: multiple/moving patches, replay support, seed-from-evolved-founder.
14. Later: CROWDING/EMIT + quorum, multi-resource + stoichiometry, sub-gene/selfish-DNA/whole-genome-duplication indel variants, catabolism + corpse decomposition + multi-cell bodies (contract-v5 omissions).
