# Kernel v0.1 — Caching & Precomputation Specification

## Purpose

This document defines **what may and may not be cached or precomputed** in Kernel v0.1.

The goal is to:
* minimize per-tick computational cost
* preserve determinism
* keep genome mutation semantics clear
* avoid premature graph specialization

This specification applies **only to Kernel v0.1**.

---

## 1. Fundamental Rule

> **Any value that depends only on genome structure and fixed kernel configuration  
> MAY be precomputed and cached.**

> **Any value that depends on runtime state, signal values, memory, RNG state,  
> or environment MUST NOT be cached.**

This rule is absolute.

---

## 2. Cacheable Data (Allowed)

The following items MAY be precomputed and cached.

### 2.1 Gene Decoding

Raw 32-bit genome entries MAY be decoded into a cached representation.

Cached fields MAY include:
* source node local index
* destination node local index
* absolute source index (unit offset applied)
* absolute destination index (unit offset applied)
* scaled connection weight (double)
* validity flag

No bit-level decoding SHALL occur during the tick loop.

---

### 2.2 Weight Scaling

The mapping from signed 16-bit integer to bounded weight range:

```
weight = int16 * (MAX_WEIGHT / 32768.0)
```

MAY be computed once during genome compilation and reused.

No clamping SHALL occur during runtime execution.

---

### 2.3 Connection Validity

Connection validity rules (e.g. Sensory → Sensory is invalid) MAY be evaluated
once during genome compilation.

Invalid connections:
* MUST NOT propagate signals
* MAY be omitted entirely from cached connection lists
* MUST still contribute to structural complexity if tracked

---

### 2.4 Absolute Node Indices

Per-unit node offsets MAY be applied during cache construction so that cached
connections store **absolute indices** into flat state arrays.

Runtime code MUST NOT recompute absolute indices.

---

### 2.5 Node Role Ranges

Fixed node layout ranges MAY be cached as constants:

* sensory node index range
* internal node index range
* action node index range
* delay node index list

These values are invariant for Kernel v0.1.

---

## 3. Non-Cacheable Data (Forbidden)

The following MUST NOT be cached or precomputed.

### 3.1 Runtime Signal Values

* node outputs
* accumulators
* delay memory
* action outputs

These are per-tick dynamic state.

---

### 3.2 Activity State

* dormancy detection
* activity flags
* per-tick cost calculations

These depend on runtime signal flow and MUST be computed during execution.

---

### 3.3 Execution Order Beyond Fixed Phases

The kernel execution phases are fixed:

1. clear accumulators
2. propagate connections
3. evaluate nodes
4. swap buffers

No graph-based reordering, topological sorting, or cycle analysis
SHALL be cached in Kernel v0.1.

Cycles are legal and intentional.

---

### 3.4 Incoming / Outgoing Connection Graphs

Per-node adjacency lists (e.g. “all incoming connections for node X”)
MUST NOT be cached in Kernel v0.1.

Reason:
* increases mutation complexity
* complicates genome editing
* unnecessary for expected genome sizes

---

## 4. Cache Invalidation Rules

All cached data MUST be invalidated and rebuilt if and only if:

* the unit’s genome changes
* a unit is created or destroyed
* kernel node substrate configuration changes

Cached data MUST remain valid across ticks if the genome is unchanged.

---

## 5. Flat Memory Requirement

All cached data SHALL be stored in flat arrays.

Units SHALL reference cached data using:
* offsets
* lengths

No per-unit dynamic allocation SHALL occur during ticks.

---

## 6. Performance Contract

> **No code inside the kernel tick loop SHALL perform:**
* bit extraction on genome data
* modulo operations for node IDs
* weight scaling math
* connection validity checks

Violation of this rule is considered a kernel defect.

---

## 7. Future Extensions (Non-Binding)

Later kernel versions MAY introduce:
* node-centric connection lists
* SIMD-friendly layouts
* JIT-like compilation strategies

Such extensions MUST NOT violate the rules in this document for Kernel v0.1.

---

## 8. Contract Status

This document is **binding for Kernel v0.1**.

Any implementation that violates these caching rules is non-compliant.
