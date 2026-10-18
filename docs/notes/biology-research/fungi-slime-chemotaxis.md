# Fungi, slime molds and chemical following: primitives for new Simolution physics

Scope: real mechanisms, stated as physical or chemical primitives. No simulator design. Every DOI below was checked against Europe PMC, Crossref, PubMed Central or a publisher page during this session. Where a number comes from a secondary summary, or I could not read the full text, the bullet says so. Section "Unverified or secondary" near the end lists them again.

Conventions: "units + medium" means separate cells that interact only through a shared field. "Needs connection" means physical fusion, tubes, a body or a pairing.

## 1. E. coli temporal chemotaxis (run and tumble, adaptation)

- **Primitive:** A cell with no useful size for spatial comparison senses receptor occupancy at one point and compares it with its own recent past. Swimming alternates straight runs with short tumbles that pick a random new heading. A downhill-to-uphill change in occupancy lowers the tumble rate; the reverse raises it. The comparison signal is the difference between current occupancy and a slowly tracking average. That average is set by receptor methylation: an integral-feedback loop that restores the output to baseline after any step. Result: the cell reports change, not level.
- **Numbers:**
  - Wild-type cells weight the most recent 1 s positively and the preceding 3 s negatively, so the comparison window reaches about 4 s back (Segall, Block, Berg 1986, abstract). Methylation-defective cheR cheB mutants lose the short comparison and adapt with a decay time over 30 s. cheZ mutants weight at least 40 s into the past (same abstract).
  - Gain: a change in occupancy of one receptor molecule gives a significant response (Segall 1986 abstract). Methylation amplifies sensitivity: the half-maximal attractant concentration equals the receptor dissociation constant in cheR cheB mutants but is 35 times smaller in wild type (Sourjik and Berg 2002, abstract).
  - Adaptation precision does not depend on protein concentrations, even though adaptation time and steady-state output do (Alon et al. 1999, abstract).
  - Cells respond to the gradient of the logarithm of concentration (drift velocity rises with grad[L]/[L]); the mechanism is the adaptation kinetics, not ligand occupancy (Kalinin et al. 2009, abstract). Earlier work found responses to ratios of concentration over the middle of the response range (Mesibov, Ordal, Adler 1973, abstract). Fold-change detection makes the search path independent of source strength (Shoval et al. 2010, abstract).
  - Detection floor quoted from the literature: responses to attractant (aspartate) changes as low as 3.2 nM; a fast swimmer's motion-induced apparent gradient can exceed the true gradient by more than 100 times (both statements as reported in Endres and Wingreen 2008, full text, not primary measurements there).
  - Run about 1 s, tumble about 0.1 s: from a secondary description of Berg and Brown 1972; I could not retrieve that abstract text. Swim speed: not verified here.
- **Trade-off:** Cheap in structure (one sensing point, one memory variable) but needs motion to create the signal, so it is blind while stationary. Short memory (about 4 s) tracks fast gradients but is noisy; a long memory averages noise but lags. Adaptation buys scale invariance (works from nM to mM) at the cost of reporting nothing about absolute level. Below roughly 1 micrometre, locomotion yields no benefit because Brownian rotation scrambles heading faster than a cell can measure it (section 2).
- **Needs connection:** No. Units + medium.
- **Sources:**
  - Berg HC, Brown DA. Nature 1972;239:500-504. https://doi.org/10.1038/239500a0 (existence and citation verified; abstract not retrieved)
  - Macnab RM, Koshland DE. PNAS 1972;69:2509-2512. https://doi.org/10.1073/pnas.69.9.2509 (verified: bacteria detect a temporal change experienced while moving; both increases and decreases modulate tumbling)
  - Block SM, Segall JE, Berg HC. J Bacteriol 1983;154:312-323. https://doi.org/10.1128/jb.154.1.312-323.1983 (verified: motors respond to the difference between current occupancy and occupancy averaged over the recent past)
  - Segall JE, Block SM, Berg HC. PNAS 1986;83:8987-8991. https://doi.org/10.1073/pnas.83.23.8987
  - Alon U, Surette MG, Barkai N, Leibler S. Nature 1999;397:168-171. https://doi.org/10.1038/16483
  - Sourjik V, Berg HC. PNAS 2002;99:123-127. https://doi.org/10.1073/pnas.011589998
  - Kalinin YV, Jiang L, Tu Y, Wu M. Biophys J 2009;96:2439-2448. https://doi.org/10.1016/j.bpj.2008.10.027
  - Mesibov R, Ordal GW, Adler J. J Gen Physiol 1973;62:203-223. https://doi.org/10.1085/jgp.62.2.203
  - Shoval O et al. PNAS 2010;107:15995-16000. https://doi.org/10.1073/pnas.1002352107

## 2. Spatial gradient sensing in eukaryotes and the physical limits (Berg and Purcell)

