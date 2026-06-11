# 0002 — Multi-unit: one flat array set, unit count as constructor arg

## Context
Kernel must run many units. Layout already reserved space (`unitCount * NodeLayout.TOTAL`) but evaluation hardcoded unit 0.

## Decision
One Kernel instance owns the whole population. All units share the four state arrays, unit u's nodes at `u * NodeLayout.TOTAL + offset`. Connections compiled per unit with that offset (`GenomeCompiler.compileAll`), then concatenated; propagation stays one unit-agnostic loop over all connections. Evaluation loops units. Unit count moved from `KernelConfig.UNIT_COUNT` to a `Kernel` constructor argument.

## Why
Flat shared arrays: no per-unit object overhead, cache-linear, trivial snapshot. Propagation untouched because indices are absolute. Constructor arg because population size is per-simulation runtime data, not a kernel law; contract v0 wants preallocated fixed-capacity slots, which this models directly.

## Rejected
- Kernel-per-unit: object graphs, per-instance buffers, contradicts data-oriented doctrine.
- Per-unit array sets: same memory, worse locality, more indirection.
