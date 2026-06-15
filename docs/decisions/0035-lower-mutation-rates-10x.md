# 0035 — Lower mutation + indel rates 10×

**Status:** accepted · **Date:** 2026-06-16 · **Milestone:** kernel v5+ (calibration) · **Relates:** [ADR 0021](0021-differentiated-mutation-rates.md), [ADR 0026](0026-variable-length-genomes.md)

## Context

On an 8000-tick cyclic/moving-band run a winning clade reached ~510 generations,
and its genome **churned every few generations** — over the ancestral span ≈66
weight-bit flips, ≈13 structural rewires, ≈32 indels. Decoding "how a lineage
evolved" was dominated by noise: the conserved strategy core was real, but it was
buried under constant edge add/drop. The owner judged the rates too high — 8000
ticks is not long for that much drift to be legible.

## Decision

Scale all four per-bit/per-gene rates **uniformly ×0.1** (preserving the ADR-0021
weight:struct ratio and the dup=del symmetry):

| const | was | now |
|---|---|---|
| `MUTATION_RATE_WEIGHT` | 2.5e-4 | 2.5e-5 |
| `MUTATION_RATE_STRUCT` | 5e-5 | 5e-6 |
| `INDEL_RATE_DUP` | 1e-3 | 1e-4 |
| `INDEL_RATE_DEL` | 1e-3 | 1e-4 |

Per birth on a 32-gene genome this drops expected events to ≈0.013 weight flips,
≈0.0026 rewires, ≈0.0032 dup, ≈0.0032 del — so a ~500-generation chain accumulates
only a handful of changes, and a lineage's evolution becomes trackable while
evolution still happens.

## Why uniform ×0.1

The relative balance (weight mutates 5× more than structure; indels symmetric) was
already calibrated (ADR 0021/0026) and not the complaint — only the absolute pace.
A single global factor keeps that balance and is the least-surprising knob.

## Consequences

- **Baseline digest changes** (expected — these are kernel-behavior constants):
  `b3f5198071d3d9e5 → 1f97e28f3e369e9b`. Gross dynamics on the 1000-tick baseline
  are unchanged (births 1364→1363, peak-pop 1020→1016, max-gen 5, audit ~5e-8); the
  diff is sub-rounding micro-trajectory only. Prior ADRs/specs that quote
  `b3f5198071d3d9e5` describe the pre-change tree and stay as historical record.
- Generation *count* is unaffected — that is reproduction/fission speed, not
  mutation. This change slows genome *drift per division*, not the division rate.

## Rejected

- **Per-rate hand-tuning** — no evidence the balance is wrong, only the pace.
- **Lowering reproduction instead** — the owner's concern was mutation churn, not
  how many generations occur; fission economics are a separate lever.
