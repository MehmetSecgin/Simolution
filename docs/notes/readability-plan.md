# Readability Refactor Implementation Plan (stages 1-5 and 7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the harness, config, genome and test code readable without comments, as a pure refactor that keeps every output byte-identical.

**Architecture:** First a golden-output harness captures every output of a fixed set of runs at the starting commit. Each later task restructures one area (Main, CLI, EngineConfig, genome, inflow, writers, reports, tests) and must reproduce the golden outputs exactly. Kernel phases (stage 6) and package moves (stage 8) are out of scope.

**Tech Stack:** Java 26, Gradle 9.8.1, JUnit 5.11.4, Jackson databind 2.18.2, zstd-jni.

**Spec:** `docs/notes/readability-design.md` (detailed findings with file:line in `docs/notes/readability-audit-harness.md` and `docs/notes/readability-audit-config-genome-tests.md`).

## Global Constraints

- Pure refactor: no behaviour, output format, CLI flag, JSON key or checkpoint byte changes. The one allowed message change: a missing flag value reports "`<flag>` needs a value" (Task R3).
- After every task, both checks below print `IDENTICAL`.
- Zero allocation per tick; no streams, boxing or lambdas in per-tick paths. Lambdas created once at setup are fine.
- StrictMath for transcendental functions in kernel code.
- No back-compat shims: no forwarding constructors with defaults, no deprecated aliases; update every call site, tests included.
- Comment rule: a comment stays only for an invariant or numeric trick a name cannot carry (list in the spec, rule 4). Delete history and narration from every file a task touches. Tests carry only `// arrange`, `// act`, `// assert`.
- Methods aim for 25 lines or fewer; no positional constructor with more than about 5 arguments of one type.
- Observer order in the tick loop stays: dynamics observer, per-tick digest print, time series, birth log, map frames, event log, metrics, checkpoints, trace.
- The two energy-audit formulas (`MetricsWriter`, `DynamicsSummary`) keep their own addition order; never merge them.
- Do not touch `kernel/phase/*`, `kernel/runtime/Kernel.java`, `kernel/runtime/KernelSnapshot.java` or `kernel/state/*` beyond the call-site edits a task names (stage 6 territory, being changed on a parallel branch).
- Conventional Commits, small commits, stage explicit paths. Never push.

**BASELINE-CHECK:**

```bash
./gradlew run -q --args="--units 100 --ticks 1000 --seed 42 --world 100 --out build/baseline-check/baseline.txt" && diff runs/baseline.txt build/baseline-check/baseline.txt && echo IDENTICAL
```

**OUTPUT-CHECK** (after Task R0 exists):

```bash
rm -rf build/golden-now && scripts/golden-outputs.sh build/golden-now && diff -r .golden/ref build/golden-now && echo IDENTICAL
```

## Review Focus

1. A run that throws mid-way (bad config discovered late, disk full, kernel exception) must still leave complete, readable gzip and zstd side files, not truncated streams (test in R2).
2. A flag given without its value (`--units` as the last argument) must fail naming the flag, not with an index exception (test in R3).
3. An existing config JSON file and an existing checkpoint written before the refactor must still load, and the config hash must be unchanged (test in R4).
4. An inflow spec with an unknown key or a repeated key must behave exactly as today: unknown key rejected with its name, last value wins (test in R3).
5. A gene with raw weight -32768 must decode without failing any bound check (test in R5).

---

### Task R0: Golden-output harness and the comment rule

**Files:**
- Create: `scripts/golden-outputs.sh`
- Modify: `AGENTS.md` (Conventions: replace the "Javadoc is welcome here" bullet), `.git/info/exclude` (add `/.golden/`)

**Interfaces:**
- Produces: `scripts/golden-outputs.sh <dir>` and the reference directory `.golden/ref`, used by OUTPUT-CHECK in every later task.

