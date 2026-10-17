# 0043: Per-run config, versioned checkpoints, seek and branch

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Design:** [engine-v6-design](../notes/engine-v6-design.md) §8 · **Supersedes:** report-v8's manifest, `Replayer` and `saveState` ([ADR 0023](0023-observability-layer.md))

## Context

Constants lived in `static final KernelConfig` and inflow was set after construction, so a run was not fully described by anything on disk. Checkpoints were positional, unversioned, omitted the inflow, and needed a manifest beside them. A web app needs resume, seek and branch.

## Decision

- `EngineConfig` is one immutable object per engine instance, read from JSON (`--config`) and stored verbatim in every checkpoint. Two engines with different configs run in one JVM. Thread count is not part of it.
- A checkpoint (`CheckpointCodec`, format version 2) is self-sufficient: magic `SIMO`, format version, engine version, config JSON, node layout JSON, the `StateDigest`, scalars, then every `SAVED` field in declared order. It stores no thread count. Read refuses a bad magic, another format or engine version, a different layout, truncation, and a digest that does not match the loaded state.
- `RunDirectory` writes `run.json` and `ckpt/<tick>.ckpt`. `runId` is the SHA-256 of the first checkpoint. `seek(T)` loads the nearest checkpoint at or before T and steps forward. A branch restores with `readBranch` under a changed config; it is refused unless world size, `maxGenes` and chemical count are unchanged, and the new run records its parent (run id and tick).
- `LatestFrame` publishes a compact frame every N ticks to any readers without blocking the tick loop.

## Why

Running `0 to T`, or `0 to t`, restore, `t to T`, gives the same bytes at T on any thread counts, which tests enforce. Old v5 runs are refused by the version check and stay viewable from their frames.

## Rejected

- Keeping manifest plus side files: not self-sufficient.
- Java serialization: fragile across versions and unreadable by other tools.
- Allowing a branch to change state shape: needs a new run.
