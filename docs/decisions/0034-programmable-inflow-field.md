# 0034 — Programmable inflow field (composable sources + value noise)

**Status:** accepted · **Date:** 2026-06-16 · **Milestone:** kernel v7 (config/harness) · **Spec:** [contract-v7](../contract/contract-v7.md)

## Context

v6 (ADR 0029) shipped inflow as a single hardcoded central pulsing disk. The v6
omissions — *multiple patches*, *moving patches* — and the owner's framing ("we
hold a pixel grid; give coordinates or functions that resolve to them … later use
noise maps like Minecraft worldgen") point at the real abstraction: the inflow
field is a pure function `f(x, y, tick)`, and a disk is just one way to draw it.
Adding each new shape as another boolean+param tuple on the `InflowConfig` record
was the "too manual" path to avoid.

## Decisions

1. **Sealed `InflowSource` seam, not disk-special.** The field is
   `(baseline, InflowSource[] sources, Combine{MAX,ADD})`. Sources: `Disk`
   (STATIC/ORBIT/DRIFT motion, optional pulse), `Rect`, `Points` (explicit cells),
   `Noise` (value-noise worldgen). New patterns become new records in the seam, not
   new flags. Coordinates are fractional so a spec is world-size-independent.
2. **Static bake into `CompiledInflowField`.** `InflowConfig.compile(W)` folds all
   `isStatic()` sources + baseline once into a `double[cells]` layer (the
   GenomeCompiler pattern); time-varying sources stay live. Hot-path `cap(cell,tick)`
   reads an array plus dynamic sources — zero per-tick allocation. The one new
   kernel-adjacent allocation (a cell-sized array) is O(cells), constant in ticks,
   and **allocated only when a static non-baseline source exists** — `UNIFORM`
   allocates nothing, so the baseline run is footprint-neutral and byte-identical
   (`b3f5198071d3d9e5`, verified).
3. **Value noise, not Perlin.** Integer-lattice corner hashes (reusing
   `Noise.mix`) + quintic-fade interpolation, no transcendental functions →
   bit-stable across JVMs. Gradient/Perlin noise needs gradient dot-products and is
   easy to get platform-divergent; value noise keeps the determinism invariant for
   the whole environment. Threshold sets coverage, gain sets edge sharpness; a 3rd
   noise axis `z = tick/animPeriod` animates.
4. **DSL over per-shape flags.** `--inflow "<spec>"` parses composable sources
   (`disk|rect|points|noise` + `key=val`, `;`-separated, `combine=`/`baseline=`).
   `--resource-cycle` stays as sugar for the central disk (CLI flag, not a record
   shim — allowed). Mutually exclusive guard.
5. **Record reshape, no shim.** `InflowConfig`'s components changed; every call
   site updated directly (global no-shim rule). `UNIFORM`/`cyclic(...)` are real
   named factories building the new representation, not forwarders.
6. **`configureInflow` compiles internally** (Kernel knows W); the only kernel
   change is the field type + one `cap` call. No phase reorder, no serialized state
   (replay still guards non-uniform inflow off).

## Equivalence

The `cyclic()` factory builds one STATIC pulsing `Disk` at `fx=fy=0.5`. For even
worlds `0.5·W == W/2` exactly, so the cap is byte-identical to v6 (locked by the
exact-value centre/periphery conformance test). Odd worlds shift the centre ½ cell
(no current/test world is odd). Baseline byte-identical; a noise + drifting-disk
run conserves energy (`energy-audit-error` ~3.9e-3 over 6.6e7 inflow, FP noise).

## Rejected

- **In-kernel lambda / function injection for inflow** — would smuggle
  interpretation into the kernel and break the no-allocation/no-boxing doctrine.
  The sealed seam keeps it pure data the kernel only reads.
- **Full expression interpreter** (`peak*sin(...)` strings) — overkill, and trig
  reopens the determinism risk. A fixed source vocabulary covers the worldgen need.
- **Perlin/gradient noise** — non-determinism risk across platforms; value noise is
  trig-free and sufficient.
- **Per-tick centre recompute for moving patches** — accepted as-is (per-cell trig
  for ORBIT over a handful of patches is cheap). If profiling bites, precompute
  per-tick centres into a Kernel buffer when phase-5 parallelization (ADR 0025) is
  taken. Deferred to keep `cap` a pure, parallel-safe function.
