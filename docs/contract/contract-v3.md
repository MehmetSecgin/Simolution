# Kernel v3 Contract — Space (the world becomes a place)

## 0. Scope

Kernel v0–v2 ran every unit in a single well-mixed world: one global resource
reservoir, sensed identically by all, harvested from a shared pool. That made
competition mean-field — a strategy competes against the *average*, so the
fittest harvester wins everywhere and the population converges to one circuit
(observed: the gen-1757 monoculture). There are no niches, no refuges, no
neighbours, and therefore nowhere for diversity to hide.

v3 makes the world a **place**: a 2D toroidal lattice of cells, each holding its
own resource, replenished locally and spread by diffusion. A unit lives in a
cell, senses and harvests **only its own cell**, and its offspring are placed in
**neighbouring cells**. Local depletion creates gradients; gradients create
niches and spatial refuges; competition becomes local. This is the keystone that
unlocks ongoing diversity and is the prerequisite for every later ecological
mechanic (motility, signalling, predation — all of which need neighbours to
exist).

This milestone is deliberately **basic**: the world is spatial and **sessile**
(units do not move). Motility (a `MOVE` effector family) is the explicit next
sub-milestone (§9) and gets its own contract section + ADR.

It **supersedes**:
- contract-v1 §2–§5 (global RESOURCE sensor + shared-reservoir intake) — replaced
  by per-cell sensing and per-cell uptake (§3, §4).
- contract-v2 §5 (the `MAX_UNITS` slot pool + halt-on-full) — replaced by the
  grid itself as the slot pool and **local spatial exclusion** as the honest
  carrying capacity (§5).

Every other law — time, decay, the metabolic bill (basal + activity +
maintenance + aging), reproduction + mutation, death-as-`energy ≤ 0`,
conservation, determinism, no-semantics — holds unchanged.

This contract is **binding**. If code and this contract disagree, the contract
wins.

---

## 1. The governing principle (no semantics, restated for space)

Space is a new chance to smuggle meaning: a privileged location, a "good" patch,
a coded edge, a movement reward. None are permitted.

> **The lattice is homogeneous and the resource law is identical in every cell.
> No cell, direction, or position is privileged by the kernel. Every spatial
> structure — gradients, patches, clusters, fronts — must emerge from the units'
> own depletion and reproduction, never be placed by the world.**

The torus (no edges, §2) is chosen precisely so that no location is special: a
walled box would make corners and centres physically different places, a coded
asymmetry. Uniform inflow (§4) is chosen so that no cell is intrinsically richer
than another — all heterogeneity is life's own doing.

---

## 2. The lattice (binding geometry)

* The world is a square grid of side `W`, so `W × W` **cells**, indexed
  `cell = y·W + x` with `x = cell % W`, `y = cell / W`.
* **One unit per cell.** A cell holds at most one living unit; occupancy is
  exclusive (a unit takes space). A unit's **slot** is its permanent storage
  identity (genes, connections, energy, brain state — fixed for its whole life);
  the **cell** it occupies is `position[slot]`, and `cellOccupant[cell]` is the
  living slot there or empty. In the **sessile** world a unit never changes cell,
  so `position` is constant; **motility** (§9, ADR 0020) changes only
  `position`/`cellOccupant`, never the slot — so the per-slot connection store is
  never recompiled or reallocated mid-life. Founders take slot `k` at cell `k`.
* **The grid is the slot pool.** `MAX_UNITS = W·W` exactly. There is no separate
  capacity parameter and no halt-on-full (§5). `W` is the single arena anchor,
  supplied to the kernel at construction. Because `#living = #occupied cells ≤
  W·W = #slots`, a free cell always implies a free slot.
* **Toroidal.** Every cell has exactly 4 von-Neumann neighbours (N/S/E/W) and 8
  Moore neighbours (incl. diagonals), with coordinates taken modulo `W`. No edge,
  no corner, no privileged location.
* **Founder placement.** The `N` initial genomes occupy cells `0 … N−1`
  (a deterministic block, the inoculation pattern). On a homogeneous torus this
  is an arbitrary labelling, not a privileged region; a scattered placement is a
  trivial, non-binding later refinement.

---

## 3. Local sensing — `LOCAL_RESOURCE` replaces the global `RESOURCE` sensor

* The global `RESOURCE` sensor is **removed**. The sensor index it occupied now
  carries **`LOCAL_RESOURCE`**: a unit reads the resource standing in **its own
  cell only**.
