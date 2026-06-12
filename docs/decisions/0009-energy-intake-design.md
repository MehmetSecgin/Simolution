# 0009 — Energy intake design (locked before implementation)

## Context
Planning the open-system milestone (energy intake). This ADR records the design decisions reached in discussion; the binding spec is docs/contract/contract-v1.md. No code yet.

## Decisions

**Intake before reproduction.** Neither alone yields evolution: intake-without-reproduction sorts a fixed population then freezes; reproduction-without-intake subdivides a shrinking pool until collective death (or forces the kernel to create energy = broken conservation + smuggled "breed!" reward). Reproduction's economy is denominated in energy only intake can replenish, so intake is the floor.

**No semantics, restated:** the kernel/world offer energy uniformly and convert uniformly; all inequality in capture emerges from genome wiring. Same principle as the sun shining equally — capture machinery lives in the organism, not in physics.

**Transduction vs perception** is a binding design line: code raw measurement-at-a-point minimally; let relationships/history/abstraction emerge via internal nodes + DELAY. Never add high-level sensors.

**Location-free → well-mixed global scalars.** No space means all coupling is through global scalars (chemostat, not petri dish). RESOURCE is one global level; spatial gradients are impossible, temporal ones emerge via DELAY.

**RESOURCE pool == RESOURCE sensor** — the competition and the perception are the same variable. A depletable pool is preferred over an infinite field because a constant field is informationally dead (nothing to perceive, so no perception can evolve).

**Sensory acuity is emergent from connection weights, not a coded per-unit value.** The weight on a sensor's outgoing connection already IS the gain knob; acuity is therefore already heritable and evolvable. The decay budget forces specialization (can't afford to wire every sensor strongly). A coded efficiency number was rejected: redundant with weights, and it would have the kernel assigning talent the genome did not earn. This satisfied the owner's "good eyes vs other senses" intent with zero new mechanism.

## Rejected
- Reproduction first (see above).
- Coded per-unit/per-sensor efficiency (redundant with weights, smuggles privilege).
- Infinite flat resource field as the lasting model (informationally dead for perception; acceptable only as a throwaway harvest-mechanism validation).
- High-level/percept sensors (violates transduction-minimal).

## Deferred to implementation (need an ADR when built)
Infinite-field-first vs depletable-pool-first; dedicated HARVEST action vs ACTION_Y-as-mouth (leaning dedicated); intake-vs-cost order within the energy settle.

## Deferred to later milestones
Reproduction + mutation (next); CROWDING/EMIT + quorum sensing; perceptual fidelity/noise (prefer emergent from wiring investment if ever added); genome-encoded efficiency; space.
