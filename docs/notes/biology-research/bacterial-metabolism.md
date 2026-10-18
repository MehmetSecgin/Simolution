# How bacteria (and archaea) eat and make energy: primitives for a simulator

Citation hygiene: DOI, PMID, authors, year and title were checked against Europe PMC or Crossref. Journal volume and page numbers were typed from memory and were not checked, so rely on the DOI. Author names are ASCII-fied (Kuemmerli, Goerke).

Scope: primitives only, no feature design. Every citation below was looked up in Europe PMC or Crossref (existence, authors, year, DOI). Numbers are tagged "verified in abstract" or "verified in full text" when I read the sentence myself. Anything tagged "not verified" is from memory of the paper or the field and must be checked before it becomes a constant. Publisher sites (PNAS, ASM, Cell Press, PMC) blocked full-text fetches, so most full-text checks are limited to open-access papers served by Europe PMC.

## 1. Uptake kinetics and the diffusion ceiling (Monod, Michaelis-Menten)

- **Primitive:** A cell surface carries N transporter proteins. Each binds one substrate molecule from the cell's surroundings, moves it across the membrane, and is free again after a residence time tau. Uptake rate v = Vmax S / (K + S), where Vmax is set by transporter count and 1/tau, and K by binding and turnover. Growth follows the same saturating shape in the external concentration (Monod), with biomass yield per unit substrate roughly constant. At low S the cell is limited by how often molecules collide with transporters, not by the transporters. A cell of radius a in a field of diffusivity D and concentration C can at most absorb about 4 pi D a C. Covering only a small fraction of the surface with transporters already captures most of that maximum, because the capture probability of a molecule that hits the cell is Ns/(Ns + pi a) for N sites of radius s.
- **Numbers:** Specific affinity (rate per concentration at low S) spans a 10^8-fold range across organisms while Vmax spans only about 10^3-fold, and specific affinity correlates inversely with the saturation constant (verified in abstract, Button 1998). Berg and Purcell's capture formula and the "small fraction of surface need be adsorbing" result are in the abstract (verified in abstract). E. coli high-affinity glucose scavenging is tuned to roughly 10^-6 M glucose (verified in abstract, Ferenci 1996). Monod's Ks, mu_max and constant-yield statements: the paper exists (Crossref), content not text-verified here.
- **Trade-off:** More transporters raise capacity but cost proteome and membrane area (see sections 2, 12, 14), and past a small coverage the extra capture is negligible, so the real limit is internal, not geometric. Saturating uptake means a rich cell wastes capacity, a poor cell is starved by encounter rate. Neighbours that also absorb deplete the same field, which is what turns uptake into competition.
- **Sources:**
  - Button DK (1998) Microbiol Mol Biol Rev 62:636. https://doi.org/10.1128/mmbr.62.3.636-645.1998 (PMID 9729603)
  - Berg HC, Purcell EM (1977) Physics of chemoreception. Biophys J 20:193. https://doi.org/10.1016/s0006-3495(77)85544-6 (PMID 911982)
  - Ferenci T (1996) FEMS Microbiol Rev 18:301. https://doi.org/10.1111/j.1574-6976.1996.tb00246.x (PMID 8703508)
  - Monod J (1949) The growth of bacterial cultures. Annu Rev Microbiol 3:371. https://doi.org/10.1146/annurev.mi.03.100149.002103 (existence only)

## 2. Transporter classes: affinity versus rate versus energy cost

- **Primitive:** Three common uptake machines. (a) Secondary transporters (symporters) use the proton motive force to pull substrate in. (b) The phosphotransferase system (PTS) phosphorylates the sugar using phosphoenolpyruvate (PEP) as it crosses, so uptake and first metabolic step are one event and the price is one PEP. (c) ABC importers use a periplasmic binding protein that grabs substrate and hands it to an ATP-driven membrane complex. For ABC systems the apparent half-saturation concentration depends on how many binding proteins the cell makes, so affinity is tunable by expression, independent of Vmax. For PTS the half-saturation is an intrinsic property of the transporter.
- **Numbers:** A model of marine oligotrophs predicts nanomolar half-saturation concentrations from micromolar binding-protein dissociation constants, more than 1000-fold below the binding protein's own Kd, but this needs many binding proteins and a large periplasm, and slow binding-protein diffusion limits the maximal rate (verified in abstract and full text, Norris 2021). Copiotrophs such as Vibrios carry many PTS; the oligotroph SAR11 lacks PTS and uses ABC (verified in abstract). Hyperthermophilic bacteria and archaea prefer ATP-driven (primary) uptake systems with high-affinity ABC sugar transporters (verified in abstract, Konings 2002). E. coli induces a high-affinity ABC pathway only under glucose limitation, not under feast and not under famine (verified in abstract, Ferenci 1996). The PTS uses PEP "at the expense of" which is stated in the Postma abstract; an exact ATP-equivalents-per-sugar count for ABC systems was not verified.
- **Trade-off:** High affinity costs binding proteins, periplasm volume and slow handover, so it caps maximal rate and growth (oligotroph strategy). High rate and low affinity (copiotroph) wins in rich pulses and loses in dilute fields. Strains evolved in glucose-limited chemostats show the same rate versus affinity partition within one species.
- **Sources:**
  - Norris N, Levine NM, Fernandez VI, Stocker R (2021) PLoS Comput Biol 17:e1009023. https://doi.org/10.1371/journal.pcbi.1009023 (PMID 34010286)
  - Postma PW, Lengeler JW, Jacobson GR (1993) Microbiol Rev 57:543. https://doi.org/10.1128/mr.57.3.543-594.1993 (PMID 8246840)
  - Davidson AL, Dassa E, Orelle C, Chen J (2008) Microbiol Mol Biol Rev 72:317. https://doi.org/10.1128/mmbr.00031-07 (existence only)
  - Konings WN, Albers SV, Koning S, Driessen AJM (2002) Antonie van Leeuwenhoek 81:61. https://doi.org/10.1023/a:1020573408652 (PMID 12448706)
  - Ferenci T (2016) Trade-off mechanisms shaping the diversity of bacteria. Trends Microbiol 24:209. https://doi.org/10.1016/j.tim.2015.11.009 (PMID 26705697; abstract names resource allocation, design constraint and regulation as the three trade-off mechanisms)
  - Fraebel DT et al (2017) eLife 6:e24669. https://doi.org/10.7554/elife.24669 (existence only; relevant to constrained phenotype space in evolved E. coli)

