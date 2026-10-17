# Readability audit: config/, genome/, tests (commit b4c95c1, branch kernel-v6-engine)

Read in full: all 11 main files in config/ + genome/, the 11 largest-by-relevance tests (MapFrameWriter, Energy, CheckpointCodec, ReproductionClaim, InflowConfig, MulNode, EngineConfig, GeneDecoder, Indel, Biomass, Movement), plus a skim of the other 21 test files. Nothing edited.

## Ranked by reading payoff

| # | Item | Where | Payoff | Risk |
|---|------|-------|--------|------|
| 1 | Group EngineConfig's 27 positional components into 8 domain sub-records, keep JSON flat via a wire DTO | EngineConfig.java:14-41, 43-78, 120-133, 159-166; tests below | Highest: kills 3 copy-pasted 27-arg call sites, makes `v5Defaults` readable, shrinks the 36-line ctor | JSON/hash compat (see 2.4) |
| 2 | Split SlotCompiler.compile (54 lines, two near-identical passes) | SlotCompiler.java:30-83 | High: it is the densest method in the area | Low, byte-identical |
| 3 | Shared test helpers: `Worlds`, `Genes`, `EnergyAudit`, `Ticks`, `FrameHarness` | 32 test files | High: removes ~30 copies of `new Kernel(new int[][]{g}, new int[]{0}, EngineConfig.v5Defaults(0L, 1, 32), 1)`, 5 copies of the audit, 4 copies of the feedback genome | None (tests only) |
| 4 | `GeneLayout` constants + named accessors shared by GeneDecoder and GeneBuilder | GeneDecoder.java:24-46, GeneBuilder.java:105-120 | Medium-high: removes all magic shifts and the `// Source` / `// Destination` comments | Low |
| 5 | CompiledInflowField ctor (43 lines): partition + `bake()` | CompiledInflowField.java:24-66 | Medium | Low, order of float ops preserved |
| 6 | Collapse GeneBuilder to records (123 to ~40 lines) | GeneBuilder.java:25-122 | Medium | None: all 28 test chains unchanged |
| 7 | InflowSource.Disk.at/Rect.at split + `pulse()` helper; Disk component names `m0/m1` | InflowSource.java:38-62, 80-98, 128, 89/97 | Medium | Low; do not rename JSON keys |
| 8 | Tests: replace 69 non-marker comments with names or `@DisplayName`; fix weak assertions; split 2-behaviour tests | see section 5 | Medium | None |
| 9 | Rename `v5Defaults` (engine is v6) and stale/unclear component names | EngineConfig.java:120 + 100+ call sites | Medium, mechanical | JSON keys must stay (via DTO) |
| 10 | ConfigJson double-parse trick and dense error unwrapping | ConfigJson.java:57-67, 14-16 | Low-medium | None |
| 11 | ValueNoise.at (28 lines of 8 corners + 7 lerps) | ValueNoise.java:29-56 | Low | Byte-identical if lerp nesting kept |
| 12 | InflowConfig `InflowSource[]` to `List<InflowSource>` drops 2 hand-written equals/hashCode | InflowConfig.java:26-37 | Low | Call-site updates (RunConfig, RunConfigTest) |

Latent bug found while reading (not readability, but affects naming): `GeneDecoder.MAX_WEIGHT = 4.0` is not a bound. Raw `-32768` decodes to `-32768 * 4.0/32767 = -4.00012`. `GeneDecoderTest.anyRandomIntDecodesToConnection` (line 76) asserts `abs(weight) <= 4.0` for 10,000 genes and only passes because `Random(42)` never hits raw -32768 (about 14% chance for a random seed). Either fix the assertion to `<= 4.0 * 32768 / 32767` or rename the constant.

---

## 1. Methods / records over ~25 lines

