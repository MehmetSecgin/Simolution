# 0042: The resource field becomes a chemical table

**Status:** accepted · **Date:** 2026-10-17 · **Milestone:** engine v6 · **Design:** [engine-v6-design](../notes/engine-v6-design.md) §5.5

## Context

The owner's next features (several resources, secretions, corpses that release what the body held) all need more than one diffusing field. v5 had one field wired through the kernel by name.

## Decision

The field is chemical 0 of a table of `ChemicalSpec` (name, inflow, capacity, initial, diffusion rate, decay rate, decay target). Inflow, diffusion and decay are per-cell phases that loop over chemicals; arrays are chemical-major (`[k * cells + cell]`) so each sweep is contiguous. `unit x chemical` stores are declared but unused. Exactly one chemical is configured, with the v8 constants. Energy and mass stay dedicated unit fields. `decayTarget` is `-1` (sink) today; other values are reserved for rotting into another chemical.

## Why

Adding a chemical becomes config. Secretion, multi-resource harvest and corpse release become small transfer phases later, each with its own contract. One chemical with today's constants reproduces the v8 biology.

## Rejected

- Building multi-chemical sensors, harvest and secretion now: new biology, out of this milestone.
- Making energy and mass chemicals: a later contract question.
