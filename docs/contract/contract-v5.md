# Contract v5 — biomass (Pirt size physics)

Delta over [contract-v4](contract-v4.md). Adds **biomass** as a distinct per-unit
state variable: a unit now has a *size* (`mass[slot]`) separate from its energy.
Size is something a unit **grows** (a new `GROW` effector converts energy into
mass) and something it **pays for** (maintenance and locomotion now scale with
mass). Reproduction stops being an energy-investment "budding" act and becomes
**symmetric binary fission**: a unit splits its mass *and* energy in half into two
daughters. Geometry, intake wiring, motility geometry, indels, death-is-derived,
and determinism all carry over from v3/v4; this contract changes the *economics
of size and the act of dividing*, not the world's layout.

**Status: implemented** in kernel v5 (ADR 0028) — `KernelConfig` constants,
`NodeLayout` (`SELF_MASS` sensor, `GROW` action, `TOTAL`=24), `Kernel` (`mass[]`,
`settleGrowth` phase 7, fission `settleReproduction`, mass-scaled
maintenance/harvest/movement, `die()` mass dissipation), audit threaded through
`KernelSnapshot`/`DynamicsObserver`/`DynamicsSummary`/`MetricsWriter`, conformance
in `BiomassTest`. **Binding.**

Scope note — this is **Axis A** (scalar mass on the existing one-unit-per-cell
lattice). A unit's *size* is a number; it does **not** occupy more cells. The
multi-cell "a body fills N cells" model (Axis B), where surface-area-to-volume
effects fall out of geometry instead of being asserted (§4), is a separate future
milestone, not this one.

## 1. Mass is a distinct state variable — and it is crystallized energy

* Each slot gains `mass[slot]`, a non-negative `double` carried in the per-unit
  storage region alongside `energy[slot]` (one extra double per unit; ADR 0028
  justifies the footprint).
* **Mass is energy in structural form, not a new kind of matter.** There is still
  exactly **one** conserved currency. `GROW` (§2) converts spendable energy into
  structural mass; maintenance (§3) spends energy to *hold* that mass; fission
  (§5) splits it; death (§6) dissipates it. No second resource, no second
  conservation law — "matter you eat as a distinct resource" is the *multi-resource*
  milestone, deliberately deferred (§12).
* Because mass *is* stored energy, it enters the closed-system audit as a
  reservoir (§6): the audit equation gains a `Σ mass` term on both sides. No energy
  is created by growing; only the conversion loss and maintenance flow to the sink.
* Mass has **no hard cap**. Its upper bound is *physical*, not a constant: harvest
  (∝ `mass^α`, α<1, §4) is sublinear while maintenance (∝ `mass`, §3) is linear, so
  past an environment-set optimum the bill outruns the intake and growth cannot be
  sustained. Size is bounded by the energy economy, exactly as harvest capacity is
  bounded by genome size (contract-v4 §2) — not by a tuned ceiling.
