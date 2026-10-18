# Microbial predation, competition, cooperation, communication and kin recognition: primitives for a thermodynamic lattice simulator

Scope: eleven mechanisms, each reduced to the physical/chemical primitive. No simulator design here.

Verification method: every citation below was looked up in Europe PMC or PubMed by DOI or exact title (metadata plus abstract returned, so each paper exists). Numbers marked "(full text)" were read in the open full text (PMC via NCBI efetch, or the Kerr PDF). Numbers marked "(abstract)" come from the abstract only. Anything I could not verify is labelled "UNVERIFIED" or "not found". DOIs resolve at https://doi.org/<DOI>.

Cross-cutting finding: in almost every system below, "recognition" is a molecular match between a tag on one cell and a receptor or immunity protein on another. No meaning is attached. Meaning arises only because the match gates a cost or a benefit (toxin delivery, adhesion, immunity).

---

## 1. Myxococcus xanthus group predation (lytic enzymes, contact-dependent killing)

- **Primitive:**
  - M. xanthus is an epibiotic predator: it lyses prey outside its own body and takes up the released macromolecules and their breakdown products. Berleman and Kirby (2009) say predation "appears to require close proximity to prey", with prey death occurring in the extracellular environment.
  - Two killing channels coexist. (1) Secreted hydrolytic enzymes and secondary metabolites. The genome encodes more than 300 predicted hydrolytic enzymes (full text, Berleman and Kirby 2009). (2) Contact-dependent killing: a Tad-like type IV-filament-like machine (Kil) assembles at the predator-prey contact site, arrests predator motility and plasmolyses the prey cell (Seef et al. 2021).
  - Feeding is on non-diffusing macromolecules. Rippling (traveling waves of cells) occurs only when cells touch prey or large macromolecules (peptidoglycan, protein, nucleic acid). It does not occur with their monomers, or when the macromolecules are physically separated from the cells (Berleman et al. 2006, abstract).
- **Numbers:**
  - Cell-density-dependent growth on casein (a polymer): above 1e4 cells/mL, growth rate rises with cell number. Between 1e4 and 1e7 cells/mL, mean doubling time fell from 15.2 to 8 h (strain YS) and from 26 to 8.5 h (strain TNS). Extracellular protease activity was proportional to cell number. At low density neither strain grew on casein, but growth on pre-hydrolysed casein (Casitone) was normal and density independent (Rosenberg et al. 1977, abstract). So the cooperative step is the shared pool of extracellular hydrolysate.
  - Contact killing at single-cell resolution (Seef et al. 2021, full text): about 30% of predator-prey contacts were productive for Kil cluster assembly. About 26% of contacts led to prey lysis in wild type, versus about 2% in core Kil mutants. Killing was observed about 2 min after Kil assembly. Roughly 8% of contacts caused a motility pause in the mutants versus about 30% in wild type.
  - Contact range: zero (cell-cell touching). Secreted-enzyme range: not quantified in these papers (UNVERIFIED in micrometres).
- **Trade-off:**
  - Constitutive diffusible lytic factors are wasted to dilution. Berleman and Kirby (2009) argue contact-regulated release "would require much lower concentrations of lytic factors than constitutive expression" (full text).
  - Density dependence creates an Allee-like effect: below a threshold density the pool of hydrolysate is too small to support growth (Rosenberg 1977). A lone cell starves where a group feeds.
  - When prey are gone, starving M. xanthus cells scavenge their own lysed kin: developmental rippling is "a result of scavenging lysed cell debris" (Berleman 2006, abstract). That links predation to the necromass section (11).
- **Recognition-as-chemistry:** Prey detection at the contact site is a surface-chemistry event: Kil assembles only when a prey cell is touched (cluster formed only in the presence of prey, Seef 2021). No prey identity is computed. The kernel-level reading is "touch something not self with the right envelope → trigger". How M. xanthus tells prey from its own kin is handled by the TraA/SitA system (section 9).
- **Sources:**
  - Berleman JE, Kirby JR. Deciphering the hunting strategy of a bacterial wolfpack. FEMS Microbiol Rev 2009. https://doi.org/10.1111/j.1574-6976.2009.00185.x (PMID 19519767)
  - Berleman JE et al. Rippling is a predatory behavior in Myxococcus xanthus. J Bacteriol 2006. https://doi.org/10.1128/jb.00559-06 (PMID 16885457)
  - Rosenberg E, Keller KH, Dworkin M. Cell density-dependent growth of Myxococcus xanthus on casein. J Bacteriol 1977. https://doi.org/10.1128/jb.129.2.770-777.1977 (PMID 402357)
  - Seef S et al. A Tad-like apparatus is required for contact-dependent prey killing in predatory social bacteria. eLife 2021. https://doi.org/10.7554/elife.72409 (PMID 34505573)
  - Munoz-Dorado J et al. Myxobacteria: moving, killing, feeding, and surviving together. Front Microbiol 2016. https://doi.org/10.3389/fmicb.2016.00781 (PMID 27303375)

## 2. Bdellovibrio bacteriovorus invasion predation

- **Primitive:**
  - A small, fast attack-phase cell finds a larger Gram-negative prey, attaches irreversibly, enters the periplasm, and reseals the prey outer membrane to form a bdelloplast. It digests the single prey cell from inside, grows as a filament, septates, and exits through discrete pores (Sockett 2009; Fenton et al. 2010; Lai et al. 2023).
  - All nutrients for replication come from the digestion of that one invaded cell (Fenton 2010, abstract). The predator genome carries large paralogous families of hydrolases and transporters used for prey entry, killing and uptake of complex molecules (Rendulic et al. 2004, abstract).
  - Prey-derived chemical cues drive the predator's transcriptional switch from attack to growth (Lai et al. 2023, full text).
- **Numbers:**
  - Attack-phase swimming up to 160 um/s with a single polar flagellum (Lai et al. 2023, full text, citing earlier work).
  - Predatory cycle in E. coli lasts 3 to 4 h (Baker et al. 2017, full text, citing the literature).
  - Yield: on average 4 to 6 progeny per prey cell of genome size similar to the predator's. Septation is synchronous and non-binary, producing odd and even progeny counts (Fenton 2010, abstract).
  - Free-living predator cells wait in the attack phase; the host range is Gram-negative only.
- **Trade-off:**
  - Prey is a one-shot, single-use resource: no prey, no replication. This makes the predator a pure encounter-rate strategy (speed, search) with yield limited by prey size.
  - Prey biomass is converted to a few progeny, not many. The yield figure above is the whole energetic return.
