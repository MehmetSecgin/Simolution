# Observatory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or subagent-driven-development) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One local web app that starts, watches, checks, branches and compares runs. The CLI writes the same run directory, and the old viewers are deleted.

**Architecture:** First the harness gets one run directory layout, branch restore and two metrics columns. Then a new package `com.simolution.observatory` runs simulations in-process behind a JDK `HttpServer`, with a JSON and binary API. A plain HTML/JS frontend in `src/main/resources/observatory/` draws the three screens. Last, the old viewers go.

**Tech Stack:** Java 26, Gradle, JUnit 5, `com.sun.net.httpserver`, `java.net.http.HttpClient` in tests, Jackson and `zstd-jni` (both already deps), vanilla JS modules, canvas, SVG.

**Spec:** `docs/notes/observatory-design.md`. Visual reference: `docs/notes/observatory-mockup.html`, a throwaway; the spec wins where they differ.

## Global Constraints

- The baseline digest `c0f052e9d43f300c` and the baseline report content never change. No kernel behaviour change.
- No new dependencies. No npm, no bundler, no framework. The server binds to `127.0.0.1`.
- AGENTS.md invariants and code style hold: determinism, no per-tick allocation in `kernel/`, the `final` rules, no `var`, no comments beyond the allowed kinds.
- The server keeps no trajectories in RAM. The one exception is the genome catalog index, which gets a `ponytail:` note.
- No em dashes anywhere.
- Each task ends with `./gradlew check` passing and BASELINE-CHECK printing `IDENTICAL`. Use Conventional Commits and explicit paths. Never push.
- ADRs: 0055 one run directory, 0056 branch restore and live frame, 0057 in-JVM server, 0058 retire the old viewers.

**BASELINE-CHECK** (from Task 1 Step 3 on; before that use the AGENTS.md form):

```bash
./gradlew run -q --args="--units 100 --ticks 1000 --seed 42 --world 100 --out build/baseline-check" && diff runs/baseline/report.txt build/baseline-check/report.txt && echo IDENTICAL
```

**OUTPUT-CHECK:**

```bash
rm -rf build/golden-now && scripts/golden-outputs.sh build/golden-now && diff -r .golden/ref build/golden-now && echo IDENTICAL
```

## Rulings on the spec

- `run.json` drops `created`, so run directories stay byte-deterministic. The run list shows the directory's modification time instead.
- Frame rows gain the lineage id. The map overlay is labelled "Lineage" and colours by it.
- All harness format changes go in one new binding spec, `docs/specs/run-directory-v1.md`. The report schema stays `report-v15`.

## Review Focus

1. `--out runs/x.txt` from old habit is refused, and the message names `--out runs/x` (Task 1).
2. After a server restart mid-run, the run lists as `stopped` and its files still open (Task 2).
3. An unreadable directory under `runs/` is listed with its reason and never breaks the list (Task 2).
4. A frame past `ticksDone` answers 404 as JSON. A tick in a branch's prefix comes from the parent (Task 2).
5. A double submit of the same run or branch returns the existing run and creates no duplicate (Task 2).

---

### Task 1: Harness: one run directory, branch restore, lineage columns

**Files:**
- Create in `sim/run/`: `RunManifest.java`, `RunStatus.java`, `RunState.java`, `Branching.java`.
- Create docs: `docs/specs/run-directory-v1.md`, ADR 0055, ADR 0056.
- Modify:
  - Harness: `RunPaths`, `RunArtifacts`, `RunConfig`, `RunOptions`, `Main`, `RunReport`.
  - Writers: `EventLogWriter`, `MetricsWriter`, `MapFrameWriter`, `ChunkedFrameStream`.
  - Run directory: `run/RunDirectoryWriter`, `run/RunDirectoryReader`, `ReplayCommand` (deleted in Task 4).
  - Kernel: `kernel/state/CheckpointCodec`.
  - Repo files: `scripts/golden-outputs.sh`, `.gitignore`, `AGENTS.md`, the report-v8 and report-v14 specs (one pointer line each).
- Delete: `TimeSeriesSampler`, `Observation` and their tests.
- Move: `git mv runs/baseline.txt runs/baseline/report.txt`.

**Interfaces (produced):**
- `record RunPaths(Path dir)` with these accessors:
  - `report()` → `report.txt`, `config()` → `config.json`, `manifest()` → `run.json`, `status()` → `status.json`
  - `metrics()` → `metrics.csv`, `events()` → `events.jsonl`, `checkpoints()` → `ckpt/`
  - `framesZst()`, `framesIdx()`, `catalog()` → `catalog.gz`, `births()` → `births.csv.gz`, `layoutJson()` → `layout.json`
