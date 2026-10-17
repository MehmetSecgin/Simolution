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
