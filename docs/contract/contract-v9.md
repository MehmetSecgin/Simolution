# Contract v9: claim phases, seed-keyed streams, thread-count independence (engine v6)

Delta over [contract-v8](contract-v8.md). Rewrites how the two order-dependent phases (reproduction
and movement) resolve conflicts, how mutation is keyed and sampled, which seed drives which
random stream, and what "deterministic" means once phases run on several threads. The biology
(energy, mass, intake, decay, indels, fission economics) is unchanged.

**Status: implemented** (engine v6, ADRs 0038 to 0045). **Binding.** Design:
[engine-v6-design.md](../notes/engine-v6-design.md) §5.6, §5.7, §5.8, §6. Conformance:
`MovementTest`, `ReproductionClaimTest`, `SeedTest`, `NoiseTest`, `IndelTest`,
`DeterminismMatrixTest`, `StrictMathLintTest`.

## What this supersedes

Nothing else in v0 to v8 changes. Exactly these statements stop holding:

| Superseded text | Replaced by |
|---|---|
| contract-v3 §9, motility: "two units to the same free cell resolved by ascending slot order", and the implied chain rule (a cell vacated earlier in the same scan is enterable) | §1 |
| contract-v5 §5, reproduction: "units scanned in ascending slot order, child installed immediately", the placement order (room, claim a free slot, build the genome, pay), and a failed build burning a slot | §2 |
| contract-v4 §4, determinism: mutation keyed `(seed, childSlot, birthTick, opIndex)` with one draw per point-mutation bit | §3 |
| the "pipeline is locked, new mechanics only append" rule and its phase listings (contract-v2 §11, contract-v3 §6, contract-v5 §8, contract-v8) | §6 |

Contract-v4 §4's statement that mutation is domain-separated from the `RAND` sensor and founder placement still holds (now by explicit stream tags, §4). Seeding of kernel randomness and the `Math.pow` to `Math.sqrt` change (§4, §5) correct the implementation, not a contract sentence.

Old contracts are not rewritten; each carries a one-line pointer here.

## 1. Movement is a claim (replaces contract-v3 §9 tie and chain rules)

Phase `movement` has two steps.

1. **Propose**, in parallel, per unit. Intent is computed exactly as before (`MOVE_E - MOVE_W`
   and `MOVE_N - MOVE_S` past the deadzone `θ`, one Moore step, toroidal wrap) and is valid only if
   the target cell is **free at the start of the phase** and
   `energy > MOVE_COST_BASE + MOVE_COST_PER_MASS · mass`. Otherwise the unit has no intent.
2. **Resolve**, serially, in ascending slot order: the first intent on a target cell wins. Winners
   move and pay the cost to the sink; losers stay put and pay nothing.

Consequences, all binding:

- Freeness is read at phase start, so a cell vacated by a mover this tick cannot be entered this
  tick. There are no chains and no swaps.
- Among units targeting the same free cell the **lowest slot wins**. Losers do not retry.
- The slot never changes, so movement allocates nothing and touches no connection store.

## 2. Reproduction is a claim (replaces contract-v5 §5 placement order)

The economics of contract-v5 §5 stand: `REPRODUCE > 0` is an ungated trigger,
`buildCost = BUILD_COST + BUILD_COST_PER_GENE · childGeneCount` goes to the sink and into
`damage[parent]`, the remaining energy and the mass split 50/50, the child's `damage` is 0.
What changes is the order of the steps and who wins a contested resource.

1. **Propose**, in parallel, per parent. A parent proposes only if it wants to divide, has a free
   Moore neighbour **at phase start** (lowest cell index), and
   `energy > buildCost` for the child it would build. The child genome length, and so
   `buildCost`, is known from the parent's mutation key alone (§3), before any slot exists. A
   parent that cannot afford its child **drops out before any claim: it pays nothing and burns no slot**.
2. **Resolve**, serially, in ascending parent slot: the first claim on a child cell wins
   (**lowest parent wins**); losers pay nothing and retry nothing this tick.
3. **Assign slots.** Winners, in ascending parent slot, take free slots in **ascending slot order**.
   A slot is free when its energy is `<= 0` at phase start, so a slot freed by a death earlier in
   the tick is free here. If no free slot is left, the remaining winners do not divide.
4. **Install.** Each winner pays `buildCost`, splits, copies and mutates the genome, compiles the
   child's connections, and sets `lineageId` (inherited), `generation` (parent + 1), position and
   occupancy.
5. **Publish.** The tick's births are appended to the **birth list** (`birthParent`,
   `birthChild`, `birthChildCell`, `birthCount`), ascending in parent slot and in child slot.
   It is a plain array, rebuilt every tick; observers read it and write nothing.

A parent divides at most once per tick. The parent's own slot, energy and cell are untouched by
being a parent beyond the §2.4 charge, so fission frees no cell and kills no unit.

## 3. Mutation is keyed by the parent and skip-sampled (replaces contract-v4 §4)

