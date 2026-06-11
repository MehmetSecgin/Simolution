# 0005 — MUL: top-2 product via compile-time connection split

## Context
Spec (slice §2): MUL "multiplies the two strongest incoming signals". The propagation accumulator sums signals, destroying the individual values MUL needs. Placeholder pass-through had MUL ≡ ADD.

## Decision
Connections are split at kernel construction (structure-only precompute, cache-spec legal): non-MUL connections keep the branch-free sum loop; MUL-destined connections run a second loop maintaining per-unit top-2 signals by |magnitude|, signed values kept. Cost: 2 doubles + 1 int per unit (~468 B/unit total), zero hot-loop branches.

Output by structural in-degree:
- 0 inputs → 0
- 1 input → that signal, passed through
- ≥2 inputs → product of the two strongest

## Why single-input passes through
The binding slice's own example genome (§5) wires DELAY → MUL → ADD — MUL with one structural input — and §12 declares implementations failing to reproduce those dynamics incorrect. Strict "two or nothing" would zero the spec's own feedback loop. Pass-through keeps the example alive; a lone signal has nothing to gate, so identity is the least-semantic choice.

## Sub-decisions
- Ties in |signal| resolve to the earlier connection in genome order (deterministic).
- Non-finite signals lose every top-2 comparison (NaN compares false) and never enter the slots; divergence classification is the observer's job.
- "Strongest" is per-tick from actual signals, not weights: a strong connection from a silent source loses to a weak one from a loud source.

## Rejected
- Spec change to product-of-all-inputs: worse blowup (4^k vs 16×), needs a dormancy special case.
- Keeping pass-through: MUL ≡ ADD, substrate loses its only multiplicative (gating) primitive, bends a binding spec for convenience.
- Top-2 arrays sized totalNodes: +224 B/unit for one MUL instance per unit.

## Observed effect (baseline diff, seed 42, 100u/1000t)
Divergent 20 → 15, fixed-point 11 → 13, bounded 69 → 72, propagations −5%. Calmer universe: MUL products of sub-unit signals shrink instead of accumulating, fewer feedback explosions.