- `enum RunState { QUEUED, RUNNING, PAUSED, FINISHED, EXTINCT, STOPPED, FAILED }`, written lowercase.
- `record RunManifest(String runId, String engine, String name, String parent, Integer branchTick, String twinOf, int targetTicks, int checkpointEvery, int founders, int genesPerFounder)`, with `static read(Path)` and `write(Path)` via Jackson.
- `record RunStatus(RunState status, int ticksDone, String finalDigest, String error)`, with the same `read` and `write`.
- `record RunOrigin(String name, String parent, Integer branchTick, String twinOf)`.
- Run directory:
  - `RunArtifacts.open(RunConfig config, Kernel kernel, RunOrigin origin)`
  - `RunArtifacts.finish(RunState state, long finalDigest, String report)`
  - `RunDirectoryWriter.create(RunPaths paths, Kernel kernel, RunManifest draft)` accepts tick > 0 only when `draft.parent()` is set.
  - `RunDirectoryReader.seek(Path runDir, int tick, int threads)`
- `RunConfig` changes: it loses `observation`, `reportPath` becomes `Path runDir`, and it gains `int checkpointEvery` with default `500`.
- `EventLogWriter(Writer out, WorldState start, int founderCount)` seeds its previous state from `start`.
- `metrics.csv` gains `top_lineage,top_lineage_share` after `mass_total`. Ties go to the lowest id. An empty world writes `-1,0`.
- The frame row is `u <cell> <slot> <genomeId> <lineage>`. The `frames.idx` header says `format v16`.
- Kernel and branching:
  - `CheckpointCodec.readBranch(InputStream in, int threads, EngineConfig newConfig) -> Kernel` refuses with `a branch cannot change <worldWidth|maxGenes|chemical count> (<old> -> <new>)`.
  - `CheckpointCodec.readDigest(InputStream in) -> long`
  - `Branching.start(Path parentDir, int tick, EngineConfig newConfig, int threads) -> Kernel`
- For branches, `RunReport` adds two header lines, `branch-of` and `branch-tick`.

- [ ] **Step 1: Failing tests.**
  - `RunPathsTest`: every accessor maps to its file name.
  - `RunConfigTest`:
    - `--out runs/x.txt` throws `--out names a run directory, not a file: use --out runs/x`
    - `--observe` is an unknown flag.
  - `RunArtifactsTest`:
    - A 20-founder, 30-tick, `--checkpoint-every 10` run writes every file. Its `ckpt/` holds 0, 10, 20 and 30, and there is no `frames.zst`. `status.json` says `finished` with the hex final digest.
    - A dying world ends `extinct`.
    - The same run twice is byte-identical.
  - `ObservabilityTest`: with lineage 2 holding 3 of 4 units, the row reads `2,0.75`.
  - `MapFrameWriterTest`: a row carries the slot's lineage.
  - `CheckpointCodecBranchTest`:
    - A branch at 250 with an unchanged config matches the parent's digests at 300 and 400, with 1 thread against 4.
    - Each of the three shape changes is refused with its message.
    - `harvestCapacityPerConnection` 1.5 → 3.0 diverges within 50 ticks.
  - `BranchingTest`:
    - The branch dir starts at `ckpt/250.ckpt`, and its `runId` is that file's SHA-256.
    - The same branch twice gives the same `runId`.
    - No false events appear at tick 251.
- [ ] **Step 2: Implement.**
  - `--out` takes a directory. Every `--out` run writes checkpoints, metrics and events. Frames stay behind `--map-frames`.
  - The CLI ends `finished` or `extinct`. It prints `run written to <dir>` in place of the old viewer hints.
  - `readBranch` shares header parsing with `read` and keeps the corrupt-checkpoint digest check.
- [ ] **Step 3: Collateral.**
  - Golden script: `--out <name>`, no `--observe`.
  - `.gitignore`: `runs/*`, `!runs/baseline/`, `runs/baseline/*`, `!runs/baseline/report.txt`.
  - AGENTS.md: Commands, baseline workflow, Architecture.
  - Write `run-directory-v1.md`, which covers the layout, the two JSON files, the metrics delta over report-v8, the frame row delta over report-v14 and the removal of `timeseries.csv`.
  - Write ADR 0055 (rejected: an importer, a still-optional `--observe`).
  - Write ADR 0056, which builds ADR 0043's `readBranch` now and the live frame in Task 2.
- [ ] **Step 4: Verify.**
  - `./gradlew check` passes and BASELINE-CHECK prints `IDENTICAL`.
  - Regenerate `.golden/ref` and review the diff. Only paths, the new files, the dropped `timeseries.csv`, the two metrics columns and the frame row may differ. `events.jsonl` content must not.
- [ ] **Step 5: Commit** as two commits: `feat!: one run directory, branch restore, lineage columns` and `docs: run-directory-v1, ADRs 0055 and 0056`.

### Task 2: Observatory backend