- [ ] **Step 1: Write `scripts/golden-outputs.sh <dir>`.** Build once with `./gradlew -q installDist`, then call `build/install/Simolution/bin/Simolution` directly (no Gradle noise in stderr). Each case gets its own subdirectory with `stdout.txt`, `stderr.txt` and `exit.txt` (the exit code). Remove every stdout line starting with `(wall clock:` (the only nondeterministic line). Cases, with all paths under `<dir>`:
  1. `full`: `--units 100 --ticks 600 --seed 42 --world 100 --map-frames 0 --observe --checkpoint-every 200 --threads 2 --out <dir>/full/run.txt`
  2. `spread`: `--units 60 --ticks 300 --seed 7 --world 60 --map-frames 25 --genes 24 --max-genes 48 --out <dir>/spread/run.txt`
  3. `inflow`: `--config examples/baseline.json --units 150 --ticks 400 --seed 3 --world 80 --inflow "disk orbit=0.15,4000 r=12 peak=8; rect fx0=0 fy0=0 fx1=0.3 fy1=1 peak=5; points cells=100,250,4000 peak=8; noise seed=7 freq=0.05 thr=0.6 gain=3 peak=6 anim=800; combine=max baseline=0.1" --map-from 100 --map-to 140 --out <dir>/inflow/run.txt`
  4. `cycle`: `--units 50 --ticks 300 --seed 5 --world 60 --resource-cycle --cycle-period 200 --cycle-radius 10 --cycle-peak 5 --out <dir>/cycle/run.txt`
  5. `demo`: no arguments (demo genome with trace)
  6. `digest`: `--units 20 --ticks 30 --seed 1 --world 20 --digest-every-tick`
  7. `replay`: `--replay <dir>/full/run.obs --at 450 --snapshot-out <dir>/replay/snap-450.txt`, and a second call without `--snapshot-out` (stdout)
  8. `errors`: one sub-case per call: `--observe` without `--out`; `--resource-cycle --inflow "disk r=10"`; `--map-from 5`; `--threads 0`; `--bogus`; `--inflow "disk wat=1"`
  Paths printed to stdout differ between `<dir>` values, so the script runs every case with the working directory set to `<dir>` and passes relative output paths, so the output is the same for any `<dir>`. The `--config` path is absolute (`$REPO/examples/baseline.json`). If it shows up in any output, replace it with `examples/baseline.json` in the script.
- [ ] **Step 2: Prove reproducibility.** Run `scripts/golden-outputs.sh build/golden-a` and then `scripts/golden-outputs.sh build/golden-b`, then `diff -r build/golden-a build/golden-b`. Expected: no output. If any file differs between two runs of the same commit, find the cause and normalise it in the script (record which file and why in the task report). Never drop a whole file.
- [ ] **Step 3: Create the reference:** `scripts/golden-outputs.sh .golden/ref`. Add `/.golden/` to `.git/info/exclude`.
- [ ] **Step 4: Replace the AGENTS.md Javadoc bullet** with: "Comments only for an invariant or numeric trick a name cannot carry (examples: MUL in-degree rule, saturating-drive overflow rearrangement, mutation operation-counter determinism, Noise hashing, barrier handshake, signed-zero junk pruning). Everything else is a name or lives in docs/. No history in code. Tests: only `// arrange`, `// act`, `// assert`."
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: both `IDENTICAL`.
- [ ] **Step 6: Commit** `scripts/golden-outputs.sh`, `AGENTS.md`, `docs/notes/readability-design.md`, `docs/notes/readability-plan.md`, `docs/notes/readability-audit-*.md`: `chore: golden-output check and comment rule for the readability refactor`.

### Task R1: Founders, run paths, demo genome

**Files:**
- Create: `src/main/java/com/simolution/sim/Founders.java`, `src/main/java/com/simolution/sim/RunPaths.java`
- Modify: `src/main/java/com/simolution/Main.java`, `src/main/java/com/simolution/sim/GenomeFactory.java`
- Test: `src/test/java/com/simolution/sim/FoundersTest.java`, `src/test/java/com/simolution/sim/RunPathsTest.java`

**Interfaces:**
- Produces:
  - `public record RunPaths(Path report)` with `Path layoutJson()` (`<base>.layout.json`), `catalog()` (`.catalog.gz`), `births()` (`.births.csv.gz`), `framesZst()`, `framesIdx()`, `timeSeries()` (`.timeseries.csv`), `obsDir()` (`<base>.obs`), and `void createParentDirectory() throws IOException`. `<base>` is the file name up to its last dot, or the whole name when there is no dot.
  - `public record Founders(int[][] genomes, int[] cells, EngineConfig engine)`, with `static Founders create(RunConfig config)` (genomes from the config, `engine` widened with `withMaxGenes(longest genome)`, cells from `Kernel.scatterFounders(seed, count, worldWidth)`), `CompiledConnection[] connections()`, `int slotCount()` (worldWidth²) and `Kernel newKernel(int threads)`.
  - `GenomeFactory.demo()` returns the 4-gene demo genome now in `Main.demoGenome`.

- [ ] **Step 1: Write failing tests.**
  - `RunPathsTest.siblingsShareTheReportBase`: `new RunPaths(Path.of("runs/a.txt")).catalog()` equals `Path.of("runs/a.catalog.gz")`, `.obsDir()` equals `Path.of("runs/a.obs")`.
  - `RunPathsTest.reportWithoutDotOrParent`: `new RunPaths(Path.of("a")).births()` equals `Path.of("a.births.csv.gz")`.
  - `FoundersTest.maxGenesWidensToTheLongestFounder`: a config with `--max-genes 8 --genes 24` gives `founders.engine().maxGenes() == 24`.
  - `FoundersTest.demoHasOneFeedbackGenome`: `Founders.create(RunConfig.parse(new String[0]))` has 1 genome equal to `GenomeFactory.demo()`, 4 genes long.
