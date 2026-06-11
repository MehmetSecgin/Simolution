# 0001 — Node state uses double, CompiledConnection stays an object array (for now)

## Context
Kernel will later embed in a game loop; footprint and cache behavior matter. Candidates for shrinking: `double[]` → `float[]` state (halves memory), object-per-connection → structure-of-arrays (removes headers and pointer chasing).

## Decision
Keep `double` state and the `CompiledConnection` object array through single-unit v0.1.

## Why
Correctness against the spec is the current milestone; conformance tests assert exact double arithmetic. Switching representations now would optimize unmeasured code and complicate spec verification. Both switches are mechanical later and gated behind the multi-unit milestone, where a benchmark can justify them.

## Rejected
- Optimizing now: no measurement, no multi-unit workload to measure against.
- Promising never to switch: contradicts the elevator-brain budget.
