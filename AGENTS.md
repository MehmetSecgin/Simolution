# Simolution

Artificial-life experiment: emergent survival under honest thermodynamics. The kernel defines physical laws (energy, decay, signal propagation) and never meanings, goals, or fitness. Lifelike strategies must emerge from evolution, never be coded in.

Current milestone: **Engine v6** on the **v5 biology** (contracts v0 to v9). The engine is a deterministic, multithreaded VM for *evolving* signal graphs living on a **2D toroidal lattice**; the same engine version, config and seed give a byte-identical state on any machine and any thread count (contract-v9). Genome = list of 32-bit genes, one gene = one weighted connection between nodes of a fixed substrate. Law constants named in CAPS in the contracts (`MOVE_COST`, `BUILD_COST`, ...) are fields of the per-run `EngineConfig` (camelCase), loaded from JSON with `--config`.

Biology in force:

- **Space.** Each cell holds its own resource (inflow, diffusion, decay; chemical 0 of a chemical table). A unit occupies one cell (`position[slot]`, exclusive via `cellOccupant`), senses and harvests only its own cell (`LOCAL_RESOURCE`), and the W×W grid is the slot pool (no free neighbour or slot, no birth). The slot is a unit's permanent storage identity, so moving never recompiles (ADR 0020).
- **Motility.** Four `MOVE_N/S/E/W` effectors: each tick a unit may take one Moore step (`dx` from E−W, `dy` from N−S, deadzone `θ`) paying `MOVE_COST`. Movement is a **claim** (contract-v9 §1): the target must be free at phase start, the lowest slot wins a contested cell, no chains. Chemotaxis is emergent via `LOCAL_RESOURCE` + `DELAY`.
- **Genome length is heritable** (contract-v4): at birth, after point mutation, each gene may be deleted (`INDEL_RATE_DEL`), then each survivor may spawn a tandem duplicate (`INDEL_RATE_DUP`), within the soft `MAX_GENES` cap (floor 0 = legal and inert). Replication cost is `buildCost = BUILD_COST + BUILD_COST_PER_GENE·geneCount`, build-time only. Mutation is keyed by the **parent** slot and point mutation is skip-sampled (contract-v9 §3).
- **Biomass** (contract-v5): `mass` is crystallized energy (audit gains a Σmass term), grown by the `GROW` effector, scaling maintenance, move cost and harvest (∝ mass^α, α = 0.5 so `sqrt`). Reproduction is **symmetric binary fission** (mass and energy split 50/50), `REPRODUCE` an ungated costly trigger, resolved as a **claim** (contract-v9 §2); the size-control checkpoint emerges via `SELF_MASS`, it is never coded.
- **Resource decay** (contract-v8) dissipates part of every cell's field to the sink each tick; the field starts empty.
- **Layout.** `NodeLayout.TOTAL` = 24 (sensors 7, internals 8, actions 9 including junk), generated from `NodeCatalog` and exported as `<base>.layout.json`.

Next: sensors that read beyond the own cell, several resources with separate harvesting, secretions, corpses that release their body stores, each with its own contract.

## Commands