## 3. Extracellular digestion and diffusible public goods

- **Primitive:** Bacteria secrete exoenzymes (chitinases, proteases, hydrolases) that cut polymers outside the cell into oligomers small enough for transporters. The product is a diffusible molecule released into the shared field. Any neighbour with a transporter can take it. The producer pays the enzyme cost (proteome, secretion) and gets back only the fraction of product that reaches its own transporters before diffusing away.
- **Numbers:** In yeast sucrose hydrolysis in the periplasm, about 99 percent of the monosaccharide made diffuses away before import (verified in abstract, Gore 2009; eukaryote, but the same physics). Marine bacteria on chitin particles self-organise into clusters of about 10 to 20 micrometres in which cells share hydrolysis products, and degradation shows a critical cell concentration below which it does not happen at all even with unlimited substrate (verified in abstract, Ebrahimi 2019). Low rate and high uptake efficiency emerges from cooperation among degraders, while labile substrate with fast turnover makes aggregation competitive and lowers carbon-use efficiency (verified in abstract). Vibrio cholerae digesting chitin avoids exploitation either by forming thick biofilms that confine products, or by flow removing soluble products from non-producers (verified in abstract, Drescher 2014).
- **Trade-off:** Enzyme secretion is a gamble on locality. Dense, still, sticky surroundings make secretion pay for the producer and for clone-mates. Dilute, mixed or flowing surroundings make the product a commons and favour non-producers (cheaters). Both extremes are viable strategies in different places, and non-producers can invade until products become scarce enough to collapse the whole commons (snowdrift).
- **Sources:**
  - Gore J, Youk H, van Oudenaarden A (2009) Nature 459:253. https://doi.org/10.1038/nature07921 (PMID 19349960)
  - Ebrahimi A, Schwartzman J, Cordero OX (2019) PNAS 116:23309. https://doi.org/10.1073/pnas.1908512116 (PMID 31666322)
  - Drescher K, Nadell CD, Stone HA, Wingreen NS, Bassler BL (2014) Curr Biol 24:50. https://doi.org/10.1016/j.cub.2013.10.030 (PMID 24332540)
  - Allison SD (2005) Cheaters, diffusion and nutrients constrain decomposition by microbial enzymes in spatially structured environments. Ecol Lett 8:626. https://doi.org/10.1111/j.1461-0248.2005.00756.x (existence only)
  - Datta MS et al (2016) Nat Commun 7:11965. https://doi.org/10.1038/ncomms11965 (existence only; micro-scale successions on model marine particles)

## 4. Siderophores: the privatizable versus diffusible good

- **Primitive:** Iron is nearly insoluble. Cells secrete small chelators (siderophores, for example pyoverdine) that bind Fe(III) outside the cell. The iron-bound complex is taken up by a dedicated receptor on any cell that carries it. Producers pay the synthesis cost. Non-producers that keep the receptor take iron for free. How far a siderophore spreads before capture sets whether it behaves as a private or a public good.
- **Numbers:** In packed P. aeruginosa microcolonies, pyoverdine is not freely diffusing but passed between contacting cells, forming a centre-high gradient, and this local trading modulates single-cell growth rate (verified in abstract, Julou 2013). Higher relatedness selects for higher siderophore production, while more local competition selects for lower, with a relatedness by scale-of-competition interaction (verified in abstract, Griffin 2004). Viscous medium changes the fitness consequence of siderophore production (verified in abstract, Kuemmerli 2009). Pyoverdine is a very durable public good and producers lower production when enough has accumulated, which limits what cheats gain (verified in abstract, Kuemmerli and Brown 2010). In wild marine bacterioplankton, public-good dynamics drive the evolution of iron-acquisition strategies (verified in abstract, Cordero 2012).
- **Trade-off:** The same molecule is private in a crowded clump and public in open fluid. Regulation that turns production down when enough product is around is a cheap anti-cheater device. Producer, cheater, and conditional producer are all stable roles depending on diffusion length versus cell spacing.
- **Sources:**
  - Julou T et al (2013) PNAS 110:12577. https://doi.org/10.1073/pnas.1301428110 (PMID 23858453)
  - Griffin AS, West SA, Buckling A (2004) Nature 430:1024. https://doi.org/10.1038/nature02744 (PMID 15329720)
  - Kuemmerli R, Griffin AS, West SA, Buckling A, Harrison F (2009) Proc R Soc B 276:3531. https://doi.org/10.1098/rspb.2009.0861 (PMID 19605393)
  - Kuemmerli R, Brown SP (2010) PNAS 107:18921. https://doi.org/10.1073/pnas.1011154107 (PMID 20944065)
  - Cordero OX, Ventouras LA, DeLong EF, Polz MF (2012) PNAS 109:20059. https://doi.org/10.1073/pnas.1213344109 (PMID 23169633)

## 5. Respiration versus fermentation: ATP yield versus rate per resource

