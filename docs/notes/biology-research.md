# Real biology, read as physics

Non-binding. A survey of how real organisms eat, fight, signal, recognise kin and suffer their environment, read for one question: which physical primitives would widen what evolution can discover here, at the least new law each. [biology-atlas.html](biology-atlas.html) is the illustrated version, with live sketches and charts. The cited detail lives in five topic files under [biology-research/](biology-research/):

| File | Covers |
|---|---|
| [bacterial-metabolism.md](biology-research/bacterial-metabolism.md) | uptake kinetics, public goods, respiration vs fermentation, overflow, cross-feeding, maintenance, growth laws, storage, dormancy, corpses, waste |
| [predation-kin-signalling.md](biology-research/predation-kin-signalling.md) | Myxococcus, Bdellovibrio, grazing, T6SS, CDI, colicins, quorum sensing, cheaters, kin and self recognition, Hamilton's rule, necromass |
| [fungi-slime-chemotaxis.md](biology-research/fungi-slime-chemotaxis.md) | temporal vs spatial chemotaxis, Berg and Purcell, hyphal foraging, transport networks, extracellular digestion, het incompatibility, trapping fungi, mycorrhiza, Physarum, Dictyostelium |
| [ants-stigmergy.md](biology-research/ants-stigmergy.md) | trail pheromones, double bridge, negative pheromones, two-sensor following, alarm, nestmate odour, stigmergic building, ant mills, response thresholds, trophallaxis, signal honesty |
| [environmental-stressors.md](biology-research/environmental-stressors.md) | temperature (Arrhenius, performance curves, breadth), pH, osmotic, toxin dose response, resistance cost, detox, oxygen, UV, stress mutagenesis, mutation-rate evolution, crowding, fluctuating worlds |

Each file tags how far every number was verified (full text, abstract, bibliographic only, or unverified). Treat any untagged or "unverified" number as a starting guess for a config value, not a fact.

## The main finding: five reports, one primitive

Every behaviour report ranked the same thing first or second: **a unit puts a molecule into the shared medium, the medium spreads and decays it, and anything standing there reads it or is acted on by it.** Pheromone trails, quorum signals, alarm signals, slime memory, cAMP relay waves, exoenzymes, siderophores and colicins are all that primitive with different diffusion, decay and effect. Meaning is never in the molecule: a trail, a warning and a weapon differ only in physics and in what evolution wires to them.

The engine already has the medium: the chemical table (`ChemicalSpec`: inflow, capacity, diffusion, decay) with chemical 0 as food. What is missing is a way for units to write into it, read more of it, and be affected by it.

## Primitives, ranked by new possibility per new law

