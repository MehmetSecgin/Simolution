# 0045: Retire the microcontroller budget and the locked pipeline

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Design:** [engine-v6-design](../notes/engine-v6-design.md) §1, §3 · **Supersedes:** the performance doctrine and "tick pipeline is locked" in AGENTS.md; the per-array footprint rule of [0037](0037-float-signal-state.md)

## Context

Through v5 the kernel was built to stay small enough to embed in a game loop: microcontroller-class budgets, an ADR for any per-unit or per-connection footprint growth, and a pipeline that could only be appended to. The project's audience is now a researcher who reruns and trusts a simulation, on a multi-core machine.

## Decision

- The embedding budget is gone. Footprint stays linear in units and cells and constant in tick count, and a growth needs a note in the design or ADR, not a justification per array.
- Phases are a declared list that may be added, split, removed or reordered (ADR 0039). A reorder is a contract change if it alters the world.
- What stays: no allocation in the steady-state tick, flat primitive arrays, strict arithmetic, determinism for any thread count, the honesty doctrine.

## Why

The old limits blocked the work this milestone needs (threads, claim phases, chemicals) and bought nothing the new audience values. The invariants that protect results (determinism, conservation, honesty) are kept.

## Rejected

- Keeping the budget as a soft goal: it would keep arguing against every parallel scratch array.
- Dropping the no-allocation rule: GC pauses would show up in thread-scaling numbers.