```sh
./gradlew test    # JUnit 5; spec-conformance, determinism matrix, resume equivalence, strict-math lint
./gradlew build   # full verification

# population run with deterministic report (schema: docs/specs/report-v15.md)
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100"
# flags: --units N --ticks T --seed S --genes G --world W --max-genes G2 --trace --out <file>
#        --config <file.json>  (EngineConfig as JSON, see examples/baseline.json; --seed/--world/--max-genes override it)
#        --threads N           (worker threads, default all cores; NEVER changes results, see contract-v9 §7)
#        --digest-every-tick   (debug: prints `digest <tick> <hex>` after each tick; the report digest is end-of-run only)
#        --map-frames N --map-from T0 --map-to T1   (map frames, see below)
#        --observe --checkpoint-every C             (run directory, see below)
#        --resource-cycle --cycle-period N --cycle-radius N --cycle-peak X  (contract-v6, opt-in)
#        --inflow "<dsl>"      (contract-v7, opt-in; mutually exclusive with --resource-cycle)

# reproducible benchmark (fixed configs: small, dense200, big500; ticks/s per thread count)
./gradlew bench --args="--threads 4"     # numbers and the stopping-rule check: docs/notes/engine-v6-bench.md

# patchy + cyclic resource (contract-v6): a central disk pulses 0->peak->0 (period in ticks),
# periphery barren, diffusion spreads it; watch the breathing gradient live.
./gradlew run --args="--units 300 --ticks 30000 --seed 42 --world 100 --resource-cycle --cycle-period 2000 --cycle-radius 22 --cycle-peak 10 --out runs/cyc.txt"
# --world W = lattice side; the W×W grid IS the slot pool (one unit per cell,
#   contract-v3 §2/§5). Population is bounded physically by W² and energetically
#   by starvation; a birth with no free neighbour cell or free slot just doesn't happen.
#   Founders (--units N) must fit: N ≤ W². Default if omitted: W = ceil(sqrt(units·4)).
# --out <base>.txt writes the report plus <base>.timeseries.csv, <base>.layout.json
#   (node names, offsets, junk ranges, gene bit layout: the viewers read it),
#   <base>.catalog.gz and <base>.births.csv.gz. Map frames are opt-in (below).

# bake the spatial map (report-v14) into a SELF-CONTAINED rich viewer: the same
# genome panel + circuit inspector + colour by genome as the live view, frames + catalog
# inlined, opens anywhere (even file://). The run writes chunked-zstd <base>.frames.zst
# (lean row `u <cell> <slot> <genomeId>`) + <base>.frames.idx + the gzipped <base>.catalog.gz
# when --out is set and frames are requested
# (--map-frames N: negative/absent = off [default], 0 = every tick, N>0 = N frames;
#  --map-from T0 --map-to T1: a frame EVERY tick only within [T0,T1]). Then:
python3 tools/mapviz.py runs/baseline.frames.zst   # writes runs/baseline.map.html (offline; needs the zstd CLI)

# run directory (engine v6, ADR 0043): optional, off by default. --observe with --out writes
# <base>.obs/ = run.json (runId = SHA-256 of the first checkpoint, engine version, parent),
# ckpt/<tick>.ckpt (self-sufficient, versioned checkpoint format 2 with the state digest in
# the header; tick 0 at creation, then every --checkpoint-every ticks, default 2000),
# events.jsonl and metrics.csv. Sink-off runs stay byte-identical.
./gradlew run --args="--units 100 --ticks 1000 --seed 42 --world 100 --out runs/foo.txt --observe --checkpoint-every 500"
# seek: reconstruct any tick (nearest checkpoint at or before T, then step forward). Works for
# any thread count and for inflow runs (the checkpoint carries the full config):
./gradlew run --args="--replay runs/foo.obs --at 600 --snapshot-out runs/foo.obs/snap-600.txt"
python3 tools/timetravel.py runs/foo.obs/snap-600.txt   # writes snap-600.html
# branch (API, no CLI flag): CheckpointCodec.readBranch(in, changedConfig, threads) + RunDirectoryWriter.createBranch.
# A branch may change rates, costs, thresholds and inflow; it may not change world size, maxGenes
# or chemical count. Checkpoints from another engine version or format are refused.
# aggregate queries go straight to DuckDB over events.jsonl / metrics.csv (no JVM dep)

# watch a run LIVE while it ticks (report-v14): the viewer (live.html) loads the tiny
# .frames.idx (chunk index) + whole .catalog.gz, then HTTP-Range-fetches ONE zstd chunk
# (256 frames) on scrub and fzstd-decodes it client-side — O(one chunk) browser RAM on any
# run. NB this needs a Range-capable static server: the stdlib `python3 -m http.server`
# IGNORES Range (returns the whole file), so use scripts/serve.py (a stdlib subclass that
# answers 206); the served viewer also needs fzstd.min.js beside it. serve-live.sh does both.
bash scripts/serve-live.sh   # runs a sim in the background, serves runs/ on :8090 via serve.py
# then open http://localhost:8090  (or any run: python3 scripts/serve.py 8090 runs, open <base>.html)

# lineage / "what happened to who": every --out run writes <base>.births.csv.gz
# (tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated).
# parent_slot is exact: rows come from the engine's published per-tick birth list.
# Query with DuckDB (no JVM); lineage+generation are kernel-exact, so genome history is:
#   duckdb -c "SELECT DISTINCT generation, child_genome_id FROM 'runs/baseline.births.csv.gz'
#              WHERE lineage=76 ORDER BY 1"   -- then diff catalog genes hop to hop
```

**Baseline workflow — mandatory before kernel-behavior changes**: regenerate
`runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --world 100 --out runs/baseline.txt`
after the change and diff it (`runs/baseline.txt` is the committed baseline, `state-digest:
78644e65e8086e45`; only the report is tracked, the sidecars next to it are not). Output is
byte-deterministic and identical for any `--threads` value, so every changed line was caused by
your change; the `state-digest` line is the digest of the whole final state (`StateDigest`) and
catches drift below display rounding. Explain the diff (or its absence) when delivering.

Requires JDK 26 (`.sdkmanrc` pins `26.0.1.0+8-open`; `sdk env` activates it).

## Architecture