- **Recognition-as-chemistry:** Entry requires irreversible attachment on a "suitable" prey (Lai 2023). Lai et al. (2023) describe prey-derived chemical cues and surface structures at attachment, not a computed identity. How the host range maps onto envelope chemistry was not verified here.
- **Sources:**
  - Sockett RE. Predatory lifestyle of Bdellovibrio bacteriovorus. Annu Rev Microbiol 2009. https://doi.org/10.1146/annurev.micro.091208.073346 (PMID 19575566)
  - Rendulic S et al. A predator unmasked: life cycle of Bdellovibrio bacteriovorus from a genomic perspective. Science 2004. https://doi.org/10.1126/science.1093027 (PMID 14752164)
  - Fenton AK et al. Shadowing the actions of a predator. J Bacteriol 2010. https://doi.org/10.1128/jb.00914-10 (PMID 20935099)
  - Lai TF, Ford RM, Huwiler SG. Advances in cellular and molecular predatory biology of Bdellovibrio bacteriovorus six decades after discovery. Front Microbiol 2023. https://doi.org/10.3389/fmicb.2023.1168709 (PMID 37256055)
  - Baker M et al. Measuring and modelling the response of Klebsiella pneumoniae KPC prey to Bdellovibrio bacteriovorus predation. Sci Rep 2017. https://doi.org/10.1038/s41598-017-08060-4

## 3. Protist grazing and size refuges

- **Primitive:**
  - Phagotrophic flagellates and ciliates capture particles by size-selective interception or filtration and digest them in vacuoles. Prey vulnerability is a function of cell size, motility, surface properties and toxicity, acting at different stages: encounter, capture, ingestion, digestion (Jurgens and Matz 2002, abstract).
  - When protists are the main bacterivores, "both very small and large bacterial cells gain some size refuge" (Jurgens and Matz 2002, abstract).
- **Numbers:**
  - A bacterivorous flagellate (Bodo saltans) ingested only cells shorter than 3 um. Fewer than 4% of bacterial numbers formed large, grazing-resistant thread-like cells, and those protected about 70% of total bacterial biomass from grazing (Simek et al. 1997, abstract).
  - Within one day of adding the flagellate, filamentous inedible bacteria appeared, making up 8.7 to 34% of a beta-proteobacterial subgroup. Total abundance of that subgroup fell from 3.05e6 to 0.23e6 cells/mL. A second group stayed small, grew faster, and was heavily grazed (more than 85% of flagellates had ingested it) yet dominated numerically (Pernthaler et al. 1997, abstract).
  - Predation and viral lysis together consume prokaryote biomass at approximately the rate it is produced in oxic water columns (Pernthaler 2005, abstract).
- **Trade-off:**
  - Two coexisting escapes: outgrow the predation pressure (fast, small, grazed) or become inedible (filament, large, inactive). Pernthaler 1997 describes exactly these two strategies.
  - The inedible morph is physiologically inactive in that study, so the refuge costs growth.
  - Defence beyond size (toxins, surface properties, biofilm, motility) is catalogued in Matz and Kjelleberg (2005, abstract).
- **Recognition-as-chemistry:** None needed. The gate is a geometric threshold: edible if size is below a cut-off (3 um in Simek 1997). Selectivity is physical, not chemical. Physical size gates are a clean primitive for a law.
- **Sources:**
  - Jurgens K, Matz C. Predation as a shaping force for the phenotypic and genotypic composition of planktonic bacteria. Antonie Van Leeuwenhoek 2002. https://doi.org/10.1023/a:1020505204959 (PMID 12448740)
  - Simek K et al. Morphological and compositional shifts in an experimental bacterial community influenced by protists with contrasting feeding modes. Appl Environ Microbiol 1997. https://doi.org/10.1128/aem.63.2.587-595.1997 (PMID 16535515)
  - Pernthaler J et al. Contrasting bacterial strategies to coexist with a flagellate predator in an experimental microbial assemblage. Appl Environ Microbiol 1997. https://doi.org/10.1128/aem.63.2.596-601.1997 (PMID 16535516)
  - Pernthaler J. Predation on prokaryotes in the water column and its ecological implications. Nat Rev Microbiol 2005. https://doi.org/10.1038/nrmicro1180 (PMID 15953930)
  - Matz C, Kjelleberg S. Off the hook: how bacteria survive protozoan grazing. Trends Microbiol 2005. https://doi.org/10.1016/j.tim.2005.05.009 (PMID 15935676)
  - Hahn MW, Hofle MG. Grazing of protozoa and its effect on populations of aquatic bacteria. FEMS Microbiol Ecol 2001. https://doi.org/10.1111/j.1574-6941.2001.tb00794.x (PMID 11295449)

## 4. Type VI secretion system (T6SS), contact-range injection

- **Primitive:**
  - A phage-tail-like contractile sheath, anchored at the cell envelope, fires a tube (Hcp) tipped with effector proteins through the neighbour's envelope. Contraction supplies the energy for translocation (Basler et al. 2012, abstract).
  - Effectors are enzymes. P. aeruginosa Tse1 and Tse3 hydrolyse peptidoglycan in the target's periplasm. Self-protection is by periplasmic immunity proteins specific to each effector. The immunity is needed because sister cells also hit each other (Russell et al. 2011, abstract).
  - Firing is not only constitutive: in P. aeruginosa, assembly and counterattack are triggered at the point where a neighbour's T6SS strikes ("tit-for-tat", Basler et al. 2013, abstract).
- **Numbers:**
  - Sheath assembly about 20 to 30 s per um; contraction takes about 5 ms or less; extended and contracted sheaths measured 667 nm and 372 nm by cryotomography. About 25% of sequenced Gram-negative bacteria encode a T6SS (Basler 2012, full text).
  - Counterattack engages the attacker "within seconds" of its first strike. In competition with P. aeruginosa, about 1000-fold fewer T6SS+ A. baylyi cells were recovered than T6SS- cells: prey that fire back are killed far more (Basler 2013, full text).
  - Substrates (Hcp, tip) are exchanged among bystander cells within minutes of contact and reused for new assemblies (Vettiger and Basler 2016, abstract).
  - Cost: UNVERIFIED as a number. No growth-rate cost figure was found in the abstracts or texts read. Taillefer et al. (2026) report that T6SS expression is heterogeneous (ON and OFF subpopulations) and that OFF cells buffer the population against retaliation, which implies a cost or risk, but the abstract gives no figure.
