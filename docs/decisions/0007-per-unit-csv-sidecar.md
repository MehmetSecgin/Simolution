# 0007 — Per-unit detail as a CSV sidecar; report keeps aggregates

## Context
The owner wanted a far more extensive per-unit view: lifespan, energy burn rate, death tick, work done, etc. The report-v2 inline `## units` table only printed for ≤20 units and held few columns.

## Decision
Per-unit detail moves to a `.units.csv` sidecar (one row per unit, fixed unit order), written beside the report whenever `--out` is given. The report (now v3) drops the inline units table and instead carries a `## burn-rate` distribution section plus a `per-unit-detail` pointer line. CSV columns documented in docs/specs/report-v4.md.

## Why
- CSV is the right tool for the owner's intent — sort by lifespan, plot burn rate, correlate structure vs survival — without bespoke report code.
- Scales to any population: bounded by units, not ticks, so it never violates the memory discipline (all observer metrics remain O(units) running aggregates; nothing per-tick is retained).
- Keeps the report human-readable: aggregates and distributions in the report, raw rows in the CSV. Both stay byte-deterministic and diffable (fixed unit order).
- runs/baseline.units.csv is committed alongside runs/baseline.txt so per-unit baselines diff across kernel changes too.

## New per-unit metrics
lifespan, alive, energy_consumed, final_energy, mean_burn_rate, peak_burn (largest single-tick charge, derived from energy deltas — no kernel change), total_propagations, ticks_active, per-unit clamp_saturations and thresh_flips, plus existing regime/reachable/rand-wired/first-activity/final-abs-y/max-abs-output and per-unit connection counts.

## Rejected
- Inline table in the report: bloats to thousands of lines on big runs; the report should stay a readable summary.
- Storing per-tick per-unit history to enable richer stats: violates memory discipline (unbounded in ticks). All metrics are computed as running aggregates instead.

## Observed (baseline seed 42, 100u/1000t)
The two survivors (units 3, 78) both sit at the minimum burn rate (~1.0, near the pure structural-decay floor), are not sensor→action reachable, and are not noise-driven — empirical confirmation that quiet, lean wiring persists longest, with nothing selecting for it.