- **Primitive:** Fermentation makes ATP by substrate-level phosphorylation in glycolysis and sends reduced carriers out as a partially oxidised waste (acetate, lactate, ethanol). Respiration passes electrons from NADH and FADH2 down a membrane chain to an external electron acceptor (oxygen, nitrate, others), pumps protons, and lets ATP synthase run off the gradient (section 7). Respiration needs a second resource, the electron acceptor, which is a separate field in nature. Respiration also needs a large amount of membrane-bound machinery.
- **Numbers:** Embden-Meyerhof glycolysis nets 2 ATP per glucose and the Entner-Doudoroff pathway 1 ATP, with ED needing several-fold less enzymatic protein for the same glucose flux (verified in abstract, Flamholz 2013). Counting NADH and FADH2 equivalents, E. coli "fermentation" (acetate overflow) gives about 2.0 ATP per carbon, about 12 per glucose, and full respiration about 4.4 ATP per carbon, about 26 per glucose (verified in full text, Basan 2015, Extended Data table, built from EcoCyc and a conversion ratio of 2 ATP per NADH). Measured energy flux per proteome fraction is about 750 mM ATP per OD600 per hour for fermentation versus about 390 for respiration, so fermentation is roughly two-fold cheaper in proteome per unit ATP flux (verified in full text, Basan 2015). Membrane real estate gives an independent reason: fermentation makes ATP faster per unit membrane area, and the cell switches only when respiratory proteins crowd the membrane (verified in abstract, Szenk 2017). Theory and biochemistry agree that low-rate high-yield ATP production behaves as cooperative resource use and can evolve in spatially structured environments, while high-rate low-yield wins in mixed competition (verified in abstract, Pfeiffer 2001).
- **Trade-off:** Per unit resource: respiration wins (about 2x more ATP per carbon). Per unit machinery or per unit time: fermentation wins. A resource-rich, crowded setting favours fast wasteful burners; a scarce, structured one favours efficient burners. Beardmore et al show rate-yield trade-offs can maintain both a fittest and a more mutationally robust "flattest" type coexisting.
- **Sources:**
  - Flamholz A, Noor E, Bar-Even A, Liebermeister W, Milo R (2013) PNAS 110:10039. https://doi.org/10.1073/pnas.1215283110 (PMID 23630264)
  - Basan M et al (2015) Nature 528:99. https://doi.org/10.1038/nature15765 (PMID 26632588)
  - Szenk M, Dill KA, de Graff AMR (2017) Cell Syst 5:95. https://doi.org/10.1016/j.cels.2017.06.005 (PMID 28755958)
  - Pfeiffer T, Schuster S, Bonhoeffer S (2001) Science 292:504. https://doi.org/10.1126/science.1058079 (PMID 11283355)
  - Beardmore RE, Gudelj I, Lipson DA, Hurst LD (2011) Nature 472:342. https://doi.org/10.1038/nature09905 (PMID 21441905)
  - Falkowski PG, Fenchel T, Delong EF (2008) Science 320:1034. https://doi.org/10.1126/science.1153213 (existence only; cited for electron acceptors as a distinct resource class, not text-verified)

## 6. Overflow metabolism and the acetate switch

- **Primitive:** Fast-growing E. coli with oxygen present still excretes acetate. Acetate is made from acetyl-CoA via phosphotransacetylase and acetate kinase, which generates one extra ATP by substrate-level phosphorylation. When the preferred sugar is gone, cells import acetate and re-activate it (acetyl-CoA synthetase) and run it through the TCA cycle: the "acetate switch". Acetate is therefore a waste when carbon is abundant and a food when it is not.
- **Numbers:** Acetate excretion rate per biomass is threshold-linear in growth rate: zero below about 0.76 per hour (about 55 minutes per doubling) and rising linearly above it, regardless of which glycolytic carbon source sets the growth rate (verified in full text, Basan 2015). The explanation is a proteome budget: the energy sector plus biomass sectors hit a cap of about 42 percent of the proteome (phi max 0.42), so past a certain growth rate the cell shifts to the cheaper-in-protein pathway (verified in full text, Basan 2015). The switch in life-style is covered in Wolfe 2005 (verified in abstract). Overflow wastes ATP-per-glucose several-fold relative to full respiration (verified in abstract, Szenk 2017).
- **Trade-off:** Overflow buys speed at the price of yield and of a toxic, acidifying by-product (section 19). It also hands a free secondary food to neighbours (section 9) and to the same lineage after the primary sugar is exhausted, at the price of a lag (section 8).
- **Sources:**
  - Basan M et al (2015) Nature 528:99. https://doi.org/10.1038/nature15765 (PMID 26632588)
  - Wolfe AJ (2005) Microbiol Mol Biol Rev 69:12. https://doi.org/10.1128/mmbr.69.1.12-50.2005 (PMID 15755952)
  - Szenk M, Dill KA, de Graff AMR (2017) https://doi.org/10.1016/j.cels.2017.06.005

## 7. Proton motive force (PMF) and ATP synthase

- **Primitive:** Respiratory chains (and some pumps in fermenters, for example by ATP hydrolysis running the synthase backwards) move protons across the membrane. This creates a gradient of charge and pH, the PMF. Protons flowing back through ATP synthase rotate it and make ATP from ADP and phosphate. The same gradient also pays for secondary transporters, flagellar motors and ion homeostasis. Any leak that lets protons cross without work (uncouplers, weak acids, open membranes) spends the gradient as heat.
- **Numbers:** The thermodynamic H+/ATP ratio of E. coli ATP synthase is 4.0 plus or minus 0.3, different from the 3.3 stoichiometry of c-subunits to beta-subunits (verified in abstract, Steigmiller 2008). PMF magnitudes in the abstracts I verified: about 120 mV for Streptococcus faecalis, held near constant by trading membrane potential for pH gradient with K+ transport (Bakker and Mangerich 1981), and 140 to 160 mV for the archaeon Sulfolobus acidocaldarius at pH 3.5, mostly as a pH gradient (Luebben and Schaefer 1989). A typical E. coli PMF value of about -150 to -200 mV is commonly quoted but I did not verify a source. The antibiotic-tolerance literature reports that the TisB toxin puts cells into dormancy by lowering PMF and ATP (verified in abstract, Lewis 2010).
- **Trade-off:** PMF is a shared energy currency between ATP making, transport and motility. Spending it on one thing drains the others. Because archaeal and bacterial membranes differ in proton leak, the same machinery gives different safety margins (section 21).
- **Sources:**
  - Steigmiller S, Turina P, Graeber P (2008) PNAS 105:3745. https://doi.org/10.1073/pnas.0708356105 (PMID 18316723)
  - Bakker EP, Mangerich WE (1981) J Bacteriol 147:820. https://doi.org/10.1128/jb.147.3.820-826.1981 (PMID 6268609)
  - Luebben M, Schaefer G (1989) J Bacteriol 171:6106. https://doi.org/10.1128/jb.171.11.6106-6116.1989 (PMID 2478523)
  - Lewis K (2010) Persister cells. Annu Rev Microbiol 64:357. https://doi.org/10.1146/annurev.micro.112408.134306 (PMID 20528688)
  - Mitchell P (1961) Nature 191:144. https://doi.org/10.1038/191144a0 (existence only; chemiosmotic hypothesis)