- **Trade-off:**
  - Killing happens only at the interface between T6SS+ and sensitive domains, while growth happens everywhere. Sufficiently large domains of sensitive cells can therefore survive and even outgrow the attackers. If two mutually sensitive T6SS+ strains meet, the more numerous strain wins, with an advantage that grows with attack rate (Borenstein et al. 2015, abstract, simulation plus E. coli versus V. cholerae experiment).
  - Killing neighbours in a packed colony makes clonal domains: T6SS killing drives genetic phase separation (order-disorder transition) and, by assorting kin, favours public goods cooperation (McNally et al. 2017, abstract).
  - Retaliation risk: firing invites counterfire from a T6SS+ neighbour (Basler 2013; Taillefer 2026).
- **Recognition-as-chemistry:** Entirely a tag match: effector versus its cognate immunity protein. A cell with the immunity protein is not harmed whether or not it is kin. The trigger for counterattack is a physical event (being hit), not an identity decision.
- **Sources:**
  - Basler M et al. Type VI secretion requires a dynamic contractile phage tail-like structure. Nature 2012. https://doi.org/10.1038/nature10846 (PMID 22367545)
  - Basler M, Ho BT, Mekalanos JJ. Tit-for-tat: type VI secretion system counterattack during bacterial cell-cell interactions. Cell 2013. https://doi.org/10.1016/j.cell.2013.01.042 (PMID 23415234)
  - Russell AB et al. Type VI secretion delivers bacteriolytic effectors to target cells. Nature 2011. https://doi.org/10.1038/nature10244 (PMID 21776080)
  - Hood RD et al. A type VI secretion system of Pseudomonas aeruginosa targets a toxin to bacteria. Cell Host Microbe 2010. https://doi.org/10.1016/j.chom.2009.12.007 (PMID 20114026)
  - Vettiger A, Basler M. Type VI secretion system substrates are transferred and reused among sister cells. Cell 2016. https://doi.org/10.1016/j.cell.2016.08.023 (PMID 27616061)
  - Borenstein DB et al. Established microbial colonies can survive type VI secretion assault. PLoS Comput Biol 2015. https://doi.org/10.1371/journal.pcbi.1004520 (PMID 26485125)
  - McNally L et al. Killing by Type VI secretion drives genetic phase separation and correlates with increased cooperation. Nat Commun 2017. https://doi.org/10.1038/ncomms14371 (PMID 28165005)
  - Taillefer B et al. Phenotypic heterogeneity optimizes trade-offs during adaptive deployment of the type VI secretion system. PLoS Biol 2026. https://doi.org/10.1371/journal.pbio.3003838

## 5. Contact-dependent growth inhibition (CDI)

- **Primitive:**
  - A large cell-surface exoprotein CdiA, secreted by the two-partner pore CdiB, binds a receptor on a neighbouring cell and delivers its C-terminal toxin domain (CdiA-CT) into the target. A small immunity protein CdiI inside the producer binds and inactivates its cognate CdiA-CT only, not heterologous ones (Aoki et al. 2005; Aoki et al. 2010, abstracts).
  - Receptor: the outer-membrane protein BamA. Variation in extracellular loops 6 and 7 of BamA between species restricts which cells are susceptible. Loops are identical across hundreds of E. coli strains, so the system acts as self-nonself discrimination within a species (Ruhe et al. 2013, abstract).
  - Inhibition is conditional on the inhibitor's growth state and the target's pili state (Aoki 2005, abstract). Toxin domains differ (different nuclease activities), so the molecular harm is varied but the delivery logic is the same.
- **Numbers:** Not found in the abstracts. UNVERIFIED: delivery rate, cost per cell, contact duration. Do not invent them. (Aoki 2010 abstract shows bacteria lacking CdiA-CT/CdiI cannot compete with isogenic CDI+ cells in laboratory media and on a eukaryotic host.)
- **Trade-off:**
  - Same logic as T6SS: only cells in contact are hit; immune cells are safe; the producer pays for a very large protein and for immunity.
  - Danka et al. (2017, abstract) argue CDI systems may also act as cooperative "contact-dependent signalling" among self cells, because multiple levels of specificity restrict delivery and activity to the same strain. That makes CDI a kin-recognition device as well as a weapon.
- **Recognition-as-chemistry:** Yes, fully: receptor-loop shape matches CdiA binding epitope (who can be hit), and CdiI matches CdiA-CT (who is immune). Two independent tag matches, neither carrying meaning.
- **Sources:**
  - Aoki SK et al. Contact-dependent inhibition of growth in Escherichia coli. Science 2005. https://doi.org/10.1126/science.1115109 (PMID 16109881)
  - Aoki SK et al. A widespread family of polymorphic contact-dependent toxin delivery systems in bacteria. Nature 2010. https://doi.org/10.1038/nature09490 (PMID 21085179)
  - Ruhe ZC et al. Receptor polymorphism restricts contact-dependent growth inhibition to members of the same species. mBio 2013. https://doi.org/10.1128/mbio.00480-13 (PMID 23882017)
  - Ruhe ZC, Low DA, Hayes CS. Bacterial contact-dependent growth inhibition. Trends Microbiol 2013. https://doi.org/10.1016/j.tim.2013.02.003 (PMID 23473845)
  - Danka ES, Garcia EC, Cotter PA. Are CDI systems multicolored, facultative, helping greenbeards? Trends Microbiol 2017. https://doi.org/10.1016/j.tim.2017.02.008

## 6. Bacteriocins and colicins: diffusible toxin, immunity, suicidal lysis, and rock-paper-scissors

- **Primitive:**
  - A colicinogenic E. coli carries a col plasmid with three determinants: the toxin (colicin), a colicin-specific immunity protein, and a lysis protein (Cascales et al. 2007; Kerr et al. 2002 full text). Colicin synthesis is under SOS control. The lysis protein is expressed under stress and causes partial lysis of the producer, which is how the colicin is released (Cascales 2007 abstract; Kerr 2002 full text).
  - Colicins bind outer-membrane receptors that are also used for nutrient uptake, then are translocated and kill. Resistance usually means losing or altering that receptor (Kerr 2002 full text; Cascales 2007 abstract). So resistance has its own cost: it impairs nutrient uptake.
  - The toxin diffuses, so its range is a diffusion field around the producer, giving an inhibition zone (Chao and Levin 1981, abstract).