- **Primitive:** A cell large enough to compare concentration across its own body reads the difference between a front and a back sensor at one moment, so it needs no motion to get a direction. The limit is counting noise: the cell can only count molecules that arrive at its receptors by diffusion in time T. Berg and Purcell showed the relative error in a concentration estimate falls as 1/sqrt(D a c T) (D diffusion constant, a cell radius, c concentration, T averaging time), and that only a small fraction of the surface needs to be receptive for near-optimal performance. Endres and Wingreen extended this to gradients, treating the cell as a perfect absorber (consumes what it counts) or a perfect monitor (counts without removing). Eukaryotic amoebae and neutrophils add a local-excitation, global-inhibition circuit (PI3K activator, PTEN-like inactivator) that adapts perfectly to uniform changes and persists only in a gradient.
- **Numbers:**
  - Dictyostelium senses a concentration difference of 1 to 5 percent across the cell, which is a receptor-occupancy difference of about five receptors (Endres and Wingreen 2008, full text). Neutrophils detect a 1 percent difference in peptide concentration in optimal gradients (Zigmond 1977, abstract).
  - Dictyostelium: no response below 1e-3 nM/micrometre of cAMP; response plateau near 1e-1 nM/micrometre; directionality lost above 10 nM/micrometre; front-to-back occupancy difference in the optimal regime is about 100 molecules (Song et al. 2006, abstract). Chemotaxis was seen out to 700 micrometres from a 1e-4 M cAMP pipette, which corresponds to a mean concentration of about 7 nM and a gradient of about 0.01 nM/micrometre (Endres and Wingreen, citing van Haastert and Postma).
  - Cell radius a = 5 micrometres and D = 300 square micrometres per second for cAMP are the values Endres and Wingreen use. Fitted averaging time for Dictyostelium is about 3.2 s (absorbing model) or 27.5 s (monitoring model).
  - Table 1 of Endres and Wingreen (as reconstructed by my fetch tool, formulas are images): relative variance of concentration for a monitoring sphere 3/(5 pi D a c T); for an absorbing sphere 1/(4 pi D a c T); gradient estimate for the absorber 1/(4 pi D a c T) in units of (c/a)^2, independent of the true gradient; monitoring sphere 15/(7 pi D a c T). The absorber beats the monitor by 12/5 for concentration and 60/7 (about 8.6) for gradients.
  - My own arithmetic, check before use: detecting a 1 percent front-back difference needs D a c T of roughly 3200 counts. At 7 nM (about 4 molecules per cubic micrometre) with the numbers above, D a c is about 6300 per second, so a few seconds of averaging suffices.
  - Size floor: chemical-gradient orientation by temporal comparison is useless below about 0.58 to 0.65 micrometres diameter; the smallest motile bacterial genus is about 0.8 micrometres long (Dusenbery 1997). Dusenbery notes that spatial comparison depends on different powers of the parameters than temporal comparison, so spatial sensors could be smaller; the abstract does not give a number for that case.
  - Fuller et al. 2010: Dictyostelium responds to both gradient steepness and local concentration; at shallow gradients and low concentration the limit is external (finite receptor number), at steeper gradients and higher concentration internal processing is suboptimal (abstract).
- **Trade-off:** Spatial sensing gives direction instantly, but sensor separation (body size) and integration time are both needed to beat counting noise. Larger cells sense shallower gradients but pay more maintenance and move more slowly. A cell that consumes the molecules it counts senses better (absorber), which ties sensing to consumption. A moving cell sees an apparent gradient: Endres and Wingreen estimate a Dictyostelium cell moving at 0.2 micrometres per second at 7 nM would perceive an apparent gradient about half the real one.
- **Needs connection:** No. One cell, but the cell must be larger than its sensing resolution (sensor sites separated in space).
- **Sources:**
  - Berg HC, Purcell EM. Biophys J 1977;20:193-219. https://doi.org/10.1016/S0006-3495(77)85544-6 (citation and abstract-level claims verified via secondary index; equation prefactors come from Endres and Wingreen)
  - Zigmond SH. J Cell Biol 1977;75:606-616. https://doi.org/10.1083/jcb.75.2.606
  - Dusenbery DB. PNAS 1997;94:10949-10954. https://doi.org/10.1073/pnas.94.20.10949
  - Levchenko A, Iglesias PA. Biophys J 2002. https://doi.org/10.1016/s0006-3495(02)75373-3
  - Song L et al. Eur J Cell Biol 2006;85:981-989. https://doi.org/10.1016/j.ejcb.2006.01.012
  - Endres RG, Wingreen NS. PNAS 2008;105:15749-15754. https://doi.org/10.1073/pnas.0804688105
  - Fuller D et al. PNAS 2010;107:9656-9659. https://doi.org/10.1073/pnas.0911178107
  - Parent CA, Devreotes PN. Science 1999;284:765-770. https://doi.org/10.1126/science.284.5415.765 (title and DOI verified; abstract not read)

## 3. Self-generated gradients (attractant depletion)

