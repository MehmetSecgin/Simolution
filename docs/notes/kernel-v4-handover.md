# Kernel v4 — handover & next steps (variable-length genomes)

Snapshot for whoever picks this up next. Branch `kernel-v4-genome-length` (off the
`kernel-v3-space` tip). **v4 is now BUILT** — `KernelConfig` constants,
`Kernel.mutate` (operation-counter point mutation + indels), `settleReproduction`
(per-gene build cost, build-then-charge), `buildChildGenome`/`finishChild`,
`IndelTest`, baseline regenerated (digest `38e7da02ca42e804`). Binding laws:
`docs/contract/contract-v4.md` + ADR 0026 (both now "implemented"). The
implementation checklist below is kept for the record; **two deviations from these
notes** were made and recorded in ADR 0026:

1. **No separate `Noise` indel stream** (checklist item 2). contract-v4 §4 mandates a
   *single* monotone `opIndex` keyed `(seed, childSlot, birthTick, opIndex)` across all
   of a birth's decisions — point and indel draws then occupy disjoint indices, so a
   second stream is redundant and would contradict the one-key wording. `mutate` reuses
   `Noise.mutationUniform`.
2. **Indel rates calibrated to `1e-3`, not `5e-5`** (checklist item 1 / the "≤
   `MUTATION_RATE_STRUCT`" guidance). At the per-bit struct rate, duplication fired
   ≈100× less often than any gene is point-mutated and dosage growth never expressed.
   Per the tuning discipline (item 7 below: "no length change ever → reset once with an
   ADR"), the rate was reset once; contract-v4's constant guidance was corrected to
   compare per-gene, not against the per-bit constant. Also: the harness now defaults
   `--max-genes` to **2× founder count** (was = founder count, zero headroom — duplication
   could never fire in the mandated baseline).

Original non-binding notes below.

## Where we are

Shipped on `kernel-v3-space` (frozen tip, this branch's base):
- **Kernel v3** — 2D toroidal space + motility (contract-v3, ADR 0018–0020).
- **Observability layer (report-v8, ADR 0023).** Deterministic-replay time-travel:
  `RunManifest` + `ConfigHash`, `Kernel.saveState`/`loadState`, `CheckpointWriter`,
  `Replayer`, decoupled `EventLogWriter`/`MetricsWriter` (kernel stays pure — **no
  event hook**; events from snapshot deltas, mutation bits by child↔parent gene
  diff), `SnapshotDump` + `tools/timetravel.py`. Behind `--observe` (off by default,
  sink-off runs byte-identical). Guarantees asserted in `ObservabilityTest`.
- **ADR 0024** — reproductive death now frees the cell. A parent could spend to
  *exactly* 0 energy in reproduction and die without freeing its cell (only
  `settleCost` freed cells); `settleReproduction` now frees `cellOccupant` on that
  path too. Changed dynamics (baseline digest is now `6aef1ca1a6df1a4e`). Note:
  checkpoints **store** `cellOccupant` rather than rederiving it, because of this.
- **ADR 0025** — parallel-tick feasibility analysis (deferred). Propagate/evaluate/
  diffusion are embarrassingly parallel; the blocker is FP-reduction determinism for
  the two scalar sinks; reproduction/movement are serial by design. SoA + `double→
  float` are the cheaper single-core wins to take first.

This branch (`kernel-v4-genome-length`) adds only: `docs/contract/contract-v4.md`,
`docs/decisions/0026-variable-length-genomes.md`, this handover. **No code yet.**

## What v4 is

Make genome **length** heritable: gene *count* can change via insertion/deletion, so
complexity itself can evolve (the first operator that grows the repertoire instead of
re-weighting a fixed graph). contract-v2 §9/§12 already **provisioned the storage**
(per-slot `MAX_GENES` capacity + live `geneCount`), so this is a pure
mutation-operator addition — zero storage rework.

### Decisions locked (contract-v4 / ADR 0026)
1. **Two operators, duplication-based.** At birth, after point mutation: per-gene
   **deletion** (`INDEL_RATE_DEL`), then per-survivor **tandem duplication**
   (`INDEL_RATE_DUP`, copy appended). Insertion is duplication-only (no random-novel-
   gene op).
2. **`MAX_GENES` = soft physical cap.** A duplication at the cap is dropped (no-op),
   never a halt. Floor 0 genes (empty = inert, legal). No mutation rejected.
3. **Replication cost ∝ length:** `buildCost = BUILD_COST + BUILD_COST_PER_GENE ·
   childGeneCount`, paid once at birth → sink. The anti-bloat pressure (no per-tick
   per-gene tax — that invariant stands).
4. **Operation-counter RNG keying** (`seed, childSlot, birthTick, opIndex`): one
   monotone counter across all mutation decisions in fixed order (point flips →
   deletions → duplications). Replaces the index key that indels would break.

Biology grounding + the modeling regime we picked (bacterial / large-Ne /
streamlining; what's deliberately omitted — frameshift, selfish DNA, WGD) are on
record in contract-v4 "Biology grounding & modeling regime" and ADR 0026 "Biology."

## Implementation checklist (for the next agent)

1. **Constants** in `KernelConfig`: `INDEL_RATE_DUP`, `INDEL_RATE_DEL` (per-gene,
   low — at or below `MUTATION_RATE_STRUCT = 0.00005`; set them near-equal so length
   has no strong intrinsic drift), `BUILD_COST_PER_GENE` (small; tune for livability
   once, never to a target length — ADR 0019 discipline). These feed `configHash`
   automatically (reflection), so old checkpoints correctly refuse replay.
2. **`Noise`**: add an indel stream (a new domain-separated method, like
   `mutationUniform`/`placementUniform`) so duplication/deletion draws never collide
   with bit-flip or placement draws.
3. **`Kernel.mutate(child)`**: rework to the operation-counter scheme — walk one
   `opIndex` across (a) the existing per-bit point-mutation flips on the inherited
   genes, then (b) per-gene deletion draws (compact survivors toward index 0, update
   `geneCount`), then (c) per-survivor duplication draws (append copies up to
   `maxGenes`, update `geneCount`). Order is load-bearing for determinism — match
   contract-v4 §2/§4 exactly. Re-`compileSlot(child)` afterward (already happens in
   `installChild`); it reads `geneCount`, so variable length already flows through.
4. **`settleReproduction`**: replace the fixed `BUILD_COST` with
   `BUILD_COST + BUILD_COST_PER_GENE · geneCount[child]`. Careful: the parent's
   afford check and the `commit`/`dissipated` math must use the new build cost, and
   energy must stay conserved (the per-gene cost flows to `energySink` exactly like
   `BUILD_COST`; ADR 0024's free-cell-on-reproductive-death path must still hold). A
   subtlety: build cost now depends on the *child's* post-indel `geneCount`, which is
   only known after `mutate` — decide whether to compute the child genome first then
   charge (cleanest), and keep the parent-can't-afford → no-birth rule.
5. **Tests**: a conformance test (duplication grows `geneCount`, deletion shrinks it,
   cap clamps, empty genome is inert), a determinism test (same seed → identical
   genomes + digest; **replay-exactness still holds** — `ObservabilityTest` must pass
   unchanged since checkpoints store genes+geneCount), and an energy-conservation test
   (audit ~0 with per-gene build cost).
6. **Baseline** (mandatory): regen `runs/baseline.txt` with `--units 100 --ticks 1000
   --seed 42 --world 100 --out runs/baseline.txt`, diff, **explain** the change
   (indels will shift genomes/population/digest — expected). Commit baseline +
   `.units.csv`.
7. **Docs**: flip contract-v4 + ADR 0026 from "design" to "implemented", update
   AGENTS.md (roadmap item, architecture note, mutation description), and the
   `simolution-*` memory.

## Gotchas / things to watch

- **Replay must survive this.** Checkpoints already serialize per-slot `genes` +
  `geneCount` (report-v8), so variable length round-trips through save/restore for
  free — but the determinism test that ticks-from-0 == seek-from-checkpoint must keep
  passing. Run `ObservabilityTest` after the change.
- **Build cost ordering.** `buildCost` depends on child length, known only post-indel.
  Don't double-charge or let a child be installed the parent can't afford.
- **Harvest-capacity dosage is the headline emergent payoff** — duplicating a HARVEST
  transporter gene raises `harvestConnCount` → higher intake ceiling (ADR 0012). This
  is *why* indels matter; sanity-check it appears in evolved runs (popwiring + the
  units' transporter counts over generations).
- **Substrate is unchanged** (`NodeLayout.TOTAL = 22`); v4 touches mutation only, no
  new nodes. Don't grow the substrate.
- **Mutation-safe invariant**: every gene (duplicated/deleted/flipped) is still a
  legal 32-bit connection — no validation that can reject a gene.
- **Tuning discipline (ADR 0019):** the three new constants are livability settings,
  set once. If a baseline + seed scan shows runaway bloat to the cap (cost too low) or
  no length change ever (cost too high / rates too low), reset *once* with an ADR —
  never nudge toward a target genome length.

## Key files

- `kernel/runtime/Kernel.java` — `mutate` (rework here), `installChild`,
  `settleReproduction` (per-gene build cost), `compileSlot` (already length-aware).
- `kernel/runtime/Noise.java` — add the indel stream.
- `kernel/config/KernelConfig.java` — the three new constants.
- `docs/contract/contract-v4.md`, `docs/decisions/0026-variable-length-genomes.md`.
- `src/test/java/com/simolution/...` — conformance + determinism + energy tests;
  `ObservabilityTest` must keep passing.