* Mass is **not separately heritable**. A genome does not encode a target size;
  size is a runtime trait built by the `GROW` wiring under selection. Inheritance of
  size happens only through fission (a daughter is born at half the parent's mass).

## 2. Growth — the `GROW` effector (anabolism)

A new action node, `GROW`, lets a unit convert energy into mass. Like `HARVEST`
and `REPRODUCE`, growth is an **evolved drive**, never automatic — the kernel does
not "grow a unit when it can"; the wiring decides, or it never grows (a legal,
inert choice). Each tick, in the new growth phase (§8):

```
commit  = GROW_MAX · σ(growOutput)          # σ(x)=x/(HALF+x) ∈ [0,1), saturating
commit  = min(commit, energy[unit])          # cannot spend energy you lack
energy[unit] -= commit
mass[unit]   += GROW_YIELD · commit           # GROW_YIELD < 1: anabolism is lossy
energySink   += (1 − GROW_YIELD) · commit     # the conversion loss dissipates
```

A non-positive or non-finite `growOutput` grows nothing (you cannot un-grow; there
is no `SHRINK`/catabolism in v5 — §12). The saturating shape matches `HARVEST`
(contract-v1 §5) and the retired reproduction drive: a runaway feedback signal only
approaches `GROW_MAX`, never exceeds it, so growth per tick stays bounded.

## 3. Mass costs (what size *takes*)

**Maintenance now attaches to mass, replacing the energy proxy.** Contract-v1 §6
charged maintenance ∝ *energy held*, with its own justification that "energy
proxies biomass." v5 introduces real biomass, so the proxy is retired: the
maintenance term in the cost phase becomes

```
maintenance = MAINT_PER_MASS · mass[unit]        # was STORAGE_LEAK_RATE · energy[unit]
```

charged in **energy** to the sink (you spend energy to keep your body ordered; the
body itself is not consumed by maintenance — that would be catabolism, §12).
`STORAGE_LEAK_RATE` is retired; `MAINT_PER_MASS` replaces it. The size equilibrium
that the energy-proxy term used to enforce (a hoard implodes) now lives on mass:
a body bigger than the §4 optimum cannot pay its own upkeep and starves. The other
three cost terms — basal, activity (∝ propagations), aging (∝ damage) — are
**unchanged** (contract-v0 §3/§6, contract-v2 §7).

**Locomotion now scales with mass.** Moving a bigger body costs more:

```
moveCost = MOVE_COST_BASE + MOVE_COST_PER_MASS · mass[unit]      # was the flat MOVE_COST
```

charged on a taken step (contract-v3 §6, motility geometry otherwise unchanged:
one Moore step, deadzone, blocked-if-occupied). `MOVE_COST` is renamed
`MOVE_COST_BASE`; `MOVE_COST_PER_MASS` is new. A heavy unit is sluggish — it pays
more per step — which makes dispersal a real cost of size, not a free option.
Mass scales the *cost* of a step, not its *cadence*: a unit still moves at most one
cell per tick (throttling speed by size would need per-unit timing state — §12).

## 4. Mass benefits (what size *gives*) — the asserted surface law

Harvest capacity gains a **sublinear mass factor**, multiplying the existing
gene-dosage term (contract-v4 §2, ADR 0012):

```
capacity = HARVEST_CAPACITY_PER_CONNECTION · transporterCount · mass^α        # α < 1
```

so demand stays `capacity · σ(harvestOutput)` (contract-v1 §5, unchanged shape).
A bigger body harvests more (more surface to take up resource), but with
**diminishing returns** in size — the surface-area-to-volume law.

This `α` (start `0.5`, the 2-D surface/area scaling) is the **one place v5 asserts
by hand a law that Axis B would derive from geometry.** On a multi-cell body only
the perimeter cells touch fresh resource, so `mass^α` would *emerge* from how a
shape tiles the lattice; on the scalar model we encode the exponent directly. It is
disclosed here as the single hand-set surface constant, not smuggled in.

The combination is what makes size *interesting* (ADR 0028): harvest sublinear
(`mass^α`) against maintenance linear (`mass`) yields a **finite optimal size**,
set by how rich the local cell is — a richer cell supports a larger optimum. Were
both terms linear, size would be selectively neutral and merely drift. The
asymmetry is the milestone's reason to exist.

Emergent consequence (no extra code): a large unit drains its single cell faster
than a small one, depleting local resource and pressuring dispersal — size couples
to motility through the resource field, not through a rule.

Energy **storage** does *not* scale with mass in v5 (no `energy ≤ k·mass` cap). A
cap proportional to size creates a bootstrap trap — a tiny founder could not bank
enough energy to grow — so it is deferred (§12). The benefits of size in v5 are
harvest scaling and the capacity to fund fission; the storage-buffer benefit waits.

## 5. Reproduction is symmetric binary fission

This **supersedes the contract-v2 reproduction-investment model** (v2 §2/§4/§11's
`commit` / `REPRODUCE_YIELD` budding). With biomass present, a child's substance
must come from the parent's body — you cannot conjure a daughter's mass — so
reproduction becomes a *split*, not a *gift*.

Each tick, in the reproduction phase, for each unit still alive after paying its
bill (units scanned in ascending slot order, child installed immediately, so the
whole genealogy + geography stays deterministic — unchanged from v2):

1. **Trigger, not investment.** The `REPRODUCE` output is read as a bare intent:
   `divide ⇔ reproduceOutput > 0`. Its *magnitude* no longer means anything (there
   is no commitment to size). `REPRODUCE_MAX`, `REPRODUCE_HALF_SATURATION`, and
   `REPRODUCE_YIELD` are **retired**.
2. **Room.** Find the lowest-indexed free Moore neighbour for the child; none → no
   division this tick (contract-v3 §5, unchanged — a physical local constraint, no
   run halt). Claim a free slot; none → no division.
3. **Build the child genome** (point mutation + indels, contract-v4 §2, unchanged),
   giving `childGeneCount`, then `buildCost = BUILD_COST + BUILD_COST_PER_GENE ·
   childGeneCount` (contract-v4 §3, unchanged).
4. **Pay the replication work.** If `energy[parent] ≤ buildCost`, no division (you
   cannot afford the *act* of copying — an energy constraint, not a viability gate
   on the child). Otherwise charge it: `energy[parent] −= buildCost`,
   `energySink += buildCost`, `damage[parent] += buildCost`.
5. **Fission — split both reservoirs in half.** With `E' = energy[parent]` and
   `M = mass[parent]` *after* step 4:

   ```
   child:  energy = E'/2,  mass = M/2
   parent: energy = E'/2,  mass = M/2        # the slot-keeping daughter
   ```

   No yield loss on the split itself (the only loss was `buildCost`). Conservation
   is exact: `E_before = buildCost(→sink) + E'/2 + E'/2` and `M = M/2 + M/2`.

**There is no viability gate.** REPRODUCE always attempts the split when triggered
and affordable; the kernel never refuses a division because the daughters would be
"too small." A daughter born under-sized simply cannot cover its maintenance (§3)
against its sublinear harvest (§4) and dies within a few ticks — death stays
derived (`energy ≤ 0`), no special case. This is the deliberate, central design
choice (ADR 0028): premature division is **physically possible but costly** (you
spent `buildCost` and threw half your body into a doomed child), and the
**size-control checkpoint is left to evolve** — a `SELF_MASS`-gated REPRODUCE
wiring is *discovered* by selection, never coded. Real cells evolved nucleoid
occlusion / the Min system / the SOS brake for exactly this reason; we let the
guard arise rather than building it in. No exploit results: blind REPRODUCE drains
`buildCost` per attempt and spawns corpses, so it is selected out on its own.

**Aging asymmetry is retained as-is.** The slot-keeping daughter ("parent") keeps
its accumulated `damage`; the new-slot daughter ("child") resets `damage = 0`
(germline renewal, contract-v2 §7). This maps cleanly onto real bacterial
old-pole/new-pole aging asymmetry (Stewart 2005) — the inherited slot is the old
pole. `damage` does not split in v5 (a possible refinement, §12).

The **cell cycle is now physically mandatory**: a unit must `GROW` to accumulate
mass before fission yields two viable-sized daughters; it can no longer reproduce
off raw energy intake alone. Whether the evolved size-control law is sizer-, adder-,
or timer-like (the real debate, settled empirically as "adder") is left to emerge
and observe — the kernel imposes none of them.

## 6. Death dissipates mass; conservation and audit gain a `Σ mass` term

Because mass is stored energy, it must be accounted at death. **When a unit's
energy crosses to `≤ 0` (death, contract-v0 §9, derived — never a flag), its mass
dissipates to the sink** and `mass` is zeroed, at every site where death can occur
(cost, growth, reproduction work, movement):

```
energySink += mass[unit];  mass[unit] = 0      # body decays to the sink on death
```

so `Σ mass` always equals the summed mass of the *living*. (Routing a corpse's mass
into its cell's resource field instead — decomposition / nutrient cycling — is a
tantalizing but separate ecological feature, deferred to §12.)

The closed-system audit (contract-v3 §7) extends by the stored-mass reservoir:

```
INITIAL_energy + Σ initialCellResource + Σ initialMass + cumulativeInflow
    == Σ energy + Σ cellResource + Σ mass + energySink
```

Founders are provisioned with mass (`Σ initialMass`, §7) exactly as they are
provisioned with energy. `GROW`'s conversion loss, maintenance, `buildCost`, and
death-dissipation all flow to the sink; nothing creates energy. The
`energy-audit-error` line must stay ~0 (FP noise only) — the harness audit
(RunReport / observer) adds the `Σ mass` term.

## 7. Substrate grows — `SELF_MASS` sensor and `GROW` action

Two meaningful nodes are added to the fixed substrate (`NodeLayout`):

* **`SELF_MASS`** sensor — proprioception of size, normalized like `SELF_ENERGY`:
  `output = min(1, mass[unit] / SELF_MASS_SCALE)`. This is what lets a `REPRODUCE`
  or `GROW` channel be gated on the unit's own size (the substrate for the evolved
  checkpoint of §5). `Sensor.MEANINGFUL_COUNT` 4 → 5.
