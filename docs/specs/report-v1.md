# Run Report Schema — report-v1

## Purpose

Defines every line of the `report-v1` run report so that any reader — human or
agent — given a report file plus this document can reconstruct what happened in
the run without reading code.

The report is **deterministic**: the same kernel code, seed, and configuration
produce a byte-identical file. No wall-clock data is ever included; timing is
printed to the console only.

## How a run works

1. `units` genomes are produced: either the built-in 4-gene demo genome
   (`genome-source: builtin-demo`) or seeded random genomes
   (`genome-source: random`, see GenomeFactory — gene (u, i) is a pure hash of
   the salted seed, unit index, and gene index).
2. Genomes compile to connections (`GenomeCompiler.compileAll`); the kernel
   ticks `ticks` times.
3. An observer (`DynamicsObserver`) inspects the snapshot after each tick. It
   re-derives the propagation phase from the previous tick's outputs and the
   wiring — the kernel is never modified or instrumented.

## Header

| Line | Meaning |
|---|---|
| `schema` | this format, `report-v1` |
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
| `signal-propagations-total` | count of non-zero `source × weight` propagations. This is the quantity activity cost will be proportional to (contract v0 §6) |
| `junk-sink-propagations` | propagations whose destination is a junk node — activity wasted into sinks |
| `junk-sink-propagation-fraction` | junk-sink / total propagations (count-based, immune to overflow) |
| `clamp-saturation-events` | per unit per tick: CLAMP input magnitude exceeded 1.0 |
| `thresh-flips-total` | per unit per tick (from tick 2): THRESH output changed sign |
| `units-dormant-from-birth` | units with zero propagations the entire run (dormancy per contract v0 §7) |
| `units-dormant-at-end` | units with zero propagations during the final tick |

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

## `## units` — per-unit lines, only when units ≤ 20

`unit | regime | reachable | rand-wired | first-activity-tick | final-abs-y | max-abs-output`

`first-activity-tick` = first tick with a non-zero propagation in that unit
(−1 = never). Note tick 1 can never be active: propagation reads the previous
tick's outputs, which start at zero.

## `state-digest`

A 64-bit fold (splitmix64 finalizer) over every output and delay-memory cell
of every tick, in fixed order, RAND included. Two runs share a digest iff
their full trajectories are bit-identical. Any kernel behavior change — even
below display rounding — moves this line.

## Numbers

Counts are decimal integers. All doubles are rendered with Java's
`Double.toString` (shortest round-trip representation), which is
deterministic across runs and platforms.