* This is transduction, not perception (contract-v1 §2): a raw
  measurement-at-a-point of the unit's own cell, scaled to a bounded O(1) range
  so the connection weight stays the only gain knob. No neighbour reading, no
  gradient, no history is computed by the kernel — a circuit that compares its
  cell now against its cell last tick (via `DELAY`) to climb a gradient is the
  genome's to build (and is exactly how real chemotaxis works: temporal, blind to
  the true field). The substrate is unchanged in size; only the meaning of the
  former `RESOURCE` slot changes. `NodeLayout.TOTAL` is unchanged.
* Scaling: `LOCAL_RESOURCE` emits `min(1, cellResource / CELL_CAPACITY)`. The
  cell's `CELL_CAPACITY` is a uniform scale anchor, identical for every cell.

`SELF_ENERGY` (own energy) is local-by-nature and is retained unchanged. `CONST`,
`RAND` are unaffected. No new sensor node is added this milestone.

---

## 4. Per-cell resource: field, uptake, inflow, diffusion (binding)

The single global reservoir scalar becomes a **resource field** — one
non-negative scalar per cell — and its physics is identical in every cell.

* **Uptake is local (supersedes the v1 shared-pool allocation).** In phase 5,
  each living unit draws from **its own cell**: `intake = min(demand,
  cellResource)`, where `demand` is the unchanged saturating harvest demand
  (contract-v1 §5: `capacity · σ(output)`, `capacity ∝ transporterCount`). Energy
  moves cell → unit; the cell is debited by exactly the intake. Because cells are
  exclusive (§2) there is no within-cell contention, so the v1 proportional
  rationing across competitors disappears — competition for a cell is resolved by
  *who lives there*, decided by birth and death, not by a shared-pool factor.
* **Inflow is local and uniform.** Each cell admits up to `CELL_INFLOW` per tick,
  capped so it never exceeds `CELL_CAPACITY`: `admit = min(CELL_INFLOW, max(0,
  CELL_CAPACITY − cellResource))`. The summed admitted amount is the run's
  accounted inflow (the only energy treated as entering the system), exactly as
  the global inflow was in v1.
* **Diffusion is a new tick phase (§6).** Resource spreads to neighbours by a
  discrete, mass-conserving Laplacian with coefficient `DIFFUSION_RATE` over the 4
  von-Neumann neighbours, double-buffered (read the whole field, write the next)
  so the update is order-independent and deterministic. Diffusion conserves mass
  exactly on the torus (every flux leaving a cell enters its neighbour); it is
  never clamped, so it cannot create or destroy resource. Cells may briefly
  exceed `CELL_CAPACITY` via diffusion — only *inflow* is capped, never the field.
* `CELL_CAPACITY`, `CELL_INFLOW`, `DIFFUSION_RATE` are uniform world-harshness /
  scale anchors (§ tuning discipline, contract-v1 §12 / v2 §16): set once to make
  a livable, evolvable world, never tuned to an outcome.

---

## 5. Population control is spatial + energetic — no halt-on-full

contract-v2 §5 needed a `MAX_UNITS` slot wall with a loud halt because the
well-mixed pool had no physical density limit. Space supplies the real one and
the wall is **retired**:

* The grid is finite, so population is bounded by `W·W` *physically* — a unit
  cannot exist where there is no cell. This is the honest carrying capacity that
  v2 §5 was careful never to fake with a cap.
* **A birth needs a free neighbouring cell.** In phase 7 a reproducing parent
  places its child in a **free** (dead/empty) cell among its 8 Moore neighbours;
  if none is free, **the unit does not reproduce this tick** — a local, physical
  constraint (you cannot build a cell where there is no room), exactly like being
  unable to afford `BUILD_COST`. This is *not* a denied birth imposed by a global
  cap; it is the geometry. There is **no run halt** for fullness.
* Determinism: parents are scanned in ascending slot order; each places its child
  in the **lowest-indexed free Moore neighbour**; the child is installed
  immediately so a later parent in the same tick sees the cell occupied. Identical
  seed → identical placements.
* Energetic death still frees a cell (a dead unit's slot/cell returns to the free
  pool), so the grid breathes: fronts advance into emptied cells.

---

## 6. Tick pipeline (additive — diffusion is the new phase 8)

Phases 1–7 are unchanged in order and meaning; spatialization changes only the
*internals* of phase 5 (uptake now per-cell) and phase 7 (placement now spatial),
not their position. Movement and diffusion are appended as new phases 8 and 9 (the
pipeline grows by appending, never by reordering):

```
1 clear → 2 propagate → 3 evaluate → 4 swap → 5 settle-intake (per-cell uptake + per-cell inflow)
        → 6 settle-cost → 7 settle-reproduction (spatial placement)
        → 8 settle-movement (motility, §9) → 9 settle-diffusion
```

