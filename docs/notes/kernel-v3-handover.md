# Kernel v3 — handover & next steps

Snapshot for whoever (owner or agent) picks this up next. Branch
`kernel-v3-space`. Non-binding notes; the binding laws are
`docs/contract/contract-v3.md` and ADRs 0018–0020.

## Where we are

Kernel v3 turns the well-mixed world into a **place** and lets units **move**:

- **Space (ADR 0018/0019).** A `W×W` **toroidal lattice**, one unit per cell. The
  global resource reservoir became a **per-cell field** (uniform inflow + a
  mass-conserving diffusion phase); a unit senses and harvests **only its own
  cell** (`LOCAL_RESOURCE` replaced the global `RESOURCE` sensor). Offspring are
  placed in a free **Moore neighbour** — the grid *is* the slot pool, so v2's
  `MAX_UNITS` halt-on-full is retired (population is bounded physically by `W²` and
  energetically by starvation). Result: **coexisting lineages** instead of the old
  mean-field monoculture.
- **Motility (ADR 0020).** Four `MOVE_N/S/E/W` effectors (Fork B). Each tick a unit
  may take one Moore step (`dx` from `E−W`, `dy` from `N−S`, deadzone `θ`), paying
  `MOVE_COST`; blocked if the target cell is occupied. **Chemotaxis is emergent**
  via `LOCAL_RESOURCE` + `DELAY` (temporal sensing, blind to the true field — real
  run-and-tumble). A unit's **slot is a permanent storage identity**; the cell it
  occupies is `position[slot]` (`cellOccupant[cell]` enforces exclusivity), so a
  move is three int writes — **no recompile, no allocation**.
- **Viewer (report-v7).** `MapFrameWriter` streams a sampled `<base>.map.txt`
  (dense resource digits + sparse `cell:lineage`, O(1) RAM, flushed per frame).
  `tools/mapviz.py` makes a self-contained HTML scrub player; `--serve PORT`
  starts an in-JVM `LiveServer` (JDK `com.sun.net.httpserver`, no deps) that tails
  the file so a run can be **watched live as it ticks**, then scrubbed.

Determinism holds (digests stable on rerun), energy is conserved (audit ~1e-8
including `MOVE_COST` → sink), and the memory doctrine holds (footprint O(cells),
constant in tick count; frames live on disk).

## Substrate change to be aware of

Adding the 4 `MOVE` effectors grew `NodeLayout.TOTAL` to **22** (sensors 6,
internals 8, actions 8) and the action type space from 4 → 8. So a random gene
targeting an action now hits `HARVEST`/`REPRODUCE` with probability ~1/8 (was
1/4): **spontaneous ignition is rarer**, the abiogenesis lottery is harder, and a
given seed produces a thinner founding population than the v3-space-only substrate
did. This is an honest consequence (contract-v2 §10), not a bug — scan seeds.

## The current constants (world-harshness / anchors — set once, never
## outcome-tuned; contract-v3 §10)

`KernelConfig`, new in v3:
- resource field: `CELL_CAPACITY = 100`, `CELL_INITIAL = CELL_CAPACITY`,
  `CELL_INFLOW = 1.0`, `DIFFUSION_RATE = 0.1`
- motility: `MOVE_COST = 0.2`, `MOVE_DEADZONE = 0.1`

These are **provisional livability settings** (ADR 0019). A baseline + seed scan
showed the world is livable and evolvable (lineages coexist, audit clean). Per the
tuning discipline they may be reset *once* if a scan shows universal extinction or
a degeneracy — never nudged toward a population/lineage/generation target.

Worth a deeper look: with `CELL_INFLOW=1` per cell the field stays near-full where
population is sparse, so **depletion gradients are faint** in the map. If you want
to *see* (and select on) gradients, a stingier inflow or richer harvest would make
local scarcity visible — but treat that as a one-time livability decision with an
ADR, not a knob to fiddle.

## Next ideas on deck (roughly in order; contract-v3 §9)

1. **Per-unit map overlays** (cheap, high-value for observation). The frame schema
   can carry extra per-cell channels without a format break (report-v7): energy as
   brightness, age (damage) as hue shift, or movement trails. Lets you *see*
   fast/slow movers, old vs young, dense vs starving — currently the map only
   shows lineage + resource.