| File:line | Does | Why hard | Split (names + params in call order) |
|---|---|---|---|
| SlotCompiler.java:30-83 (54) `compile(state, unit)` | Writes a slot's connections: sum-destined first, MUL-destined after, computes ranges, harvest count, MUL in-degree | Two near-identical loops; `cursor`, `harvest`, `mulDegree` all mutated across both; 5 outputs from one body | `compile(state, unit)`: `base`, `count`; `sumEnd = placeSumConnections(state, base, count)`; `mulEnd = placeMulConnections(state, sumEnd, base, count)`; set `sumStart/sumEnd/mulStart/mulEnd`; `state.harvestConnCount[unit] = countHarvestTransporters(state, base, sumEnd)`; `state.mulInDegree[unit] = mulInDegree(state.genes, base, count)`. Shared helper `storeConnection(state, slotIndex, src, dst, gene)` replaces the 3-line writes at 51-53 and 74-76. `isStored(src)` = `NodeLayout.isMeaningful(src)` carries the junk-pruning invariant by name. `countHarvestTransporters` re-reads `connDst[base..sumEnd)`; `mulInDegree` = `Math.min(2, n)` over genes whose dst is MUL (junk source included). Two extra O(count) passes per birth, not per tick; check birth-heavy runtime on the baseline. Digest unchanged. |
| CompiledInflowField.java:24-66 (43) ctor | Splits sources into static/dynamic, bakes static ones into `double[cells]` | Two counting loops, one fill loop, nested bake loop, early `return` in a ctor | ctor: assign `baseline/combine/worldWidth`; `dynamic = select(config.sources(), false)`; `baked = bake(select(config.sources(), true))`. `private static InflowSource[] select(InflowSource[] all, boolean wantStatic)`. `private double[] bake(InflowSource[] fixed)` returns `null` when `fixed.length == 0`; inner `bakedCapAt(cell, fixed)`. Add `x(cell)`/`y(cell)` helpers to remove the duplicated `cell % w`, `cell / w` at 56-57 and 74-75. Same source order, same `combine.apply` chain: byte-identical. |
| EngineConfig.java:43-78 (36) compact ctor | Validates 27 fields | Wall of `nonNegative("x", x)` calls | Sub-record compact ctors validate their own 2 to 5 fields (see 2). EngineConfig ctor keeps `world` and `chemicals` checks only (about 6 lines). Move `nonNegative/positive/unitInterval` (102-118) to package-private `Checks`. |
| EngineConfig.java:120-133 (14, 27 literals) `v5Defaults` | Builds defaults | 27 unlabelled numbers in 7 lines; swapping two adjacent doubles compiles | Labelled sub-records (see 2.2). |
| EngineConfig.java:159-166 `copy(...)` | Forwards 27 args | Exists only because the record is flat | Disappears; each wither becomes `new EngineConfig(seed, world, chemicals, founder, ...)` with 11 args. |
| InflowSource.java:38-62 (25) `Disk.at` | Computes the moving centre, toroidal distance, pulse | `final double cx; cy;` assigned in three switch arms; ORBIT computes `ang` and two trig calls inline | `at`: `dx = wrapDelta(x - centreX(tick, worldWidth), worldWidth)`; `dy = ...centreY...`; `return dx*dx + dy*dy > radius*radius ? 0.0 : pulse(peak, tick, periodTicks, phaseTicks)`. `centreX/centreY(tick, W)` each a switch expression over `motion`; `orbitAngle(tick)`. No per-eval allocation (do not return a pair). Identical float ops. |
| InflowSource.java:80-98 (19) `Rect.at` | Static box vs drifting toroidal band | Two modes in one body, `peak * triangle(...)` twice | `at`: `return covers(x, y, tick, W) ? pulse(...) : 0.0`; `coversStaticBox(x, y, W)`; `coversDriftingBand(x, y, tick, W)`. |
| InflowSource.java:89, 97, 128 | `peak * triangle(tick, periodTicks, phaseTicks)` x3 (Disk 61, Rect 89/97, Points 128) | Repeated expression | `static double pulse(double peak, int tick, int periodTicks, int phaseTicks)` next to `triangle`. |
| InflowSource.java:110-121 (12) `Points.equals/hashCode`; InflowConfig.java:26-37 | Array-in-record boilerplate | Noise | InflowConfig: use `List<InflowSource>` (JSON identical). Points: keep `int[]` (boxing in a per-tick scan would break the perf doctrine); import `java.util.Arrays` instead of the fully-qualified names at 112, 120. |
| ValueNoise.java:29-56 (28) `at` | Trilinear value noise | 8 corner reads + 7 lerps in a flat list | `alongX(seed, x0, y, z, u) = lerp(corner(seed,x0,y,z), corner(seed,x0+1,y,z), u)`; `at` = `lerp(lerp(alongX(y0,z0), alongX(y0+1,z0), v), lerp(alongX(y0,z0+1), alongX(y0+1,z0+1), v), w)`. Same lerp nesting: bit-identical. |
| GeneBuilder.java:25-122 (3 nested classes, 98 lines) | Staged builder | Each stage re-declares and re-copies all earlier fields | `record SourceStage(Kind kind, int id)`, `record DestinationStage(SourceStage source, Kind kind, int id)`, `record WeightStage(DestinationStage destination, short weightRaw)` with `build()` calling `GeneLayout.pack(...)`. Public API unchanged. |
| ConfigJson.java:57-67 (11 but dense) `read` | Parses twice (lenient-missing first so unknown keys are named first), unwraps cause | Intent of the first `readValue` (result discarded) is invisible; ternary at 62-63 `cause == e ? ... : ...` | Rename `MAPPER` to `UNKNOWN_KEY_FIRST_MAPPER`; `read` = `rejectUnknownKeys(json)` then `readStrict(json)`; `messageOf(ValueInstantiationException e)` replaces the ternary. The double parse is the one place a one-line comment is honest (or put it in the method name `readNamingUnknownKeysFirst`). |