- **Primitive:** Cells that degrade or consume an attractant create their own gradient: the local sink lowers concentration at the cell, diffusion refills from outside, and the resulting slope points outward through the cell's surroundings. The gradient moves with the cells. A shared medium plus a per-cell sink term is the whole mechanism.
- **Numbers:** Self-generated gradients let a leading cell population move directionally while cells behind are undirected, and the leading population is denser than followers, especially at high attractant concentration (Tweedy et al. 2016, abstract). Single cells solved mazes and chose live channels over dead ends; accuracy was set by attractant diffusivity, cell speed and path complexity (Tweedy et al. 2020, abstract). Neutrophils at high density orient away from the centre of the population because the cells inactivate the peptide (Zigmond 1977, abstract). Dictyostelium degrades cAMP with phosphodiesterase, which is why the absorbing-sphere model fits its data (Endres and Wingreen 2008, abstract).
- **Trade-off:** Works over distances where a fixed external gradient would flatten out, and it is robust to attractant concentration. Cost: it needs a crowd (the leading population is denser), it consumes the signal, and followers are undirected.
- **Needs connection:** No. Units + medium.
- **Sources:**
  - Tweedy L, Knecht DA, Mackay GM, Insall RH. PLoS Biol 2016;14:e1002404. https://doi.org/10.1371/journal.pbio.1002404
  - Tweedy L et al. Science 2020;369:eaay9792. https://doi.org/10.1126/science.aay9792
  - Zigmond 1977 and Endres and Wingreen 2008 (above)

## 4. Fungal hyphal tip growth and foraging (exploration versus exploitation)

- **Primitive:** A hypha extends only at its tip. Internal hydrostatic pressure (turgor) drives expansion; calcium gradients regulate tip growth; secretory vesicles are carried to the tip along the cytoskeleton. Osmotically driven water uptake away from the tip creates bulk cytoplasmic flow from the old mycelium toward the growing edge. Foraging adds two decisions: where to branch and whether to commit biomass to a region. Branching and fusion turn the tree into a network.
- **Numbers:**
  - Measured in 1 micrometre wide microfluidic channels: Aspergillus nidulans hyphal elongation under 50 micrometres per hour in the device, with species values of 37 +/- 15 and 84 +/- 37 micrometres per hour for slow species; Neurospora crassa about 200 to 245 micrometres per hour before and in the channels (Fukuda et al. 2021, full text; conditions are confined growth in a chip, so free-growth rates in agar are higher and I did not verify them).
  - Faster elongation correlates with growth failure and loss of polarity in confinement: a trade-off between plasticity and velocity (Fukuda et al. 2021, abstract).
  - Phanerochaete velutina colonies spontaneously shift to asymmetric growth even without added resources, and with a cellulose resource they show highly localised growth, often at the expense of growth elsewhere in the colony, with marked accumulation of labelled amino acid in the resource sector (Tlalka et al. 2008, abstract).
  - Cord-forming species differ in foraging strategy by resource quantity and quality, the presence of other organisms and microclimate; their morphology follows fractal geometry; they show reallocation of biomass and nutrients when new resources are found (Boddy 1999, abstract).
- **Trade-off:** Fast, thin, sparse exploration finds resources early but is fragile and poorly connected; slow, dense, cord-forming growth exploits and holds a found resource and carries it back. The Fukuda result shows the same speed versus robustness trade at the single-hypha scale. Growth is irreversible tip biomass, so every foray is a bet.
- **Needs connection:** Yes for the body, since the mycelium is one connected cytoplasm; the exploration decision itself is local to each tip.
- **Sources:**
  - Boddy L. Mycologia 1999. https://doi.org/10.1080/00275514.1999.12060990
  - Lew RR. Nat Rev Microbiol 2011;9:509-518. https://doi.org/10.1038/nrmicro2591
  - Tlalka M et al. Fungal Genet Biol 2008;45:1111-1121. https://doi.org/10.1016/j.fgb.2008.03.015
  - Fukuda S et al. mBio 2021;12:e03196-20. https://doi.org/10.1128/mbio.03196-20
  - Fricker MD, Heaton LLM, Jones NS, Boddy L. Microbiol Spectrum 2017;5:FUNK-0033-2017. https://doi.org/10.1128/microbiolspec.funk-0033-2017 (title and DOI verified; full text not retrievable, publisher blocks XML)

## 5. Mycelial transport, reallocation and anastomosis

