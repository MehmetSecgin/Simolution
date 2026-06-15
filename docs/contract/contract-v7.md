# Contract v7 — programmable inflow field (composable sources + value noise)

Delta over [contract-v6](contract-v6.md). Generalizes resource inflow from the
v6 **single central pulsing disk** to a **programmable field**: the inflow ceiling
at a cell is a pure function `f(x, y, tick)` drawn by a list of composable
**sources**. The disk stops being privileged — it is one shape alongside
rectangles, explicit coordinate masks, and seeded **value-noise** maps
(Minecraft-style worldgen: threshold a deterministic noise field into emergent
resource regions, no hand-placed coordinates). Closes the v6 omissions *multiple
patches* and *moving patches*, and lays the worldgen foundation for richer
environments.

**Status: implemented** (kernel unchanged — harness/config only, ADR 0034),
**opt-in**. Off by default: `UNIFORM` stays the v1–v5 law, so the committed
baseline is byte-identical (`state-digest b3f5198071d3d9e5` unchanged). Geometry,
energy, biomass, fission, determinism all carry over from v5/v6; only the inflow
*distribution* changes.

## 1. The field is a list of sources

`InflowConfig` (kernel `config`) becomes `(double baseline, InflowSource[] sources,
Combine combine)`. The cap at a cell is:

```
inflowCap(cell, tick) = combine( baseline, source₀.at(x,y,tick), source₁.at(x,y,tick), … )
```

* `combine ∈ {MAX, ADD}` — MAX treats sources as overlapping regions (cap is the
  richest source); ADD stacks them.
* `baseline` is the floor everywhere (the uniform trickle; `UNIFORM` is just
  `baseline = CELL_INFLOW` with no sources).
* The kernel still reads the field via `Kernel.configureInflow(InflowConfig)`,
  called once before ticking — **not** a constructor parameter (the Kernel is an
  engine, not a DTO; contract v6 §1, ADR 0029 reasoning carries over). An
  unconfigured kernel stays `UNIFORM`.

## 2. Sources (`InflowSource`, sealed)

Each source is a pure, deterministic function of `(x, y, tick, worldWidth)` and
reports `isStatic()` (time-invariant). Coordinates are **fractional** (`fx, fy ∈
[0,1]`, scaled by `W` at evaluation) so a spec is world-size-independent.

* **`Disk(fx, fy, radius, peak, periodTicks, phaseTicks, motion, m0, m1, motionPeriod)`** —
  a circular patch. `periodTicks > 0` pulses the cap `0 → peak → 0` as a triangle
  wave (the v6 wave, kept exact). `motion`:
  * `STATIC` — centre fixed at `(fx·W, fy·W)`.
  * `ORBIT` — centre circles the base point at fractional radius `m0` with angular
    period `motionPeriod` (uses `StrictMath.sin/cos` for bit-stable trig).
  * `DRIFT` — centre translates `(m0, m1)` cells/tick, toroidally wrapped.
  Membership is **toroidal-aware** (the disk wraps across edges).
* **`Rect(fx0, fy0, fx1, fy1, peak, periodTicks, phaseTicks)`** — an axis-aligned
  region; optional pulse.
* **`Points(cells[], peak, periodTicks, phaseTicks)`** — an explicit set of cell
  indices (`y·W + x`); the "give exact coordinates" case.
* **`Noise(seed, frequency, threshold, gain, peak, animPeriod)`** — a seeded
  value-noise map (see §3). `(noise − threshold)·gain`, clamped to `[0,1]`, scales
  `peak`, so `threshold` sets coverage and `gain` sets edge sharpness.
  `animPeriod > 0` feeds a slow time axis `z = tick/animPeriod`, morphing the
  field; `0` freezes it (static).

## 3. Value noise — deterministic, trig-free

The noise source uses **value noise**: integer-lattice corner hashes (the
`Noise.mix` SplitMix64 finalizer reused verbatim) interpolated with a quintic
fade, sampled in 3D `(x·freq, y·freq, z)`. No transcendental functions, so the
whole field is **bit-identical across JVMs/platforms** — the determinism invariant
(contract v3 §1) covers the environment, not just the RNG. Same `(seed, coords)` →
same value, always.

## 4. Compilation — static bake

`InflowConfig.compile(worldWidth)` produces a `CompiledInflowField` (the
`GenomeCompiler` analogue). All `isStatic()` sources + the baseline are folded once
into a precomputed `double[cells]` layer; time-varying sources stay live. The hot
path (`cap(cell, tick)`, called per cell per tick in the intake phase) then reads
an array plus any dynamic sources — zero per-tick allocation, primitives only
(performance doctrine). When the field is the bare `CELL_INFLOW` baseline
(`UNIFORM`), no array is allocated and `cap` returns the baseline directly — the
baseline run is footprint-neutral and byte-identical.

## 5. Determinism — no wall-clock

Pulses, motion, and noise are pure functions of the integer `tick` (contract v3
§1); periods are in **ticks**, never wall-clock. `StrictMath` for orbit trig and
trig-free value noise keep histories bit-reproducible. Same seed + same
`InflowConfig` → identical history.

## 6. Conservation — unchanged

Inflow stays the sole audited source (`cumulativeInflow` sums whatever is actually
admitted, capped at `CELL_CAPACITY`). Only the *distribution* of inflow in space
and time changes, not the accounting, so the closed-system audit (contract v5 §6)
holds — `energy-audit-error` stays ~0 (verified on a noise + drifting-disk run).

## 7. CLI

* `--inflow "<spec>"` — the programmable field. Sources separated by `;`, each a
  keyword (`disk|rect|points|noise`) followed by `key=val` pairs; bare
  `combine=max|add` and `baseline=N` segments set field-level options. Example:
  ```
  --inflow "noise seed=7 freq=0.05 thr=0.55 gain=3 peak=10 anim=6000; \
            disk fx=0.8 fy=0.2 r=8 peak=8 period=1500 drift=0.005,0.003; combine=max"
  ```
  Keys: `fx fy fx0 fy0 fx1 fy1 r peak period phase seed freq thr gain anim cells
  orbit=R,period drift=vx,vy`.
* `--resource-cycle / --cycle-period / --cycle-radius / --cycle-peak` — kept as
  **sugar** for a single central pulsing disk (the v6 surface). Mutually exclusive
  with `--inflow`.

## 8. What does not change

Everything in v5/v6 (mass, GROW, fission, costs, the 10-phase pipeline, the
substrate, determinism, diffusion spreading the field). Inflow's *ceiling per cell
per tick* is the only changed quantity, inside the existing intake phase — no new
phase, no kernel-state change beyond the optional baked layer.

## Deliberate omissions (future knobs)

* **Replay of non-uniform inflow.** The report-v8 manifest still does not carry the
  inflow field, so any non-`UNIFORM` inflow with `--observe` is **rejected**
  (guarded). Threading `InflowConfig` through `RunManifest`/`ConfigHash` is a
  follow-up (the v6 omission, now generalized).
* **Seeding from evolved founders.** Unchanged from v6 — random founders still die
  fast in a harsh patchy world; seeding an adapted lineage is a separate
  experiment.
* **Richer noise.** Single-octave value noise only — no fractal/fBm octaves,
  domain warp, or gradient (Perlin) noise yet. Each is a new `InflowSource` impl or
  a `ValueNoise` extension, dropping into the same seam.
