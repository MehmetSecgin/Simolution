# 0020 — Motility: MOVE effectors, position decoupled from slot (kernel v3.1)

## Context
v3-basic (ADR 0018) made the world spatial but **sessile**: a lineage spreads
only by where its offspring land. Motility was the locked next sub-milestone
(contract-v3 §9, Fork B). Adding it lets a unit relocate each tick, which —
combined with `LOCAL_RESOURCE` + `DELAY` (temporal sensing) — makes **chemotaxis**
emergent: a real run-and-tumble that is blind to the true gradient, exactly as
bacteria forage.

## Decision (binding: contract-v3 §6/§9, amends §2)
**Four independent directional effectors** `MOVE_N / MOVE_S / MOVE_E / MOVE_W`
(meaningful actions; Action.MEANINGFUL_COUNT 3 → 7, `NodeLayout.TOTAL` grows by
4). Per tick, the world reads them (from `outputsPrev`, the swapped current tick)
and converts to at most one Moore step by a uniform rule:

```
H = MOVE_E − MOVE_W ;  dx = +1 if H > θ,  −1 if H < −θ,  else 0
V = MOVE_N − MOVE_S ;  dy = −1 if V > θ,  +1 if V < −θ,  else 0   (N = −y / up)
target = ((y+dy) mod W)·W + ((x+dx) mod W)   (toroidal)
```

* **Diagonals are emergent** (both axes fire) — never an encoded node.
* **One cell per tick**; magnitude past the deadzone `θ` (`MOVE_DEADZONE`) buys no
  extra distance — speed is "move every tick vs intermittently," emergent.
* **Blocked if occupied** (`cellOccupant[target] ≠ −1`) → unit stays. Two units →
  same free cell resolved by ascending slot order (the lower slot claims it; it is
  installed immediately so the higher slot sees it taken). Deterministic.
* **`MOVE_COST` charged per actual step** → sink (and `damage`, like every
  dissipation — motility is metabolically expensive: flagella/ATP). A unit that
  cannot afford `MOVE_COST` does not move. No charge for a blocked/declined move.
* **New phase 8** (`settle-movement`); diffusion becomes phase 9. Append-only, no
  reorder. A child born this tick has zeroed outputs → zero move drive → cannot
  move the tick it is born.

## Why position is decoupled from slot (the key implementation call)
v3-basic used **slot index = cell index**. Motility breaks that: if a moving unit
changed slots, its per-slot connection store would have to be **recompiled at the
destination** (compiled connections bake in absolute node indices = `slot·TOTAL`),
and `GeneDecoder.decode` **allocates** a `CompiledConnection` per gene — so a
per-tick move would allocate in the hot path, violating the memory doctrine
(AGENTS.md: zero allocation inside `tick()`).

So a unit's **slot is its permanent storage identity** (genes, connections,
energy, brain state — all stay put for its whole life), and the **cell it occupies
is `position[slot]`**. Two new arrays make this O(1) and allocation-free:
* `position[slot]` → the cell the unit currently occupies (valid while alive).
* `cellOccupant[cell]` → the slot living there, or `−1`. Enforces one-per-cell
  exclusivity and answers "is this cell free" in O(1).

A move is then three int writes (vacate old cell, set position, occupy new cell) —
no copy, no recompile, no allocation. Sensing and harvest index the field through
`position[slot]`. A free slot for a birth is found with the monotone
`freeSlotCursor` scan (reinstated from v2 §11); because `#living = #occupied
cells ≤ W²·= #slots`, a free Moore-neighbour **cell** guarantees a free **slot**
exists, so birth still never halts.

## No semantics check
Four symmetric directional channels, identical rule, no privileged direction;
`θ` and `MOVE_COST` are uniform anchors, not per-unit talents. The kernel never
rewards moving toward resource — chemotaxis, if it appears, is a circuit the
genome built from `LOCAL_RESOURCE` + `DELAY`. Drift, run-and-tumble, escape, and
sessility are all genome outcomes.

## Footprint
Two `int[W·W]` arrays (`position`, `cellOccupant`) — O(cells), constant in ticks,
preallocated. Movement is O(units) per tick, allocation-free. Within budget.

## Rejected
- **State-move (keep slot = cell, copy unit state on move)** — forces a per-move
  `compileSlot` → `GeneDecoder.decode` allocation in the hot path; per-tick alloc
  if movement is common. The whole reason for decoupling.
- **2 signed axis effectors** instead of 4 channels (Fork A) — brittle (sign flip
  near zero), cannot evolve asymmetric drive; Fork B chosen earlier for
  evolvability (motor-neuron-like independent channels).
- **8-way (dedicated diagonal nodes)** — redundant slots; diagonals emerge.
- **Multi-cell / continuous velocity** — off-lattice, allocation, breaks doctrine.
- **Heading + turn/forward (turtle)** — needs a stored heading and angle
  quantisation on a grid; ugly. Axis channels need no extra per-unit state.

## Deferred
Motility cost scaling with size/biomass (Pirt), momentum/inertia, and a spatial
map/live viewer that shows units moving (report-v7) — all later.