- **Primitive:** Cytoplasm is one connected, incompressible fluid. Uptake at some places and growth at others set a pressure field, so material flows from sources to sinks faster than diffusion. Flow is itself a signal: cords carrying large or fast flow thicken, others thin and are recycled. Fusion (anastomosis) of hyphae closes loops, which adds redundancy and lets flow reroute. Reallocation moves a limiting nutrient from where it was taken up to where it is needed.
- **Numbers:**
  - Labelled amino-acid analogue in Phanerochaete velutina cords: maximum 50 mm per hour (average 23 mm per hour) toward the colony margin, pulsatile with a period of 11 to 12 h, and faster than diffusion (Tlalka et al. 2002, abstract). Oscillation period 16 h at 21 C with a temperature coefficient of 2.1 (Tlalka et al. 2003, abstract). For scale: 50 mm per hour is about 14 micrometres per second.
  - Arbuscular mycorrhizal hyphae: mean particle speed 18.8 +/- 11.8 micrometres per second (fastest above 50), roughly 100 times Brownian; direction reverses within seconds; estimated diffusion crossing time over at least 3 cm of 100 to 2600 days versus a 60-day experiment (Whiteside et al. 2019, full text).
  - Under high resource inequality (rich:poor 90:10) the fungus moved phosphorus from rich to poor patches, stored less and delivered more to the host; with no inequality (50:50) net movement did not differ from zero (Whiteside et al. 2019, abstract and full text). Poor patches received significantly better exchange rates.
  - Networks show high transport capacity and robustness to damage, both enhanced as the network grows, while relative construction cost falls, through selective reinforcement and recycling of pathways (Bebber et al. 2007, abstract).
  - Fluid incompressibility means a local flow change is felt across the whole mycelium, so flow velocity carries quasi-global information about a cord's role; cords predicted to carry fast or large currents were significantly more likely to enlarge (Heaton et al. 2010, abstract).
  - Fusion-competent hyphae show remote sensing: they initiate branches or redirect growth to meet (Hickey et al. 2002, abstract); cytoplasmic flow between fused hyphae changes dramatically after fusion.
- **Trade-off:** A connected network spreads load and recovers from damage, but it also spreads parasites and demands standing investment in transport tissue. Reallocation needs a signal of where demand is (here, flow and exchange rate); without it the network wastes carbon feeding barren regions. Relative cost per transport capacity falls with size, which favours large, long-lived individuals.
- **Needs connection:** Yes. The mechanism is flow through a continuous lumen.
- **Sources:**
  - Tlalka M, Watkinson SC, Darrah PR, Fricker MD. New Phytol 2002;153:173-184. https://doi.org/10.1046/j.0028-646x.2001.00288.x
  - Tlalka M et al. New Phytol 2003;158:325-335. https://doi.org/10.1046/j.1469-8137.2003.00737.x
  - Hickey PC, Jacobson DJ, Read ND, Glass NL. Fungal Genet Biol 2002. https://doi.org/10.1016/s1087-1845(02)00035-x
  - Glass NL, Rasmussen C, Roca MG, Read ND. Trends Microbiol 2004;12:135-141. https://doi.org/10.1016/j.tim.2004.01.007 (title and DOI verified; abstract not retrieved)
  - Bebber DP et al. Proc R Soc B 2007;274:2307-2315. https://doi.org/10.1098/rspb.2007.0459
  - Heaton LLM et al. Proc R Soc B 2010;277:3265-3274. https://doi.org/10.1098/rspb.2010.0735
  - Whiteside MD et al. Curr Biol 2019;29:2043-2050. https://doi.org/10.1016/j.cub.2019.04.061

## 6. Extracellular digestion and decomposition (secreted enzymes)

- **Primitive:** An osmotroph can only import small molecules. A unit that releases a catalyst into the medium converts a non-importable resource (polymer, disaccharide) into importable monomers at the site of release. The catalyst and its products both diffuse, so most product is lost to neighbours unless the producer sits in a spatially structured, slowly diffusing environment. This is a public good with a private cost.
- **Numbers:**
  - In yeast growing on sucrose, about 99 percent of the monosaccharides made by extracellular invertase diffuse away before import (Gore, Youk, van Oudenaarden 2009). Non-producing cheaters invade producers, producers also invade cheaters, and the result is stable coexistence (a snowdrift game); the equilibrium producer fraction was about 0.3 at saturating histidine and fell as cost rose or glucose was added. Invertase is repressed above roughly 0.1 percent glucose, so wild type produces mainly when cheaters are around. At low cell density growth on sucrose was about 40 percent slower than at high density (Gore et al. 2009, full text). The paper gives no percentage cost of invertase production in the main text.
  - Diffusion rate controls which strategies persist: restricted diffusion favours a diversity of enzyme production strategies, and cheating frequency rises with diffusion rate (Allison et al. 2014, abstract, Pseudomonas). Allison 2005 (individual-based model) reports that cheaters limit decomposition of complex compounds in spatially structured environments; I read this only through secondary descriptions.
  - Lineage-level specialisation: lignin-degrading peroxidases expanded in the ancestor of the Agaricomycetes (reconstructed as a white-rot species) and contracted in parallel lineages leading to brown-rot and mycorrhizal species; the origin of lignin degradation may coincide with a sharp drop in organic carbon burial near the end of the Carboniferous (Floudas et al. 2012, abstract).
  - Cost of secretion as a number: not found in any source I could verify.