**Files:**
- Create in `src/main/java/com/simolution/observatory/`:
  - Running: `Observatory` (main), `RunStore`, `RunEntry`, `RunManager`, `RunSession`, `RunRequest`
  - Serving: `Api`, `EventStream`
  - Data: `MetricsTable`, `Health`, `Moments`, `Comparison`, `Twins`
  - Frames: `FrameReader`, `LatestFrame`, `Seeker`
- Create: ADR 0057, plus a placeholder `src/main/resources/observatory/index.html`.
- Modify: `build.gradle` (task `observatory`, `JavaExec`, `-Xmx8g`) and `AGENTS.md`.
- Tests: `src/test/java/com/simolution/observatory/`.

**Interfaces (produced):**
- `Observatory --port 8090 --runs runs`. A taken port exits with `port <n> is in use; pass --port`.
- `RunStore(Path root)`: `list()`, `get(dir)`, `newDir(name)`, `findByRunId(runId)`, `delete(dir)`, `markInterrupted()`.
  - Slugs: lowercase, runs of non-alphanumerics become `-`, at most 40 chars, then `-<n>` with the lowest free n.
  - `delete` refuses runs that have branches or are running.
- `record RunEntry(String dir, RunManifest manifest, RunStatus status, long modifiedMillis, String unreadable)`
- `RunManager`:
  - Calls: `create(RunRequest)`, `branch(dir, tick, overrides, targetTicks, name)`, `rerun(dir)`, `pause`, `resume`, `stop`.
  - It runs one run at a time on all cores and queues the rest.
  - Pause keeps the session in memory and passes the slot on. Resume goes to the front of the queue.
  - Extinction ends a run early. An unknown override answers `unknown setting: <key>`.
  - When the `runId` already exists, `create` and `branch` return the existing run.
  - `rerun` uses half the cores when the original used all of them, otherwise all.
- The API is the spec's table. Errors are 400, 404 or 409, each with body `{"error": "..."}`. Server-sent events are `run` (summary on status change) and `progress` (at most 4 per second).
- Wire formats, little-endian:
  - Frame: `int32 tick, int32 width, uint8[w*w] resourceLevel, int32 n, n × (int32 cell, int32 slot, int32 lineage)`.
  - State: `int32 tick, int32 width, float32[w*w] mass, uint8[w*w] action`, where 255 means empty and any other value is the strongest meaningful action's local index.
  - Unit JSON: SAVED fields of dims `UNIT`, `UNIT_NODE` and `GENE` by `FieldSpec` name (genes cut to `geneCount`), unit-local connections, and outputs.
- Frames:
  - `FrameReader` keeps an LRU of 8 decoded chunks. Ticks before `branchTick` read the parent.
  - When there is no frame (an unsealed chunk, or a CLI run without frames), the endpoint seeks.
  - `LatestFrame` is an observer with two buffers, copying at most 10 times per wall-clock second.
  - `Seeker` holds at most 4 viewer kernels on half the cores, and steps forward from a cached kernel when that is shorter than restoring.
- Data:
  - The metrics endpoint returns `tick`, `population`, `energy_total`, `births_per_tick`, `lineages_alive` and `mass_total`, as bucket means of at most N points. A branch's parent prefix is joined in front.
  - Health and moments follow the spec's tables. The constants: crash drop 0.30, window 0.05 and spacing 0.10 of `targetTicks`, minimum running max 30, takeover share > 0.5 with population ≥ 30 and founders ≥ 2, world full 0.95, audit warn 1e-6 and bad 1e-3 of `energy_total + field_total + sink + mass_total`.
  - `Twins.compare(a, b) -> TwinResult(identical, checkpoints, firstDifferentTick)` reads checkpoint header digests.
  - Comparison divergence:
    - `ancestor`: the earliest branch tick on the two chains below their common ancestor.
    - `twin`: the twin result.
    - `none`: tick 0.

- [ ] **Step 1: Failing tests.**
  - `RunStoreTest`:
    - A broken dir is listed with a reason.
    - `running` becomes `stopped` after `markInterrupted`.
    - `delete` refusals.
  - `RunManagerTest`:
    - Queue order, and pause/resume order.
    - Early extinction.
    - An unknown override is refused.
    - An Observatory run's digest and `metrics.csv` equal the CLI's.
  - `MomentsTest` and `HealthTest`: synthetic metrics hitting each rule and each threshold.
  - `FrameReaderTest`: ticks 1, 256, 257, 512 and 600 match a reference run, and a branch prefix is read from the parent.
  - `SeekerTest`: digests at 137, 499, 500 and 501 match a straight run.
  - `TwinsTest`: a match, and a corrupted digest that names its tick.
  - `ApiTest` (server on port 0 over a temp root):
    - Each endpoint's happy path.
    - 400, 404 and 409 cases, and a frame past the end giving 404 as JSON.
    - A double create and a double branch return one run.
    - A re-run turns determinism health `ok`.
    - A bad branch shape gives 400.
    - The event stream delivers `finished`.
    - The server binds to loopback.
