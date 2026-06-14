# 0027 — Node-honesty doctrine: raw taps, no smuggled organs

## Context
The AGENTS.md invariant *"no semantics in the kernel"* is usually read at the
selection layer — Simolution has no fitness function, unlike biosim4's `CHALLENGE_*`
(see [biosim4-lineage](../notes/biosim4-lineage.md)). But meaning enters at a second,
subtler layer: the **sensor and action nodes themselves**. biosim4's nodes are
pre-built cognitive organs — `LONGPROBE_*` (raycast vision), `GENETIC_SIM_FWD` (kin
oracle), the `*_FWD/_LR` family (egocentric projection onto a maintained heading),
`OSC1` (free tunable clock), `SET_RESPONSIVENESS` (global behavioral gain). There the
genome only wires organs together; the organ supplies the cognition. Roadmap item 11
(CROWDING / EMIT / quorum / multi-resource) will add nodes, so the temptation to copy
those organs is imminent. This ADR fixes the rule before then. Doctrine, no code change.

## Decision
**Litmus.** A *sensor* reports a raw physical quantity at the unit's own location. An
*action* is a raw physical transaction (move one cell, harvest this cell, reproduce,
emit). **No node may perform a computation the unit could instead evolve** — projection
onto a body axis, raycasting, kin comparison, oscillation, gain/meta-control, or any
spatial aggregation beyond the unit's cell.

Concretely, a candidate node is rejected if it requires the kernel to, on the unit's
behalf: (a) maintain a heading and project signals onto it (`*_FWD/_LR`); (b) scan
beyond the occupied cell (raycast / long-probe); (c) compare genomes (kin oracle);
(d) generate a clock (oscillator); (e) scale all outputs by a learned knob
(responsiveness); or (f) precompute a world abstraction the geometry doesn't already
make local (e.g. "distance to edge" — moot on a torus anyway).

The current substrate passes clean: sensors `CONST / RAND / LOCAL_RESOURCE /
SELF_ENERGY`; actions `HARVEST / REPRODUCE / MOVE_N/S/E/W` (absolute, no heading);
typed internals carry the compute. Directionality, timing, and sociality are *built*
from these plus `DELAY`, paying genes and energy.

## Why
- The litmus is the node-level form of the founding invariant. A smart organ is a
  coded behavior: it hands the unit a faculty (sight, a sense of kin, a clock) that the
  project exists to watch *emerge*. Pre-supplying it pre-decides the answer.
- It keeps the cost ledger honest. An evolved faculty (a `DELAY`-loop oscillator, a
  heading reconstructed from last-move memory) costs genes and per-tick activity energy;
  a free organ costs nothing, so selection never pays for what it uses.
- It preserves the complexity-budget choice (lineage note): richness lives in neutral
  typed compute, not in meaning-laden perception.

## Roadmap-11 guard (honest forms of the wanted biosim4 faculties)
- **Density / crowding** → sense raw signal concentration in the **own cell only**.
  Directional density must be evolved from movement + `DELAY`, never exposed as
  `*_FWD/_LR`.
- **EMIT** → emitting a signal **costs energy** (biosim4's is free — pins the
  neighborhood to 255 for nothing). Emission is a transaction, like `HARVEST`.
- **Quorum** → emerges from many units each reading a local concentration; never a
  "neighbor count" sensor.
- **Kin effects / altruism** → a heritable **emitted tag** others sense (greenbeard),
  not a `GENETIC_SIM` relatedness readout. The kernel must not compare genomes for a unit.

## Rejected (the biosim4 organs, by name)
- `*_FWD / *_LR` egocentric sensors — presuppose a body axis + free coordinate transform.
- `LONGPROBE_*` + `SET_LONGPROBE_DIST` — raycast vision with tunable range.
- `GENETIC_SIM_FWD` — kin oracle; relatedness is the kernel doing biology for the unit.
- `OSC1` + `SET_OSCILLATOR_PERIOD` — a clock handed over instead of evolved as a feedback loop.
- `MOVE_FORWARD/REVERSE/LEFT/RIGHT` — heading-relative locomotion; requires maintained
  orientation state. Absolute `MOVE_N/S/E/W` only.
- `SET_RESPONSIVENESS` — a global behavioral gain knob; meta-control the weights should express.
- `BOUNDARY_DIST(_X/_Y)` — precomputed "edge" concept (and meaningless on the toroidal world).

These are not bad engineering — they are *why* biosim4 produces legible behavior fast.
They are rejected here because legibility-by-pre-wiring is the opposite of this project's bet.

## Status
Doctrine (no code change). Guards every future sensor/action addition, starting with
roadmap item 11. The current substrate (NodeLayout) already conforms.
