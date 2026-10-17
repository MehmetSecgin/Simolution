# Readability audit: Main + sim/ + sim/run/ + bench/ (commit b4c95c1, read-only)

All paths under `src/main/java/com/simolution/`. Line numbers are from the files as read.

## Ranked by reading payoff

| # | Item | Where | Payoff |
|---|------|-------|--------|
| 1 | Main.main = setup + writer wiring + loop + shutdown + console, 150 lines | Main.java:36-185 | biggest |
| 2 | RunConfig.parse (118 lines, 20 nullable locals) + inflow DSL parser living in the same record | RunConfig.java:44-161, 194-293 | very high |
| 3 | Founder source modelled as `demo` boolean + `genesPerUnit=0` + magic `4` | RunConfig.java:113-121, Main.java:43-45, RunReport.java:30-31 | high |
| 4 | MapFrameWriter does 4 jobs (sampling policy, row format, genome cache, zstd chunk container) | MapFrameWriter.java:122-174 + fields 61-87 | high |
| 5 | EventLogWriter.observe/ctor: two state machines interleaved in one loop, 3 inline comments | EventLogWriter.java:56-147 | high |
| 6 | RunReport.render 60 lines + DynamicsSummary 18 positional args | RunReport.java:18-77, DynamicsSummary.java:3-22, DynamicsObserver.java:50-78 | medium-high |
| 7 | StructuralAnalyzer.analyze 70 lines, mixes global tallies and per-unit wiring; dead per-unit arrays | StructuralAnalyzer.java:18-87 | medium |
| 8 | RunDirectory = writer + static reader in one class; `seek` 37 lines, stringly JSON | run/RunDirectory.java:40-119 | medium |
| 9 | Cross-file duplication (alive loops, quantizer, audit, path helpers, `emit` blocks) | many | medium |
| 10 | Dead/legacy code and history-narrating Javadoc | see section 5 | medium |
| 11 | Package layout `sim` + `sim.run` hides the story | see section 6 | medium |
| 12 | Bench duplicates the tick loop | bench/Bench.java:50-73 | low |

## 1. Methods over ~25 lines

