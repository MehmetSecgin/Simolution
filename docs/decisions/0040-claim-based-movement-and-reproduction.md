# 0040: Claim-based movement and reproduction

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Spec:** [contract-v9](../contract/contract-v9.md) §1, §2

## Context

v5 resolved both phases by scanning slots in ascending order and mutating as it went: earlier units won contested cells, a cell vacated this tick could be entered this tick, a failed build burned a slot, and the child slot keyed the mutation RNG. None of that can be split across threads.

## Decision

Both phases become propose-then-resolve. Proposals are computed in parallel from phase-start state. A serial resolve then picks winners in ascending slot order: the lowest slot wins a contested cell, an unaffordable parent drops out before any claim and pays nothing, winners take free slots in ascending order, and the tick's births are published as a plain array (`birthParent`, `birthChild`, `birthChildCell`).

## Why

The expensive part (reading outputs, finding a target, computing the child genome length and cost) runs in parallel, and the part that must be ordered is a cheap linear pass. The tie rule is the old one (lowest slot), so the biology only changes where it had to: no chains, no burned slots. The published birth list lets the writers stop inferring parents from geometry.

## Rejected

- Keeping the serial scan: it was the largest serial cost and blocked thread-count independence.
- A random tie-break: needs another keyed stream and gains nothing.
- Allowing chains by iterating to a fixed point: order-dependent in disguise.

## Consequence

The resolve passes stay serial, so they cap parallel speed-up (see the stopping-rule result in [engine-v6-bench](../notes/engine-v6-bench.md)). The baseline moved once, explained in [report-v15](../specs/report-v15.md).

The resolves were made parallel later, in [0046](0046-parallel-claim-resolution.md).
