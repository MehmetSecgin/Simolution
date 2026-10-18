# Observatory design

**Status:** draft for review · **Date:** 2026-10-18 · **Mockup:** the hybrid prototype (claude.ai artifact "Simolution Observatory", version 3), a throwaway that this document supersedes wherever they differ.

## Intent

The owner wants one place to work with runs: "run my thing, make some checks, have some fun, then be able to compare stuff". Today that takes the CLI, a Range-capable Python server, a live viewer, a baker, a time-travel page and DuckDB. The Observatory replaces the viewing half of that with one local web app. It starts runs, shows them live, checks them, branches them and compares them.

It comes before any new biology. Every later biology milestone gets judged in it, so it must make a run's story easy to read at a glance.

## Goals

1. Start a run from the browser, from a config file plus a few overrides.
2. Watch it live: map, health, four charts, moments.
3. Scrub to any tick and see the exact state there, down to one unit's circuit.
4. Branch from any tick with changed settings.
5. Re-run with the same seed and see the determinism proof.
6. Compare two to four runs: settings diff, outcome table, overlaid charts, side-by-side maps.
7. One tool. The old viewers go away once the Observatory covers them.

## Non-goals

- Remote access, a VPS, several users, logins. It runs on this Mac only (12 cores, 32 GB).
- New biology or any kernel behaviour change.
- A single-file offline export of a run (what `tools/mapviz.py` does today). Add later if missed.
- A lineage tree explorer. `births.csv.gz` plus DuckDB stays the deep-query path.
- Phone layouts. Laptop widths from 1280 px up.

## Anti-goals (failure even if it works)

- **Another tool beside the old ones.** If `live.html`, `mapviz.py`, `timetravel.py` and the serve scripts still exist at the end, the project failed its main purpose.
- **Hard to read.** Fable's prototype lost on readability. Every number has a label and a unit, words are plain ("energy held", not "Σe"), text is at least 12 px, contrast passes WCAG AA in both themes.
- **Changed results.** The baseline digest `c0f052e9d43f300c` stays. Nothing in the Observatory may change what the kernel computes.
- **Memory that grows with ticks.** The server keeps no trajectories in RAM. History lives on disk and is read on demand (AGENTS.md memory discipline).
- **A frontend build.** No npm, no bundler, no framework.

## Constraints

- Engine v6.1, JDK 26, Gradle. Existing deps only: `zstd-jni`, `jackson-databind`. HTTP via the JDK's `com.sun.net.httpserver`.
- All AGENTS.md invariants hold: determinism at any thread count, no per-tick allocation in the kernel, flat arrays, code style rules, no comments beyond the allowed kinds.
- Thread count never changes results, so the Observatory may pick any thread count for any kernel it runs.
- Branch rules from ADR 0043: a branch keeps world size, `maxGenes` and the chemical count; everything else may change.

## Decided shape

### One process

`./gradlew observatory` starts a JVM that serves the app on `http://localhost:8090` and runs the simulations in-process. It binds to `127.0.0.1` only.

This reverses part of ADR 0031, which deleted the in-JVM `LiveServer`. ADR 0031 was right for viewing: a static server could tail a file. The Observatory also controls runs (start, pause, branch, re-run, seek), and that needs the engine. One JVM owning both is less machinery than a Python server driving Gradle processes. An ADR records this.

New package `com.simolution.observatory`. The CLI (`Main`, `--out`, `--observe`, report, bench) stays as it is for the baseline, golden outputs, tests and benchmarks.

### Run storage

Every run is one directory under `runs/<dir>/`, with everything inside it. The CLI writes the same directory (see "One run layout for the CLI"), so the Observatory lists every directory under `runs/` that has a `run.json`, whoever made it. A run made on another machine (the CX33) shows up after an `rsync` into `runs/`.

| File | What | Written by |
|---|---|---|
| `run.json` | identity, written once (below) | new |
| `status.json` | status, ticks done, final digest; rewritten on change | new |
| `config.json` | the full `EngineConfig` as `ConfigJson` writes it | new |
| `ckpt/<tick>.ckpt` | checkpoints, format 3 | `RunDirectoryWriter` |
| `metrics.csv` | one row per tick | `MetricsWriter` |
| `frames.zst`, `frames.idx`, `catalog.gz` | every-tick map frames | `MapFrameWriter`, `GenomeCatalogWriter` |
| `births.csv.gz`, `events.jsonl` | lineage and events | existing writers |
| `report.txt` | report-v15, written at the end | `RunReport` |

