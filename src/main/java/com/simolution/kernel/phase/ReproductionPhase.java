package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.SlotCompiler;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Noise;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 8: reproduction as symmetric binary fission (contract v5 §5, supersedes the
 * contract-v2 investment/budding model), resolved as a claim (engine v6 design §5.6). A unit
 * that wants to divide ({@code REPRODUCE} output {@code > 0}, a bare trigger whose magnitude is
 * not an investment amount) and can afford the replication work splits its body in two: the
 * child receives <i>half</i> the parent's energy and <i>half</i> its mass, and the parent keeps
 * the other half. There is no viability gate (contract v5 §5): an undersized daughter simply
 * cannot pay its maintenance against its sublinear harvest and dies on its own, and a unit that
 * fires {@code REPRODUCE} blindly wastes {@code buildCost} and halves itself toward corpses, so
 * the size-control checkpoint is left to evolve via {@code SELF_MASS} rather than coded here.
 * <p>
 * The act of copying costs {@code buildCost = BUILD_COST + BUILD_COST_PER_GENE · childGeneCount}
 * (contract v4 §3), charged to the sink before the split. Indel decisions never read gene values,
 * so {@link #childLength} knows the post-indel length from the parent's key alone, before any
 * slot is claimed. Because the parent always retains {@code (energy − buildCost)/2 > 0}, fission
 * is never terminal, so this phase frees no cell and kills no unit.
 * <p>
 * {@link #run} (parallel, per parent) reads only phase-start state and writes only the parent's
 * own {@code birthCell} and {@code birthCost}: the lowest-indexed free Moore neighbour (contract
 * v3 §5) and the build cost, or {@code birthCell = -1} when the unit does not want to divide,
 * has no free neighbour, or cannot afford its child. {@link #finish} (serial) resolves in
 * ascending parent slot: the first claim on a cell wins ({@code cellWinner}, reset per touched
 * entry so it is all {@code -1} again when movement starts). Winners take free slots
 * ({@code energy ≤ 0} at phase start) in ascending slot order and install their child; losers
 * and unaffordable parents pay nothing and claim no slot. Each birth is appended to the
 * published birth list ({@code birthParent}, {@code birthChild}, {@code birthChildCell},
 * {@code birthCount}), ascending in parent and child slot alike.
 * <p>
 * The free-slot scan uses one monotone cursor, so two winners never share a slot. A slot freed
 * by a death earlier this tick (cost or growth) has {@code energy ≤ 0} at phase start, so it is
 * free here like any other dead slot. Slots this phase changes stay live: a parent keeps
 * positive energy and a child is installed only behind the cursor, so reading
 * {@code energy[slot] ≤ 0} at the cursor is the same as reading it at phase start.
 * <p>
 * Shape is {@link Shape#CLAIM}.
 */
public final class ReproductionPhase implements Phase {

    private final WorldState state;
    private final int maxGenes;
    private final int worldWidth;
    private final int unitCount;
    private final double buildCostBase;
    private final double buildCostPerGene;
    private final double logKeepWeight;
    private final double logKeepStruct;
    private final double indelRateDup;
    private final double indelRateDel;
    private final long seed;

    public ReproductionPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.maxGenes = state.maxGenes;
        this.worldWidth = state.worldWidth;
        this.unitCount = state.unitCount;
        this.buildCostBase = config.buildCost();
        this.buildCostPerGene = config.buildCostPerGene();
        this.logKeepWeight = StrictMath.log(1 - config.mutationRateWeight());
        this.logKeepStruct = StrictMath.log(1 - config.mutationRateStruct());
        this.indelRateDup = config.indelRateDup();
        this.indelRateDel = config.indelRateDel();
        this.seed = config.seed();
    }

    @Override
    public String name() {
        return "reproduction";
    }

    @Override
    public Shape shape() {
        return Shape.CLAIM;
    }

    @Override
    public int extent() {
        return unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final int[] position = state.position;
        final int[] birthCell = state.birthCell;
        final double[] birthCost = state.birthCost;
        final int tick = state.tick;

        for (int parent = from; parent < to; parent++) {
            birthCell[parent] = -1;
            if (energy[parent] <= buildCostBase || !wantsToDivide(parent)) {
                continue;
            }
            final int childCell = freeMooreNeighbour(position[parent]);
            if (childCell < 0) {
                continue;
            }
            final long prefix = Noise.mutationPrefix(seed, parent, tick);
            final double buildCost = buildCostBase
                    + buildCostPerGene * childLength(geneCount[parent], prefix);
            if (energy[parent] <= buildCost) {
                continue;
            }
            birthCell[parent] = childCell;
            birthCost[parent] = buildCost;
        }
    }

    @Override
    public void finish() {
        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final int[] birthCell = state.birthCell;
        final double[] birthCost = state.birthCost;
        final int[] cellWinner = state.cellWinner;
        final int slots = unitCount;

        for (int parent = 0; parent < slots; parent++) {
            final int cell = birthCell[parent];
            if (cell >= 0 && cellWinner[cell] < 0) {
                cellWinner[cell] = parent;
            }
        }
        int births = 0;
        int freeCursor = 0;
        double sink = 0.0;
        for (int parent = 0; parent < slots; parent++) {
            final int cell = birthCell[parent];
            if (cell < 0 || cellWinner[cell] != parent) {
                continue;
            }
            cellWinner[cell] = -1;
            while (freeCursor < slots && energy[freeCursor] > 0.0) {
                freeCursor++;
            }
            if (freeCursor == slots) {
                continue;
            }
            final int child = freeCursor++;
            final double buildCost = birthCost[parent];
            energy[parent] -= buildCost;
            damage[parent] += buildCost;
            sink += buildCost;
            final double childEnergy = energy[parent] * 0.5;
            final double childMass = mass[parent] * 0.5;
            energy[parent] = childEnergy;
            mass[parent] = childMass;

            final int count = geneCount[parent];
            System.arraycopy(genes, parent * maxGenes, genes, child * maxGenes, count);
            geneCount[child] = mutate(genes, child * maxGenes, count,
                    Noise.mutationPrefix(seed, parent, state.tick));
            SlotCompiler.compile(state, child);
            finishChild(child, cell, parent, childEnergy, childMass);

            state.birthsTotal++;
            if (state.generation[child] > state.maxGeneration) {
                state.maxGeneration = state.generation[child];
            }
            state.birthParent[births] = parent;
            state.birthChild[births] = child;
            state.birthChildCell[births] = cell;
            births++;
        }
        state.birthCount = births;
        state.sinkPartial[0] += sink;
        state.mergePartials();
    }

    /**
     * Whether a unit drives its {@code REPRODUCE} effector to divide this tick
     * (contract v5 §5). Fission has no investment amount, so this is a bare
     * trigger: a finite, positive output means "divide now"; a non-finite output
     * is garbage, not an action. Affordability (reserving {@code buildCost}) is
     * applied by the caller.
     */
    private boolean wantsToDivide(final int unit) {
        final float[] outputsPrev = state.outputsPrev;

        final int reproduceIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                + (NodeLayout.Action.REPRODUCE * NodeLayout.Action.INSTANCES_PER_TYPE);
        final double output = outputsPrev[reproduceIdx];
        return Double.isFinite(output) && output > 0.0;
    }

    /**
     * The lowest-indexed free (unoccupied) cell among a cell's 8 Moore neighbours
     * on the toroidal lattice (contract v3 §5), or -1 if every neighbour is
     * occupied. Freeness is read from {@code cellOccupant} (cell-indexed), not
     * slot energy, since slot and cell are decoupled (ADR 0020). Deterministic
     * (fixed scan order, min index), so a birth lands in the same cell on every
     * run of the same seed. On a tiny torus (W &le; 2) opposite offsets wrap onto
     * the same neighbour, harmless as the min dedupes them; at W = 1 the only
     * neighbour is the cell itself (the parent's), so a lone-cell world cannot
     * reproduce.
     */
    private int freeMooreNeighbour(final int cell) {
        final int[] cellOccupant = state.cellOccupant;

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
     * Finish installing a child whose genome has already been written and compiled
     * (contract v2 §6). Node state (outputs, delay memory) is zeroed so the child
     * starts inert and first acts next tick; energy, mass (each half the parent's,
     * symmetric fission, contract v5 §5), {@code damage} (reset to 0, germline
     * renewal, contract v2 §7), {@code lineageId} (inherited unchanged, the root
     * ancestor), {@code generation} (parent's + 1), and cell are set. Slot and cell
     * are independent (ADR 0020).
     */
    private void finishChild(final int child, final int childCell, final int parent,
            final double childEnergy, final double childMass) {
        state.resetForBirth(child);
        state.energy[child] = childEnergy;
        state.mass[child] = childMass;
        state.lineageId[child] = state.lineageId[parent];
        state.generation[child] = state.generation[parent] + 1;
        state.position[child] = childCell;
        state.cellOccupant[childCell] = child;
    }

    /**
     * The post-indel length of the child a parent with {@code parentCount} genes would build
     * under mutation key {@code prefix}: {@link #mutate} as a dry run that consumes the same
     * draws and writes nothing. Pure and allocation free, so {@link #run} calls it in parallel.
     */
    private int childLength(final int parentCount, final long prefix) {
        return mutate(null, 0, parentCount, prefix);
    }

    /**
     * Mutate a freshly-copied child genome of {@code count} genes at {@code genes[base..]} in
     * place and return its new length; with {@code genes == null} only the length is computed
     * ({@link #childLength}). The three heritable operators of contract v4 §2, in the fixed order
     * determinism depends on:
     * <ol>
     *   <li><b>Point mutation</b> (contract v2 §9 / ADR 0021): each of the 32 bits of each
     *       <i>inherited</i> gene flips independently at its class rate: weight bits 0–15 at
     *       {@code MUTATION_RATE_WEIGHT}, structure bits 16–31 at {@code MUTATION_RATE_STRUCT}.
     *       Drawn by geometric skip sampling (engine v6 design §5.7): each class keeps the
     *       position of its next flip in its own bit sequence (gene by gene, 16 bits per gene),
     *       one {@link Noise#geometricGap} draw per flip instead of one draw per bit. Flips are
     *       visited in today's bit order (gene by gene, bits 0 to 31), and each flip draws the
     *       gap to its class's next flip. Which bits flip depends only on the key and the gene
     *       count, never on gene values.</li>
     *   <li><b>Deletion</b>: each gene position is dropped with probability
     *       {@code INDEL_RATE_DEL}; survivors compact toward index 0 (no holes).</li>
     *   <li><b>Tandem duplication</b>: each <i>survivor</i> spawns, with probability
     *       {@code INDEL_RATE_DUP}, a faithful copy appended at the end (Ohno). A duplication
     *       that would exceed {@code maxGenes} is dropped (the soft cap, §1); the floor is 0
     *       genes (an empty genome is legal and inert).</li>
     * </ol>
     * Mutation-safe by construction: every 32-bit value decodes to a legal gene, and
     * copy/delete cannot forge an illegal one, so no operator can be rejected.
     *
     * <p><b>Determinism (contract v4 §4, keyed by parent per engine v6 design §5.6).</b> One
     * monotone {@code op} counter walks every draw: the first weight gap, the first structure
     * gap, one gap per flip in bit order, then one deletion draw per gene, then one duplication
     * draw per survivor, each keyed {@code (seed, parentSlot, birthTick, op)} through
     * {@code prefix}. A parent divides at most once per tick, so every key is unique, and the
     * genome is known before the child's slot is. The duplication draw is consumed even when the
     * cap drops its effect, so a binding cap never desynchronises the counter.
     */
    private int mutate(final int[] genes, final int base, final int count, final long prefix) {
        final long classBits = 16L * count;
        int op = 0;
        long weight = nextFlip(-1, Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeepWeight),
                classBits);
        long struct = nextFlip(-1, Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeepStruct),
                classBits);
        while (weight < classBits || struct < classBits) {
            final long weightBit = weight < classBits ? geneBit(weight, 0) : Long.MAX_VALUE;
            final long structBit = struct < classBits ? geneBit(struct, 16) : Long.MAX_VALUE;
            final long bit;
            if (weightBit < structBit) {
                bit = weightBit;
                weight = nextFlip(weight,
                        Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeepWeight), classBits);
            } else {
                bit = structBit;
                struct = nextFlip(struct,
                        Noise.geometricGap(Noise.mutationUniformFromPrefix(prefix, op++), logKeepStruct), classBits);
            }
            if (genes != null) {
                genes[base + (int) (bit >>> 5)] ^= 1 << (bit & 31);
            }
        }

        int survivors = 0;
        for (int g = 0; g < count; g++) {
            if (Noise.mutationUniformFromPrefix(prefix, op++) >= indelRateDel) {
                if (genes != null) {
                    genes[base + survivors] = genes[base + g];
                }
                survivors++;
            }
        }

        int length = survivors;
        for (int g = 0; g < survivors; g++) {
            if (Noise.mutationUniformFromPrefix(prefix, op++) < indelRateDup && length < maxGenes) {
                if (genes != null) {
                    genes[base + length] = genes[base + g];
                }
                length++;
            }
        }
        return length;
    }

    /**
     * The class position after {@code position} that flips next, given the skipped-bit
     * {@code gap}, or {@code limit} once past the class's last bit. Written so a saturated gap
     * ({@link Long#MAX_VALUE}, rate 0) cannot overflow.
     */
    private static long nextFlip(final long position, final long gap, final long limit) {
        return gap >= limit - position - 1 ? limit : position + 1 + gap;
    }

    /**
     * Genome-wide bit index ({@code gene · 32 + bit}) of class position {@code k}: class
     * positions run 16 per gene, at bit offset 0 for weight bits and 16 for structure bits.
     */
    private static long geneBit(final long k, final int offset) {
        return ((k >>> 4) << 5) + offset + (k & 15);
    }
}