* Phase 3 reads `LOCAL_RESOURCE` from the unit's current cell (`position[slot]`)
  as the field stands at tick start (after the previous tick's diffusion) —
  deterministic and consistent.
* Phase 8 relocates each motile unit by at most one Moore step (§9); a unit born
  this tick has zeroed outputs and so cannot move the tick it is born.
* Phase 9 relaxes the field for the next tick. Placing it last is an arbitrary
  fixed choice (any fixed position is deterministic); end-of-tick keeps "what a
  unit sensed this tick" equal to "what it harvested from this tick."

---

## 7. Conservation and audit (extended)

Energy now flows **field (all cells) → units → {other units (births), sink}**,
with local inflow topping up each cell. The audit invariant stays exact (FP noise
only):

```
INITIAL_unitEnergy · N₀ + Σ_cells initialCellResource + cumulativeInflow
   == Σ unitEnergy (all live slots) + Σ_cells cellResource + sink
```

The snapshot exposes `Σ_cells cellResource` (the world's total standing resource —
the spatial generalisation of the old `reservoir` scalar, and reported under that
name) plus the resource field itself (for inspection / future map views). Inflow
sums per-cell admissions; diffusion is mass-neutral and contributes nothing to
the audit. The `energy-audit-error` line must stay ~0.

---

## 8. Invariants retained

Determinism (same seed → identical history, including field evolution, births,
mutations, and which cell each child lands in), memory discipline (footprint is
`O(W·W)` — units + two field buffers — and constant in tick count; no per-tick
allocation; diffusion uses preallocated double buffers), memory-only-via-DELAY,
mutation-safe encoding, death as a derived predicate (`energy ≤ 0`, no alive
flag), and no-semantics-in-the-kernel all still hold. The per-tick hot path gains
one O(cells) diffusion pass and the intake pass becomes O(cells) cell-local
instead of a two-pass global allocation — both linear in the arena, none
allocating.

---

## 9. Future, explicitly out of v3-basic

**Motility — implemented (phase 8, ADR 0020).** A `MOVE_N/S/E/W` effector family
(Fork B — four independent directional channels; Action.MEANINGFUL_COUNT 3 → 7).
Kernel rule: `dx = +1 if (E−W) > θ, −1 if < −θ, else 0`, likewise `dy` from
`(N−S)` with N = −y; one Moore step per tick, magnitude past the deadzone `θ`
(`MOVE_DEADZONE`) buys no extra distance. Target occupied → blocked (stay); two
units → same free cell resolved by ascending slot order; toroidal wrap;
`MOVE_COST` charged per actual step → sink (a unit that cannot afford it does not
move). Diagonals are emergent (two axes firing), never encoded. Chemotaxis emerges
free from `LOCAL_RESOURCE` + `DELAY` (temporal sensing, blind to the true field —
real run-and-tumble). The only new state is `position`/`cellOccupant` (§2); the
slot — and thus the connection store — never changes, so movement allocates
nothing.

**Later** (each its own ADR): scattered/seeded founder placement; signalling
(`CROWDING` / `EMIT` + quorum) now that neighbours exist; multiple resources +
stoichiometry (niches without space); patchy / fluctuating inflow (environmental
heterogeneity and disturbance); variable-length genomes (still provisioned, §
contract-v2 §9); biomass as a variable (Pirt); a spatial map export + live
viewing instrument (report-v7).

---

## 10. The tuning discipline still binds

* **Scale anchors** — `CELL_CAPACITY` (also the `LOCAL_RESOURCE` normaliser),
  `W` (arena size, a memory/scale anchor — *not* a population regulator, §5).
* **World-harshness** — `CELL_INFLOW` (how fast a cell rains resource),
  `DIFFUSION_RATE` (how fast resource spreads / gradients smooth), `MOVE_COST`
  (the metabolic price of one step, §9). Set once to make the world livable and
  evolvable.
* **Scale anchor** — `MOVE_DEADZONE` `θ` (how hard a `MOVE` channel must drive to
  register a step, §9).
* **Outcome targets** — population size, number of coexisting lineages, spatial
  diversity, generations reached, time-to-ignition — **MUST NEVER be tuned.**
  They are what evolution must produce on its own under whatever livable physics
  we fixed. The first values (`CELL_CAPACITY=100`, `CELL_INFLOW=1`,
  `DIFFUSION_RATE=0.1`) are provisional livability settings, to be reset once if a
  baseline/seed scan shows the world is unlivable (everything dies) or degenerate,
  never nudged toward a target count.
