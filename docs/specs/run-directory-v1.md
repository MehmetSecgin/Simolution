# run-directory-v1: one run directory for the CLI and the Observatory

**Status:** implemented 2026-10-18 · **Kernel:** v6.1 (unchanged) · **Report:** report-v15 (unchanged)
**ADRs:** [0055](../decisions/0055-one-run-directory.md), [0056](../decisions/0056-branch-restore-and-live-frame.md)
**Delta over:** [report-v8](report-v8.md) (metrics, events, run directory) and [report-v14](report-v14.md) (frame row)

## Why

The CLI wrote a report with sidecars beside it (`<base>.frames.zst`, `<base>.obs/`), and only `--observe` runs had checkpoints. The Observatory needs every run to seek and branch, and it lists runs made by anyone. So every `--out` run now writes one directory with everything inside it.

## Layout

`--out runs/<dir>` names the directory. A value ending in `.txt` is refused with `--out names a run directory, not a file: use --out runs/<dir>`.

| File | What | When |
|---|---|---|
| `run.json` | identity, written once at the start | always |
| `status.json` | state, ticks done, final digest, error; rewritten on change | always |
| `config.json` | the full `EngineConfig` as `ConfigJson` writes it | always |
| `layout.json` | `NodeLayout.toJson()` | always |
| `ckpt/<tick>.ckpt` | checkpoint format 3: the first tick, then every `checkpointEvery` ticks (default 500, `--checkpoint-every`) | always |
| `metrics.csv` | one row per tick | always |
| `events.jsonl` | births, deaths, extinctions | always |
| `births.csv.gz`, `catalog.gz` | lineage store and genome catalog | always |
| `frames.zst`, `frames.idx` | map frames | with `--map-frames` or `--map-from/--map-to` |
| `report.txt` | report-v15, written at the end | at the end |

`--observe` and `<base>.timeseries.csv` are gone: their contents are always in the directory, and `metrics.csv` covers the time series. Same version, config and seed give a byte-identical directory.

## `run.json`

```json
{
  "runId" : "<sha-256 of the first checkpoint>",
  "engine" : "v6.1",
  "name" : "baseline",
  "parent" : null,
  "branchTick" : null,
  "twinOf" : null,
  "targetTicks" : 1000,
  "checkpointEvery" : 500,
  "founders" : 100,
  "genesPerFounder" : 32
}
```

- `parent` and `twinOf` name sibling directories under the same root, never run ids. `branchTick` is set with `parent`.
- `targetTicks` is the absolute end tick, also for a branch.
- `founders` and `genesPerFounder` are the CLI's `--units` and `--genes`. They are not part of `EngineConfig`, so they stay out of the `runId`. A branch inherits them from its parent.
- No wall-clock field, so the file stays deterministic. A run's age is its directory's modification time.

## `status.json`

```json
{ "status" : "finished", "ticksDone" : 1000, "finalDigest" : "c0f052e9d43f300c", "error" : null }
```

`status` is one of `queued`, `running`, `paused`, `finished`, `extinct`, `stopped`, `failed`. A CLI run writes `running` at the start and `finished` (or `extinct` when no unit is alive at the end) with the hex state digest at the end. A directory left `running` or `paused` by a process that died is read as `stopped`.

## Branches

A branch directory holds the parent's state at `branchTick` restored under a new config (`CheckpointCodec.readBranch`, refused when `worldWidth`, `maxGenes` or the chemical count changes). Its first checkpoint is `ckpt/<branchTick>.ckpt` and its `runId` is that file's SHA-256, so the same branch twice has the same id. Its own files cover ticks after `branchTick`; earlier ticks are read from the parent chain. `report.txt` covers the same span and adds two header lines after `max-genes`:

```
branch-of: <parent dir>
branch-tick: <tick>
```

## `metrics.csv` delta over report-v8

Two columns after `mass_total`:

```
tick,population,lineages_alive,births_cum,deaths_cum,energy_total,field_total,sink,inflow_cum,audit_error,max_generation,mass_total,top_lineage,top_lineage_share
```

- `top_lineage`: the founder lineage with the most living units; a tie goes to the lowest id; `-1` when no unit is alive.
- `top_lineage_share`: its members over the population, `0` when no unit is alive.

## `events.jsonl` delta over report-v8

The format is unchanged. On a branch the writer starts from the restored state, so no false event appears at the branch tick. Two fields then cover the branch span only: a `DEATH`'s `lifespan` is `-1` for a unit born before the branch tick, and an `EXTINCTION`'s `peakMembers` and `maxGeneration` count from the branch tick.

## Frame delta over report-v14

The unit row gains the slot's lineage id:

```
u <cell> <slot> <genomeId> <lineage>
```

The `frames.idx` header reads `format v16 world <W> sample-every <N> chunk 256`. Containers, chunking and the catalog are unchanged.
