# 0055: One run directory for the CLI and the Observatory

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** `--out runs/x.txt` wrote a report with sidecars beside it, and only `--observe` added `run.json`, checkpoints, metrics and events in `x.obs/`. The Observatory must open any run, seek it and branch it, so it needs checkpoints and one known place for every file.

**Decision.** `--out runs/<dir>` names a directory that holds every file of the run (`docs/specs/run-directory-v1.md`). Every `--out` run writes checkpoints (default every 500 ticks), metrics and events, plus `run.json`, `status.json` and `config.json`. Frames stay opt-in. `--observe`, `TimeSeriesSampler` and the sidecar naming are removed. `--out runs/x.txt` is refused with the directory to use. The baseline lives at `runs/baseline/report.txt`.

**Why.** CLI runs, Observatory runs and runs copied from another machine then look the same, so one reader serves all of them. `run.json` has no wall-clock field, so the directory stays byte-deterministic.

**Rejected.** An importer that converts old sidecar layouts: the old runs are throwaway and the code would live forever. Keeping `--observe` optional: a run without checkpoints can neither seek nor branch, and the flag only saved some disk.
