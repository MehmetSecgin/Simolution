# 0010 — Energy intake implementation (the deferred forks, decided)

## Context
Contract-v1 + ADR 0009 locked the energy-intake *design* and explicitly left three
forks "to be settled with an ADR when built" (contract-v1 §8). This ADR decides
those, plus the sub-decisions implementation forced (reservoir bounding, sensor
scaling, audit shape). Binding spec is still contract-v1; this records *how* the
build realises it.

## Decisions

**Depletable pool from the start (not infinite-field-first).** The infinite flat
field was only ever a throwaway harvest-mechanism validation (ADR 0009). Building
it would cost a second ADR + baseline regen to then delete. The depletable pool is
the lasting model (contract-v1 §4), so it is built once, correctly.

**Dedicated HARVEST action + dedicated RESOURCE sensor, both new *meaningful*
nodes.** ACTION_Y stays the generic channel; HARVEST is explicit and measurable
(contract-v1 §8 leaning). RESOURCE is the first real sensor. Junk padding counts
are unchanged, so the substrate grows by exactly two meaningful nodes
(`NodeLayout.TOTAL` 9 → 11). Per-unit footprint grows 2 × 4 arrays × 8 B = 64 B/unit;
justified: these are the milestone, not incidental. Memory stays linear in units,
constant in ticks (memory doctrine intact).

**Intake before cost, as a new phase 6.** Phases 1–5 are untouched (tick pipeline
locked). A unit harvests while alive this tick, and that energy is available to pay
this tick's metabolic bill — so a well-adapted harvester reaches steady state
(persistence, the whole point of opening the system). Death stays a *between-tick*
derived predicate (`energy ≤ 0` observed at next tick start); "eating its way out
of dying" is therefore a non-event — only the net `intake − cost` over the tick
decides survival. Order is uniform for all units (no semantics smuggled).

**Bounded reservoir; RESOURCE sensor emits `level / capacity ∈ [0,1]`.** An
unbounded inflow-fed pool would grow without limit and the raw-level sensor signal
would eventually dominate all wiring. A capacity bounds both. The sensor is scaled
to [0,1] so it sits at the same O(1) scale as CONST (1.0) and RAND — the
*connection weight remains the only gain knob* (contract-v1 §6). Scaling a raw
point measurement by a fixed unit is still transduction, not perception (§2): no
relationship/history is computed in the kernel.

**Unified scarcity law.** `f(resourceAvailable)` (contract-v1 §5) and the
"proportional to demand" allocation are the *same* mechanism:

    demand_i = EFFICIENCY × max(0, harvestOutput_i)
    D        = Σ demand_i                      (population total, order-independent)
    intake_i = demand_i × min(1, R / D)        (R = current reservoir level)

When demand fits (`D ≤ R`) each unit gets its full demand; under scarcity every
unit is scaled by the same `R/D`. Deterministic, order-independent, conserving.

**Audit extended to an open system.** Energy flows reservoir → units → sink, and a
fixed inflow law tops up the reservoir each tick (the sun). Inflow is the
*accounted* source, not creation-from-nothing, so the invariant generalises to:

    INITIAL_unitEnergy×N + INITIAL_reservoir + cumulativeInflow
        == Σ unitEnergy + reservoir + sink

`cumulativeInflow` counts only energy actually admitted to the reservoir (inflow
beyond capacity is discarded and never entered the books). `energy-audit-error`
stays ~0 (FP noise only).

**Dead units neither harvest nor are fed.** The intake phase reuses the same
`energy ≤ 0` guard as propagate/evaluate/settle — a corpse computes nothing
(contract-v0 §9) and so demands nothing.

**Non-finite harvest demands nothing.** A divergent unit's HARVEST output
overflows to ±Inf/NaN (the substrate never clamps). Left raw, `Inf × min(1,R/Inf)`
= `Inf × 0` = NaN poisons the shared reservoir for everyone. So a non-finite
harvest output contributes zero demand. This guards IEEE pathology, not
behaviour — the same stance the observer already takes classifying non-finite as
divergent. Finite-but-huge outputs are untouched: proportional allocation simply
hands such a unit nearly the whole pool. Rejected alternative: let an Inf demand
take the pool — that lets a numerical artifact win resources, strictly worse.

## Constants (tunable, not semantic — chosen for a live, auditable baseline)
- `HARVEST_EFFICIENCY = 1.0` — intake on the same O(1) scale as metabolic cost, so
  a strong harvester can offset its bill.
- `RESOURCE_CAPACITY = 100000.0`, `RESOURCE_INITIAL = RESOURCE_CAPACITY` (full).
- `RESOURCE_INFLOW = 50.0` / tick (admitted up to capacity).

## Rejected
- Infinite-field-first (throwaway; see above).
- Reusing ACTION_Y as the mouth (harvest then unmeasurable, conflated with generic output).
- Unbounded reservoir / raw-level sensor (signal-scale blow-up, dominates wiring).
- Separate `f(·)` and proportional-allocation mechanisms (redundant; unified above).
- Coded per-unit harvest efficiency (ADR 0009 — redundant with weights, smuggles privilege).
- Merging intake and cost into one net transfer (breaks the reservoir/sink audit split).

## Deferred to later milestones
Reproduction + mutation (next); CROWDING/EMIT + quorum; perceptual fidelity/noise;
genome-encoded efficiency; space.
