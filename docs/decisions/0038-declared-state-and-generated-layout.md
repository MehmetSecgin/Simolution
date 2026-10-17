# 0038: Declared state and a generated node layout

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Design:** [engine-v6-design](../notes/engine-v6-design.md) §5.1, §5.2

## Context

In v5 one new sensor or per-unit variable touched a dozen hand-synced places: `NodeLayout`, one line per node in `evaluateUnit`, `localName`, `live.html`, the Python tools, and for state the constructor, `finishChild`, `die`, `saveState`, `loadState` and a 23-argument `KernelSnapshot`.

## Decision

- Every state array is declared once in `WorldState.fields()` as a `FieldSpec`: name, dimension (unit, cell, unit x node, cell x chemical, gene, chunk, scalar), persistence (`SAVED`, `DERIVED`, `SCRATCH`), birth rule and death rule. Birth reset, death cleanup, checkpoint save and load, and `StateDigest` walk that list.
- The node layout is data: `NodeCatalog` lists sensors, internals and actions plus junk counts, and `NodeLayout` derives every offset, `TOTAL` and `TYPE_COUNT` from it. The engine writes `<base>.layout.json` beside every `--out` run, and the viewers read it.

## Why

Adding a field or a node becomes one declaration, and a field cannot be saved but not digested, or reset at birth in one place and not another. The checkpoint layout check compares the generated JSON, so a layout change is refused loudly.

## Rejected

- An `Organism` object graph: cache-hostile, and it kills the parallel model.
- A generic ECS dependency: too much machinery for one simulation.
- Generating Java sources at build time: a plain list read at start-up is enough, and debuggable.