| Method | Lines | What it does / why hard | Split (in call order) |
|---|---|---|---|
| `Main.main` | 36-185 (150) | Dispatch replay, build genomes, compute maxGenes, open 3-7 writers, build kernel, open obs dir, tick loop with 8 optional sinks, close writers by hand, print report, write report, print paths. `startNanos`/`finalDigest` declared uninitialised outside try. Writers opened before the try, so a throw leaks them. | See sketch below |
| `Main.runReplay` | 194-225 (32) | Own arg loop (dup of RunConfig style), seek, render, write-or-print. | `ReplayArgs.parse(args)` -> `ReplayArgs(obsDir, tick, snapshotOut, threads)`; `renderSnapshotAt(args)`; `emit(dump, snapshotOut, tick)` |
| `Main.demoGenome` | 245-269 (25) | Builds the 4-gene demo. Belongs with the other genome source. | Move to `GenomeFactory.demo()` (and expose `DEMO_GENE_COUNT`) |
| `RunConfig.parse` | 44-161 (118) | 20 `Integer/Long/Double/String null` locals, switch with `args[++i]`, 4 validations, thread default, two build paths (demo/full) with defaults sprinkled (`ticks == null ? 10`, `? 1000`, `? 2000` x2, `? 32`, `? 6.0`...). | `Options.read(args)` (ArgCursor) -> `options.validate()` -> `config = options.isDemo() ? demo(options) : population(options)`; `engineForPopulation(options)`; `requireFoundersFit(engine, founders)`; `withInflow(engine, options)`. Defaults become named constants. |
| `RunConfig.parseInflowSpec` | 194-221 (28) | Splits segments, handles `combine=`/`baseline=` segment, delegates sources. | Move whole DSL to `InflowSpecParser` (own file): `parse(spec)`; `applySettingsSegment(tokens)`; `parseSource(kind, tokens)` |
| `RunConfig.parseSource` | 223-293 (71) | 22 local defaults for every source kind, one 30-case switch, then a second switch picks which locals matter. `m0` is orbit radius for ORBIT but dx for DRIFT, `m1` is dy. | Tokens -> `Params` (map, last-wins, unknown-key check against one known set) once; then `disk(p)`, `rect(p)`, `points(p)`, `noise(p)`, `ring(p)`; `motion(p)` returns `MotionSpec(kind, a, b, period)`; reads like the DSL docs: `p.dbl("fx", 0.5)` |
| `MapFrameWriter.maybeFrame` | 122-174 (53) | Sample gate, header, RLE, lazy cache alloc, per-slot living loop with cache hit/miss, row append, chunk bookkeeping, seal. | `if (!sampler.samples(tick)) return;` `appendFrameHeader(tick)`; `appendResourceRle(field)`; `appendLivingUnits(snapshot)` (uses `genomeIds.idFor(slot, snapshot)`); `noteFrame(tick)`; `sealIfFull()` |
| `MapFrameWriter.appendResourceRle` | 200-226 (26) | The run-append block is written twice. | `appendRun(level, length)`; `quantize(value)` shared with SnapshotDump |
| `EventLogWriter` ctor | 56-90 (35) | 14 parallel arrays + founder seeding with a comment. | `seedFounderState(founderCells, founderEnergy)`; group arrays (see below) |
| `EventLogWriter.observe` | 92-147 (56) | Per-slot: lineage tally, birth match, death emit, birth emit; then extinction pass; then roll-forward. 3 comments name the three phases. | `tallyLivingLineages(snapshot)`; `diffSlots(snapshot)` (emit death/birth); `emitExtinctions(tick)`; `rememberCurrentState(snapshot)` |
| `StructuralAnalyzer.analyze` | 18-87 (70) | Weight stats, duplicate-pair detection, meaningful count, rand-wired, adjacency + BFS per unit, packaging 10 values. | `WeightTally` (add(weight); mean/max/positiveFraction); per unit `wiringOf(connections, from, to)` returning `UnitWiring(meaningful, reachable, randWired, duplicates)`; assemble stats |
| `StructuralAnalyzer.sensorReachesAction` | 103-127 (25) | BFS, fine length; comment explains junk. | keep; name `isActionReachableFromAnySensor`; replace Javadoc by `adjacency` being named `meaningfulEdges` |
| `DynamicsObserver.summarize` | 50-78 (29) | 12 `last == null ? 0 : ...` ternaries then an 18-arg ctor. | `if (last == null) return DynamicsSummary.empty(ticksObserved, digest);` `return summaryOf(last, digest)` |
| `RunReport.render` | 18-77 (60) | One run of ~55 `out.append(..).append('\n')` over 6 sections. Computes derived values inline (junk-touching, mass mean). | `appendHeader`, `appendStructure`, `appendDynamics`, `appendReproduction`, `appendEnergy`, `appendMass`; helpers `line(out, key, value)` and `section(out, title)`; move derived values to `StructuralStats.connectionsJunkTouching()` and `DynamicsSummary.finalMassMean()` |
| `RunDirectory.seek` | 83-119 (37) | Scan dir for best checkpoint, load, verify tick, step with close-on-fail. Two copies of "close kernel then throw". | `latestCheckpointTickAtOrBefore(ckptDir, tick)`; `loadCheckpoint(path, expectedTick, threads)`; `stepOrClose(kernel, ticks)` |
| `SnapshotDump.render` | 29-76 (48) | Header, heatmap, units, wires in one body. | `appendHeader`, `appendHeatmap`, `appendUnits`, `appendWires` |
| `MetricsWriter.sample` | 39-82 (44) | Aggregate loop, derive deaths, audit formula, row append. | `tally(snapshot)` (population, energyTotal, lineagesAlive into fields or a small reusable accumulator); `auditError(snapshot, energyTotal)`; `appendRow(...)` |
| `Bench.runOnce(config,name)` | 54-73 (20) | OK length, but duplicates Main loop. | see item 12 |

### Main.main after refactor (sketch)