- **Trade-off:** Secretion pays only if enough product is recaptured, which depends on diffusion, density and clumping. At low density the enzyme is wasted; at high density a clone is exploited by non-producers. Strategies seen in real data: constitutive producer, repressed-until-needed producer, non-producing cheater, and complete loss of the enzyme gene (mycorrhizal lineages that trade for carbon instead).
- **Needs connection:** No. Units + medium. Spatial structure (clonal clumping) substitutes for connection.
- **Sources:**
  - Allison SD. Ecol Lett 2005. https://doi.org/10.1111/j.1461-0248.2005.00756.x (title, journal, DOI verified through Crossref; content from secondary descriptions)
  - Gore J, Youk H, van Oudenaarden A. Nature 2009;459:253-256. https://doi.org/10.1038/nature07921
  - Floudas D et al. Science 2012;336:1715-1719. https://doi.org/10.1126/science.1221748
  - Allison SD, Lu L, Kent AG, Martiny AC. Front Microbiol 2014;5:169. https://doi.org/10.3389/fmicb.2014.00169
  - Sinsabaugh RL, Follstad Shah JJ. Annu Rev Ecol Evol Syst 2012. https://doi.org/10.1146/annurev-ecolsys-071112-124414 (title and DOI verified only)

## 7. Self and non-self: heterokaryon (vegetative) incompatibility

- **Primitive:** When two hyphae fuse, their cytoplasms mix. A set of het loci acts as an identity check: if the two fusing partners carry incompatible alleles (at the same locus, or at two interacting loci), the fusion compartment is shut off and killed. Compatible partners (identical specificity at every het locus) form a stable shared cytoplasm. The identity is a short genetic marker, not a measure of relatedness.
- **Numbers:**
  - Ascomycetes carry 5 to 11 het loci per species, each with usually 2 to 4 allelic specificities (Glass and Kaneko 2003, full text). A random pair is compatible only if all loci match, so odds fall roughly as (allele count) raised to minus (locus count); e.g. 6 loci with 2 alleles each gives 1 in 64 (my arithmetic).
  - After incompatible fusion, cytoplasmic granules appear within minutes, septal pores close, vacuoles burst, and the fusion cell and often its neighbours die; destruction can be complete within 30 min of fusion (Glass and Kaneko 2003, full text).
  - Aspergillus fumigatus: 5 of 148 offspring (3.4 percent) were compatible with a parent and 166 of 2,142 sibling pairs (7.7 percent) were compatible, consistent with several segregating loci (Auxier et al. 2024, abstract).
- **Trade-off:** Benefit of fusion: larger shared biomass, resource pooling, parasexual exchange. Cost: shared cytoplasm lets selfish or infected genotypes in (viruses, aggressive genotypes that take resources without paying). Proposed explanation: fusion within an individual is beneficial, selecting for the ability to fuse; fusion between individuals is on average costly, selecting for non-self recognition (Aanen et al. 2008, abstract). The loci are under balancing selection, which keeps them diverse (Auxier 2024, Glass and Kaneko 2003). The price of the check is the loss of the fusion compartment and some neighbours each time a stranger touches.
- **Needs connection:** Yes. The mechanism is fusion; recognition needs contact between two bodies.
- **Sources:**
  - Saupe SJ. Microbiol Mol Biol Rev 2000;64:489-502. https://doi.org/10.1128/mmbr.64.3.489-502.2000 (title and DOI verified; not read)
  - Glass NL, Kaneko I. Eukaryot Cell 2003;2:1-8. https://doi.org/10.1128/ec.2.1.1-8.2003
  - Aanen DK, Debets AJM, de Visser JAGM, Hoekstra RF. BioEssays 2008;30:1193-1203. https://doi.org/10.1002/bies.20840
  - Auxier B et al. Mol Biol Evol 2024;41:msae079. https://doi.org/10.1093/molbev/msae079

## 8. Predatory fungi (nematode-trapping): cue-gated trap switching

- **Primitive:** A saprotrophic hypha switches morphology to a costly capture device only when two conditions hold: nutrients are scarce and a prey signal is present. The prey signal is a conserved small molecule (ascarosides) that the prey releases constantly. The capture device converts another organism's body into fungal biomass. Some species also emit odours that mimic food and sex cues to draw prey in.
- **Numbers:**
  - Ascaroside-induced trap formation occurs only under nutrient-deprived conditions and is conserved across several closely related species; each of the four tested species responded to a different set of ascarosides (Hsueh et al. 2013, abstract and secondary summary).
  - Arthrobotrys oligospora wild isolates: strongly responding strains formed first traps about 2.5 h after exposure to C. elegans, weak responders about 5 to 7.5 h; trap counts ranged from about 8 to 162 across 35 isolates; at 12 h about 20 percent of nematodes survived with highly responsive isolates versus about 75 to 85 percent with weak ones (Yang et al. 2020, full text).
  - In a competition with and without nematodes, the strong-responding strain made about 62 percent of conidia without nematodes and 78 percent with nematodes, so trap formation is a measurable fitness trait (Yang et al. 2020, full text).
  - The cAMP-PKA pathway, acting downstream of G-protein signalling, is required for prey sensing and trap morphogenesis; supplying cAMP rescues a signalling mutant (Chen et al. 2022, abstract).
  - Cost of building a trap in energy units: not found in any source I verified.