Records over ~25 lines of declaration: only EngineConfig (27 components). ChemicalSpec (7) and the InflowSource records (6 to 10) are fine, except `Disk` (10 components, two of them untyped `m0, m1`).

---

## 2. EngineConfig

### 2.1 Problem
- 27 positional components; 22 are `double`, so a swap of two adjacent arguments compiles and changes the physics.
- Three test files copy all 27 accessors just to change 1 to 6 fields: CheckpointCodecTest.java:180-189, ReproductionClaimTest.java:35-44, EngineConfigTest.java:50-56.
- Each phase reads only its own domain (CostPhase 4 fields, GrowthPhase 3, IntakePhase 3, MovementPhase 3, ReproductionPhase 6, EvaluatePhase 2), so the domains already exist in the code.

### 2.2 Proposal: 8 sub-records (each at most 4 args), EngineConfig drops to 11 components

| Sub-record | Components | Read by |
|---|---|---|
| `World` | `worldWidth, maxGenes` | Kernel, WorldState |
| `Founder` | `initialEnergy, initialMass` | Kernel ctor (creditedInitial*) |
| `Metabolism` | `basalCost, costPerPropagation, maintPerMass, agingCost` | CostPhase |
| `Harvest` | `capacityPerConnection, halfSaturation, massExponent` | IntakePhase |
| `Motility` | `costBase, costPerMass, deadzone` | MovementPhase |
| `Growth` | `max, halfSaturation, yield` | GrowthPhase |
| `Sensing` | `selfEnergyScale, selfMassScale` | EvaluatePhase |
| `Reproduction` | `buildCost, buildCostPerGene` | ReproductionPhase |
| `Mutation` | `weightRate, structRate, duplicationRate, deletionRate` | ReproductionPhase |

`EngineConfig(long seed, World world, List<ChemicalSpec> chemicals, Founder founder, Metabolism metabolism, Harvest harvest, Motility motility, Growth growth, Sensing sensing, Reproduction reproduction, Mutation mutation)`.

Each sub-record validates itself in its compact ctor (3 to 6 lines). Keep the error text field names identical to today (`"buildCost must be..."`), because EngineConfigTest.assertRejected matches on them.

### 2.3 Before / after (real call sites)

Before, CheckpointCodecTest.java:180-189 (10 lines):
```java
return new EngineConfig(base.seed(), base.worldWidth(), base.maxGenes(), base.chemicals(),
        base.initialEnergy(), basalCost, base.costPerPropagation(), base.harvestCapacityPerConnection(),
        ... 17 more accessors ...);
```
After (1 line):
```java
return CONFIG.withMetabolism(CONFIG.metabolism().withBasalCost(basalCost));
```

Before, ReproductionClaimTest.java:35-44:
```java
return new EngineConfig(d.seed(), d.worldWidth(), ..., buildCost, buildCostPerGene,
        mutationRate, mutationRate, indelRate, indelRate, d.agingCost());
```
After:
```java
return EngineConfig.defaults(0L, world, maxGenes)
        .withReproduction(new Reproduction(buildCost, buildCostPerGene))
        .withMutation(new Mutation(mutationRate, mutationRate, indelRate, indelRate));
```

Before, `v5Defaults` (EngineConfig.java:121-132, 27 bare literals). After:
```java
return new EngineConfig(seed, new World(worldWidth, maxGenes), List.of(defaultResource()),
        new Founder(1000.0, 1.0),
        new Metabolism(0.5, 0.06, 0.5, 0.0005),
        new Harvest(1.5, 1.0, 0.5),
        new Motility(0.2, 0.1, 0.1),
        new Growth(5.0, 1.0, 0.8),
        new Sensing(2000.0, 10.0),
        new Reproduction(10.0, 0.25),
        new Mutation(0.000025, 0.000005, 0.0001, 0.0001));
```
(Mapping checked against the current literal order and examples/baseline.json: initialEnergy 1000, basalCost 0.5, costPerPropagation 0.06, harvest 1.5/1.0/0.5, move 0.2/0.1/0.1, self scales 2000/10, initialMass 1.0, maintPerMass 0.5, grow 5.0/1.0/0.8, build 10/0.25, mutation 2.5e-5/5e-6, indel 1e-4/1e-4, aging 5e-4.)

Phase call sites: `config.basalCost()` becomes `config.metabolism().basalCost()` (about 25 sites across 8 phase/kernel files). Phases keep copying to primitive fields in their ctors, so the hot path is untouched.

Withers: only add what tests and RunConfig need: `withSeed/withWorld/withChemicals/withChemical0Inflow` (exist), plus `withMetabolism`, `withReproduction`, `withMutation`, and `Metabolism.withBasalCost`. No speculative withers.

### 2.4 Constraint check

