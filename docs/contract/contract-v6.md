# Contract v6 — patchy + cyclic resource inflow

Delta over [contract-v5](contract-v5.md). Makes resource inflow **spatially
patchy and temporally oscillating**, breaking the spatial+temporal uniformity of
v1–v5. Until now every cell admitted `CELL_INFLOW` every tick — a homogeneous
world, which by competitive exclusion supports essentially **one** winning
strategy (a fast r-strategist that sweeps to monoculture; biomass goes unused
because nothing rewards storage). A pulsing source creates **niches** — center vs
edge, boom vs bust — so storage (grow during boom, survive the trough), timing,
migration, and dormancy can finally pay. This is the *fluctuating/patchy inflow*
roadmap item.

**Status: implemented** (kernel v6, ADR 0029), **opt-in**. Off by default, so the
uniform world — and the committed baseline — stays byte-identical (`state-digest`
unchanged). Geometry, energy, biomass, fission, determinism all carry over from
v5; only the inflow *distribution* changes.

## 1. Inflow becomes a configurable pattern

* A new `InflowConfig` (kernel `config`) selects the inflow law. `UNIFORM` is the
  v1–v5 default: every cell admits `CELL_INFLOW` (contract v1 §5), unchanged.
* The kernel reads it via `Kernel.configureInflow(InflowConfig)`, called once
  before ticking. It is **not** a constructor parameter (the Kernel is an engine,
  not a DTO): an unconfigured kernel stays uniform, so no existing call site or
  test changes (ADR 0029). The `RunConfig` *record* does get the field, with its
  call sites updated explicitly (no defaulting — the global record rule).

## 2. The cyclic pattern

When cyclic, the per-cell inflow ceiling at a tick is:

```
inflowCap(cell, tick) =
    peak · triangle(tick)      if cell is within `radius` of the lattice centre
    0                          otherwise        (the periphery is barren)
```

* **Central disk.** Cells whose plain (non-toroidal) coordinate distance from
  `(W/2, W/2)` is ≤ `radius`. A disk in the middle of the displayed lattice.
* **Triangle wave.** `phase = tick mod period`; the wave rises `0 → 1` over the
  first half of the period and falls `1 → 0` over the second. So a run **starts at
  the trough** (empty source) and fills. A triangle (not a sine) keeps the kernel
  free of transcendental functions — exact and portable.
* **Diffusion does the rest.** The existing diffusion phase spreads the central
  pulse outward, so the field becomes a **breathing radial gradient**, richest at
  the centre at peak, draining everywhere at the trough.

## 3. Determinism — no wall-clock

The oscillation is a pure function of the integer `tick` (contract v3 §1): "every
10 minutes" would be a wall-clock, which is forbidden, so the period is in
**ticks**, not time. Same seed + same `InflowConfig` → identical history.

## 4. Conservation — unchanged

Inflow stays the sole audited source (`cumulativeInflow` sums whatever is actually
admitted, capped at `CELL_CAPACITY` as before). Only the *distribution* of inflow
in space and time changes, not the accounting, so the closed-system audit
(contract v5 §6) holds — `energy-audit-error` stays ~0. Cells still **start** at
`CELL_INITIAL`, so a cyclic run has an initial resource stock everywhere that the
barren periphery then drains (a grace period before the periphery dies).

## 5. CLI

* `--resource-cycle` — enable (default off).
* `--cycle-period N` — period in ticks (default 2000).
* `--cycle-radius N` — central disk radius in cells (default `W/5`).
* `--cycle-peak X` — peak inflow per central cell (default 6.0).

## 6. What does not change

Everything in v5 (mass, GROW, fission, costs, the 10-phase pipeline, the
substrate, determinism). Inflow's *ceiling per cell per tick* is the only changed
quantity, inside the existing intake phase (no new phase).

## Deliberate omissions (future knobs)

* **Replay of cyclic runs.** The report-v8 manifest does not yet carry the inflow
  pattern, so `--resource-cycle` with `--observe` is **rejected** (guarded), not
  silently wrong. Threading `InflowConfig` through `RunManifest`/`ConfigHash` is a
  follow-up.
* **One central disk only.** No multiple patches, moving patches, rotating
  gradients, or per-cell noise — a single centred pulse. Richer spatial structure
  is a later addition.
* **Seeding from evolved founders.** Random founders die fast in this harsh world
  (extinction before adaptation — the bootstrap problem, worse than uniform).
  Seeding a cyclic run from an already-adapted lineage (e.g. a uniform-world
  facultative grower) to watch it adapt is a natural next experiment, not yet
  supported.