- **Numbers:**
  - Liquid culture: colicin producers have an advantage only when common (frequency above 2e-2). In a soft-agar matrix they win even from frequencies as low as 1e-6 (Chao and Levin 1981, abstract). The reason: producers have a lower intrinsic growth rate than sensitives, but in structured habitats the producer colony kills neighbours, creating a zone of released resource around itself.
  - Fraction of producers: the introduction of Bayramoglu et al. (2017, full text) reports that 0.5 to 3% of planktonic and 7 to 9% of sessile E. coli populations produce colicins (cited from earlier work, not re-derived). Single-cell imaging shows expressers are a small, dynamic, slow-growing, elongated subpopulation with stochastic switching between expressing and non-expressing (abstract). Percentage of expressers that lyse per generation: UNVERIFIED.
  - Kerr et al. 2002 (PDF full text): three E. coli strains, colicin producer C, sensitive S, resistant R. Transfers every 24 h. Static plate (local dispersal and interaction): all three coexisted for the whole experiment, as patches "chased" each other (C encroached on S, S displaced R, R invaded C). Flask and mixed plate (large-scale mixing): S then C dropped below detection and R won. Diversity needs local interaction; surface growth alone was not enough (the mixed plate behaved like the flask).
  - Cost ordering needed for RPS (Kerr 2002 full text): resistance cost must be less than toxin production cost, but not too small or too large. "If the costs of resistance or allelopathy are either too great or too small, the community can collapse."
- **Trade-off:**
  - Producers pay twice: the toxin investment, and sacrificing a fraction of themselves to release it (suicidal lysis, spite). Gardner et al. (2004, abstract) analyse bacteriocin warfare as a spiteful behaviour whose outcome depends on how many neighbours are kin.
  - Resistant strains escape the toxin but pay in lost uptake. Sensitive strains grow fastest of the three. That loop gives non-transitive dominance: C beats S, S beats R, R beats C (Kerr 2002).
  - In well-mixed conditions the loop collapses to a single winner. In structured conditions it persists. This is the cleanest published demonstration that spatial locality is the engine of diversity.
- **Recognition-as-chemistry:** Fully chemical: the colicin binds a specific receptor protein, and a cognate immunity protein binds the colicin inside the producer. Nobody "recognises" anybody. Receptor present: harmed. Immunity protein present: safe.
- **Sources:**
  - Kerr B, Riley MA, Feldman MW, Bohannan BJM. Local dispersal promotes biodiversity in a real-life game of rock-paper-scissors. Nature 2002. https://doi.org/10.1038/nature00823 (PMID 12110887)
  - Cascales E et al. Colicin biology. Microbiol Mol Biol Rev 2007. https://doi.org/10.1128/mmbr.00036-06 (PMID 17347522)
  - Chao L, Levin BR. Structured habitats and the evolution of anticompetitor toxins in bacteria. PNAS 1981. https://doi.org/10.1073/pnas.78.10.6324 (PMID 7031647)
  - Riley MA, Wertz JE. Bacteriocins: evolution, ecology, and application. Annu Rev Microbiol 2002. https://doi.org/10.1146/annurev.micro.56.012302.161024 (PMID 12142491)
  - Gardner A, West SA, Buckling A. Bacteriocins, spite and virulence. Proc R Soc B 2004. https://doi.org/10.1098/rspb.2004.2756 (PMID 15306326)
  - Bayramoglu B et al. Bet-hedging in bacteriocin producing Escherichia coli populations: the single cell perspective. Sci Rep 2017. https://doi.org/10.1038/srep42068 (PMID 28165017)
  - Turnbull L et al. Explosive cell lysis as a mechanism for the biogenesis of bacterial membrane vesicles and biofilms. Nat Commun 2016 (a second lysis-release example in P. aeruginosa biofilms, via a prophage endolysin). https://doi.org/10.1038/ncomms11220 (PMID 27075392)

## 7. Quorum sensing, diffusion sensing and signal cheaters (LuxI/LuxR, Vibrio fischeri)

- **Primitive:**
  - A synthase (LuxI) makes a small autoinducer (an acyl-homoserine lactone; the LuxI/LuxR roles and the feedback are standard background, not verified in a source read, only the V. fischeri autoinducer data below are). The molecule crosses the cell envelope passively, so intracellular and external concentrations equalise. A receptor/transcription factor (LuxR) is activated when the concentration rises, and switches on the synthase, giving positive feedback (Kaplan and Greenberg 1985, abstract; Fuqua et al. 1994, LuxR-LuxI family review, no abstract available to verify).
  - Because the signal is made by the same cell that reads it, the concentration a cell sees depends on production rate, cell number, and how fast the signal leaves (diffusion, flow, decay), not on cell number alone. Redfield (2002, abstract) proposed that the true function is "diffusion sensing": a cell decides whether its secretions are likely to be lost, and regulates secretion of degradative enzymes accordingly. Hense et al. (2007, abstract) propose "efficiency sensing" that combines cell density, mass transfer and spatial distribution, and show that spatial arrangement of cells can matter more than density.
  - Signal decay is chemical: AHLs undergo pH-dependent lactonolysis, faster at higher pH and temperature, and faster for short acyl chains (C4-HSL turned over more than 3-oxo-C12-HSL). The ring opens at alkaline pH and re-closes when acidified (Yates et al. 2002, abstract).
- **Numbers:**
  - V. fischeri autoinducer (Kaplan and Greenberg 1985, abstract): cellular concentration equals external concentration; equilibration within 20 s; 85 to 99.5% of intracellular label escaped when cells were moved to autoinducer-free buffer; induction at concentrations as low as 10 nM (equivalent, per the authors, to 1 or 2 molecules per cell); maximal response at about 200 nM; if external concentration fell below 10 nM after induction began, induction did not continue.
  - Range: an exogenous autoinducer diffusing along a narrow agar lane activated a QS reporter over about 1 cm on a timescale of about 10 h (Dilanji et al. 2012, abstract).
  - Small volumes: about 100 fL confinement droplets, with starting groups as small as two cells, triggered QS in P. aeruginosa. Activation was heterogeneous among and within droplets (Boedicker et al. 2009, full text; abstract not available in the indexed record).
  - Cost: QS imposes "a metabolic burden"; a lasR (QS-deficient) mutant had a growth advantage over the parent in coculture (Sandoz et al. 2007, abstract). A per-cell energy figure for autoinducer synthesis: not found, UNVERIFIED.
- **Trade-off:**
  - Cheaters: individuals that skip signal production or response save the cost and spread (Diggle et al. 2007, abstract; Sandoz 2007, abstract). Kin selection (high relatedness) is a solution (Diggle 2007).
  - A second solution: tie the signal to a private good. QS-controlled expression of cellular enzymes that only the responding cell benefits from puts a metabolic constraint on cheating (Dandekar et al. 2012, abstract).
  - Hense and Schuster (2015, abstract) generalise: autoinducer systems exist to control costly cooperative behaviours, with cheap signals pre-assessing whether the costly behaviour will be efficient.
