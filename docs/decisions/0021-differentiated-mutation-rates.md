# 0021 — Differentiated mutation rates: weight bits vs structure bits

## Context
Point mutation flipped every one of a gene's 32 bits at a single rate
(`MUTATION_RATE_PER_BIT = 0.0003`). But the 32-bit gene splits cleanly into two
kinds of change (GeneDecoder, AGENTS.md bit layout):

```
[ SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16 ]
  bit 31      24-30     23          16-22     0-15
  \________ structure (rewires the graph) _______/  \__ weight (gain) __/
```

- **Weight bits (0–15)** — `rawGene & 0xFFFF`. Near-continuous, small-effect gain
  tuning of an existing connection. Mostly safe.
- **Structure bits (16–31)** — Src/Dst type + id. Discrete, large, graph-rewiring.
  Mostly disruptive — the likely main driver of error-catastrophe meltdown seen at
  high uniform rates (ADR 0017 found ~1 flip/genome/birth too hot).

Biology backs the split: most heritable variation is quantitative tuning;
topological/regulatory rewiring is rarer and bigger. Owner's idea, specced in the
kernel-v2 handover, dropped from the v3 handover's next-list — recovered here.

## Decision
Replace the single `MUTATION_RATE_PER_BIT` with two constants, chosen by bit
index in `Kernel.mutate()`:

- `MUTATION_RATE_WEIGHT = 0.00025` for bits 0–15 (lively weight annealing)
- `MUTATION_RATE_STRUCT = 0.00005` for bits 16–31 (punctuated structural innovation)

5:1 per-bit ratio. The **expected total flip load is unchanged** from the old
single rate's *realized* load: 16·0.00025 + 16·0.00005 = 0.0048 = 32·0.00015. We
*redistribute* the same mutational pressure — more onto safe gain tuning, less
onto disruptive rewiring — rather than adding or removing variation. So this is an
honest livability reallocation (set once), not an outcome tune.

Note on the 0.00015 figure: `Noise.mutationUniform` returned [0,2) (a normalizer
bug, javadoc said [0,1)), so the old single `MUTATION_RATE_PER_BIT = 0.0003`
realized as `P(flip) = 0.0003/2 = 0.00015` per bit. That bug is fixed in the same
change (normalizer → 2⁻⁵³, so [0,1)); the new constants are picked so the
*realized* per-bit rate is identical before and after (`v/2 < r/2 ⟺ v < r`),
i.e. the baseline is byte-for-byte unchanged and the constants now mean exactly
what they say. Nominal == realized at last.

## Why
Protect topology while keeping weight search fast: smoother evolvability *and*
lower meltdown risk. The v3 substrate change (action space 4→8, ADR 0020) made the
abiogenesis lottery harder and founder circuits thinner, so shielding the
structure of a circuit that *did* ignite, while still letting it tune gains
freely, is worth more now than under v2.

## Implementation
- `KernelConfig`: two constants replace the one (no compat shim per the global
  rule — the only call site, `mutate()`, is updated directly).
- `Kernel.mutate()`: `rate = bit < 16 ? WEIGHT : STRUCT`. Determinism keying
  unchanged — still per-bit-index in the counter RNG `mutationUniform(seed, child,
  tick, index)`, so same genome + seed → identical history.
- contract-v2 §9 and §16 updated to name the split.

## Rejected
- **Keep one rate.** Simpler, but conflates two biologically and dynamically
  distinct mutation classes and leaves topology as exposed as gain.
- **Raise total mutation while splitting.** Would tangle a fidelity tune with the
  reallocation; kept aggregate identical so the baseline diff isolates the
  redistribution effect alone.
- **Per-field rates (separate Src/Dst/weight).** Finer than needed now; the
  weight-vs-structure cut is the one with clear dynamics. Revisit if structural
  search itself needs shaping.
