# 0044: A chunked final-state digest

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Spec:** [report-v15](../specs/report-v15.md)

## Context

`DynamicsObserver.foldDigest` hashed about 2.1M values per tick in one serial chain: 58% of a dense v5 run, and the one cost threads could not touch.

## Decision

`state-digest` is the hash of the final state (`StateDigest`). Scalars are folded first, then every `SAVED` array is cut into the fixed 4096-element chunks, each chunk is folded over its raw bits into its own hash, and chunk hashes are combined in ascending chunk index. The folds run on the kernel's runner. The digest is computed at the end of the run and written into every checkpoint header. `--digest-every-tick` prints it each tick for debugging and tests.

## Why

Same value for any thread count, and a checkpoint cannot load into a different state without being noticed. It covers exactly what a checkpoint saves, so equal digests mean equal resumable state.

## Rejected

- Keeping the per-tick serial chain: dominated runtime.
- A cryptographic hash: no adversary here, `Noise.mix` is enough.
- Hashing only a sample of the state: misses drift in the arrays not sampled.

## Consequence

The v5 digest `e2709232cf9636b6` and v5 lines that depended on its inputs are not comparable with v6 values.
