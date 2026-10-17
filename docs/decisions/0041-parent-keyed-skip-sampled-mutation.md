# 0041: Parent-keyed, skip-sampled mutation

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Spec:** [contract-v9](../contract/contract-v9.md) §3, §4 · **Supersedes in part:** [0026](0026-variable-length-genomes.md) (keying)

## Context

v5 keyed mutation by `(seed, childSlot, tick, op)` and drew one hash per bit: about 32 three-mix hashes per gene per birth, 16% of a dense run. The child slot is only known after the claim resolve, so the child length (and with it the build cost) could not be known earlier. The constant `RANDOM_SEED` also drove RAND, mutation and placement, so `--seed` changed none of them.

## Decision

- The key is `(seed, STREAM_MUTATION, parentSlot, birthTick, op)`. A parent divides once per tick, so keys stay unique, and the child's genome and length are known before any slot is.
- Point mutation draws the geometric gap to the next flipped bit, per class (weight bits at `MUTATION_RATE_WEIGHT`, structure bits at `MUTATION_RATE_STRUCT`), with `StrictMath.log`. At about 1e-5 per bit almost no draws remain. Deletion and duplication draws and the one monotone `op` counter are unchanged.
- Every stream is tagged (`RAND` 1, mutation 2, placement 3) and mixed with the run seed.

## Why

The propose step can compute `buildCost` from the key alone, and mutation cost drops. Same rates, so the same distribution of mutations; only which bits flip for a seed differs, inside the one baseline change.

## Rejected

- Keeping per-bit draws with a parent key: correct but the largest avoidable cost.
- Keying by the child's slot after assignment: forces a serial genome build.
- A shared stream per phase: tagged streams cannot collide as more are added.
