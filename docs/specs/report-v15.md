# report-v15: engine v6 report, parallel state digest, observer without founder state

**Kernel:** v6 (`kernel: v6`, `schema: report-v15`)
**Delta over:** [report-v14](report-v14.md) (frame, catalog and births formats are unchanged)
**Design:** [engine-v6-design.md](../notes/engine-v6-design.md) §6 (semantic changes) and §7 (observer, digest)

## Why

The v5 `DynamicsObserver` re-propagated the founders' compiled connections every tick and folded a
serial hash chain over every output, delay and energy value. A per-phase profile of Bench big500
at threads 1 (taken during the B1 review) measured `DynamicsObserver.observe` at 55 ms of 64 ms
per tick. It also described only the founders: descendants reuse slots,
and the founder-indexed arrays kept reporting the first occupant's wiring. report-v15 keeps the
lines that describe the whole living population and drops the ones that need per-founder history.

## `state-digest`

`state-digest` is the digest of the final state, computed by `StateDigest`, not a hash chain over every tick.

- Scalars first, in this order: `tick`, `energySink`, `cumulativeInflow`, `birthsTotal`,
  `maxGeneration`, `creditedInitialEnergy`, `creditedInitialMass`, `initialResourceTotal`, each
  folded as `h = Noise.mix(h ^ rawBits)` from the offset `0xcbf29ce484222325`.
- Then every `SAVED` field of `WorldState.fields()` in declared order. Each array is cut into the
  fixed 4096-element chunks of `Chunks`; a chunk is folded serially over the raw bits of its
  elements (`h = Noise.mix(h ^ bits)`, floats and doubles by `floatToRawIntBits` /
  `doubleToRawLongBits`) into its own chunk hash, and the chunk hashes are combined in ascending
  chunk index with `h = Noise.mix(h ^ chunkHash)`.
- Chunk folds run on the kernel's runner, so the digest is the same for any `--threads`.
- It is also written into every checkpoint header (checkpoint format version 2) and checked
  after the load; a mismatch refuses the file.
- `--digest-every-tick` (debug) prints `digest <tick> <hex>` to stdout after every tick.

The v5 digest `e2709232cf9636b6` and every v5 baseline line that depended on its inputs are not
comparable with v6 values.

## `## dynamics`

Kept, with the same names:

- `ticks-with-any-activity`
- `signal-propagations-total`

Both are now computed over **all living units** from the kernel's `activeThisTick` (the non-zero
propagations a unit made this tick, 0 for a slot that was dead at the start of the tick), so
descendants count. v5 counted only the founders' own connections.

Removed:

- `junk-sink-propagations`, `junk-sink-propagation-fraction`
- `clamp-saturation-events`
- `thresh-flips-total`
- `units-dormant-from-birth`, `units-dormant-at-end`

## `## energy`

Removed: `units-alive-at-end`, `units-dead-at-end`, `death-tick-first`, `death-tick-median`,
`death-tick-last`. All other lines keep their name and meaning.

## Removed sections

- `## intake` (`units-ever-harvested`, `harvest-active-ticks-total`)
- `## burn-rate`
- `## terminal-regimes`
- `## action-channel`

## Unchanged

`## structure` (founder wiring statistics), `## reproduction`, the rest of `## energy` and
`## mass`, the header lines, and the sidecars (`.timeseries.csv`, `.births.csv.gz`,
`.catalog.gz`, `.frames.zst`, `.frames.idx`, `.layout.json`).

## Values

The baseline command (`--units 100 --ticks 1000 --seed 42 --world 100`) gives different values
than v5 for every line a semantic change reaches (claim-based movement and reproduction,
parent-keyed mutation, seed-driven randomness, exact sqrt harvest, chunk-ordered totals). The
regenerated `runs/baseline.txt` is byte-identical for `--threads 1` and `--threads 8`.

## Baseline change

