# Run Report Schema — report-v5

## Purpose

Defines every line of the `report-v5` run report **and** the per-unit
`.units.csv` and `.wiring.csv` sidecars, so that any reader — human or agent —
given the files plus this document can reconstruct what happened in the run
without reading code.

Version history: v1 = signal dynamics only; v2 added the `## energy` section and
per-unit death ticks; v3 added the `## burn-rate` section and moved all per-unit
detail into the `.units.csv` sidecar; v4 added the `.wiring.csv` sidecar; **v5
opens the system (contract-v1): energy intake.** It adds the `RESOURCE` sensor
and `HARVEST` action to the substrate, a reservoir/inflow/intake block to the
`## energy` section, a new `## intake` section, and a `harvest_ticks` column to
the per-unit CSV; `energy-audit-error` becomes the open-system balance and the
state digest folds in the reservoir.

This document records only what changed from report-v4; everything not mentioned
here is unchanged from [report-v4.md](report-v4.md).

All files remain **deterministic** and free of wall-clock data.

## Substrate change

The substrate grows by two meaningful nodes (`NodeLayout.TOTAL` 9 → 11):

- `RESOURCE` — sensor index 2 (after CONST, RAND). Emits the global reservoir
  level scaled to `[0,1]` as `reservoir / RESOURCE_CAPACITY`. Identical for every
  unit (well-mixed, location-free; contract-v1 §3–§4). Acuity is emergent: the
  connection weight from RESOURCE is the only gain knob (§6).
- `HARVEST` — action index 1 (after ACTION_Y). Its output drives energy intake;
  ACTION_Y stays the generic, intake-free channel.

The `src_name` / `dst_name` vocabulary in `.wiring.csv` gains `RESOURCE` and
`HARVEST`. Absolute node indices shift accordingly (e.g. `INTERNAL_OFFSET` 4 → 5).

A divergent unit's HARVEST output may overflow to ±Infinity/NaN; such a
non-finite output contributes zero intake demand (it is unreadable garbage, not a
harvest action), so it neither feeds the unit nor poisons the shared reservoir.

## Header

`kernel` is now `v1`. `schema` is `report-v5`.

## `## energy` — extended to the open system (contract-v1 §4–§7)

Phase 5 (intake) runs before phase 6 (cost): each alive unit draws
`EFFICIENCY × max(0, HARVEST output) × min(1, reservoir/totalDemand)` from the
shared reservoir, then pays decay + activity cost. The reservoir is replenished
by a fixed inflow each tick, admitted only up to capacity. Energy is conserved
across **reservoir + units + sink**, with inflow as the accounted source.

New lines (in addition to the v4 energy lines):

| Line | Definition |
|---|---|
| `initial-reservoir` | reservoir level at run start (`RESOURCE_INITIAL`) |
| `final-reservoir` | reservoir level at the last tick |
| `cumulative-inflow` | total energy admitted to the reservoir by inflow over the run (inflow beyond capacity is discarded and never counted) |
| `intake-total` | energy drawn out of the reservoir into units over the run = `initial-reservoir + cumulative-inflow − final-reservoir` |

Changed line:

| Line | Definition |
|---|---|
| `energy-audit-error` | `\|(initial-energy-total + initial-reservoir + cumulative-inflow) − (final-energy-total + final-reservoir + energy-sink)\|`. Must be ~0 (floating-point summation noise only); a non-trivial value means energy leaked or was created — a law violation |

## `## intake` — harvest participation (new section)

| Line | Definition |
|---|---|
| `units-ever-harvested` | units whose HARVEST output was > 0 on at least one tick while alive |
| `harvest-active-ticks-total` | summed over units: ticks a unit was alive with HARVEST output > 0 |

These are participation counts only (who fed and how often), derived directly
from snapshot outputs. Exact per-unit intake amounts are intentionally not
reported in v5 — reconstructing the per-tick scarcity factor outside the kernel
is fragile and not yet needed.

## Per-unit CSV sidecar — new column

A `harvest_ticks` column is inserted before `regime`:

| Column | Definition |
|---|---|
| `harvest_ticks` | ticks this unit was alive with HARVEST output > 0 |

Note: `energy_consumed` / `mean_burn_rate` / `peak_burn` remain defined as in v4
(`INITIAL_ENERGY − final_energy` and its derivatives). With intake these are
**net** of harvest, so a unit that harvested more than it spent shows negative
consumption / burn rate — an honest net-energy figure, not a defect.

## `state-digest`

The fold now additionally includes the scalar `reservoir` and `cumulative-inflow`
at every tick, after the energy cells. Otherwise unchanged: any kernel behavior
change — including reservoir drift below display rounding — moves this line.
