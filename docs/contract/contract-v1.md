# Kernel v1 Contract — Open System (energy intake)

## 0. Scope

Kernel v1 opens the closed v0 system: units can now take in energy from an
environment. This is the threshold from "closed entropy filter" to "open system
where persistence becomes possible." It **supersedes v0 §3 and §10's no-intake
prohibitions**; every other v0 law still holds (time, decay, activity cost,
dormancy, death, conservation, no-semantics).

Reproduction is **not** in this milestone — intake lands first (see §9). Until
reproduction exists there is no evolution, only a population sorted by how well
each unit harvests; v1 is the floor reproduction will stand on.

This contract is **binding**. If code and this contract disagree, the contract
wins.

---

## 1. The governing principle (no semantics, restated for intake)

Intake is the most dangerous feature for the "no semantics in the kernel"
invariant: the instant energy arrives *because a unit did something*, the kernel
would be declaring that something good — a fitness function smuggled in.

> **The kernel and world offer energy uniformly and convert it uniformly. All
> inequality in what units actually capture must emerge from genome and
> behaviour — never assigned by the kernel.**

The sun shines equally on everything; a cell with the right chemistry eats, a
rock does not. Physics never decided chlorophyll was good.

---

## 2. Transduction vs perception (binding design line)

* **Transduction** — raw world-state entering the brain at a single point. This
  is physics. The kernel codes it, minimally.
* **Perception** — interpretation of raw signals (gradients, trends,
  coincidence, abstraction). This is computation. It MUST emerge from wiring;
  the kernel never codes it.

Heuristic for "code it or let it emerge?": a **raw measurement at one point** →
code it. A **relationship, comparison, history, or abstraction** over
measurements → let it emerge (via internal nodes + DELAY).

Consequence: never add high-level sensors ("food to my left"). Add raw
transduction; let circuits build the rest.

---

## 3. Environment model (location-free, well-mixed)

Kernel v1 remains **location-free**. With no space, all environmental coupling
is through **global, well-mixed scalars** — a stirred flask / chemostat, not a
petri dish. The world maintains a few global state variables, updates them each
tick from the population's actions, and every unit senses the same value.

No locality, no neighbourhoods, no spatial gradients. Space is a far-future
milestone that turns every global scalar into a local field.

---

## 4. RESOURCE (the first sensor) and the resource pool

* The world maintains **one global resource level** (a scalar).
* It is **depletable** (harvest draws it down) and **replenished** by a fixed
  inflow law each tick.
* The `RESOURCE` sensor reads the current level — identical for every unit.
* No spatial gradient exists; a **temporal** gradient does (the level rises and
  falls over ticks). A unit can perceive the trend only by computing it
  internally (e.g. `RESOURCE → DELAY → difference`) — an emergent percept, not a
  coded one.
* The resource pool and the RESOURCE sensor are the **same variable**: the
  competition and the perception are one global number. An infinite/constant
  field would be informationally dead (nothing to perceive), so a depletable
  pool is preferred for letting perception evolve.

---

## 5. HARVEST (the first effector) and intake

* The world reads one designated **harvest action channel** and converts its
  output to an energy *demand* via a fixed, uniform, **saturating** law, then
  draws the granted intake from the resource pool:

  `demand = INTAKE_MAX × harvest / (HALF_SATURATION + harvest)`, for `harvest = max(0, harvestOutput)`
  `intake = demand × f(resourceAvailable)`

* `max(0, …)`: you cannot un-eat. The genome must drive the channel positive to
  feed itself.
* **Uptake saturates (Michaelis–Menten).** Demand rises with harvest output but
  never past `INTAKE_MAX` — the transporter ceiling. *You cannot eat infinitely
  fast.* This is the central anti-degeneracy law: a runaway/divergent signal
  buys no extra intake, so there is no incentive to explode a feedback loop into
  the mouth, and the population demand total stays bounded (the shared
  denominator can never overflow). Realised intake still differs per unit only
  by how much harvest behaviour each genome produces — up to the ceiling.
* `INTAKE_MAX` and `HALF_SATURATION` are **single global constants** — the same
  conversion law for everyone (they replace the earlier linear `EFFICIENCY`).
* If total demand exceeds the pool (finite-pool competition), allocation is
  **proportional to demand** and computed from the population total — a
  deterministic, order-independent division. `f(resourceAvailable) = min(1, R/ΣD)`.

---

## 6. Sensory acuity is emergent, not coded

* A sensor emits a value; the **connection weight from it is the gain knob**.
  Strong wiring from a sensor = acute perception of it; weak/absent = dim/blind.
* Acuity is therefore **already per-unit, heritable, and evolvable** through
  connection weights. Different sensory strategies (good "eyes" vs reliance on
  other senses) emerge from which sensors a genome wires strongly.
