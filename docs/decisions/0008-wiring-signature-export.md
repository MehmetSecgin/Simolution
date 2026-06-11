# 0008 — Per-unit wiring export (the signature) + visualizer diagrams

## Context
The owner wanted to see how each unit is structured — its connections, its wiring. The per-unit CSV held scalar metrics but not the graph itself.

## Decision
Add a `.wiring.csv` sidecar (WiringReport), one row per connection: unit, conn_index, src/dst local index + name, weight, meaningful flag. Written beside the report on `--out`, like `.units.csv`. Report bumped to v4 with a `per-unit-wiring` pointer line. The HTML visualizer (tools/visualize.py) gains a wiring panel: pick any unit, render its connection graph on the fixed substrate (sensors left, internals middle, action right), meaningful edges colored by weight sign and sized by magnitude, junk edges optional/faint.

## Why
- The wiring is the true signature — every other per-unit metric derives from it. Exposing it closes the loop from "this unit lived long" to "because it's wired like this".
- CSV keeps it analyzable and diffable; bounded by total connections (units × genes), constant in ticks — memory discipline holds.
- NodeLayout.localName centralizes node naming (reused by export and any future tooling) instead of duplicating the index→name map.

## Observed (seed 42)
The signatures explain the survival data:
- survivor unit 3: only 4 meaningful edges, and nothing meaningful feeds its MUL/DELAY — the functional subgraph is starved, so it sits near-silent and barely burns.
- survivor unit 78: DELAY-centric with no sensor→action path (reachable=0); signals are internal echoes that fade.
- fastest-dying unit 44: CONST→ADD→THRESH→ACTION driven path plus RAND injection into MUL/THRESH/ACTION plus self-loops (DELAY→DELAY, CLAMP→CLAMP, MUL→MUL) — a self-exciting, noise-fed brain that never quiets, so it burns fastest and dies first.

## Rejected
- Folding wiring into the per-unit CSV: connections are variable-length per unit; a row-per-connection file is the natural shape.
- Drawing all 100 wiring graphs at once in the report/HTML: noise; a picker (or click-through) scales and stays readable.
