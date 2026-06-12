# 0013 — Cost model from biology (drop the per-gene tax)

## Context
The owner pushed on whether `DECAY_PER_CONNECTION` and `CAPACITY_PER_CONNECTION`
are physical laws or just "specific" coefficients. A comparison against real cell
bioenergetics settled it. A bacterium's energy goes to: biosynthesis/growth,
**maintenance** (membrane upkeep + turnover, modelled by Pirt's `m` ∝ biomass plus
an irreducible fixed floor), **active transport** (eating costs ATP), and
**expression/work** (a *running* gene costs; a *silent* one is nearly free —
carrying DNA is cheap, which is why junk persists).

Verdict on our four constants:
- `STORAGE_LEAK_RATE` (∝ energy) ≈ maintenance ∝ biomass — faithful.
- `COST_PER_PROPAGATION` (∝ signal moved) ≈ expression/work cost — faithful.
- `CAPACITY_PER_CONNECTION` (∝ #transporters) ≈ Vmax ∝ enzyme count — faithful
  (transporters are discrete, countable real objects; this "per connection" is honest).
- `DECAY_PER_CONNECTION` (flat tax ∝ #wires, paid even when silent) — **unbiological.**
  Cells barely pay to *carry* genes; they pay to *express* them and to *maintain*
  themselves. Taxing silent structure is the wrong axis.

## Decision
Remove `DECAY_PER_CONNECTION`. Replace the structural tax with a **fixed per-unit
basal cost** (`BASAL_COST = 0.5`) — the irreducible cost of staying organized
(membrane upkeep), charged to every living unit regardless of size or wiring. The
metabolic bill is now, per tick:

    charge = BASAL_COST + activity·COST_PER_PROPAGATION + energy·STORAGE_LEAK_RATE

— basal (fixed maintenance) + activity (expression/work) + maintenance (∝ size).
**No term scales with connection count.** Cost lives on what a unit *does* and
*holds*, not on what it *has*. Silent/junk wiring is now nearly free, as in
biology. `connectionCount` bookkeeping removed from the kernel.

The basal floor also fixes an old wart: a purely proportional leak decays a
dormant unit only asymptotically (never crossing ≤ 0). The fixed floor drives any
idle unit — even a connectionless husk — across zero in finite time, so there is
no costless persistence and no degenerate immortality.

`E*` becomes `(capacity − basal)/STORAGE_LEAK_RATE`. Specialization pressure
(contract §6) is now energetic — running many sensors costs traffic and finite
energy must be out-harvested — not a per-wire budget.

## Why this is right, not just different
Three of four constants already matched cell energetics; this aligns the fourth.
Cost is now defined over the conserved/flowing quantities (energy, signal),
representation-independent — it survives encoding changes, more nodes, and space.
`BASAL_COST` is per-unit (existence), not per-connection (bookkeeping), so it is no
longer tied to our genome representation.

## Evidence (harshness left comparable; not tuned for a survivor count)
Seed 42, 1000t: 39 survivors, audit 1.1e-9. Seed 7, 20k: monopolist unit 99 still
implodes (dies tick 1701), max unit energy 1986 (no hoard), 18 survivors, deaths
to tick 5053. Idle/empty genomes now die (basal floor).

## Rejected
- Keep per-connection decay (the unbiological axis).
- Fold the floor into the proportional leak only (never kills — asymptotic).
- Make basal ∝ size only (no fixed floor): same asymptotic-immortality problem.

## Deferred (biology's next gifts, explicitly future)
- **Transport costs energy** — active uptake should spend ATP ∝ intake (eating is
  not free). A cost-of-harvest term, when added.
- **Biomass as its own variable** — real cells separate size (maintenance scales
  with it) from energy charge (the spendable currency). We collapse both into
  "energy" and proxy size by it. Splitting them is the natural home for the
  growth term when reproduction lands (Pirt's `(1/Y)·growthRate`).
