# Report v6 — run report + CSV sidecars (delta over report-v5)

Report-v6 is report-v5 plus reproduction. The text report, the `.units.csv`, and
the `.wiring.csv` sidecars keep their v5 shape; this records only what changed.
Output is still byte-deterministic: same code + same config → identical files.

## Header

- `schema: report-v6` (was `report-v5`).
- `kernel: v2` (was `v1`).
- New line `max-units: <M>` after `units:` — the slot-pool capacity (a memory
  bound, not a population cap; contract-v2 §5).
- New line `max-genes: <G2>` after `genes-per-unit:` — per-slot gene capacity
  (headroom for future genome growth; this milestone is fixed-length).

## New section: `## reproduction`

Inserted between `## dynamics` and `## energy`:

- `births-total` — count of births over the whole run (cumulative).
- `max-generation` — deepest generation reached (founders are generation 0).
- `peak-population` — maximum simultaneously-alive units over the run.
- `final-population` — units alive (`energy > 0`) at the last tick, across the
  whole slot pool (founders and born children alike).
- `distinct-lineages-alive` — number of distinct founder lineages (`lineageId`,
  the root ancestor) with at least one living member at the last tick.

These are computed over the whole slot pool as running, bounded aggregates (no
per-tick history). They are the only population-wide reproduction metrics.

## New sidecar: `.lineage.csv`

One row per founder lineage that ever had a living member (lineage-id order, so
byte-deterministic). Tracks the descendants the founder-scoped `.units.csv`
cannot see. Bounded by the founder count, not ticks. Columns:
`lineage, first_tick, extinct_tick, alive_at_end, peak_members, final_members,
max_generation, lifespan, final_energy`. `extinct_tick = -1` and
`alive_at_end = 1` for a lineage still living at the end; `lifespan` runs from
first appearance to extinction (or to `ticks` if alive).

## New sidecar: `.timeseries.csv`

A downsampled population-vs-time trace for charting — at most
`TimeSeriesReport.BUCKETS` (1000) evenly-spaced ticks regardless of run length,
so its footprint is constant in ticks. Columns:
`tick, population, births, max_generation, unit_energy, reservoir`
(`population` and `unit_energy` are over the whole slot pool;
`births`/`max_generation` are cumulative). It is the only population-over-time
data the system keeps; the visualizer reads it for the reproduction charts.

## New sidecar: `.population.csv`

One row per slot alive (`energy > 0`) at the last tick, in slot order. The final
*living* population — the evolved descendants, which the founder-scoped
`.units.csv` cannot show. Columns: `slot, lineage, generation, energy, damage`
(`damage` = accumulated lifetime dissipation, the senescence variable of §7).

## New sidecar: `.popwiring.csv`

The evolved circuits of the final living population — same columns as
`.wiring.csv` but keyed by `slot` (the unit column is the slot), one row per
connection. Where `.wiring.csv` is the founders' seeded genomes, this is what
mutation made of them. Pair with `.population.csv` (slot → lineage/generation) to
compare a founder's circuit against its gen-N descendant.

## Founder-scoped metrics (unchanged shape, narrowed meaning)

The existing per-unit arrays and their derived report lines (dynamics activity,
terminal regimes, burn rate, action channel, death ticks, the `.units.csv` rows)
describe the **founders** — the initial seeded population — not their descendants.
With reproduction the founders are a subset of all units that ever lived; born
children are not tracked per-unit (deferred; ADR 0015). `units:` is the founder
count and the `.units.csv` still has exactly that many rows.

## Energy / audit (unchanged invariant)

The audit still balances exactly (`energy-audit-error` ~0, FP noise only). The
credited initial energy is `INITIAL_ENERGY × founders` (empty slots start at 0,
born children's energy is transferred from parents — never created). A birth moves
`commit + BUILD_COST` out of the parent: `REPRODUCE_YIELD × commit` to the child,
the remainder to the sink (contract-v2 §4, ADR 0016).

## state-digest

Unchanged in form. Note it folds every slot's state each tick, so its value now
depends on `max-units` (empty slots fold as zeros); a baseline must pin
`--max-units` to stay reproducible.
</content>
