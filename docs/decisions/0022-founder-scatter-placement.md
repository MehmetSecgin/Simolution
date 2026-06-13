# 0022 — Founder placement: deterministic uniform scatter

## Context
Founders were placed by `position[unit] = unit` — founder slot index equals cell
index. Cells are row-major (`cell = y·W + x`), so N founders filled cells
`0 .. N-1`: a contiguous strip along the top edge (e.g. 200 founders on a 130-wide
grid = the first ~1.5 rows). Every run therefore began as a wall-to-wall band
jammed against one edge, with the rest of the W² grid empty. The bloom could only
grow *out of* that band, and because the lattice is a torus the band sat across
the top-left seam — so the early dominant lineages appeared mirrored onto the
opposite edge and all four corners in the map viewer.

This is an imposed geometry nobody chose: a density gradient and an edge bias that
shape early competition, not a physical law. Spotted while watching a live run.

## Decision
Make founder placement an **explicit constructor input** and supply a uniform
scatter as the production policy:

- The canonical constructor now takes `int[] founderCells` — the cell each founder
  slot starts in — and validates it (length, range, no duplicate cell). No magic
  slot==cell fill, no overload that forwards a default (global no-shim rule):
  every call site states where its founders go.
- `Kernel.scatterFounders(seededCount, worldWidth)` is a static policy helper that
  returns a uniform scatter: a partial Fisher-Yates shuffle of the identity
  permutation `[0, W²)`, driven by a new placement RNG
  `Noise.placementUniform(seed, index)` keyed off `KernelConfig.RANDOM_SEED` (the
  same fixed seed RAND and mutation use). `Main` calls it; tests pass explicit
  cells (identity `{0,1,…}` reproduces the old layout exactly, so behaviour-only
  tests are unchanged).

Placement is world *setup*, not a kernel law — so it lives as a callable policy,
not baked into the constructor, mirroring how the kernel owns no fitness either.
Slot identity stays decoupled from cell (contract-v3 §2, ADR 0020): a founder's
slot is still its permanent storage identity; only its *starting position* changed
from `slot` to a scattered cell.

Implementation note: `placementUniform` returns `(h >>> 11) · 2⁻⁵³` ∈ [0,1). The
sibling `sample` uses `2⁻⁵²` then subtracts 1.0 for its [-1,1) range; copying that
scale here without the shift gave [0,2) and overran the Fisher-Yates bound — fixed
to `2⁻⁵³`. The same normalizer bug was found latent in `mutationUniform` (returned
[0,2) despite a [0,1) javadoc) and fixed in tandem, with the mutation constants
halved so realized rates are unchanged (see ADR 0021).

## Why
- **Removes an unphysical artifact.** A uniform random start has no edge or corner
  bias; the bloom radiates from many seed points instead of bleeding off one edge.
- **Stays deterministic.** Counter-based, keyed off the fixed seed and a
  domain-separated salt, so same count + grid → identical layout, reproducible
  run-to-run. No wall-clock, no mutable RNG stream.
- **No signature change, no per-tick cost.** Placement is computed once at
  construction (one O(cells) scratch array, freed after); the hot loop is
  untouched.

## Rejected
- **Keep slot==cell.** Simplest, but bakes the edge-strip geometry into every run.
- **Centered / clustered seed.** Trades one imposed geometry (edge) for another
  (central patch). Uniform scatter imposes the least structure.
- **Key the scatter off the run `--seed` so each run differs spatially too.**
  Founder *genomes* already vary by run seed; placement is world-setup, so keying
  it off the fixed `RANDOM_SEED` (as RAND/mutation do) is consistent and enough.
  The explicit `founderCells` param leaves the door open — a future caller can
  pass any run-seed-derived layout without a kernel change. Revisit if per-run
  geography becomes desirable.
- **Scatter inside the constructor.** First cut did this; pivoted to an explicit
  param so placement is visible at the call site and tests can control it (the
  blocked-movement test needs a mover and an *adjacent* blocker — impossible to
  request from an internal scatter).

## Consequence
`state-digest` folds `position`, so the baseline changes — every line downstream of
the new founder layout differs. Determinism re-verified (stable digest on rerun).
