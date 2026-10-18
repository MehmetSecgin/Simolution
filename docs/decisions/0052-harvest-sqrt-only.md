# 0052: Harvest scales with sqrt(mass), not a configurable exponent

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Contract:** [v5](../contract/contract-v5.md)

**Context.** Contract-v5 asserts one surface law: harvest ∝ mass^α with α = 0.5. ADR 0028 made α a config field (`harvestMassExponent`), and `IntakePhase` kept two loops: `sqrt` for 0.5 and `StrictMath.pow` for anything else. No run or example ever used another value, so the second loop and the field were dead weight.

**Decision.** `Harvest.massExponent`, the `harvestMassExponent` JSON key and the `pow` loop are gone. Intake multiplies the drive by `StrictMath.sqrt(mass)` in one loop. The engine version moves from `v6` to `v6.1`, because a v6 checkpoint or config carrying the key no longer loads.

**Outputs.** The only line of `runs/baseline.txt` that moves is `kernel: v6` to `kernel: v6.1`; `state-digest: 78644e65e8086e45` is unchanged (the default already took the `sqrt` path, and `Math.sqrt` and `StrictMath.sqrt` are both correctly rounded). In the golden outputs the same header line moves, and `run.json` carries `engine: v6.1` and a new `runId` because checkpoints embed the version and the config.

**Rejected.** Keeping α configurable for experiments: changing the surface law is a contract change, and then it gets its own contract and ADR.