```
com.simolution
├── Main                      entry point: reads like the run (`RunConfig` → `Founders` → observers → `SimulationRun` → report), `--replay` goes to `ReplayCommand`
├── bench.Bench               reproducible benchmark (`./gradlew bench`): small / dense200 / big500, median of 3 timed runs
└── kernel
    ├── EngineVersion         the one engine version constant (report header, checkpoints, run.json)
    ├── config
    │   ├── EngineConfig      per-run law constants as one immutable record grouped into `World`, `Founder`, `Metabolism`, `Harvest`, `Motility`, `Growth`, `Sensing`, `Reproduction`, `Mutation` (plus the chemical table); `defaults(seed, worldWidth, maxGenes)` and `with…` copies; JSON stays flat through `ConfigJson` (`--config`); `branchCompatible` says which changes keep the state shape
    │   ├── ChemicalSpec      one diffusing field: inflow, capacity, initial, diffusion, decay, decay target (ADR 0042)
    │   └── InflowConfig / InflowSource / CompiledInflowField / ValueNoise   programmable inflow field (contract-v7), static sources baked once
    ├── layout
    │   ├── NodeCatalog/NodeSpec  declared sensors, internals, actions + junk counts (ADR 0038)
    │   ├── NodeLayout        offsets, TOTAL, TYPE_COUNT and name lookup generated from the catalog; `toJson()` is `<base>.layout.json`
    │   └── CompiledConnection {srcAbsIdx, dstAbsIdx, weight}   inspection view only (`Kernel.connectionsOf`), not the engine's storage
    ├── genome
    │   ├── GeneLayout        the 32-bit gene layout once: shifts, masks, `pack` and the named field accessors
    │   ├── GeneBuilder       fluent gene packing over `GeneLayout` (manual/test use only)
    │   ├── GeneDecoder       gene → local source/destination index, weight; modulo ID wrap; weight scaling (`NOMINAL_MAX_WEIGHT` is nominal, raw -32768 decodes just past it)
    │   ├── SlotCompiler      writes a slot's flat connection region (source/destination/weight arrays), junk-source connections pruned; once per slot at start, at birth and at restore, no allocation
    │   └── GenomeCompiler    int[] genes → CompiledConnection[] (harness structure stats and tests)
    ├── state
    │   ├── WorldState        every state array and scalar, allocated once; `fields()` declares each as a FieldSpec (ADR 0038)
    │   ├── FieldSpec         name, dimension, persistence (SAVED/DERIVED/SCRATCH), birth rule, death rule
    │   ├── StateDigest       parallel chunked digest of the SAVED state (ADR 0044)
    │   └── CheckpointCodec   self-sufficient versioned checkpoint, format 2 (ADR 0043)
    ├── phase
    │   ├── Phase / Shape     a declared step and its shape: PER_UNIT, PER_CELL, CLAIM, SERIAL (ADR 0039)
    │   ├── Chunks            fixed 4096-entry chunk boundaries and chunk-ordered sums
    │   ├── PhaseRunner / ParallelRunner   run the list; the parallel one uses a fixed pool, an atomic chunk counter and an allocation-free barrier
    │   └── Clear, Propagate, Evaluate, Swap, Intake, Inflow, Cost, Growth, Reproduction, Movement, Diffusion, Decay   the 12 phases of the tick, in that order (Reproduction and Movement are CLAIM, Swap is SERIAL)
    ├── runtime
    │   ├── Kernel            builds the phase list and runs it: W×W toroidal lattice, slot = permanent storage identity, cell = position[slot]; `tick()`, `step(n)`, `snapshot()`, `stateDigest()`, `connectionsOf(slot)`, `close()` (stops workers). Population is derived (energy>0)
    │   ├── KernelSnapshot    read view of tick + outputs + delay memory + energy + mass + sink; the tick's birth list (`birthParent`, `birthChild`, `birthCount`) is published in `WorldState`
    │   └── Noise             stateless counter-based RNG with tagged streams (RAND 1, mutation 2, placement 3), all keyed by the run seed
    └── logging
        └── ConsoleTableLogger renders snapshots as a per-tick trace table
com.simolution.sim            run harness + observer (laws stay in kernel, interpretation here)
├── RunConfig / RunOptions    CLI parse into grouped records (`Observation`, `MapSampling`, `EngineConfig`); `ArgCursor` reads flags and fails with "<flag> needs a value"; `InflowSpecParser` reads the `--inflow` DSL
├── Founders                  the founder genomes, cells and engine config of a run; `newKernel(threads)`
├── GenomeFactory             seeded random genomes; stateless hash like RAND noise
├── SimulationRun / RunResult the tick loop: tick, snapshot, every `TickObserver` in order; final digest and elapsed time
├── RunArtifacts / RunPaths   opens the `--out` sidecars and the `--observe` run directory, lists their observers; `RunPaths` names every sidecar beside the report
├── RunConsole                report and timing lines on stdout
├── ReplayCommand             `--replay <dir> --at T`: seek and dump a snapshot
├── StructuralAnalyzer/Stats  wiring-derived stats of the founders: junk load, reachability, weights
├── DynamicsObserver/Summary  running aggregates over snapshots (activity, populations, energy, mass) into grouped summary records; no per-unit state, no trajectory
├── RunReport                 byte-deterministic report-v15 text in one method per section (aggregates + distributions + reproduction)
├── TimeSeriesSampler         <base>.timeseries.csv, one row per tick
├── MapFrameWriter            writes map frames: `FrameSampler` picks the ticks, `SlotGenomeCache` maps slots to catalog ids, `ResourceLevels` quantises the field, `ChunkedFrameStream` seals 256-frame zstd-19 chunks into <base>.frames.zst (report-v14: lean row `u <cell> <slot> <genomeId>`; RLE resource field) and the ASCII <base>.frames.idx (`<firstTick> <lastTick> <off> <len> <count>`, one zstd chunk per Range fetch); O(one open chunk) RAM, live tail by polling the idx (≤256-tick lag)
├── GenomeCatalogWriter       <base>.catalog.gz: gzipped global write-once genome dictionary (`g <id> <count> <genes>`), whole-loaded + native-gunzipped by the viewer; frames carry the id
├── BirthLogWriter            <base>.births.csv.gz: one row per birth from the engine's birth list (lineage/generation/parent exact, genome ids from the shared catalog); DuckDB-queryable lineage store
├── EventLogWriter            events.jsonl (observe): births from the birth list, deaths/extinctions derived from snapshot deltas; mutation bits by child↔parent gene diff (no kernel hook)
├── MetricsWriter             metrics.csv (observe): per-tick aggregates, streamed, O(1)/tick
├── SnapshotDump              one-shot tick-T state page (lattice + per-unit + evolved circuits) for tools/timetravel.py
└── run
    ├── RunDirectoryWriter    run.json + ckpt/<tick>.ckpt, `runId`, `checkpointIfDue`, `createBranch`
    ├── RunDirectoryReader    `seek(dir, T, threads)`: nearest checkpoint, then step forward
    └── LatestFrame           latest-frame-wins publisher: the tick loop publishes a compact frame every N ticks, readers never block it (kept for the web app; nothing in production calls it yet)
```

