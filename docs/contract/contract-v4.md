# Contract v4 — variable-length genomes (indels)

Delta over [contract-v3](contract-v3.md). Adds the **genome-length axis**: the
*number* of genes in a genome can now change heritably, via insertion and deletion
mutation operators, so complexity itself can evolve (gene duplication →
divergence → new function; deletion → streamlining). Supersedes contract-v2 §9
("point mutation now, variable length provisioned") — the provisioned storage
(v2 §12) is now used. No other v3 law changes: geometry, energy, intake, motility,
death, determinism all stand.

Implemented in kernel v4 — `KernelConfig` constants, `Kernel.mutate`
(operation-counter point-mutation + indels) and `Kernel.settleReproduction`
(per-gene build cost), conformance in `IndelTest`. **Binding.**

## 1. Genome length is heritable and bounded

* A gene is **always 32 bits** (v2 §9 unchanged). The axis that varies is the
  *count* of genes, `geneCount[slot]`, never the width of one gene.
* Length is bounded by `MAX_GENES` as a **soft physical cap**, not a halt: an
  insertion that would push a child past `MAX_GENES` simply does not happen (the
  operator is a no-op for that draw), exactly as a birth with no free Moore
  neighbour does not happen (v3 §5). There is **no run halt** on hitting the cap —
  v2's halt-on-full was already retired in v3, and the same physical-constraint
  philosophy applies here.
* The floor is **0 genes**. A genome may delete down to empty; a 0-gene unit is
  legal, inert (no connections, no harvest, no motility), and dies of basal cost
  like any silent unit. No minimum is enforced — a lethal deletion is just a
  lethal mutation, never a rejected one.

## 2. Three mutation operators, applied at birth

A child's genome is built from the parent's by, **in this fixed order**:

1. **Copy** the parent's `geneCount` genes into the child's slot region (unchanged).
2. **Point mutation** (v2 §9 / ADR 0021, unchanged): each bit of each *inherited*
   gene flips with its class-split probability (`MUTATION_RATE_WEIGHT` /
   `MUTATION_RATE_STRUCT`). Operates on the copied genes at their original count.
3. **Deletion**: each gene position is removed with probability `INDEL_RATE_DEL`.
   Removals are resolved left-to-right; survivors compact toward index 0 (no holes).
4. **Insertion (tandem duplication)**: each *surviving* gene position, with
   probability `INDEL_RATE_DUP`, appends a copy of that gene to the end, subject to
   the `MAX_GENES` cap (a duplication at the cap is dropped). The copy is a faithful
   duplicate; divergence comes from point mutation in *later* generations (Ohno's
   duplication→neofunctionalization), so no separate "randomize the copy" step is
   needed.

Insertion is **duplication-only** — there is no "insert a random novel gene"
operator. De-novo novelty already arrives through point mutation (every 32-bit
value is a legal gene, v2 §9), and a random insert is behaviourally
"duplicate-then-heavily-mutate", which the dup + point-mutation pair already
spans. Keeping one insertion operator keeps the law minimal.

The encoding stays **mutation-safe**: every gene (inherited, duplicated, or
bit-flipped) is a legal connection; no operator can produce a rejectable genome.
Harvest capacity (ADR 0012, ∝ transporter count) and motility wiring can now grow
**open-endedly** within the cap, because duplications can add transporters — eating
and moving capacity become unbounded-but-physically-capped heritable traits.

## 3. Replication cost scales with genome length (anti-bloat)

Because no per-tick cost scales with connection count (a deliberate v0/v1 invariant
— cost lives on what a unit *does* and *holds*, not what it *carries*), silent genes
are nearly free to keep. Without a counter-pressure, neutral indel drift would push
every genome to `MAX_GENES`. The pressure (pre-committed in contract-v2 §9/§15) is a
**replication cost proportional to the child's genome length**, paid once at birth,
not per tick:

```
buildCost(child) = BUILD_COST + BUILD_COST_PER_GENE · childGeneCount
```

charged to the sink like the fixed `BUILD_COST` (v2 §11 / ADR 0016). This keeps the
Pirt-shaped reproduction bill (fixed + proportional) and adds size to the
proportional part: a bigger genome costs more to build, so carrying genes you don't
use is selected against, and the `MAX_GENES` cap rarely binds. It is a **build-time**
cost, like real biosynthesis scaling with genome size — it does **not** reintroduce
a per-tick per-gene metabolic tax (that invariant stands). A parent that cannot
afford `buildCost(child)` on top of its reproduction commitment simply does not
reproduce that tick (an energy constraint, not a denied birth — v3 §5 unchanged).

## 4. Determinism — operation-counter keying

Mutation draws stay deterministic and replayable. Because indels shift gene indices,
the per-bit `geneIndex` key (v2 §9) is replaced by a **monotone operation counter**:
a single index walked across every mutation decision of a birth, in the fixed order
of §2 (all point-mutation bit draws, then all deletion draws, then all duplication
draws), keyed `(seed, childSlot, birthTick, opIndex)` and domain-separated from the
`RAND` sensor and founder placement. At most one birth per slot per tick, so the key
is unique. Same seed → identical genomes, always; replay (report-v8) reproduces them
exactly.

