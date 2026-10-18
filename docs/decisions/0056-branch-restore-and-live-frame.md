# 0056: Branch restore now, live frame with the Observatory

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** Observatory

**Context.** ADR 0043 decided `readBranch` and `LatestFrame` but built neither. The Observatory branches runs from any tick and shows the running run live.

**Decision.** `CheckpointCodec.readBranch(in, threads, newConfig)` shares header parsing and the corrupt-checkpoint digest check with `read`, and refuses a change of `worldWidth`, `maxGenes` or chemical count with `a branch cannot change <field> (<old> -> <new>)`. `Branching.start` seeks the parent to the tick and restores that state under the new config. `RunDirectoryWriter` accepts a first checkpoint after tick 0 only when the run has a parent. `run.json` names the parent by its directory, not its run id, because a re-run shares its original's run id. Writers that seeded from the founders (events, metrics) seed from the restored state. `LatestFrame` is built with the Observatory backend.

**Why.** Restoring under a new config needs no new format: the checkpoint already holds the full state, and the digest does not cover the config. A branch with an unchanged config reproduces its parent's digests, which is the test of the feature.

**Rejected.** Replaying the parent from tick 0 under the new config: it changes the past, so it is a new run, not a branch. Storing the parent's run id: twins would make it ambiguous.