- **Recognition-as-chemistry:** Pure concentration reading. The "meaning" (density, enclosure, or diffusion limit) is a human interpretation of one scalar field with a threshold. Species specificity comes from the receptor binding pocket matching the acyl chain, a lock-and-key match (the V. fischeri autoinducer is called "species-specific" in Kaplan and Greenberg 1985).
- **Sources:**
  - Kaplan HB, Greenberg EP. Diffusion of autoinducer is involved in regulation of the Vibrio fischeri luminescence system. J Bacteriol 1985. https://doi.org/10.1128/jb.163.3.1210-1214.1985 (PMID 3897188)
  - Fuqua WC, Winans SC, Greenberg EP. Quorum sensing in bacteria: the LuxR-LuxI family of cell density-responsive transcriptional regulators. J Bacteriol 1994. https://doi.org/10.1128/jb.176.2.269-275.1994 (PMID 8288518)
  - Redfield RJ. Is quorum sensing a side effect of diffusion sensing? Trends Microbiol 2002. https://doi.org/10.1016/s0966-842x(02)02400-9 (PMID 12160634)
  - Hense BA et al. Does efficiency sensing unify diffusion and quorum sensing? Nat Rev Microbiol 2007. https://doi.org/10.1038/nrmicro1600 (PMID 17304251)
  - Hense BA, Schuster M. Core principles of bacterial autoinducer systems. Microbiol Mol Biol Rev 2015. https://doi.org/10.1128/mmbr.00024-14 (PMID 25694124)
  - Yates EA et al. N-acylhomoserine lactones undergo lactonolysis in a pH-, temperature-, and acyl chain length-dependent manner. Infect Immun 2002. https://doi.org/10.1128/iai.70.10.5635-5646.2002 (PMID 12228292)
  - Dilanji GE et al. Quorum activation at a distance. J Am Chem Soc 2012. https://doi.org/10.1021/ja211593q (PMID 22372494)
  - Boedicker JQ, Vincent ME, Ismagilov RF. Microfluidic confinement of single cells of bacteria in small volumes initiates high-density behavior of quorum sensing and growth and reveals its variability. Angew Chem Int Ed 2009. https://doi.org/10.1002/anie.200901550 (PMID 19565587)
  - Diggle SP et al. Cooperation and conflict in quorum-sensing bacterial populations. Nature 2007. https://doi.org/10.1038/nature06279 (PMID 18004383)
  - Sandoz KM, Mitzimberg SM, Schuster M. Social cheating in Pseudomonas aeruginosa quorum sensing. PNAS 2007. https://doi.org/10.1073/pnas.0705653104 (PMID 17898171)
  - Dandekar AA, Chugani S, Greenberg EP. Bacterial quorum sensing and metabolic incentives to cooperate. Science 2012. https://doi.org/10.1126/science.1227289 (PMID 23066081)
  - Keller L, Surette MG. Communication in bacteria: an ecological and evolutionary perspective. Nat Rev Microbiol 2006. https://doi.org/10.1038/nrmicro1383 (PMID 16501584)

## 8. Public goods and cheaters: yeast invertase, siderophores, and spatial structure

- **Primitive:**
  - A cell secretes an enzyme or chelator into the shared medium. The product (sugar monomers, or iron-bound siderophore) diffuses, so neighbours can take it up. Producers pay the cost; non-producers ("cheats") get the benefit.
  - Yeast: invertase hydrolyses sucrose in the periplasm; about 99% of the glucose produced diffuses away before the cell can import it (Gore et al. 2009, abstract and full text). Pyoverdin in P. aeruginosa: an iron-scavenging siderophore, a "very durable public good", whose production is regulated by iron availability (Kummerli and Brown 2010, abstract).
- **Numbers (Gore et al. 2009, full text unless noted):**
  - Capture efficiency about 1% of invertase-made glucose. Growth rate at low cell density in 5% sucrose is about 40% slower than at high density, equivalent to growth on only 0.003% glucose, and the growth rate at high density matches that of saturating (2%) glucose.
  - Growth is strongly concave in glucose: benefit terms scale as glucose to the power alpha = 0.15 (plus or minus 0.01). That concavity turns the game into a snowdrift game and produces stable coexistence in well-mixed culture; the equilibrium cooperator fraction was about 0.3 at saturating histidine (i.e. no imposed cost).
  - Raising the "cost of cooperation" (limiting a histidine the cooperator needs) or adding glucose lowers the cooperator fraction, and can drive cooperators extinct. Adding glucose can even lower the mean coculture growth rate, because fewer cooperators hydrolyse sucrose.
  - Invertase is glucose-repressed above about 0.1% glucose: wild-type cells cooperate when facing cheaters (low glucose) and defect when facing always-cooperators (high glucose).
  - Direct cost of invertase secretion: measured (cooperators grow more slowly than cheaters when invertase is expressed) but no percentage is stated in the text read. UNVERIFIED as a number.
  - Siderophores: pyoverdin cheats had a fitness advantage when pyoverdin durability was low but not when it was high, because cooperators cut production once enough had accumulated (Kummerli and Brown 2010, abstract).
  - Viscosity: raising medium viscosity limited dispersal and siderophore diffusion and raised the fitness of producers relative to non-producers (Kummerli et al. 2009a, abstract).
  - V. cholerae on chitin: the dilemma is solved either by thick biofilms that confine the enzyme products to producers, or by fluid flow that removes soluble products from non-producers. Both work by limiting the distance over which benefits are shared (Drescher et al. 2014, abstract).
- **Trade-off:**
  - Capture fraction is the knob: if a producer keeps enough of its own product (here 1% of a concave benefit), cooperation can pay even among cheaters. If almost nothing is kept and benefits are linear, cheats win (Gore 2009 model: cooperation pays when capture efficiency exceeds the cost).
  - Spatial structure: in P. aeruginosa, limited dispersal alone does not favour siderophore production, because higher relatedness is cancelled by more local competition (Kummerli et al. 2009b, abstract). This is Taylor's prediction.
  - Regulation matters: facultative production (cut back when the good is plentiful) greatly reduces selection for cheating when the good is durable (Kummerli and Brown 2010, abstract).