- **Trade-off:** Traps cost biomass and are built only where the return is likely, so the double gate (starvation AND prey cue) avoids paying when food is plentiful or prey absent. Sensitivity varies sharply between strains, trading faster capture against false alarms. Prey evolve to avoid or sense the cue, which sets up an arms race.
- **Needs connection:** No for the sensing and switch (a hypha reacts to a diffusing cue). The capture device is a local structure of the hypha.
- **Sources:**
  - Hsueh YP, Mahanti P, Schroeder FC, Sternberg PW. Curr Biol 2013;23:83-86. https://doi.org/10.1016/j.cub.2012.11.035
  - Hsueh YP et al. eLife 2017;6:e20023. https://doi.org/10.7554/elife.20023
  - Yang CT et al. PNAS 2020;117:6762-6770. https://doi.org/10.1073/pnas.1919726117
  - Chen SA, Lin HC, Hsueh YP. G3 2022;12:jkac217. https://doi.org/10.1093/g3journal/jkac217

## 9. Mycorrhizal exchange: carbon for phosphorus with reciprocal rewards

- **Primitive:** A pairing of two organisms that each hold what the other lacks (fungus: soil nutrients; plant: photosynthetic carbon) with a conditional transfer rule on both sides. Each partner raises its output only toward the partner that gives more in return. Carbon supply across the interface also switches on the fungal nutrient uptake and transport machinery.
- **Numbers:**
  - Plants detect, discriminate and reward the best fungal partners with more carbohydrate; fungi increase nutrient transfer only to roots that supply more carbohydrate; control is bidirectional, so the symbiont cannot be enslaved (Kiers et al. 2011, abstract). I could not read the full text, so I have no quantitative fractions to report.
  - Nitrogen uptake and transport depend on carbon delivered by the host across the interface, not on carbon supplied directly to the fungus as acetate (Fellbaum et al. 2012, abstract). Phosphate uptake and translocation to roots is stimulated by increased carbohydrate availability, and its metabolic and spatial distribution inside the fungus changes (Bucking and Shachar-Hill 2005, abstract).
  - Under high resource inequality the fungus relocates phosphorus from rich to poor patches within its network and delivers more to hosts (Whiteside et al. 2019, abstract). Transport speeds in section 5.
  - Partner selection is context-dependent and varies in spatial precision among plant species (Werner and Kiers 2015, abstract).
- **Trade-off:** The exchange rate is the strategy variable: a fungus that shares with a poor-paying root loses carbon relative to rivals, and a plant that overpays loses growth. Conditional reciprocity makes cheating costly, but it needs repeated, discriminating contact. The network lets one fungus serve several hosts and shift supply to the best rate.
- **Needs connection:** Yes. A persistent pairing at an interface plus the fungal network behind it.
- **Sources:**
  - Kiers ET et al. Science 2011;333:880-882. https://doi.org/10.1126/science.1208473
  - Bucking H, Shachar-Hill Y. New Phytol 2005;165:899-911. https://doi.org/10.1111/j.1469-8137.2004.01274.x
  - Fellbaum CR et al. PNAS 2012;109:2666-2671. https://doi.org/10.1073/pnas.1118650109
  - Werner GDA, Kiers ET. New Phytol 2015;205:1437-1442. https://doi.org/10.1111/nph.13113
  - Whiteside et al. 2019 (above)

## 10. Physarum network optimisation (tube reinforcement by flow)

- **Primitive:** The plasmodium is one giant cell whose body is a network of tubes carrying cytoplasm. Fluid flow through a tube feeds back on that tube: tubes with more flux thicken, tubes with less flux thin and disappear. Tero, Kobayashi and Nakagaki cast this as a rule that a tube's conductivity grows with the flux through it and otherwise decays, with one key parameter for the strength of the feedback. Peristaltic contraction waves drive the flow, and transport is greatest when the wave wavelength is about the size of the network.
- **Numbers:**
  - Physarum networks across food sources arranged like Tokyo-area stations show efficiency, fault tolerance and cost comparable to the Tokyo rail system (Tero et al. 2010, abstract). A secondary summary reports the Physarum networks used about 30 percent of the maximum number of links; time to form the network is not verified (a secondary thesis says about a day).
  - Shuttle streaming period about 100 s; contraction period 131 +/- 43 s; organisms from 3 to 17 mm with the largest at 2.1 cm, each showing a single wavelength across the body; morphological rearrangement timescale about 45 min; optimised contraction patterns spread tracer over more than 15 percent of the network after 10 periods versus about 2 percent for random patterns (Alim et al. 2013, full text).
  - Nutrient decisions: a single plasmodium grows to contact patches of different nutrient quality in the proportions needed to compose an optimal diet (Dussutour et al. 2010, abstract). Choice depends on context (violations of transitivity) (Latty and Beekman 2011, abstract). It learns to ignore quinine or caffeine when the stimulus repeats and recovers when withheld (Boisseau et al. 2016, abstract).
  - Maze solving: Nakagaki, Yamada and Toth 2000 (Nature 407:470) report path finding through a maze; I verified the citation but could not read the abstract here.