The tick, in order: `clear → propagate → evaluate → swap → intake → inflow → cost → growth → reproduction → movement → diffusion → decay`. Shapes: `clear`, `propagate`, `evaluate`, `intake`, `cost`, `growth` are per-unit; `inflow`, `diffusion`, `decay` are per-cell; `reproduction` and `movement` are claims; `swap` is serial.

Gene bit layout (32 bits): `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. SrcType 0=sensor 1=internal; DstType 0=internal 1=action. IDs wrap modulo TYPE_COUNT, so **every random int is a legal gene**. Weight: signed int16 × (4.0 / 32767), linear, unclamped.

## Docs — read before changing kernel behavior

Binding (implementations MUST conform). Phase numbers quoted inside older contracts are historical; the current order is in Architecture.

- [docs/contract/contract-v0.md](docs/contract/contract-v0.md) — the closed-system laws: time, energy, decay, dormancy, death, prohibitions
- [docs/contract/contract-v1.md](docs/contract/contract-v1.md) — open-system laws (energy intake); supersedes v0 §3/§10. Implemented (ADR 0010–0013)
- [docs/contract/contract-v2.md](docs/contract/contract-v2.md) — reproduction + mutation + aging laws (REPRODUCE effector, SELF_ENERGY, halt-on-full, abiogenesis, per-slot storage, BUILD_COST, entropic aging + germline renewal). Implemented (ADR 0014–0017); supersedes v1 §11
- [docs/contract/contract-v3.md](docs/contract/contract-v3.md) — space + motility: 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` local sensing, one unit per cell (slot=identity, cell=`position[slot]`), spatial birth (free Moore neighbour), `MOVE_N/S/E/W` motility (phase 8). Implemented (ADR 0018 geometry, 0019 economics, 0020 motility); supersedes v1 §2–§5 (global RESOURCE) and v2 §5 (halt-on-full)
- [docs/contract/contract-v4.md](docs/contract/contract-v4.md) — variable-length genomes (indels): heritable gene *count* via per-gene deletion + per-survivor tandem duplication at birth, soft `MAX_GENES` cap (floor 0 = inert), replication cost ∝ child length (`BUILD_COST_PER_GENE`), monotone operation-counter RNG keying. Implemented (ADR 0026); supersedes v2 §9's fixed-length provision (uses the v2 §12 provisioned storage)
- [docs/contract/contract-v5.md](docs/contract/contract-v5.md) — biomass (Pirt size physics): `mass` as a distinct state variable = crystallized energy (one currency, audit gains a Σmass term), `GROW` effector (energy→mass) + `SELF_MASS` sensor, maintenance ∝ mass (replaces the v1 energy proxy, `STORAGE_LEAK_RATE` retired), harvest ∝ mass^α (asserted surface law), move cost ∝ mass, reproduction = **symmetric binary fission** (split mass+energy 50/50, `REPRODUCE` an ungated trigger — no viability gate, size-control checkpoint emergent), growth = phase 7 (pipeline 9→10), death dissipates mass→sink. Implemented (ADR 0028); supersedes contract-v2 reproduction-investment
- [docs/contract/contract-v6.md](docs/contract/contract-v6.md): patchy + cyclic resource inflow (**opt-in**): a central disk pulses `peak·triangle(tick)` (0→peak→0, period in **ticks**: no wall-clock), **barren periphery**, diffusion spreads it into a breathing radial gradient. Breaks spatial+temporal uniformity → niches (storage/timing/migration) so biomass finally pays. `InflowConfig` (now chemical 0's inflow inside `EngineConfig`), flags `--resource-cycle/--cycle-period/--cycle-radius/--cycle-peak`. Checkpoints carry the config, so cyclic runs resume and seek. Implemented (ADR 0029); supersedes v1 §5 uniform inflow when enabled
- [docs/contract/contract-v7.md](docs/contract/contract-v7.md): programmable inflow **field** (**opt-in**): generalizes v6's single disk into a composable list of `InflowSource`s combined (MAX/ADD) over a baseline: the cap is a pure `f(x,y,tick)`. Sources: `Disk` (STATIC/ORBIT/DRIFT motion + pulse), `Rect` (+ toroidal drift), `Points` (explicit cells), `Noise` (seeded trig-free value-noise thresholded into emergent regions: Minecraft-style worldgen, optionally animated via a tick time-axis), `Ring` (concentric radial traveling wave from a centre: expanding ripples). `InflowConfig` reshaped to `(baseline, sources[], combine)`; `compile(W)` **bakes static sources** once into a `double[cells]` layer (hot path = array read, zero per-tick alloc; `UNIFORM` allocates nothing). DSL flag `--inflow "disk …; rect …; points …; noise …; combine=… baseline=…"` (`--resource-cycle` kept as sugar, mutually exclusive). Closes v6's multiple/moving-patch gaps. Determinism via StrictMath orbit + integer-lattice value noise; conservation unchanged. Implemented (ADR 0034); supersedes v6 inflow when `--inflow` used
- [docs/contract/contract-v8.md](docs/contract/contract-v8.md): **resource decay** (field dissipation, always on): standing resource is no longer permanent: new **decay phase** after diffusion dissipates `field·RESOURCE_DECAY_RATE` (0.02, ~35-tick half-life) of every cell to the sink, and `CELL_INITIAL` 100→**0** (field starts empty). Before v8 the field had no sink (diffusion only redistributes, inflow only tops up), so the world started saturated and a moving source painted permanent resource: the inflow pattern was cosmetic. Now barren cells relax to 0, a moving source leaves a fading trail, `LOCAL_RESOURCE` is a live ramping input even under uniform inflow, and lean patchy worlds can drive cold-start extinction (honest pressure). Conservative (decay→sink, audit holds ~1e-6); deterministic. Implemented (ADR 0036)
- [docs/contract/contract-v9.md](docs/contract/contract-v9.md): **claim phases, seed-keyed streams, thread-count independence** (engine v6). Movement and reproduction are propose-then-resolve: a target or child cell must be free at phase start, the lowest slot (parent) wins, no chains, an unaffordable parent pays nothing and burns no slot, winners take free slots in ascending order, and the tick's births are published as a list. Mutation is keyed `(seed, parentSlot, tick, op)` and point mutation is geometric skip-sampled. All kernel randomness follows the run seed through tagged streams. Strict arithmetic (`StrictMath`, no `Math.pow/exp/log/sin/cos/tan/fma`), phases have shapes, and same version + config + seed gives identical state for any thread count (fixed 4096-entry chunks, ordered partial totals, chunked digest). Supersedes contract-v3 §9 tie and chain rules, contract-v5 §5 placement order, contract-v4 §4 keying, and the locked-pipeline rule. Implemented (ADRs 0038 to 0045)
- [docs/specs/report-v15.md](docs/specs/report-v15.md): engine v6 report: `state-digest` is the chunked digest of the final state (not a per-tick chain), the observer keeps running aggregates over all living units and drops the founder-indexed lines (`## burn-rate`, `## terminal-regimes`, `## action-channel`, ...), `schema: report-v15`, `kernel: v6`. Lists every baseline line that moved and why. Delta over report-v14
- [docs/specs/v0-1/kernel-v0.1-vertical-slice.md](docs/specs/v0-1/kernel-v0.1-vertical-slice.md) — canonical v0.1 reference: substrate, encoding, execution phases
- [docs/specs/v0-1/kernel-v0.1-cache.md](docs/specs/v0-1/kernel-v0.1-cache.md) — what may be precomputed (structure-only) vs never cached (runtime state)
- [docs/nodes/delay.md](docs/nodes/delay.md) — DELAY node semantics
- [docs/specs/report-v6.md](docs/specs/report-v6.md) — run report + per-unit CSV + wiring CSV schema (reproduction section: births, generations, population, lineages); delta over [report-v5.md](docs/specs/report-v5.md)
- [docs/specs/report-v7.md](docs/specs/report-v7.md) — spatial map sidecar `<base>.map.txt` (resource field + per-cell lineage, sampled, streamed) + `tools/mapviz.py` HTML scrub player; delta over report-v6
- [docs/specs/report-v8.md](docs/specs/report-v8.md): observability layer (the manifest, `Replayer` and positional checkpoints are superseded by engine v6 checkpoints, ADR 0043): deterministic-replay time-travel (manifest + checkpoints), decoupled `events.jsonl`/`metrics.csv` sinks (kernel stays pure: no event hook), DuckDB/Parquet query layer, `tools/timetravel.py`. Implemented (ADR 0023); `--observe` off by default, sink-off runs byte-identical; delta over report-v7
- [docs/specs/report-v9.md](docs/specs/report-v9.md) — genome-carrying map frames + circuit inspector: each `<base>.map.txt` `u` line now carries the unit's genome + per-tick node outputs, so the live viewer groups living units by genome, decodes any genome to a **force-directed circuit** (curved sign/weight edges, self-loops, ×N dosage, junk filter), and on hover isolates a node + shows its **actual weights** with per-tick active-signal glow — all client-side, no reconstruction. Kernel stays pure; delta over report-v7
- [docs/specs/report-v10.md](docs/specs/report-v10.md) — surface biomass (mass): new `## mass` report section (totals + alive distribution), `final_mass` column in `.units.csv`, `mass_total` column in `metrics.csv`, a `mass` field on each map-frame `u` line, and a **colour-by-mass** toggle in `tools/mapviz.py` + `live.html` (blue→red, scaled to run max) with mass mean/max readouts. Report `schema:` → report-v10, `kernel:` → v5. Harness + viewers only; kernel stays pure; delta over report-v9
- [docs/specs/report-v11.md](docs/specs/report-v11.md) — compact map frames: the `<base>.map.txt` `u` line was half redundant (genome re-emitted every frame though it only changes at birth) and half over-precise (full-`double` node outputs). report-v11 **dedups genome per slot** (`* <count> <genes>` define vs `^` carry-forward) and **rounds outputs to ≤4 decimals** (`NaN`→null), shrinking the u-portion ~60–80% (survivor 226 MB → ~45 MB). Fields 0–5 keep their position → `tools/mapviz.py` untouched; `LiveServer` carries genomes forward at parse → JSON + `live.html` untouched (and still reads pre-v11 files). Writer keeps an O(slots) last-genome cache (doctrine intact). Composes with the windowed sampler (`--map-from/--map-to`, commit `ac17dc3`). Harness + viewers only; kernel stays pure; text report `schema:` stays report-v10; delta over report-v10 (ADR 0030)
- [docs/specs/report-v13.md](docs/specs/report-v13.md) — lean inspection redesign: the fat `<base>.map.txt` `u` line (genome-per-frame + 24 node-output floats) becomes a **lean** `<base>.frames` row `u <cell> <slot> <mass> <action> <genomeId>`, with genomes deduped globally into a `<base>.catalog` (`GenomeCatalogWriter`) and the resource field RLE'd — full **every-tick** resolution now cheaper than the old sampled file (baseline 3.78 MB sampled → 678 KB frames + 131 KB catalog). Lineage/"what happened to who" moves to an always-on **`<base>.births.csv.gz`** (`BirthLogWriter`, DuckDB-queryable; lineage+generation kernel-exact, parent best-effort). **Retires the CSV zoo** (`UnitCsvReport`/`PopulationReport`/`WiringReport`/`LineageReport` + their tests deleted). Viewers (`live.html` + `mapviz.py`) rewritten: catalog join, colour by genome/mass/action, structural circuit. **On-demand viewing**: byte-offset index sidecars (`.frames.idx`, `.catalog.idx`) let `live.html` HTTP-Range-fetch one frame (and one genome) at a time → O(window) browser RAM on 100s-of-MB runs (measured 7 MB on a 117 MB run). Needs a Range-capable static server — stdlib `http.server` ignores Range, so `scripts/serve.py` (a generic 206-answering subclass). Kernel untouched (digest unchanged); `--observe` sinks stay opt-in; delta over report-v12 (ADR 0032)
- [docs/specs/report-v14.md](docs/specs/report-v14.md) — lean row + compressed containers (audit follow-up, [docs/notes/report-v13-design-audit-findings.md](docs/notes/report-v13-design-audit-findings.md)): the v13 `u` line drops **`mass` + `action`** (the two high-churn fields not needed to seek — owner keeps only map + location + genome/lineage) → `u <cell> <slot> <genomeId>`; the frame stream becomes **binary chunked zstd** (`<base>.frames.zst`, 256-frame chunks, `zstd-jni`) with a per-chunk `.frames.idx`; the catalog is **whole-file gzip** (`<base>.catalog.gz`, `.catalog.idx` dropped). On the cyclic seed-100 run: **128.6 MB → 4.2 MB (~31×)**, every tick preserved; baseline 890 KB → 147 KB. Viewer (`live.html` + `mapviz.py`) decodes chunks with vendored **`fzstd`** (~8 KB, served beside the page); colour-by-mass/action removed. `schema:` → report-v14. Kernel untouched (digest `b3f5198071d3d9e5` unchanged); delta over report-v13 (ADR 0033)
- [docs/specs/report-v12.md](docs/specs/report-v12.md) — one serverless viewer: collapses the two inspectors (bespoke `LiveServer`+`live.html` rich; `tools/mapviz.py` lite offline) into a single rich `live.html` that parses `.map.txt` **client-side**. Two feeds: tail a growing file over plain HTTP **Range** (follow live via any static server, e.g. `python3 -m http.server` — **no bespoke server**) or `window.SIMOLUTION_EMBED` baked in (offline, `file://`). **Deletes** `LiveServer.java` + `--serve` + the `/frames` endpoint; `mapviz.py` becomes a **baker** (inlines frames into `live.html`); `serve-live.sh` → generic static server; `MapFrameWriter.close()` writes an `end` trailer. Also fixes `live.html`'s stale node layout (v3 `TOTAL=22` → v5 `TOTAL=24`, was mislabeling `SELF_MASS`/`GROW`). Kernel untouched; delta over report-v11 (ADR 0031)

Historical / non-binding:

- [docs/specs/v0/kernel-v0-vertical-slice.md](docs/specs/v0/kernel-v0-vertical-slice.md) — superseded by v0.1 slice
- [docs/notes/kernel-v0.1-planning.md](docs/notes/kernel-v0.1-planning.md) — planning conversation; module breakdown rationale

If code and a binding spec disagree, the spec wins. If a change requires the spec to change, update the spec in the same PR and say so.

## Invariants — do not break

- **No semantics in the kernel.** Nodes carry no meaning; the kernel never interprets, rewards, or privileges behavior. If a behavior feels "obvious" while coding, you are probably smuggling semantics.
- **Determinism.** Same engine version + config + seed gives a byte-identical state on any machine and any **thread count**. No wall-clock, no unseeded randomness; every random draw is a pure function of the run seed, a stream tag and ids. Chunk boundaries are fixed (`Chunks.SIZE`) and never depend on threads; totals are merged in ascending chunk index; claim resolves run in ascending slot order. Results may change between engine versions, never within one.
- **Strict arithmetic in `kernel/`.** `StrictMath` or exact operations only; no `Math.pow/exp/log/sin/cos/tan/fma` (`StrictMathLintTest` enforces it) and no floating-point reduction in an order that depends on scheduling.
- **Phases declare a shape and keep inside it.** The tick is an ordered `List<Phase>` built in `Kernel`; adding, splitting, removing or reordering a phase is an edit to that list (and a contract change if it alters the world). Per-unit phases write only their own slot and own cell; per-cell phases write only their own cell's next buffer; claim phases write only intents in `run` and winners in `finish`, in fixed order; scalar totals go to the per-chunk partial arrays and `finish` merges them in chunk order. A phase that breaks its shape breaks thread-count independence.
- **Energy is conserved.** Never created, only moved to the sink; charges clamp to available energy. The `energy-audit-error` line must stay ~0 (FP noise only). Death is `energy ≤ 0`, derived — never store an alive flag (contract §9).
- **No per-tick allocation or decoding.** Genome decoding happens in `SlotCompiler` at start, birth and restore, never per tick. The hot loop touches flat arrays only; births allocate nothing.
- **Memory only via DELAY.** No instantaneous feedback; propagation reads previous-tick outputs only.
- **Data-oriented, not OO organisms.** Flat arrays + indices + phases. No object graphs between nodes.
- **Mutation-safe encoding.** Any 32-bit int must remain decodable. Never add validation that can reject a gene outright (structural no-ops are fine).

## Performance and memory

The engine is built for a multi-core research machine (a Hetzner CX33 is the deployment target), not for a game loop or a microcontroller (ADR 0045). Speed is measured, not assumed: `./gradlew bench` and [docs/notes/engine-v6-bench.md](docs/notes/engine-v6-bench.md) hold the numbers and the stopping-rule check.

- Primitives and flat arrays in runtime state. No boxing, collections, streams or lambdas in the hot path; no object per organism.
- Zero allocation and zero decoding inside `tick()`, births included. Anything structure-derived is computed once.
- The signal arrays (outputs ×2, accumulators, delayMemory) are `float`; the energy economy stays `double` so the audit is exact (ADR 0037).
- **Memory discipline (core value).** Total footprint is linear in units and cells and CONSTANT in tick count. Nothing accumulates per-tick history: observers keep running aggregates, never trajectories. If a feature seems to need history, it needs a bounded ring buffer. Observers preallocate in their constructors.
- Footprint growth (a new per-unit array, a wider connection record) needs a sentence in the design note or an ADR, not a per-array justification.
- `Kernel.snapshot()` allocates one small view object per call; the `observe()`-style per-tick methods allocate nothing. Boxing and string building stay out of per-tick paths (trace logging is exempt: opt-in, small runs only).
- Known serial costs (resolve passes of the claim phases, `snapshot()` sums) bound the thread speed-up; the bench note has the profile.

## Decision records

Every non-obvious decision gets a short ADR in `docs/decisions/NNNN-slug.md` — 5–15 lines: context, decision, why, what was rejected. The owner reads ADRs to stay in full command of the project; write them for him, not for posterity. No decision is too small if a future reader might ask "why is it like this?"

## Working mode

The owner wants autonomous implementation but full understanding. So: small single-purpose commits, an ADR per real decision, and every delivered change explained in plain language (what changed, why, what was traded away). Never bundle an unexplained judgment call into a big diff.

## Conventions

- Conventional Commits (`feat:`, `refactor(kernel):`, ...).
- Comments only for an invariant or numeric trick a name cannot carry (examples: MUL in-degree rule, saturating-drive overflow rearrangement, mutation operation-counter determinism, Noise hashing, barrier handshake, signed-zero junk pruning). Everything else is a name or lives in docs/. No history in code. Tests: only `// arrange`, `// act`, `// assert`.
- Tests mirror main package layout and build worlds, genes, audits and frame output through the shared helpers in `src/test/java/com/simolution/support` (`Worlds`, `Genes`, `EnergyAudit`, `Ticks`, `FrameHarness`, `CheckpointBytes`). Spec-conformance tests compare kernel output against an independent reference model that stays out of those helpers (see KernelVerticalSliceTest, GeneDecoderTest).
- No known deviations from binding specs. MUL semantics (in-degree 0 → 0, 1 → pass-through, ≥2 → product of two strongest) per ADR 0005.

## Branching

Solo repo, published **public** at [github.com/MehmetSecgin/Simolution](https://github.com/MehmetSecgin/Simolution) (`origin`). No `main` trunk and no PR flow — one long-lived branch **per milestone**, named after it (`kernel-v0.1`, `kernel-v1-energy-intake`, …); all milestone branches are pushed and the latest (`kernel-v5-biomass`) is the GitHub default. Start each new milestone branch off the previous milestone's tip; leave the old branch frozen as a marker of that finished slice. The no-`main` convention stands — do not create `main` or open PRs; just push the milestone branch.

## Roadmap (from specs, in order)

1. ~~Multi-unit evaluation~~ done (ADR 0002, 0003)
2. ~~True MUL semantics~~ done (ADR 0005)
3. ~~Energy accounting: structural decay + activity cost~~ done (ADR 0006)
4. ~~Energy intake~~ done (ADR 0009 design, ADR 0010 implementation): RESOURCE depletable global pool + HARVEST action, intake before cost, emergent acuity, open-system audit (reservoir + units + sink + inflow)
5. ~~Reproduction + mutation (evolution proper)~~ done (ADR 0014 design, 0015 implementation, 0016 BUILD_COST anti-degeneracy): REPRODUCE effector + SELF_ENERGY sensor, per-slot rewritable store, phase-7 settle-reproduction, point mutation, halt-on-full, fixed per-birth build cost. Variable-length genomes (indels) followed (item 10, done); biomass as a distinct variable (time/size physics) is next within evolution
6. ~~Space (basic)~~ done (ADR 0018 geometry, 0019 economics): 2D toroidal lattice, per-cell resource field + diffusion, `LOCAL_RESOURCE` replaces global RESOURCE, one-pixel-one-unit, spatial birth into a free Moore neighbour, halt-on-full retired. Sessile. Yields coexisting lineages (vs the old monoculture).
7. ~~Motility~~ done (ADR 0020): `MOVE_N/S/E/W` effectors (Fork B), one Moore step/tick, `MOVE_COST`, deadzone, blocked-if-occupied; position decoupled from slot (no per-move recompile/alloc); chemotaxis emergent via LOCAL_RESOURCE + DELAY.
8. ~~Spatial map + scrub viewer + live viewer~~ done (report-v7): `MapFrameWriter` streams a sampled `<base>.map.txt` (resource field + per-cell lineage, O(1) RAM); `tools/mapviz.py` renders a self-contained HTML scrub player; `--serve PORT` started an in-JVM `LiveServer` (`com.sun.net.httpserver`, no deps) that tailed the file so a run could be watched live (`src/main/resources/live.html`, follow-live + scrub). **report-v12 (ADR 0031) removed the bespoke `LiveServer` + `--serve`**: the viewer now parses `.map.txt` client-side and tails it over a generic static server (or runs fully offline from baked frames).
9. ~~Observability layer~~ done (report-v8, ADR 0023): deterministic-replay time-travel (manifest + checkpoints + `Replayer`; replaced in engine v6 by `RunDirectoryWriter`/`RunDirectoryReader` and `CheckpointCodec`, ADR 0043), decoupled `EventLogWriter`/`MetricsWriter` sinks (kernel stays pure: no event hook; events + mutation bits derived from snapshots/gene-diff), DuckDB/Parquet query layer, `tools/timetravel.py` time-travel page. `--observe` off by default; sink-off runs byte-identical.
10. ~~Variable-length genomes (indels)~~ done (contract-v4, ADR 0026): heritable gene *count* via per-gene deletion + per-survivor tandem duplication at birth (operation-counter RNG keying), soft `MAX_GENES` cap (floor 0 = inert), replication cost ∝ child length (`BUILD_COST_PER_GENE`, build-time anti-bloat — no per-tick per-gene tax). Gene dosage emergent (duplicate a transporter → more harvest capacity).
11. ~~Biomass (Pirt size physics)~~ done (contract-v5, ADR 0028): `mass` as a distinct crystallized-energy variable, `GROW` effector + `SELF_MASS` sensor, maintenance ∝ mass (replaces the energy proxy), harvest ∝ mass^α surface law, move cost ∝ mass, **symmetric binary fission** (split mass+energy 50/50), ungated `REPRODUCE` trigger (size-control checkpoint emergent — no `M_DIV_MIN`), growth phase 7, death dissipates mass→sink.
12. ~~report-v10 (surface mass)~~ done: `## mass` report section, `final_mass` (units.csv) + `mass_total` (metrics.csv) columns, `mass` on map-frame `u` lines, colour-by-mass in `tools/mapviz.py` + `live.html`.
13. ~~Fluctuating/patchy inflow~~ done (contract-v6 ADR 0029 + contract-v7 ADR 0034): v6 opt-in central pulsing disk (`--resource-cycle`); **v7 generalized to a programmable inflow field**: composable `InflowSource`s (disk w/ ORBIT/DRIFT motion, rect, explicit points, seeded value-noise worldgen) combined MAX/ADD over a baseline, `--inflow "<spec>"` DSL, static-source bake. Closes multiple/moving patches. Remaining: seed-from-evolved-founder, fractal/Perlin noise octaves.
14. ~~Engine v6~~ done (contract-v9, ADRs 0038 to 0045, report-v15): state declared once (`WorldState`/`FieldSpec`), node layout generated from `NodeCatalog` and exported as `.layout.json`, flat connection arrays with junk pruning, the tick as a declared list of shaped phases run by a fixed-chunk `ParallelRunner`, claim-based movement and reproduction, parent-keyed skip-sampled mutation, all randomness keyed by the run seed, chemical table, per-run `EngineConfig` from JSON, self-sufficient versioned checkpoints with seek and branch, chunked state digest. Benchmarks and the stopping-rule result (rule 2, 4-thread scaling on big500, not met) are in [docs/notes/engine-v6-bench.md](docs/notes/engine-v6-bench.md); the CX33 run is pending.
15. Later: sensors beyond the own cell (gradients), several resources with separate harvesting, secretions and quorum (CROWDING/EMIT), corpse release and decomposers, sub-gene/selfish-DNA/whole-genome-duplication indel variants, multi-cell bodies (contract-v5 omissions), the web app (design §9).