| Constraint | Status |
|---|---|
| Byte-identical output | Safe: pure restructuring, same doubles reach the same phase fields. |
| JSON compatible | AT RISK if done naively. Grouping changes the wire shape. The config JSON is embedded in checkpoints (CheckpointCodec.java:54, 105), hashed for the replay key (`ConfigHash`), and `examples/baseline.json` is flat. Jackson 2.18 `@JsonUnwrapped` on record creator parameters is unreliable, so do not use it. Use a private flat DTO inside ConfigJson: `record FlatEngineConfig(<today's 27 components in today's order>)` with `static FlatEngineConfig of(EngineConfig)` and `EngineConfig toConfig()`. `write/read` go through it. Output stays byte-identical, existing checkpoints and configs still load, and the config hash does not change. This is the only place the flat 27-field list survives, and it is data, not logic. |
| No back-compat shims | OK: no forwarding ctor; every call site (11 main, about 12 test files) is updated. The DTO is a wire format, not a shim. |
| Genes always legal | Untouched. |
| Error messages | Keep field names verbatim (EngineConfigTest asserts them). `ValueInstantiationException` unwrapping in ConfigJson.read still works because validation stays in compact ctors. |

Alternative rejected: a test-only `EngineConfig.Builder`. With sub-records the three painful tests need one wither each; a 27-method builder would re-create the same flat list.

---

## 3. Names that need a comment, and better names

Wire names (JSON keys) must not change, so every rename of an EngineConfig component below lives in the grouped records and is mapped by the flat DTO.

| Current | File:line | Better | Note |
|---|---|---|---|
| `v5Defaults` | EngineConfig.java:120 | `defaults` | Engine is v6; name lies. About 100 call sites, one sed. |
| `withChemical0Inflow` | EngineConfig.java:135 | `withResourceInflow` or `withPrimaryInflow` | "chemical 0 = the one units sense and harvest" is only in Kernel javadoc. |
| `maintPerMass` | :31 | `maintenancePerMass` | abbreviation |
| `moveCostBase` / `moveCostPerMass` | :25-26 | `Motility.fixedCost / costPerMass` | |
| `selfEnergyScale` / `selfMassScale` | :28-29 | `Sensing.selfEnergyScale / selfMassScale` (keep) | The group name explains it (normalising divisor of the SELF_* sensors). |
| `mutationRateWeight / mutationRateStruct` | :37-38 | `Mutation.weightRate / structRate` | |
| `indelRateDup / indelRateDel` | :39-40 | `Mutation.duplicationRate / deletionRate` | Per survivor vs per gene is a real difference; the Javadoc says it, the name should. |
| `costPerPropagation` | :21 | keep, group `Metabolism` | |
| `ChemicalSpec.decayTarget == -1` meaning "sink" | ChemicalSpec.java:3-7, EngineConfig.java:92 | `public static final int SINK = -1;` on ChemicalSpec, used in validation and in `defaults` | Removes the Javadoc and the magic `-1` in `new ChemicalSpec(..., -1)` (EngineConfig.java:123, ~10 tests). |
| `InflowSource.at(...)` | InflowSource.java:19 | `capAt(...)` | Reads as what it returns; matches `CompiledInflowField.cap`. |
| `Disk.m0, m1, motionPeriod` | InflowSource.java:35 | Keep JSON keys. Add non-component accessors: `orbitRadiusFraction()` (=m0), `driftX()` (=m0), `driftY()` (=m1). Use them inside `centreX/centreY`. | m0 means two different things depending on `motion`. Renaming the components would break JSON. Jackson does not auto-detect `foo()` methods on records, but run the EngineConfigTest round trip to confirm. |
| `Rect.vx, vy`, `Disk.fx, fy`, `Noise.gain/threshold` | | fine | |
| `GeneDecoder.sourceLocal / destinationLocal` | GeneDecoder.java:24, 34 | `sourceNodeIndex / destinationNodeIndex` | "local" = unit-local flat index; `decode` has the absolute one. |
| `WEIGHT_MULTIPLIER` | GeneDecoder.java:13 | `WEIGHT_PER_RAW_UNIT` | |
| `MAX_WEIGHT` | GeneDecoder.java:12 | `NOMINAL_MAX_WEIGHT` (or drop; used in NodeLayout.java:162 for layout.json) | It is not a bound (see latent bug above). |
| `SlotCompiler.harvest` | SlotCompiler.java:40 | `harvestTransporters` | |
| `MAPPER / STRICT_MAPPER` | ConfigJson.java:35, 39 | `UNKNOWN_KEY_FIRST_MAPPER / STRICT_MAPPER` | |
| `InflowConfig.cyclic(..)` hard-coded `0.5, 0.5` centre | InflowConfig.java:76-77 | `private static final double CENTRE = 0.5;` | |