## 8. Diauxie and catabolite repression

- **Primitive:** With two sugars present, bacteria use the preferred one first. A global regulatory system (cAMP-CRP, PTS signalling, CcpA in Gram-positives) blocks the genes for the second sugar while the first is available. When the first runs out, a lag follows while the second pathway is built.
- **Numbers:** Carbon catabolite repression works by several distinct regulatory mechanisms in different bacteria and orders substrates hierarchically (verified in abstract, Goerke and Stuelke 2008). After a shift from glucose to acetate or other fermentation products, E. coli shows multi-hour lags traced to depletion of key metabolites when central carbon flux reverses, and lag length trades off against steady-state growth rate (verified in abstract, Basan 2020). In Lactococcus lactis the lag is a bet-hedge: two stable cell types coexist, one continues to grow, one does not, with the fraction depending on the strength of repression (verified in abstract, Solopova 2014). The carbon-catabolism growth law (growth rate falls linearly with expression of catabolic genes) is optimal on many sugars but sub-optimal on some, so it is a heuristic (verified in abstract, Towbin 2017).
- **Trade-off:** Strict preference gives faster growth on the best sugar and a lag on the second. Weak repression gives no lag and slower growth on the best sugar. Mixed-population hedging between the two gives stable coexistence of types inside a clone.
- **Sources:**
  - Goerke B, Stuelke J (2008) Nat Rev Microbiol 6:613. https://doi.org/10.1038/nrmicro1932 (PMID 18628769)
  - Basan M et al (2020) Nature 584:470. https://doi.org/10.1038/s41586-020-2505-4 (PMID 32669712)
  - Solopova A et al (2014) PNAS 111:7427. https://doi.org/10.1073/pnas.1320063111 (PMID 24799698)
  - Towbin BD et al (2017) Nat Commun 8:14123. https://doi.org/10.1038/ncomms14123 (PMID 28102224)
  - Erickson DW et al (2017) Nature 551:119. https://doi.org/10.1038/nature24299 (PMID 29072300; abstract: top-down resource-allocation model of growth transition kinetics)

## 9. Cross-feeding, waste as someone else's food, metabolic division of labour

- **Primitive:** Every metabolic step that leaks an intermediate puts a molecule in the shared field. Overflow products (acetate, glycerol, amino acids, organic acids) are real food for cells with the right enzymes. Loss of a costly biosynthesis gene saves the cell its cost if neighbours leak the product (Black Queen). Two auxotrophs each overproducing what the other lacks can outgrow a prototroph because each avoids making what it is given.
- **Numbers:** In long glucose-limited E. coli chemostats a single clone became polymorphic, three clones coexisting stably through differential secretion and uptake of acetate and glycerol, even though they differed in maximum growth rate and glucose uptake kinetics (verified in abstract, Rosenzweig 1994). Deleting two genes in E. coli made amino-acid auxotroph pairs that, in the vast majority of cases, outgrew prototrophs, because the cost of overproducing one amino acid was smaller than the saving from not making the other (division of labour); frequency-dependent selection limited exploitation by non-cooperators (verified in abstract, Pande 2014). In spatially structured cultures cross-feeders with non-cooperators are excluded from the benefit, while in liquid culture non-cooperators are favoured (verified in abstract, Pande 2016). Many "costless" secretions exist: genome-scale models over 2 million pairwise simulations of 24 species found many metabolites that cost the producer nothing to release, giving ample cross-feeding opportunities, more in anoxic conditions (verified in abstract, Pacheco 2019). Leaky functions produce public goods, so loss of such a function is favoured until community supply just meets demand (verified in abstract, Morris 2012). Hundreds of soil and plant communities in minimal media converged on predictable composition governed by nutrient availability, explained by widespread nonspecific cross-feeding (verified in abstract, Goldford 2018). A counterweight: among culturable aquatic strains, the great majority of pairwise interactions are net negative, so cross-feeding is not dominantly positive (verified in abstract, Foster and Bell 2012).
- **Trade-off:** Leakiness is a cost of living at speed and a subsidy to neighbours. Specialists are cheaper but dependent. Spatial structure decides whether helpers are paid back or exploited.
- **Sources:**
  - Rosenzweig RF, Sharp RR, Treves DS, Adams J (1994) Genetics 137:903. https://doi.org/10.1093/genetics/137.4.903 (PMID 7982572)
  - Pande S et al (2014) ISME J 8:953. https://doi.org/10.1038/ismej.2013.211 (PMID 24285359)
  - Pande S et al (2016) ISME J 10:1413. https://doi.org/10.1038/ismej.2015.212 (PMID 26623546)
  - Pacheco AR, Moel M, Segre D (2019) Nat Commun 10:103. https://doi.org/10.1038/s41467-018-07946-9 (PMID 30626871)
  - Morris JJ, Lenski RE, Zinser ER (2012) mBio 3:e00036-12. https://doi.org/10.1128/mbio.00036-12 (PMID 22448042)
  - Goldford JE et al (2018) Science 361:469. https://doi.org/10.1126/science.aat1168 (PMID 30072533)
  - Foster KR, Bell T (2012) Curr Biol 22:1845. https://doi.org/10.1016/j.cub.2012.08.005 (PMID 22959348)