```java
static void main(String[] args) throws IOException {
    if (ReplayCommand.requested(args)) {
        ReplayCommand.run(args);
        return;
    }
    RunConfig config = RunConfig.parse(args);
    Founders founders = Founders.create(config);
    StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.units());

    try (RunArtifacts artifacts = RunArtifacts.open(config, founders);
         Kernel kernel = founders.newKernel(config)) {
        TickObserver[] observers = artifacts.observersFor(kernel, config);
        RunResult result = SimulationRun.execute(kernel, config.ticks(), observers);
        artifacts.finish();
        RunConsole.printReport(config, structure, result);
        artifacts.writeReport(RunReport.render(config, structure, result.summary()));
    }
}
```
Supporting pieces:
- `Founders` (record): `genomes`, `cells`, `engine` (with `withMaxGenes` already applied), `connections()`, `newKernel(config)`. Absorbs Main lines 43-57, 91.
- `RunPaths(Path report)` record: `layoutJson()`, `catalog()`, `births()`, `framesZst()`, `framesIdx()`, `timeSeries()`, `obsDir()`. Replaces `sibling`/`obsDir` (Main.java:227-243, identical bodies) and 6 repeated suffix literals (lines 72,75,79,83,86,169,173,178,179).
- `interface TickObserver { void observe(KernelSnapshot s) throws IOException; }`; `DynamicsObserver`, `TimeSeriesReport`, `BirthLogWriter`, `MapFrameWriter`, `EventLogWriter`, `MetricsWriter`, trace logger, digest printer, checkpointer all become implementations or one-line adapters. Replaces the 8 `if (x != null)` blocks at Main.java:116-133.
- `SimulationRun.execute` is the 8-line tick loop (also used by Bench). Returns `RunResult(summary, elapsedMillis)`.
- `RunArtifacts implements Closeable`: owns open/close of catalog, birth log, map writer, event log, metrics, run directory. Empty when `--out` absent. Closes all in `close()` so a thrown tick does not leave truncated gzip/zstd files.
- `RunConsole`: the 3 print blocks at Main.java:107-108, 149-150, 154-159, 167-183.

Risks (determinism):
- **Observer order must stay as today** (observer, digest print, timeSeries, birthLog, mapWriter, eventLog, metrics, runDirectory, trace). `birthLog` and `mapWriter` both call `catalog.idOf`, so reordering changes catalog ids and therefore `catalog.gz`, `births.csv.gz`, `frames.zst` bytes.
- `TimeSeriesReport.sample(i, ...)` takes the 0-based loop index (TimeSeriesReport.java:29). As an observer it needs `snapshot.tick - 1`; confirm kernel tick equals i+1 for a fresh kernel (EventLogWriter assumes first observed tick is 1).
- `RunDirectory.afterTick(kernel)` takes the kernel, not a snapshot: adapter captured once at setup, no per-tick allocation.
- Interface dispatch over an array: no allocation, no streams. Fine for the per-tick rule.
- Wall-clock timing currently covers loop + writer close (Main.java:111-152); console-only so no file impact.
- Report file is written after console print; keep.
- Main.java:161-165 re-creates the parent dir of `--out`, already done at 67-71: delete the second one.

## 2. Names that need a comment