Because the counter is **one monotone index across all of a birth's decisions**, point
and indel draws occupy disjoint `opIndex` ranges and can never collide — so the
implementation reuses the existing `Noise.mutationUniform(seed, slot, tick, opIndex)`
stream for the indel draws rather than adding a separate stream. (The handover note
suggested a distinct indel stream; that is unnecessary given the single-counter key
this section mandates, and a second stream would contradict the one-key wording above.)
The duplication draw is consumed for every survivor even when the `MAX_GENES` cap drops
its effect, so a binding cap never desynchronises the counter.

## 5. Conservation and audit — unchanged

Energy is still conserved. The new per-gene build cost flows to the sink exactly
like `BUILD_COST`, so the closed-system audit (v3 §7) stays exact: the
`energy-audit-error` line remains ~0 (FP noise only). No energy is created by
growing a genome — only the build cost is moved to the sink.

## 6. What does not change

Gene bit-width and layout; the mutation-safe encoding; the 9-phase tick pipeline
(indels happen inside the existing phase-7 birth, in `mutate`/`installChild` — no
new phase); geometry, intake, motility, diffusion, death-is-derived; determinism.
`MAX_GENES` remains a memory/scale anchor (headroom + safety cap), not a tuned
complexity ceiling — the replication cost (§3), not the cap, is what bounds length.

## New constants (world-harshness, set once, never tuned to an outcome)

* `INDEL_RATE_DUP = 0.001` — **per-gene** tandem-duplication probability at birth. A
  whole-gene structural event, so it is rarer per gene than point mutation, which
  changes a gene with probability ≈ Σ of its per-bit rates ≈ 5·10⁻³ (16 weight bits
  at `MUTATION_RATE_WEIGHT` + 16 structure bits at `MUTATION_RATE_STRUCT`). **The
  meaningful comparison is per-gene, not against the raw `MUTATION_RATE_STRUCT`
  constant** — that constant is a *per-bit* rate (≈5·10⁻⁵), and an indel rate set
  to it would fire ≈100× less often than any single gene is point-mutated, so genome
  growth would essentially never express in a run of practical length (an earlier
  draft of this section made that error; see ADR 0026 "Calibration").
* `INDEL_RATE_DEL = 0.001` — per-gene deletion probability at birth. Set equal to
  `INDEL_RATE_DUP` so length has no intrinsic mutational drift up or down; selection
  (via §3) sets the equilibrium length.
* `BUILD_COST_PER_GENE = 0.25` — replication cost per gene of the child's genome (§3).
  A 32-gene founder pays `buildCost = 18` vs the fixed `BUILD_COST = 10`, a clear
  length gradient that selects against unused genes without collapsing reproduction.

All three are set once to make an evolvable world, never tuned to hit a target
genome length, complexity, or population. `MAX_GENES` is a per-run capacity, not a
kernel constant; the harness defaults it to **2× the founder gene count** (so the
canonical baseline has headroom for duplication to express) and `--max-genes`
overrides it.

## Biology grounding & modeling regime (non-normative, but assumptions on record)

The operators mirror real mechanisms, deliberately at the **gene level**:

* **Tandem duplication** (§2.4) is the in-silico analogue of **unequal crossing-over**
  (non-allelic homologous recombination): misaligned repeats yield one copy that
  gains a tandem duplicate and (reciprocally) one that loses it. Duplication is the
  only way to acquire a *new* gene without losing the old one — Ohno's
  "innovation without abandonment" (*Evolution by Gene Duplication*, 1970).
* **Divergence is deferred to later point mutation**, reproducing the real fate of a
  redundant duplicate: freed from purifying selection, it drifts to a pseudogene
  (nonfunctionalization, the common fate), a new function (neofunctionalization), or
  a split of the ancestral role (subfunctionalization / DDC). We code none of these
  outcomes — they **emerge** from duplication + point mutation + selection.
* **Gene dosage emerges for free**: duplicating a HARVEST transporter raises harvest
  capacity (ADR 0012 counts transporters), so copy-number → phenotype, as with real
  rRNA arrays or amylase copy number.
* **Deletion** is the streamlining force — most duplicates decay and are purged.
* **The length cost (§3) places us in the large-Ne, replication-limited regime** —
  bacterium-like. There, DNA's replication cost is felt and selection keeps genomes
  **compact and efficient** (deletional bias, endosymbiont reduction). We are *not*
  modeling the small-Ne eukaryotic regime where selection is too weak to purge
  slightly-deleterious DNA and genomes bloat (Lynch's mutational-hazard / C-value
  paradox). Expect lean, every-gene-earns-its-keep genomes, not junk-laden ones.

Deliberate omissions (each a possible future knob, not a gap to fix now):

* **No sub-gene frameshift.** A gene is an atomic 32-bit unit with no reading frame,
  so we model whole-gene (segmental) indels only — not the single-base insertions/
  deletions that frameshift and scramble everything downstream (the most common, most
  destructive small indel in nature). Our indels are "clean."
* **No selfish DNA.** Duplication fires at a fixed host-neutral rate; there are no
  transposable elements that self-replicate faster than the host can purge — a major
  real driver of genome size. (A gene that duplicates *itself* would be a transposon;
  a future addition.)
* **No whole-genome duplication.**
* **Symmetric indel rates** (`INDEL_RATE_DUP ≈ INDEL_RATE_DEL`): length is set by
  **selection** (via §3), not by a baked-in mutational deletion bias. Real lineages
  usually carry a net deletion bias; a slight asymmetry could be added later if we
  want bias-driven (not purely selection-driven) size dynamics.
