# DELAY Node Specification (Kernel v0)

## Purpose
The DELAY node provides **explicit temporal memory** by outputting a value from a previous tick.  
It introduces causal separation and enables non-trivial dynamics such as oscillation, memory, and phase lag.

---

## Definition
A DELAY node is a **stateful internal transformation** with node-local persistent memory.

It does **not**:
* measure time
* pause execution
* block computation

It only stores and re-emits values across ticks.

---

## Semantics

Let:
* `in` be the input value read from the previous state buffer
* `m` be the node’s internal delay memory

Then on execution:

1. **Output**

```
out = m
```

2. **Memory update**

```
m = in
```

This represents a **one-tick delay**.

---

## Temporal Properties
* DELAY introduces a minimum of one full tick of latency.
* Feedback through DELAY cannot be instantaneous.
* DELAY enables oscillatory and multi-step dynamics.

---

## Memory Model

* Each DELAY node owns exactly **one private memory slot**.
* Delay memory is:
    * local to the node
    * persistent across ticks
    * independent of the global state buffers
* Delay memory is stored in a flat, kernel-managed array indexed by node ID.

---

## Interaction with Double Buffering

* DELAY nodes read inputs from `statePrev`.
* DELAY nodes write outputs to `stateNext`.
* DELAY nodes update their private memory during execution.
* DELAY nodes must never read from `stateNext`.

---

## Dormancy Behavior

* If a unit executes no internal transformations during a tick:
    * DELAY nodes do not execute
    * delay memory is not updated
    * stored values remain unchanged

Dormancy freezes DELAY memory but does not erase it.

---

## Energy Accounting

* Executing a DELAY node counts as **one unit of internal work**.
* DELAY execution incurs normal activity cost.
* DELAY memory persistence itself is free.
* Dormant units incur no activity cost from DELAY nodes.

---

## Structural Decay
* DELAY nodes contribute to structural complexity.
* Structural decay applies regardless of activity.
* DELAY memory does not independently decay in Kernel v0.

---

## Initialization

* Initial delay memory value is defined by the kernel (e.g. `0.0`).
* Initialization must be deterministic.
* No special casing per unit.

---

## Prohibitions

The DELAY node MUST NOT:
* read from `stateNext`
* share memory with other nodes
* allocate memory dynamically
* decay its memory independently
* introduce variable-length delays in Kernel v0

---

## Extension Notes (Non-Normative)

Future kernels may extend DELAY to:
* multi-tick delays (ring buffers)
* noisy or lossy memory
* parameterized delay length

Such extensions must preserve:
* explicit causality
* node-local memory ownership
* correct energy accounting

---

## Contract Status
This specification is **binding** for Kernel v0.

Any implementation violating these rules is considered incorrect.

---