* **`GROW`** action — the anabolic effector of §2. `Action.MEANINGFUL_COUNT` 7 → 8.

`NodeLayout.TOTAL` therefore grows **22 → 24** (sensors 6→7, actions 8→9, internals
unchanged at 8; junk paddings unchanged). Because gene IDs wrap modulo the per-type
counts (AGENTS.md encoding), changing `TYPE_COUNT` re-maps how existing 32-bit genes
decode — so the baseline digest changes, expected and explained on re-baseline, as
it did when v3 added the `MOVE` effectors.

## 8. Tick pipeline gains a growth phase (9 → 10 phases)

A **growth phase** is inserted between cost and reproduction:

```
clear → propagate → evaluate → swap → intake → cost → GROWTH → reproduction → movement → diffusion
  1         2          3        4       5       6      7(new)       8            9          10
```

The metabolic order is *eat → pay upkeep → invest surplus into mass → divide*:
maintenance (phase 6) is charged on the mass held at tick start; growth (phase 7)
turns surplus into mass; fission (phase 8) then splits the just-grown mass, so a
unit can grow and divide within one tick. This **preserves every existing relative
ordering** (intake before cost before reproduction before movement before
diffusion) — no established phase moves relative to another — so it honors the
locked-pipeline invariant; it adds a phase, it does not reshuffle. ADR 0028 records
the placement (and why appending growth after diffusion, which would lag mass
effects by a tick and divorce growth from the metabolic sequence, was rejected).

