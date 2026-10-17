# Engine v6: fast, parallel, extensible, controllable

Status: draft for owner review, 2026-10-17. Branch `kernel-v6-engine`.

Markers used below: **[owner]** = decided by the owner in conversation. **[call]** = the designer's call, open to change. Unmarked text describes the current code.

## 1. Intent

Simolution is the owner's long-running artificial-life experiment and, now, a portfolio piece **[owner]**: the goal is to show researchers at an evolution-focused lab that the author is a professional software engineer who understands the basics of evolution. It is also, first, a thing the owner loves to play with.

So the audience shifts from "a kernel small enough to embed in a game loop" to "a research-grade simulation a scientist can rerun and trust" **[owner]**. The microcontroller / game-loop budget is retired **[owner]**.

The next features the owner wants **[owner]**: sensors that read beyond the unit's own cell (gradients), several resource types with separate harvesting (later predators and prey), several secretions / pheromones for communication, and corpses that release what the body held so decomposers and scavengers can evolve. Flagella / motion physics is a distant idea, not planned **[owner]**.

This milestone does **not** add those features. It rebuilds the engine so they are cheap and honest to add, makes it fast and multithreaded, closes determinism gaps, and builds the run-control layer a web app will drive.

## 2. Goals, non-goals, anti-goals

Goals:

1. **Determinism contract** **[owner]**: same engine version + same config + same seed gives a byte-identical world on any machine and any thread count. Results may change between engine versions.
2. **Performance** **[owner]**: as fast as reasonably possible on a multi-core machine, including multithreading. Measured, with numbers a reviewer can reproduce.
3. **Extensibility** **[owner]**: adding a sensor, action, chemical, per-unit variable or phase touches one declaration, not a dozen hand-synced places. Phases can be added, split and removed **[owner]**; the "locked pipeline, append-only" rule is retired.
4. **Run control** **[owner]**: the engine supports start, pause, checkpoint, resume, seek to tick T, restart, and branch, so a web app on the owner's VPS can drive it (section 9).
5. **Port today's behaviour** **[call]**: the v6 engine runs today's biology (contract v0 to v8) with only the deliberate semantic changes listed in section 6. One explained baseline change.

Non-goals (this milestone):

- New biology: neighbour sensors, multiple chemicals in use, secretion, corpses, predation. The engine must make them easy; they ship later, each with its own contract.
- The web app itself (separate project, requirements in section 9).
- GPU, SIMD via the Vector API, off-lattice space, motion physics.
- Multi-user anything. The site is for the owner only **[owner]**.

Anti-goals (would make this a failure even if it technically works):

- Any result that depends on thread count, scheduling, or CPU model.
- Semantics smuggled into the kernel: computed gradients, special "death smell", fitness, outcome tuning. The honesty doctrine (contract v1 §2, ADR 0027) stands.
- An object-per-organism design that kills cache locality.
- A refactor that silently changes behaviour beyond the listed semantic changes.

## 3. Constraints

- Java 26, Gradle 9.8.1 wrapper, toolchain 26 **[call: keep the stack]**. A rewrite buys nothing a reviewer values more than clean Java with numbers.
- Strict IEEE-754 everywhere in the kernel. No `Math.*` transcendental whose result may differ by CPU or JIT tier (`Math.pow`, `exp`, `log`, `sin`, ...): use `StrictMath` or exact operations. No `Math.fma`. No unordered reductions (parallel streams with floating-point reduce, concurrent accumulators).
- Memory: linear in units and cells, constant in tick count (no per-tick history). The per-ADR footprint justification rule is retired; footprint growth needs only a note in the design or ADR.
- No allocation in the steady-state tick. Births may allocate nothing either (flat connection storage, section 5.4).
- Honesty doctrine unchanged: sensors are raw local taps, actions are raw physical transactions, no node computes what the unit could evolve.

## 4. Where we start (facts)

Profile, 200×200 world, 4000 founders, 3000 ticks, 43 s wall, peak population 29k (JFR, JDK 26):

| Cost | Share |
|---|---|
| `DynamicsObserver.foldDigest`: serial hash chain over ~2.1M values per tick | 58% |
| `Kernel.mutate`: 32 three-mix hashes per gene per birth | 16% |
| `DynamicsObserver.observe`: per-tick recomputation of last-tick-only values | 8% |
| `propagateConnections` | 6% |
| `GeneDecoder.resolveLocalIndex` (recompile at birth) | 5% |
| all other kernel phases | ~7% |

Known defects and gaps:

1. `KernelConfig.RANDOM_SEED = 0L` drives RAND, mutation and founder placement; `--seed` only reaches `GenomeFactory` and noise-inflow. Two seeds share all kernel randomness.
2. `Math.pow(mass, 0.5)` in harvest (`Kernel.java:532`) is not guaranteed bit-identical across CPUs.
3. `Main.java:84` tests `mapFrames() >= 0` with default 0, so with `--out` the zstd-19 frame writer always runs every tick.
4. Checkpoints omit the inflow config (replay of inflow runs is refused) and carry no format version. `saveState` is positional.
5. `connections` is a `CompiledConnection[]` of heap objects (pointer chase per gene, allocation per birth). About a third of random genes read from junk sources and always propagate 0.
6. Node layout is hand-mirrored in `NodeLayout`, `evaluateUnit` (one line per node), `localName`, `live.html`, `visualize.py`, `journey.py`. Per-unit state is hand-threaded through constructor, `finishChild`, `die`, `saveState`, `loadState`, the 23-argument `KernelSnapshot`.
7. Reproduction (phase 8) and movement (phase 9) are order-dependent by ascending slot: earlier units win contested cells, a cell vacated this tick can be entered this tick, child slots come from a monotone cursor, and the child slot keys the mutation RNG. All energy dissipation goes through one scalar `energySink`.
8. Harness dead weight: lineage aggregates and several `DynamicsSummary` arrays have no consumer; `EventLogWriter` and `BirthLogWriter` duplicate the birth-detection scan; manifest says kernel `v4` while the report says `v5`.

## 5. Engine design

### 5.1 State is a set of declared arrays **[call]**

Keep flat primitive arrays (structure of arrays). Add a small declaration layer so each array is declared once with its name, element type, dimension and lifecycle; everything that today hand-threads state is derived from the declarations.

Dimensions:

- **unit**: indexed by slot, length `S` (slot pool).
- **cell**: indexed by cell, length `C = W²`.
- **unit × chemical** and **cell × chemical**: `K` chemicals, laid out chemical-major (`[k * C + cell]`) so a per-chemical sweep is contiguous **[call]**.
- **unit × node**: the signal arrays (outputs ×2, accumulators, delay memory), `S × N` floats.
- **gene**: flat genome and compiled connection storage, `S × maxGenes`.

Each declared field states: initial value for founders, value for a newborn (copy from parent, split, reset, or explicit), what death does to it, whether it is saved in checkpoints, and whether it is visible in snapshots.

Derived from the declarations: checkpoint save/load (with a format header), snapshot views, founder init, child install, death cleanup, the state digest, and the schema the web app and Python tools read.

Rejected: an `Organism` object graph (cache-hostile, kills the parallel model); a generic ECS framework dependency (too much machinery for one simulation).

### 5.2 Node layout is generated **[call]**

Sensors, internals and actions are declared as data: name, kind, and (for sensors) what it taps, (for actions) which phase consumes it. Junk padding counts stay explicit config. `NodeLayout` offsets, `TOTAL`, `TYPE_COUNT`, name lookup and the sensor-write loop in evaluate are generated from that list. The engine exports the layout (names, offsets, junk ranges, gene bit layout) as JSON alongside every run, and `live.html` and the Python tools read it instead of hardcoding names.

Parametric families must be expressible, because the next features need them: e.g. "raw tap of chemical k in direction d" for `k ∈ chemicals`, `d ∈ {here, N, S, E, W}`; "uptake of chemical k"; "secrete chemical k". This milestone declares only today's 24 nodes.

The gene encoding (32 bits, 7-bit ids, modulo wrap, every int decodes) is unchanged.

### 5.3 Phases are declared steps with a shape **[call]**

`tick()` becomes an ordered list of phase objects built at engine construction from config. Adding, splitting, removing or reordering a phase is a change to that list. Every phase declares one shape, and the shape fixes what it may read and write:

| Shape | May read | May write | Parallel rule |
|---|---|---|---|
| **per-unit** | anything not written in this phase | only its own slot's fields, its own cell's cell fields | split slots into fixed chunks |
| **per-cell** | previous buffer of any cell field | only the next buffer of its own cell | split cells into fixed chunks (rows) |
| **claim** | anything not written in this phase | intents into a per-unit scratch, then a deterministic resolve writes winners | propose in chunks, resolve in fixed order (5.6) |
| **reduction** | per-chunk partials | one scalar | partials merged in chunk index order |

Dissipation to the sink becomes a reduction: each chunk accumulates its own partial `sink`, `inflow` and similar totals; after the phase, partials are summed in ascending chunk index.

