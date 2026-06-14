# biosim4 — lineage and the deliberate fork

Non-binding context. Records where Simolution comes from and, precisely, where it
parts ways. The binding consequence at the node layer is [ADR 0027](../decisions/0027-node-honesty-doctrine.md);
the project-wide laws are in the contracts. This note is the "why we exist relative
to biosim4" record.

## What biosim4 is

David R. Miller's [biosim4](https://github.com/davidrmiller/biosim4) — the simulator
behind the YouTube video *"I programmed some creatures. They evolved."* Creatures =
genome → neural-net brain, living on a 2D grid, evolving over discrete generations.
It is the project that motivated Simolution; the gene encoding is inherited from it
verbatim.

## Inherited (the shared mechanics)

These are taken from biosim4 with little or no change — the parts that were right:

- **Gene bit layout, identical.** biosim4 `Gene`:
  `sourceType:1 | sourceNum:7 | sinkType:1 | sinkNum:7 | int16 weight` = 32 bits.
  Simolution: `[SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]`. Same fields,
  same widths.
- **Weight range, same span.** biosim4 `weightAsFloat() = weight / 8192.0` → ≈ ±4.0.
  Simolution `int16 × (4.0 / 32767)` → ±4.0 exactly. Different divisor, same window.
- **Mutation-safe encoding.** IDs wrap modulo the node-type count, so every 32-bit int
  is a legal gene; no mutation can be rejected. (biosim4's bit-flip mutation relies on
  the same property.)
- **Brain shape.** A directed weighted graph: sensors → internal nodes → actions,
  compiled from the genome once, not decoded per step. Recurrent memory (biosim4 latches
  neuron output across steps; Simolution uses an explicit `DELAY` internal — same effect).
- **Variable-length genomes via indel.** biosim4 has insert/delete operators (off by
  default, `geneInsertionDeletionRate = 0.0`). Simolution made heritable length the
  centerpiece of kernel-v4 (contract-v4, ADR 0026).

## The fork, layer 1 — selection

biosim4's engine is a **generational genetic algorithm with a designer-chosen fitness
function** (the `CHALLENGE_*` switch — ~20 of them, all spatial/behavioral: survive if
on the right half, inside a circle, near a corner, touching a wall, forming an isolated
pair, …). Each generation: every creature lives a fixed number of steps, the criterion
culls them, survivors reproduce (sexual, multi-parent, fitness-weighted), all die, repeat.

That criterion *is* meaning injected into the engine — exactly the "semantics in the
kernel" Simolution forbids (AGENTS.md invariant: *the kernel never interprets, rewards,
or privileges behavior*). Simolution removes it entirely:

| | biosim4 | Simolution |
|---|---|---|
| Selection | designer fitness (`CHALLENGE_*`) | none — survival is `energy ≤ 0` |
| Time | discrete generations, synchronous cull | continuous, overlapping lives |
| Reproduction | gen-end, fitness-weighted, sexual | per-tick `REPRODUCE`, energy-gated, asexual |
| Death | end-of-generation judgment | bankruptcy, any tick |
| Metabolism | none (no energy; cannot starve) | intake / decay / cost / aging + conservation audit |
| World | occupancy + pheromone + barriers | resource field + inflow + diffusion |

biosim4 = evolution given a *target*. Simolution = evolution given only a *world*.

## The fork, layer 2 — nodes

The subtler smuggle, and the reason biosim4 behavior is legible so fast: its **sensors
and actions are pre-built organs with cognition already wired in**. The genome only
learns how to connect organs; the organ does the semantic work. Examples: `LONGPROBE_*`
is raycast vision; `GENETIC_SIM_FWD` is a kin oracle; every `*_FWD` / `*_LR` sensor is an
egocentric projection that presupposes a maintained heading; `OSC1` is a free tunable
clock; `SET_RESPONSIVENESS` is a global behavioral gain knob.

Simolution sensors are **raw physical taps** — `CONST`, `RAND`, `LOCAL_RESOURCE`
(own cell only), `SELF_ENERGY`. Four scalars: a constant, noise, food-here, energy-in-me.
No position, no edge, no kin, no density, no vision, no clock. Anything directional,
timed, or social must be *built* from these plus `DELAY`, paying genes and energy. The
binding rule that keeps it that way is [ADR 0027](../decisions/0027-node-honesty-doctrine.md).

## The complexity-budget inversion

Both projects spend a finite "richness" budget; they spend it in opposite places.

- **biosim4** — rich *perception* (smart organs) + uniform *compute* (identical `tanh`
  neurons, `maxNumberNeurons = 5`) + external *goal*.
- **Simolution** — minimal *perception* (4 raw taps) + typed *compute* (`ADD`, `MUL`,
  `CLAMP`, `DELAY`, `THRESH`) + *no goal*.

Miller puts the intelligence in the eyes and lets a near-trivial brain route between
pre-intelligent organs. Simolution starves the senses, enriches the math, and makes
meaning something the graph has to discover.

## What we may still borrow (roadmap item 11)

Some biosim4 capabilities are genuinely wanted — quorum, signaling, kin effects. They
must enter in their *honest* form (see ADR 0027 for the full guard):

- Density → sense raw signal concentration in the **own cell only**; directionality must
  be evolved from movement + `DELAY`, never handed over as `*_FWD/_LR`.
- `EMIT` → emitting a signal **costs energy** (biosim4's is free — sets the neighborhood
  to 255 for nothing).
- Quorum → emerges from many units each sensing a local concentration, never a
  "neighbor-count" readout.
- Kin → a heritable **emitted tag** others can sense (greenbeard), not a `GENETIC_SIM`
  relatedness oracle.

## The trade we accept

biosim4's loaded organs and explicit goal are *why* it shows complex behavior in a few
hundred generations — watchable, demoable, viral. Purity is not free: Simolution pays in
time and legibility — emergence may take far longer, or stall. He optimized for *visible*;
we optimize for *real*. That is the whole bet.

## See also

- [ADR 0027 — Node-honesty doctrine](../decisions/0027-node-honesty-doctrine.md) (binding)
- [contract-v3](../contract/contract-v3.md) — space + motility + local sensing
- [contract-v4](../contract/contract-v4.md) — variable-length genomes
- [story/biosim4-tribute.md](../story/biosim4-tribute.md) — the human version
- [story/journey.md](../story/journey.md) — the factual timeline