| Where | Now | Better |
|---|---|---|
| RunConfig:16 | `units` (founder count), but `maxUnits` in Main:56 means slot count (W*W) | `founderCount`; `slotCount` |
| RunConfig:18 | `genesPerUnit` (0 in demo) | `founderGeneCount`, or drop via `GenomeSource` (item 3) |
| RunConfig:19-20 | `trace`, `demo` booleans; demo forces trace=true (RunConfig:118) | `GenomeSource.DEMO/RANDOM`; trace default set in `demo(options)` |
| RunConfig:21 | `String outPath` nullable | `Path reportPath` nullable, or `Optional<Path>`; Main calls `Path.of(config.outPath())` 3 times |
| RunConfig:22,25,26 | `mapFrames` (<0 off, 0 every tick, N frames), `mapFrom`/`mapTo` (-1 off) | `MapSampling` sealed type: `Off`, `EveryTick`, `Spread(frames)`, `Window(from,to)`; `enabled()` replaces the repeated `mapFrames() >= 0 \|\| mapFrom() >= 0` (Main:82, 177) |
| RunConfig:24 | `checkpointEvery` | `checkpointIntervalTicks` |
| RunConfig:246-248 | `fx fy fx1 fy1 m0 m1 thr anim` | `x0 y0 x1 y1`; `m0/m1` -> `MotionSpec(kind, a, b, period)`; `thr` -> `threshold`; `anim` -> `animationPeriod` |
| RunConfig:182-196 | `parseInflowSpec` | `InflowSpecParser.parse` |
| Main:194 | `obs`, `at` (boxed Integer) | `obsDir`, `tick` (int) |
| Main:36 | `maxGenes` local reassigned in loop, then `engine` | `Founders.engine` |
| MapFrameWriter:122 | `maybeFrame` | `recordIfSampled` or `observe` |
| MapFrameWriter:70 | `chunk` (StringBuilder) | `chunkText` |
| MapFrameWriter:87 | `cacheStride` == maxGenes | delete, use `maxGenes` |
| MapFrameWriter:85 | `hasLast[]` | delete: init `lastCount` to -1 |
| MapFrameWriter:99 | `targetFrames`, `windowFrom/To` | `FrameSampler` object |
| LatestFrame:27 vs MapFrameWriter:151 | both called `genomeId`: 64-bit content hash vs int catalog id | `genomeHash` vs `catalogId` |
| EventLogWriter:40 | `maxUnits` | `slotCount` |
| EventLogWriter:109,111 | `l`, `li` | `lineageId`, `lineageIndex` |
| EventLogWriter:119 | `born` | `slotReceivedBirth` |
| EventLogWriter:48 | `birthTick[]` | `birthTickBySlot` |
| MetricsWriter:30 | param `maxUnits` is unused | delete |
| EventLogWriter:58 | takes whole `EngineConfig` for one value (`initialEnergy`) | take `double founderEnergy` |
| DynamicsObserver:20 | `last` (aliases live kernel arrays; Javadoc line 47 warns "call before the kernel ticks again") | `lastSnapshot`; make the summary eager: call `summarize` inside the run before close, or copy the 8 scalars at `observe` time |
| DynamicsObserver:50 | `summarize(stateDigest)` | `summarize(finalStateDigest)` |
| DynamicsSummary | `initialEnergyTotal`/`initialReservoir`/`creditedInitialMass` vs snapshot `creditedInitialEnergy`/`initialResourceTotal`: 3 names for the same quantities | one vocabulary: `initialEnergy`, `initialResource`, `initialMass` |
| DynamicsSummary:8 | `finalEnergySink` | `finalSink` (report key says `energy-sink`) |
| DynamicsSummary:5 | `ticks` is never read | delete |
| StructuralAnalyzer:32,42-43,89 | `cursor`, `srcLocal`, `dstLocal`, `endOfUnit`, `c` | `unitStart`, `sourceNode`, `destinationNode`, `firstIndexAfterUnit`, `connection` |
| StructuralAnalyzer:38 | `adjacency` | `meaningfulEdges` (removes the Javadoc at 98-102) |
| TimeSeriesReport | called "Report" but is a streaming sampler; `sample(int i, ...)` | `TimeSeriesSampler`; `sample(snapshot)` |
| RunDirectory:55 | `open` (writes manifest + first checkpoint) | `writeFirstCheckpointAndManifest` |
| RunDirectory:74 | `afterTick` | `checkpointIfDue` |
| RunDirectory:84-100 | `best` | `latestCheckpointTick` |
| RunDirectory:40-53 | `parentJson` is a hand-built JSON string, `"null"` literal | `runManifestJson(runId, parent)` with a `Parent` record or null |
| Bench:34,50 | `row(name, world, units, ticks, threads)`, `runOnce(config)` is the warmup | `record Scenario(name, world, units, ticks)`; `warmUp(config)` |
| Main:34 | `MAX_TRACED_UNITS` | fine |
| kernel `EngineConfig.v5Defaults` used for a v6 engine (RunConfig:117,129) | | outside area; rename `defaults` |

## 3. Comments that exist because the code is unclear