- **Trade-off:** A positive-feedback reinforcement rule plus a decay term yields short paths, redundancy and fault tolerance without a controller. Cost: standing tube biomass and pumping energy; slow reaction (tens of minutes); winner-take-most dynamics can cut useful backups unless the feedback strength is tuned (the model's key parameter).
- **Needs connection:** Yes. Flux feedback needs a connected conducting network inside one body.
- **Sources:**
  - Nakagaki T, Yamada H, Toth A. Nature 2000;407:470. https://doi.org/10.1038/35035159
  - Tero A, Kobayashi R, Nakagaki T. J Theor Biol 2007;244:553-564. https://doi.org/10.1016/j.jtbi.2006.07.015
  - Tero A et al. Science 2010;327:439-442. https://doi.org/10.1126/science.1177894
  - Dussutour A, Latty T, Beekman M, Simpson SJ. PNAS 2010;107:4607-4611. https://doi.org/10.1073/pnas.0912198107
  - Latty T, Beekman M. Proc R Soc B 2011;278:307-312. https://doi.org/10.1098/rspb.2010.1045
  - Alim K, Amselem G, Peaudecerf F, Brenner MP, Pringle A. PNAS 2013;110:13306-13311. https://doi.org/10.1073/pnas.1305049110
  - Boisseau RP, Vogel D, Dussutour A. Proc R Soc B 2016;283:20160446. https://doi.org/10.1098/rspb.2016.0446

## 11. Extracellular slime as external spatial memory

- **Primitive:** A moving cell deposits a persistent, non-living, non-diffusing layer at every location it occupies. It detects this layer on contact and avoids it unless no unmarked option exists. Memory is stored in the medium, read locally, and costs the cell no internal state. This turns a gradient follower into a searcher that does not revisit.
- **Numbers:**
  - In a Y-maze with one slimed arm, 39 of 40 plasmodia chose the blank arm; with both arms slimed, no preference (n = 23) (Reid et al. 2012, full text).
  - U-shaped trap with a chemoattractive goal behind it: 96 percent (23 of 24) reached the goal within 120 h on blank agar versus 33 percent (8 of 24) on slime-covered agar; the slime group spent almost 10 times longer on its own trail; speed did not differ significantly (Reid et al. 2012, full text).
  - Slime is a sticky high-molecular-weight glycoprotein; the paper does not report its persistence time. Cost of production: not reported.
- **Trade-off:** Cheap, solves trap-shaped local optima where gradient following fails. Cost: slime deposition, and the memory is shared with everyone else, so it also marks territory against other units (the paper tests only the organism's own slime). It becomes a handicap if the environment changes faster than the marks decay, since fresh resources inside old marks stay avoided.
- **Needs connection:** No. Units + medium.
- **Sources:**
  - Reid CR, Latty T, Dussutour A, Beekman M. PNAS 2012;109:17490-17494. https://doi.org/10.1073/pnas.1215037109

## 12. Dictyostelium aggregation by cAMP relay waves (starvation-triggered)

- **Primitive:** An excitable signalling medium made of separate cells. Starved cells release cAMP in pulses when extracellular cAMP crosses a threshold. A neighbour that senses the pulse releases its own pulse (relay), so a wave crosses the field. Extracellular cAMP is removed by degradation (phosphodiesterase), which sets pulse width and lets cells reset. Cells move toward the wave source, giving radial streams. Cell density sets pulse rate because more cells make more cAMP against a fixed removal rate (dynamical quorum sensing).
- **Numbers:**
  - Wave velocity about 300 micrometres per minute; widths of elevated cAMP 0.3 to 1 mm, so a cell signals for about 1 to 3 min; peak concentration about 1 micromolar; waves are spiral or concentric around aggregation foci (Tomchik and Devreotes 1981, abstract; the 300 micrometres per minute is an assumed value in that abstract, not a direct measurement there).
  - Cells fire synchronously about 5 h after nutrient deprivation, initially every 15 to 30 min, then every 8 min, then every 6 min once aggregating; at about 1e3 cells per square millimetre under flow of about 1 ml/min they fire about once per 6 min; isolated cells respond to extracellular cAMP above a threshold of about 500 pM; basal and peak intracellular cAMP about 400 nM and about 10 micromolar; firing frequency proportional to cell density divided by flow (dilution) rate (Gregor et al. 2010, full text).
  - Stochastic pulsing of individual cells below the threshold plays a critical role in the onset of collective behaviour; pulses originate from a discrete location despite constant exchange of cells (Gregor et al. 2010, abstract).
  - Aggregation pooling 1e4 to 1e5 cells into a slug (Strassmann et al. 2000, abstract).
- **Trade-off:** The relay turns a short-range, noisy signal into a long-range, reliable one at the cost of synchronised, energy-consuming signalling and of waiting for starvation to start it. Isolated cells never aggregate; density is a hard requirement. A cell that fails to relay slows the wave but can still follow it (a cheater strategy at the signalling level).
- **Needs connection:** Initially no (units + medium); the result is aggregation into a physical multicellular body.
- **Sources:**
  - Tomchik KJ, Devreotes PN. Science 1981;212:443-446. https://doi.org/10.1126/science.6259734
  - Gregor T, Fujimoto K, Masaki N, Sawai S. Science 2010;328:1021-1025. https://doi.org/10.1126/science.1183415
  - Endres and Wingreen 2008 (above, for the cAMP degradation role)

## 13. Dictyostelium stalk altruism, cheaters and control of cheating

- **Primitive:** After aggregation, cells split into a sterile supporting tissue (stalk, which dies) and spores that disperse. Cells from different clones can mix in the same slug. A clone that contributes less than its share to the stalk gains spores at the expense of the other clone. Linking the cheating route to a personal cost (pleiotropy), or high relatedness within groups, limits it.
- **Numbers:**
  - When starved, 1e4 to 1e5 cells aggregate into a slug; the anterior 20 percent die as stalk; half of the chimeric mixtures of field-collected clones showed a clear cheater and a victim (Strassmann et al. 2000, abstract).
  - A gene (dimA) needed to receive the stalk-inducing signal DIF-1: cells lacking it avoid the stalk but are excluded from spores when mixed with wild type, so defecting on stalk formation costs more spores than it gains (Foster et al. 2004, abstract).
  - About one third of wild-collected clones carry food bacteria through the cycle, a farming symbiosis with dispersal and prudent harvesting (Brock et al. 2011, abstract).
- **Trade-off:** The stalk lifts spores, helping dispersal, at the price of 20 percent of the group's cells. Cheaters win within a mixed slug but lose when their clone is alone, so high relatedness or a pleiotropic cost holds cooperation. Real mixtures are partly competitive.
- **Needs connection:** Yes, in the sense of aggregation into a body; the sacrifice is meaningful only inside the body.
- **Sources:**
  - Strassmann JE, Zhu Y, Queller DC. Nature 2000;408:965-967. https://doi.org/10.1038/35050087
  - Foster KR, Shaulsky G, Strassmann JE, Queller DC, Thompson CR. Nature 2004;431:693-696. https://doi.org/10.1038/nature02894
  - Brock DA, Douglas TE, Queller DC, Strassmann JE. Nature 2011;469:393-396. https://doi.org/10.1038/nature09668

## Unverified or secondary

- Berg and Brown 1972: run about 1 s and tumble about 0.1 s come from a secondary description of that paper, not its abstract. E. coli swim speed: not verified.
- Berg and Purcell 1977: the paper's own equation was not read; the 1/(4 pi D a c T) and related prefactors come from Table 1 of Endres and Wingreen 2008 as reconstructed by a text-extraction tool (the formulas are images). Check the table before turning it into a law.
- My own arithmetic (flagged inline): D a c T near 3200 counts for 1 percent gradient detection; compatibility odds for het loci.
- Hyphal tip rates: only confined-growth chip values (Fukuda et al. 2021). Free-growth rates not verified.
- Cost of secretion (enzyme production cost) and cost of building a trap in energy units: no verified number found.
- Kiers et al. 2011: abstract only, no fractions.
- Tero et al. 2010: abstract only. The 30 percent of maximum links and the formation time come from a secondary summary. Exact update equation not read (Tero et al. 2007 abstract gives the rule in words).
- Nakagaki et al. 2000 and Hsueh et al. 2013: citation verified. The statement that a second (ring) trap type did not respond to ascarosides comes from a secondary summary and is not used above.
- Allison 2005: citation verified through Crossref; claims from later papers that cite it.
- Tomchik and Devreotes 1981: wave velocity of 300 micrometres per minute is an assumption stated in the abstract, not a measurement.
- Saupe 2000, Glass et al. 2004, Fricker et al. 2017, Sinsabaugh and Follstad Shah 2012, Parent and Devreotes 1999: existence and DOI verified, abstract or full text not read.

## Top five for a simulator

1. **Secreted catalyst into the shared field** (extracellular digestion): units + medium, one new field and one conversion law; gives producer, cheater, repressed-producer and decomposer lineages from a single primitive.
2. **Persistent deposited marker, sensed locally** (slime memory): units + medium, one non-diffusing field with its own decay; lets evolution discover non-revisiting search, territory marking and trail following.
3. **Excitable relay signal with threshold and degradation** (cAMP waves): units + medium; one secreted signal plus a removal rate gives density sensing, waves, starvation-triggered aggregation and relay-cheating without any body.
4. **Neighbour-cell sensing with counting noise plus consumption-shaped gradients** (spatial sensing, absorber advantage, self-generated gradients): sense range and averaging time trade against cost; consumption of the sensed resource builds gradients that crowds can follow.
5. **Flux-reinforced links with demand-driven allocation** (fungal and Physarum transport, het checks, mycorrhizal trade): highest strategy diversity (network, trade, self and non-self, parasite spread) but the only item here that needs physical connection, so it carries the most new physics.
