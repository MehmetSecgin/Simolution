# 0051: Chemical decay always goes to the sink

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Contract:** [v8](../contract/contract-v8.md)

**Context.** ADR 0042 gave each `ChemicalSpec` a `decayTarget`: `-1` sent the decayed share to the sink, a chemical index moved it into that chemical ("rotting"). No config, run or example ever used anything but `-1`, and contract-v8 only defines decay to the sink. The option cost a branch in `DecayPhase`, a self/range validation, a JSON key and two tests.

**Decision.** `decayTarget`, `ChemicalSpec.SINK`, the target branch in `DecayPhase` and its validation are gone. Every chemical decays to the sink at its own `decayRate`. Contract-v8 §1 says so.

**Outputs.** `runs/baseline.txt` is unchanged, digest included: the world is the same. Config JSON loses the `decayTarget` key (`examples/baseline.json` updated), and checkpoints embed the config JSON, so checkpoint bytes and the `runId` in `run.json` change; the golden `full` case moves for that reason only. A config or checkpoint that still carries `decayTarget` is refused with the usual unknown-key error.

**Rejected.** Keeping the target for a future decomposer chain: when corpses and decomposers get their contract, the transfer belongs there, designed with that law, not as a dormant knob.