## 9. What does not change

Lattice geometry and the torus; per-cell resource field, inflow, diffusion; intake
*wiring* (transporter-count dosage, saturating demand — only the `mass^α` factor is
added); motility *geometry* (one Moore step, deadzone, blocked-if-occupied — only
the cost gains a mass term); indels and the mutation-safe 32-bit encoding;
death-is-derived; per-slot storage identity (slot ≠ cell, contract-v3); the
zero-allocation / no-decode hot loop and memory-linear-in-units discipline (one
extra `double` per unit; ADR 0028); determinism (operation-counter mutation keying,
contract-v4 §4; the new `GROW` and fission draws read existing per-tick outputs and
add no new RNG stream). `saveState`/`loadState` (report-v8 checkpoints) gain `mass`
in the per-unit record so replay stays byte-exact.

## New constants (world-harshness, set once, never tuned to an outcome)

Calibrated once to make an evolvable world, then frozen — same discipline as the
indel rates (contract-v4, ADR 0026). Initial values are starting points for the
mandatory baseline pass, not targets:

* `MAINT_PER_MASS` — energy/tick of maintenance per unit mass (§3). Replaces
  `STORAGE_LEAK_RATE`. Sets, against `HARVEST_CAPACITY_PER_CONNECTION` and `α`, the
  optimal size.
