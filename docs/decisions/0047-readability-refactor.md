# 0047: Readability refactor

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Spec:** [readability-design](../notes/readability-design.md)

**Context.** About ten methods ran long, three types took 14 to 27 positional arguments, and `Main.main` held the whole run. Comments carried what names should.

**Decision.** Pure refactor, no output change. `EngineConfig` groups into nine domain records with flat JSON, and `v5Defaults` became `defaults`. The run splits into `RunConfig`, `Founders`, `SimulationRun`, `RunArtifacts` and small writers. The gene layout lives once in `GeneLayout`. Comments stay only for invariants a name cannot carry. Tests build worlds and genes through shared helpers in `src/test/java/com/simolution/support`.

**Why.** Every step kept `runs/baseline.txt`, the sidecars, the run directory and the checkpoint bytes identical, so each diff can be trusted.

**Rejected.**
- Merging the two energy-audit formulas (`MetricsWriter`, `DynamicsSummary`): they add in different orders, so a merge moves digits in the output.
- A test-only `EngineConfig` builder: `defaults` and the `with` copies already cover it, and a second way to build a config would drift.
- Deleting `LatestFrame`: nothing in production calls it yet, but the web app will.
