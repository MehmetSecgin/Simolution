# 0046: Parallel claim resolution

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Spec:** [contract-v9](../contract/contract-v9.md) §1, §2, §7 · **Amends:** [0040](0040-claim-based-movement-and-reproduction.md)

## Context

ADR 0040 kept the claim resolves serial. With the snapshot totals, they were about 21% of a `big500` tick at one thread, which held 4 threads at 2.07x over 1 (stopping rule 2 wants 2.5x).

## Decision

- A phase may declare split **resolve passes** after `run`, each preceded by a serial `prepareResolve` on the caller. The runner treats every pass like a phase: fixed chunks, one barrier.
- **Winner pick:** a proposer claims its cell in `cellWinner` with an atomic minimum (`CellClaim`, a VarHandle compare-and-set). The minimum of a set does not depend on arrival order, so after the propose barrier each entry is the lowest claiming slot, as the serial scan picked it.
- **Movement:** one resolve pass; winners move and add their cost to their chunk's sink partial.
- **Reproduction:** propose counts free slots per chunk. Judge pass writes the world's free slots, ascending, into `birthChild` at the chunk's prefix offset and counts winners per chunk. Install pass gives the winner of rank `k` the slot `birthChild[k]`. `finish` walks the birth list once to add build costs to the sink in ascending parent order, so the sink stays bit-identical to the serial resolve.
- **Child length:** the propose skips the dry-run `mutate` when the parent can afford the longest child the indels could build (`min(maxGenes, 2·count)` genes); the install charges the cost of the length its real `mutate` returns.
- **Snapshot:** `reservoir()` and `massTotal()` sum on demand.

## Why

All four leave `runs/baseline.txt` byte-identical (digest `78644e65e8086e45`) at 1, 3 and 4 threads. What stays serial is O(chunks) prefixes and an O(births) walk. `birthChild` doubles as the free-slot list, so no per-slot array is added; the only new state is two per-chunk counters.

## Rejected

- A gather pass (each claimant scans its target's 8 neighbours for a lower claimant): no atomics, but one more barrier per phase, and movement's apply would race with it on `cellOccupant`.
- A per-parent child-genome scratch so the install reuses the dry run: `maxGenes` ints per slot.
- Geometric skip sampling for indel draws: removes most dry-run work, but changes which genes are deleted or duplicated, so it is a contract change, not a refactor.

## Consequence

The movement sink is now summed per chunk like every other phase, not in one ascending pass. The baseline did not move; on `dense200` (3000 ticks) the report differs from before only in `energy-sink` (last three digits), `energy-audit-error` (6e-8 to 3e-7, still noise) and the digest. Nothing in the dynamics reads the sink. The ceiling skip rarely fires on `big500` (most dividers sit just above their own build cost), so the dry run is still about 12% of a one-thread tick there; it runs in parallel.