Today's 11 phases map to: clear, propagate, evaluate, intake, cost, growth, diffusion, decay are per-unit or per-cell; swap is a pointer flip; reproduction and movement become claims. Fusing phases (e.g. clearing accumulators inside evaluate) is allowed when it keeps the same result.

### 5.4 Flat connection storage **[call]**

Replace `CompiledConnection[]` with parallel primitive arrays per slot region: source local index, destination local index (byte or short), weight (float). Indices become local to the unit's node block (connections never cross units). Connections whose source is a junk node are dropped at compile time (signal is always 0) but still counted where today's rules count them (MUL in-degree; activity cost counts non-zero signals only, so dropping zero-signal connections changes nothing). Compile at birth writes into the slot's region in place: zero allocation per birth.

`GeneDecoder.isValid` (always true) and the dead `null` branches go. `GenomeCompiler` (harness/test only) is replaced by the same compile routine the engine uses.

### 5.5 Chemicals as the general field **[call, framed by owner's direction]**

The single resource field becomes chemical 0 of a chemical table. Each chemical declares: inflow source (today's `InflowConfig` field, per chemical), cell capacity, diffusion rate, decay rate, and decay target (the sink, or another chemical: rotting) **[call]**. Diffusion and decay become per-cell phases that loop over chemicals. Per-unit chemical stores (`unit × chemical`) are declared but unused until a feature needs them.

This milestone configures exactly one chemical with today's constants, so behaviour matches v5 apart from section 6. The point is that "add a chemical" becomes config, and secretion, multi-resource harvest and corpse release (death moves body stores into the cell) become small transfer phases later.

Energy and mass stay as dedicated unit fields this milestone **[call]**. Whether they become chemicals is a later contract question.

### 5.6 Claim phases: movement and reproduction (semantic change)

Both become propose-then-resolve, independent of thread count:

**Movement.** Each living unit computes its intended target from its MOVE outputs, exactly as today (deadzone, one Moore step, afford check). A target is valid only if it is free **at the start of the phase**. Among units targeting the same free cell, the lowest slot wins **[call: same tie rule as today]**. Losers stay put and pay nothing. Chains are gone: a cell vacated this tick cannot be entered this tick.

**Reproduction.** Each candidate parent (wants to divide, can afford at least an empty child) computes, in parallel: its child genome into a per-parent scratch (copy + mutate + indel, keyed by the parent, below), the child's build cost, and its preferred child cell (lowest free Moore neighbour at phase start). Parents that cannot afford their child's build cost drop out before any claim, paying nothing and consuming no slot (today a failed build burns a slot). Contested child cells go to the lowest parent slot; a loser retries nothing this tick. Winners are sorted by parent slot and assigned free slots in ascending slot order (a prefix scan over free slots, deterministic). Then each winner installs its child: split energy and mass, compile the child's connections, set lineage and generation.

**Mutation key** changes from `(seed, childSlot, tick, op)` to `(seed, parentSlot, tick, op)`. A parent divides at most once per tick, so keys stay unique, and the genome can be computed before the child's slot is known.

These rules are a contract change (supersedes contract v3 §9 tie-breaking/chaining and contract v5 §5 placement order). It gets a new contract (`contract-v9`) and an ADR.

### 5.7 Randomness

- All kernel randomness keys off the run seed: RAND sensor, mutation, founder placement, noise inflow. `RANDOM_SEED` stops being a constant **[call; it is a bug fix]**.
- Keys stay counter-based and pure: `(seed, stream, ids...)`. Each stream gets an explicit domain tag so streams can never collide as more are added (today two streams separate by mix structure, one by an XOR salt).
- Loop-invariant hash steps are hoisted (the first two mixes of a mutation key are constant per birth).
- **[call]** Point mutation may switch from "one draw per bit" to geometric skip sampling (draw the distance to the next flipped bit). At today's rates (~1e-5 per bit) this removes almost all draws. It changes which bits flip for a given seed, so it lands together with the claim change, inside the one baseline change.

### 5.8 Threading

- A fixed-size worker pool owned by the engine; the thread count is a run setting **[call]**. Thread count never affects results.
- Chunk boundaries are a function of array length and a **constant chunk size** baked into the engine version, never of the thread count **[call]**. Work scheduling may be dynamic (work stealing is fine); only chunk boundaries and merge order must be fixed.
- One barrier between phases. Small worlds may run phases single-threaded when the work is below a threshold; results are identical either way.
- ADR 0025 rejected work stealing outright; that reasoning was about reduction order, which the fixed-chunk rule makes irrelevant.

## 6. Deliberate semantic changes (the one baseline change)

1. Movement: targets must be free at phase start; no same-tick chains (5.6).
2. Reproduction: claim-based placement, no slot burned by an unaffordable build, slots assigned by ascending parent slot (5.6).
3. Mutation keyed by parent slot; possibly geometric skip sampling (5.6, 5.7).
4. Kernel randomness keyed by the run seed (5.7).
5. `Math.pow(m, 0.5)` becomes `Math.sqrt(m)`. Bit-exact for the current exponent; a general exponent would use `StrictMath.pow`.
6. Totals merged per chunk in chunk order. This changes the printed `energy-sink`, `cumulative-inflow`, `intake-total`, `final-reservoir` and `energy-audit-error` values (full double precision in the report) and therefore the digest, not just the audit's low bits.
7. Report schema bump to report-v15 (section 7).

Everything else must reproduce the v5 kernel exactly.

Order of work **[call]**: the pure refactor stage stays **serial** and byte-identical to `runs/baseline.txt`: declaration layer, generated layout, phase list, flat connections, junk pruning, mutation hoisting, complete checkpoints, per-run config, harness speedups that do not change printed values. The parallel executor, chunked reductions, and every change in this section land together in one semantic stage and regenerate the baseline once, with the diff explained. No compatibility path for the old serial digest is kept after that stage.

## 7. Harness and observability

- **State digest**: replace the serial chain with a per-chunk hash combined in chunk order, so it runs in parallel; compute it at checkpoints and at the end of the run, not every tick **[call]**. A per-tick digest stays available as a debug/test option (used by the determinism tests).
- **DynamicsObserver**: running aggregates stay per tick; last-tick-only values are computed once at the end. Delete the lineage aggregates and unused `DynamicsSummary` arrays.
- **Founder-indexed report sections** (`## dynamics`, `## terminal-regimes`, `## burn-rate`, `## action-channel`) depend on a founder-only double-precision re-propagation and read the wrong wiring once a founder slot is reused by a child. In the refactor stage they stay byte-identical. In the semantic stage they are redefined over all living units from values the engine already computes (e.g. per-unit activity counts, action outputs); anything that cannot be computed without re-propagating (clamp saturation, thresh flips) is dropped. The schema becomes report-v15 **[call]**.
- **Frame writer**: fix the `>= 0` flag; frames become opt-in as documented.
- **Birth/event logs**: merge the duplicated birth-detection scan into one tracker. Better: the engine exposes per-tick birth and death lists from the claim resolve (it knows exactly who was born where and from which parent), so writers stop inferring parents from geometry **[call]**. This is the one place the kernel publishes events; it is a plain array of this tick's births, not a callback, and observers stay read-only.
- Fix version strings: one engine version constant feeds the report, manifest and checkpoints.

## 8. Run lifecycle in the engine

The web app (section 9) needs these from the engine; they belong in this milestone.

A **run** is: engine version, full config (world, chemicals and inflow, node layout, constants, thread count is NOT part of it), seed, founders. Its identity is a hash of everything except thread count.

**Per-run config** **[call]**: the `static final` constants in `KernelConfig` and the post-construction `configureInflow` become one immutable config object per engine instance, loaded from a human-readable file (JSON) and stored verbatim in every checkpoint. Example experiments are config files. Two engines with different configs can run in one JVM. Hot loops read config values into final fields or locals; the benchmark measures any cost of losing compile-time constants. A **branch** may change rates, costs, thresholds and inflow parameters; it may not change anything that shapes state (world size, chemical count, node layout, `maxGenes`), which would need a new run.

**Existing observability stack** **[call]**: `RunManifest`, `CheckpointWriter`, `Replayer` and the `.obs/` layout are replaced by the run lifecycle below; the CLI (`--out`, `--replay`, ...) stays as a thin front over the same API. Runs written by v5 are not readable by v6 (the version check refuses them). `births.csv.gz` takes exact parents from the engine's birth list in the semantic stage. `live.html`, `mapviz.py` and `timetravel.py` follow the new frame format and exported layout; they need only read v6 runs, and the web-app project may replace them.

Engine API (shape, not names) **[call]**:

- `create(config, seed)`; `step(n)`; `tick()` count; `pause` is simply not stepping.
- `checkpoint()` writes a complete, versioned file: header (magic, format version, engine version, config hash), full config, all declared state fields. Checkpoints are self-sufficient: no side files needed to resume. This closes the inflow-replay gap.
- `restore(checkpoint)` refuses a checkpoint whose engine version differs from the running engine.
- `seek(T)`: nearest checkpoint at or before T, then step forward.
- **branch**: restore a checkpoint, apply a config change, continue as a new run with a parent pointer (run id + tick).
- A frame sink: the run can publish a compact frame (cells, occupancy, chemical fields, genome ids) every N ticks to any number of listeners without blocking the tick loop (latest-frame-wins buffer) **[call]**.

Guarantee, enforced by tests: running `0 → T` uninterrupted, or `0 → t`, checkpoint, restore, `t → T`, gives byte-identical state at T, for any thread counts on either side.

## 9. Web app requirements (next project, not this milestone)

- Runs on the owner's VPS; the owner opens it from a laptop by domain or bare IP **[owner]**.
- Single user **[owner]**: one password, HTTPS via a reverse proxy (e.g. Caddy) **[call]**. Without auth, anyone finding the IP can start runs and burn CPU.
- Pages: pick or edit an example config, start a run, watch it live, pause/stop, list past runs, open a past run at any tick, continue, restart (same seed or new), branch from a tick with a changed setting **[owner, branch is a call]**.
- Server owns a run manager: several runs at once under a total thread budget (setting) **[call]**. On the CX33 (4 shared vCPUs, 8 GB) the default budget is 3 worker threads for runs, leaving one for the web server and I/O.
- Old runs from an older engine version: always viewable from recorded frames; continue is disabled with an explanation; restart on the current engine is offered **[call]**.
- Storage: runs directory with config, checkpoints, frames, birth log; retention/cleanup controls **[call]**.

## 10. Testing and proof

- **Determinism matrix** (new, core): the same config and seed run with 1, 2, 4 and 8 threads produce identical per-tick digests and identical final state.
- **Resume equivalence**: uninterrupted vs checkpoint/restore at several ticks, across differing thread counts.
- **Seek equivalence**: today's `ObservabilityTest` replay check, kept.
- **Seed sensitivity**: different seeds differ in RAND, mutation and placement (catches the `RANDOM_SEED` bug class).
- **Strict-math lint**: a test or build check that fails on `Math.pow/exp/log/sin/cos/tan/fma` in kernel sources.
- **Behavioural tests** (Energy, Biomass, Indel, MulNode, Movement, MultiUnit, VerticalSlice): ported. Exact-value pins that encode order-dependent movement/reproduction are updated deliberately with the semantic change and their new values explained.
- **Baseline workflow**: every pure-refactor step keeps `runs/baseline.txt` byte-identical, digest included (the refactor stage is serial, so this is achievable). The semantic stage regenerates it once.
- **Benchmark harness** **[call]**: a reproducible bench command (fixed configs: small sparse, 200×200 dense, 500×500) reporting ticks/s per thread count and ms per phase. Before/after table goes in the README.
- **Target hardware** **[owner]**: the run host is a Hetzner CX33, 4 shared vCPUs (AMD EPYC or Intel) and 8 GB RAM. Shared vCPUs make timings noisy, so the README numbers come from the dev machine, with a CX33 sanity run alongside.
- **Stopping rule** **[call]**: optimization is done when all three hold. (1) On the 200×200 dense config, single-threaded v6 is at least 5× faster than v5 end to end (harness included, no `--out`). (2) On 500×500, 4 threads give at least 2.5× over 1 thread. (3) A profile shows no avoidable hotspot above 20% of tick time and the harness below 10%. A 1000×1000 world must fit in 4 GB of heap.

## 11. Documentation changes

- `AGENTS.md`: retire the microcontroller budget and the locked-pipeline rule; describe phase shapes, the declaration layer, the determinism contract (any thread count), and the new commands.
- New `contract-v9` for the semantic changes in section 6.
- ADRs for: declaration layer, phase shapes and parallel executor, claim-based movement/reproduction, parent-keyed mutation and skip sampling, chemical table, run lifecycle and checkpoint format, digest change.
- Fix stale comments found in review (phase numbers in `Kernel` and `EventLogWriter`, the merged diffusion/decay javadoc, "slot index == cell index", "∝ connection count").
- README: new architecture section and the benchmark table.

## 12. Left to the builder

- Class and package structure, names, the exact declaration API.
- Chunk size value, small-world single-thread threshold, pool implementation.
- Whether geometric skip sampling lands this milestone (it is allowed, not required).
- Checkpoint byte format details beyond the required header and self-sufficiency.
- How the pure-refactor steps prove byte-identity before the semantic change.
- Order of work, as long as pure refactors precede the single semantic change.