| Comment | Where | Structural fix |
|---|---|---|
| "tick-0 state: founders alive at their scattered cells..." | EventLogWriter:78-80 | `seedFounderState(...)` |
| "peak membership and extinction edges..." | EventLogWriter:130 | `emitExtinctions(tick)` |
| "roll current state into the prev buffers..." | EventLogWriter:141 | `rememberCurrentState(snapshot)` |
| Javadoc "Call once per tick after observe; reads..." | MapFrameWriter:117-121 | `TickObserver` contract makes it implicit |
| Per-slot dedup Javadoc + "Lazily allocated on the first frame" | MapFrameWriter:77-82 | `SlotGenomeCache` class with ctor sizing (snapshot `energy.length` is W*W; Main:56 already assumes it; verify before allocating eagerly) |
| `targetFrames <= 0 means every tick` | MapFrameWriter:90-93 | `FrameSampler.everyTick() / spread(frames, totalTicks) / window(from,to)` |
| "BFS over meaningful-node edges only. Junk nodes..." | StructuralAnalyzer:98-102 | name `meaningfulEdges` |
| "Reads the last observed snapshot's live kernel arrays, so call it before the kernel ticks again" | DynamicsObserver:46-49 | summarize eagerly (temporal coupling goes away) |
| `--resource-cycle is sugar for...` in error text, `--map-from requires...` | RunConfig:97-103 | fine, user-facing |
| Javadoc narrating history: "report-v13 retired...", "is now/is gone/(report-v14)" | RunReport:5-11, MapFrameWriter:14-53, GenomeCatalogWriter:9-30, BirthLogWriter | history belongs in `docs/specs/report-v*.md` and ADRs, not the class; keep one sentence of purpose |
| "Founders are recorded in the checkpoints and run.json... there is no abiogenesis" | EventLogWriter:30 | delete (documents an absence) |
| "(trace logging is exempt)" etc. | n/a | n/a |
| Main:107 "observability layer writing to..." strings embed run instructions | Main:149-150 | `RunConsole` method with a name (`announceObservability`) |

Note: repo AGENTS.md allows Javadoc, global rule says none. If the owner wants "no comments", MapFrameWriter alone drops ~45 lines of Javadoc once the format is encoded in `FrameFormat` constants/method names, with the wire format living in `docs/specs/report-v14.md`.

## 4. Cross-cutting patterns

