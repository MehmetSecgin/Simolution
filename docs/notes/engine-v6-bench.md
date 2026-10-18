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

W=64 is a single chunk, so it never splits. On the larger worlds 4 threads give only 3 to 7 percent. A per-phase profile of big500 at `--threads 1` taken during the B1 review put `DynamicsObserver.observe` at 55 of 64 ms per tick (about 86%), so the harness alone capped the speed-up; the phases split here were a small share of the tick. B5 removed that cost (next section).

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

## v6 (after)

Final v6 engine, harness included (observer and time series every tick, state digest once at the end, no `--out`). One `./gradlew bench --args="--threads N"` run per thread count, nothing reused from the tables above. Machine: Apple M2 Pro, 12 cores (8 performance + 4 efficiency), OpenJDK 26.0.1+8, `-Xmx4g`. B2 to B4 changed the dynamics, so a v6 row and a v5 row are not one identical workload.

| config | threads | ticks | wall ms | ticks/s | heap MB |
|---|---|---|---|---|---|
| small (W=64, 200 founders) | 1 | 2000 | 166 | 12048 | 11 |
| small (W=64, 200 founders) | 4 | 2000 | 167 | 11976 | 33 |
| dense200 (W=200, 4000 founders) | 1 | 3000 | 4766 | 629 | 73 |
| dense200 (W=200, 4000 founders) | 4 | 3000 | 3369 | 890 | 136 |
| big500 (W=500, 20000 founders) | 1 | 1000 | 11033 | 90 | 781 |
| big500 (W=500, 20000 founders) | 4 | 1000 | 5376 | 186 | 391 |

W=64 is a single chunk, so it never splits. The 781 MB heap reading of big500 at 1 thread is uncollected garbage (the reading is taken without a forced GC); the same run at 4 threads and the B5 run read 391 to 395 MB.

### Before / after against v5

| config | v5 ticks/s (1 thread) | v6 ticks/s (1 thread) | factor | v6 ticks/s (4 threads) | factor over v5 |
|---|---|---|---|---|---|
| small | 1088 | 12048 | 11.1x | 11976 | 11.0x |
| dense200 | 73 | 629 | 8.6x | 890 | 12.2x |
| big500 | 12 | 90 | 7.5x | 186 | 15.5x |

### Stopping rule (design §10)

| Rule | Requirement | Result | Verdict |
|---|---|---|---|
| 1 | `dense200`, 1 thread, at least 5x v5's median (73 ticks/s) | 629 ticks/s, 8.6x | met |
| 2 | `big500`, 4 threads at least 2.5x over 1 thread | 186 / 90 = 2.07x (needs 225 ticks/s at 4 threads) | **not met**, short by 0.43x (17%) |
| 3a | no avoidable hotspot above 20% of tick time | largest inclusive share is `ReproductionPhase.run` at 19.4% (`mutate` 15.5%, of which the length dry run `childLength` 11.6%; `freeMooreNeighbour` 7.8%); largest self share is `ReproductionPhase.mutate` at 13.0% | met, narrowly |
| 3b | harness below 10% | 8.0% of samples on `dense200` (`Kernel.snapshot` 4.1%, `DynamicsObserver.observe` 1.8%, `TimeSeriesReport.sample` 1.1%, the rest start-up and the final digest) | met |
| 3c | W=1000, 50000 founders, 100 ticks inside `-Xmx4g` | ran in 2.8 s at `--threads 4`, peak resident set 1.7 GB, peak population 417079 | met |

Rule 2 failed. v6 is not claimed to meet the stopping rule anywhere. The 2.07x factor is the same one the B5 table showed (2.10x, 90 to 189 ticks/s).

Why rule 2 fails, from a JFR profile of `big500` at `--threads 1` (1036 samples): the serial parts of the tick are `ReproductionPhase.finish` 9.4%, `MovementPhase.finish` 3.1%, `Kernel.snapshot` 4.2%, and the observer and time series about 4%, together about 21% of the run (the swap and the partial merges come on top). Amdahl's law with a 21% serial share gives 1/(0.21 + 0.79/4) = 2.5x at 4 threads, so the measured 2.07x also pays barrier waits and the efficiency cores of the M2 Pro (the 4 threads are not guaranteed performance cores). Closing the gap needs a smaller serial share: the first candidates are the serial claim resolves (cell-winner pass and slot assignment in `ReproductionPhase.finish`, `MovementPhase.finish`) and a cheaper `snapshot()`. That is a profile-driven follow-up, not part of this milestone.

### JFR profile, `dense200`, 1 thread

Command (recording in `build/`, not in the repo): `java -Xmx4g -XX:StartFlightRecording=filename=build/dense200.jfr,settings=profile -cp <runtime classpath> com.simolution.Main --units 4000 --ticks 3000 --seed 42 --world 200 --threads 1`, 438 execution samples, 5.0 s wall. Shares of samples, inclusive unless marked self:

| Share | Where |
|---|---|
| 92.0% | `Kernel.tick` (everything below) |
| 19.4% | `ReproductionPhase.run` (propose): `mutate` for the length dry run 11.6%, `freeMooreNeighbour` 7.8% (self) |
| 13.2% | `ReproductionPhase.finish` (resolve, install): `mutate` about 4%, `finishChild` (`resetForBirth`) 3.0%, `SlotCompiler.compile` 2.5% |
| 11.0% | `DiffusionPhase.run` |
| 10.0% | `PropagatePhase.run` |
| 10.0% | `EvaluatePhase.run` (`evaluateUnit` 4.6%, `freezeDeadUnit` 4.1%) |
| 5.3% | `WorldState.die` |
| 5.0% | `MovementPhase.run`; `MovementPhase.finish` 2.3% |
| 4.1% | `Kernel.snapshot`, all of it in `Chunks.orderedSum` |
| 3.4% | `DecayPhase.run` |
| about 9.6% | the other tick phases (`cost` 2.7%, `growth` 2.5%, `inflow` 2.3%, `intake` 2.1%) |
| 8.0% | harness outside `Kernel.tick`, see 3b |

Self-time hotspots: `ReproductionPhase.mutate` 13.0%, `DiffusionPhase.run` 11.0%, `PropagatePhase.run` 10.0%, `ReproductionPhase.freeMooreNeighbour` 7.8%, `ParallelRunner.runInline` 7.1%. Nothing is above 20%. Reproduction as a whole (propose plus resolve, 32.6%) is the largest cost centre, and the dry-run length computation in the propose step is the one piece of it that does the same work twice (once in `run` for the build cost, once in `finish` to write the genome).

## after parallel claims

Changes ([ADR 0046](../decisions/0046-parallel-claim-resolution.md)): movement and reproduction resolve in parallel passes (winner by atomic minimum, child slots by prefix over per-chunk counts), snapshot totals on demand, and the child-length dry run skipped for parents above the cost ceiling. `runs/baseline.txt` is byte-identical at threads 1, 3 and 4 (digest `78644e65e8086e45`).

Same machine and command as "v6 (after)". Three rounds, each one `./gradlew bench` per thread count, run back to back; each cell is that bench's median of 3 timed runs.

| config | threads | round 1 ticks/s | round 2 | round 3 |
|---|---|---|---|---|
| dense200 | 1 | 653 | 658 | 676 |
| dense200 | 3 | | 1005 | 1031 |
| dense200 | 4 | 1012 | 1031 | 1022 |
| big500 | 1 | 91 | 91 | 96 |
| big500 | 3 | | 217 | 209 |
| big500 | 4 | 237 | 241 | 257 |

| Rule 2, big500 | before | round 1 | round 2 | round 3 |
|---|---|---|---|---|
| 4 threads over 1 | 2.07x (186 / 90) | 2.60x | 2.65x | 2.68x |

A fresh run of the unchanged v6 code just before these rounds gave 96 and 183 ticks/s (1.91x), so the gain is not machine drift. The worst cross-round pairing (237 at 4 threads over 96 at 1) is 2.47x; the one-thread figure moves between 91 and 96 run to run. At 3 threads (the CX33 default) big500 reaches 2.2 to 2.4x.

**Verdict: rule 2 met** (2.60x to 2.68x per round, 2.5x required).

### JFR profile, `big500`, 1 thread, after

1050 samples. The serial share is now the harness plus O(chunks) steps: `TimeSeriesReport.sample` 4.7% (it samples every tick of a 1000-tick run, a full pass over energy plus the reservoir sum), `DynamicsObserver.observe` 1.4%, the final digest 1.0%. The claim work moved into the parallel part: `ReproductionPhase.run` 13.6% (the length dry run `childLength` 12.4%), `ReproductionPhase.resolve` 9.5% (`install` 7.5%, `judge` 2.0%), `MovementPhase.run` 7.5%, `MovementPhase.resolve` 2.8%, `CellClaim.claim` 7.2% (one random `cellWinner` access per proposal, mostly a cache miss). `Kernel.snapshot` no longer shows up.

The ceiling skip rarely fires on `big500`: most dividers sit just above their own build cost, so the dry run still runs for them. Removing it byte-identically needs a per-parent genome scratch (`maxGenes` ints per slot); geometric sampling of indel draws would remove it but changes the mutation stream (a contract change). Both are left out.

## CX33 sanity run

Not done yet: this session had no access to the Hetzner CX33. Command to run on it, from a checkout of this branch, then add the rows here:

```sh
./gradlew bench --args="--threads 3"
```

Shared vCPUs make timings noisy, so these rows are a sanity check and the table above is the reference.

## Observatory throughput

The Observatory runs one run at a time on all 12 cores with every sidecar on: frames every tick, events, births, catalog, metrics and checkpoints every 500 ticks. The design's check compares it with the CLI writing the same sidecars (`--out <dir> --map-frames 0 --checkpoint-every 500`, all cores). CLI: tick-loop wall clock from the console, median of 3. Observatory: ticks per second between the first progress sample and the end, polled over the API, after one warm-up run; all runs give the same final digest. Seed 42, `examples/baseline.json` with the world side and max genes of the bench config.

| config | `./gradlew bench --threads 12` (no sidecars) | CLI, same sidecars | Observatory | Observatory / CLI |
|---|---|---|---|---|
| small (W=64, 200 founders, 2000 ticks) | 14084 | 2833 | 6813 | 240% |
| dense200 (W=200, 4000 founders, 3000 ticks) | 837 | 178 | 176 | 99% |

The check (at least 90% of the CLI with the same sidecars) is met. On `small` the long-lived server's warm JIT beats a fresh CLI JVM. Against the bare bench the sidecars cost most of the time: on `dense200` the engine alone takes 3.7 s, events, births, catalog, metrics and checkpoints bring it to 11.8 s (`events.jsonl` alone is 231 MB for 3000 ticks), and every-tick frames to 16.9 s.