## 10. Syntrophy and the thermodynamic floor (bacteria and archaea)

- **Primitive:** Some fermentations are energetically uphill under standard conditions. They run only if a partner (a hydrogen-consuming methanogen archaeon) removes the product and keeps its concentration low. Each partner then lives on a sliver of free energy. There is a smallest usable energy packet per reaction because a cell can only make ATP from reactions that release at least the cost of moving an ion across its membrane.
- **Numbers:** The free energy shared between syntrophic partners sits near the minimum biochemically convertible energy of 20 to 25 kJ per mol per reaction, about one-third of an ATP and about the energy for a monovalent ion to cross the charged membrane (verified in abstract, Schink 1997). Syntrophic fermenters make ATP by substrate-level phosphorylation and spend part of it on reversed electron transport to release electrons at a level the partner can use (verified in abstract). Energetic and metabolic interaction networks in methanogenic communities show redundancy can drive cooperation or competition (Embree 2015, existence and abstract start verified).
- **Trade-off:** Metabolic "bankruptcy" is avoided only by a partner that keeps the product concentration low. A waste that is toxic or thermodynamically blocking when it accumulates becomes the basis of an obligate partnership when someone consumes it.
- **Sources:**
  - Schink B (1997) Microbiol Mol Biol Rev 61:262. https://doi.org/10.1128/mmbr.61.2.262-280.1997 (PMID 9184013)
  - Embree M, Liu JK, Al-Bassam MM, Zengler K (2015) PNAS 112:15450. https://doi.org/10.1073/pnas.1506034112 (PMID 26621749)

## 11. Pirt maintenance energy and the cost of staying alive

- **Primitive:** A cell must spend energy on things that do not make new biomass: turnover of macromolecules, ion gradients, repair. Pirt wrote this as substrate consumption = growth-linked part (growth rate over true yield) + a rate m per unit biomass, independent of growth rate. In the starved state the same drain empties reserves and kills cells.
- **Numbers:** Pirt 1965 exists (Proc R Soc B) and defines the maintenance relation; the abstract is not indexed, so the equation is from the standard formulation, not text-verified here. A review states that maintenance appears as a growth-rate-independent function of cell mass and time, but that maintenance alone cannot explain all yield variation, so overflow, energy spilling (futile cycles of K+, NH4+ and H+) and metabolite secretion also lower yield (verified in abstract, Russell and Cook 1995). The quantification of maintenance is contested, mostly because definitions mix non-growth components and because cell death is ignored (verified in abstract, van Bodegom 2007). Rates scale about 10^6 : 10^3 : 1 for growth : maintenance : survival across environments, with a single activation energy near 110 kJ (verified in abstract, Price and Sowers 2004). Subsurface cells catabolise 10^4 to 10^6 times slower than lab cultures and turn biomass over on timescales of centuries to millennia, with energy fluxes about 1000-fold below culture-based maintenance estimates (verified in abstract, Hoehler and Joergensen 2013). E. coli in carbon-free medium die at 0.25 to 0.59 per day, and slower prior growth gives exponentially slower death through a lower maintenance rate per cell volume; cells grown slowly survive longer, a rapid-growth versus survival trade-off (verified in abstract and full text, Biselli 2020). Dead cells are food: the recycling yield is 12 to 23 percent, about 8 dead cells to make one new cell at the low end (verified in full text, Biselli 2020).
- **Trade-off:** Maintenance is a fixed drain proportional to biomass, so a bigger, faster cell has a larger bill and dies sooner when food stops. Slow, efficient lifestyles live longer. Corpses return a fraction of their stores to survivors.
- **Sources:**
  - Pirt SJ (1965) Proc R Soc Lond B 163:224. https://doi.org/10.1098/rspb.1965.0069 (PMID 4378482)
  - Russell JB, Cook GM (1995) Microbiol Rev 59:48. https://doi.org/10.1128/mr.59.1.48-62.1995 (PMID 7708012)
  - van Bodegom P (2007) Microb Ecol 53:513. https://doi.org/10.1007/s00248-006-9049-5 (PMID 17333428)
  - Tempest DW, Neijssel OM (1984) Annu Rev Microbiol 38:459. https://doi.org/10.1146/annurev.mi.38.100184.002331 (existence only)
  - Price PB, Sowers T (2004) PNAS 101:4631. https://doi.org/10.1073/pnas.0400522101 (PMID 15070769)
  - Hoehler TM, Joergensen BB (2013) Nat Rev Microbiol 11:83. https://doi.org/10.1038/nrmicro2939 (PMID 23321532)
  - Biselli E, Schink SJ, Gerland U (2020) Mol Syst Biol 16:e9478. https://doi.org/10.15252/msb.20209478 (PMID 32500952)
  - Bauchop T, Elsden SR (1960) J Gen Microbiol 23:457. https://doi.org/10.1099/00221287-23-3-457 (existence only; the classic Y_ATP of about 10.5 g dry weight per mol ATP is from this paper, not text-verified)
  - Stouthamer AH (1973) Antonie van Leeuwenhoek 39:545. https://doi.org/10.1007/bf02578899 (PMID 4148026; ATP needed for cell material, not text-verified)

## 12. Bacterial growth laws and proteome allocation

