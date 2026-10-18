# 0059: A viable reference world for new biology

**Status:** accepted · **Date:** 2026-10-18 · **Note:** [viable-world](../notes/viable-world.md)

**Context.** Under `examples/baseline.json` with 100 founders, the population dies out by about tick 1,750. Any new biology tested there is measured against an extinction, so its effect cannot be seen.

**Decision.** `examples/viable.json`, run with 1,000 founders, is the reference world. It differs from the baseline config only in harvest capacity per connection (1.5 to 5.0) and in resource inflow (uniform 1.0 replaced by a 0.2 baseline with noise patches). It persists for 50,000 ticks on seeds 1 to 3, holds 36% to 46% of the cells, keeps breeding, and keeps 2 or more lineages in 2 of 3 seeds. `baseline.json` and `runs/baseline/report.txt` stay as they are: they are the regression check, not a living world.

**Why.** It reaches the criteria with the fewest changed settings, and the kernel stays untouched. The patches give lineages separate niches; without them every run ended as a monoculture.

**Rejected.**
- Harvest 8.0 with 100 founders: it died out on seed 3 in the cold start.
- Cheaper propagation or basal cost: it filled the world, so crowding decided everything.
- Uniform inflow with harvest 5.0: it persisted, but one lineage always took over.
