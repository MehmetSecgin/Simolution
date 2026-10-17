# 0048: No test-only or unused production code

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Supersedes:** the `LatestFrame` and branch parts of [0043](0043-per-run-config-versioned-checkpoints-seek-branch.md) and [0047](0047-readability-refactor.md)

**Context.** After the readability refactor some members in `src/main` had no caller, or only a test called them: `LatestFrame`, `RunDirectoryWriter.createBranch`, `CheckpointCodec.readBranch`, `GeneBuilder`, `Kernel.connectionsOf`, `Kernel.phases`, `Noise.mutationUniform`, `StateDigest.of`, three `EngineConfig.with…` copies, `NodeSpec.kind`, `WorldState.birthChildCell` and a few layout constants.

**Decision.** Delete them. If all tests were deleted, nothing in `src/main` would be unused. A test that needs a helper owns it under `src/test` (`GeneBuilder` and the gene packing moved there, next to the package-private `GeneLayout`). A test that only exercised a deleted member is deleted. The rule is one line in AGENTS.md Conventions.

**Why.** Test-only code in production hides what the program really does, and the web app can re-add what it needs from git history with a real caller behind it. Every deletion kept `runs/baseline.txt`, the golden outputs and the checkpoint bytes identical.

**Kept on purpose.** Anything that is an output byte stays, even if production never reads it: `run.json` still writes `"parent": null`, and `unitChem` stays in the checkpoint and the state digest.

**Rejected.**
- Keeping `LatestFrame` and `createBranch` for the web app: the owner's rule wins over a future caller.
- Changing the checkpoint format to drop `unitChem`: moves the digest and every checkpoint byte, so it needs the owner's call.