- **Primitive:** The proteome is a fixed budget. Ribosomes make protein, so growth rate needs ribosomal protein in proportion to growth rate. Metabolic enzymes for nutrient uptake and catabolism take another share. A growth-rate-independent core takes a third. Adding useless protein (or running an unneeded pathway) reduces the share available for the growth-limiting sector and slows growth, with no need for the useless protein to do anything.
- **Numbers:** Gratuitous protein expression slows growth in a way a resource-allocation theory predicts quantitatively (verified in abstract, Scott 2010). The growth-rate-dependent components of the proteome make up about half of it by mass and fit a flux model with two parameters (verified in abstract and full text, Hui 2015). In the Basan fit the energy-sector offset at zero growth is about 19 percent of the proteome and the total cap for growth-relevant sectors is about 0.42 (verified in full text). E. coli at its replication-speed limit makes about four mass-equivalents of non-ribosomal protein per mass-equivalent of ribosomes, explained by fitness being energy efficiency under fast growth (verified in abstract, Maitra and Dill 2015). The specific ribosomal fraction values (about 55 percent maximum) from Scott 2010 are not text-verified here.
- **Trade-off:** Every gene product competes with every other for one budget. Fast growth needs ribosomes, which crowds out catabolic and stress capacity. Slow growth keeps spare capacity for hard times.
- **Sources:**
  - Scott M, Gunderson CW, Mateescu EM, Zhang Z, Hwa T (2010) Science 330:1099. https://doi.org/10.1126/science.1192588 (PMID 21097934)
  - Hui S et al (2015) Mol Syst Biol 11:784. https://doi.org/10.15252/msb.20145697 (PMID 25678603)
  - Maitra A, Dill KA (2015) PNAS 112:406. https://doi.org/10.1073/pnas.1421138111 (PMID 25548180)
  - Basan M et al (2015) https://doi.org/10.1038/nature15765

## 13. Cost of protein expression

- **Primitive:** Making a protein costs ATP-equivalents for amino-acid synthesis and polymerisation, plus ribosome and RNA polymerase occupancy. In E. coli the measured burden of useless expression sits in the act of transcription and translation, not in the product's activity or amino-acid content.
- **Numbers:** For the lac operon the fitness cost of unneeded constitutive expression is tied to the process of expressing, not the products; expressed lactose permease can be costly or beneficial depending on environment (verified in abstract, Stoebel 2008). The lac system has a cost-benefit optimum expression level per lactose concentration, and cells evolved to that optimum within a few hundred generations (verified in abstract, Dekel and Alon 2005). Per-gene energy costs scale with cell volume, are large enough to be seen by selection in bacteria and not in eukaryotes (verified in abstract, Lynch and Marinov 2015). Their absolute ATP numbers are contested by others and I did not verify them, so treat the figure of a few ATP per peptide bond only as the textbook minimum.
- **Trade-off:** Evolution trims unused capacity, so having a gene ready "just in case" costs growth. This is why regulation, induction lags and repression exist at all.
- **Sources:**
  - Stoebel DM, Dean AM, Dykhuizen DE (2008) Genetics 178:1653. https://doi.org/10.1534/genetics.107.085399 (PMID 18245823)
  - Dekel E, Alon U (2005) Nature 436:588. https://doi.org/10.1038/nature03842 (PMID 16049495)
  - Lynch M, Marinov GK (2015) PNAS 112:15690. https://doi.org/10.1073/pnas.1514974112 (PMID 26575626)

## 14. Size control: the adder

- **Primitive:** A cell does not divide at a fixed size nor after a fixed time. It adds a roughly constant volume between birth and division, whatever its size at birth, so size deviations decay within a few generations. Mean size scales with nutrient-set growth rate.
- **Numbers:** In E. coli and Caulobacter, cells grow the same amount between divisions regardless of birth length, abating deviations exponentially within a few generations, with constant extension implemented at or near division (verified in abstract, Campos 2014). Hundreds of thousands of E. coli and B. subtilis cells across steady-state conditions add a constant volume per generation, irrespective of newborn size, supporting the constant-delta model (verified in abstract, Taheri-Araghi 2015). The dependence of cell size and RNA content on medium (larger cells in richer medium) is from Schaechter, Maaloe and Kjeldgaard 1958 (existence only).
- **Trade-off:** An adder corrects noise without precise sensing. A cell that grows fast adds more per cycle; slow growers stay small. Size ties to the surface-to-volume budget for transporters and respiration (sections 1, 5).
- **Sources:**
  - Campos M et al (2014) Cell 159:1433. https://doi.org/10.1016/j.cell.2014.11.022 (PMID 25480302)
  - Taheri-Araghi S et al (2015) Curr Biol 25:385. https://doi.org/10.1016/j.cub.2014.12.009 (PMID 25544609)
  - Schaechter M, Maaloe O, Kjeldgaard NO (1958) J Gen Microbiol 19:592. https://doi.org/10.1099/00221287-19-3-592 (existence only)

## 15. Storage polymers (glycogen, PHB)

- **Primitive:** When carbon is plentiful but another nutrient limits growth, cells polymerise carbon into insoluble reserves inside the cell: glycogen (a glucose polymer) or poly-3-hydroxybutyrate (PHB, in a small number of cytoplasmic granules). Insoluble polymer adds little osmotic load. Reserves are depolymerised for ATP when external food is gone.
- **Numbers:** PHAs, with PHB the most abundant, are carbon and energy reserve materials of widespread occurrence, existing as a small number of cytoplasmic granules per cell (verified in abstract, Anderson and Dawes 1990). Accumulation raises survival and stress tolerance in changing, competitive environments (verified in abstract, Kadouri 2005). In Sinorhizobium meliloti, starved dividing cells make two daughters with high and low PHB: high-PHB cells can survive over a year without food, low-PHB cells compete better for resources (verified in abstract, Ratcliff and Denison 2010). Glycogen synthesis is a main metabolic storage strategy across yeast and bacteria, regulated by many nutrient-sensing mechanisms (verified in abstract, Wilson 2010). PHB mass as a percentage of dry weight (a very high figure is often quoted) is not verified here.
- **Trade-off:** A reserve is biomass that does not grow or divide the cell, so carrying it slows the cell in good times and is paid back only if starvation comes. Uneven splitting of reserves at division is a bet-hedge built into the mother cell.
- **Sources:**
  - Anderson AJ, Dawes EA (1990) Microbiol Rev 54:450. https://doi.org/10.1128/mr.54.4.450-472.1990 (PMID 2087222)
  - Kadouri D, Jurkevitch E, Okon Y, Castro-Sowinski S (2005) Crit Rev Microbiol 31:55. https://doi.org/10.1080/10408410590899228 (PMID 15986831)
  - Ratcliff WC, Denison RF (2010) Curr Biol 20:1740. https://doi.org/10.1016/j.cub.2010.08.036 (PMID 20869244)
  - Wilson WA et al (2010) FEMS Microbiol Rev 34:952. https://doi.org/10.1111/j.1574-6976.2010.00220.x (PMID 20412306)
  - Dawes EA, Senior PJ (1973) Adv Microb Physiol 10:135. https://doi.org/10.1016/s0065-2911(08)60088-0 (existence only)