* `α = 0.5` (`HARVEST_MASS_EXPONENT`) — the sublinear surface exponent on harvest
  (§4); the single hand-asserted surface law.
* `GROW_MAX` — ceiling on energy converted to mass per tick (§2).
* `GROW_HALF_SATURATION` — the σ half-saturation for the `GROW` drive (§2).
* `GROW_YIELD` (< 1) — anabolic efficiency; `1 − GROW_YIELD` dissipates to sink.
* `MOVE_COST_BASE` (renamed from `MOVE_COST`) and `MOVE_COST_PER_MASS` — locomotion
  cost floor plus the per-mass term (§3).
* `SELF_MASS_SCALE` — normalization for the `SELF_MASS` sensor (§7), analogue of
  `SELF_ENERGY_SCALE`.
* `INITIAL_MASS` (`M₀`) — founder mass at abiogenesis (§6/§7), provisioned like
  `INITIAL_ENERGY`. Chosen so a founder is immediately viable and can fission after
  a little growth.

Retired: `STORAGE_LEAK_RATE`, `REPRODUCE_MAX`, `REPRODUCE_HALF_SATURATION`,
`REPRODUCE_YIELD` (no back-compat shims — every call site moves to the new law).

## Biology grounding & modeling regime (non-normative, assumptions on record)

* **Pirt maintenance/growth split.** Energy splits into maintenance (∝ mass, §3,
  the upkeep of staying ordered) and growth (the surplus a unit invests, §2) — the
  classic Pirt partition. Maintenance spends energy without consuming the body;
  starvation death (energy → 0) is what eventually claims an over-grown cell.
* **Surface-area-to-volume.** Uptake on a perimeter, upkeep on a volume → sublinear
  harvest vs linear maintenance → a finite optimal size that grows with resource
  richness (Schaechter's growth law: richer medium, bigger cells). Asserted here as
  `mass^α` (§4); emergent from shape under Axis B.
* **Binary fission, symmetric.** The bacterial default: grow to ~2× birth size,
  split ~50/50 — energy/cytoplasm partition *with* the body, so halving mass entails
  halving energy (§5); they are not independent knobs.
* **Old-pole/new-pole aging.** Retaining `damage` on the slot-keeping daughter and
  resetting it on the new one (§5) reproduces the functional asymmetry of even
  "symmetric" *E. coli* division (Stewart 2005).
* **Evolved size-control checkpoint.** Real cells guard division (nucleoid
  occlusion, Min system, SOS/SulA) because premature/mispositioned fission is
  costly — minicells (anucleate, sterile) and chromosome guillotining. v5 mirrors
  the *selective situation*, not the machinery: division is dangerous-not-impossible
  (§5), and the checkpoint must evolve via `SELF_MASS` (§7). We can then observe
  which size-control law (sizer/adder/timer) emerges.

## Deliberate omissions (each a future knob, not a gap to fix now)

* **No catabolism / `SHRINK`.** Mass only leaves a living unit via fission (§5);
  there is no "burn your own body for energy when starving" (autophagy). A natural
  v5.1.
* **No mass-proportional energy storage cap.** Deferred to avoid the founder
  bootstrap trap (§4).
* **No corpse decomposition.** Death routes mass to the sink, not to the cell's
  resource field (§6); nutrient cycling ("blooms on corpses") is a separate
  ecological feature.
* **Symmetric fission only.** The split is fixed ½. An evolvable/asymmetric split
  ratio (budding-yeast / stem-like, restoring asymmetry the fission-native way) and
  `damage`-splitting are later additions.
* **Scalar mass (Axis A) only.** No multi-cell bodies; the `mass^α` surface law is
  asserted, not geometric (Axis B is its own milestone).
* **Mass = energy, one currency.** No distinct "matter" resource with its own
  conservation; biomass from multiple resources with stoichiometry is the
  multi-resource milestone.