- [ ] **Step 2: Run** `./gradlew test --tests '*FoundersTest' --tests '*RunPathsTest'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the three pieces and use them in `Main`. Delete `Main.sibling`, `Main.obsDir`, `Main.demoGenome` and the second `createDirectories` block (the parent already exists by then).
- [ ] **Step 4: Run** the new tests, then `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice.
- [ ] **Step 6: Commit:** `refactor(sim): founders and run paths out of Main`.

### Task R2: Tick observers, run artifacts, Main in a dozen lines

**Files:**
- Create: `src/main/java/com/simolution/sim/TickObserver.java`, `SimulationRun.java`, `RunResult.java`, `RunArtifacts.java`, `RunConsole.java`, `ReplayCommand.java` (all in `com.simolution.sim`)
- Modify: `Main.java`, `bench/Bench.java`, `BirthLogWriter.java`, `MapFrameWriter.java` (rename `maybeFrame` to `observe`), `MetricsWriter.java` (rename `sample` to `observe`), `TimeSeriesReport.java` (`sample(int, snapshot)` becomes `observe(snapshot)`), `EventLogWriter.java`, `DynamicsObserver.java`, `run/RunDirectory.java` (rename `afterTick` to `checkpointIfDue`)
- Test: `src/test/java/com/simolution/sim/SimulationRunTest.java`, `RunArtifactsTest.java`; update the tests calling renamed methods

**Interfaces:**
- Consumes: `Founders`, `RunPaths` (R1).
- Produces:
  - `public interface TickObserver { void observe(KernelSnapshot snapshot) throws IOException; }`, implemented by `DynamicsObserver`, `TimeSeriesReport`, `BirthLogWriter`, `MapFrameWriter`, `EventLogWriter`, `MetricsWriter`. The digest printer, checkpointer and trace logger are adapters built once at setup.
  - `public record RunResult(long finalDigest, long elapsedMillis)`.
  - `public final class SimulationRun { public static RunResult execute(Kernel kernel, int ticks, TickObserver[] observers) throws IOException }`: each tick runs `kernel.tick()`, takes one snapshot, then calls the observers in array order.
  - `public final class RunArtifacts implements Closeable`, with `static RunArtifacts open(RunConfig config, Founders founders, Kernel kernel) throws IOException` (empty when there is no `--out`), `List<TickObserver> observers()` (birth log, map frames, event log, metrics, checkpoints, in that order), `void writeReport(String report, String timeSeriesCsv) throws IOException` and `close()`, which closes every writer once, catalog last.
  - `RunConsole`: `announceObservability(Path obsDir, int checkpointEvery)`, `announceObservabilityWritten(Path obsDir)`, `printReport(String report, long elapsedMillis)`, `printOutputPaths(RunPaths paths, boolean framesEnabled)`. Message text is copied verbatim from today's `Main`.
  - `ReplayCommand`: `static boolean requested(String[] args)`, `static void run(String[] args) throws IOException`. Arguments go in `record ReplayArgs(Path obsDir, int tick, Path snapshotOut, int threads)` with `snapshotOut` nullable.

- [ ] **Step 1: Write failing tests.**
  - `SimulationRunTest.observersRunInArrayOrderEveryTick`: two recording observers over a 3-tick kernel record `[a1, b1, a2, b2, a3, b3]` (observer name plus `snapshot.tick`).
  - `RunArtifactsTest.throwDuringRunStillLeavesReadableSideFiles`: open artifacts for a temp `--out`, observe 5 ticks, then simulate an exception by closing through try-with-resources from a thrown `IllegalStateException`. `new GZIPInputStream(Files.newInputStream(paths.births())).readAllBytes()` succeeds, and the catalog gz decompresses.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** Check first that `TimeSeriesReport` gets the same index as today: the old loop passes `i` and the kernel tick after `tick()` is `i + 1` for a fresh kernel, so `observe` uses `snapshot.tick - 1`. Build the observer array once, in the Global Constraints order. Main reads:
  ```java
  static void main(String[] args) throws IOException {
      if (ReplayCommand.requested(args)) { ReplayCommand.run(args); return; }
      RunConfig config = RunConfig.parse(args);
      Founders founders = Founders.create(config);
      StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.units());
      try (Kernel kernel = founders.newKernel(config.threads());
           RunArtifacts artifacts = RunArtifacts.open(config, founders, kernel)) {
          ... build observers, execute, summarize, render, print, write ...
      }
  }
  ```
  Stdout line order must match today. So `RunArtifacts.close()` is idempotent, and `main` calls it explicitly right after `SimulationRun.execute`, before printing the report. That keeps the "observability artifacts written" line where it is now, and try-with-resources still covers the exception path. `Main.main` stays at 25 lines or fewer. `Bench` uses `SimulationRun.execute` with no observers except the one it needs today.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice. Catalog, births and frames are in OUTPUT-CHECK, so an observer-order mistake shows up here.
