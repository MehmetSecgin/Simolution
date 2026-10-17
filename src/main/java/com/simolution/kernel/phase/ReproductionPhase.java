package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.SlotCompiler;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Noise;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 8: reproduction as symmetric binary fission (contract v5 §5, supersedes the
 * contract-v2 investment/budding model). A unit that wants to divide ({@code REPRODUCE}
 * output {@code > 0}, a bare trigger whose magnitude is not an investment amount) and can
 * afford the replication work splits its body in two: the child receives <i>half</i> the
 * parent's energy and <i>half</i> its mass, and the parent keeps the other half. There
 * is no viability gate (contract v5 §5): an undersized daughter simply cannot pay its
 * maintenance against its sublinear harvest and dies on its own, and a unit that fires
 * {@code REPRODUCE} blindly wastes {@code buildCost} and halves itself toward corpses, so the
 * size-control checkpoint is left to evolve via {@code SELF_MASS} rather than coded here.
 * <p>
 * The act of copying still costs {@code buildCost = BUILD_COST + BUILD_COST_PER_GENE ·
 * childGeneCount} (contract v4 §3), charged to the sink before the split; the child genome is
 * built first so its post-indel length is known to price it. A parent that cannot afford
 * {@code buildCost} does not divide (an energy constraint, not a denied birth); the cheap
 * pre-filter {@code energy ≤ BUILD_COST} skips parents too poor for even an empty child
 * without claiming a slot. Because the parent always retains {@code (energy − buildCost)/2 >
 * 0}, fission is never terminal: the parent cannot die dividing (no semelparity), so this
 * phase frees no cell. Placement is spatial (contract v3 §5): the lowest-indexed free Moore
 * neighbour, or no birth if none is free. Parents scan in ascending slot order with the child
 * installed immediately, so the genealogy and geography stay deterministic.
 * <p>
 * Shape is {@link Shape#SERIAL}: an in-order scan with immediate installation is the conflict
 * resolution, and births claim slots and cells as they go.
 */
public final class ReproductionPhase implements Phase {

    private final WorldState state;
    private final int maxGenes;
    private final int worldWidth;
    private final int unitCount;
    private final double buildCostBase;
    private final double buildCostPerGene;
    private final double mutationRateWeight;
    private final double mutationRateStruct;
    private final double indelRateDup;
    private final double indelRateDel;
    private int freeSlotCursor;

    public ReproductionPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.maxGenes = state.maxGenes;
        this.worldWidth = state.worldWidth;
        this.unitCount = state.unitCount;
        this.buildCostBase = config.buildCost();
        this.buildCostPerGene = config.buildCostPerGene();
        this.mutationRateWeight = config.mutationRateWeight();
        this.mutationRateStruct = config.mutationRateStruct();
        this.indelRateDup = config.indelRateDup();
        this.indelRateDel = config.indelRateDel();
    }

    @Override
    public String name() {
        return "reproduction";
    }

    @Override
    public Shape shape() {
        return Shape.SERIAL;
    }

    @Override
    public int extent() {
        return unitCount;
    }

    @Override
    public void prepare() {
        freeSlotCursor = 0;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final int[] generation = state.generation;
        final int[] position = state.position;

        for (int parent = from; parent < to; parent++) {
            if (energy[parent] <= buildCostBase) {
                continue;
            }
            if (!wantsToDivide(parent)) {
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
            buildChildGenome(childSlot, parent);
            final double buildCost = buildCostBase
                    + buildCostPerGene * geneCount[childSlot];
            if (energy[parent] <= buildCost) {
                continue;
            }
            energy[parent] -= buildCost;
            state.energySink += buildCost;
            damage[parent] += buildCost;
            final double childEnergy = energy[parent] * 0.5;
            final double childMass = mass[parent] * 0.5;
            energy[parent] = childEnergy;
            mass[parent] = childMass;
            finishChild(childSlot, childCell, parent, childEnergy, childMass);
            state.birthsTotal++;
            if (generation[childSlot] > state.maxGeneration) {
                state.maxGeneration = generation[childSlot];
            }
        }
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
     * Lowest free (energy &le; 0) slot at or beyond the monotone cursor, or -1 if
     * none remain (contract v2 §11). A slot is a unit's permanent storage identity
     * (ADR 0020); births claim a fresh one. Because {@code #living = #occupied
     * cells ≤ W·W = #slots}, a free Moore-neighbour cell (the placement constraint)
     * guarantees a free slot exists, so this never returns -1 in practice. The
     * cursor only advances, so the per-tick scan is O(maxUnits) total, not per
     * birth; it is reset in {@link #prepare()} at the start of each tick.
     */
    private int nextFreeSlot() {
        final double[] energy = state.energy;

        for (int slot = freeSlotCursor; slot < unitCount; slot++) {
            if (energy[slot] <= 0.0) {
                freeSlotCursor = slot + 1;
                return slot;
            }
        }
        return -1;
    }

    /**
     * Build a child's genome into a free slot from its parent's (contract v4 §2):
     * copy the parent's live genes, apply the three mutation operators
     * ({@link #mutate}: point mutation, then deletion, then tandem duplication),
     * and recompile the slot's connection region ({@link SlotCompiler} is
     * length-aware, so variable length flows through with no storage rework,
     * contract v2 §12). After this, {@code geneCount[child]} is the child's
     * post-indel length, which {@link #run} needs to price the per-gene build
     * cost (contract v4 §3) before charging the parent. Runs at a birth attempt
     * only, never per tick. If the parent then cannot afford the build, the slot
     * is simply left dead (energy untouched): its now-stale genes are inert and
     * unreferenced until a later birth reclaims the slot.
     */
    private void buildChildGenome(final int child, final int parent) {
        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;

        final int count = geneCount[parent];
        System.arraycopy(genes, parent * maxGenes, genes, child * maxGenes, count);
        geneCount[child] = count;
        mutate(child);
        SlotCompiler.compile(state, child);
    }

    /**
     * Finish installing a child whose genome {@link #buildChildGenome} has
     * already written and compiled (contract v2 §6). Node state (outputs, delay
     * memory) is zeroed so the child starts inert and first acts next tick;
     * energy, mass (each half the parent's, symmetric fission, contract v5 §5),
     * {@code damage} (reset to 0, germline renewal, contract v2 §7),
     * {@code lineageId} (inherited unchanged, the root ancestor), {@code
     * generation} (parent's + 1), and cell are set. The child takes storage slot
     * {@code child} and cell {@code childCell} (a free Moore neighbour, §5); slot
     * and cell are independent (ADR 0020). Called only after the parent has been
     * charged, so an unaffordable build never installs a living unit.
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
     * Mutate a freshly-copied child genome in place: the three heritable
     * operators of contract v4 §2, applied in the fixed order determinism
     * depends on:
     * <ol>
     *   <li><b>Point mutation</b> (contract v2 §9 / ADR 0021): each of the 32
     *       bits of each <i>inherited</i> gene flips at its class-split rate:
     *       weight bits 0–15 at {@code MUTATION_RATE_WEIGHT}, structure bits
     *       16–31 at the lower {@code MUTATION_RATE_STRUCT}, protecting topology
     *       while keeping weight search fast.</li>
     *   <li><b>Deletion</b>: each gene position is dropped with probability
     *       {@code INDEL_RATE_DEL}; survivors compact toward index 0 (no holes),
     *       shrinking {@code geneCount}.</li>
     *   <li><b>Tandem duplication</b>: each <i>survivor</i> spawns, with
     *       probability {@code INDEL_RATE_DUP}, a faithful copy appended at the
     *       end, the gene-level analogue of unequal crossing-over (Ohno).
     *       Divergence of the copy is deferred to point mutation in later
     *       generations, so there is no separate "randomise the copy" step. A
     *       duplication that would exceed {@code maxGenes} is dropped (the soft
     *       cap, §1); the floor is 0 genes (an empty genome is legal and inert).</li>
     * </ol>
     * Mutation-safe by construction: every 32-bit value decodes to a legal gene,
     * and copy/delete cannot forge an illegal one, so no operator can be rejected.
     *
     * <p><b>Determinism (contract v4 §4).</b> A single monotone {@code opIndex}
     * walks every decision in exactly the order above (all point-mutation bit
     * draws, then all deletion draws, then all duplication draws), each keyed
     * {@code (seed, childSlot, birthTick, opIndex)} via
     * {@link Noise#mutationUniform}. The counter never repeats within a birth and
     * at most one birth claims a slot per tick, so every draw has a unique key:
     * same seed gives the same genome, and report-v8 replay reproduces it exactly. The
     * duplication draw is consumed for every survivor even when the cap drops its
     * effect, so a binding cap never desynchronises the counter. This monotone
     * single-counter key is contract v4 §4 verbatim; it replaces the v2 per-bit
     * {@code geneIndex} key, which the index shifts of indels would have broken.
     */
    private void mutate(final int child) {
        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;

        final int base = child * maxGenes;
        int count = geneCount[child];
        final long prefix = Noise.mutationPrefix(Noise.LEGACY_RNG_SEED, child, state.tick);
        int opIndex = 0;

        for (int g = 0; g < count; g++) {
            int gene = genes[base + g];
            for (int bit = 0; bit < 32; bit++) {
                final double rate = bit < 16
                        ? mutationRateWeight
                        : mutationRateStruct;
                if (Noise.mutationUniformFromPrefix(prefix, opIndex++)
                        < rate) {
                    gene ^= (1 << bit);
                }
            }
            genes[base + g] = gene;
        }

        int survivors = 0;
        for (int g = 0; g < count; g++) {
            final boolean delete = Noise.mutationUniformFromPrefix(prefix, opIndex++)
                    < indelRateDel;
            if (!delete) {
                genes[base + survivors++] = genes[base + g];
            }
        }
        count = survivors;

        for (int g = 0; g < survivors; g++) {
            final boolean duplicate = Noise.mutationUniformFromPrefix(prefix, opIndex++)
                    < indelRateDup;
            if (duplicate && count < maxGenes) {
                genes[base + count++] = genes[base + g];
            }
        }

        geneCount[child] = count;
    }
}