Comments that exist only because the code is unclear (candidates to delete once the structural change lands):
- GeneBuilder.java:108, 112, 116: `// Source`, `// Destination`, `// Weight` (replaced by `GeneLayout.pack`).
- SlotCompiler class Javadoc (lines 6-22, 17 lines): the junk-pruning proof. Reduce to 2 lines at `isStored` / `mulInDegree` (the signed-zero argument is genuinely non-obvious); move the rest to the contract link.
- ConfigJson.java:14-16: the Javadoc says unknown-key ordering in a hard-wrapped over-long line; replaced by the method names above.
- CompiledInflowField class Javadoc (lines 3-15): "baked stays null and dynamic empty": expressed by `bake()` returning null for no static sources.
- InflowSource.java:200-206 (`triangle` doc): keep one line (phase 0 starts at the trough), delete the rest.
- ValueNoise.java:5-17, 22-28: keep 2 lines (trig-free for cross-JVM determinism; 53-bit uniform), drop the rest.
- Owner's AGENTS.md says Javadoc is welcome here (project-local exception) while the goal is "basically no comments". Decide which wins before stripping Javadoc; I recommend keeping only invariants that cannot be named.

---

## 4. Bit-level code

### 4.1 Make self-explanatory with named constants/methods
Today the layout `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]` is repeated as literals in three places: GeneDecoder.java:25-26, 35-36, 45; GeneBuilder.java:109-117; and (as an oracle, deliberately) GeneDecoderTest.java:85-89. Decoder also mixes two idioms for a 1-bit test: `(rawGene >>> 31) == 0` (:26) vs `((rawGene >>> 23) & 0b1) == 0` (:36).

Proposed `final class GeneLayout` (package-private, genome/):
```java
static final int SOURCE_KIND_SHIFT = 31, SOURCE_ID_SHIFT = 24;
static final int DESTINATION_KIND_SHIFT = 23, DESTINATION_ID_SHIFT = 16;
static final int ID_MASK = 0x7F, WEIGHT_MASK = 0xFFFF;
static boolean sourceIsInternal(int gene)       // (gene >>> 31) != 0
static int sourceId(int gene)                   // (gene >>> 24) & ID_MASK
static boolean destinationIsAction(int gene)    // ((gene >>> 23) & 1) != 0
static int destinationId(int gene)              // (gene >>> 16) & ID_MASK
static short weightRaw(int gene)                // (short) gene
static int pack(boolean srcInternal, int srcId, boolean dstAction, int dstId, short weightRaw)
```
Then GeneDecoder reads like the spec:
```java
public static int sourceNodeIndex(int gene) {
    return GeneLayout.sourceIsInternal(gene)
            ? nodeIndex(INTERNAL_OFFSET, sourceId(gene), Internal.TYPE_COUNT, Internal.INSTANCES_PER_TYPE)
            : nodeIndex(SENSOR_OFFSET, sourceId(gene), Sensor.TYPE_COUNT, Sensor.INSTANCES_PER_TYPE);
}
private static int nodeIndex(int blockOffset, int id, int typeCount, int instancesPerType) {
    return blockOffset + (id % typeCount) * instancesPerType;
}
```
This removes the 4 duplicated `offset + (id % COUNT) * PER_TYPE` expressions (GeneDecoder.java:27-28, 30-31, 37-38, 40-41). Keep GeneDecoderTest's literal reference model untouched: it is the independent oracle and must not use GeneLayout. NodeLayout.java:162 (`toJson` emits the bit layout) could read the same constants so there is one source of truth.

### 4.2 Genuine non-obvious tricks (keep, one short comment or one well-named method)

| Trick | Where | Treatment |
|---|---|---|
| `% typeCount` is safe only because the id was masked to 7 bits first (non-negative); a negative id would give a negative modulus | GeneDecoder.java:25, 35 | Name `ID_MASK` and call it `wrapToBlock`; no comment needed once masking and modulo sit in one helper. This is the "every int is a legal gene" invariant. |
| Sign extension: `(short)(rawGene & 0xFFFF)` | GeneDecoder.java:45 | `GeneLayout.weightRaw(gene)` names it. |
| Divisor is `Short.MAX_VALUE`, so -32768 maps to -4.00012 (asymmetric, "unclamped") | GeneDecoder.java:12-13 | One short comment or an ADR line; this is the surprise that makes MAX_WEIGHT misleading. |
| Junk-source pruning is exact because junk outputs are `+0.0` (signed-zero addition, strict comparisons) | SlotCompiler.java:15-19 | Keep 2 lines at `isStored`. `mulInDegree` counts junk sources: name it `mulInDegreeIncludingJunk`. |
| ValueNoise hash multipliers 73856093 / 19349663 / 83492791 are the classic spatial-hash primes; `(h >>> 11) * 0x1.0p-53` gives a uniform 53-bit mantissa in [0,1) | ValueNoise.java:59-62 | Constants `X_PRIME, Y_PRIME, Z_PRIME` and `UNIT_INTERVAL_53BIT`; one comment on the 53-bit scale. |
| Double modulo for negatives: `((a % p) + p) % p` | InflowSource.java:211, 228; Ring:188 | Ints (line 211): replace with `Math.floorMod(tick + phaseTicks, periodTicks)`, identical for p>0. Doubles (188, 228): keep as is, `floorMod` does not exist for doubles with the same bits; name it `positiveModulo(double, double)`. |
| Quintic fade `6t^5 - 15t^4 + 10t^3` | ValueNoise.java:66 | Already named `fade`; fine. |

