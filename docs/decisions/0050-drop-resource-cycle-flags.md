# 0050: Drop the --resource-cycle flags

**Status:** accepted · **Date:** 2026-10-18 · **Milestone:** engine v6 · **Contracts:** [v6](../contract/contract-v6.md), [v7](../contract/contract-v7.md)

**Context.** `--resource-cycle`, `--cycle-period`, `--cycle-radius` and `--cycle-peak` (contract-v6) built one central pulsing disk. Since contract-v7 the same disk is one `--inflow` source, so the four flags were a second parser path, a mutual-exclusion check, two defaults and `InflowConfig.cyclic` for nothing the DSL cannot say.

**Decision.** The four flags, `InflowConfig.cyclic` and `ArgCursor.doubleValue` are gone. The v6 disk is `--inflow "disk r=R peak=X period=N"`: centre 0.5/0.5, baseline 0, combine max, exactly the config the flags built. The old defaults (radius W/5, period 2000, peak 6) are not carried over; the DSL defaults apply when a key is left out.

**Outputs.** `runs/baseline.txt` is unchanged (it never used the flags). The golden `cycle` case now runs `--inflow "disk r=10 peak=5 period=200"` and its report, sidecars and digest are byte-identical to the old `--resource-cycle --cycle-period 200 --cycle-radius 10 --cycle-peak 5` run. The `errors/cycle-and-inflow` case is gone with the check it exercised.

**Rejected.** Keeping the flags as documented sugar: two spellings of one world, and the shorter one hides the defaults it picks.