2. **Variable-length genomes** (indels / gene duplication). Storage is already
   provisioned (per-slot `MAX_GENES` + `geneCount`). Operators-only: insert /
   delete / duplicate, a `replication-cost ∝ genome length` term so genomes don't
   bloat free, and the `MAX_GENES` cap behaviour. The big open-ended-complexity
   unlock — duplicate a HARVEST transporter gene → more capacity. Its own ADR.
3. **Multiple resources + stoichiometry** (Redfield/resource-ratio). Breaks
   competitive exclusion further and gives true niches *even within* space — a
   second `RESOURCE`/`HARVEST` channel with a required ratio. Its own ADR.
4. **Signalling now that neighbours exist** — `CROWDING` (occupied-neighbour
   count) sensor + `EMIT` effector → density-dependence, quorum, public goods,
   cooperation/cheating. Space made this possible; it was deferred from v3.
5. **Environmental dynamics** — fluctuating / patchy inflow (seasons, disturbance)
   to select for bet-hedging and dormancy (`RAND` + `DELAY` already make those
   wireable). Patchy inflow introduces privileged-location heterogeneity, so it
   needs an honest framing in an ADR.
6. **Biomass as a distinct variable** (Pirt) — the fuller home for aging; gives
   reproduction a real *time* cost (cell cycle). `damage` is the scalar down
   payment to reinterpret.
7. **Live viewer polish** — CORS header on `/frames` (so the live map can embed in
   other origins), an in-page lineage legend, FPS control in live mode.

## How to run / inspect

```sh
# evolving population + all sidecars + map
./gradlew run --args="--units 100 --ticks 2000 --seed 99 --world 100 --out runs/run.txt"
python3 tools/visualize.py runs/run.txt        # economy/population/lineage dashboard
python3 tools/mapviz.py   runs/run.map.txt     # spatial scrub player -> runs/run.map.html

# watch a run LIVE while it ticks
./gradlew run --args="--units 200 --ticks 40000 --seed 99 --world 130 --out runs/live.txt --serve 8090"
# open http://localhost:8090  (follow-live + scrub; Ctrl-C to stop serving)
```

Seed note: many seeds go extinct (founder lottery, harder now — §"substrate
change"). Seed 99 at `--world 100+` blooms then settles into a textured grid-wide
population with several lineages; good for the viewer.

## Papercuts / gotchas

- `--serve` requires `--out` (the `.map.txt` is the live source of truth) and
  blocks after the run (keeps serving until Ctrl-C). Off by default, so tests /
  baseline don't hang.
- Only `runs/baseline.txt` + `runs/baseline.units.csv` + `runs/baseline.wiring.csv`
  are committed; every other sidecar (`.map.txt`, `.map.html`, `.lineage`,
  `.timeseries`, `.population`, `.popwiring`) is gitignored (`runs/*` whitelist).
- **Mandatory baseline workflow** before any kernel-behaviour change: regen
  `runs/baseline.txt` with `--units 100 --ticks 1000 --seed 42 --world 100 --out
  runs/baseline.txt`, diff, explain. `state-digest` (now folds the resource field
  and `position`) catches sub-display drift.
- No CLI override for the v3 constants (`CELL_*`, `MOVE_*`, `MUTATION_*`,
  `AGING_*`) — experiments mean editing `KernelConfig` + rebuild. If sweeping
  often, add `--cell-inflow` etc. (no compat shim per the global rule).
- The old docs said `NodeLayout.TOTAL = 13`; that was stale long before v3. It is
  **22** now (and was 18 at v3-space). Compute it, don't trust prose.

## Key files

- `kernel/runtime/Kernel.java` — 9-phase tick; per-cell resource field +
  diffusion (phase 9); per-cell intake; `position`/`cellOccupant`; spatial birth
  (`freeMooreNeighbour` + `nextFreeSlot`); `settleMovement` (phase 8).
- `kernel/config/KernelConfig.java` — all constants.
- `kernel/layout/NodeLayout.java` — substrate; `LOCAL_RESOURCE`, `MOVE_N/S/E/W`.
- `sim/MapFrameWriter.java`, `sim/LiveServer.java`, `src/main/resources/live.html`,
  `tools/mapviz.py` — the map viewer (report-v7).
- `docs/contract/contract-v3.md`; ADRs 0018 (geometry), 0019 (economics),
  0020 (motility); `docs/specs/report-v7.md`.