| Pattern | Where | Proposal |
|---|---|---|
| Arg parsing: `for (i) switch (args[i]) { case ... -> x = parse(args[++i]) }`, no bounds check (missing value = ArrayIndexOutOfBounds), nullable boxed locals, defaults scattered | RunConfig:67-92, Main.runReplay:199-207, Bench:23-26 | One `ArgCursor` (`next()`, `nextInt()`, `nextLong()`, `nextDouble()` with message "--ticks needs a value"); defaults as constants (`DEFAULT_TICKS=1000`, `DEMO_TICKS=10`, `DEFAULT_GENES=32`, `DEFAULT_CHECKPOINT_EVERY=2000`, `DEFAULT_CYCLE_PERIOD=2000`, `DEFAULT_CYCLE_PEAK=6.0`); nullable only where "not provided" matters (units, genes, seed, world, maxGenes, config, inflow) |
| Thread default `availableProcessors()` repeated | RunConfig:109, Main:198, Bench:27 | `Threads.defaultCount()` or single parse in ArgCursor helper |
| Writer lifecycle: writers created before `try`, closed manually after the kernel in `if (x != null)` blocks; catalog closed only inside `birthLog != null` block (hidden coupling); exceptions leave truncated gzip/zstd | Main:64-151 | `RunArtifacts implements Closeable` + try-with-resources; catalog is owned by it, closed last |
| Every writer has `Closeable` + an observe-like method with 4 different names (`observe`, `sample`, `maybeFrame`, `afterTick`) | all writers | `TickObserver` interface; `TickSink extends TickObserver, Closeable` |
| "For each living unit" loop `energy[slot] > 0.0` repeated 8x | DynamicsObserver:33,52,90,114; EventLogWriter:105-106; MapFrameWriter:146; MetricsWriter:47; SnapshotDump:51; TimeSeriesReport:36 | Add `KernelSnapshot.isAlive(slot)`, `population()`, `forEachLiving`-free helpers (no lambdas on hot path): `int population()`, `int genomeBase(slot)`. Kernel-side file, outside my area, flag for the other auditor |
| Population counted separately in 4 places | DynamicsObserver:31-36 and 88-96, MetricsWriter:49-50, TimeSeriesReport:36-38 | `snapshot.population()` |
| Distinct-lineages-alive computed twice with different bounds | DynamicsObserver:98-110 (bound `slots`), MetricsWriter:52-56 (bound `founderCount`) | one `LineageCensus` helper |
| Resource quantization (round 9*v/cap, clamp 0..9) copy-pasted | MapFrameWriter:205-210, SnapshotDump:39-44 | `ResourceLevels(capacity).level(value)` |
| Energy audit formula in two places | MetricsWriter:63-65 (signed), DynamicsSummary:32-34 (abs) | one `EnergyAudit` ... **RISK**: addition order differs today (held = energy+field+sink+mass vs energy+reservoir+mass+sink). Unifying changes FP rounding of `audit_error` / `energy-audit-error` and breaks byte-identity. Either keep each order inside the shared helper (two methods) or accept a baseline diff and say so |
| Same FP risk: `energyTotal` summed over living only (MetricsWriter:51) vs all slots (DynamicsObserver:53); identical only if dead energy is exactly 0 | | do not merge without checking |
| `line.setLength(0); line.append(...); out.write(line.toString()); out.flush();` x5 | EventLogWriter:159-170,176-184,189-196; BirthLogWriter:56-63; MetricsWriter:67-81 | tiny `LineWriter`/private `flushLine()`; for events `beginEvent(tick, "BIRTH")` |
| Birth list walked twice in two writers | BirthLogWriter:43-48, EventLogWriter:101-127 | `snapshot.birthCount()/birthChild(i)/birthParent(i)` accessors instead of reaching into `snapshot.state.*` (kernel-side) |
| Wide positional constructors | `RunConfig` 14 args (5 adjacent ints: units/ticks/genes at 16-18, mapFrames/checkpointEvery/mapFrom/mapTo at 22,24-26); `DynamicsSummary` 18 args (7 doubles in a row); `MapFrameWriter` 9 args (5 ints in a row); `EventLogWriter` 5; `StructuralStats` 10 | Group: `RunConfig(units, ticks, genomeSource, MapSampling, Observation(observe, checkpointEvery), reportPath, engine, threads, flags)`; `DynamicsSummary(Activity, Population, EnergyLedger, Mass, digest)`; `MapFrameWriter(streams, catalog, FrameSampler, WorldGeometry)`. Update every call site incl. `RunReportTest.java:34,47,58,79` and any `RunConfig(`/`DynamicsSummary(` in tests. No overloaded "defaults" constructors (owner rule); use named constants like `MapSampling.OFF`, `Observation.OFF` at the call sites |
| String building duplication in report | RunReport:22-75 | `line(key, value)` helper; `StringBuilder.append(double)` and `String.valueOf(Double)` give identical text, so output stays byte-equal; keep `String.format("%016x")` for digest |
| Path derivation | Main:227-243 duplicate bodies; `Files.createDirectories(parent)` x3 (Main:69,163,220) | `RunPaths` + `writeCreatingParents(path, text)` |
| Demo mode silently ignores `--world --max-genes --inflow --resource-cycle --map-from/to` and forces `--trace` | RunConfig:113-121 | Keep (byte-identical CLI) but make it explicit in `demo(options)`; do not "fix" in this refactor |
| Responsibility blur: `RunConfig` = CLI parser + inflow DSL parser + config file loader + validation + derived accessors | RunConfig | split: `RunConfig` (record only), `RunOptions`/`ArgCursor` (parse), `InflowSpecParser` |
| Responsibility blur: `RunDirectory` is both checkpoint writer (instance) and checkpoint reader (static `seek`); instance fields serve only the writer | run/RunDirectory | `RunDirectoryWriter` (`create`, `createBranch`, `checkpointIfDue`) + `RunDirectoryReader.seek` (or `CheckpointStore`) |
| `Founders`/engine prep done in Main (maxGenes widening, scatter, maxUnits) | Main:43-57 | `Founders.create(config)` |
| `Bench` builds a `RunConfig` by formatting then re-parsing CLI strings | Bench:36-38 | build the record directly after the RunConfig regrouping, or a `RunConfig.population(...)` factory used only by Bench/tests (check it is not a forwarding-defaults shim in the owner's sense; safer: construct with explicit sub-records) |

## 5. Dead / legacy code

| Item | Where | Evidence |
|---|---|---|
| `StructuralStats.perUnitConnections`, `perUnitMeaningful` (+ computation) | StructuralStats:12-13, StructuralAnalyzer:29-30,35,62,84-85 | never read anywhere in src (main or test); removing also shortens `analyze` |
| `DynamicsSummary.ticks` | DynamicsSummary:4 | no `.ticks()` reader on a summary |
| `MetricsWriter` ctor param `maxUnits` | MetricsWriter:30 | unused |
| `EventLogWriter.prevGeneration`/`prevPosition` used only for DEATH rows; fine, not dead | | |
| `LatestFrame` | run/LatestFrame.java | only referenced from `RunLifecycleTest`; no production caller. Delete, or move to test/future-viewer module |
| `RunDirectory.runId()`, `createBranch` | RunDirectory:49,70 | only used by `RunLifecycleTest`; keep only if branching is a roadmap item, else delete |
| `RunConfig.worldWidth()/maxGenes()/seed()` pass-throughs | RunConfig:32-42 | used by RunReport and Main; after `Founders` they can go (report reads `engine`) |
| `MapFrameWriter.hasLast`, `cacheStride` | MapFrameWriter:85,87 | redundant (see names) |
| Redundant second `Files.createDirectories` | Main:161-165 | dir already created at 67-71 |
| Doc drift (not code): AGENTS.md says report-v6 for RunReport, `SCHEMA = "report-v15"` (RunReport:14); `v5Defaults` factory name; AGENTS lists `UnitCsvReport/WiringReport` that no longer exist | | refresh in a docs commit |
| Em dash in console string | Main:159 ("... ms — console only") | global rule says no em dashes in text; console output is not in the report, so changing it is byte-safe |

## 6. Package layout

Today: `com.simolution.sim` is a flat bag of 14 classes (parsing, simulation loop inputs, analysis, 6 writers, 3 reports, 2 records); `sim.run` is really replay + checkpoint store + a live-frame publisher; `Main` hosts the actual orchestration. The story "configure -> found -> run -> observe -> write -> replay" is not visible.

| Proposed package | Classes |
|---|---|
| `com.simolution` | `Main` (dispatch only, ~15 lines) |
| `com.simolution.cli` | `ArgCursor`, `RunConfig` (record), `RunOptions`, `InflowSpecParser`, `ReplayArgs` |
| `com.simolution.run` | `Founders`, `GenomeFactory` (+`demo()`), `SimulationRun`, `RunResult`, `RunArtifacts`, `RunPaths`, `RunConsole`, `TickObserver` |
| `com.simolution.observe` | `DynamicsObserver`, `DynamicsSummary` (+ groups), `TimeSeriesSampler`, `StructuralAnalyzer`, `StructuralStats` |
| `com.simolution.output` | `EventLogWriter`, `MetricsWriter`, `BirthLogWriter`, `GenomeCatalogWriter`, `map/` (`MapFrameWriter`, `FrameSampler`, `SlotGenomeCache`, `ChunkedFrameStream`, `ResourceLevels`) |
| `com.simolution.report` | `RunReport`, `SnapshotDump` |
| `com.simolution.replay` | `RunDirectoryWriter`, `RunDirectoryReader`, `ReplayCommand` (and `LatestFrame` if kept) |
| `com.simolution.bench` | `Bench` |

Moves are pure renames plus imports; tests mirror packages (AGENTS.md convention), so `src/test/.../sim/*` move too. Do the package move as its own commit before the behavioural splits, so diffs stay reviewable.

## 7. Suggested order (each step byte-identical against runs/baseline.txt)

1. `RunPaths` + delete duplicate `createDirectories` + `Founders` extraction out of Main (pure moves).
2. `TickObserver` + `RunArtifacts` (try-with-resources) + `SimulationRun`; keep observer order exactly.
3. `ArgCursor` + `RunOptions` + `InflowSpecParser`; regroup `RunConfig` and fix test call sites.
4. `GenomeSource` (demo flag) and `GenomeFactory.demo()`.
5. MapFrameWriter split; EventLogWriter split.
6. RunReport sections + DynamicsSummary groups + delete dead fields.
7. StructuralAnalyzer; RunDirectory split; package moves; Bench.

Baseline caveat: `runs/baseline.txt` only covers the report, not the sidecars. For steps 2 and 5, also diff `--out` sidecars (`.catalog.gz` after gunzip, `.births.csv.gz`, `.frames.zst`, `.frames.idx`, `.timeseries.csv`, and `--observe` `events.jsonl`/`metrics.csv`) before/after, since ordering bugs there do not show in baseline.txt.