The operators and their order are unchanged (contract-v4 §2): point mutation of inherited bits,
then per-gene deletion, then per-survivor tandem duplication under the soft `MAX_GENES` cap.

- **Key.** Every draw is keyed `(seed, STREAM_MUTATION, parentSlot, birthTick, opIndex)`. A
  parent divides at most once per tick, so keys are unique, and the child genome is known before
  the child's slot is.
- **One monotone counter.** `opIndex` walks every draw of a birth: first weight gap, first
  structure gap, one gap per flip in bit order, one deletion draw per gene, one duplication draw
  per survivor. The duplication draw is consumed even when the cap drops its effect.
- **Skip sampling.** Point mutation does not draw per bit. Weight bits (0 to 15) and structure
  bits (16 to 31) each keep the position of their next flip in their own bit sequence (16 per
  gene), and one draw gives the geometric gap to it: `floor(log(1 - u) / log(1 - rate))` with
  `StrictMath`. Flips are visited in gene-by-gene, bit-by-bit order, and each flip draws the gap to
  the next flip of its own class. Rate 0 never flips; rate 1 flips every bit.
- Which bits flip, and the child length, depend only on the key and the parent's gene count,
  never on gene values, so the length can be computed in the propose step without writing a genome.

The rates are unchanged, so the distribution of mutations is the contract-v4 one; only the mapping
from seed to specific flips differs.

## 4. All kernel randomness follows the run seed

The run seed (`--seed`, `EngineConfig.seed`) keys every kernel stream. Each stream has a domain
tag and a draw is `mix(mix(seed + γ·tag) + γ·ids…)`, so streams never collide:

| Stream | Tag | Keys |
|---|---|---|
| RAND sensor | 1 | unit, tick |
| mutation | 2 | parent slot, birth tick, op index |
| founder placement | 3 | Fisher-Yates draw index |

Two seeds share no kernel randomness. The `noise` inflow source keeps its own `seed` field in its
config (contract-v7); it does not follow the run seed.

## 5. Strict arithmetic

Kernel sources use no `Math.pow`, `exp`, `log`, `sin`, `cos`, `tan` or `fma`: `StrictMath` or exact
operations only (`StrictMathLintTest` fails the build otherwise). Harvest `mass^α` with the
asserted `α = 0.5` is `Math.sqrt(mass)`, which is correctly rounded and so bit-exact; any other
exponent goes through `StrictMath.pow`. No floating-point reduction runs in an unordered or
thread-dependent order.

## 6. Phases have shapes; the pipeline is a list

The tick is an ordered list of phases built at construction. The "locked, append-only pipeline" of
contracts v0 to v8 is retired. Today's list is: `clear`, `propagate`, `evaluate`, `swap`,
`intake`, `inflow`, `cost`, `growth`, `reproduction`, `movement`, `diffusion`, `decay`. Intake
and inflow were one phase before; the order of effects is otherwise the old order. A phase may be
added, split, removed or reordered by editing the list, and the change is a contract change if it
alters the world.

Each phase declares one shape, which fixes what it may touch:

| Shape | May write | Split rule |
|---|---|---|
| per-unit | its own slot, its own cell's fields | fixed chunks over slots |
| per-cell | its own cell, next buffer | fixed chunks over cells |
| claim | proposals into per-unit scratch in parallel; winners in a serial resolve | propose in chunks, resolve in fixed order |
| serial | anything | caller thread, chunk 0 |

## 7. Thread-count independence (the determinism contract)

> Same engine version, same config, same seed: a byte-identical state on any machine and any
> thread count.

How it is held:

- **Fixed chunks.** Chunk `c` of an extent covers `[c·4096, min(extent, (c+1)·4096))`. The chunk
  size is an engine constant; boundaries depend on the extent only, never on the thread count.
- **Ordered totals.** Phases that add to a scalar (sink, inflow, intake, cost) add into their own
  chunk's entry of a partial array; `finish` sums the partials in ascending chunk index. Claim
  resolves run serially in ascending slot order. Scheduling of chunks over threads is free
  (an atomic chunk counter); nothing observable depends on it.
- **State digest.** `StateDigest` folds the scalars, then every saved array chunk by chunk, and
  combines the chunk hashes in ascending chunk index, so it is the same for any thread count. It
  is the `state-digest` report line and is stored in every checkpoint header.
- **Resume equivalence.** Running `0 → T`, or `0 → t`, checkpoint, restore, `t → T`, gives the
  same state at `T`, with different thread counts on either side. A checkpoint stores no thread
  count.

Totals printed by the report (`energy-sink`, `cumulative-inflow`, `intake-total`,
`final-reservoir`, `energy-audit-error`) are sums in chunk order, so they differ from v5 in the
last digits even where the dynamics agree. Conservation (contract-v5 §6) is unchanged: the audit
error stays at floating-point noise.

## 8. Not covered

Checkpoint byte format, run directory layout and the branch rules are specified in
[ADR 0043](../decisions/0043-per-run-config-versioned-checkpoints-seek-branch.md). The report
changes (observer without founder state, digest line) are in [report-v15](../specs/report-v15.md).
