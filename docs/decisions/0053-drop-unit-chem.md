# 0053: Drop the unused per-unit chemical store

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **ADR:** amends [0043](0043-per-run-config-versioned-checkpoints-seek-branch.md), [0048](0048-no-test-only-production-code.md)

**Context.** Engine v6 declared `WorldState.unitChem` (`Dim.UNIT_CHEM`, unit × chemical) for a future feature. No phase reads or writes it, yet it is `SAVED`: it costs `chemCount · W²` doubles of memory, every checkpoint stores it, and the state digest folds it. ADR 0048 kept it only because dropping it moves output bytes, which needed the owner's call; the owner made it.

**Decision.** `unitChem` and `Dim.UNIT_CHEM` are gone. `CheckpointCodec.FORMAT_VERSION` goes from 2 to 3, so a format-2 checkpoint is refused with the format error instead of failing on a field count. A feature that needs per-unit chemical stores declares the field with its own contract.

**Outputs.** The world does not change: every count, total and distribution in `runs/baseline.txt` is identical. Only `state-digest` moves, from `78644e65e8086e45` to `c0f052e9d43f300c`, because the digest folds every `SAVED` field and one all-zero field left. The same holds for the golden runs (`state-digest`, the `--digest-every-tick` lines, checkpoint bytes and `runId`). The engine version stays `v6.1`: ADR 0052 and this change land together, before any v6.1 result was published.

**Rejected.** Keeping the field out of the digest and checkpoints but in memory: an unread array still costs memory and still has to be explained.