- **Recognition-as-chemistry:** None needed for the public good itself. Anyone with the transporter or receptor can use it. Siderophore "piracy" (a cell that takes up another strain's siderophore) is the same match logic with a different receptor, but that was not verified in a source read here.
- **Sources:**
  - Gore J, Youk H, van Oudenaarden A. Snowdrift game dynamics and facultative cheating in yeast. Nature 2009. https://doi.org/10.1038/nature07921 (PMID 19349960)
  - Griffin AS, West SA, Buckling A. Cooperation and competition in pathogenic bacteria. Nature 2004. https://doi.org/10.1038/nature02744 (PMID 15329720)
  - Kummerli R, Brown SP. Molecular and regulatory properties of a public good shape the evolution of cooperation. PNAS 2010. https://doi.org/10.1073/pnas.1011154107 (PMID 20944065)
  - Kummerli R et al. (2009a) Viscous medium promotes cooperation in the pathogenic bacterium Pseudomonas aeruginosa. Proc R Soc B 2009. https://doi.org/10.1098/rspb.2009.0861 (PMID 19605393)
  - Kummerli R, Gardner A, West SA, Griffin AS. (2009b) Limited dispersal, budding dispersal, and cooperation: an experimental study. Evolution 2009. https://doi.org/10.1111/j.1558-5646.2008.00548.x (PMID 19154373)
  - Drescher K et al. Solutions to the public goods dilemma in bacterial biofilms. Curr Biol 2014. https://doi.org/10.1016/j.cub.2013.10.030 (PMID 24332540)

## 9. Self and kin recognition: Proteus Dienes line, Myxococcus TraA and OME, Dictyostelium, yeast FLO1

- **Primitive (one per system):**
  - **Proteus mirabilis (Dienes line).** Swarms of independent isolates meet and form a visible boundary; clonal swarms merge. The ids gene cluster (idsABCDEF, one operon) encodes self-identity proteins, and ids deletion mutants do not merge with their parent (Gibbs et al. 2011, abstract). Ids and Idr (rhs-related) proteins act in two independent pathways joined by a type VI secretion system that exports select Ids and Idr proteins (Wenren et al. 2013, abstract). IdsD and IdsE form a heteromeric complex whose binding is strain- and allele-specific, and the specificity sits in a predicted membrane-spanning subdomain with unique amino acids per variant (Cardarelli et al. 2015, abstract). Expression rises in late log and early stationary phase, is bistable, and rises when a non-self swarm approaches (Gibbs 2011, abstract).
  - **Myxococcus TraA/TraB and outer membrane exchange (OME).** TraA is a polymorphic cell-surface receptor (PA14 lectin-like domain) that binds homophilically to compatible TraA on a touching cell, which allows the two outer membranes to fuse and exchange lipids and lipoproteins. TraA and TraB are needed in donor and recipient (Pathak et al. 2012, abstract; Vassallo et al. 2015, abstract). A single residue (A/P205) acts as a switch for binding specificity (Cao and Wall 2017, abstract). TraA alone does not discriminate kin precisely. OME also delivers polymorphic SitA lipoprotein toxins, each with a cognate SitI immunity gene, and SitA passes serially from cell to cell "like an infectious agent", building strong identity barriers (Vassallo et al. 2017, abstract). OME can repair damage: LPS mutants and a conditional lethal lpxC mutant were rescued by healthy donors, and OME diluted outer-membrane damage across the merged population (Vassallo 2015, abstract).
  - **Dictyostelium discoideum.** csA is a homophilic adhesion gene: wild-type cells mixed with csA-knockout cells are more altruistic overall but preferentially direct the benefit to other wild-type cells, a single-gene greenbeard (Queller et al. 2003, abstract). tgrB1 and tgrC1: a matching allele pair is necessary and sufficient for attractive self-recognition, mediated by differential cell-cell adhesion, probably by direct binding (Hirose et al. 2011, abstract). lagB1 and lagC1 are highly polymorphic Ig-repeat transmembrane proteins whose sequence dissimilarity correlates with strain segregation (Benabentos et al. 2009, abstract). Cheaters sporulate without contributing to the dying stalk, so recognition is a defence against exploitation (Hirose 2011, abstract). D. purpureum prefers kin groups where some individuals die to help others (Mehdiabadi et al. 2006, abstract).
  - **Yeast FLO1.** FLO1 makes cells stick to one another (flocculation), protecting FLO1-expressing cells from antimicrobials and ethanol. FLO1+ cells preferentially stick to each other regardless of relatedness across the rest of the genome, so cheaters not expressing FLO1 are excluded: a greenbeard gene. FLO1 varies in sequence and expression among strains (Smukalla et al. 2008, abstract).
- **Numbers:**
  - Scale: Myxococcus strains from a centimetre-scale population already show strong antagonism when mixed in development, and self/nonself discrimination during swarming shows many distinct recognition types even among identical MLST genotypes. No significant correlation between genetic distance at MLST loci and social asymmetry was found (Vos and Velicer 2009, abstract). So recognition is a fast-evolving surface tag, not a readout of genome-wide relatedness.
  - Contact timescale: all four systems act on touching cells, but no uniform timescale was found for OME or adhesion (T6SS protein exchange, a different system, is within minutes, Vettiger 2016). Binding affinities for IdsD-IdsE, TraA-TraA, TgrB1-TgrC1: not found, UNVERIFIED.
- **Trade-off:**
  - Recognition keeps benefits (adhesion, toxin immunity, shared outer membrane repair, altruistic stalk) inside a group sharing the same tag, but it costs: tag polymorphism must be maintained by balancing selection (Benabentos 2009, abstract).
  - A one-gene tag is also a one-gene attack surface: a lineage that carries the tag but not the cooperative behaviour passes the recognition test. In csA, the altruism and the targeting both derive directly from homophilic adhesion of one protein (Queller 2003, abstract). In FLO1 the adhesin is likewise both tag and benefit (Smukalla 2008, abstract). A tag separable from the helping act could be faked; no source read here tests that.
  - Tag malleability: modest changes in TraA create new recognition groups, so new kin classes can arise quickly (Cao and Wall 2017, abstract).
- **Recognition-as-chemistry:** All four are homophilic or cognate protein binding: a variable region on one cell binds the same variable region (or its cognate partner) on another. Strain identity is "do the two polymorphic surface proteins bind?" Nothing in the molecule encodes "kin". Relatedness is inferred by the match. A tag that matches is accepted whether or not the carrier is truly related, which is exactly what makes it a greenbeard.
- **Sources:**
  - Gibbs KA, Wenren LM, Greenberg EP. Identity gene expression in Proteus mirabilis. J Bacteriol 2011. https://doi.org/10.1128/jb.01167-10 (PMID 21551301)
  - Gibbs KA, Urbanowski ML, Greenberg EP. Genetic determinants of self identity and social recognition in bacteria. Science 2008. https://doi.org/10.1126/science.1160033 (listed in Europe PMC; abstract not read)
  - Wenren LM et al. Two independent pathways for self-recognition in Proteus mirabilis are linked by type VI-dependent export. mBio 2013. https://doi.org/10.1128/mbio.00374-13 (PMID 23882014)
  - Cardarelli L, Saak C, Gibbs KA. Two proteins form a heteromeric bacterial self-recognition complex in which variable subdomains determine allele-restricted binding. mBio 2015. https://doi.org/10.1128/mbio.00251-15 (PMID 26060269)
  - Pathak DT et al. Cell contact-dependent outer membrane exchange in myxobacteria: genetic determinants and mechanism. PLoS Genet 2012. https://doi.org/10.1371/journal.pgen.1002626 (PMID 22511878)
  - Vassallo C et al. Cell rejuvenation and social behaviors promoted by LPS exchange in myxobacteria. PNAS 2015. https://doi.org/10.1073/pnas.1503553112 (PMID 26038568)
  - Vassallo CN et al. Infectious polymorphic toxins delivered by outer membrane exchange discriminate kin in myxobacteria. eLife 2017. https://doi.org/10.7554/elife.29397 (PMID 28820387)
  - Cao P, Wall D. Self-identity reprogrammed by a single residue switch in a cell surface receptor of a social bacterium. PNAS 2017. https://doi.org/10.1073/pnas.1700315114 (PMID 28320967)
  - Vos M, Velicer GJ. Social conflict in centimeter- and global-scale populations of the bacterium Myxococcus xanthus. Curr Biol 2009. https://doi.org/10.1016/j.cub.2009.08.061 (PMID 19879146)
  - Queller DC, Ponte E, Bozzaro S, Strassmann JE. Single-gene greenbeard effects in the social amoeba Dictyostelium discoideum. Science 2003. https://doi.org/10.1126/science.1077742 (PMID 12511650)
  - Hirose S et al. Self-recognition in social amoebae is mediated by allelic pairs of tiger genes. Science 2011. https://doi.org/10.1126/science.1203903 (PMID 21700835)
  - Benabentos R et al. Polymorphic members of the lag gene family mediate kin discrimination in Dictyostelium. Curr Biol 2009. https://doi.org/10.1016/j.cub.2009.02.037 (PMID 19285397)
  - Mehdiabadi NJ et al. Social evolution: kin preference in a social microbe. Nature 2006. https://doi.org/10.1038/442881a (PMID 16929288)
  - Smukalla S et al. FLO1 is a variable green beard gene that drives biofilm-like cooperation in budding yeast. Cell 2008. https://doi.org/10.1016/j.cell.2008.09.037 (PMID 19013280)

## 10. Hamilton's rule and where relatedness comes from (viscosity versus active recognition)

- **Primitive:**
  - Hamilton's rule: a costly helping trait is favoured when r times b exceeds c, with r the relatedness of the recipient to the actor (a statistical association between actor and recipient genotypes), b the benefit to the recipient and c the cost to the actor. Source: Hamilton (1964), https://doi.org/10.1016/0022-5193(64)90038-4. NOT VERIFIED from the paper text: Europe PMC holds no abstract for it, so I confirmed only that the paper exists. The inequality is the standard statement; confirm against the paper before quoting it.
  - Two routes to high r: (a) passive, from limited dispersal and growth from few founders (relatives end up next to each other); (b) active, from tag-matching (greenbeard or kin discrimination, section 9). West et al. (2007, abstract) stress separating mechanism from survival value.
  - The catch with route (a): the same limited dispersal makes relatives compete. "Competition between relatives can reduce, and even totally negate, the kin-selected benefits of altruism" (West, Pen and Griffin 2002, abstract).
- **Numbers:**
  - Experiments in P. aeruginosa siderophore production: higher relatedness evolves more siderophore production, but more local competition selects for lower production, with a significant interaction (relatedness matters less when competition is more local) (Griffin et al. 2004, abstract). Limited dispersal alone did not favour cooperation, supporting Taylor's prediction that increased relatedness and local competition exactly cancel in the simplest case. Budding dispersal (dispersing in groups) did favour cooperation, since it keeps relatedness high while spreading competition more widely (Kummerli et al. 2009b, abstract). A viscous medium, which also limits diffusion of the good, favoured producers (Kummerli et al. 2009a, abstract), partly because producers recapture more of their own product so the trait becomes less altruistic.
  - Per-parameter values for r, b, c in these systems: not given in the abstracts, UNVERIFIED.
- **Trade-off:**
  - Passive structure is free of tags but needs a mechanism that spreads competition (group dispersal, a good that benefits the producer directly, nutrient flow).
  - Active recognition is tag-driven and works in a mixed population, but needs a polymorphic tag and can be faked or lost.
  - T6SS killing creates clonal domains from the other direction: killing neighbours removes non-kin from the neighbourhood and thereby raises r (McNally et al. 2017, abstract).
- **Recognition-as-chemistry:** r itself is only a statistic; the chemistry (tag match) is one way to make it high, and physical locality is the other. The first needs a tag field, the second only space and growth.
- **Sources:**
  - Hamilton WD. The genetical evolution of social behaviour. I. J Theor Biol 1964. https://doi.org/10.1016/0022-5193(64)90038-4 (PMID 5875341)
  - West SA, Pen I, Griffin AS. Cooperation and competition between relatives. Science 2002. https://doi.org/10.1126/science.1065507 (PMID 11935015)
  - West SA, Griffin AS, Gardner A. Social semantics: altruism, cooperation, mutualism, strong reciprocity and group selection. J Evol Biol 2007. https://doi.org/10.1111/j.1420-9101.2006.01258.x (PMID 17305808)
  - Griffin AS, West SA, Buckling A. Cooperation and competition in pathogenic bacteria. Nature 2004. https://doi.org/10.1038/nature02744
  - Kummerli R et al. 2009a and 2009b (see section 8).

## 11. Corpses, necromass, cannibalism and lysis feeding

- **Primitive:**
  - A dead or lysed cell dumps its cytoplasm into the surrounding medium, where it can be taken up by neighbours. No "decision" is involved: the contents are simply no longer enclosed.
  - Bacillus subtilis cannibalism (a regulated version): cells that have entered the sporulation pathway export a killing factor and a signalling protein that cause nonsporulating siblings to lyse; the sporulating cells feed on the released nutrients and keep growing instead of committing to spores, so sporulation is delayed (Gonzalez-Pastor et al. 2003, abstract). The killing toxin SdpC induces an immunity protein SdpI in the producing cells through a three-protein pathway, so the cannibals are protected (Ellermeier et al. 2006, abstract). Toxins skf and sdp lyse a fraction of sensitive siblings; the released nutrients are preferentially used by matrix-producing cells, which are the only ones expressing resistance (Lopez et al. 2009, abstract). A second route: early sporulators release glycerol that acts as both nutrient and signal and delays sporulation in neighbours (Updegrove et al. 2024, abstract).
  - Myxococcus developmental autolysis: during fruiting-body development a fraction of cells irreversibly commits to lyse, lysis is cell-concentration independent (so autolytic), and the surviving cells need the lysis products to induce spores (Wireman and Dworkin 1977, abstract). The fraction that lyses: reviews say "only a fraction" survives as spores (Cao et al. 2015, full text); a precise percentage was NOT verified here.
  - Starved E. coli: stationary-phase cultures are dynamic populations whose survivors evolve, with mutants expressing the "growth advantage in stationary phase" (GASP) phenotype (Finkel 2006, abstract; Finkel and Kolter 1999, abstract). Aged-culture mutants (rpoS changes) took over and killed cells from young cultures (Zambrano et al. 1993, abstract). The review frames a "death phase" followed by long-term stationary phase, but an explicit measured fraction of dead cells feeding survivors was not read in these abstracts, UNVERIFIED.
  - Pseudomonas explosive cell lysis: a subpopulation of cells lyses through a prophage endolysin, liberating cytosolic content and extracellular DNA and producing membrane vesicles (Turnbull et al. 2016, abstract).
- **Numbers:**
  - Marine scale: about 10 billion viruses per litre and viral lysis plus protist predation consume prokaryote biomass at about the rate it is produced (Fuhrman 1999, abstract; Pernthaler 2005, abstract). Fuhrman lists nutrient cycling as an effect. Percent of lysate carbon reused by neighbours: UNVERIFIED.
  - Experimental cannibalism: no single mortality or yield figure was extracted from the abstracts. UNVERIFIED.
- **Trade-off:**
  - Killing siblings buys time: the cannibal delays an irreversible, costly commitment (sporulation) by eating its neighbours (Gonzalez-Pastor 2003). The cost is to the victims, and to the group if too many lyse. Immunity limits who pays.
  - The benefit is private only if the cannibal gets the released nutrients, which is why spatial and regulatory co-location of toxin immunity and resource uptake matter (Lopez 2009).
  - Autolysis in Myxococcus sacrifices most cells for a minority of spores; the benefit is inclusive-fitness if the lysing cells are kin, which is where section 9's recognition and section 10's relatedness enter.
- **Recognition-as-chemistry:** Again toxin plus cognate immunity (SdpC/SdpI, Skf, Sdp). Survivors are those who carry the immunity; the spent cell contents are generic and carry no label.
- **Sources:**
  - Gonzalez-Pastor JE, Hobbs EC, Losick R. Cannibalism by sporulating bacteria. Science 2003. https://doi.org/10.1126/science.1086462 (PMID 12817086)
  - Ellermeier CD et al. A three-protein signaling pathway governing immunity to a bacterial cannibalism toxin. Cell 2006. https://doi.org/10.1016/j.cell.2005.11.041 (PMID 16469701)
  - Lopez D, Vlamakis H, Losick R, Kolter R. Cannibalism enhances biofilm development in Bacillus subtilis. Mol Microbiol 2009. https://doi.org/10.1111/j.1365-2958.2009.06882.x (PMID 19775247)
  - Updegrove TB et al. Altruistic feeding and cell-cell signaling during bacterial differentiation actively enhance phenotypic heterogeneity. Sci Adv 2024. https://doi.org/10.1126/sciadv.adq0791 (PMID 39423260)
  - Wireman JW, Dworkin M. Developmentally induced autolysis during fruiting body formation by Myxococcus xanthus. J Bacteriol 1977. https://doi.org/10.1128/jb.129.2.798-802.1977 (PMID 402359)
  - Cao P, Dey A, Vassallo CN, Wall D. How myxobacteria cooperate. J Mol Biol 2015. https://doi.org/10.1016/j.jmb.2015.07.022 (PMID not looked up)
  - Finkel SE. Long-term survival during stationary phase: evolution and the GASP phenotype. Nat Rev Microbiol 2006. https://doi.org/10.1038/nrmicro1340 (PMID 16415927)
  - Finkel SE, Kolter R. Evolution of microbial diversity during prolonged starvation. PNAS 1999. https://doi.org/10.1073/pnas.96.7.4023 (PMID 10097156)
  - Zambrano MM et al. Microbial competition: Escherichia coli mutants that take over stationary phase cultures. Science 1993. https://doi.org/10.1126/science.7681219 (PMID 7681219)
  - Turnbull L et al. Explosive cell lysis. Nat Commun 2016. https://doi.org/10.1038/ncomms11220
  - Fuhrman JA. Marine viruses and their biogeochemical and ecological effects. Nature 1999. https://doi.org/10.1038/21119 (PMID 10376593)
  - Pernthaler J. 2005 (see section 3).

---

## Unverified items to treat as open

- T6SS cost per cell; CDI delivery rate and cost; AHL synthesis cost; invertase cost as a percentage (Gore 2009 measures it but the text read gives no number).
- AHL half-lives (the paper shows pH, temperature and chain-length dependence, but I did not extract numbers).
- Fraction of Myxococcus cells that lyse in development; fraction of colicin producers that lyse per generation.
- Binding affinities for IdsD-IdsE, TraA-TraA, TgrB1-TgrC1.
- Hamilton 1964: inequality not read in the source.
- Quantitative secreted-enzyme range for Myxococcus; grazing clearance rates.

## Top five for a simulator

Ranked by strategy diversity per unit of physics. "Physics" means a conserved quantity plus a local rule, not a behaviour.

1. **Death and lysis release the body's contents into the cell/field (section 11).** Almost free (energy and mass are already conserved; only the "where does it go" rule is new) and it makes scavenging, cannibalism, spite, bet-hedging and suicide-for-others discoverable. Cannibalism, lysis feeding, colicin release and developmental autolysis are all this one primitive plus a trigger.
2. **Contact-triggered or diffusible toxin with a heritable toxin tag and a matching immunity tag (sections 4, 5, 6).** One compare (tag versus tag) gates damage. It produces killers, immune lineages, resistant-but-slower lineages (rock-paper-scissors, which needs local dispersal and resistance cost below production cost), sacrificial release, and kin discrimination.
3. **A secreted, decaying, diffusing molecule with a threshold-reading sensor (sections 7, 8).** One emission field, one decay rate, one read gives density sensing, diffusion sensing, signal cheaters, public goods, and snowdrift dynamics (capture fraction and concave benefit). The decay rate and the producer's recapture share are the tuning knobs.
4. **Homophilic adhesion or cognate-binding tag (section 9).** A variable surface tag that binds the same tag on a touching cell turns "who stays together" into an evolvable trait (FLO1, csA, TraA). It lets relatedness emerge by assortment, and it makes greenbeard cheaters possible.
5. **A size or contact gate on who can be eaten (sections 1, 2, 3).** A hard threshold (inedible above about 3 um in Simek 1997) or a contact-and-lyse step gives predator-prey arms races (outgrow versus become inedible) with almost no extra law. It also needs the prey's body to be a resource, which depends on item 1.