---

## 5. Tests

### 5.1 Long methods (>25 lines) and what to do

| File:line | Lines | Fix |
|---|---|---|
| KernelVerticalSliceTest:25 `matchesSpecReferenceModelForTenTicks` | 60 | Extract `referenceModel` as a small private class `FeedbackReference` with `step()` returning the five expected arrays; genome from `Genes.FEEDBACK_LOOP`; the test loop becomes 8 lines. |
| MulNodeTest:142 `specSliceFeedbackLoopStaysAlive` | 44 | Same genome as above with `MUL` inserted; use `Genes.internalToInternal(...)`; extract `ticksUntil` flags. Rename: `feedbackThroughMulKeepsSignalAlive` ("spec slice" is jargon). |
| EnergyTest:136 `intakeSaturatesAndDivergentHarvesterImplodes` | 42 | Two behaviours in one test: split into `intakeNeverExceedsTheTransporterCeiling` and `divergentHarvesterEventuallyDies`; shared `delayBomb` fixture. |
| MapFrameWriterTest:163 `chunkIndexOffsetsPointAtChunkStarts` | 35 | `FrameHarness` with `chunks()` returning decoded blocks; loop body 4 lines. |
| DynamicsObserverTest:75 `countsEveryLivingUnitIncludingDescendants` | 35 | Extract `expectedPropagations(kernel)` helper; the arrange/act/assert is interleaved in one loop. |
| BirthLogWriterTest:22 | 36 | Extract `parseBirthRows(String)`; the assertions on columns go in a loop helper `assertWellFormed(row)`. |
| EnergyTest:76 `unitDiesAndStaysDeadAndInert` | 30 | `Ticks.untilDead(kernel, slot, max)` then one `assertFrozen(...)` after death. |
| BiomassTest:62 `fissionHalvesMassAndEnergy` | 30 | `Ticks.until(kernel, 10, () -> births >= 1)`; `livingSlots(snapshot)` helper. |
| ParallelRunnerTest:111 | 29 | Extract `cleanWindow(kernel, ids)`; the loop condition at 129 is cryptic. |
| GeneDecoderTest:81 | 28 | Fine as an oracle; extract `referenceSource(gene)`, `referenceDestination(gene)` private methods. |
| RunConfigTest:16, CheckpointCodecTest:48 `assertSavedStateEquals`, ReproductionClaimTest:152, :100, EnergyTest:108, DeterminismMatrixTest:69, ObservabilityTest:80, ReproductionClaimTest:181 | 25-27 | Borderline. `assertSavedStateEquals` could split into `assertScalarsEqual` and `assertSavedArraysEqual`. `skipSamplingRate` rename: `geometricGapFlipsBitsAtTheConfiguredRate`. |

### 5.2 Setup duplication (counts from grep)

| Duplicate | Count | Where |
|---|---|---|
| `new Kernel(new int[][]{g}, new int[]{0}, EngineConfig.v5Defaults(0L, 1|2|4, 32), 1)` | about 30 | EnergyTest x8, MulNodeTest x4, MovementTest x5, BiomassTest x3, MultiUnit x4, KernelVerticalSlice x3, IndelTest |
| `IntStream.range(0, genomes.length).toArray()` as founder cells | 3 | EnergyTest:44, DynamicsObserverTest:38, RunReportTest:20 |
| The same 4-gene feedback genome (CONST->ADD, ADD->DELAY, DELAY->ADD, ADD->Y) | 4 | MultiUnitKernelTest:14-31, DynamicsObserverTest:15-32, KernelVerticalSliceTest:27-44, Main.java:252-268 |
| Energy conservation audit (`held` vs `credited`) | 5 | EnergyTest:54, IndelTest:116-117, BiomassTest:51-53, InflowConfigTest:190-192, MovementTest:60 |
| `totalEnergy(snapshot)` | 2 | EnergyTest:31, MovementTest:25; `population()` in DeterminismMatrixTest:46 |
| `weight(raw) = raw * WEIGHT_MULTIPLIER` | 3 | MulNodeTest:17, KernelVerticalSliceTest:47-50, MovementTest:70 |
| Food-rich config `v5Defaults(...).withChemical0Inflow(field(8.0, MAX))` | 3 | IndelTest:36, SeedTest:30, ReproductionClaimTest:158 |
| `DEFAULTS = v5Defaults(0L, 1, 1)` used only as a value bag | 4 | EnergyTest:14, MovementTest:15, IndelTest:21, BiomassTest:22, ReproductionClaimTest:26 |
| MapFrameWriterTest 8 tests each repeat: `ByteArrayOutputStream`, `StringWriter`, kernel, writer, tick loop, `close()` | 8 | MapFrameWriterTest |
| `bytes(kernel)` checkpoint round trip | 3 | CheckpointCodecTest:34-42, RunLifecycleTest:39-43, WorldStateScalarsTest:73-75 |