## 16. Dormancy and sporulation

- **Primitive:** Dormancy is a reversible low-activity state. Sporulation in Bacillus is an irreversible, multi-stage differentiation: the cell splits asymmetrically, the smaller compartment is engulfed and wrapped in protective layers, and the mother cell lyses. A spore pays almost no maintenance and revives on a nutrient cue (germination) followed by outgrowth. Commitment is a decision with a delay, and cells can postpone it by killing siblings for food.
- **Numbers:** In B. subtilis microcolonies after a nutrient downshift, first pre-spores appear 10 to 20 h later ("early spores") and a second wave 20 h or more later, roughly half and half; early spores germinate about twice as fast, with more than 90 percent germination after 2 h under a saturating stimulus (verified in full text, Mutlu 2018). The timing of sporulation sets spore quality through carry-over from the vegetative cell, a quantity versus quality trade-off (verified in abstract and full text). Sporulating cells export a killing factor and a signal that lyse sister cells and let the sporulating cells feed on the released nutrients, which delays commitment, so sporulation is a last-resort stress response (verified in abstract, Gonzalez-Pastor 2003). Cells at the same moment use bet-hedging: some sporulate, others use alternative metabolites to keep growing, and the decision carries intergenerational memory across more than two generations through positive feedback (verified in abstract, Veening 2008). Dormancy as a bet-hedging seed bank (resuscitation after environmental change) is reviewed in Lennon and Jones 2011 (verified in abstract). A full ATP cost of making a spore was not found in a verifiable source, so I give none.
- **Trade-off:** Sporulate early and you lock in a lower-quality, slower-reviving spore and forgo growth. Sporulate late and you may starve first. Cannibalism turns siblings into a food source for the delay.
- **Sources:**
  - Mutlu A et al (2018) Nat Commun 9:69. https://doi.org/10.1038/s41467-017-02477-1 (PMID 29302032)
  - Gonzalez-Pastor JE, Hobbs EC, Losick R (2003) Science 301:510. https://doi.org/10.1126/science.1086462 (PMID 12817086)
  - Veening JW et al (2008) PNAS 105:4393. https://doi.org/10.1073/pnas.0700463105 (PMID 18326026)
  - Lennon JT, Jones SE (2011) Nat Rev Microbiol 9:119. https://doi.org/10.1038/nrmicro2504 (PMID 21233850)
  - Higgins D, Dworkin J (2012) FEMS Microbiol Rev 36:131. https://doi.org/10.1111/j.1574-6976.2011.00310.x (PMID 22091839)

## 17. Persisters and bet hedging in fluctuating environments

- **Primitive:** A small fraction of a clonal population is in a slow-growing or non-growing state and survives stresses that kill growing cells. Cells switch spontaneously and reversibly between normal and persister states. Persisters have low energy status, with low ATP or low levels of energy-generating enzymes, so the state is cheap to hold and costly to leave.
- **Numbers:** E. coli persistence is a phenotypic switch between normally growing cells and slow-growing persister cells that preexist the stress (verified in abstract, Balaban 2004). Switching beats sensing when the environment changes infrequently, and optimal switching rates mimic the statistics of environmental change (verified in abstract, Kussell and Leibler 2005). The optimal persister switching rate depends strongly on how often the environment changes and only weakly on selection strength, and wild-type persistence suits rare stress (verified in abstract, Kussell 2005 Genetics). Cells with low ATP survive ampicillin better and these cells form stochastically from fluctuations in energy-generating enzymes (verified in abstract, Manuse 2021). The actual switching rates per generation are in the Balaban and Kussell papers but I could not read them, so no rate is stated here.
- **Trade-off:** Persisters lose growth rate in good conditions (insurance premium) and win under rare lethal stress. The premium is tuned to the frequency of stress.
- **Sources:**
  - Balaban NQ, Merrin J, Chait R, Kowalik L, Leibler S (2004) Science 305:1622. https://doi.org/10.1126/science.1099390 (PMID 15308767)
  - Kussell E, Leibler S (2005) Science 309:2075. https://doi.org/10.1126/science.1114383 (PMID 16123265)
  - Kussell E, Kishony R, Balaban NQ, Leibler S (2005) Genetics 169:1807. https://doi.org/10.1534/genetics.104.035352 (PMID 15687275)
  - Manuse S et al (2021) PLoS Biol 19:e3001194. https://doi.org/10.1371/journal.pbio.3001194 (PMID 33872303)
  - Lewis K (2010) https://doi.org/10.1146/annurev.micro.112408.134306

## 18. Corpses and recycling (starvation side of the ledger)

- **Primitive:** A dead cell lyses and its stores (protein, nucleic acids, polymers) become a dissolved food source in the local field, which survivors can take up.
- **Numbers:** The recycling yield of E. coli dead cells is 12 to 23 percent depending on prior growth rate, about eight dead cells per new cell at the low end (verified in full text, Biselli 2020). The cannibalism result in section 16 shows living cells can also produce corpses on purpose.
- **Trade-off:** Corpse recycling lengthens the survival of a crowd at the cost of those who died, and turns death into a food pulse that rewards fast scavengers.
- **Sources:**
  - Biselli E, Schink SJ, Gerland U (2020) https://doi.org/10.15252/msb.20209478
  - Gonzalez-Pastor JE, Hobbs EC, Losick R (2003) https://doi.org/10.1126/science.1086462

## 19. Toxic waste, acidification and self-limitation

