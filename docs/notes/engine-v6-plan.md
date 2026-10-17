# Engine v6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Simolution kernel into a fast, multithreaded, deterministic, declaratively extensible engine with a checkpoint/resume/branch run lifecycle, porting today's biology with one deliberate baseline change.

**Architecture:** Stage A refactors serially and must keep `runs/baseline.txt` byte-identical after every task: per-run config, generated node layout, declared state arrays, flat connection storage, phase objects, chemical table, versioned checkpoints and run lifecycle. Stage B then lands the parallel executor and every semantic change (claim phases, parent-keyed mutation, seed plumbing, `sqrt`, chunked totals, new digest, report-v15) and regenerates the baseline once at the end.

**Tech Stack:** Java 26, Gradle 9.8.1, JUnit 5.11.4, zstd-jni (existing), Jackson databind 2.18.2 (new, config JSON).

**Spec:** `docs/notes/engine-v6-design.md`

## Global Constraints

- Determinism: same engine version + same config + same seed gives byte-identical state on any machine and any thread count.
- Stage A: after every task, `runs/baseline.txt` regenerated with `--units 100 --ticks 1000 --seed 42 --world 100` is byte-identical, `state-digest: e2709232cf9636b6` included.
- Kernel sources: no `Math.pow/exp/log/sin/cos/tan/fma`; use `StrictMath` or exact operations. No floating-point reductions in unordered or thread-count-dependent order.
- No allocation in the steady-state tick (births included once Task A6 lands).
- Memory linear in units and cells, constant in tick count.
- Honesty doctrine: sensors are raw local taps, actions are raw physical transactions; no computed gradients, no fitness, no outcome tuning.
- Gene encoding unchanged: 32 bits `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`, ids modulo `TYPE_COUNT`, every int decodes.
- Javadoc welcome on non-obvious code (project rule); Conventional Commits; small single-purpose commits; stage explicit paths.
- Stage A baseline check (every Stage A task's last verification), referred to below as **BASELINE-CHECK**:

```bash
./gradlew run -q --args="--units 100 --ticks 1000 --seed 42 --world 100 --out build/baseline-check/baseline.txt" && diff runs/baseline.txt build/baseline-check/baseline.txt && echo IDENTICAL
```

Expected: `IDENTICAL`.

## Review Focus

1. A checkpoint written at 2 threads, restored at 4 threads, must continue byte-identically to an uninterrupted 1-thread run (test in B1).
2. A truncated, corrupted, or other-engine-version checkpoint must be refused with a message naming the problem, never loaded into garbage state (test in A8).
3. A branch that changes world size, chemical count, node layout or `maxGenes` must be refused; a branch changing a rate must succeed (test in A8).
4. Tiny and extreme worlds (W=1, world smaller than one chunk, total extinction mid-run) must run without exceptions and stay thread-count-independent (test in B1).
5. A config file with an unknown key or a missing required key must fail with the key's name, not a default silently applied (test in A3).

---

## Stage A: serial, byte-identical refactor

### Task A1: Benchmark command and v5 numbers

**Files:**
- Create: `src/main/java/com/simolution/bench/Bench.java`
- Modify: `build.gradle` (add `bench` JavaExec task)
- Create: `docs/notes/engine-v6-bench.md`

**Interfaces:**
- Produces: `./gradlew bench --args="--threads 1"` prints one table row per config: `config | ticks | wall ms | ticks/s | heap MB`. Configs: `small` (W=64, 200 founders, 2000 ticks), `dense200` (W=200, 4000 founders, 3000 ticks), `big500` (W=500, 20000 founders, 1000 ticks). Seeds fixed (42). No `--out`; the harness observer runs as in a normal run (end-to-end). Each config runs once for JIT warm-up, then 3 timed runs; report the median.

- [ ] **Step 1: Implement `Bench.main(String[] args)`** building `RunConfig`s for the three configs and running the same loop as `Main` (factor the per-run loop out of `Main` into `static RunResult runOnce(RunConfig)` in `Main` if needed; `Main.main` behaviour unchanged). Heap MB = `Runtime.totalMemory() - freeMemory()` after the run.
- [ ] **Step 2: Add Gradle task** `tasks.register('bench', JavaExec)` with `mainClass = 'com.simolution.bench.Bench'`, `classpath = sourceSets.main.runtimeClasspath`, `jvmArgs '-Xmx4g'`.
- [ ] **Step 3: Run** `./gradlew bench`. Expected: three rows, no exceptions.
- [ ] **Step 4: Record** the machine (CPU, cores, JDK), the three rows, and the per-phase note "v5, harness included" in `docs/notes/engine-v6-bench.md` under `## v5 (before)`.
- [ ] **Step 5: BASELINE-CHECK, then** `./gradlew test`. Expected: IDENTICAL; tests pass.
- [ ] **Step 6: Commit** `build.gradle`, `Bench.java`, `Main.java` (if touched), `engine-v6-bench.md`: `perf(bench): reproducible benchmark command + v5 numbers`.

### Task A2: Harness cleanups that do not change the report

**Files:**
- Modify: `src/main/java/com/simolution/Main.java:84`, `src/main/java/com/simolution/sim/RunConfig.java`
- Modify: `src/main/java/com/simolution/sim/DynamicsObserver.java`, `DynamicsSummary.java`
- Delete: `src/main/java/com/simolution/sim/LineageSummary.java`
- Create: `src/main/java/com/simolution/sim/BirthTracker.java`
- Modify: `BirthLogWriter.java`, `EventLogWriter.java`
- Test: `src/test/java/com/simolution/sim/RunConfigTest.java`, `DynamicsObserverTest.java`, `BirthLogWriterTest.java`, `ObservabilityTest.java`

**Interfaces:**
- Produces: `RunConfig.mapFrames()` default `-1` (off); `--map-frames 0` = every tick; frame writer runs only when `mapFrames() >= 0 || mapFrom() >= 0`.
- Produces: `final class BirthTracker { BirthTracker(int maxUnits, int founderCount, int worldWidth, int[] founderCells); int observe(KernelSnapshot s); int birthCount(); int birthChild(int i); int birthParent(int i); }` holding the previous-tick buffers and `findParent` logic now duplicated in both writers. Both writers take a `BirthTracker` instead of re-scanning; when both run, one tracker instance is shared and observed once per tick by `Main`.

- [ ] **Step 1: Write failing test** `RunConfigTest.mapFramesDefaultsOff`: `assertEquals(-1, RunConfig.parse(new String[]{"--out","x.txt"}).mapFrames())` and `assertEquals(0, RunConfig.parse(new String[]{"--out","x.txt","--map-frames","0"}).mapFrames())`.
- [ ] **Step 2: Run** `./gradlew test --tests '*RunConfigTest*'`. Expected: FAIL on the default.
- [ ] **Step 3: Implement** the default change in `RunConfig` and keep `Main`'s condition `>= 0`.
- [ ] **Step 4: In `DynamicsObserver`**, move every value that is overwritten each tick and only read at the end (`endedAtFixedPoint`, `dormantAtEnd`, `finalEnergy`, `finalMass`, `finalPopulation`, `distinctLineagesAlive`, `finalMassMax`, `finalEnergyTotal`, `finalReservoir`, and their helpers) into `summarize()`, computed from the last observed snapshot and `prevOutputs`/`prevDelay` as they stand after the last `observe`. Delete the lineage aggregates (fields, loop, `lineageSummary()`), `LineageSummary.java`, and the unused `DynamicsSummary` arrays (`peakBurn`, `propagationsByUnit`, `ticksActiveByUnit`, `clampByUnit`, `threshFlipsByUnit`, `finalMass[]`). `foldDigest` stays per tick and unchanged.
- [ ] **Step 5: Implement `BirthTracker`** by moving the shared code out of `BirthLogWriter` and `EventLogWriter`; writers keep their output formats byte for byte.
- [ ] **Step 6: Run** `./gradlew test`. Expected: PASS (existing `BirthLogWriterTest`, `ObservabilityTest` and `DynamicsObserverTest` unchanged and green).
- [ ] **Step 7: BASELINE-CHECK.** Also run with `--observe --out build/baseline-check/obs.txt` before and after the change (stash to compare) and `diff` `events.jsonl` and `births.csv.gz` contents (`gzcat`). Expected: identical.
- [ ] **Step 8: Update `AGENTS.md`** `--map-frames` line to "negative/absent = off [default], 0 = every tick, N>0 = N frames". **Commit**: `refactor(sim): opt-in frames, end-only observer values, shared birth tracker`.

### Task A3: Per-run config (`EngineConfig`)

**Files:**
- Create: `src/main/java/com/simolution/kernel/config/EngineConfig.java`, `ChemicalSpec.java`
- Create: `src/main/java/com/simolution/kernel/config/ConfigJson.java`
- Delete: `src/main/java/com/simolution/kernel/config/KernelConfig.java` (every use replaced)
- Modify: `Kernel.java`, `InflowConfig.java`, `NodeLayout.java` (junk counts), `RunConfig.java`, `Main.java`, all sim and test call sites
- Create: `examples/baseline.json`
- Modify: `build.gradle` (add `implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'`)
- Test: `src/test/java/com/simolution/kernel/config/EngineConfigTest.java`

**Interfaces:**
- Produces: `public record ChemicalSpec(String name, InflowConfig inflow, double capacity, double initial, double diffusionRate, double decayRate, int decayTarget)`; `decayTarget == -1` means the sink.
- Produces: `InflowConfig.uniform(double baseline)` replacing the `UNIFORM` constant (which referenced `KernelConfig.CELL_INFLOW`).
- Produces: `public record EngineConfig(long seed, int worldWidth, int maxGenes, List<ChemicalSpec> chemicals, double initialEnergy, double basalCost, double costPerPropagation, double harvestCapacityPerConnection, double harvestHalfSaturation, double harvestMassExponent, double moveCostBase, double moveCostPerMass, double moveDeadzone, double selfEnergyScale, double selfMassScale, double initialMass, double maintPerMass, double growMax, double growHalfSaturation, double growYield, double buildCost, double buildCostPerGene, double mutationRateWeight, double mutationRateStruct, double indelRateDup, double indelRateDel, double agingCost)` with `static EngineConfig v5Defaults(long seed, int worldWidth, int maxGenes)` returning today's `KernelConfig` values and one chemical `new ChemicalSpec("resource", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1)`; `EngineConfig withChemical0Inflow(InflowConfig)`; `boolean branchCompatible(EngineConfig other)` (true iff `worldWidth`, `maxGenes`, chemical count equal).
- Produces: `ConfigJson.write(EngineConfig): String` and `ConfigJson.read(String): EngineConfig`, Jackson with `FAIL_ON_UNKNOWN_PROPERTIES` on and every field required (missing key throws `IllegalArgumentException` naming it). `InflowSource` subtypes serialize with a `"type"` discriminator.
- Produces: `new Kernel(int[][] initialGenomes, int[] founderCells, EngineConfig config)`; `configureInflow` removed (inflow lives in the config). The kernel's RNG seed stays the constant `0L` in Stage A (`private static final long LEGACY_RNG_SEED = 0L`), so the baseline is unchanged; Stage B switches it to `config.seed()`.
- Weight scaling (`WEIGHT_MULTIPLIER`) and junk counts stay engine constants (move to `NodeLayout` / `GeneDecoder`); they define the encoding and layout, not the experiment.
- Produces: CLI `--config <file.json>` loads a config; other flags (`--seed`, `--world`, `--max-genes`, `--inflow`, ...) override fields after loading. `examples/baseline.json` reproduces the baseline run's config.

- [ ] **Step 1: Write failing tests** in `EngineConfigTest`: `roundTripsThroughJson` (`assertEquals(cfg, ConfigJson.read(ConfigJson.write(cfg)))` for `v5Defaults(42,100,64)` and for a config with a noise + orbiting-disk inflow); `rejectsUnknownKey` (JSON with `"basalCostt"` → exception message contains `basalCostt`); `rejectsMissingKey` (JSON without `growYield` → message contains `growYield`); `branchCompatibility` (`v5Defaults(1,100,64).branchCompatible(v5Defaults(1,120,64))` is false; same config with `basalCost` changed is true).
- [ ] **Step 2: Run** `./gradlew test --tests '*EngineConfigTest*'`. Expected: FAIL, classes missing.
- [ ] **Step 3: Implement** `ChemicalSpec`, `EngineConfig`, `ConfigJson`, the Jackson dependency.
- [ ] **Step 4: Replace every `KernelConfig.X`** in `Kernel` with a `final` field copied from the config in the constructor (hot loops read fields or locals, not record accessors). Replace `KernelConfig` uses in the harness and tests with `EngineConfig.v5Defaults(...)` values. Delete `KernelConfig`. `ConfigHash.compute()` becomes `ConfigHash.compute(EngineConfig)` = SHA-256 of `ConfigJson.write(config)`.
- [ ] **Step 5: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 6: BASELINE-CHECK**, plus `./gradlew run -q --args="--config examples/baseline.json --units 100 --ticks 1000 --out build/baseline-check/b2.txt"` diffed against `runs/baseline.txt`. Expected: IDENTICAL for both.
- [ ] **Step 7: Commit**: `refactor(kernel): per-run EngineConfig loaded from JSON, KernelConfig retired`.

### Task A4: Generated node layout and exported layout JSON

**Files:**
- Create: `src/main/java/com/simolution/kernel/layout/NodeSpec.java`, `NodeCatalog.java`
- Modify: `NodeLayout.java`, `GeneDecoder.java`, `Kernel.java` (`evaluateUnit`)
- Delete: `src/main/java/com/simolution/kernel/layout/NodeType.java` (if unused after the change)
- Modify: `src/main/java/com/simolution/sim/MapFrameWriter.java` or `Main.java` (write `<base>.layout.json` whenever `--out` is set), `src/main/resources/live.html`, `tools/mapviz.py`
- Test: `src/test/java/com/simolution/kernel/layout/NodeLayoutTest.java`, existing `GeneDecoderTest`

**Interfaces:**
- Produces: `public record NodeSpec(String name, Kind kind, Role role, int chemical)` with `enum Kind { SENSOR, INTERNAL, ACTION }` and `enum Role { CONST, RAND, CHEMICAL_HERE, SELF_ENERGY, SELF_MASS, ADD, MUL, CLAMP, DELAY, THRESH, PASSTHROUGH }` (`chemical` used only by `CHEMICAL_HERE`, else `-1`). Actions are `PASSTHROUGH` (output = accumulator); their consumers are phases.
- Produces: `NodeCatalog.V5`: the 13 meaningful nodes in today's order (sensors CONST, RAND, LOCAL_RESOURCE=`CHEMICAL_HERE(0)`, SELF_ENERGY, SELF_MASS; internals ADD, MUL, CLAMP, DELAY, THRESH; actions ACTION_Y, HARVEST, REPRODUCE, MOVE_N, MOVE_S, MOVE_E, MOVE_W, GROW) plus junk counts 2/3/1.
- Produces: `NodeLayout` keeps every existing public name (`Sensor.RAND`, `Action.GROW`, `SENSOR_OFFSET`, `TOTAL`, `isMeaningful`, `localName`), now computed from `NodeCatalog.V5` at class init (no longer compile-time constants); adds `static String toJson()` (names by local index, offsets, type counts, junk ranges, gene bit layout) and `static NodeSpec spec(int localIndex)` (null for junk).
- Produces: `GeneDecoder` primitive accessors used by the engine: `static int sourceLocal(int gene)`, `static int destinationLocal(int gene)`, `static double weight(int gene)`; `decode(int, int)` (returns `CompiledConnection`) stays for harness/analysis. `isValid` and its dead `null` paths are deleted.
- `evaluateUnit` writes sensors by iterating the sensor specs and switching on `Role`, and copies accumulators for each action; internal-node semantics are unchanged.

- [ ] **Step 1: Write failing tests** `NodeLayoutTest.layoutMatchesV5` (`TOTAL == 24`, `INTERNAL_OFFSET == 7`, `ACTION_OFFSET == 15`, `localName(2).equals("LOCAL_RESOURCE")`, `localName(5).equals("JUNK-S5")`, `Action.GROW == 7`), `jsonListsEveryNode` (parsed `toJson()` has 24 names in local-index order), and in `GeneDecoderTest` `primitiveAccessorsMatchDecode` (for 10,000 `Noise`-derived ints, `sourceLocal/destinationLocal/weight` equal `decode(g, 0)`'s fields).
- [ ] **Step 2: Run** `./gradlew test --tests '*NodeLayoutTest*' --tests '*GeneDecoderTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `NodeSpec`, `NodeCatalog`, the computed `NodeLayout`, the decoder accessors and the spec-driven `evaluateUnit`.
- [ ] **Step 4: Write `<base>.layout.json`** next to every `--out` run; `live.html` loads node names from it (replacing the hardcoded `NAMES`, `SENS_TC`, `INT_TC`, `ACT_TC`); `mapviz.py` inlines it when baking. Verify by baking `runs/baseline.frames.zst` from a `--map-frames 0` run and opening the HTML (names shown match `layout.json`).
- [ ] **Step 5: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 6: BASELINE-CHECK.** Expected: IDENTICAL.
- [ ] **Step 7: Commit**: `refactor(kernel): node layout generated from NodeCatalog, exported as layout.json`.

### Task A5: Declared state (`WorldState` + `FieldSpec`)

**Files:**
- Create: `src/main/java/com/simolution/kernel/state/WorldState.java`, `FieldSpec.java`
- Modify: `Kernel.java`, `KernelSnapshot.java`, sim writers that read the snapshot
- Test: `src/test/java/com/simolution/kernel/state/WorldStateTest.java`

**Interfaces:**
- Produces: `public final class WorldState` owning every array and scalar the kernel mutates, as public final fields with today's names and types (`outputsPrev`/`outputsNext` swap via a method `swapOutputs()`; `cellChem`/`cellChemNext` replace `resourceField`/`resourceFieldNext`, laid out `[k * cells + cell]`, swap via `swapChem()`), plus `double[] unitChem` (`[k * S + slot]`, declared `SAVED`, unused until a feature needs body stores), plus scalars `tick`, `energySink`, `cumulativeInflow`, `birthsTotal`, `maxGeneration`, `creditedInitialEnergy`, `creditedInitialMass`, `initialResourceTotal`. Constructor `WorldState(EngineConfig config)` allocates; founders are installed by the kernel.
- Produces: `public record FieldSpec(String name, Dim dim, Persist persist, OnBirth onBirth, OnDeath onDeath, Object array)` with `enum Dim { UNIT, CELL, UNIT_NODE, CELL_CHEM, GENE, SCALAR }`, `enum Persist { SAVED, DERIVED, SCRATCH }`, `enum OnBirth { RESET_ZERO, EXPLICIT }`, `enum OnDeath { KEEP, ZERO }`. `WorldState.fields(): List<FieldSpec>` declares every array in a fixed order; `DERIVED` = rebuilt from genes at restore (compiled connections, ranges, `harvestConnCount`, `mulInDegree`), `SCRATCH` = never saved (accumulators, `mulTop*`, `activeThisTick`, `outputsNext`, `cellChemNext`).
- Produces: `WorldState.resetForBirth(int slot)` zeroes every `RESET_ZERO` unit/unit-node field of that slot (replaces the hand loop in `finishChild`); `WorldState.applyDeath(int slot)` zeroes every `ZERO` field (today: `mass`).
- Produces: `KernelSnapshot(WorldState state, double reservoir, double massTotal)`: keeps every existing public field name (writers unchanged), filled from `state`, plus `public final WorldState state` and `public final int[] activeThisTick` (needed in B5).
- Order-sensitive code (energy sink accumulation, loops) moves verbatim; this task only relocates storage.

- [ ] **Step 1: Write failing tests** `WorldStateTest.fieldsCoverEveryArray` (reflection: every array-typed field of `WorldState` appears in `fields()` exactly once) and `savedFieldsHaveStableOrder` (names of `SAVED` fields equal a literal list beginning `genes, geneCount, energy, mass, damage, lineageId, generation, position, cellChem, cellOccupant, outputsPrev, delayMemory`).
- [ ] **Step 2: Run** `./gradlew test --tests '*WorldStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `FieldSpec`, `WorldState`; move kernel storage into it; rebuild `KernelSnapshot`.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK.** Expected: IDENTICAL.
- [ ] **Step 6: Commit**: `refactor(kernel): state declared once in WorldState/FieldSpec`.

### Task A6: Flat connection storage, junk pruning, hoisted mutation hashing

**Files:**
- Modify: `Kernel.java` (`compileSlot`, `propagateConnections`, `mutate`, `liveConnections`, `connectionsOf`), `WorldState.java`
- Modify: `src/main/java/com/simolution/kernel/runtime/Noise.java`
- Delete: `src/main/java/com/simolution/kernel/layout/CompiledConnection.java` only if no harness use remains (harness analysis keeps it otherwise)
- Test: `src/test/java/com/simolution/kernel/runtime/NoiseTest.java` (new), existing `MulNodeTest`, `IndelTest`

**Interfaces:**
- Produces in `WorldState`: `short[] connSrc`, `short[] connDst` (unit-local node indices), `double[] connWeight` (stays `double`: the product `outputsPrev[src] * weight` is computed in double and cast to float today; a float weight would change rounding, deferred), capacity `S × maxGenes`, replacing `CompiledConnection[] connections`. `sumStart/sumEnd/mulStart/mulEnd` stay.
- Compile: connections whose source is a junk node are not stored; `mulInDegree` still counts every MUL-destined gene (junk source included), computed before pruning. Zero allocation per compile.
- Produces: `Noise.mutationPrefix(long seed, int slot, int tick): long` (the first two mixes) and `Noise.mutationUniformFromPrefix(long prefix, int index): double`, with `mutationUniform(seed, slot, tick, index) == mutationUniformFromPrefix(mutationPrefix(seed, slot, tick), index)` bit for bit. `mutate` computes the prefix once per birth.
- `liveConnections()`/`connectionsOf()` build `CompiledConnection` objects from the flat arrays for the harness (off the hot path; they now omit junk-source connections).
- `GenomeCompiler` stays as the harness's analysis decoder (the `## structure` report section counts junk-touching connections, which the engine no longer stores). This deviates from design §5.4's "replaced by the engine routine" on purpose.

- [ ] **Step 1: Write failing test** `NoiseTest.prefixFormMatchesDirectForm`: for 1,000 `(slot, tick, index)` triples, `assertEquals(Double.doubleToRawLongBits(direct), Double.doubleToRawLongBits(viaPrefix))`.
- [ ] **Step 2: Run** `./gradlew test --tests '*NoiseTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the `Noise` methods, flat storage, pruning and hoisting.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS (`MulNodeTest` tie order and `IndelTest` same-seed identity unchanged).
- [ ] **Step 5: BASELINE-CHECK.** Expected: IDENTICAL. Then `./gradlew bench` and append a `## after A6` row set to `engine-v6-bench.md`.
- [ ] **Step 6: Commit**: `perf(kernel): flat connection arrays, compile-time junk pruning, hoisted mutation hash`.

### Task A7: Phase objects and the chemical table

**Files:**
- Create: `src/main/java/com/simolution/kernel/phase/Phase.java`, `Shape.java`, `PhaseRunner.java`, `SerialRunner.java`
- Create: one class per phase in `src/main/java/com/simolution/kernel/phase/`: `ClearPhase`, `PropagatePhase`, `EvaluatePhase`, `SwapPhase`, `IntakePhase`, `CostPhase`, `GrowthPhase`, `ReproductionPhase`, `MovementPhase`, `DiffusionPhase`, `DecayPhase`
- Modify: `Kernel.java` (becomes construction, founder install, `tick()` driving the phase list, and the public API)
- Test: `src/test/java/com/simolution/kernel/phase/ChemicalPhaseTest.java`, existing `EnergyTest`, `BiomassTest`, `MovementTest`, `InflowConfigTest`

**Interfaces:**
- Produces: `enum Shape { PER_UNIT, PER_CELL, CLAIM, SERIAL }`.
- Produces: `interface Phase { String name(); Shape shape(); default void prepare() {} void run(int from, int to, int chunk); default void finish() {} int extent(); }` where `extent()` is slot count (`PER_UNIT`), cell count (`PER_CELL`) or either for `SERIAL`. Contract: `PER_UNIT`/`PER_CELL` may write only their own index's data; `SERIAL` runs once over `[0, extent)` with `chunk == 0`; `CLAIM` proposes in `run` and resolves in `finish`.
- Produces: `interface PhaseRunner { void runTick(List<Phase> phases); }` and `SerialRunner implements PhaseRunner` calling `prepare(); run(0, extent(), 0); finish();` per phase in list order.
- Produces: `Kernel.phases(): List<Phase>` built once in the constructor in today's order. Reproduction and movement are `SERIAL` in Stage A. Phases accumulate into `state.energySink` exactly as today.
- Chemicals: `DiffusionPhase` and `DecayPhase` loop over `config.chemicals()`; inflow is per chemical (`ChemicalSpec.inflow`, compiled once per chemical); intake reads chemical 0. A `decayTarget >= 0` adds the decayed amount to that chemical in the same cell instead of the sink.
- Fix the stale javadoc while moving code: diffusion/decay docs separated, "Slot index == cell index", "∝ connection count", phase numbers.

- [ ] **Step 1: Write failing tests** in `ChemicalPhaseTest`: `decayIntoAnotherChemicalConserves` (2 chemicals, chemical 0 decays into 1, no units, 50 ticks: sum over both fields + sink equals initial + cumulative inflow within `1e-9`, and chemical 1 is non-zero); `decayToSinkMatchesV5` (1 chemical with v5 defaults: field after 30 ticks equals a hand-computed reference loop of diffusion-then-decay over the same initial field, exact `assertArrayEquals(expected, actual, 0.0)`).
- [ ] **Step 2: Run** `./gradlew test --tests '*ChemicalPhaseTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the phase interface, runner, the 11 phase classes and the chemical loop.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK.** Expected: IDENTICAL.
- [ ] **Step 6: Commit**: `refactor(kernel): tick as a declared phase list; chemical table with per-chemical inflow, diffusion, decay`.

### Task A8: Versioned checkpoints and the run lifecycle

**Files:**
- Create: `src/main/java/com/simolution/kernel/EngineVersion.java`
- Create: `src/main/java/com/simolution/kernel/state/CheckpointCodec.java`
- Create: `src/main/java/com/simolution/sim/run/RunDirectory.java`, `LatestFrame.java`
- Modify: `Kernel.java` (`saveState`/`loadState` replaced), `CheckpointWriter.java`, `Main.java`, `RunConfig.java`, `RunReport.java` (kernel string from `EngineVersion`)
- Delete: `src/main/java/com/simolution/sim/RunManifest.java`, `Replayer.java` (replaced), `ConfigHash.java` (folded into `CheckpointCodec`)
- Test: `src/test/java/com/simolution/kernel/state/CheckpointCodecTest.java`, `src/test/java/com/simolution/sim/run/RunLifecycleTest.java`; rewrite `ObservabilityTest` onto the new API

**Interfaces:**
- Produces: `EngineVersion.ID` = `"v5"` in Stage A (the report prints `kernel: v5`, unchanged); B5 sets `"v6"`.
- Produces: checkpoint format: magic `"SIMO"`, `int formatVersion = 1`, UTF engine version, UTF config JSON, UTF layout JSON, then every `SAVED` field of `WorldState.fields()` in declared order (arrays length-prefixed), scalars included. Self-sufficient: no manifest needed.
- Produces: `CheckpointCodec.write(Kernel k, OutputStream out)`; `Kernel CheckpointCodec.read(InputStream in)` (refuses with `IllegalStateException` on bad magic, other format version, other engine version, layout JSON different from `NodeLayout.toJson()`, or truncation, each message naming the cause); `Kernel CheckpointCodec.readBranch(InputStream in, EngineConfig override)` (refuses when `!original.branchCompatible(override)`).
- Produces: `Kernel.step(int n)`, `Kernel.currentTick()`, `Kernel.config()`.
- Produces: `RunDirectory` over `<base>.obs/` (kept path): `static RunDirectory create(Path dir, Kernel k, int checkpointEvery)` writes `ckpt/0.ckpt` immediately and `run.json` (`{"runId": <SHA-256 hex of the tick-0 checkpoint bytes>, "engine": ..., "parent": null}`; the tick-0 checkpoint holds config, layout and founders, and never thread count, so this is the run identity of design §8); `static RunDirectory createBranch(Path dir, Kernel branched, int checkpointEvery, String parentRunId, int parentTick)` does the same with `"parent": {"runId": ..., "tick": ...}`; `void afterTick(Kernel k)` writes `ckpt/<tick>.ckpt` every `checkpointEvery` ticks; `static Kernel seek(Path dir, int tick)` loads the nearest checkpoint `<= tick` and steps forward; `events.jsonl` and `metrics.csv` writers keep their formats.
- Produces: `LatestFrame` (latest-frame-wins, never blocks the tick loop): `void publish(Kernel k)` copies `cellOccupant`, occupant genome ids, chemical fields into one of two preallocated buffers and swaps an `AtomicReference`; `Frame latest()` returns the most recent completed buffer. Published every N ticks by the caller.
- CLI: `--replay <obsDir> --at T [--snapshot-out f]` uses `RunDirectory.seek`. Non-uniform inflow runs may now use `--observe` (the guard at `RunConfig.java:107` is removed, since the config travels in the checkpoint).

- [ ] **Step 1: Write failing tests** in `CheckpointCodecTest`: `roundTripIsExact` (run 300 ticks of a 32×32, 60-founder world, write, read, compare every `SAVED` field with `assertArrayEquals`, then step both 200 ticks and compare again); `refusesTruncated` (drop the last 10 bytes: message contains `truncated`); `refusesOtherEngineVersion` (patch the version string: message contains `engine version`); `refusesBadMagic`; `branchRefusesShapeChange` (override with `worldWidth` 40: refused); `branchAllowsRateChange` (override `basalCost` 0.6: loads, and stepping diverges from the unbranched run within 100 ticks). In `RunLifecycleTest`: `seekMatchesUninterruptedAtEveryTick` (checkpointEvery 50, for every T in [0, 300] the sought state equals the canonical run's state at T) and `cyclicInflowRunCanBeSought` (same with `InflowConfig.cyclic(200, 10, 10)`).
- [ ] **Step 2: Run** `./gradlew test --tests '*CheckpointCodecTest*' --tests '*RunLifecycleTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `EngineVersion`, `CheckpointCodec`, `RunDirectory`, `LatestFrame`, the CLI rewiring; delete `RunManifest`, `Replayer`, `ConfigHash`; port `ObservabilityTest` (observer-neutrality digest test kept as is).
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK.** Expected: IDENTICAL.
- [ ] **Step 6: Commit**: `feat(kernel): self-sufficient versioned checkpoints, seek and branch, latest-frame publisher`.

---

## Stage B: parallel engine and the one semantic change

Stage B tasks are committed one by one but `runs/baseline.txt` is regenerated only in B5. From B1 on, BASELINE-CHECK no longer applies; each task's own tests and `./gradlew test` gate it.

### Task B1: Parallel runner, chunked totals, thread setting

**Files:**
- Create: `src/main/java/com/simolution/kernel/phase/ParallelRunner.java`, `Chunks.java`
- Modify: every phase that touches `energySink` or `cumulativeInflow`; `WorldState.java`; `Kernel.java`; `RunConfig.java` (`--threads N`, default `Runtime.availableProcessors()`); `Bench.java` (`--threads`)
- Test: `src/test/java/com/simolution/kernel/phase/DeterminismMatrixTest.java`

**Interfaces:**
- Produces: `Chunks.SIZE = 4096` (engine constant); `Chunks.count(int extent)`; chunk `c` covers `[c*SIZE, min(extent,(c+1)*SIZE))`.
- Produces: `ParallelRunner(int threads) implements PhaseRunner, AutoCloseable`: persistent worker threads, chunks claimed from an atomic counter, one barrier per phase; `SERIAL` phases and extents of one chunk run inline on the caller. `threads == 1` never starts workers. No per-tick allocation.
- Produces in `WorldState`: `double[] sinkPartial`, `double[] inflowPartial` sized `Chunks.count(max(S, C))`; phases add into `sinkPartial[chunk]`; `finish()` (or the runner after each phase) adds partials into `energySink`/`cumulativeInflow` in ascending chunk index and zeroes them. `KernelSnapshot` totals (`reservoir`, `massTotal`) are summed per chunk and merged in chunk order the same way.
- Produces: `Kernel(int[][] genomes, int[] founderCells, EngineConfig config, int threads)`; the old constructor delegates with `threads = 1`. Thread count is not part of the config and not in checkpoints.
- Produces (test sources): `src/test/java/com/simolution/kernel/TestDigest.java` with `static long of(Kernel k)`, a serial fold over every `SAVED` field in declared order, used by B1 to B4 tests; B5's `StateDigest` supersedes it and the tests switch over.

- [ ] **Step 1: Write failing tests** in `DeterminismMatrixTest`: `identicalAcrossThreadCounts` (64×64, 300 founders, 400 ticks, seed 7: per-tick `TestDigest.of` arrays equal for threads 1, 2, 4, 8); `resumeAcrossThreadCounts` (run 2 threads to tick 150, checkpoint, restore with 4 threads, run to 400; final digest equals uninterrupted 1-thread run); `tinyWorlds` (W=1 with 1 founder, W=3 with 9 founders, a 2000-tick run that goes extinct: no exception, digests equal for threads 1 and 8).
- [ ] **Step 2: Run** `./gradlew test --tests '*DeterminismMatrixTest*'`. Expected: FAIL (runner missing).
- [ ] **Step 3: Implement** `Chunks`, `ParallelRunner`, partial totals, `--threads`.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS except exact-total pins that encode serial summation (e.g. sink values in `EnergyTest`/`BiomassTest` compared with tolerance 0); loosen those to `1e-9` relative only where the assertion is about a total, never about per-unit trajectories.
- [ ] **Step 5: Run** `./gradlew bench --args="--threads 1"` and `--threads 4`; append `## after B1` rows.
- [ ] **Step 6: Commit**: `feat(kernel): parallel phase runner with fixed chunks and ordered partial totals`.

### Task B2: Movement as a claim phase

**Files:**
- Modify: `src/main/java/com/simolution/kernel/phase/MovementPhase.java`, `WorldState.java`
- Test: `src/test/java/com/simolution/kernel/runtime/MovementTest.java`

**Interfaces:**
- Produces in `WorldState`: `int[] moveIntent` (per slot, `-1` = none) and `int[] cellWinner` (per cell, `-1`), both `SCRATCH`.
- Behaviour: `run` (parallel, per unit): intent = target cell if the unit is alive, its MOVE outputs clear the deadzone, the target is free at phase start (`cellOccupant[target] < 0`), and `energy > moveCost`; else `-1`. `finish` (serial): ascending slot, first intent per target cell wins (`cellWinner`); apply winners' moves and charges into `sinkPartial[0]` then merge; reset touched `cellWinner` entries. Losers stay and pay nothing. No same-tick chains.

- [ ] **Step 1: Write failing tests**: `contestedCellGoesToLowestSlot` (slots 0 and 1 both target one free cell: slot 0 moves, slot 1 stays with unchanged energy); `vacatedCellCannotBeEnteredSameTick` (unit A moves out of cell X while unit B targets X: B stays this tick, enters next tick). Keep the existing latency, cost, deadzone and blocking tests.
- [ ] **Step 2: Run** `./gradlew test --tests '*MovementTest*'`. Expected: the new chain test FAILS.
- [ ] **Step 3: Implement** the claim phase.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS (determinism matrix included).
- [ ] **Step 5: Commit**: `feat(kernel)!: movement as a deterministic claim phase (no same-tick chains)`.

### Task B3: Reproduction as a claim phase, parent-keyed mutation, birth list

**Files:**
- Modify: `src/main/java/com/simolution/kernel/phase/ReproductionPhase.java`, `Kernel.java` (`mutate`), `Noise.java`, `WorldState.java`
- Modify: `src/main/java/com/simolution/sim/BirthTracker.java`, `BirthLogWriter.java`, `EventLogWriter.java` (exact parents from the engine)
- Test: `src/test/java/com/simolution/kernel/runtime/ReproductionClaimTest.java` (new), existing `BiomassTest`, `IndelTest`

**Interfaces:**
- Mutation key becomes `(seed, parentSlot, tick, op)`. Point mutation uses geometric skip sampling: for each rate class, the gap to the next flipped bit is `floor(StrictMath.log(u) / StrictMath.log(1 - rate))` with `u = 1 - draw` (in (0, 1]) from the next keyed draw; bits are visited in today's order (gene by gene, bits 0 to 31, weight bits at `mutationRateWeight`, structure bits at `mutationRateStruct`). Deletion and duplication draws stay one per gene, after point mutation, continuing the same op counter.
- Produces: `static int childLength(int parentCount, long prefix, ...)` computing the post-indel length from the indel draws alone (indel decisions never depend on gene values), so affordability is known before any slot is claimed; the install replays the same keyed draws while writing genes.
- Produces in `WorldState` (`SCRATCH`): `int[] birthCell` (per slot, `-1`), `double[] birthCost`, and the published per-tick birth list `int[] birthParent`, `int[] birthChild`, `int[] birthChildCell`, `int birthCount`.
- Behaviour: `run` (parallel, per parent): candidate if alive, `energy > buildCost` (with `childLength`), wants to divide, and has a free Moore neighbour at phase start (lowest index); writes `birthCell`/`birthCost`. `finish` (serial): ascending parent slot, first claim per cell wins; winners take free slots (`energy <= 0` at phase start) in ascending slot order; install each child (copy + mutate + compile into its slot, split energy and mass after charging `birthCost` to the sink, lineage, generation); append to the birth list. Losers and unaffordable parents pay nothing and burn no slot.
- `BirthTracker` reads the engine's birth list; parents are exact (no geometric inference).

- [ ] **Step 1: Write failing tests** in `ReproductionClaimTest`: `contestedChildCellGoesToLowestParent`; `unaffordableBuildBurnsNoSlot` (a parent that cannot afford its child leaves every slot's energy and genes untouched); `childGenomeIndependentOfSlot` (the same parent at the same tick produces identical child genes whether the free slot is 5 or 900); `birthListMatchesPopulationDelta` (over 500 ticks, `birthCount` summed equals `birthsTotal`, and each listed child's `lineageId` equals its parent's); `skipSamplingRate` (over 10^6 simulated bits at rate 1e-3, flip count within 4 standard deviations of 1000).
- [ ] **Step 2: Run** `./gradlew test --tests '*ReproductionClaimTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the claim phase, keyed mutation with skip sampling, `childLength`, the birth list, and the writer change.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS after updating exact pins in `BiomassTest`/`IndelTest` that encode slot-keyed genomes; justify each updated value in the commit message.
- [ ] **Step 5: Commit**: `feat(kernel)!: reproduction as a claim phase, parent-keyed skip-sampled mutation, published birth list`.

### Task B4: Seed plumbing, exact harvest law, RNG streams, strict-math lint

**Files:**
- Modify: `Kernel.java` (`LEGACY_RNG_SEED` removed, `scatterFounders(long seed, int count, int worldWidth)`), `Noise.java`, `IntakePhase.java`, `Main.java`
- Test: `src/test/java/com/simolution/kernel/runtime/SeedTest.java`, `src/test/java/com/simolution/kernel/StrictMathLintTest.java`

**Interfaces:**
- All kernel randomness keys off `config.seed()`.
- Produces: `Noise` streams with explicit domain tags: `static final long STREAM_RAND = 1, STREAM_MUTATION = 2, STREAM_PLACEMENT = 3`; every keyed draw mixes `seed`, then the stream tag, then its ids. `sample`, `mutationPrefix`, `placementUniform` keep their signatures and use their tag.
- Harvest: `Math.sqrt(mass)` when `harvestMassExponent == 0.5`, else `StrictMath.pow(mass, exponent)`, chosen once at construction (no per-unit branch on the exponent).

- [ ] **Step 1: Write failing tests**: `SeedTest.seedReachesAllStreams` (seeds 1 and 2, identical genomes and founder cells: RAND outputs at tick 1 differ, founder placement differs, and after 300 ticks with births the child genomes differ); `StrictMathLintTest.kernelUsesNoNonStrictTranscendentals` (scan `src/main/java/com/simolution/kernel/**/*.java` text for `Math.pow(`, `Math.exp(`, `Math.log(`, `Math.sin(`, `Math.cos(`, `Math.tan(`, `Math.fma(` not preceded by `Strict`: zero matches).
- [ ] **Step 2: Run** both tests. Expected: FAIL (seed constant, `Math.pow` present).
- [ ] **Step 3: Implement** seed plumbing, stream tags, harvest change.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: Commit**: `fix(kernel)!: run seed drives all kernel randomness; exact sqrt harvest; tagged RNG streams`.

### Task B5: New digest, report-v15, engine v6, the baseline change

**Files:**
- Create: `src/main/java/com/simolution/kernel/state/StateDigest.java` (replaces the B1 test helper)
- Modify: `DynamicsObserver.java`, `DynamicsSummary.java`, `RunReport.java`, `EngineVersion.java` (`"v6"`), `CheckpointCodec.java` (writes the digest into the header), `runs/baseline.txt`
- Create: `docs/specs/report-v15.md`
- Test: `src/test/java/com/simolution/kernel/state/StateDigestTest.java`, `RunReportTest.java`, `DynamicsObserverTest.java`

**Interfaces:**
- Produces: `StateDigest.of(Kernel k, PhaseRunner runner): long`: for each `SAVED` field in declared order, per chunk a serial `Noise.mix` fold over the chunk's elements' raw bits, chunk hashes combined in ascending chunk index with `h = Noise.mix(h ^ chunkHash)`; chunk folds run on the runner. Computed at the end of a run (report) and at each checkpoint; `--digest-every-tick` (debug) prints a per-tick digest line to stdout.
- report-v15 (`schema: report-v15`, `kernel: v6`): `## dynamics` keeps `ticks-with-any-activity` and `signal-propagations-total`, both computed over all living units from `activeThisTick`; removes `junk-sink-*`, `clamp-saturation-events`, `thresh-flips-total`, `units-dormant-*`. `## energy` removes `units-alive-at-end`, `units-dead-at-end`, `death-tick-*`. Removes `## intake`, `## burn-rate`, `## terminal-regimes`, `## action-channel`. `state-digest` = final-state digest. All other lines unchanged in name and meaning. `DynamicsObserver` drops the founder connection re-propagation and all founder-indexed state.

- [ ] **Step 1: Write failing tests**: `StateDigestTest.digestIndependentOfThreads` (same run, final digest at threads 1 and 8 equal) and `digestDetectsOneBitFlip` (flip one bit of one `energy` entry: digest changes); `RunReportTest.reportContainsAllSections` updated to the v15 section list and `schema: report-v15`.
- [ ] **Step 2: Run** them. Expected: FAIL.
- [ ] **Step 3: Implement** `StateDigest`, the observer and report changes, `EngineVersion.ID = "v6"`, `docs/specs/report-v15.md` (delta over report-v14 listing exactly the removals above).
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: Regenerate the baseline**: `./gradlew run -q --args="--units 100 --ticks 1000 --seed 42 --world 100 --out runs/baseline.txt"`; then `git diff runs/baseline.txt` and write, for the commit message, one line per changed report line naming which design §6 change caused it. Run it twice with `--threads 1` and `--threads 8` and diff: identical.
- [ ] **Step 6: Commit**: `feat!: engine v6 semantic stage: parallel digest, report-v15, regenerated baseline`.

### Task B6: Proof and documentation

**Files:**
- Modify: `docs/notes/engine-v6-bench.md`, `README.md`, `AGENTS.md`
- Create: `docs/contract/contract-v9.md`; ADRs `docs/decisions/0038-*.md` onward
- Modify: `docs/notes/engine-v6-design.md` (status line: implemented)

**Interfaces:**
- contract-v9: claim movement (start-of-phase freeness, lowest slot wins, no chains), claim reproduction (lowest parent wins, no slot burned, ascending slot assignment), parent-keyed skip-sampled mutation, seed-keyed streams, thread-count independence; supersedes contract-v3 §9 tie/chain rules and contract-v5 §5 placement order.
- ADRs (one each, 5-15 lines): declared state and generated layout; phase shapes and the parallel runner with fixed chunks; claim-based movement and reproduction; parent-keyed skip-sampled mutation; chemical table; per-run config and versioned checkpoints with seek/branch; final-state digest; retirement of the microcontroller budget and the locked pipeline.
- AGENTS.md: architecture tree updated to the new packages, phase-shape rules replace "tick pipeline is locked", microcontroller budget and per-array ADR rule removed, determinism contract stated for any thread count, new flags (`--config`, `--threads`, `--digest-every-tick`), report-v15 and contract-v9 in the doc list.

- [ ] **Step 1: Run** `./gradlew bench --args="--threads 1"` and `--threads 4` on the dev machine, and once on the CX33 (`--threads 3`). Record under `## v6 (after)`.
- [ ] **Step 2: Check the stopping rule** from the design §10: (1) `dense200` single-thread at least 5× v5's median; (2) `big500` 4 threads at least 2.5× 1 thread; (3) a JFR profile of `dense200` shows no avoidable hotspot above 20% and the harness below 10%; a W=1000 world with 50000 founders runs 100 ticks inside `-Xmx4g`. Expected: all hold. If one fails, stop and report the numbers instead of continuing (profile-driven follow-up is a separate task).
- [ ] **Step 3: Write** contract-v9, the ADRs, the README before/after table and architecture section, the AGENTS.md changes.
- [ ] **Step 4: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit**: `docs: contract-v9, v6 ADRs, AGENTS/README for engine v6, benchmark results`.