### 5.3 Proposed helpers (src/test/java/com/simolution/support/)

| Helper | Signature | Replaces |
|---|---|---|
| `Worlds.kernel` | `static Kernel kernel(int worldWidth, int[]... genomes)` (founders in cells 0..n-1, `defaults(0L, worldWidth, 32)`, 1 thread) | about 30 constructions and the 3 `IntStream` lines |
| `Worlds.kernelAt` | `static Kernel kernelAt(int worldWidth, int[][] genomes, int... cells)` | the explicit-cell cases (Movement, Reproduction) |
| `Worlds.randomKernel` | `static Kernel randomKernel(long seed, int founders, int genes, EngineConfig config)` (uses `GenomeFactory.random` + `scatterFounders`) | StateDigest, Checkpoint, Seed, Determinism, MapFrameWriter |
| `Worlds.foodRich` | `static EngineConfig foodRich(int world, int maxGenes)` | the three `withChemical0Inflow(field(8.0, MAX))` |
| `Genes.*` | `sensorToInternal(int sensor, int internal, int weightRaw)`, `sensorToAction`, `internalToInternal`, `internalToAction`, `constTo(int action, int weightRaw)`, `weight(int raw)`, constant `FEEDBACK_LOOP` | 28 four-line `GeneBuilder` chains become one-liners; kills 4 copies of the feedback genome and 3 `weight()` copies. GeneBuilder stays as the production-side API; GeneDecoderTest keeps raw builder chains. |
| `EnergyAudit.assertConserved` | `static void assertConserved(KernelSnapshot s, double tolerance)` plus `static double totalEnergy(KernelSnapshot s)` | 5 audit copies and 2 `totalEnergy`. Verify first that MovementTest's `totalField(s)` equals `s.reservoir` (it uses a different term). Tolerances differ (1e-2 vs 1e-6) so keep the parameter. |
| `Ticks.until` | `static int until(Kernel k, int maxTicks, BooleanSupplier done)` returns ticks run, or -1 | `tickUntilFirstBirth`, BiomassTest fission loop, 4 "died" loops (Energy, Indel x2, Determinism) |
| `Ticks.step` | use existing `kernel.step(n)` instead of the 28 bare `for (...) kernel.tick()` loops that assert nothing | MovementTest:75-77, 90-92, EnergyTest, MultiUnit, BiomassTest:101, 124 |
| `FrameHarness` | `FrameHarness.forWorld(int w).ticks(int n).window(int from, int to).sampleEvery(int k)`; `runFor(int ticks)`; `framesText()`, `tickLines()`, `idx()`, `catalog()` | 8 MapFrameWriterTest tests drop to about 6 lines each |
| `CheckpointBytes` | `static byte[] of(Kernel)`, `static Kernel restore(byte[], int threads)` | the 3 `bytes(...)`/`read(...)` duplicates |

Resulting shape of one test (EnergyTest:179 `deductionClampsToAvailableNeverOverdraws`):
```java
@Test
void energyNeverGoesNegativeAndSinkNeverExceedsWhatEntered() {
    // arrange
    Kernel kernel = Worlds.kernel(1, Genes.BUSY_LOOP);

    // act + assert
    for (int i = 0; i < 5000; i++) {
        kernel.tick();
        EnergyAudit.assertSinkWithinCredits(kernel.snapshot());
    }
}
```

### 5.4 Test names and weak assertions