- [ ] **Step 6: Commit:** `refactor(sim): tick observers and run artifacts, Main reads as the run`.

### Task R3: CLI parsing and the inflow mini-language

**Files:**
- Create: `src/main/java/com/simolution/sim/ArgCursor.java`, `RunOptions.java`, `GenomeSource.java`, `MapSampling.java`, `Observation.java`, `InflowSpecParser.java`
- Modify: `RunConfig.java`, `Founders.java`, `RunArtifacts.java`, `RunConsole.java`, `ReplayCommand.java`, `RunReport.java`, `MapFrameWriter.java` (constructor takes `MapSampling`), `bench/Bench.java`
- Test: `src/test/java/com/simolution/sim/ArgCursorTest.java`, `InflowSpecParserTest.java` (the inflow cases move here from `RunConfigTest`), `RunConfigTest.java`, `RunReportTest.java`

**Interfaces:**
- Produces:
  - `final class ArgCursor` (package-private): `ArgCursor(String[] args)`, `boolean hasNext()`, `String next()`, `String value(String flag)` (throws `IllegalArgumentException(flag + " needs a value")`), `int intValue(String flag)`, `long longValue(String flag)`, `double doubleValue(String flag)`.
  - `sealed interface GenomeSource permits GenomeSource.Demo, GenomeSource.Random`, with `record Demo()` and `record Random(int genesPerFounder)`.
  - `sealed interface MapSampling` with `Off`, `EveryTick`, `Spread(int frames)`, `Window(int fromTick, int toTick)` and `boolean enabled()`. Encode today's precedence between `--map-frames` and `--map-from/--map-to` exactly as `MapFrameWriter` applies it now; read it before writing the type.
  - `record Observation(boolean enabled, int checkpointEvery)` with `static final Observation OFF`.
  - `public record RunConfig(int founderCount, int ticks, GenomeSource genomes, boolean trace, Path reportPath, MapSampling mapSampling, Observation observation, EngineConfig engine, int threads, boolean digestEveryTick)`. `reportPath` is null without `--out`. `static RunConfig parse(String[] args)` reads `RunOptions.read(args)`, validates, then builds via `demo(options)` or `population(options)`.
  - Named defaults in `RunConfig`, each equal to today's literal: `DEMO_TICKS`, `DEFAULT_TICKS`, `DEFAULT_GENES`, `DEFAULT_CHECKPOINT_EVERY`, `DEFAULT_CYCLE_PERIOD`, `DEFAULT_CYCLE_RADIUS`, `DEFAULT_CYCLE_PEAK`, plus the default founder count when only `--genes` is given.
  - `public final class InflowSpecParser { public static InflowConfig parse(String spec) }`. Tokens go into a `Params` holder once (last value wins, unknown keys rejected with today's message), then one method per source: `disk`, `rect`, `points`, `noise`, `ring`. JSON component names on `InflowSource` records (`m0`, `m1`, `motionPeriod`) stay.

- [ ] **Step 1: Write failing tests.**
  - `ArgCursorTest.missingValueNamesTheFlag`: `RunConfig.parse(new String[]{"--units"})` throws `IllegalArgumentException` with message `--units needs a value`.
  - `InflowSpecParserTest.lastRepeatedKeyWins`: `InflowSpecParser.parse("disk r=5 r=9").sources()[0]` has radius 9.0.
  - `InflowSpecParserTest.unknownKeyIsRejectedByName`: `InflowSpecParser.parse("disk wat=1")` throws with today's exact message (copy it from the current parser).
  - The existing `RunConfigTest` inflow cases move to `InflowSpecParserTest` unchanged except for the call target.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** Update every `RunConfig` call site (`Founders`, `RunArtifacts`, `RunConsole`, `RunReport`, `Bench`, the `RunReportTest` constructors at lines 34, 47, 58, 79). `Bench` builds `RunConfig` directly instead of formatting and re-parsing CLI strings. `--threads` defaults through one helper, shared with `ReplayCommand` and `Bench`.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice. The `errors` case messages must match exactly.
- [ ] **Step 6: Commit:** `refactor(sim): readable CLI parsing, inflow spec parser of its own`.

### Task R4: EngineConfig in domain records, JSON kept flat

**Files:**
- Modify: `src/main/java/com/simolution/kernel/config/EngineConfig.java`, `ConfigJson.java`, `ChemicalSpec.java` (add `SINK = -1`)
- Create: `src/main/java/com/simolution/kernel/config/Checks.java` (the `nonNegative/positive/unitInterval` helpers)
- Modify (call-site edits only): every reader of an `EngineConfig` component in `kernel/`, `sim/`, `bench/`, and tests. Inside phase and kernel files, change only the lines that read config.
- Test: `src/test/java/com/simolution/kernel/config/EngineConfigTest.java`, `ConfigJsonTest.java` (new), `src/test/resources/engine-config-defaults.json` (new)

**Interfaces:**
- Produces:
  - Records in `kernel.config`: `World(int worldWidth, int maxGenes)`, `Founder(double initialEnergy, double initialMass)`, `Metabolism(double basalCost, double costPerPropagation, double maintenancePerMass, double agingCost)`, `Harvest(double capacityPerConnection, double halfSaturation, double massExponent)`, `Motility(double fixedCost, double costPerMass, double deadzone)`, `Growth(double max, double halfSaturation, double yield)`, `Sensing(double selfEnergyScale, double selfMassScale)`, `Reproduction(double buildCost, double buildCostPerGene)`, `Mutation(double weightRate, double structRate, double duplicationRate, double deletionRate)`. Each validates its own fields in its compact constructor with today's error text.
  - `public record EngineConfig(long seed, World world, List<ChemicalSpec> chemicals, Founder founder, Metabolism metabolism, Harvest harvest, Motility motility, Growth growth, Sensing sensing, Reproduction reproduction, Mutation mutation)`.
  - `static EngineConfig defaults(long seed, int worldWidth, int maxGenes)` (renamed from `v5Defaults`, same values). Withers: `withSeed`, `withMaxGenes`, `withChemicals`, `withResourceInflow` (renamed from `withChemical0Inflow`), `withMetabolism`, `withReproduction`, `withMutation`, plus `Metabolism.withBasalCost`. Add no other withers.
  - `ConfigJson` writes and reads through a private `record FlatEngineConfig(...)` holding every current component, in today's order and with today's names. `read` becomes `rejectUnknownKeys(json)` then `readStrict(json)`.

- [ ] **Step 1: Capture the wire format before changing anything.**
  - Write `ConfigJson.write(EngineConfig.v5Defaults(42L, 100, 32))` to `src/test/resources/engine-config-defaults.json`.
  - Write a checkpoint of a small kernel (W=8, 10 random founders, seed 11, 50 ticks, encoded with `CheckpointCodec`) to `src/test/resources/checkpoint-pre-readability.ckpt`, and record its state digest as a constant in the test.
- [ ] **Step 2: Write failing tests.**
  - `ConfigJsonTest.defaultsSerialiseExactlyAsBefore`: `ConfigJson.write(EngineConfig.defaults(42L, 100, 32))` equals the resource file's content.
  - `ConfigJsonTest.checkpointFromBeforeTheRefactorStillLoads`: reading `checkpoint-pre-readability.ckpt` at 1 thread gives a kernel whose `stateDigest()` equals the recorded constant.
  - `ConfigJsonTest.exampleConfigRoundTrips`: `ConfigJson.write(ConfigJson.read(Files.readString(Path.of("examples/baseline.json"))))` read back equals the first read.
  - `EngineConfigTest`: the existing rejection tests keep their expected messages. The 27-argument constructor calls in `EngineConfigTest:50`, `CheckpointCodecTest:180` and `ReproductionClaimTest:35` become one-line wither chains.
- [ ] **Step 3: Run them.** Expected: FAIL to compile (new names).
- [ ] **Step 4: Implement** the records, the flat wire record and the renames across all call sites (`v5Defaults` has 88 uses).
- [ ] **Step 5: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 6: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice (`run.json` run ids and checkpoint bytes prove the config hash is unchanged).
- [ ] **Step 7: Commit:** `refactor(config): EngineConfig grouped by domain, JSON wire format unchanged`.

### Task R5: Gene layout, decoder, builder, slot compiler

**Files:**
- Create: `src/main/java/com/simolution/kernel/genome/GeneLayout.java`
- Modify: `GeneDecoder.java`, `GeneBuilder.java`, `SlotCompiler.java`, `kernel/layout/NodeLayout.java` (`toJson` reads `GeneLayout` constants and `NOMINAL_MAX_WEIGHT`)
- Test: `src/test/java/com/simolution/kernel/genome/GeneDecoderTest.java`, `GeneLayoutTest.java` (new)

**Interfaces:**
- Produces:
  - `final class GeneLayout` (package-private): `SOURCE_KIND_SHIFT = 31`, `SOURCE_ID_SHIFT = 24`, `DESTINATION_KIND_SHIFT = 23`, `DESTINATION_ID_SHIFT = 16`, `ID_MASK = 0x7F`, `WEIGHT_MASK = 0xFFFF`, plus `boolean sourceIsInternal(int gene)`, `int sourceId(int gene)`, `boolean destinationIsAction(int gene)`, `int destinationId(int gene)`, `short weightRaw(int gene)`, `int pack(boolean sourceInternal, int sourceId, boolean destinationAction, int destinationId, short weightRaw)`.
  - `GeneDecoder`: `MAX_WEIGHT` becomes `NOMINAL_MAX_WEIGHT`, `WEIGHT_MULTIPLIER` becomes `WEIGHT_PER_RAW_UNIT` (same expression, same double), `sourceLocal`/`destinationLocal` become `sourceNodeIndex`/`destinationNodeIndex`. `INSTANCES_PER_TYPE` stays (stage 6 removes it).
  - `GeneBuilder`: same public fluent API; stages become records that call `GeneLayout.pack`.
  - `SlotCompiler.compile(state, unit)` reads as: `placeSumConnections`, `placeMulConnections`, set the four range fields, `countHarvestTransporters`, `mulInDegreeIncludingJunk`, sharing `storeConnection`. Junk pruning stays named `isStored(src)`, with the two-line signed-zero comment.

- [ ] **Step 1: Write failing tests.**
  - `GeneLayoutTest.packThenReadReturnsEveryField`: for 10,000 `Random(7)` tuples, `pack` then each accessor returns the inputs. Ids are masked to 7 bits.
  - `GeneLayoutTest.minRawWeightDecodesJustBeyondMinusFour`: a gene with raw weight -32768 decodes to `-32768 * GeneDecoder.WEIGHT_PER_RAW_UNIT`, which is less than -4.0.
  - `GeneDecoderTest.anyRandomIntDecodesToConnection`: the bound becomes `Math.abs(w) <= 32768 * GeneDecoder.WEIGHT_PER_RAW_UNIT`, using `assertTrue`, and the trivial non-null assertion goes. The literal reference model in `GeneDecoderTest` keeps raw shifts; it must not use `GeneLayout`.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK** (the `layout.json` sidecar is included). Expected: `IDENTICAL` twice.
- [ ] **Step 6: Commit:** `refactor(genome): named gene layout, slot compiler in steps`.

### Task R6: Inflow sources and the compiled field

**Files:**
- Modify: `src/main/java/com/simolution/kernel/config/CompiledInflowField.java`, `InflowSource.java`, `InflowConfig.java`, `ValueNoise.java`, `InflowSpecParser.java` (R3), and the tests using `sources()` arrays
- Test: `src/test/java/com/simolution/kernel/config/InflowConfigTest.java`

**Interfaces:**
- Produces:
  - `InflowConfig.sources()` returns `List<InflowSource>` (the same JSON array). The hand-written `equals`/`hashCode` go.
  - `InflowSource.at` becomes `capAt`. `Disk` gets non-component accessors `orbitRadiusFraction()`, `driftX()` and `driftY()`, plus private `centreX(tick, worldWidth)` and `centreY(tick, worldWidth)`. A shared `static double pulse(double peak, int tick, int periodTicks, int phaseTicks)` replaces the three `peak * triangle(...)` copies. `Rect.capAt` splits into `coversStaticBox` and `coversDriftingBand`. `Points` keeps `int[] cells`.
  - `CompiledInflowField`: the constructor assigns fields, then `dynamic = select(sources, false)` and `baked = bake(select(sources, true))`; `bake` returns null when there are no static sources.
  - `ValueNoise.at` uses `alongX(...)` with the same lerp nesting. The hash primes become `X_PRIME`, `Y_PRIME`, `Z_PRIME`.

- [ ] **Step 1: Capture cap values before changing anything.** For one source of each kind (disk static, disk orbit, disk drift, rect static, rect drifting, points, noise animated, ring) on W=40, write `at(x, y, tick, 40)` for 64 fixed `(x, y, tick)` triples (from `Random(5)`) to `src/test/resources/inflow-caps-pre-readability.txt`, using `Double.toHexString`.
- [ ] **Step 2: Write failing tests.**
  - `InflowConfigTest.capValuesAreBitIdenticalToBefore`: the same sources and triples, through `capAt`, reproduce every line of the resource file exactly.
  - `InflowConfigTest.jsonRoundTripIsUnchangedForEverySourceKind`: a config with one source of each kind survives `ConfigJson.write` then `read` as an equal record. Its JSON contains the keys `m0`, `m1` and `motionPeriod`, and no key named `orbitRadiusFraction` or `driftX`.
- [ ] **Step 3: Run them.** Expected: FAIL to compile (`capAt`, `List` signature).
- [ ] **Step 4: Implement.** Keep every float operation in its current order.
- [ ] **Step 5: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 6: BASELINE-CHECK and OUTPUT-CHECK** (the `inflow` and `cycle` cases exercise every source). Expected: `IDENTICAL` twice.
- [ ] **Step 7: Commit:** `refactor(config): inflow sources read as geometry and pulse`.

### Task R7: Writers

**Files:**
- Create: `src/main/java/com/simolution/sim/FrameSampler.java`, `SlotGenomeCache.java`, `ChunkedFrameStream.java`, `ResourceLevels.java`
- Modify: `MapFrameWriter.java`, `EventLogWriter.java`, `MetricsWriter.java`, `BirthLogWriter.java`, `GenomeCatalogWriter.java`, `SnapshotDump.java` (uses `ResourceLevels`), `run/LatestFrame.java` (field `genomeId` becomes `genomeHash`), `RunArtifacts.java`
- Test: `src/test/java/com/simolution/sim/MapFrameWriterTest.java`, `ObservabilityTest.java`, `FrameSamplerTest.java` (new)

**Interfaces:**
- Consumes: `MapSampling` (R3), `TickObserver` (R2).
- Produces:
  - `FrameSampler`: `static FrameSampler of(MapSampling sampling, int totalTicks)` and `boolean samples(int tick)`.
  - `SlotGenomeCache`: `SlotGenomeCache(int slots, int maxGenes, GenomeCatalogWriter catalog)` and `int catalogIdFor(KernelSnapshot snapshot, int slot)`. It assigns ids in the same order as today.
  - `ChunkedFrameStream`: owns the 256-frame zstd chunks and the `.frames.idx` lines. It has `StringBuilder chunkText()`, `void frameWritten(int tick)` (seals when full) and `close()`.
  - `ResourceLevels(double capacity)` with `int level(double value)`, the 0-9 quantizer shared by `MapFrameWriter` and `SnapshotDump`.
  - `MapFrameWriter.observe` reads: `if (!sampler.samples(tick)) return;` `appendFrameHeader`, `appendResourceRuns`, `appendLivingUnits`, `stream.frameWritten(tick)`.
  - `EventLogWriter(Writer out, int slotCount, int founderCount, int[] founderCells, double founderEnergy)`, whose `observe` reads `tallyLivingLineages`, `diffSlots`, `emitExtinctions`, `rememberCurrentState`. `MetricsWriter(Writer out, int founderCount)` (the unused `maxUnits` goes), whose `observe` reads `tally`, `auditError`, `appendRow`, with the audit addition order unchanged.

- [ ] **Step 1: Write a failing test** `FrameSamplerTest.matchesTodaysSchedule`: for `Spread(5)` over 10 ticks, the sampled ticks are `[2, 4, 6, 8, 10]`. `Window(3, 5)` samples exactly 3, 4, 5. `EveryTick` samples every tick, and `Off` none.
- [ ] **Step 2: Run it.** Expected: FAIL.
- [ ] **Step 3: Implement.** Class Javadoc that narrates report history goes; keep one sentence of purpose and leave the wire format to `docs/specs/report-v14.md`.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice.
- [ ] **Step 6: Commit:** `refactor(sim): writers split by job`.

### Task R8: Reports, summary, analysis, run directory

**Files:**
- Modify: `RunReport.java`, `DynamicsSummary.java`, `DynamicsObserver.java`, `StructuralAnalyzer.java`, `StructuralStats.java`, `SnapshotDump.java`, `TimeSeriesReport.java` (renamed to `TimeSeriesSampler`)
- Create: `src/main/java/com/simolution/sim/run/RunDirectoryWriter.java`, `RunDirectoryReader.java` (replacing `RunDirectory.java`)
- Test: `RunReportTest.java`, `DynamicsObserverTest.java`, `SnapshotDumpTest.java`, `run/RunLifecycleTest.java`

**Interfaces:**
- Produces:
  - `DynamicsSummary(Activity activity, Population population, EnergyLedger energy, Mass mass, long stateDigest)`, with nested records holding today's fields. `ticks` goes (it is never read). One vocabulary: `initialEnergy`, `initialResource`, `initialMass`. `energyAuditError()` keeps today's addition order. `static DynamicsSummary empty(long stateDigest)`.
  - `DynamicsObserver.summarize(long finalStateDigest)` reads `if (lastSnapshot == null) return DynamicsSummary.empty(...)`, then `summaryOf(lastSnapshot, digest)`.
  - `RunReport.render` reads `appendHeader`, `appendStructure`, `appendDynamics`, `appendReproduction`, `appendEnergy`, `appendMass`, with `line(out, key, value)` and `section(out, title)` helpers. Derived values move to `StructuralStats.connectionsJunkTouching()` and `DynamicsSummary.finalMassMean()`.
  - `StructuralAnalyzer.analyze` reads `WeightTally`, then per unit `wiringOf(connections, from, to)` returning `UnitWiring(meaningful, reachable, randWired, duplicates)`. `StructuralStats.perUnitConnections` and `perUnitMeaningful` are deleted (no readers).
  - `SnapshotDump.render` reads `appendHeader`, `appendHeatmap`, `appendUnits`, `appendWires`.
  - `RunDirectoryWriter`: `create`, `createBranch`, `checkpointIfDue`, `runId`. `RunDirectoryReader.seek(Path obsDir, int tick, int threads)` reads `latestCheckpointTickAtOrBefore`, `loadCheckpoint`, `stepOrClose`. The run manifest JSON is built by one `runManifestJson(String runId, Parent parentOrNull)` method.

- [ ] **Step 1: Write a failing test** `RunReportTest.sectionsAppearInTodaysOrder`: render a 50-tick run and check the section headers appear in today's order. Update the existing `RunReportTest` and `DynamicsObserverTest` constructors to the grouped records.
- [ ] **Step 2: Run it.** Expected: FAIL to compile.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK** (the `replay` case covers the reader). Expected: `IDENTICAL` twice.
- [ ] **Step 6: Commit:** `refactor(sim): reports in sections, grouped summary, run directory split`.

### Task R9: Test helpers and test comments

**Files:**
- Create: `src/test/java/com/simolution/support/Worlds.java`, `Genes.java`, `EnergyAudit.java`, `Ticks.java`, `FrameHarness.java`, `CheckpointBytes.java`
- Modify: every test under `src/test/java` that the helpers or the comment rule touch

**Interfaces:**
- Consumes: `EngineConfig.defaults`, `withResourceInflow` (R4), `GenomeFactory.demo()` (R1).
- Produces (test scope only):
  - `Worlds.kernel(int worldWidth, int[]... genomes)` (founders in cells 0..n-1, `defaults(0L, worldWidth, 32)`, 1 thread), `Worlds.kernelAt(int worldWidth, int[][] genomes, int... cells)`, `Worlds.randomKernel(long seed, int founders, int genes, EngineConfig config)`, `Worlds.foodRich(int worldWidth, int maxGenes)`.
  - `Genes.sensorToInternal(int sensor, int internal, int weightRaw)`, `sensorToAction`, `internalToInternal`, `internalToAction`, `weight(int raw)`, and `FEEDBACK_LOOP` (equal to `GenomeFactory.demo()`).
  - `EnergyAudit.assertConserved(KernelSnapshot s, double tolerance)` and `EnergyAudit.totalEnergy(KernelSnapshot s)`.
  - `Ticks.until(Kernel kernel, int maxTicks, BooleanSupplier done)`, which returns the ticks run, or -1.
  - `FrameHarness.forWorld(int width)` with `ticks(int)`, `sampling(MapSampling)`, `run()`, `framesText()`, `tickLines()`, `idx()`, `catalog()`.
  - `CheckpointBytes.of(Kernel kernel)` and `CheckpointBytes.restore(byte[] bytes, int threads)`.

- [ ] **Step 1: Add the helpers** and convert `EnergyTest`, `MovementTest`, `MulNodeTest`, `BiomassTest`, `MultiUnitKernelTest`, `KernelVerticalSliceTest`, `IndelTest`, `MapFrameWriterTest`, `CheckpointCodecTest`, `RunLifecycleTest`, `WorldStateScalarsTest`, `DynamicsObserverTest`, `RunReportTest` to use them. The test count before and after must be equal or higher; record both numbers in the report.
- [ ] **Step 2: Split two-behaviour tests:** `EnergyTest.intakeSaturatesAndDivergentHarvesterImplodes` becomes `intakeNeverExceedsTheTransporterCeiling` and `divergentHarvesterEventuallyDies`. Rename `MulNodeTest.specSliceFeedbackLoopStaysAlive` to `feedbackThroughMulKeepsSignalAlive`, `ReproductionClaimTest.skipSamplingRate` to `geometricGapFlipsBitsAtTheConfiguredRate`, and `DeterminismMatrixTest.tinyWorlds` to `parallelMatchesSerialInDegenerateWorlds`.
- [ ] **Step 3: Apply the comment rule to tests:** contract references go to `@DisplayName("contract-vN §M: ...")`, number explanations become named constants, and every other non-marker comment and test-class Javadoc goes. Prose after a marker (`// arrange: ...`) is cut back to the bare marker.
- [ ] **Step 4: Run** `./gradlew test`. Expected: PASS, with the same or a higher test count.
- [ ] **Step 5: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice.
- [ ] **Step 6: Commit:** `test: shared world and gene helpers, tests read as specs`.

### Task R10: Docs follow the code

**Files:**
- Modify: `AGENTS.md` (Architecture tree for `sim/`, the new classes, `defaults` instead of `v5Defaults`), `README.md` if it names a renamed class
- Create: `docs/decisions/0047-readability-refactor.md` (context, decision, rejected: merging the audit formulas, a test-only `EngineConfig` builder, deleting `LatestFrame`)

- [ ] **Step 1: Update the docs.** Write the ADR in 5 to 15 lines.
- [ ] **Step 2: Run** `grep -rn "v5Defaults\|maybeFrame\|afterTick\|TimeSeriesReport\|RunDirectory\b" AGENTS.md README.md docs/specs docs/decisions src`. Expected: no hits in `src`; hits in docs only where they describe history.
- [ ] **Step 3: BASELINE-CHECK and OUTPUT-CHECK.** Expected: `IDENTICAL` twice.
- [ ] **Step 4: Commit:** `docs: architecture and ADR for the readability refactor`.
