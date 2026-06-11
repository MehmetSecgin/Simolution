# 0006 — Energy accounting: phase 5, decay + activity cost, death by invariant

## Context
Contract v0's core thesis: persistence under entropy. Units must hold energy, pay to exist and to compute, and die when it runs out. This is roadmap step 3 and the reason the project exists.

## Decisions (the four forks, as discussed)

**Tick gains phase 5 — settle energy.** After swap, each living unit is charged decay + activity cost. An additional phase, not a modification of phases 1–4 (talk doc: new mechanics are new phases).

**1. Activity cost ∝ non-zero propagations** (not evaluated-connection count). Smoother selection gradient, rewards sparse efficient wiring, and reconciles exactly with the observer's `signal-propagations-total`.

**2. Structural decay ∝ connection count** charged every tick regardless of activity. Currently connection count == gene count because the encoding makes every gene valid (no gene is ever dropped), so this is faithful to slice §3 ("junk counts toward decay"). If invalid genes ever become droppable, decay must switch to original gene count.

**3. Death-tick charge clamps to available energy.** No overdraw: the sink receives exactly what existed, so `INITIAL_ENERGY × units == sum(energy) + sink` holds to floating-point exactness. Reported every run as `energy-audit-error` (NaN-saga lesson: calibrate instruments for extremes before extremes arrive).

**4. Dead units skipped via per-unit connection ranges.** compileAll groups connections by unit; precomputed [start,end) ranges let a dead unit's whole range skip with one branch, not one per connection. A dying population costs less to simulate — thermodynamically faithful. Activity-per-unit counting falls out of the same loop.

## Notable sub-decisions
- **No `alive[]` array.** Alive ≡ energy > 0, derived. Contract §9 "death is a failed invariant, not an event" maps to not storing a flag.
- **Dead units freeze** (previous outputs carried forward) rather than going stale across the double buffer — inert corpse, deterministic.
- **Empty-structure immortality**: zero connections → zero decay → never dies. Logically consistent (no structure, no rent); only reachable with hand-built empty genomes.
- Energy folded into the state digest; KernelSnapshot gained `energy[]` + `energySink` (no compat shim, all call sites updated).

## Constants (calibrated, INITIAL_ENERGY=1000, DECAY=0.02, COST=0.06)
Pure timescale knobs, no principled value. Calibrated on the standard baseline (seed 42, 100u/1000t) for a visible death curve with survivors: deaths 483→944, median 677, 2 survivors. Cost-dominated ratio (decay:cost = 1:3) deliberately widens the curve — busy units die early, quiet lean units persist — which is the selection pressure the thesis wants. Recalibrate only with a documented run.

## Rejected
- Activity cost ∝ evaluated connections: punishes structure you have, not work you do; coarser gradient.
- Allowing negative energy: breaks the conservation audit, meaningless physically (can't spend ATP you lack).
- `alive[]` flag / death event: contradicts §9 and stores derivable state.