| # | Primitive | Real examples | What evolution could discover | Kernel delta (guess) | Needs |
|---|---|---|---|---|---|
| 1 | **Emit**: pay energy to add chemical k to the own cell | ant trails, quorum sensing, slime marker, cAMP | trails, territory, density sensing, alarm, relay waves, signal cheaters | one `EMIT_k` effector per secretable chemical, cost per unit emitted, a sensor per chemical | chemical table |
| 2 | **Spatial sensing**: read the difference between neighbour cells | ant antennae (osmotropotaxis), Dictyostelium, neutrophils | true chemotaxis without the `DELAY` trick, trail following, ant mills | `GRAD_X_k`, `GRAD_Y_k` sensors per chemical | 1 makes it pay off more |
| 3 | **Death releases the body**: a corpse's mass and energy go into a cell field instead of the sink | lysis, necromass, Bacillus cannibalism | scavenging, cannibalism, spite, suicide that feeds kin; prerequisite for predation | death rule routes to a field; audit term moves, total unchanged | nothing |
| 4 | **Chemical effects on bodies**: a chemical changes physics for whoever stands in it | colicins (damage), exoenzymes (catalyse food), waste (inhibit growth) | killers and immune lines, producers and cheaters, rock-paper-scissors, self-limitation | per-chemical effect columns in `ChemicalSpec` (damage with a dose curve, catalysis, inhibition) | 1 |
| 5 | **By-products**: eating chemical A leaks chemical B; B is food for a different harvester | overflow acetate, cross-feeding, syntrophy | division of labour, successions, obligate partners | harvest writes a by-product; a second harvest effector | chemical table |
| 6 | **Rate vs yield**: two ways to burn food, fast and wasteful or slow and efficient | respiration vs fermentation, overflow metabolism | fast-wasteful vs slow-efficient lineages, tragedy of the commons | two harvest effectors with different yield and capacity | 5 gives the waste somewhere to go |
| 7 | **Temperature**: a field that multiplies every metabolic rate (Arrhenius), with a steep collapse above an optimum | thermal niches, Bennett and Lenski 42 C lines | thermal specialists vs generalists, seasonal timing, thermotaxis | one non-food chemical read as a rate multiplier, plus a body trait (next section) | body traits |
| 8 | **Heritable tags**: a body label compared to another unit's label on contact | toxin and immunity pairs, Dienes line, FLO1 greenbeard, nestmate odour | kin discrimination, greenbeards, mimicry arms races | a trait read on contact | body traits, contact physics |
| 9 | **Stress-coupled mutation rate** | SOS mutagenesis, mutator alleles | evolvability as an evolved, stress-dependent trait | mutation rate scaled by a stress signal and a heritable gain | body traits |

Two points worth keeping:

- **Different resources only add possibilities when they differ in chemistry**, not in label. Items 5 and 6 are the grounded version of "several resources": one resource is another's waste, or burns at a different rate and yield. Two foods that behave the same are the same food twice.
- **Fluctuation is free.** The stressors report ranked "change the statistics of any field over time" second, with no new law at all. The inflow DSL already does this for food (pulses, drift, noise); it would apply to any new chemical too.

## What happens to bodies (layer 2)

Things that act on a unit regardless of its decisions, in the order the stressors report ranked them: temperature as a rate multiplier, fluctuating fields, a growth-inhibiting waste field, a toxin field with a Hill dose curve, a stress-coupled mutation rate. Each one only creates new strategies if units can answer it, in one of three ways:

1. **Escape**: sense it and move. Needs a sensor for that field (items 1 and 2).
2. **Endure**: store energy or mass and outlast it. Already possible.
3. **Be built for it**: a heat-tolerant or toxin-resistant body. Not possible today, because the genome encodes only the brain.

In real organisms most tolerance is body, not behaviour, and every tolerance has a cost (thermal breadth costs peak performance; resistance costs growth rate), which is what splits one population into specialists.

## Open question: can the genome shape the body?

One option that needs no change to the gene format (a guess, not decided): **body traits as action nodes that physics reads.** A trait like thermal optimum or toxin resistance becomes an action node. The genome already encodes constants (a `CONST` to action connection with a weight), so a fixed trait is just a fixed weight. Wiring a sensor into the same node makes the trait plastic, which is real acclimation. Physics then charges for it: holding resistance high costs energy every tick, moving the thermal optimum costs energy per step. This keeps every random int a legal gene and adds no semantics: the kernel only reads a number and charges for it.

The alternative is a separate trait section in the genome (genes that set body parameters directly). That is closer to real genetics but changes the encoding, the compiler and mutation.

## Suggested order (a guess)

1. Emit + per-chemical local and gradient sensors (items 1, 2). Biggest jump; reuses diffusion and decay.
2. Death releases the body (item 3). Small, and predation later depends on it.
3. Chemical effects on bodies (item 4), starting with damage and catalysis.
4. By-products and rate vs yield (items 5, 6): the grounded "several resources".
5. Body traits, then temperature, tags and stress mutagenesis (items 7 to 9).

Each step is its own contract and gets judged in the viable reference world ([viable-world.md](viable-world.md)).
