# Kernel v0 Contract

## 0. Scope

Kernel v0 defines a closed, location-free system whose purpose is to establish truthful energy accounting under fixed laws.
Kernel v0 is a baseline experiment in persistence under entropy, not a life simulator.

---

## 1. Time
* Time advances in discrete, global ticks.
* Time is monotonic and deterministic.
* There is no real-time coupling.

---

## 2. Population
* The system maintains a preallocated, fixed-capacity set of slots.
* A slot may be alive or dead.
* Slot indices have no semantic meaning.
* Death is marking, not deletion.

---

## 3. Energy
* Energy is a scalar stored per alive slot.
* Total system energy strictly decreases over time.
* There is no energy intake in Kernel v0.
* Energy is never created.
* Energy may be irreversibly lost to a sink.

---

## 4. Structural decay (mandatory entropy)
* Every alive slot loses energy every tick due to structural decay.
* Structural decay represents unavoidable entropy.
* Structural decay depends only on static properties of structure (e.g. size, wiring).
* Structural decay applies regardless of internal activity.
* Structural decay cannot be disabled, paused, or bypassed.

---

## 5. Internal computation
* Internal computation is defined exclusively by genome-specified transformations.
* Internal computation consists of executing transformation rules on internal state.
* The kernel does not interpret meaning, intent, or goals.
* The kernel does not force internal computation.

---

## 6. Activity cost (work-based)
* Activity cost is incurred only when internal computation executes.
* Activity cost is proportional to actual work performed (e.g. node executions).
* If no internal transformations execute, activity cost is zero.
* Activity cost represents the energetic cost of maintaining non-equilibrium dynamics.

---

## 7. Dormancy (emergent)
* Dormancy is not a mode or state.
* Dormancy is the absence of internally sustained dynamics.
* A slot is considered dormant when no genome-defined transformations execute.
* Dormancy incurs no activity cost.
* Dormancy does not alter or reduce structural decay.

---

## 8. External laws
* External/global laws may modify state variables directly.
* External laws represent enforced constraints, not internal actions.
* External laws do not consume activity energy.
* External laws must be uniform and non-conditional.
* External laws must not encode computation.

---

## 9. Death
* A slot is dead when its energy ≤ 0.
* Death is not an event; it is a failed invariant.
* Dead slots do not execute internal computation.
* Dead slots remain as inert capacity.

---

## 10. Prohibitions (Kernel v0 MUST NOT include)
* No energy intake
* No reproduction
* No mutation
* No interaction between slots
* No space or locality
* No sensors or perception semantics
* No goals, fitness, or rewards
* No species or type labels
* No dormancy flags or special modes

---

## 11. Valid outcomes

Kernel v0 is considered correct if:
* All slots eventually die.
* Different structures persist for different durations.
* Quiet (low-dynamic) regimes can emerge naturally.
* No strategy is privileged by the kernel.
* Energy accounting is exact and explainable.

---

## 12. Interpretation boundary

Kernel v0 defines laws, not meanings.
Any interpretation of behavior or “strategy” is external to the kernel.
