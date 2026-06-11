# Run Report Schema — report-v3

## Purpose

Defines every line of the `report-v3` run report **and** the per-unit
`.units.csv` sidecar, so that any reader — human or agent — given the files
plus this document can reconstruct what happened in the run without reading
code.

Version history: v1 = signal dynamics only; v2 added the `## energy` section
and per-unit death ticks; v3 adds the `## burn-rate` section and moves all
per-unit detail into the CSV sidecar (the report keeps aggregates and
distributions only).

Both files are **deterministic**: the same kernel code, seed, and configuration
produce byte-identical output. No wall-clock data is ever included; timing is
printed to the console only.

## Output files

- The report (this schema) goes to stdout always, and to `--out <file>` when given.
- When `--out` is given, a per-unit CSV is written beside it: the report path's
  extension is replaced with `.units.csv` (e.g. `runs/baseline.txt` →
  `runs/baseline.units.csv`).

## How a run works

1. `units` genomes are produced: either the built-in 4-gene demo genome
   (`genome-source: builtin-demo`) or seeded random genomes
   (`genome-source: random`, see GenomeFactory — gene (u, i) is a pure hash of
   the salted seed, unit index, and gene index).
2. Genomes compile to connections (`GenomeCompiler.compileAll`); the kernel
   ticks `ticks` times.
3. An observer (`DynamicsObserver`) inspects the snapshot after each tick. It
   re-derives the propagation phase from the previous tick's outputs and the
   wiring — the kernel is never modified or instrumented. It mirrors the
   kernel's dead-unit skip: a unit dead at a tick's start (its energy in the
   previous snapshot) propagated nothing that tick.

## Header

| Line | Meaning |
|---|---|
| `schema` | this format, `report-v3` |
| `kernel` | kernel contract version the binary implements |
| `seed` | run seed; drives both genome generation (salted) and RAND noise |
| `units`, `ticks`, `genes-per-unit` | run dimensions |
| `genome-source` | `builtin-demo` or `random` |

## `## structure` — tick-independent, derived from wiring alone

| Line | Definition |
|---|---|
| `connections-compiled` | total compiled connections (every gene decodes; see kernel-v0.1 slice §3) |
| `connections-meaningful` | connections whose source AND destination are meaningful (evaluated) nodes |
| `connections-junk-touching` | compiled − meaningful. Junk nodes are never evaluated, their outputs stay 0: junk-source connections carry nothing, junk-destination connections are signal sinks |
| `duplicate-connection-pairs` | genes repeating an already-seen (src, dst) pair within a unit; their weights stack in the accumulator |
| `weight-abs-mean` / `weight-abs-max` | over all compiled connection weights |
| `weight-positive-fraction` | fraction of weights > 0 |
| `units-sensor-action-reachable` | units with a directed path from a meaningful sensor to an action node through meaningful nodes only (BFS) |
| `units-rand-wired` | units where RAND has at least one outgoing connection to a meaningful node — such units are noise-driven and can never reach a fixed point |

## `## dynamics` — observed over the whole run

| Line | Definition |
|---|---|
| `ticks-with-any-activity` | ticks where ≥1 connection anywhere propagated a non-zero signal. Activity per slice §9 |
| `signal-propagations-total` | count of non-zero `source × weight` propagations. Activity cost is proportional to this (contract v0 §6) |
| `junk-sink-propagations` | propagations whose destination is a junk node — activity wasted into sinks |
| `junk-sink-propagation-fraction` | junk-sink / total propagations (count-based, immune to overflow) |
| `clamp-saturation-events` | per unit per tick: CLAMP input magnitude exceeded 1.0 |
| `thresh-flips-total` | per unit per tick (from tick 2): THRESH output changed sign |
| `units-dormant-from-birth` | units with zero propagations the entire run (dormancy per contract v0 §7) |
| `units-dormant-at-end` | units with zero propagations during the final tick |

## `## energy` — the entropy law (contract v0 §3, §4, §6, §9)

Each unit starts with `INITIAL_ENERGY`. Every tick a living unit pays
**structural decay** (`connection-count × DECAY_PER_CONNECTION`, charged
regardless of activity) plus **activity cost**
(`non-zero-propagations × COST_PER_PROPAGATION`). The charge is clamped to
available energy, so a unit never overdraws. Energy is never created; charges
flow to a global sink. A unit with energy ≤ 0 is dead: it computes nothing
from the next tick on and its state is frozen.