`runs/baseline.txt` (`--units 100 --ticks 1000 --seed 42 --world 100`) was regenerated once, by the
commit that adds this spec. Evidence: the same command run with `--threads 1` at the last commit
before each stage (v5 = 40a0f73, B1 = 2cd2ab9, B2 = 2297da7, B3 = d1ad480, B4 = c1400ec) and the
regenerated file for B5. The v5 run is byte-identical to the old committed baseline. Each line
below names the first stage that moved it and the later stages that moved it again; the final
state is chaotic, so a line that is moved by B2 is moved again by B3 and B4 by an unrelated
amount (births-total and the energy totals stay within about 1%).

Stages: B1 chunked partial totals (summation order of sink, reservoir, audit). B2 movement as a
claim phase (no same-tick chains). B3 reproduction as a claim phase, parent-keyed skip-sampled
mutation. B4 run seed drives all randomness with tagged streams, exact sqrt harvest. B5 this
report (digest, observer, removals).

Changed, kept lines:

- `schema`, `kernel`: B5 (`report-v14` to `report-v15`, `v5` to `v6`).
- `energy-sink`: B1 moves only the last digits (`9606214.373473449` to `9606214.373473413`); B2, B3, B4 move it by about 2 to 5 units of 9.6 million.
- `final-reservoir`: B1 moves only the last digits (`489468.922056942` to `489468.9220569405`); B2 and B3 move it in the second decimal; B4 moves it by about 5 (`489473.53716242`).
- `energy-audit-error`: B1 keeps the order of magnitude (`9.67e-7` to `1.00e-6`); B2 drops it to `1.3e-8` because the trajectory changed, B3 and B4 keep it near 1e-8 (`7.82e-8`). It is floating-point noise whose size depends on the trajectory.
- `births-total`: B2 (`1371` to `1374`), B3 (`1359`), B4 (`1355`).
- `peak-population`: unchanged by B1 and B2 (`976`); B3 (`967`), B4 (`969`).
- `final-energy-total`: B2 (`4413.70` to `4415.68`), then B3 (`4415.52`) and B4 (`4415.71`).
- `intake-total`: B2 (`9510531.059413247`), B3 (`9510531.140385117`), B4 (`9510526.46283758`). It is `initial-reservoir + cumulative-inflow - final-reservoir`, so it follows `final-reservoir` (B1 changes it only below printed precision).
- `signal-propagations-total`: B2 (`360492` to `360694`), B3 (`363011`), B4 (`361326`) under the v5 founder-wiring counter; B5 changes the definition (all living units from `activeThisTick`) and the value to `522202`.
- `state-digest`: B2, B3 and B4 change the state the old chain hashed (`c2f989cbde86b100`, `ecf95a7ffd9a3819`, `dde4869ed9842b3b`); B5 replaces the definition (`78644e65e8086e45`).

Unchanged by every stage: `ticks-with-any-activity` (999), `max-generation`, `final-population`,
`distinct-lineages-alive`, `initial-energy-total`, `initial-reservoir`, `cumulative-inflow`, the
`## structure` lines, and the `## mass` lines.

Removed lines, all by B5 (they are v5 founder-indexed or per-unit history). The v5 value moved
across B2 to B4 where one is shown, for the record:

- `junk-sink-propagations`, `junk-sink-propagation-fraction`, `clamp-saturation-events`, `thresh-flips-total` (each moved by B2, B3 and B4, for example `clamp-saturation-events` 8760, 8772, 8894, 8755).
- `units-dormant-from-birth`, `units-dormant-at-end`.
- `units-alive-at-end`, `units-dead-at-end`.
- `death-tick-first` (B3, B4), `death-tick-median` (B3, B4), `death-tick-last` (B4).

Removed sections, all by B5:

- `## intake`: `units-ever-harvested` (B2, B4), `harvest-active-ticks-total` (B2, B3, B4).
- `## burn-rate`: `mean-burn-rate-p0` (B2, B3, B4), `-p50` (B3, B4), `-p90` (B3, B4), `-p100` (B3, B4).
- `## terminal-regimes`: `fixed-point`, `divergent` (B2, B3, B4), `bounded`, `divergence-cutoff`.
- `## action-channel`: `final-abs-y-p50` (B2, B3, B4), `-p90` (B2, B3, B4), `-p100` (B4), `units-final-y-nonfinite`, `final-abs-y-p0`.