| File:line | Issue | Fix |
|---|---|---|
| GeneDecoderTest:75-76 | `assertEquals(true, connection != null, ...)` and `assertEquals(true, Math.abs(..) <= 4.0)`: non-null is trivially true; second is the latent -4.00012 bug | `assertTrue`; drop the null check; assert `abs <= 4.0 * 32768 / 32767` |
| GeneDecoderTest:66 | `new java.util.Random(42)` fully qualified | import |
| EnergyTest:42, 44, RunReportTest:20, ObservabilityTest:108-117 | Fully qualified `com.simolution.sim.GenomeFactory`, `java.util.stream.IntStream`, `Noise` | imports (also removed by `Worlds`) |
| EnergyTest:136 | Two behaviours, 2 clauses in the name | split (5.1) |
| MulNodeTest:142 `specSliceFeedbackLoopStaysAlive` | "spec slice" is repo jargon | `feedbackThroughMulKeepsSignalAlive` |
| ReproductionClaimTest:181 `skipSamplingRate` | name says mechanism, not behaviour | `geometricGapFlipsBitsAtTheConfiguredRate` |
| DeterminismMatrixTest:98 `tinyWorlds` | | `parallelMatchesSerialInDegenerateWorlds` |
| ReproductionClaimTest:99 `childGenomeIndependentOfSlot` | fine, but helpers `withFillers/fillerCells` (127-149) sit between tests | move private helpers to the bottom |
| ObservabilityTest:41 | `ENGINE` constant declared after use | reorder |
| EnergyTest/MovementTest | `DEFAULTS = v5Defaults(0L,1,1)` as a value bag, then a second `v5Defaults(0L, 4, 32)` per test | one `CONFIG` constant or `Worlds` default |

### 5.5 Comments in tests (owner rule: only `// arrange`, `// act`, `// assert`)
69 non-marker comment lines: EnergyTest 15, IndelTest 14, InflowConfigTest 12, BiomassTest 10, MapFrameWriterTest 6, MovementTest 6, ObservabilityTest 5, RunReportTest 1. Also markers carrying prose: `// act + assert: contract v5 §6 — ...` (EnergyTest:49), `// arrange: no harvest -> ...` (EnergyTest:77), `// arrange: one mover on a 4x4 torus...` (MovementTest:43). Plus Javadoc on test classes and helpers (MapFrameWriterTest:23-29, 32; ReproductionClaimTest:19-23; IndelTest:13-18; BiomassTest:13-19; ParallelRunnerTest:104-109; ObservabilityTest:19-22).

Treatment:
1. Contract references (`// contract-v5 §6: ...`, BiomassTest:34, 59, 93, 113; IndelTest:40, 56, 83, 103): move to `@DisplayName("contract-v5 §6: mass is crystallized energy, audit stays exact")`. It is an annotation, not a comment, keeps traceability, and shows in test reports.
2. Explanations of a number (`// 512 * 4/32767 ~ 0.0625 < MOVE_DEADZONE 0.1`, MovementTest:69): replace with a named constant `SUB_DEADZONE_RAW` and the existing precondition assertion (already at :70).
3. `// CONST(=1.0) -> MOVE_E with a weight...` (MovementTest:17): delete, `driveEast` says it.
4. Inline "why this value" (`// 10 ticks, 5 target frames -> sample-every 2 -> ticks 2,4,6,8,10`, MapFrameWriterTest:104): the test name plus `assertEquals(List.of("t 2",...))` already says it.
5. IndelTest:30-33 (food-rich world rationale): the helper name `Worlds.foodRich` carries it.
6. Genuine non-obvious: ParallelRunnerTest:104-109 (JIT warm-up allocation window) and EnergyTest:172-175 (why the diverger implodes) deserve to survive as a one-line comment or a `@DisplayName`.
Also: AGENTS.md says Javadoc is welcome; the goal says no comments. Same decision as section 3.

---

## 6. Constraint summary

| Constraint | Items at risk |
|---|---|
| Determinism / byte-identical | #1 (none if only wiring changes), #2 (extra passes, same results), #5 (keep op order), #11 (keep lerp nesting). Run the mandatory baseline diff (`runs/baseline.txt`, digest `b3f5198071d3d9e5` per AGENTS.md) after each. |
| Every int a legal gene | Untouched by all proposals; `GeneLayout.destinationId/sourceId` must keep the 7-bit mask before `%`. |
| No back-compat shims | #1 updates every call site; no forwarding ctors. The flat DTO is a wire format only. #9 rename is a full sed, no deprecated alias. |
| JSON compat | #1 and #9 only, via the flat DTO in ConfigJson (keeps today's keys, order, and so the config hash and checkpoint-embedded configs). #7 must not rename Disk keys `m0, m1, motionPeriod`. #12 (`List<InflowSource>`) serialises as the same JSON array. |
| Performance doctrine | #5: constructor-time streams or loops are fine (precompute). Do not replace `Combine`'s per-constant `apply` with lambdas on the hot path. Keep `Points.cells` as `int[]` (no boxing in the per-tick scan). |

## Suggested order
1. Test helpers (#3), zero production risk, instantly shorter tests.
2. SlotCompiler split (#2) and GeneLayout (#4), each one commit plus one ADR line, baseline diff.
3. EngineConfig sub-records + flat DTO (#1, #9), one commit, round-trip and checkpoint tests must pass unchanged.
4. InflowSource / CompiledInflowField / ValueNoise (#5, #7, #11).
5. Test comment sweep (#8) last, once names carry the meaning.
