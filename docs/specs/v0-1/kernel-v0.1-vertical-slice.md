# Kernel v0.1 Vertical Slice — Connection-Based Genome

## Purpose

This document defines a **minimal, binding vertical slice** for Kernel v0.1 using a
**connection-based genome** encoded as fixed-width 32-bit genes.

It replaces earlier node-list examples and serves as the canonical reference for:

* genome decoding
* node substrate behavior
* signal propagation
* explicit memory (delay)
* activity and cost semantics

Any Kernel v0.1 implementation MUST be able to represent and execute this slice correctly.

---

## 1. High-Level Overview

* Units do not define nodes.
* Nodes are a **fixed substrate** provided by the kernel.
* Genomes define **connections between nodes**.
* One gene = one directed weighted connection.
* All nodes are numeric-in / numeric-out.
* Nodes are stateless across ticks unless explicitly defined otherwise.
* Memory exists only via DELAY nodes.

---

## 2. Node Substrate (Fixed)

Kernel v0.1 supports exactly **three node roles**:

* **Sensory** — signal sources, no inputs
* **Internal** — signal transformers (may include memory)
* **Action** — signal sinks, interpreted by the environment

No node encodes semantic meaning.

---

### Sensors

| Type ID | Node  | Description                                |
|---------|-------|--------------------------------------------|
| S0      | CONST | Always outputs `1.0`                       |
| S1      | RAND  | Outputs uniform random value in `[-1, +1]` |

---

### Internal Nodes

| Type ID | Node   | Description                                   |
|---------|--------|-----------------------------------------------|
| I0      | ADD    | Sums all incoming signals                     |
| I1      | MUL    | Multiplies the two strongest incoming signals |
| I2      | CLAMP  | Clamps input to `[-1, +1]`                    |
| I3      | DELAY1 | Outputs previous tick input                   |
| I4      | THRESH | Outputs `+1` if input > 0, else `-1`          |

All internal nodes are **stateless across ticks**, except `DELAY1`.

---

### Actions

| Type ID | Node     | Description                                       |
|---------|----------|---------------------------------------------------|
| A0      | ACTION_Y | Output channel (interpreted by environment later) |

Action nodes do not produce outputs usable by the brain.

---

## 3. Genome Encoding (32-bit)

Each genome entry is **8 hexadecimal digits (32 bits)**.

Example:

```
F1351FE2
```

Binary layout:

```
[ SrcType | SrcID | DstType | DstID | Weight ]
[   1     |  7    |   1     |  7    |   16   ]  bits
```

---

### Field Definitions

| Field   | Bits | Meaning                                              |
|---------|------|------------------------------------------------------|
| SrcType | 1    | 0 = Sensory, 1 = Internal                            |
| SrcID   | 7    | Index into source node list (modulo applied)         |
| DstType | 1    | 0 = Internal, 1 = Action                             |
| DstID   | 7    | Index into destination node list (modulo applied)    |
| Weight  | 16   | Signed integer mapped linearly to a bounded strength |

---

### Weight Mapping (No Clamping)

The 16-bit signed integer range `[-32768, +32767]` is **linearly mapped** to a fixed
strength range:

weight = `int16 * (MAX_WEIGHT / 32768.0)`

Where:

`MAX_WEIGHT = 4.0`

This mapping guarantees:

* bounded connection strength
* no discontinuities
* no clamping during decoding

Signal stability is handled **only** by explicit CLAMP nodes.

---

## 4. Vertical Slice Genome (4 Genes)

This slice uses **4 genome entries** to form a minimal feedback system with memory.

### Genome (Hex)

```
G1 = 40A10200
G2 = 40A30200
G3 = 44A10200
G4 = 84A00200
```

(Exact hex values are illustrative; decoding rules matter, not literal values.)

---

## 5. Decoded Connections

| Gene | Source | Destination | Weight |
|------|--------|-------------|--------|
| G1   | CONST  | ADD         | +1.0   |
| G2   | ADD    | DELAY1      | +1.0   |
| G3   | DELAY1 | MUL         | +1.0   |
| G4   | MUL    | ADD         | +1.0   |

This forms a closed feedback loop.

---

## 6. Signal Flow Graph

```
CONST ──▶ ADD ──▶ DELAY ──▶ MUL ──▶ ADD
▲                                   │
└───────────────────────────────────┘
```

No semantics. Only numeric signal flow.

---

## 7. Execution Model (Per Tick)

### Phase 1 — Clear Accumulators

* All node input accumulators set to `0`

---

### Phase 2 — Propagate Connections

For each genome entry:

```
signal = sourceOutput * weight
accumulator[destination] += signal
```

Invalid connections (e.g. Sensory → Sensory) are ignored for propagation
but still count toward structural complexity and decay.

---

### Phase 3 — Node Evaluation

Nodes compute outputs from current accumulators:

* **ADD**: sum of inputs
* **MUL**: product of two strongest inputs
* **CLAMP**: clamp to `[-1, +1]`
* **THRESH**: step function at `0`
* **DELAY1**:
    * output = stored memory
    * memory = current input

---

### Phase 4 — Buffer Swap

* Computed outputs become visible next tick
* DELAY memory persists

---

## 8. Initial Conditions

* All node outputs = 0
* All DELAY memory = 0

CONST outputs `1.0` regardless of inputs.

---

## 9. Activity & Cost Semantics

* Activity occurs if **any connection propagates a non-zero signal**
* Activity cost ∝ number of evaluated connections
* Node evaluation cost may apply
* If no activity occurs, activity cost is zero
* Structural decay always applies

Dormancy is defined as **absence of signal flow**, not absence of structure.

---

## 10. Emergent Behavior

This genome can produce:

* amplification
* oscillation
* memory-dependent dynamics
* eventual collapse under structural decay

No behavior is encoded explicitly.

---

## 11. Invariants Enforced

* Genome encodes **connections only**
* Node substrate is fixed
* Memory exists only in DELAY nodes
* No instantaneous feedback
* No semantic meaning in the brain
* Complexity scales with genome length

---

## 12. Contract Status

This vertical slice is **binding for Kernel v0.1**.

Any implementation failing to reproduce these dynamics is incorrect.

---