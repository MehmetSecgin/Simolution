# 0029 — Patchy + cyclic resource inflow (opt-in)

**Status:** accepted · **Date:** 2026-06-14 · **Milestone:** kernel v6 · **Spec:** [contract-v6](../contract/contract-v6.md)

## Context

The uniform world (same inflow everywhere, every tick) is homogeneous, so by
competitive exclusion it rewards exactly one strategy. Observed directly: across
~14 seeds the survivors all converge on a fast r-strategist that sweeps to
monoculture (lineage 48 in seed 271828 even *neutralized* its own GROW), and
biomass is unused because nothing rewards storage. To get strategy diversity we
need environmental heterogeneity. The owner asked for an oscillating central
resource patch.

## Decisions

1. **Opt-in `InflowConfig`, default `UNIFORM`.** A pulsing central disk:
   `inflowCap = peak·triangle(tick)` inside `radius` of the lattice centre, `0`
   outside (barren periphery); diffusion spreads it into a breathing gradient.
   Default uniform → baseline `state-digest` unchanged (`b3f5198071d3d9e5`).
2. **`Kernel.configureInflow(InflowConfig)`, not a constructor parameter.** The
   Kernel is a behavioural engine, not a record/DTO, so the global no-shim rule
   (which targets record signatures) does not force a constructor change. A config
   method keeps all 32 `new Kernel(...)` call sites and every test byte-identical
   to before — the alternative (a 5th constructor param) would have churned 32
   sites to write `InflowConfig.UNIFORM` with zero behavioural change. The
   `RunConfig` *record* DOES get the field, with its call sites updated explicitly.
3. **Triangle wave, period in ticks.** No `Math.cos` (exact, portable); no
   wall-clock (determinism, contract v3 §1) — "every 10 min" becomes N ticks.
4. **Conservation untouched.** Inflow stays the audited source; only its
   distribution changes. `energy-audit-error` stays ~0 (verified, cyclic run 6.5e-7).
5. **Replay guarded.** `--resource-cycle` + `--observe` throws (the manifest does
   not carry the inflow pattern yet) rather than replaying wrong.

## Finding

The cyclic world is **harsher** than uniform: with a barren periphery and a
pulsing centre, random founders bloom (peak ~2700) then go **extinct** by ~3k
ticks (seed 42, radius 22, peak 10, period 2000). Same bootstrap problem as
uniform, amplified — the population dies before evolution finds the storage
strategy the cycle would reward. The patch is invisible while extinct (cells start
full at `CELL_INITIAL` and nothing drains them). Surfacing an adapted strategy
will likely need either a seed sweep for the rare survivor or seeding from an
already-adapted lineage (deferred, contract-v6 omissions). Not a knob-tuning
problem to "fix" — it's the honest selective pressure of the world.

## Rejected

- **Constructor param for inflow** — correct-by-DTO-rules but 32 sites of churn
  for an engine that isn't a DTO; `configureInflow` is cleaner and keeps the
  baseline provably untouched.
- **Sine wave** — needs `Math.cos`; triangle is exact and kernel-appropriate.
- **Baseline-everywhere trickle** — the owner chose a barren periphery (stronger
  selection for positioning/migration/storage).
