# Viable reference world

`examples/viable.json` is the world new biology is judged in. Run it with 1,000 founders:

```sh
./gradlew run --args="--config examples/viable.json --units 1000 --ticks 50000 --world 100 --out runs/viable"
```

## Criteria

On a 100×100 world, 50,000 ticks, seeds 1 to 3:

| Check | Pass |
|---|---|
| Persists | population never reaches 0 |
| Not saturated | after tick 5,000 the population stays between 10% and 90% of the cells |
| Still evolving | births continue in the last 10,000 ticks |
| Diverse (soft) | 2 or more founder lineages alive at the end in at least 2 of 3 seeds |

## Result

| Seed | Cells held after tick 5,000 | Births in the last 10,000 ticks | Lineages at the end | Mass per unit at the end |
|---|---|---|---|---|
| 1 | 43% to 46% | 500,261 | 3 | 0.39 |
| 2 | 37% to 46% | 356,469 | 2 | 0.84 |
| 3 | 36% to 45% | 480,409 | 1 | 0.71 |

Seed 1 ends at state digest `68ac021dd1f4dbb8` (engine v6.1).

## What changed from `baseline.json`

| Setting | baseline | viable |
|---|---|---|
| `harvestCapacityPerConnection` | 1.5 | 5.0 |
| resource inflow | uniform 1.0 | baseline 0.2, plus noise patches (seed 7, frequency 0.04, threshold 0.55, gain 3, peak 4), combined by MAX |
| founders (CLI `--units`) | 100 | 1,000 |
| seed | 42 | 1 |

## Why the baseline dies

A random 32-gene founder averages about 1.8 connections into `HARVEST`. At capacity 1.5 that is at most about 1.3 energy per tick, below an upkeep of about 3. The field fills to about 49 per cell, so food is not the limit. Founders split their energy through early reproduction and then starve; every birth happens in the first 250 ticks.

## How it was found

1. A screen at 5,000 ticks, seed 1, over harvest capacity {1.5, 3, 5, 8} × propagation cost {0.06, 0.02} × basal cost {0.5, 0.2} × founders {100, 1,000}. 13 of 32 passed. Cheap propagation with basal 0.2 filled the world. Every pass ended with one lineage.
2. The two passes that change the fewest settings, over 50,000 ticks × 3 seeds:
   - Harvest 5.0 with 1,000 founders passed all seeds, holding 34% to 81% of the cells with one lineage.
   - Harvest 8.0 with 100 founders died out on seed 3 during the cold start.
3. The same harvest 5.0 world with patchy inflow narrowed the population band to around 43% of the cells. It also kept 2 or more lineages in 2 of 3 seeds, the only variant that met the soft criterion.
