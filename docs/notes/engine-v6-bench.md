# Engine v6 benchmark

Command: `./gradlew bench --args="--threads 1"`. Each config runs once for JIT warm-up, then 3 timed runs; the row is the median by wall time. Seeds fixed (42), no `--out`, observer and time series run every tick as in a normal run. `--threads` is accepted and ignored in v5 (single-threaded engine).

## v5 (before)

v5, harness included.

Machine: Apple M2 Pro, 12 cores, OpenJDK 26.0.1+8 (`-Xmx4g`).

| config | ticks | wall ms | ticks/s | heap MB |
|---|---|---|---|---|
| small (W=64, 200 founders) | 2000 | 1838 | 1088 | 25 |
| dense200 (W=200, 4000 founders) | 3000 | 41087 | 73 | 333 |
| big500 (W=500, 20000 founders) | 1000 | 82197 | 12 | 523 |

Heap MB is `totalMemory() - freeMemory()` right after the run, without a forced GC, so it includes uncollected garbage.

## after A6

Flat `short`/`double` connection arrays, compile-time junk pruning, hoisted mutation hash, allocation-free births. Same machine and command as above.

| config | ticks | wall ms | ticks/s | heap MB |
|---|---|---|---|---|
| small (W=64, 200 founders) | 2000 | 1852 | 1079 | 11 |
| dense200 (W=200, 4000 founders) | 3000 | 38000 | 78 | 86 |
| big500 (W=500, 20000 founders) | 1000 | 74964 | 13 | 426 |

## after B1

`ParallelRunner` with fixed 4096-entry chunks; intake, inflow, cost, growth and decay now split across threads with per-chunk partial totals. Reproduction and movement are still serial, and the harness (observer, time series) runs on the caller every tick. Same machine (Apple M2 Pro, 12 cores: 8 performance + 4 efficiency) and command, `--threads 1` and `--threads 4`.

| config | threads | ticks | wall ms | ticks/s | heap MB |
|---|---|---|---|---|---|
| small (W=64, 200 founders) | 1 | 2000 | 1850 | 1081 | 11 |
| small (W=64, 200 founders) | 4 | 2000 | 1855 | 1078 | 22 |
| dense200 (W=200, 4000 founders) | 1 | 3000 | 36882 | 81 | 85 |
| dense200 (W=200, 4000 founders) | 4 | 3000 | 35925 | 83 | 152 |
| big500 (W=500, 20000 founders) | 1 | 1000 | 74129 | 13 | 447 |
| big500 (W=500, 20000 founders) | 4 | 1000 | 68941 | 14 | 428 |

W=64 is a single chunk, so it never splits. On the larger worlds 4 threads give only 3 to 7 percent: the time goes to the serial phases and the harness, not to the phases split here.

## after B5

`DynamicsObserver` no longer re-propagates the founders' connections or folds a per-tick hash chain: it sums `activeThisTick` and counts the living in one pass. The state digest runs once, at the end of the run. Movement and reproduction are the claim phases of B2 and B3 (their resolution is still serial); the world differs from the earlier tables because B2 to B4 changed the dynamics, so the rows are not a like-for-like comparison of one workload. Same machine and command, `--threads 1` and `--threads 4`.

| config | threads | ticks | wall ms | ticks/s | heap MB |
|---|---|---|---|---|---|
| small (W=64, 200 founders) | 1 | 2000 | 165 | 12121 | 20 |
| small (W=64, 200 founders) | 4 | 2000 | 167 | 11976 | 34 |
| dense200 (W=200, 4000 founders) | 1 | 3000 | 4746 | 632 | 71 |
| dense200 (W=200, 4000 founders) | 4 | 3000 | 3312 | 905 | 77 |
| big500 (W=500, 20000 founders) | 1 | 1000 | 11055 | 90 | 391 |
| big500 (W=500, 20000 founders) | 4 | 1000 | 5278 | 189 | 395 |

W=64 is a single chunk, so it never splits.
