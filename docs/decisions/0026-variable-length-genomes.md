# 0026 — Variable-length genomes: indel operators, soft cap, length-cost

## Context
Point mutation (ADR 0021) leaves gene *count* fixed per lineage — complexity cannot
grow. contract-v2 §9/§15 provisioned the storage (per-slot `MAX_GENES` capacity +
live `geneCount`) and delegated three choices to "the indel ADR": the `MAX_GENES`
cap behaviour, a replication-cost-∝-length anti-bloat term, and RNG re-keying.
This milestone (kernel-v4) makes those choices and adds insertion/deletion so genome
length evolves. Full laws in [contract-v4](../contract/contract-v4.md). Design only.

## Decision
1. **Two indel operators, duplication-based.** At birth, after the existing point
   mutation: each gene position may be **deleted** (`INDEL_RATE_DEL`), then each
   survivor may spawn a **tandem duplicate** appended at the end (`INDEL_RATE_DUP`).
   Insertion is duplication-only — no "random novel gene" operator (point mutation
   already supplies de-novo novelty; a random insert ≈ duplicate-then-mutate).
2. **`MAX_GENES` is a soft physical cap, not a halt.** A duplication that would
   exceed it is dropped (no-op), like a birth with no free neighbour (v3 §5). Floor
   is 0 genes (empty genome legal + inert). No mutation is ever rejected.
3. **Replication cost ∝ child genome length:** `buildCost = BUILD_COST +
   BUILD_COST_PER_GENE · childGeneCount`, paid once at birth, to the sink. This is
   the pre-committed anti-bloat pressure (contract-v2 §9/§15).
4. **Operation-counter RNG keying.** A single monotone counter walks every mutation
   decision in fixed order (point flips → deletions → duplications), keyed
   `(seed, childSlot, birthTick, opIndex)`. Replaces the index-based `geneIndex`
   key, which indels would break.

## Why
- **Duplication is the realistic complexity engine** (Ohno): copy a working gene,
  let point mutation diverge the copy across generations into new function. It also
  lets transporter count — and thus harvest capacity (ADR 0012) — and motility
  wiring grow open-endedly, which a fixed count forbade.
- **Soft cap** keeps the v3 "physical constraint, never a run halt" philosophy and
  avoids a special halt path; with §3 in place the cap rarely binds anyway.
- **Build-time length cost, not per-tick.** It bounds neutral bloat without
  reintroducing a per-tick per-gene metabolic tax — that invariant (cost on what a
  unit does/holds, not what it carries; ADR 0006/0013) stands. Biosynthesis scaling
  with genome size is biologically apt and keeps the Pirt-shaped (fixed +
  proportional) reproduction bill.
- **Operation-counter** is the minimal change that stays deterministic once indices
  shift; same seed → identical genomes, and report-v8 replay still reproduces them.

## Biology
Operators are gene-level analogues of real mechanisms: tandem duplication ≈ unequal
crossing-over; divergence-by-later-point-mutation ≈ Ohno neofunctionalization (1970);
deletion ≈ streamlining. Gene dosage (copy a transporter → more harvest capacity)
emerges from the count-based capacity (ADR 0012). The length cost (§3) puts the world
in the **large-Ne, replication-limited (bacterial) regime** — compact, efficient
genomes — explicitly *not* the small-Ne eukaryotic bloat regime (Lynch). Full
grounding + omissions in contract-v4 "Biology grounding & modeling regime."

## Rejected
- **Random-novel-gene insertion** — redundant with duplicate + point mutation, and
  adds a second insertion law for no new reachable behaviour.
- **`MAX_GENES` halt-on-exceed** (v2's old halt-on-full analogue) — contradicts v3's
  no-halt philosophy; a dropped duplication is the physical analogue.
- **Per-tick cost ∝ gene count** (the obvious anti-bloat knob) — breaks the
  long-standing "no structural per-tick tax" invariant; would also make silent junk
  expensive, which the model deliberately keeps cheap. Build-time cost instead.
- **Length-coupled point-mutation rate / explicit length ceiling tuned to a target**
  — smuggles a complexity target into the laws; rejected as semantics.
- **Sub-gene frameshift indels, selfish-DNA/transposons, whole-genome duplication,
  baked-in deletion bias** — deferred (see contract-v4 omissions), not rejected on
  principle; each is a clean future knob, kept out to keep this milestone minimal.

## Calibration (set once, per the §3 tuning discipline)
- `INDEL_RATE_DUP = INDEL_RATE_DEL = 0.001`, `BUILD_COST_PER_GENE = 0.25`.
- The design note said "≤ `MUTATION_RATE_STRUCT`" (5·10⁻⁵). That conflates a
  *per-gene* indel rate with a *per-bit* point-mutation rate: at 5·10⁻⁵ per gene,
  duplication fires ≈100× less often than any gene is point-mutated, so dosage growth
  never expressed (first baseline + IndelTest: deletion fired, duplication never did).
  Per contract-v4's own tuning rule ("no length change ever → rates too low → reset
  once with an ADR, never toward a target"), the rate was reset **once** to 10⁻³ —
  rarer per gene than point mutation (≈5·10⁻³/gene) but expressive. Not tuned to a
  target length; the equilibrium remains selection-set via §3. contract-v4's constant
  guidance was corrected to match.

## Implementation deviations from the design notes
1. **No separate `Noise` indel stream.** contract-v4 §4 mandates a *single* monotone
   `opIndex` keyed `(seed, childSlot, birthTick, opIndex)` across all of a birth's
   decisions. That makes point and indel draws occupy disjoint indices — collision is
   impossible — so the implementation reuses `Noise.mutationUniform` rather than adding
   a stream (the handover suggested one; it is redundant and would contradict §4's
   single-key wording).
2. **Default `MAX_GENES` = 2× founder gene count.** The harness previously defaulted
   `--max-genes` to the founder count, leaving zero headroom — duplication could never
   fire in the mandated baseline. Defaulting to double gives the canonical run room to
   express growth (≈2× the per-slot `genes`/`connections` capacity; linear in units,
   user-overridable via `--max-genes`). Rejected: keeping the no-headroom default (the
   milestone's headline payoff would be invisible in the baseline).

## Status
Implemented (kernel v4). `INDEL_RATE_DUP`/`INDEL_RATE_DEL`/`BUILD_COST_PER_GENE` in
`KernelConfig`; operation-counter point-mutation + indels in `Kernel.mutate`; per-gene
build cost (build-then-charge) in `settleReproduction`; `installChild` split into
`buildChildGenome` + `finishChild`; `IndelTest` (bounds, grow/shrink, empty-inert,
conservation, determinism); `ObservabilityTest` replay-exactness unchanged. Baseline
regenerated (digest `38e7da02ca42e804`); live genome length spans 29–33 in the
baseline, both directions in IndelTest. Roadmap: biomass / Pirt size-physics follows.