| Line | Definition |
|---|---|
| `initial-energy-total` | `INITIAL_ENERGY × units` — the closed system's entire energy budget |
| `final-energy-total` | sum of remaining energy across all units at the last tick |
| `energy-sink` | total energy irreversibly dissipated to the sink |
| `energy-audit-error` | `\|initial − (final + sink)\|`. Must be ~0 (floating-point summation noise only); a non-trivial value means energy leaked or was created — a law violation |
| `units-alive-at-end` / `units-dead-at-end` | by energy > 0 at the last tick |
| `death-tick-first` / `-median` / `-last` | over units that died (−1 if none died); the death curve |

Note: a unit with zero connections has zero structural decay and, if also
inactive, never loses energy — degenerate immortality. Random genomes always
have connections, so this appears only with hand-built empty genomes.

## `## burn-rate` — how fast units spent energy

Mean burn rate of a unit = `energy_consumed / lifespan` (energy per tick lived).
The distribution is over all units (dead and alive together).

| Line | Definition |
|---|---|
| `mean-burn-rate-p0 / -p50 / -p90 / -p100` | quantiles of per-unit mean burn rate; p0 = slowest burner (longest-lived lean unit), p100 = fastest |

## `## terminal-regimes` — classification of each unit's endgame

Priority order; first match wins:

1. `divergent` — any output was non-finite (overflowed IEEE double to
   ±Infinity/NaN; the kernel deliberately never clamps) OR max finite
   |output| over the run exceeded `divergence-cutoff` (a documented,
   arbitrary-but-fixed constant).
2. `fixed-point` — at the final tick, every output and delay-memory cell
   equals the previous tick's value bit-for-bit, **excluding the RAND
   sensor's own output** (it changes every tick by construction; if it
   feeds anything, downstream nodes expose that).
3. `bounded` — everything else: still moving, still finite.

## `## action-channel`

Final-tick |ACTION_Y| per unit. `units-final-y-nonfinite` counts units whose
final action value overflowed; quantiles are over the finite rest.
Quantile rule: sorted ascending, `index = floor(p/100 × (n−1))`, no
interpolation. p0 = min, p100 = max.

## `per-unit-detail` line

Names the `.units.csv` sidecar and its row count. The detail itself is in that
file (next section).

## Per-unit CSV sidecar

One header row plus one row per unit, in unit-index order (so it diffs
cleanly). Columns:

| Column | Definition |
|---|---|
| `unit` | unit index |
| `connections` | compiled connections for this unit (gene count, since every gene is valid) |
| `meaningful_connections` | connections with both endpoints meaningful (non-junk) |
| `reachable` | 1 if a meaningful sensor→action path exists, else 0 |
| `rand_wired` | 1 if RAND feeds a meaningful node (noise-driven), else 0 |
| `first_activity_tick` | first tick with a non-zero propagation (−1 = never; tick 1 can never be active) |
| `death_tick` | tick energy first reached 0 (−1 = still alive at end) |
| `lifespan` | ticks lived = `death_tick`, or the whole run if it survived |
| `alive` | 1 if alive at the end, else 0 |
| `energy_consumed` | `INITIAL_ENERGY − final_energy` |
| `final_energy` | energy remaining at the last tick (0 if dead) |
| `mean_burn_rate` | `energy_consumed / lifespan` |
| `peak_burn` | largest energy charge in any single tick of its life |
| `total_propagations` | non-zero propagations summed over its life |
| `ticks_active` | ticks in which it had any activity |
| `clamp_saturations` | ticks its CLAMP input exceeded ±1 |
| `thresh_flips` | times its THRESH output changed sign |
| `regime` | `fixed-point` / `bounded` / `divergent` (see terminal-regimes) |
| `final_abs_y` | |ACTION_Y| at the last tick |
| `max_abs_output` | largest finite |output| over the run |

## `state-digest`

A 64-bit fold (splitmix64 finalizer) over every output, delay-memory cell, and
energy cell of every tick, in fixed order, RAND included. Two runs share a
digest iff their full trajectories are bit-identical. Any kernel behavior
change — even below display rounding — moves this line.

## Numbers

Counts are decimal integers. All doubles are rendered with Java's
`Double.toString` (shortest round-trip representation), which is
deterministic across runs and platforms.