- [ ] **Step 2: Implement.** The run loop in `RunSession` is `tick`, snapshot, observers, then a pause/stop check. Overrides apply to the config file's JSON by key before `ConfigJson.read`. A branch report's structure section analyzes `kernel.liveConnections()`.
- [ ] **Step 3: Write ADR 0057.** ADR 0031 was right for viewing, but control needs the engine. Rejected: Python plus subprocesses, WebSocket.
- [ ] **Step 4: Verify.** `./gradlew check` passes, BASELINE-CHECK prints `IDENTICAL`, and a `/state` seek at tick 499 on a 100×100 run with checkpoints every 500 answers in under 0.5 s.
- [ ] **Step 5: Commit** `feat(observatory): run manager and HTTP API`.

### Task 3: Observatory frontend

**Files:**
- Create in `src/main/resources/observatory/`: `index.html`, `app.css`, `app.js`, `runs.js`, `run.js`, `compare.js`, `map.js`, `charts.js`, `circuit.js`.

**Interfaces:**
- Routes: `#runs`, `#run/<dir>`, `#compare/<a>,<b>[,...]`.
- `app.js` exports `api`, `onEvent`, `go`, `fmt`.
- `map.js` exports `drawFrame(canvas, frame, overlay, state)`.
- `charts.js` exports `drawChart(canvas, series[], options)`, shared by the run view and compare.
- `circuit.js` exports `drawCircuit(svg, unit, layout)`.

- [ ] **Step 1: Shell and run list.**
  - Copy the CSS tokens, fonts and both themes from the mockup, and add `[hidden] { display: none !important; }`.
  - The run list is a tree table: status pill, progress, peak population, health chip, modified time. Unreadable runs are greyed with their reason.
  - New run form: config file, name, seed, world, founders, genes, ticks, checkpoint every, and "add setting".
  - "Compare" works for 2 to 4 runs. Delete asks for confirmation.
- [ ] **Step 2: Run view.**
  - Map with Lineage (12-colour palette), Resource, Mass and Action overlays. Mass and Action wait for the state body.
  - Health card, moments list and unit card with fields and circuit.
  - Timeline: four charts, scrubber and lettered moment markers. Dragging uses frames; release, pause and step keys seek.
  - Controls: Jump to live, Pause, Branch panel, Re-run.
  - Keys: Space, ←/→ (Shift 100), Home, End, B and C. Keys are ignored in inputs.
  - Clicking a unit while following live pins that tick.
- [ ] **Step 3: Compare.**
  - Banner, one of:
    - `Identical until tick N, then they split.`
    - `Byte-identical at all N checkpoints and the final state.`
    - `Differs from checkpoint tick N: a determinism bug.`
    - `Different from tick 0.`
  - Settings diff with "show all". When nothing differs: `No setting differs. Same config, same seed.`
  - Outcome table with % deltas against A.
  - Overlaid charts with a dashed divergence line, and minimaps on one shared scrubber.
- [ ] **Step 4: Verify** in the in-app browser against `./gradlew observatory`:
  - Create, follow live, scrub, inspect a unit, branch, re-run.
  - Compare a branch pair, a twin pair and four runs.
  - Both themes, at 1280 px wide, with no console errors.
- [ ] **Step 5: Commit** `feat(observatory): run list, run view and compare`.

### Task 4: Retire the old viewers

**Files:**
- Delete:
  - `src/main/resources/live.html`, `fzstd.min.js`
  - `tools/mapviz.py`, `tools/timetravel.py`
  - `scripts/serve.py`, `scripts/serve-live.sh`
  - `ReplayCommand`, `SnapshotDump` and their tests
- Modify: `Main` (no `--replay`), `scripts/golden-outputs.sh` (drop the replay rows), `.golden/ref`, `AGENTS.md` (an Observatory section replaces live, bake, replay and time-travel; Roadmap marks the web app done).
- Create: ADR 0058.

- [ ] **Step 1: Delete and update.** `grep -rn "live.html\|mapviz\|timetravel\|serve-live\|serve.py\|--replay\|SnapshotDump"` finds only historical ADRs and specs.
- [ ] **Step 2: Verify.**
  - `./gradlew check` passes and BASELINE-CHECK prints `IDENTICAL`.
  - In the regenerated `.golden/ref`, only the replay rows drop.
  - Observatory ticks/s on the bench's `dense200` settings reaches at least 90% of `./gradlew bench`. Note the result in `docs/notes/engine-v6-bench.md`.
- [ ] **Step 3: Commit** `feat!: the Observatory replaces the old viewers`.
