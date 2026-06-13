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

    private double reservoir;
    private double cumulativeInflow;

    private final int unitCount;
    private int freeSlotCursor;

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
    public Kernel(final int[][] initialGenomes, final int maxUnits, final int maxGenes) {

        final int seededCount = initialGenomes.length;
        if (seededCount > maxUnits) {
            throw new IllegalArgumentException(
                    "initial population " + seededCount + " exceeds maxUnits " + maxUnits);
        }
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
        this.freeSlotCursor = 0;

        this.reservoir = KernelConfig.RESOURCE_INITIAL;
        this.cumulativeInflow = 0.0;

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
            }
            compileSlot(unit);
        }
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

        final int resourceIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RESOURCE * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int selfEnergyIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.SELF_ENERGY * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        outputsNext[constIdx] = 1.0;
        outputsNext[randIdx] = Noise.sample(KernelConfig.RANDOM_SEED, unit, tick);
        outputsNext[resourceIdx] = reservoir / KernelConfig.RESOURCE_CAPACITY;
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
    }

    private void swapBuffers() {
        final double[] tmp = outputsPrev;
        outputsPrev = outputsNext;
        outputsNext = tmp;
    }

    /**
     * Phase 5 — energy intake (contract v1 §4–§5). Each alive unit demands
     * {@code EFFICIENCY × max(0, harvestOutput)} from the shared reservoir; the
     * {@code max(0, …)} means you cannot un-eat. When total demand exceeds the
     * pool, every unit is scaled by the same {@code min(1, reservoir/demandTotal)}
     * — the contract's {@code f(resourceAvailable)} and its proportional-to-demand
     * allocation are one order-independent factor (ADR 0010). The reservoir is
     * drawn down by exactly what units received, then topped up by a fixed inflow
     * admitted only up to capacity; the admitted amount is the sole energy the
     * audit treats as entering the system (cumulativeInflow), so
     * {@code INITIAL_total + cumulativeInflow == sum(energy) + reservoir + sink}
     * stays exact. Intake runs before cost so a unit can pay this tick's
     * metabolism with this tick's harvest (persistence becomes possible);
     * harvest output is read from outputsPrev, the just-swapped current tick.
     */
    private void settleIntake() {
        double demandTotal = 0.0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            demandTotal += harvestDemand(unit);
        }

        if (demandTotal > 0.0) {
            final double factor = Math.min(1.0, reservoir / demandTotal);
            double drawn = 0.0;
            for (int unit = 0; unit < unitCount; unit++) {
                if (energy[unit] <= 0.0) {
                    continue;
                }
                final double intake = harvestDemand(unit) * factor;
                energy[unit] += intake;
                drawn += intake;
            }
            reservoir -= drawn;
        }

        final double admitted = Math.min(KernelConfig.RESOURCE_INFLOW,
                Math.max(0.0, KernelConfig.RESOURCE_CAPACITY - reservoir));
        reservoir += admitted;
        cumulativeInflow += admitted;
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
     * the sink. Reproduction never fails for lack of room — an exhausted pool
     * halts the run (contract v2 §5): denying a birth would be a smuggled
     * population cap. Parents are scanned in ascending slot order and each birth
     * claims the lowest free slot, so the whole genealogy is deterministic.
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
            final int child = nextFreeSlot();
            if (child < 0) {
                throw new IllegalStateException(
                        "tick " + tick + ": MAX_UNITS=" + unitCount
                        + " exhausted, lineage " + lineageId[parent]
                        + " birth blocked — raise --max-units and rerun");
            }
            energy[parent] -= commit + KernelConfig.BUILD_COST;
            final double childEnergy = KernelConfig.REPRODUCE_YIELD * commit;
            final double dissipated = (commit - childEnergy) + KernelConfig.BUILD_COST;
            energySink += dissipated;
            damage[parent] += dissipated;
            installChild(child, parent, childEnergy);
            birthsTotal++;
            if (generation[child] > maxGeneration) {
                maxGeneration = generation[child];
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
     * Lowest free (energy &le; 0) slot at or beyond the monotonic cursor, or -1
     * if none remain. The cursor only advances, so each birth in a tick takes a
     * distinct slot and the scan is O(maxUnits) per tick in total, not per birth.
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
     * Install a mutated copy of the parent's genome into a free slot (contract
     * v2 §6/§9). The slot is fully reset: genes copied then point-mutated,
     * recompiled, node state (outputs, delay memory) zeroed so the child starts
     * inert and first acts next tick, energy and lineage tags set. {@code
     * lineageId} is inherited unchanged (the root ancestor); {@code generation}
     * is the parent's + 1.
     */
    private void installChild(final int child, final int parent, final double childEnergy) {
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
    }

    /**
     * Point mutation (contract v2 §9): each of a gene's 32 bits flips
     * independently with probability {@code MUTATION_RATE_PER_BIT}, drawn from
     * the birth-keyed counter RNG so the result is deterministic. Mutation-safe
     * by construction — every 32-bit value decodes to a legal gene — so no flip
     * can be rejected.
     */
    private void mutate(final int child) {
        final int base = child * maxGenes;
        final int count = geneCount[child];
        int index = 0;
        for (int g = 0; g < count; g++) {
            int gene = genes[base + g];
            for (int bit = 0; bit < 32; bit++) {
                if (Noise.mutationUniform(KernelConfig.RANDOM_SEED, child, tick, index++)
                        < KernelConfig.MUTATION_RATE_PER_BIT) {
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
        return new KernelSnapshot(tick, outputsPrev, delayMemory, energy, damage, energySink,
                reservoir, cumulativeInflow, lineageId, generation, birthsTotal,
                maxGeneration, creditedInitialEnergy);
    }
}
