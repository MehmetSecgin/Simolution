# Kernel readability audit (runtime, phase, state, layout, logging)

Branch `kernel-v6-engine` @ `b4c95c1`. Paths are relative to `src/main/java/com/simolution/kernel/`.
Risk tags: **[safe]** = pure rename or extract, byte-identical, no allocation. **[bench]** = byte-identical but touches a hot loop, so run `Bench` before and after. **[format]** = changes checkpoint bytes or digest, so it is not a pure refactor.

---

## Ranked by reading payoff

### 1. `ReproductionPhase.finish` + `mutate`: the hardest code in the area **[safe]**

| Method | Lines | Problem |
|---|---|---|
| `finish()` | `phase/ReproductionPhase.java:118-179` (62) | Four jobs in one body: mark first claims, find a free slot, pay and split, copy and mutate the genome, append to the birth log. 9 hoisted locals before any logic. |
| `mutate()` | `:293-338` (46) + 32-line Javadoc | Three operators in one loop body. The `nextFlip(x, Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeepX), classBits)` expression appears 4 times (`:296`, `:298`, `:307`, `:311`). |
| `run()` | `:89-115` (27) | Borderline. Reads fine once `canAffordChild` is extracted. |

Proposed `finish()` shape, read top to bottom:
1. `markFirstClaims(state.birthCell)` (shared with movement, see #5)
2. loop over winners: `int child = nextFreeSlot(cursor)`, then `if (child == slots) continue`
3. `sink += payBuildCost(parent)` (energy -= cost, damage += cost, returns cost: see #6 `spend`)
4. `splitBody(parent)`: halves energy and mass, returns nothing; child reads the halves back from the parent
5. `inheritGenome(parent, child)`: arraycopy, `GenomeMutation.mutate`, `SlotCompiler.compile`
6. `installChild(child, cell, parent)` (today `finishChild`, `:240`; rename)
7. `recordBirth(births++, parent, child, cell)`: birthsTotal, maxGeneration, three birth-log arrays

Proposed `mutate()` split. Determinism stays easy because only point mutation consumes a variable number of draws. Deletion consumes exactly `count` draws, duplication exactly `survivors`, so no shared mutable counter and no allocation are needed:
```
int op = flipPointMutations(genes, base, count, key);           // returns next op
int survivors = deleteGenes(genes, base, count, key, op);       // uses op .. op+count-1
return duplicateGenes(genes, base, survivors, key, op + count);
```
- Add `private long drawGap(long key, int op, double logKeep)` to cut the 4 repeated draw expressions.
- Move `mutate`, `childLength`, `nextFlip`, `geneBit`, `logKeep*`, `indelRate*` out to `genome/GenomeMutation` (one final class built from `EngineConfig`). Reproduction then reads as fission only. Coordinate with the genome auditor.
- After the split, each step method carries 3 to 5 lines of its own doc. The class doc loses paragraph 2 (`:20-24`). The op-counter determinism paragraph (`:285-291`) stays: it is a real invariant.

### 2. Node index arithmetic is copy-pasted ~15 times, and inconsistently **[safe]**

Today's pattern: `unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.GROW * NodeLayout.Action.INSTANCES_PER_TYPE)`.

| Site | Note |
|---|---|
| `phase/GrowthPhase.java:65-66`, `phase/IntakePhase.java:123-124`, `phase/ReproductionPhase.java:191-192` | full form |
| `phase/EvaluatePhase.java:131,143,150,157,161,165,169` | 7 copies for internals, sensors, actions |
| `phase/MovementPhase.java:78-82` | **drops** `INSTANCES_PER_TYPE` |
| `logging/ConsoleTableLogger.java:30-37`, `genome/SlotCompiler` `MUL_LOCAL`/`HARVEST_LOCAL` | **drop** it too |

`INSTANCES_PER_TYPE` is `1` everywhere (`layout/NodeLayout.java:34,52,73`), and half the code already ignores it. It is dead generality, and a value other than 1 would already be broken.

Fix:
1. Delete `INSTANCES_PER_TYPE` and `COUNT` (COUNT equals TYPE_COUNT). Update `genome/GeneDecoder`, `SeedTest` and `GeneDecoderTest` with no shim.
2. Delete the unused `SENSOR_JUNK_COUNT`, `INTERNAL_JUNK_COUNT`, `ACTION_JUNK_COUNT` (`NodeLayout.java:17-19`, zero readers).
3. Add the local node index constants to `NodeLayout`, following the `SlotCompiler.MUL_LOCAL` pattern: `HARVEST_NODE`, `GROW_NODE`, `REPRODUCE_NODE`, `MOVE_N_NODE`..., `ADD_NODE`, `MUL_NODE`, `CLAMP_NODE`, `DELAY_NODE`, `THRESH_NODE`.
4. Add `WorldState.actionOutput(int unit, int actionNode)` = `outputsPrev[unit * TOTAL + actionNode]`. Intake, Growth, Reproduction and Movement then read `state.actionOutput(unit, GROW_NODE)`.

### 3. `Kernel` constructor: 75 lines, the pipeline hidden in a `List.of` **[safe]**

`runtime/Kernel.java:67-141`. It mixes validation, founder install, compile-all and phase wiring. Founder-cell validation (`:111-119`) runs mid-mutation, after `arraycopy` has already written.

Proposed shape:
1. `requireFoundersFit(initialGenomes, founderCells, maxGenes, cellCount)`: all four checks, before any write
2. `state = new WorldState(config)`; `creditInitialEndowment(founderCount)`
3. `for founder: installFounder(slot, genome, cell)`
4. `compileAllSlots()`
5. `phases = tickPipeline(state, config)`: a private static method whose body is the 12-entry list. That list *is* the domain story (sense, think, eat, pay, grow, divide, move, spread, decay), so it deserves its own name.
6. `runner`, `digest`

Also:
- The Javadoc at `:41-66` describes `WorldState` and `SlotCompiler` layout, not the constructor. Move the slot-region paragraph to `WorldState` and the sum-then-mul paragraph to `SlotCompiler` (it is already there). Keep only the `threads` sentence.
- The `phases()` Javadoc (`:143-149`) re-lists the order. `tickPipeline` makes that list redundant, so delete it.
- `Kernel.unitCount` (`:36,84`) duplicates `state.unitCount`. Delete it.
- `maxUnits` / `seededCount` / `unitCount` are three names for two things. Use `cellCount` and `founderCount`.

### 4. `EvaluatePhase.evaluateUnit`: 57 lines, 10 hoisted arrays **[bench]**

`phase/EvaluatePhase.java:116-172`. Proposed shape:
1. `evaluateSensors(unit, base)`, with `chemicalHere(unit, s)` replacing the `// a unit senses only the cell...` comment (`:135`)
2. `outputsNext[base+ADD_NODE] = accumulators[base+ADD_NODE]`
3. `outputsNext[base+MUL_NODE] = mulOutput(unit)`, carrying the MUL in-degree rule (`:146-149`) as its Javadoc. That rule is genuine (ADR 0005), so keep it.
4. `clampOutput(base)`, `delayOutput(base)` (read memory, then store the accumulator), `thresholdOutput(base)`
5. `passThroughActions(base)`

With #2's constants, each internal is one line. The extraction is byte-identical. Bench only because it is the hottest per-unit method.

### 5. Movement + reproduction: duplicated claim resolution and torus math **[safe]**

| Duplicate | Sites |
|---|---|
| first-claim-wins marking via `cellWinner` | `phase/MovementPhase.java:123-128`, `phase/ReproductionPhase.java:129-134` |
| toroidal wrap `(((v % w) + w) % w)` | `MovementPhase.java:99-100`, `ReproductionPhase.java:216,221`, `DiffusionPhase.java:58-61` |
| move cost `moveCostBase + moveCostPerMass * mass[unit]` | `MovementPhase.java:105` and `:135` |

Fixes:
- `phase/Claims.markFirstClaims(int[] targetBySlot, int[] cellWinner)` plus `Claims.isWinner(slot, target, cellWinner)`. The "reset only touched entries" rule (`MovementPhase.java:26-27`, `ReproductionPhase.java:30-31`) is then documented once.
- `layout/Torus` (or in `phase/`): `wrap(v, w)`, `step(cell, dx, dy, w)`, `up/down/left/right`. Integer only, so determinism holds.
- `MovementPhase.run` (`:63-110`, 48 lines) becomes: `int dx = axisStep(eastWestDrive(unit))`, `int dy = -axisStep(northSouthDrive(unit))`, `if still continue`, `target = Torus.step(position[unit], dx, dy, w)`, `if occupied or !canAfford(unit) continue`. `axisStep(drive)` returns +1, -1 or 0 against the deadzone. N = -y then lives in one place (`-axisStep`) instead of an inverted `if` (`:89-94`).
- `MovementPhase.finish` (`:113-146`, 34 lines) becomes `markFirstClaims` plus a loop calling `sink += moveTo(unit, target)`.
- Rename the single letters: `h`/`v`/`theta`/`w` (`:71-82`) to `eastWest`/`northSouth`/`deadzone`/`width`, and `DiffusionPhase` `d` (`:53`) to `rate`.

### 6. The energy economy is spelled out by hand in 5 phases **[safe]**

| Pattern | Sites | Fix |
|---|---|---|
| `energy -= x; damage += x; sink += x` | `CostPhase.java:90-92`, `MovementPhase.java:139-141`, `ReproductionPhase.java:152-154` | `WorldState.spend(unit, amount)` returns the amount for the caller's sink. "Dissipation wears the body" becomes one named rule. |
| saturating drive `cap / (1 + half / output)` | `IntakePhase.java:132`, `GrowthPhase.java:71-72` | `Saturation.drive(output, ceiling, halfSaturation)`, with the overflow-rearrangement comment (`IntakePhase.java:130-131`, a real numeric trick) living there once |
| `!Double.isFinite(output) \|\| output <= 0.0` | `IntakePhase.java:126`, `GrowthPhase.java:68`, `ReproductionPhase.java:194` | fold it into `Saturation.drive` (returns 0), and add `isFiring(output)` for the reproduce trigger |
| `sinkPartial[chunk] += sink` + `finish(){ mergePartials(); }` | Cost, Growth, Decay, Inflow, Movement, Reproduction | `state.addSink(chunk, x)`; acceptable as is, low priority |

`CostPhase.run` (`:74-98`) then reads `charge = Math.min(metabolicBill(unit), energy[unit])`, with `metabolicBill` = basal + activity + maintenance + aging.

### 7. Names that need a comment to be read

| Current | Where | Problem | Better |
|---|---|---|---|
| `outputsPrev` / `outputsNext` | `state/WorldState.java:46-47` | After the swap, `outputsPrev` holds *this* tick's outputs. Three Javadocs have to say "outputsPrev, the just-swapped current tick" (`IntakePhase.java:14`, `MovementPhase.java:9-10`, `ReproductionPhase` implicit). | `outputs` (last completed evaluation) / `nextOutputs` (being written) |
| `unitCount` used as a cell count | `WorldState.java:31`; `extent()` of Inflow, Diffusion, Decay; `cells = state.unitCount` (`InflowPhase.java:58`, `DiffusionPhase.java:50`, `DecayPhase.java:58`) | Readers must recall that slots = cells = W² | `slotCount` + `cellCount` (both final, same value) |
| `activeThisTick` | `WorldState.java:64` | It is the count of nonzero propagations that cost charges for | `propagationCount` |
| `harvestConnCount` | `WorldState.java:58` | The docs call these "transporters" | `transporterCount` |
| `mulTop1` / `mulTop2` | `WorldState.java:62-63` | | `mulStrongest` / `mulSecondStrongest` |
| `moveIntent` | `WorldState.java:65` | It holds a target cell | `moveTarget` |
| `birthCell` / `birthCost` | `WorldState.java:67-68` | Per-parent claims, easy to confuse with the birth log `birthChildCell` | `claimedBirthCell` / `claimedBuildCost` |
| `KernelSnapshot.reservoir` | `runtime/KernelSnapshot.java:13`, `Kernel.java:271` | It is the sum of *all* chemicals in `cellChem`; "reservoir" is the v1 global pool, long gone | `chemicalTotal` |
| `KernelSnapshot.resourceField` | `KernelSnapshot.java:14` | It is all chemicals, not "the resource" | `cellChem` |
| `prefix` | `ReproductionPhase.java:106,293`, `Noise.mutationPrefix` | It is the per-birth mutation key | `mutationKey`; `Noise.mutationUniformFromPrefix` → `Noise.mutationDraw(key, op)` |
| `weight` / `struct` (long positions) | `ReproductionPhase.java:296-311` | They read as gene fields, not flip positions | `nextWeightFlip` / `nextStructFlip`; `classBits` → `bitsPerClass` |
| `finishChild` | `ReproductionPhase.java:240` | | `installChild` |
| `commit` | `GrowthPhase.java:71` | | `invested` |
| `sqrtHarvest` | `IntakePhase.java:27` | | `squareRootSurfaceLaw` (or drop it, see #10) |
| `seededCount`, `maxUnits` | `Kernel.java:72-73` | | `founderCount`, `cellCount` |
| `Noise.sample` | `runtime/Noise.java:48` | Only the RAND sensor uses it | `randSensor` |
| `zeroStride(spec)` called for its throw | `WorldState.java:155,159` | The return value is discarded; it is really a validation | `requireZeroablePerSlot(spec)` |

### 8. `WorldState` constructor (78 lines) + declaration hygiene **[safe]**

`state/WorldState.java:88-165`. A flat list of 37 `new` calls, plus the initial chemical fill, plus the policy indexing.
- Split it into `fillInitialChemicals(config)` (returns the total) and `indexSlotPolicies()` (builds `resetOnBirth`/`zeroOnDeath`). Order the allocations, and the field declarations at `:34-73`, by the same groups `fields()` uses: saved body/genome/space, derived wiring, scratch. The 45-line `fields()` table (`:183-227`) is fine as a table. Static imports of `Persist.*`/`OnBirth.*` would remove the 5 alias locals (`:184-188`).
- **Contradiction:** the `FieldSpec` Javadoc says "a spec must not be kept across ticks" (`state/FieldSpec.java:7-9`), but `WorldState.resetOnBirth`/`zeroOnDeath` keep specs forever (`:85-86`). It works only because both swap buffers are listed. Restate the rule as "a spec binds the array, not the name; list both buffers of a swapped pair".
- `Dim.SCALAR` (`FieldSpec.java:19`) has no users. Delete it.
- `unitChem` is saved and never read (`WorldState.java:44`); it is provisioned for the v6 plan. Removing it is **[format]** (checkpoint and digest change), so leave it unless that plan is dropped.
- `die()` (`:257`) is a law (mass dissipates to the sink) living in the state class whose doc says "the kernel holds the laws". This is acceptable, but `spend` (#6) and `die` together are the "body economy". Consider one small `Bodies` helper class if the economy grows.

### 9. `PropagatePhase.run`: 48 lines, 13 hoisted arrays **[bench]**

`phase/PropagatePhase.java:36-83`. Proposed shape: `if dead { propagationCount=0; continue }`, then `count = propagateSums(unit, offset) + propagateMuls(unit, offset)`. In `propagateMuls`, the top-2 update (`:70-75`) becomes `keepStrongestTwo(unit, signal)`. Each helper hoists only the arrays it touches.

### 10. `IntakePhase.run`: the same loop twice **[bench]**

`phase/IntakePhase.java:53-89`. The two loops differ only in `Math.sqrt(mass)` vs `StrictMath.pow(mass, α)`. Merge them into one loop with `surfaceFactor(mass)` = `sqrtHarvest ? Math.sqrt(m) : StrictMath.pow(m, α)`. It is the same function per run, so it is byte-identical. C2 unswitches a loop-invariant final-field branch. The last Javadoc paragraph of `harvestDrive` (`:110-113`) exists only to justify the duplicate loop and goes with it. Bench, and keep the two loops if the merge measures slower.

### 11. `KernelSnapshot` is not a snapshot **[safe in kernel; touches sim]**

`runtime/KernelSnapshot.java:5-58`: 24 public fields. 19 of them alias *live* `WorldState` arrays; only the scalars are copied. It also exposes `state` itself (`:6`), so every field is redundant. The name implies a frozen copy.
- Fix: `record KernelView(WorldState state, double chemicalTotal, double massTotal)`. Callers in `sim/` read `view.state().energy`. Call sites are about 50 (mostly `sim/`), so it is a mechanical rename with no shim. If the `sim/` churn is unwanted, at least rename `reservoir`/`resourceField` (#7) and drop the `state` field or the duplicates.

### 12. `CheckpointCodec.read`: 44 lines; saved scalars listed three times

- `state/CheckpointCodec.java:86-129` becomes `expectMagic(data)`, `expectFormatVersion(data)`, `expectEngineVersion(data)`, `EngineConfig written = ConfigJson.read(readString(data))`, `expectLayout(data)`, `long expectedDigest = data.readLong()`, `EngineConfig config = branchOrWritten(written, override)`, `return restore(data, config, threads, expectedDigest)`. **[safe]**
- The scalar order is hand-copied in `write` (`:57-64`), `readState` (`:132-139`) and `StateDigest.compute` (`:59-66`). `WorldStateScalarsTest` (commit `6bde70e`) already pins it, so this is low priority. A shared declaration would be **[format]** unless it keeps the per-type writes. Leave it.
- Five type switches over `Object array` (`CheckpointCodec.java:166-225`, `StateDigest.java:100-136`, `WorldState.java:296-307`) are inherent without boxing. Only `StateDigest.lengthOf` could become `FieldSpec.length()`. Leave the rest.

### 13. Cross-cutting: hoisting `final double[] x = state.x;`

| Kind | Sites | Fix |
|---|---|---|
| Hoist used once in a tiny helper | `ReproductionPhase.wantsToDivide:189`, `.freeMooreNeighbour:209`, `IntakePhase.harvestDrive:116-117`, `EvaluatePhase.freezeDeadUnit:109-110` | inline `state.x[...]` **[safe]** |
| Hoist blocks in hot loops | every `run()` | Keep them: they are the phase's read/write set at a glance. After #4/#9 each helper hoists only its own 2 to 4 arrays. |
| Hoists in serial `finish()` | `ReproductionPhase.java:119-127`, `MovementPhase.java:114-121` | Shrink them as the extracted methods take their pieces. |

### 14. Domain story: phase numbering fights the list

- The class docs say "Phase 5, second half" (`InflowPhase.java:9`), "Phase 8" (`ReproductionPhase.java:10`), "Phase 11" (`DecayPhase.java:7`), and so on. Those are contract numbers, while the runtime list has 12 entries with different indices. A reader has to reconcile two numberings. Fix: drop "Phase N:" from the class docs, since `tickPipeline()` (#3) is the order. Keep the contract § references.
- `Shape` (`phase/Shape.java`) and the `ParallelRunner` class doc (`:7-49`) are genuine concurrency reasoning (handshake, visibility, no lost wakeup). Keep them.

### 15. Small items

| Item | Where | Fix |
|---|---|---|
| `log` is 46 lines | `logging/ConsoleTableLogger.java:10-55` | `printHeaderOnce()` + `printRow(snapshot, unit)`; the node constants come from #2 |
| `liveConnections` duplicates `connectionsOf` | `runtime/Kernel.java:220-261` | `connectionCount(slot)` + `copyConnections(slot, out, cursor)` shared by both |
| chunk bound math twice | `phase/ParallelRunner.java:98-99`, `:150-151` | `runChunk(phase, c, n)` |
| `scatterFounders` lives in `Kernel` | `runtime/Kernel.java:171-187` | Its own Javadoc says it is harness policy. Move it to `FounderPlacement` (runtime or sim), and the 8 explanatory lines go with it. |
| `CompiledConnection` field names | `layout/CompiledConnection.java` | Fine; it could be a record (export only). |
| `NodeLayout` nested classes repeat 4 lines each | `layout/NodeLayout.java:21-76` | After #2 each nested class is name constants + `MEANINGFUL_COUNT` + `TYPE_COUNT` only |

---

## Comments: which go, which stay

| Comment | Where | Verdict |
|---|---|---|
| Constructor Javadoc on slot regions and sum/mul layout | `Kernel.java:41-62` | Move to `WorldState`/`SlotCompiler` (#3) |
| `phases()` order listing | `Kernel.java:143-149` | Goes; `tickPipeline()` replaces it |
| `// a unit senses only the cell it currently occupies` | `EvaluatePhase.java:135` | Goes; becomes `chemicalHere` |
| MUL in-degree rule | `EvaluatePhase.java:146-149` | Stays (ADR 0005), on `mulOutput` |
| overflow rearrangement | `IntakePhase.java:130-131` | Stays (numeric trick), in `Saturation.drive` |
| sqrt vs pow loop justification | `IntakePhase.java:110-113` | Goes with #10 |
| claim protocol paragraphs | `MovementPhase.java:15-29`, `ReproductionPhase.java:26-41` | Half go into `Claims`; the free-cursor invariant (`ReproductionPhase.java:37-41`) stays |
| `mutate` operator list | `ReproductionPhase.java:261-283` | Split into the three step methods; the op-counter determinism paragraph stays |
| `FieldSpec` "must not be kept across ticks" | `FieldSpec.java:7-9` | Wrong today; restate (#8) |
| `mergePartials` reduction reasoning | `WorldState.java:264-272` | Stays (determinism invariant); trim the ping-pong aside |
| `Noise` hash and range docs | `runtime/Noise.java` | Stay (numeric tricks) |
| `Chunks`, `Shape`, `ParallelRunner` docs | `phase/` | Stay |

## Note on `Math` vs `StrictMath`

`Math.min/max/abs/sqrt` appear in Evaluate, Intake, Inflow, Cost and Propagate. These are IEEE-exact by spec, so determinism is fine and switching gives the same bytes. Leave them. Only transcendental calls need `StrictMath`, and they already use it (`pow`, `log`, `floor`).