* The **decay budget forces specialization**: a unit cannot afford to wire every
  sensor strongly (more connections = more structural decay), so it must invest
  its budget — acute vision *or* acute internal sense, rarely both.
* The kernel MUST NOT carry a per-unit, per-sensor efficiency value. That would
  be redundant with connection weights and would have the kernel assigning
  talent the genome did not earn.
* **Fidelity / perceptual noise** (signal-to-noise, which weights cannot
  express) is a genuine separate axis and a **documented FUTURE option** — not
  in v1. If ever added, prefer making it emergent from wiring investment over a
  coded per-unit value.

---

## 6a. Storage maintenance — no costless persistence

Saturating uptake caps the *rate* of eating; this law caps the *worth of
hoarding*. Together they make "eat everything, live forever" thermodynamically
impossible.

* Every living unit pays a **maintenance cost proportional to the energy it
  holds**: `maintenance = STORAGE_LEAK_RATE × energy`, charged to the sink each
  tick alongside structural decay and activity cost. It is basal metabolism /
  entropy on the hoard — bigger store, bigger upkeep.
* This is a **uniform thermodynamic law**, not a goal: it applies identically to
  every unit and privileges no behaviour (same standing as structural decay,
  contract v0 §6). The kernel still interprets nothing.
* **Emergent carrying capacity.** Because intake saturates at `INTAKE_MAX` and
  upkeep grows with the hoard, there is a stable equilibrium energy
  `E* = (INTAKE_MAX − baseCost) / STORAGE_LEAK_RATE`. Above it, upkeep exceeds
  the most a unit could ever eat, so the hoard **implodes** back toward `E*`. A
  unit cannot accumulate without bound; a gluttonous diverger starves on its own
  bulk. `E*` is per-unit and emergent (it falls out of the genome's costs), not
  coded.
* **Persistence now requires intake** (supersedes contract v0's "degenerate
  immortality" note in §6/§9). Any wired unit that stops eating dies: its fixed
  structural-decay floor plus the leak drive energy across zero in finite time.
  A connectionless husk has no fixed floor, so its purely-proportional leak only
  decays its store asymptotically toward zero — it never crosses ≤ 0, but bleeds
  to negligible energy and loses all competitive standing. Either way there is no
  costless persistence: staying meaningfully alive means eating at least as fast
  as you leak. This is the real consequence of opening the system — survival is
  earned, not free.
* Conservation is unaffected: maintenance flows unit → sink, so the audit of §7
  still balances exactly.

---

## 7. Conservation and audit (extended)

* Energy now flows **reservoir → units → sink**. The environment reservoir is a
  finite (or inflow-fed) source; intake draws it down.
* Energy is still never created from nothing: it is conserved across
  **reservoir + live units + sink**. The audit invariant generalises; the
  `energy-audit-error` line must stay ~0 (floating-point noise only).

---

## 8. Tick pipeline (additive)

Intake is a **new additive phase**; phases 1–5 (clear → propagate → evaluate →
swap → settle cost) are unchanged. Open implementation choices (to be settled
with an ADR when built):

* infinite flat field first (validate harvest) vs depletable pool first
  (informative sensor) — §4 leans depletable;
* dedicated HARVEST action vs reusing ACTION_Y as the mouth — leaning dedicated,
  so ACTION_Y stays the generic channel and harvest is explicit/measurable;
* order of intake vs cost within the energy settle (can a unit eat its way out
  of dying the same tick?).

---

## 9. Sequence (binding order)

1. **Intake** (this contract): RESOURCE + HARVEST, uniform transduction,
   emergent acuity, well-mixed depletable pool.
2. **Reproduction + mutation**: the loop closes, evolution proper. Reproduction
   cost is paid in energy intake supplies; without intake first it is only
   subdivision of a shrinking pool.

Rationale: neither alone gives evolution. Intake without reproduction sorts a
fixed population then freezes; reproduction without intake subdivides a fixed
pool until collective death. Intake is the floor because reproduction's economy
is denominated in the energy only intake can replenish.

---

## 10. Invariants retained from v0

Determinism (same seed → identical history), memory discipline (footprint
linear in units, constant in ticks; global scalars are O(1), computed
order-independently from population sums), no per-tick allocation/decoding in
the hot loop, memory only via DELAY, mutation-safe encoding, and death as a
derived invariant (`energy ≤ 0`, no alive flag) all still hold.

---

## 11. Future, explicitly out of v1

Reproduction, mutation (next milestone); CROWDING / EMIT and quorum sensing;
perceptual fidelity/noise; genome-encoded efficiency traits; and space (which
turns every global scalar into a local field). Each arrives as its own additive
milestone with its own ADR.
