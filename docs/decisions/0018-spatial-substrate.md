# 0018 — Space: 2D toroidal lattice, per-cell resource, local life (kernel v3-basic)

## Context
Through v2 the world was well-mixed: one global resource reservoir, sensed and
harvested identically by all. Competition was mean-field — strategies compete
against the average, so the single fittest harvester wins everywhere and the
population collapses to a monoculture (the gen-1757 specialist). No niches, no
refuges, no neighbours: nowhere for diversity to live, and no substrate for any
later ecological mechanic (motility, signalling, predation all need neighbours).
The owner chose space as the next milestone (see contract-v3). This ADR records
the geometry + physics decisions; constants are in ADR 0019.

## Decision (binding spec: contract-v3)
The world becomes a square **`W × W` toroidal lattice**, one unit per cell, **slot
index = cell index** (no position array). Life is **local**:

* **`MAX_UNITS = W·W`; the grid is the slot pool.** `W` is the only arena anchor,
  passed to the kernel at construction (the constructor's `maxUnits` parameter
  becomes `worldWidth`). contract-v2 §5's separate `MAX_UNITS` cap and
  **halt-on-full are retired**: space is the honest carrying capacity.
* **`LOCAL_RESOURCE` replaces the global `RESOURCE` sensor** at the same node
  index — a unit reads only its own cell. `NodeLayout.TOTAL` is unchanged (no new
  node), so genome bit-meaning and substrate size are preserved; only that one
  sensor's transduction changes (reservoir/CAPACITY → cell/CELL_CAPACITY).
* **Resource is a per-cell field** (`double[W·W]`, double-buffered for diffusion).
  Uptake is cell-local: `intake = min(demand, cellResource)` with the unchanged
  saturating `demand` (no within-cell contention — cells are exclusive, so the v1
  shared-pool proportional rationing is gone). Inflow is per-cell and uniform
  (`min(CELL_INFLOW, CAPACITY−cell)`), summed into `cumulativeInflow`.
* **Diffusion is new phase 8** — a mass-conserving 4-neighbour discrete Laplacian,
  double-buffered, never clamped. Phases 1–7 keep their order/meaning; only phase
  5 (uptake → per-cell) and phase 7 (placement → spatial) change internally. This
  honours "append, never reorder."
* **Birth is spatial.** A reproducing parent places its child in the
  lowest-indexed **free Moore neighbour**; none free → no birth this tick (a
  physical, local constraint, like failing to afford `BUILD_COST` — *not* a global
  cap). Children install immediately for deterministic same-tick placement.

## Why this shape (and not the alternatives)
* **2D, not 3D** — 2D captures gradients/patches/fronts and renders as an image
  for the planned live map; 3D triples cost (N³ cells, 6-neighbour stencil) for no
  evolutionary novelty until biomass/size exists (surface-to-volume). Deferred,
  maybe permanently.
* **Discrete lattice, not off-lattice float positions** — a cell is an array
  index; field = flat `double[]`; diffusion = stencil. Cache-friendly,
  zero-allocation, deterministic. Off-lattice needs spatial-hash neighbour queries
  and per-tick allocation — breaks the flat-array/footprint doctrine.
* **One unit per cell** — cells take space (real), gives spatial exclusion for
  free, and makes the grid *be* the slot pool so the awkward v2 halt-on-full
  dissolves into geometry.
* **Toroidal, not walled** — no edge/corner = no privileged location, matching
  no-semantics. A box would make corners physically different places.
* **Uniform inflow, not patchy** — no cell is intrinsically richer; all gradients
  are life's own depletion. Patchy geology is a deliberate later heterogeneity
  knob, loaded enough to deserve its own decision.
* **Sessile this milestone** — motility is a whole second mechanic (movement
  physics, collisions, motility cost, 4 new effectors); sessile-with-dispersal
  (offspring placement) already delivers gradients, local competition, refuges.
  Motility is the next sub-milestone (contract-v3 §9, Fork B locked).

## No semantics check
The lattice is homogeneous, the resource law identical in every cell, the torus
edgeless. The kernel never rewards clustering, dispersal, or any location.
Gradients, patches, and fronts are emergent. `LOCAL_RESOURCE` is transduction (a
point measurement), not perception — gradient-climbing (chemotaxis) is the
genome's to build from `LOCAL_RESOURCE` + `DELAY`, exactly as real bacteria sense
temporally, blind to the true field.

## Footprint
Per-tick state grows by two `double[W·W]` field buffers (the live field + the
diffusion next-buffer) — `O(cells)`, constant in tick count, preallocated once.
The connection/unit store is unchanged (`MAX_UNITS · MAX_GENES`, now with
`MAX_UNITS = W·W`). The hot path gains one O(cells) diffusion pass; intake becomes
a single O(cells) cell-local pass (was a two-pass global allocation). No new
per-tick allocation. Within the memory budget (AGENTS.md): linear in the arena.

## Rejected
- Keep the global reservoir (the monoculture degeneracy itself).
- Walled/reflecting boundary (privileged corners/centre).
- Patchy or point-source inflow now (privileged rich cells — a loaded later knob).
- Off-lattice continuous space (allocation + hash queries, breaks doctrine).
- Scattered founder placement (cosmetic; a non-binding later refinement —
  block placement on a homogeneous torus is just a labelling).
- 3D (cost without payoff pre-biomass).
- Motility folded in now (too big; its own sub-milestone).

## Deferred
Motility (Fork B), signalling/quorum, multi-resource + stoichiometry, fluctuating
/ patchy inflow, scattered founders, and a spatial map export + live viewer
(report-v7) — all build on this. See contract-v3 §9.
