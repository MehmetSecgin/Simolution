package com.simolution.kernel.runtime;

import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneDecoder;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class Kernel {

    private double[] outputsPrev;
    private double[] outputsNext;
    private final double[] accumulators;
    private final double[] delayMemory;

    private final CompiledConnection[] connections;
    private final int[] sumStart;
    private final int[] sumEnd;
    private final int[] mulStart;
    private final int[] mulEnd;

    private final int[] genes;
    private final int[] geneCount;
    private final int maxGenes;

    private final double[] mulTop1;
    private final double[] mulTop2;
    private final int[] mulInDegree;

    private final double[] energy;
    private final double[] damage;
    private final int[] harvestConnCount;
    private final int[] activeThisTick;
    private double energySink;

    private final long[] lineageId;
    private final int[] generation;
    private long birthsTotal;
    private int maxGeneration;
    private final double creditedInitialEnergy;

    private double[] resourceField;
    private double[] resourceFieldNext;
    private final double initialResourceTotal;
    private double cumulativeInflow;

    private final int worldWidth;
    private final int unitCount;
    private int freeSlotCursor;

    private final int[] position;
    private final int[] cellOccupant;

    private int tick = 0;

    /**
     * The substrate is a fixed pool of {@code genomes.length} slots; a slot is
     * alive iff its energy is &gt; 0 (contract v2 §5/§6, death is derived). Each
     * slot owns a fixed region {@code [slot·maxGenes, slot·maxGenes + maxGenes)}
     * of the flat connection array and an equal region of the raw {@code genes}
     * array, both rewritten in place when the slot is reused by a birth — no
     * growth, no per-birth heap allocation beyond decoding (contract v2 §12).
     * {@code maxGenes} is the per-slot gene capacity (headroom for genome growth,
     * §16); the live extent is {@code geneCount[slot]}.
     * <p>
     * Within a slot's region the compile lays out sum-destined connections first
     * (in gene order) then mul-destined ones (in gene order). MUL needs its two
     * strongest individual inputs, which the summing accumulator destroys, so it
     * runs a separate top-2 loop (ADR 0005); the contiguous per-slot ranges let a
     * dead slot be skipped with one branch (ADR 0006). Both arrays are sized to
     * the pool's full capacity {@code unitCount · maxGenes} up front (contract v2
     * §12 footprint note).
     */
    public Kernel(final int[][] initialGenomes, final int worldWidth, final int maxGenes,
            final int[] founderCells) {

        final int maxUnits = worldWidth * worldWidth;
        final int seededCount = initialGenomes.length;
        if (seededCount > maxUnits) {
            throw new IllegalArgumentException(
                    "initial population " + seededCount + " exceeds grid capacity "
                    + maxUnits + " (worldWidth " + worldWidth + ")");
        }
        if (founderCells.length != seededCount) {
            throw new IllegalArgumentException(
                    "founderCells length " + founderCells.length
                    + " must equal founder count " + seededCount);
        }
        this.worldWidth = worldWidth;
        this.unitCount = maxUnits;
        this.maxGenes = maxGenes;

        final int totalNodes = maxUnits * NodeLayout.TOTAL;

        this.outputsPrev = new double[totalNodes];
        this.outputsNext = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.delayMemory = new double[totalNodes];

        this.mulTop1 = new double[maxUnits];
        this.mulTop2 = new double[maxUnits];
        this.mulInDegree = new int[maxUnits];

        // empty slots start dead (energy 0, contract v2 §5): they are skipped
        // by every phase exactly as corpses are, and a birth may claim them.
        this.energy = new double[maxUnits];
        this.damage = new double[maxUnits];
        this.harvestConnCount = new int[maxUnits];
        this.activeThisTick = new int[maxUnits];

        this.lineageId = new long[maxUnits];
        Arrays.fill(lineageId, -1L);
        this.generation = new int[maxUnits];
        this.birthsTotal = 0L;
        this.maxGeneration = 0;
        this.creditedInitialEnergy = KernelConfig.INITIAL_ENERGY * seededCount;

        this.resourceField = new double[maxUnits];
        this.resourceFieldNext = new double[maxUnits];
        Arrays.fill(resourceField, KernelConfig.CELL_INITIAL);
        this.initialResourceTotal = KernelConfig.CELL_INITIAL * maxUnits;
        this.cumulativeInflow = 0.0;

        this.position = new int[maxUnits];
        Arrays.fill(position, -1);
        this.cellOccupant = new int[maxUnits];
        Arrays.fill(cellOccupant, -1);
        this.freeSlotCursor = 0;

        final int capacity = maxUnits * maxGenes;
        this.connections = new CompiledConnection[capacity];
        this.genes = new int[capacity];
        this.geneCount = new int[maxUnits];
        this.sumStart = new int[maxUnits];
        this.sumEnd = new int[maxUnits];
        this.mulStart = new int[maxUnits];
        this.mulEnd = new int[maxUnits];

        for (int unit = 0; unit < maxUnits; unit++) {
            if (unit < seededCount) {
                final int[] genome = initialGenomes[unit];
                if (genome.length > maxGenes) {
                    throw new IllegalArgumentException(
                            "unit " + unit + " genome has " + genome.length
                            + " genes, exceeds maxGenes " + maxGenes);
                }
                System.arraycopy(genome, 0, genes, unit * maxGenes, genome.length);
                geneCount[unit] = genome.length;
                energy[unit] = KernelConfig.INITIAL_ENERGY;
                lineageId[unit] = unit;
                final int cell = founderCells[unit];
                if (cell < 0 || cell >= maxUnits) {
                    throw new IllegalArgumentException(
                            "founder cell " + cell + " out of range [0, " + maxUnits + ")");
                }
                if (cellOccupant[cell] >= 0) {
                    throw new IllegalArgumentException(
                            "founder cell " + cell + " assigned to more than one founder");
                }
                position[unit] = cell;
                cellOccupant[cell] = unit;
            }
            compileSlot(unit);
        }
    }

    /**
     * Founder placement policy (ADR 0022): deterministically scatter
     * {@code seededCount} founders over distinct cells of a
     * {@code worldWidth × worldWidth} lattice. Returns, for each founder slot
     * {@code [0, seededCount)}, the cell it starts in — pass straight to the
     * constructor's {@code founderCells}.
     * <p>
     * A partial Fisher-Yates shuffle over the identity permutation
     * {@code [0, W²)} drives the draws, keyed off the placement RNG
     * ({@link Noise#placementUniform}, seeded by {@code KernelConfig.RANDOM_SEED}
     * like RAND and mutation). This replaces the old slot==cell fill, which
     * packed founders into a contiguous row-major strip at one edge and imposed
     * an artificial density gradient on every run. The scatter is uniform and
     * reproducible: same count + grid → identical layout. Placement is a setup
     * policy, not a kernel law, so it lives here as a static helper the harness
     * calls, leaving the constructor to take explicit cells.
     */
    public static int[] scatterFounders(final int seededCount, final int worldWidth) {
        final int cellCount = worldWidth * worldWidth;
        final int[] cell = new int[cellCount];
        for (int i = 0; i < cellCount; i++) {
            cell[i] = i;
        }
        final int[] founderCell = new int[seededCount];
        for (int i = 0; i < seededCount; i++) {
            final double u = Noise.placementUniform(KernelConfig.RANDOM_SEED, i);
            final int j = i + (int) (u * (cellCount - i));
            final int tmp = cell[i];
            cell[i] = cell[j];
            cell[j] = tmp;
            founderCell[i] = cell[i];
        }
        return founderCell;
    }

    /**
     * Decode a slot's live genes into its connection region and recompute the
     * structure-derived per-slot data (ranges, harvest transporter count, MUL
     * in-degree). Runs once per slot at construction and again at every birth
     * (contract v2 §6/§12) — off the hot path, never per tick. Two decode passes
     * place sum-destined connections (in gene order) then mul-destined ones (in
     * gene order) contiguously without a scratch buffer; this is the same
     * per-unit ordering the old global packed array produced, so results stay
     * bit-identical across the storage rework.
     */
    private void compileSlot(final int unit) {
        final int base = unit * maxGenes;
        final int unitOffset = unit * NodeLayout.TOTAL;
        final int count = geneCount[unit];

        int cursor = base;
        int harvest = 0;
        for (int g = 0; g < count; g++) {
            final CompiledConnection c = GeneDecoder.decode(genes[base + g], unitOffset);
            if (c == null || isMulDestination(c)) {
                continue;
            }
            connections[cursor++] = c;
            if (isHarvestTransporter(c)) {
                harvest++;
            }
        }
        sumStart[unit] = base;
        sumEnd[unit] = cursor;

        mulStart[unit] = cursor;
        int mulDegree = 0;
        for (int g = 0; g < count; g++) {
            final CompiledConnection c = GeneDecoder.decode(genes[base + g], unitOffset);
            if (c == null || !isMulDestination(c)) {
                continue;
            }
            connections[cursor++] = c;
            mulDegree = Math.min(2, mulDegree + 1);
        }
        mulEnd[unit] = cursor;

        harvestConnCount[unit] = harvest;
        mulInDegree[unit] = mulDegree;
    }

    private static boolean isMulDestination(final CompiledConnection connection) {
        final int dstLocal = connection.destinationAbsoluteIndex % NodeLayout.TOTAL;
        return dstLocal == NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
    }

    /**
     * A "transporter": a connection feeding the HARVEST node from a meaningful
     * source. The count of these is a unit's structural uptake investment — its
     * intake ceiling scales with it (contract v1 §5, ADR 0012), so eating
     * capacity is a heritable genome trait, not a global grant. Junk-source
     * connections carry no signal, so they are not transporters.
     */
    private static boolean isHarvestTransporter(final CompiledConnection connection) {
        final int dstLocal = connection.destinationAbsoluteIndex % NodeLayout.TOTAL;
        if (dstLocal != NodeLayout.ACTION_OFFSET + NodeLayout.Action.HARVEST) {
            return false;
        }
        final int srcLocal = connection.sourceAbsoluteIndex % NodeLayout.TOTAL;
        return NodeLayout.isMeaningful(srcLocal);
    }

    public void tick() {
        // Phase 1
        clearAccumulators();
        // Phase 2
        propagateConnections();
        // Phase 3
        evaluateNodes();
        // Phase 4
        swapBuffers();
        // Phase 5
        settleIntake();
        // Phase 6
        settleCost();
        // Phase 7
        settleReproduction();
        // Phase 8
        settleMovement();
        // Phase 9
        settleDiffusion();

        tick++;
    }

    private void clearAccumulators() {
        Arrays.fill(accumulators, 0.0);
        Arrays.fill(mulTop1, 0.0);
        Arrays.fill(mulTop2, 0.0);
    }

    /**
     * Per-unit propagation. Dead units (energy &lt;= 0) are skipped entirely —
     * the one alive-check per unit replaces what would be a branch per
     * connection, and a dying population literally costs less to simulate.
     * Counts non-zero propagations per unit; that count is the activity the
     * energy settle phase charges for (contract v0 §6, fork 1a).
     */
    private void propagateConnections() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                activeThisTick[unit] = 0;
                continue;
            }

            int active = 0;

            for (int i = sumStart[unit]; i < sumEnd[unit]; i++) {
                final CompiledConnection c = connections[i];
                final double signal = outputsPrev[c.sourceAbsoluteIndex] * c.weight;
                accumulators[c.destinationAbsoluteIndex] += signal;
                if (signal != 0.0) {
                    active++;
                }
            }

            for (int i = mulStart[unit]; i < mulEnd[unit]; i++) {
                final CompiledConnection c = connections[i];
                final double signal = outputsPrev[c.sourceAbsoluteIndex] * c.weight;
                if (Math.abs(signal) > Math.abs(mulTop1[unit])) {
                    mulTop2[unit] = mulTop1[unit];
                    mulTop1[unit] = signal;
                } else if (Math.abs(signal) > Math.abs(mulTop2[unit])) {
                    mulTop2[unit] = signal;
                }
                if (signal != 0.0) {
                    active++;
                }
            }

            activeThisTick[unit] = active;
        }
    }

    private void evaluateNodes() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                freezeDeadUnit(unit);
            } else {
                evaluateUnit(unit);
            }
        }
    }

    /**
     * A dead unit computes nothing (contract v0 §9), but the double buffer
     * would otherwise surface its state from two ticks ago after the swap.
     * Carrying the previous outputs forward freezes the corpse at its last
     * living configuration — inert, unchanging.
     */
    private void freezeDeadUnit(final int unit) {
        final int base = unit * NodeLayout.TOTAL;
        System.arraycopy(outputsPrev, base, outputsNext, base, NodeLayout.TOTAL);
    }

    private void evaluateUnit(final int unit) {
        final int base = unit * NodeLayout.TOTAL;

        final int constIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        final int localResourceIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.LOCAL_RESOURCE * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int selfEnergyIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.SELF_ENERGY * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        outputsNext[constIdx] = 1.0;
        outputsNext[randIdx] = Noise.sample(KernelConfig.RANDOM_SEED, unit, tick);
        // a unit senses only the cell it currently occupies (contract v3 §3)
        outputsNext[localResourceIdx] = Math.min(1.0, resourceField[position[unit]] / KernelConfig.CELL_CAPACITY);
        outputsNext[selfEnergyIdx] = Math.min(1.0, energy[unit] / KernelConfig.SELF_ENERGY_SCALE);

        final int addIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[addIdx] = accumulators[addIdx];

        // MUL by structural in-degree: 0 inputs -> 0; 1 input -> that signal
        // (the binding slice's own example wires DELAY -> MUL -> ADD, so a
        // lone input must pass through); 2+ inputs -> product of the two
        // strongest (slice section 2, ADR 0005)
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[mulIdx] = switch (mulInDegree[unit]) {
            case 0 -> 0.0;
            case 1 -> mulTop1[unit];
            default -> mulTop1[unit] * mulTop2[unit];
        };

        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final double clampIn = accumulators[clampIdx];
        outputsNext[clampIdx] = Math.max(-1.0, Math.min(1.0, clampIn));

        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[delayIdx] = delayMemory[delayIdx];
        delayMemory[delayIdx] = accumulators[delayIdx];

        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[threshIdx] = accumulators[threshIdx] > 0.0 ? 1.0 : -1.0;

        final int actionIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.Y * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[actionIdx] = accumulators[actionIdx];

        final int harvestIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.HARVEST * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[harvestIdx] = accumulators[harvestIdx];

        final int reproduceIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.REPRODUCE * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[reproduceIdx] = accumulators[reproduceIdx];

        final int moveNIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.MOVE_N * NodeLayout.Action.INSTANCES_PER_TYPE);
        final int moveSIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.MOVE_S * NodeLayout.Action.INSTANCES_PER_TYPE);
        final int moveEIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.MOVE_E * NodeLayout.Action.INSTANCES_PER_TYPE);
        final int moveWIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.MOVE_W * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[moveNIdx] = accumulators[moveNIdx];
        outputsNext[moveSIdx] = accumulators[moveSIdx];
        outputsNext[moveEIdx] = accumulators[moveEIdx];
        outputsNext[moveWIdx] = accumulators[moveWIdx];
    }

    private void swapBuffers() {
        final double[] tmp = outputsPrev;
        outputsPrev = outputsNext;
        outputsNext = tmp;
    }

    /**
     * Phase 5 — energy intake, now <b>cell-local</b> (contract v3 §4, supersedes
     * v1's shared-reservoir allocation). Slot index == cell index, so each alive
     * unit draws from its own cell only: {@code intake = min(demand, cellResource)}
     * with the unchanged saturating {@code demand} (contract v1 §5). Because cells
     * are exclusive (one unit per cell, contract v3 §2) there is no within-cell
     * contention — the v1 proportional rationing across competitors disappears;
     * competition for a cell is settled by who lives there (birth/death), not a
     * shared-pool factor. The cell is debited by exactly the intake.
     * <p>
     * Every cell then admits up to {@code CELL_INFLOW}, capped at
     * {@code CELL_CAPACITY}; the summed admission is the sole energy the audit
     * treats as entering the system (cumulativeInflow), so {@code INITIAL_total +
     * Σ initialCellResource + cumulativeInflow == Σ energy + Σ cellResource + sink}
     * stays exact (contract v3 §7). Intake runs before cost so a unit can pay this
     * tick's metabolism with this tick's harvest; harvest output is read from
     * outputsPrev, the just-swapped current tick. Diffusion (phase 8) relaxes the
     * field afterward.
     */
    private void settleIntake() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double demand = harvestDemand(unit);
            if (demand <= 0.0) {
                continue;
            }
            final int cell = position[unit];
            final double intake = Math.min(demand, resourceField[cell]);
            energy[unit] += intake;
            resourceField[cell] -= intake;
        }

        for (int cell = 0; cell < unitCount; cell++) {
            final double admitted = Math.min(KernelConfig.CELL_INFLOW,
                    Math.max(0.0, KernelConfig.CELL_CAPACITY - resourceField[cell]));
            resourceField[cell] += admitted;
            cumulativeInflow += admitted;
        }
    }

    /**
     * Saturating uptake with an <b>emergent</b> ceiling (contract v1 §5, ADR
     * 0012). Demand is {@code capacity · σ(output)} where:
     * <ul>
     *   <li>{@code capacity = CAPACITY_PER_CONNECTION · transporterCount} — the
     *       intake ceiling, set by the unit's structural investment in harvest
     *       wiring (counted once at construction) and paid for through ordinary
     *       per-connection decay. Eating capacity is therefore a heritable,
     *       evolvable genome trait, not a global constant grant. It is bounded by
     *       the genome size, so it cannot diverge.</li>
     *   <li>{@code σ(output) = output / (HALF_SATURATION + output) ∈ [0,1)} — how
     *       far the cell is currently driving its mouth open. Saturating, so a
     *       runaway/divergent signal only approaches the ceiling, never exceeds
     *       it: there is no payoff to exploding a feedback loop into the mouth,
     *       and the demand total stays bounded (the shared denominator cannot
     *       overflow).</li>
     * </ul>
     * A non-finite output is unreadable garbage, not a harvest action, so it
     * demands nothing; {@code max(0, …)} also means you cannot un-eat.
     */
    private double harvestDemand(final int unit) {
        final int transporters = harvestConnCount[unit];
        if (transporters == 0) {
            return 0.0;
        }
        final int harvestIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                + (NodeLayout.Action.HARVEST * NodeLayout.Action.INSTANCES_PER_TYPE);
        final double output = outputsPrev[harvestIdx];
        if (!Double.isFinite(output) || output <= 0.0) {
            return 0.0;
        }
        final double capacity = KernelConfig.HARVEST_CAPACITY_PER_CONNECTION * transporters;
        // capacity · output/(HALF+output), rearranged to avoid overflow when
        // output is a huge (divergent) finite value.
        return capacity / (1.0 + KernelConfig.HARVEST_HALF_SATURATION / output);
    }

    /**
     * Phase 6 — the entropy law (contract v0 §3, §4, §6). Each unit that was
     * alive this tick pays structural decay (∝ its connection count, charged
     * regardless of activity) plus activity cost (∝ non-zero propagations).
     * The charge is clamped to available energy so a unit can never overdraw:
     * the sink receives exactly what existed, keeping the audit invariant exact.
     * <p>
     * A unit alive at tick start computes the full tick and pays for it;
     * crossing to zero here makes phases 2–3 skip it from the next tick on,
     * so death takes effect next tick. Because intake (phase 5) ran first, a
     * unit's net energy change this tick is {@code intake − charge}: a strong
     * harvester reaches steady state, a poor one still decays to death.
     * <p>
     * The charge is the cell's metabolic bill (contract v1 §6/§6a, ADR 0013),
     * modelled on real maintenance energy (Pirt) rather than a per-gene tax:
     * <ul>
     *   <li><b>basal</b> ({@code BASAL_COST}) — the fixed, irreducible cost of
     *       staying organized (membrane upkeep). Charged to every living unit
     *       regardless of size or activity; this floor is what makes a dormant
     *       unit actually die instead of decaying asymptotically.</li>
     *   <li><b>activity</b> (∝ propagations) — the cost of running/expressing
     *       machinery: you pay for signal you move, not for wiring you merely
     *       carry (so silent/junk structure is nearly free, as in biology).</li>
     *   <li><b>maintenance</b> (∝ energy held) — upkeep proportional to size
     *       (energy proxies biomass). Makes a hoard above the equilibrium
     *       {@code E* = (capacity − basal)/LEAK_RATE} unsustainable — it implodes.</li>
     *   <li><b>aging</b> ({@code damage · AGING_COST}) — senescence: upkeep that
     *       rises with accumulated wear (contract v2 §7, ADR 0017). {@code damage}
     *       is the unit's lifetime dissipated energy (incremented below by this
     *       very charge, so older cells pay more and the cost accelerates —
     *       Gompertz). Since every cell pays at least {@code BASAL_COST}, damage
     *       only ever grows while the harvest ceiling is bounded, so no cell is
     *       immortal; offspring reset {@code damage} to 0 (germline renewal), so
     *       the lineage outruns entropy only by reproducing. The rate is a uniform
     *       conversion; the lifespan that results is per-unit and emergent.</li>
     * </ul>
     * No term scales with connection count: cost lives on what a unit does and
     * holds, not on what it has. Charge clamps to available energy so a unit
     * never overdraws; crossing to zero makes phases 2–3 skip it next tick.
     */
    private void settleCost() {
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double activity = activeThisTick[unit] * KernelConfig.COST_PER_PROPAGATION;
            final double maintenance = energy[unit] * KernelConfig.STORAGE_LEAK_RATE;
            final double aging = damage[unit] * KernelConfig.AGING_COST;
            final double charge = Math.min(
                    KernelConfig.BASAL_COST + activity + maintenance + aging, energy[unit]);
            energy[unit] -= charge;
            energySink += charge;
            damage[unit] += charge;
            if (energy[unit] <= 0.0) {
                cellOccupant[position[unit]] = -1;
            }
        }
    }

    /**
     * Phase 7 — reproduction (contract v2 §2/§4/§5/§11). Each unit still alive
     * after paying this tick's bill drives its REPRODUCE channel; the world
     * converts that output to a saturating energy commitment, draws it from the
     * parent, and installs a mutated copy into a free slot. The child is inert
     * until next tick (its node state is zeroed on install), so it cannot
     * reproduce again this tick. Energy is conserved: the child receives
     * {@code REPRODUCE_YIELD} of the commitment and the lossy remainder flows to
     * the sink. Placement is <b>spatial</b> (contract v3 §5, supersedes v2 §5's
     * halt-on-full): the child is installed in the lowest-indexed free Moore
     * neighbour of the parent's cell; if every neighbour is occupied the unit
     * simply does not reproduce this tick — a physical, local constraint (no room
     * to build), not a denied birth from a global cap, so there is no run halt.
     * Parents are scanned in ascending slot order and the child is installed
     * immediately, so a later parent sees the cell occupied and the whole
     * genealogy + geography is deterministic.
     * <p>
     * Each birth also burns a fixed {@code BUILD_COST} to the sink — the
     * irreducible biosynthesis overhead of assembling a new unit (ADR 0015),
     * the reproduction analogue of the metabolic {@code BASAL_COST}. Together
     * with the proportional yield loss it gives a Pirt-shaped reproduction bill
     * (fixed + proportional), so spamming tiny offspring is net-lethal and total
     * births are bounded by the energy in the system. A parent that cannot
     * afford {@code BUILD_COST} on top of any commitment simply does not
     * reproduce — an energy constraint, not a denied birth.
     */
    private void settleReproduction() {
        freeSlotCursor = 0;
        for (int parent = 0; parent < unitCount; parent++) {
            if (energy[parent] <= KernelConfig.BUILD_COST) {
                continue;
            }
            final double drive = reproduceDrive(parent);
            if (drive <= 0.0) {
                continue;
            }
            final double commit = Math.min(drive, energy[parent] - KernelConfig.BUILD_COST);
            if (commit <= 0.0) {
                continue;
            }
            final int childCell = freeMooreNeighbour(position[parent]);
            if (childCell < 0) {
                continue;
            }
            final int childSlot = nextFreeSlot();
            if (childSlot < 0) {
                continue;
            }
            energy[parent] -= commit + KernelConfig.BUILD_COST;
            final double childEnergy = KernelConfig.REPRODUCE_YIELD * commit;
            final double dissipated = (commit - childEnergy) + KernelConfig.BUILD_COST;
            energySink += dissipated;
            damage[parent] += dissipated;
            installChild(childSlot, childCell, parent, childEnergy);
            birthsTotal++;
            if (generation[childSlot] > maxGeneration) {
                maxGeneration = generation[childSlot];
            }
        }
    }

    /**
     * The raw reproduction drive: {@code REPRODUCE_MAX · σ(output)} with the
     * same saturating shape as harvest demand (contract v2 §4) — a runaway
     * signal buys no extra investment. {@code max(0, …)}: you cannot
     * un-reproduce; a non-finite output is garbage, not an action, so it drives
     * nothing. Affordability (the parent reserving BUILD_COST and not
     * overdrawing) is applied by the caller.
     */
    private double reproduceDrive(final int unit) {
        final int reproduceIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                + (NodeLayout.Action.REPRODUCE * NodeLayout.Action.INSTANCES_PER_TYPE);
        final double output = outputsPrev[reproduceIdx];
        if (!Double.isFinite(output) || output <= 0.0) {
            return 0.0;
        }
        return KernelConfig.REPRODUCE_MAX
                / (1.0 + KernelConfig.REPRODUCE_HALF_SATURATION / output);
    }

    /**
     * The lowest-indexed free (unoccupied) cell among a cell's 8 Moore neighbours
     * on the toroidal lattice (contract v3 §5), or -1 if every neighbour is
     * occupied. Freeness is read from {@code cellOccupant} (cell-indexed), not
     * slot energy, since slot and cell are decoupled (ADR 0020). Deterministic
     * (fixed scan order, min index), so a birth lands in the same cell on every
     * run of the same seed. On a tiny torus (W &le; 2) opposite offsets wrap onto
     * the same neighbour — harmless, the min dedupes them; at W = 1 the only
     * neighbour is the cell itself (the parent's), so a lone-cell world cannot
     * reproduce.
     */
    private int freeMooreNeighbour(final int cell) {
        final int w = worldWidth;
        final int x = cell % w;
        final int y = cell / w;
        int best = -1;
        for (int dy = -1; dy <= 1; dy++) {
            final int ny = (((y + dy) % w) + w) % w;
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                final int nx = (((x + dx) % w) + w) % w;
                final int ncell = ny * w + nx;
                if (cellOccupant[ncell] < 0 && (best < 0 || ncell < best)) {
                    best = ncell;
                }
            }
        }
        return best;
    }

    /**
     * Lowest free (energy &le; 0) slot at or beyond the monotone cursor, or -1 if
     * none remain (contract v2 §11). A slot is a unit's permanent storage identity
     * (ADR 0020); births claim a fresh one. Because {@code #living = #occupied
     * cells ≤ W·W = #slots}, a free Moore-neighbour cell (the placement constraint)
     * guarantees a free slot exists, so this never returns -1 in practice. The
     * cursor only advances, so the per-tick scan is O(maxUnits) total, not per
     * birth; it is reset at the start of each settle-reproduction.
     */
    private int nextFreeSlot() {
        for (int slot = freeSlotCursor; slot < unitCount; slot++) {
            if (energy[slot] <= 0.0) {
                freeSlotCursor = slot + 1;
                return slot;
            }
        }
        return -1;
    }

    /**
     * Phase 8 — resource diffusion (contract v3 §4/§6). A mass-conserving explicit
     * discrete Laplacian over the 4 von-Neumann neighbours on the torus:
     * {@code next = here + DIFFUSION_RATE · (Σ neighbours − 4·here)}. Double-buffered
     * (whole field read, next written, then swapped) so the update is
     * order-independent and deterministic. Conserves mass exactly on the torus —
     * every flux leaving a cell enters its neighbour — and is never clamped, so it
     * creates and destroys nothing; only inflow (phase 5) is capped. One O(cells)
     * pass per tick, no allocation.
     */
    private void settleDiffusion() {
        final int w = worldWidth;
        final double d = KernelConfig.DIFFUSION_RATE;
        for (int y = 0; y < w; y++) {
            final int row = y * w;
            final int up = (((y - 1) % w) + w) % w * w;
            final int down = ((y + 1) % w) * w;
            for (int x = 0; x < w; x++) {
                final int left = (((x - 1) % w) + w) % w;
                final int right = (x + 1) % w;
                final double here = resourceField[row + x];
                final double neighbours = resourceField[row + left]
                        + resourceField[row + right]
                        + resourceField[up + x]
                        + resourceField[down + x];
                resourceFieldNext[row + x] = here + d * (neighbours - 4.0 * here);
            }
        }
        final double[] tmp = resourceField;
        resourceField = resourceFieldNext;
        resourceFieldNext = tmp;
    }

    /**
     * Phase 8 — motility (contract v3 §9, ADR 0020). Each living unit reads its
     * four MOVE effectors (from outputsPrev, the swapped current tick) and takes
     * at most one Moore step: {@code dx} from {@code MOVE_E − MOVE_W}, {@code dy}
     * from {@code MOVE_N − MOVE_S} (N = −y, up), each thresholded by
     * {@code MOVE_DEADZONE} so a quiet circuit stays put. Diagonals are emergent
     * (both axes firing). A step is taken only into a free cell
     * ({@code cellOccupant < 0}); a blocked or zero step does nothing and costs
     * nothing. An actual step charges {@code MOVE_COST} to the sink (and damage —
     * motility is dissipation); the afford check ({@code energy > MOVE_COST})
     * means a step never kills, so no cell is freed here. Units are scanned in
     * ascending slot order with occupancy updated immediately, so two units
     * targeting one free cell resolve by lowest slot, and a cell vacated this tick
     * can be entered this tick — all deterministic. The slot never changes, so the
     * connection store is untouched and nothing is allocated.
     */
    private void settleMovement() {
        final int w = worldWidth;
        final double theta = KernelConfig.MOVE_DEADZONE;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= KernelConfig.MOVE_COST) {
                continue;
            }
            final int actionBase = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET;
            final double h = outputsPrev[actionBase + NodeLayout.Action.MOVE_E]
                    - outputsPrev[actionBase + NodeLayout.Action.MOVE_W];
            final double v = outputsPrev[actionBase + NodeLayout.Action.MOVE_N]
                    - outputsPrev[actionBase + NodeLayout.Action.MOVE_S];
            int dx = 0;
            if (h > theta) {
                dx = 1;
            } else if (h < -theta) {
                dx = -1;
            }
            int dy = 0;
            if (v > theta) {
                dy = -1;
            } else if (v < -theta) {
                dy = 1;
            }
            if (dx == 0 && dy == 0) {
                continue;
            }
            final int cell = position[unit];
            final int nx = (((cell % w) + dx) % w + w) % w;
            final int ny = (((cell / w) + dy) % w + w) % w;
            final int target = ny * w + nx;
            if (cellOccupant[target] >= 0) {
                continue;
            }
            cellOccupant[cell] = -1;
            position[unit] = target;
            cellOccupant[target] = unit;
            energy[unit] -= KernelConfig.MOVE_COST;
            energySink += KernelConfig.MOVE_COST;
            damage[unit] += KernelConfig.MOVE_COST;
        }
    }

    /**
     * Install a mutated copy of the parent's genome into a free slot (contract
     * v2 §6/§9). The slot is fully reset: genes copied then point-mutated,
     * recompiled, node state (outputs, delay memory) zeroed so the child starts
     * inert and first acts next tick, energy and lineage tags set. {@code
     * lineageId} is inherited unchanged (the root ancestor); {@code generation}
     * is the parent's + 1. The child takes storage slot {@code child} and is
     * placed in cell {@code childCell} (a free Moore neighbour, §5); slot and cell
     * are independent (ADR 0020).
     */
    private void installChild(final int child, final int childCell, final int parent, final double childEnergy) {
        final int count = geneCount[parent];
        System.arraycopy(genes, parent * maxGenes, genes, child * maxGenes, count);
        geneCount[child] = count;
        mutate(child);
        compileSlot(child);

        final int nodeBase = child * NodeLayout.TOTAL;
        for (int n = 0; n < NodeLayout.TOTAL; n++) {
            outputsPrev[nodeBase + n] = 0.0;
            outputsNext[nodeBase + n] = 0.0;
            delayMemory[nodeBase + n] = 0.0;
        }
        activeThisTick[child] = 0;

        energy[child] = childEnergy;
        damage[child] = 0.0;
        lineageId[child] = lineageId[parent];
        generation[child] = generation[parent] + 1;
        position[child] = childCell;
        cellOccupant[childCell] = child;
    }

    /**
     * Point mutation (contract v2 §9): each of a gene's 32 bits flips
     * independently, drawn from the birth-keyed counter RNG so the result is
     * deterministic. Mutation-safe by construction — every 32-bit value decodes
     * to a legal gene — so no flip can be rejected.
     *
     * <p>The per-bit rate is split by what the bit changes (ADR 0021). Bits 0–15
     * are the weight field ({@code rawGene & 0xFFFF}): near-continuous tuning of
     * an existing connection, mostly safe, so they anneal at the higher
     * {@code MUTATION_RATE_WEIGHT}. Bits 16–31 are the structure fields
     * (Src/Dst type + id): discrete graph rewiring, mostly disruptive, so they
     * mutate at the lower {@code MUTATION_RATE_STRUCT} — protecting topology
     * while keeping weight search fast.
     */
    private void mutate(final int child) {
        final int base = child * maxGenes;
        final int count = geneCount[child];
        int index = 0;
        for (int g = 0; g < count; g++) {
            int gene = genes[base + g];
            for (int bit = 0; bit < 32; bit++) {
                final double rate = bit < 16
                        ? KernelConfig.MUTATION_RATE_WEIGHT
                        : KernelConfig.MUTATION_RATE_STRUCT;
                if (Noise.mutationUniform(KernelConfig.RANDOM_SEED, child, tick, index++)
                        < rate) {
                    gene ^= (1 << bit);
                }
            }
            genes[base + g] = gene;
        }
    }

    /**
     * Compiled connections of every currently-alive unit, in slot order (each
     * slot's sum range then mul range — the same per-unit ordering the wiring
     * export expects). Unlike the founders' static wiring, these reflect every
     * mutation a lineage accrued, so the descendants' evolved circuits can be
     * inspected. End-of-run export only — allocates, never called per tick.
     */
    public CompiledConnection[] liveConnections() {
        int count = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] > 0.0) {
                count += (sumEnd[unit] - sumStart[unit]) + (mulEnd[unit] - mulStart[unit]);
            }
        }
        final CompiledConnection[] out = new CompiledConnection[count];
        int cursor = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            for (int i = sumStart[unit]; i < sumEnd[unit]; i++) {
                out[cursor++] = connections[i];
            }
            for (int i = mulStart[unit]; i < mulEnd[unit]; i++) {
                out[cursor++] = connections[i];
            }
        }
        return out;
    }

    public KernelSnapshot snapshot() {
        double resourceTotal = 0.0;
        for (final double r : resourceField) {
            resourceTotal += r;
        }
        return new KernelSnapshot(tick, outputsPrev, delayMemory, energy, damage, energySink,
                resourceTotal, resourceField, worldWidth, position, initialResourceTotal, cumulativeInflow,
                lineageId, generation, birthsTotal, maxGeneration, creditedInitialEnergy);
    }
}
