
# Kernel v0 Vertical Slice — Genome, Nodes, Brain

## Purpose

This document defines a **minimal, concrete vertical slice** of Kernel v0:
* one unit
* four genome entries
* double-buffered execution
* explicit DELAY memory
* no environment, no interaction

Its purpose is to lock down:
* genome structure
* node semantics
* state layout
* execution order
* memory behavior

Any Kernel v0 implementation MUST be able to represent and execute this slice correctly.

---

## 1. Unit Definition

* Unit count: 1
* Node count: 4
* State slots: 4
* State index range: `[0..3]`
* Genome is immutable during execution
* Node IDs are globally indexed but form a contiguous slice for this unit

---

## 2. Genome Record Format

Each genome entry is a fixed-width record:

`[ opcode | inA | inB | out | param ]`

* `opcode` determines node type
* `inA`, `inB`, `out` are indices into the unit’s state slice
* `param` is optional and opcode-dependent
* Unused fields are ignored

---

## 3. Genome Contents (4 Nodes)

| Node | Opcode | inA | inB | out | param |
|------|--------|-----|-----|-----|-------|
| N0 | CONST | – | – | 0 | 1.0 |
| N1 | ADD | 0 | 3 | 1 | – |
| N2 | DELAY | 1 | – | 2 | – |
| N3 | MUL | 2 | 0 | 3 | – |

---

## 4. Wiring Interpretation

Signal flow:

```
CONST(1.0) ──┐
├─> ADD ──> DELAY ──┐
│                   │
└──────── MUL <─────┘
```

More explicitly:

* `state[0]` = constant signal
* `state[1]` = state[0] + state[3]
* `state[2]` = delayed(state[1])
* `state[3]` = state[2] * state[0]

---

## 5. State and Memory Initialization

Initial conditions:

```
statePrev = [0, 0, 0, 0]
stateNext = [0, 0, 0, 0]
delayMemory[N2] = 0
```

Initialization is deterministic.

---

## 6. Execution Model

* Execution is **double-buffered**
* All nodes:
    * read from `statePrev`
    * write to `stateNext`
* DELAY nodes additionally read/write their private delay memory
* No node may read from `stateNext`

Nodes are executed in genome order.

---

## 7. DELAY Semantics (Applied)

For node N2:

* Output = stored delay memory
* Delay memory is updated with `statePrev[inA]`
* Delay memory is not updated if the unit does not execute

---

## 8. Activity and Cost Implications

* Executing any node counts as internal activity
* Each node execution incurs activity cost
* If no nodes execute in a tick, activity cost is zero
* Structural decay still applies regardless of activity

---

## 9. Dynamics Summary

This genome defines:
* a feedback loop
* explicit temporal memory
* self-amplifying dynamics

Depending on parameters and decay:
* activity may grow
* stabilize
* oscillate
* or eventually collapse

No semantics or goals are encoded.

---

## 10. Invariants Enforced by This Slice

* Genome indices reference only local state
* Node memory is node-local and persistent
* No instantaneous feedback exists
* All causality flows across ticks
* No engine-side interpretation is required

---

## 11. Contract Status

This vertical slice is **binding** for Kernel v0.

Failure to support this configuration indicates:
* incorrect genome layout
* incorrect buffering
* incorrect DELAY semantics
* or incorrect energy accounting

---
