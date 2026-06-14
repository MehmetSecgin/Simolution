# 0028 — Biomass (Pirt size physics): mass, growth, fission

**Status:** accepted · **Date:** 2026-06-14 · **Milestone:** kernel v5 · **Spec:** [contract-v5](../contract/contract-v5.md)

## Context

Roadmap item 11. Add a unit's *size* as a distinct state variable, with the
Pirt maintenance/growth split, so size becomes an evolvable trade-off rather than
an energy proxy. Design was settled in conversation (see contract-v5); this records
what was built and the calls made along the way.

## Decisions

1. **Mass = crystallized energy, one currency.** `mass[slot]` is a reservoir of
   energy in structural form, not a new resource. The closed-system audit gains a
   `Σ mass` term on both sides (founders provisioned `INITIAL_MASS`); `GROW`'s
   conversion loss, maintenance, and death-dissipation all flow to the sink, so
   `energy-audit-error` stays ~0. Distinct "matter" is deferred to multi-resource.
2. **`GROW` effector + `SELF_MASS` sensor.** Growth is an evolved choice
   (`GROW_MAX·σ(out)`, lossy `GROW_YIELD`), never automatic. `SELF_MASS` gives
   proprioception so the size-control checkpoint can evolve. `NodeLayout.TOTAL`
   22→24 (sensors 6→7, actions 8→9) — re-maps gene decoding, so the baseline digest
   changes (as v3's MOVE effectors did).
3. **Maintenance ∝ mass replaces ∝ energy.** The v1 maintenance term's own comment
   said "energy proxies biomass"; v5 makes it real. `STORAGE_LEAK_RATE` retired →
   `MAINT_PER_MASS`. Keeping both would double-charge biomass.
4. **Harvest ∝ mass^α (α=0.5).** Sublinear (surface) intake against linear
   (volume) maintenance → a finite optimal size. The one hand-asserted surface law
   (it would emerge from geometry under a multi-cell model); disclosed, not smuggled.
5. **Reproduction = symmetric binary fission.** Split mass + energy 50/50;
   `REPRODUCE` is a bare trigger (`out>0`), so `REPRODUCE_MAX` /
   `REPRODUCE_HALF_SATURATION` / `REPRODUCE_YIELD` are retired. **No viability gate**
   — premature division is possible but costly (wasted `buildCost`, halved into a
   doomed undersized child); the checkpoint is left to evolve via `SELF_MASS`. The
   parent always keeps `(energy−buildCost)/2 > 0`, so **fission is never terminal** —
   it frees no cell, which makes ADR 0024's reproductive-death path moot in v5.
6. **Growth is a new phase 7** (pipeline 9→10: eat → cost → grow → fission → move →
   diffuse). Inserting between cost and reproduction preserves every existing
   relative ordering, honoring the locked-pipeline invariant.
7. **Death dissipates mass to the sink** via a `die(unit)` helper at the two sites a
   charge can cross to `energy≤0`: `settleCost` and `settleGrowth`. Fission and
   movement cannot kill (parent keeps half; move afford-check is strict).

## Calibration (set once, then frozen — not tuned to an outcome)

`INITIAL_MASS=1.0`, `MAINT_PER_MASS=0.5`, `HARVEST_MASS_EXPONENT=0.5`,
`GROW_MAX=5.0`, `GROW_HALF_SATURATION=1.0`, `GROW_YIELD=0.8`,
`MOVE_COST_BASE=0.2` (renamed from `MOVE_COST`), `MOVE_COST_PER_MASS=0.1`,
`SELF_MASS_SCALE=10.0`.

## Baseline (seed 42, world 100, 100u, 1000t)

Digest `38e7da02ca42e804` → `b3f5198071d3d9e5`; `energy-audit-error` 5.1e-8 (mass
conserved). births 1060→1364, peak-pop 317→**1020**, max-gen 39→**5**,
final-energy 477→**8842**. The shallow lineages confirm the **cell cycle is now
physical**: mass halves each division and a non-growing lineage's harvest (∝mass^α)
falls below `BASAL_COST` after ~5 halvings, so it cannot divide further — random
founders don't evolve `GROW` in 1000 ticks. The population blooms (cheap fission)
then busts (offspring shrink below viability).

## Watch-item

Final energy rose 18× — retiring the ∝energy leak (decision 3) lets survivors hoard
energy with no size to pay for it. Honest (aging still ultimately kills them) and
expected, but if a long *evolved* run shows pathological hoarding, re-add a small
reserve-holding term (∝ energy) with its own ADR. Not retuned now: observe-first
discipline, and a random-founder 1000-tick baseline is the wrong evidence to tune on.

## Rejected

- **Keep ∝energy maintenance** — double-counts biomass once real mass exists.
- **Viability gate / `M_DIV_MIN`** — the kernel refusing a too-small division is the
  kernel protecting a unit from a bad strategy = smuggled semantics. The guard must
  emerge (real cells evolved nucleoid occlusion / Min / SOS for the same reason).
- **Append growth after diffusion** — would lag mass effects a tick and divorce
  growth from the metabolic sequence.
