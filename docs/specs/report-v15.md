# report-v15: engine v6 report, parallel state digest, observer without founder state

**Kernel:** v6 (`kernel: v6`, `schema: report-v15`)
**Delta over:** [report-v14](report-v14.md) (frame, catalog and births formats are unchanged)
**Design:** [engine-v6-design.md](../notes/engine-v6-design.md) §6 (semantic changes) and §7 (observer, digest)

## Why

The v5 `DynamicsObserver` re-propagated the founders' compiled connections every tick and folded a
serial hash chain over every output, delay and energy value. A profile put that harness at about
86% of wall time on a 500x500 world. It also described only the founders: descendants reuse slots,
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