- **Primitive:** Fermentation exports weak acids (acetate, lactate). In the neutral external medium they are mostly charged, but the protonated fraction crosses the membrane, releases its proton inside and re-exports the anion by diffusion cycles, which drains PMF and lowers internal pH. Cumulative excretion lowers the external pH too, and growth can stop below a species-specific range. This makes a population limit itself by its own waste.
- **Numbers:** At pH 6.0, 8 mM acetate halves the specific growth rate of E. coli, via depletion of the methionine pool and homocysteine accumulation on top of lowering of cytoplasmic pH (verified in abstract, Roe 2002). Growth rate falls logarithmically with acetate concentration (verified in abstract, Luli and Strohl 1990), and in glucose-fed cultures some strains reached about 2 g per litre of acetate for 30 g per litre of biomass and others 8 g per litre acetate with less than 10 g per litre biomass (verified in abstract). Paenibacillus modifies environmental pH so strongly at high density that whole populations go extinct (ecological suicide), and lowering growth rate by antibiotics can rescue them (verified in abstract, Ratzke 2018, Nat Ecol Evol). pH modification creates feedback loops that can facilitate or inhibit growth and create interaction motifs: bistability, successive growth, extended suicide, stabilisation (verified in abstract, Ratzke and Gore 2018, PLoS Biol). Cytoplasmic pH homeostasis across bacteria and archaea uses buffering, membrane adaptations, ion transport and metabolic consumption of acids and bases (verified in abstract, Slonczewski 2009). The mechanism by which fermentation acids damage cells by anion accumulation, as opposed to uncoupling, is Russell 1992 (existence only, not text-verified).
- **Trade-off:** Fast fermentation brings a poison. Species that consume the waste (or neutralise the medium) create room for others. Species that keep acidifying set their own ceiling.
- **Sources:**
  - Roe AJ, O'Byrne C, McLaggan D, Booth IR (2002) Microbiology 148:2215. https://doi.org/10.1099/00221287-148-7-2215 (PMID 12101308)
  - Luli GW, Strohl WR (1990) Appl Environ Microbiol 56:1004. https://doi.org/10.1128/aem.56.4.1004-1011.1990 (PMID 2187400)
  - Ratzke C, Denk J, Gore J (2018) Nat Ecol Evol 2:867. https://doi.org/10.1038/s41559-018-0535-1 (PMID 29662223)
  - Ratzke C, Gore J (2018) PLoS Biol 16:e2004248. https://doi.org/10.1371/journal.pbio.2004248 (PMID 29538378)
  - Slonczewski JL, Fujisawa M, Dopson M, Krulwich TA (2009) Adv Microb Physiol 55:1. https://doi.org/10.1016/s0065-2911(09)05501-5 (PMID 19573695)
  - Russell JB (1992) J Appl Bacteriol 73:363. https://doi.org/10.1111/j.1365-2672.1992.tb04990.x (existence only)

## 20. Archaeal energy strategies (what differs)

- **Primitive:** Archaeal membranes use ether-linked lipids with low proton and sodium leakiness, so a given PMF is cheaper to hold. Many archaea live at the energetic edge: slow, low-yield, near the thermodynamic minimum energy packet.
- **Numbers:** Adaptation to chronic energy stress is proposed as the main selective pressure that sets archaea apart from bacteria, through low-permeability membranes and specific catabolic pathways (verified in abstract, Valentine 2007). Mesophilic, hyperthermophilic and halophilic archaea adjust membrane lipid so proton permeability at growth temperature stays low and constant, and hyperthermophiles prefer ATP-driven uptake (verified in abstract, Konings 2002). The archaeon Sulfolobus acidocaldarius holds a PMF of 140 to 160 mV at pH 3.5 and drives ATP synthase from it (verified in abstract, Luebben and Schaefer 1989). Methanogens and syntrophic partners live on 20 to 25 kJ per mol per reaction (section 10).
- **Trade-off:** Archaeal-style "low leak, low yield, slow" is a cheap way to survive on tiny energy fluxes, bought at the price of growth rate.
- **Sources:**
  - Valentine DL (2007) Nat Rev Microbiol 5:316. https://doi.org/10.1038/nrmicro1619 (PMID 17334387)
  - Konings WN et al (2002) https://doi.org/10.1023/a:1020573408652
  - Luebben M, Schaefer G (1989) https://doi.org/10.1128/jb.171.11.6106-6116.1989
  - Schink B (1997) https://doi.org/10.1128/mmbr.61.2.262-280.1997

## Top five for a simulator

Ranked by strategy diversity per unit of physics (one new law each, many strategies):

1. **Rate versus yield per unit machinery (sections 5, 6, 12):** one law, two energy pathways with different ATP-per-resource and ATP-per-machinery, plus a shared capacity budget, gives fast-wasteful versus slow-efficient lifestyles, overflow, and cooperative versus competitive resource use (Pfeiffer, Basan, Szenk).
2. **Diffusible products versus contact capture (sections 3, 4):** one rule that a secreted product spreads in the field produces exoenzyme producers, cheaters, siderophore-style conditional producers, and spatial clustering, with the outcome set by diffusion length versus cell spacing.
3. **Leaky by-products as a second food (sections 6, 9, 10):** if overflow or partial oxidation puts a molecule in the field that others can consume, cross-feeding, division of labour, successions and obligate partners appear without any new organism-level rule.
4. **Maintenance-limited starvation with reserves, dormancy and corpses (sections 11, 15, 16, 17, 18):** a drain proportional to biomass plus optional reserves and a low-drain state gives bet hedging, storage versus growth, and necromass recycling, all from one balance equation.
5. **Affinity versus capacity of uptake with regulation lag (sections 1, 2, 8, 13):** two transporter properties (half-saturation and maximal rate, both costing budget) plus an induction delay give oligotroph versus copiotroph and diauxic lag.

Runner-up: waste acidification as a field that feeds back on growth (section 19), which gives self-limitation and collapse (ecological suicide) for one extra diffusing, decaying variable.