`<dir>` is a readable slug plus a counter (`baseline-seed-42-3`), never the `runId`. A same-seed re-run has the same `runId` as its original (both first checkpoints are identical), so the id cannot name the directory.

`run.json`: `runId`, `engine`, `name`, `parent` (parent's dir, or null), `branchTick` (or null), `twinOf` (dir of the run it re-runs, or null), `targetTicks`, `checkpointEvery`, `created` (wall clock, metadata only, never part of `runId`).

`run.json` also holds `founders` and `genesPerFounder`. They are not part of `EngineConfig` (the CLI's `--units` and `--genes`), and they stay out of it so no `runId` changes. Compare diffs them next to the config keys. *(Builder's call, settled in review.)*

`status.json`: `status` (`queued`, `running`, `paused`, `finished`, `extinct`, `stopped`, `failed`), `ticksDone`, `finalDigest`, `error`.

A new run whose config, seed and founders match an existing run has the same `runId`. The server opens the existing run and says so, as for branches. Twins exist only through Re-run. *(Settled in review.)*

On start the server scans `runs/`. A run found as `running` or `paused` from a previous session becomes `stopped` at its last checkpoint. Resuming a stopped run is not in v1. A run whose checkpoints come from another engine version is listed but greyed out, with the reason.

### One run layout for the CLI

Decided by the owner: CLI runs open in the Observatory.

- `--out runs/<dir>` names a run directory and writes the layout above, `run.json` and `status.json` included.
- Every `--out` run writes checkpoints (default every 500 ticks, `--checkpoint-every` still sets it). Without them the Observatory can neither seek nor branch.
- Frames stay opt-in on the CLI (`--map-frames`), as they are today. A run without frames still opens: the map then comes from seeks only, and dragging the scrubber shows the map when the drag stops.
- `--observe` goes away: its contents are now always part of the run directory.
- `timeseries.csv` and `TimeSeriesSampler` go away: `metrics.csv` covers them.
- Sidecar naming beside a report (`<base>.frames.zst`, `<base>.obs/`) goes away; `RunPaths` names files inside the directory.
- Baseline workflow: `--out runs/baseline` writes `runs/baseline/report.txt`, the only tracked file under `runs/`. `.gitignore`, `scripts/golden-outputs.sh`, `.golden/ref` and AGENTS.md follow. The report's content and the digest do not change.

### Running

- One run computes at a time, with all cores. Others wait as `queued`.
- Pause keeps the run's kernel and open writers in memory and hands the slot to the next queued run. Resume puts the run at the front of the queue; it never preempts the running run. *(Settled in review.)*
- The Observatory has its own tick loop (tick, snapshot, observers, pause check between ticks). `SimulationRun` stays for the CLI.
- A run ends `extinct` when the population reaches 0, otherwise `finished` at `targetTicks`, or `stopped` by the user.
- Default checkpoint cadence 500 ticks, editable in the New run form. It bounds seek time.
- Frames are written every tick.

### Live feed

- `LatestFrame` (decided in ADR 0043, not yet built) holds two preallocated frame buffers: cell to genome id, and quantised resource. An observer copies into the back buffer at most 10 times per wall-clock second and swaps. Readers never block the tick loop. Wall clock here only paces observation and never touches state.
- The browser polls the live frame at about 10 Hz while following live.
- Run status and tick progress go to the browser over Server-Sent Events, one stream for the whole app.

### Seeing a past tick

Two paths, because dragging needs speed and inspecting needs exactness:

1. **While dragging the scrubber:** frames from `frames.zst`. The server decodes one 256-frame chunk and keeps a small LRU of decoded chunks. Shows genome colours and the resource field.
2. **When the scrubber stops, or on pause, or on a step key:** an exact seek. A viewer kernel (separate from the running one) loads the nearest checkpoint and steps to the tick. It stays at that tick, so a later forward seek steps on from there when that is shorter. This feeds the mass and action overlays, the unit inspector and the circuit.

Ticks before a branch's `branchTick` come from the parent's files. Ticks of a running run not yet sealed in a frame chunk come from a seek.

Seek target: under 500 ms on a 100×100 world with checkpoints every 500.

### Branching

- `CheckpointCodec.readBranch(in, threads, newConfig)` restores a checkpoint's state under a changed config. It refuses a change of world width, `maxGenes` or chemical count with a message naming the field.
- Branch at tick T: seek the parent to T, restore under the new config, write the branch's first checkpoint at T. The branch's `runId` is that checkpoint's SHA-256, as for any run.
- `RunDirectoryWriter` accepts a first checkpoint at a tick other than 0 when a parent is given.
- The same branch twice (same parent, tick and settings) gives the same `runId`. The server says so and opens the existing branch.
- A branch with an unchanged config must reproduce the parent's digests at every later checkpoint. This is the key test of the feature.
- A branch's own files cover tick T onward only. `targetTicks` in `POST /branch` is the absolute end tick. *(Settled in review.)*
- Charts, health, moments and compare outcomes read a branch as one story from tick 0: the server joins the parent's metrics before T to the branch's own. `report.txt` covers T onward and says so in its header.
- Writers that seed from the founders today (`EventLogWriter`'s previous state, the founder count in `MetricsWriter` and `RunReport`) seed from the restored state on a branch, so no false events appear at T.

### Re-run with the same seed

Creates a twin: same config, same seed, a different thread count (half the cores if the original used all, otherwise all). When the twin finishes, the server compares digests at every checkpoint and the final digest. The compare banner then reads "Byte-identical at all N checkpoints and the final state" or names the first checkpoint that differs. A difference is a determinism bug and the health card turns red.

### Health

Computed by the server from `metrics.csv` and `status.json`. Each check is ok, warn or bad, with one plain sentence.

| Check | Ok | Warn | Bad |
|---|---|---|---|
| Alive | population > 0 | population ≥ 95% of W² (world full) | extinct |
| Energy audit | max abs `audit_error` ≤ 1e-6 of system energy | above that | above 1e-3 of system energy |
| Lineages | 2 or more alive | 1 alive (monoculture) | |
| Determinism | twin matched | never checked (shows the Re-run button) | twin differed |

Thresholds are constants in code, not config.

### Moments

Detected by the server from `metrics.csv`, shown as a list in the run view and as lettered markers on the timeline:

| Mark | Moment | Rule |
|---|---|---|
| P | peak population | the maximum |
| C | crash | population falls 30% or more below its running max within 5% of the run's length; at most one per 10% of the run |
| T | takeover | first tick one lineage holds over half the living units |
| X | extinction | population reaches 0 |
| B | branch | each child's branch tick, and the run's own branch point |

Takeover needs two new `metrics.csv` columns, `top_lineage` and `top_lineage_share`. `MetricsWriter` already counts lineages per tick, so this is a preallocated count array. The report-v8 metrics schema gets updated in the same change.

### Compare

- Two to four runs, chosen in the run list or opened from a run (`C` compares with the parent, Re-run opens the twin compare).
- Banner: "Identical until tick N" when one run is an ancestor of the other, the twin result for twins, otherwise "Different from tick 0".
- What differs: flat config keys with differing values; "show all settings" lists every key.
- Outcome table: peak population, final population, births, lineages alive, total mass, each with % delta against run A.
- Over time: the four charts with every run overlaid, a dashed line at the branch tick.
- Maps: one minimap per run on one shared scrubber.

### API

JSON over HTTP, localhost only. Frames go as binary.

| Method and path | Does |
|---|---|
| `GET /api/runs` | list with status, progress, peak population, health summary |
| `POST /api/runs` | create from `{name, configFile, overrides, targetTicks, checkpointEvery}` |
| `GET /api/runs/{dir}` | run.json, status, health, moments |
| `POST /api/runs/{dir}/pause`, `/resume`, `/stop` | control |
| `POST /api/runs/{dir}/branch` | `{tick, overrides, targetTicks, name}` |
| `POST /api/runs/{dir}/rerun` | create the twin |
| `DELETE /api/runs/{dir}` | delete; refused while it has branches |
| `GET /api/runs/{dir}/metrics?points=N` | columns downsampled to at most N points (bucket mean) |
| `GET /api/runs/{dir}/frame?tick=T` | binary frame from frames; `tick=live` for the live buffer |
| `GET /api/runs/{dir}/state?tick=T` | exact per-cell mass and strongest action, after a seek |
| `GET /api/runs/{dir}/unit?tick=T&slot=S` | every per-unit field by `FieldSpec` name, live connections, node outputs |
| `GET /api/compare?runs=a,b,...` | config diff, outcomes, divergence |
| `GET /api/events` | Server-Sent Events: status and progress |
| `GET /api/configs` | config files in `examples/` |

The unit inspector lists per-unit fields generically from `WorldState.fields()`, so new biology shows up without UI work.

### Frontend

- Plain HTML, CSS and JS modules served from `src/main/resources/observatory/`. Canvas for the map and charts, SVG for the circuit. Node names come from `NodeLayout.toJson()`.
- Three screens with hash routes: `#runs`, `#run/<dir>`, `#compare/<a>,<b>`.
- Visual style from the hybrid mockup: Bricolage Grotesque headings, Atkinson Hyperlegible body, JetBrains Mono numbers; cool slate palette, cobalt accent; light and dark themes; colours as CSS tokens.
- Run list is a tree: branches indented under their parent with "branched at tick N".
- Run view: header (status, pause, branch from this tick, re-run), map with overlays (genome, resource, mass, action), health card, moments list, unit inspector with circuit, timeline with four charts (population, energy held, births per tick, lineages alive), scrubber, moment markers, Jump to live.
- Keys in the run view: Space pauses or resumes the run, ← → step one tick (Shift: 100), Home and End jump to tick 0 and live, B opens the branch panel at the current tick, C compares with the parent. Keys are ignored while typing in a field.
- Clicking a unit while following live stops following (the run keeps going) and pins that tick.

### Retiring the old viewers

Once the Observatory covers watching, scrubbing and inspecting, one commit deletes `src/main/resources/live.html`, `fzstd.min.js`, `tools/mapviz.py`, `tools/timetravel.py`, `scripts/serve.py`, `scripts/serve-live.sh`, `ReplayCommand`, `SnapshotDump`, `--replay` and their tests, and updates AGENTS.md. The CLI layout change above removes `--observe`, `TimeSeriesSampler` and the sidecar naming. `MapFrameWriter` and the frame format stay, the Observatory reads them.

### Decision records to write

- In-JVM Observatory server, and why it reverses ADR 0031's no-server stance.
- `readBranch` under a changed config, building `LatestFrame` and `readBranch` from ADR 0043.
- Retiring the old viewers.
- One run directory layout for the CLI and the Observatory (drops `--observe` and `timeseries.csv`).

## Verification

- Baseline digest and report content unchanged. Golden outputs change only in file names and locations (the CLI layout), the dropped `timeseries.csv`, and the two new `metrics.csv` columns, each explained in its commit.
- A CLI run copied into `runs/` opens, scrubs, branches and compares like an Observatory run.
- Tests: branch with unchanged config reproduces parent digests; `readBranch` refuses each shape change; twin comparison finds equal and unequal digests; moment detection on synthetic metrics (peak, crash, takeover, extinction); health thresholds; frame chunk reads at chunk edges and inside a branch's prefix; one API smoke test per endpoint with the JDK `HttpClient`.
- Observatory runs reach at least 90% of the ticks/s of the CLI with the same sidecars on `small` and `dense200`.
- Server heap stays flat over a 30,000-tick run, apart from the genome catalog's index. That index grows with distinct genomes (one global id per genome) and is accepted as a known ceiling; it gets a `ponytail:` note naming the upgrade path (spill the index to disk). *(Settled in review.)*
- Manual click-through in the browser of every goal above, both themes.

## Left to the builder

- Class split inside `com.simolution.observatory`, handler structure, JSON field names beyond those listed.
- Binary frame layout, LRU size, downsampling details within "bucket mean, at most N points".
- How the server picks the viewer kernel's thread count.
- Chart drawing details, empty and error states, toasts, the exact copy of messages within the readability rule.
- Order of implementation stages, as long as the old viewers are deleted only after parity.
