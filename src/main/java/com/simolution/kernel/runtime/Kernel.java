package com.simolution.kernel.runtime;

import java.util.Arrays;
import java.util.List;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.SlotCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.phase.ClearPhase;
import com.simolution.kernel.phase.DecayPhase;
import com.simolution.kernel.phase.DiffusionPhase;
import com.simolution.kernel.phase.EvaluatePhase;
import com.simolution.kernel.phase.GrowthPhase;
import com.simolution.kernel.phase.IntakePhase;
import com.simolution.kernel.phase.CostPhase;
import com.simolution.kernel.phase.MovementPhase;
import com.simolution.kernel.phase.Phase;
import com.simolution.kernel.phase.PhaseRunner;
import com.simolution.kernel.phase.PropagatePhase;
import com.simolution.kernel.phase.ReproductionPhase;
import com.simolution.kernel.phase.SerialRunner;
import com.simolution.kernel.phase.SwapPhase;
import com.simolution.kernel.state.WorldState;

public final class Kernel {

    private final WorldState state;
    private final int maxGenes;
    private final int unitCount;
    private final List<Phase> phases;
    private final PhaseRunner runner = new SerialRunner();

    /**
     * The substrate is a fixed pool of {@code genomes.length} slots; a slot is
     * alive iff its energy is &gt; 0 (contract v2 §5/§6, death is derived). Each
     * slot owns a fixed region {@code [slot·maxGenes, slot·maxGenes + maxGenes)}
     * of the flat connection arrays and an equal region of the raw {@code genes}
     * array, both rewritten in place when the slot is reused by a birth: no
     * growth, no per-birth heap allocation (contract v2 §12).
     * {@code maxGenes} is the per-slot gene capacity (headroom for genome growth,
     * §16); the live extent is {@code geneCount[slot]}.
     * <p>
     * Within a slot's region the compile ({@link SlotCompiler}) lays out sum-destined
     * connections first (in gene order) then mul-destined ones (in gene order). MUL
     * needs its two strongest individual inputs, which the summing accumulator
     * destroys, so it runs a separate top-2 loop (ADR 0005); the contiguous per-slot
     * ranges let a dead slot be skipped with one branch (ADR 0006). Both arrays are
     * sized to the pool's full capacity {@code unitCount · maxGenes} up front
     * (contract v2 §12 footprint note).
     * <p>
     * The chemical table is any number of chemicals; each decays to the sink
     * ({@code decayTarget == -1}) or into another chemical. Chemical 0 is the one
     * units sense and harvest.
     */
    public Kernel(final int[][] initialGenomes, final int[] founderCells, final EngineConfig config) {
        validateChemicals(config);
        final int worldWidth = config.worldWidth();
        final int maxGenes = config.maxGenes();
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
        this.unitCount = maxUnits;
        this.maxGenes = maxGenes;

        this.state = new WorldState(config);
        state.creditedInitialEnergy = config.initialEnergy() * seededCount;
        state.creditedInitialMass = config.initialMass() * seededCount;

        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final long[] lineageId = state.lineageId;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;

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
                energy[unit] = config.initialEnergy();
                mass[unit] = config.initialMass();
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
            SlotCompiler.compile(state, unit);
        }

        this.phases = List.of(
                new ClearPhase(state),
                new PropagatePhase(state),
                new EvaluatePhase(state, config),
                new SwapPhase(state),
                new IntakePhase(state, config),
                new CostPhase(state, config),
                new GrowthPhase(state, config),
                new ReproductionPhase(state, config),
                new MovementPhase(state, config),
                new DiffusionPhase(state, config),
                new DecayPhase(state, config));
    }

    private static void validateChemicals(final EngineConfig config) {
        final List<ChemicalSpec> chemicals = config.chemicals();
        if (chemicals.isEmpty()) {
            throw new IllegalArgumentException("kernel needs at least one chemical (units sense and harvest chemical 0)");
        }
        for (int k = 0; k < chemicals.size(); k++) {
            final int target = chemicals.get(k).decayTarget();
            if (target < -1 || target >= chemicals.size()) {
                throw new IllegalArgumentException(
                        "chemical " + k + " decayTarget " + target + " is neither -1 (sink) nor a chemical index");
            }
            if (target == k) {
                throw new IllegalArgumentException("chemical " + k + " cannot decay into itself");
            }
        }
    }

    /**
     * The tick as an ordered list of declared phases, built once at construction
     * (contract v0 §8, the locked order of the 11-phase pipeline): clear, propagate,
     * evaluate, swap, intake, cost, growth, reproduction, movement, diffusion, decay.
     */
    public List<Phase> phases() {
        return phases;
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
     * ({@link Noise#placementUniform}, seeded by {@link Noise#LEGACY_RNG_SEED}
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
            final double u = Noise.placementUniform(Noise.LEGACY_RNG_SEED, i);
            final int j = i + (int) (u * (cellCount - i));
            final int tmp = cell[i];
            cell[i] = cell[j];
            cell[j] = tmp;
            founderCell[i] = cell[i];
        }
        return founderCell;
    }

    public void tick() {
        runner.runTick(phases);
        state.tick++;
    }

    /**
     * Compiled connections of every currently-alive unit, in slot order (each
     * slot's sum range then mul range — the same per-unit ordering the wiring
     * export expects). Unlike the founders' static wiring, these reflect every
     * mutation a lineage accrued, so the descendants' evolved circuits can be
     * inspected. Junk-source connections are not stored and so are absent.
     * End-of-run export only — allocates, never called per tick.
     */
    public CompiledConnection[] liveConnections() {
        final double[] energy = state.energy;
        final int[] sumStart = state.sumStart;
        final int[] mulEnd = state.mulEnd;

        int count = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] > 0.0) {
                count += mulEnd[unit] - sumStart[unit];
            }
        }
        final CompiledConnection[] out = new CompiledConnection[count];
        int cursor = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            for (int i = sumStart[unit]; i < mulEnd[unit]; i++) {
                out[cursor++] = connectionAt(unit, i);
            }
        }
        return out;
    }

    /**
     * The compiled connections of a single slot (sum range then mul range, the
     * same per-unit ordering {@link #liveConnections} uses), or an empty array if
     * the slot is dead. The evolved circuit of one unit — used by the replay
     * time-travel surface ({@code Replayer.circuitOf}) to inspect a unit at tick T.
     * Junk-source connections are not stored and so are absent. Allocates; not a
     * per-tick path.
     */
    public CompiledConnection[] connectionsOf(final int slot) {
        if (state.energy[slot] <= 0.0) {
            return new CompiledConnection[0];
        }
        final int from = state.sumStart[slot];
        final CompiledConnection[] out = new CompiledConnection[state.mulEnd[slot] - from];
        for (int i = from; i < state.mulEnd[slot]; i++) {
            out[i - from] = connectionAt(slot, i);
        }
        return out;
    }

    private CompiledConnection connectionAt(final int unit, final int i) {
        final int unitOffset = unit * NodeLayout.TOTAL;
        return new CompiledConnection(
                unitOffset + state.connSrc[i], unitOffset + state.connDst[i], state.connWeight[i]);
    }

    public KernelSnapshot snapshot() {
        double resourceTotal = 0.0;
        for (final double r : state.cellChem) {
            resourceTotal += r;
        }
        double massTotal = 0.0;
        for (final double m : state.mass) {
            massTotal += m;
        }
        return new KernelSnapshot(state, resourceTotal, massTotal);
    }

    /**
     * Serialize the complete <i>restorable</i> kernel state to a binary stream
     * (report-v8 checkpoints). Only mutable, future-affecting state is written;
     * everything structure-derived ({@code connSrc/connDst/connWeight}, sum/mul ranges,
     * {@code harvestConnCount}, {@code mulInDegree}) is rebuilt by
     * {@link #loadState} from the genes, and the founding constants
     * ({@code creditedInitialEnergy}, {@code initialResourceTotal}) are recomputed
     * by the constructor from the manifest. {@code cellOccupant} <b>is</b> written
     * rather than rederived from living positions, so a restore never depends on
     * that invariant. Per-tick scratch
     * ({@code accumulators}, {@code mulTop*}, {@code activeThisTick},
     * {@code outputsNext}) is intentionally omitted — it is
     * cleared/overwritten before it is read each tick, so it cannot influence the
     * future. No RNG state is stored: {@link Noise} is a pure function of
     * {@code (seed, slot, tick)}, and {@code tick} is in the dump — this is exactly
     * why restore + tick-forward reproduces the canonical history (report-v8).
     * <p>
     * Format is JVM-internal (the {@link com.simolution.sim.Replayer} is the only
     * reader): {@link java.io.DataOutputStream} big-endian, length-framed.
     */
    public void saveState(final java.io.DataOutput out) throws java.io.IOException {
        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final long[] lineageId = state.lineageId;
        final int[] generation = state.generation;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final float[] delayMemory = state.delayMemory;
        final float[] outputsPrev = state.outputsPrev;
        final double[] cellChem = state.cellChem;

        out.writeInt(state.tick);
        out.writeInt(unitCount);
        out.writeInt(maxGenes);
        for (int slot = 0; slot < unitCount; slot++) {
            final int count = geneCount[slot];
            out.writeInt(count);
            final int base = slot * maxGenes;
            for (int g = 0; g < count; g++) {
                out.writeInt(genes[base + g]);
            }
            out.writeDouble(energy[slot]);
            out.writeDouble(mass[slot]);
            out.writeDouble(damage[slot]);
            out.writeLong(lineageId[slot]);
            out.writeInt(generation[slot]);
            out.writeInt(position[slot]);
        }
        for (int cell = 0; cell < unitCount; cell++) {
            for (int k = 0; k < state.chemCount; k++) {
                out.writeDouble(cellChem[k * unitCount + cell]);
            }
            out.writeInt(cellOccupant[cell]);
        }
        for (int i = 0; i < outputsPrev.length; i++) {
            out.writeFloat(outputsPrev[i]);
        }
        for (int i = 0; i < delayMemory.length; i++) {
            out.writeFloat(delayMemory[i]);
        }
        out.writeDouble(state.energySink);
        out.writeDouble(state.cumulativeInflow);
        out.writeLong(state.birthsTotal);
        out.writeInt(state.maxGeneration);
    }

    /**
     * Rehydrate this kernel from a {@link #saveState} dump, overwriting all
     * mutable state and rebuilding everything structure-derived. The kernel must
     * have been constructed with the same {@code worldWidth} and {@code maxGenes}
     * as the saved run (the {@link com.simolution.sim.Replayer} builds it from the
     * founders in the manifest), so the founding constants and array sizes already
     * match; this only fast-forwards the mutable arrays to the checkpoint tick.
     * After loading, ticking forward produces history bit-identical to the
     * canonical run from tick 0 (asserted by the replay-exactness test).
     */
    public void loadState(final java.io.DataInput in) throws java.io.IOException {
        final int[] genes = state.genes;
        final int[] geneCount = state.geneCount;
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final double[] damage = state.damage;
        final long[] lineageId = state.lineageId;
        final int[] generation = state.generation;
        final int[] position = state.position;
        final int[] cellOccupant = state.cellOccupant;
        final float[] delayMemory = state.delayMemory;
        final float[] outputsPrev = state.outputsPrev;
        final float[] outputsNext = state.outputsNext;
        final double[] cellChem = state.cellChem;
        final int[] activeThisTick = state.activeThisTick;

        state.tick = in.readInt();
        final int savedUnits = in.readInt();
        final int savedMaxGenes = in.readInt();
        if (savedUnits != unitCount || savedMaxGenes != maxGenes) {
            throw new IllegalStateException(
                    "checkpoint shape (" + savedUnits + " slots, maxGenes " + savedMaxGenes
                    + ") does not match this kernel (" + unitCount + " slots, maxGenes "
                    + maxGenes + ")");
        }
        for (int slot = 0; slot < unitCount; slot++) {
            final int count = in.readInt();
            geneCount[slot] = count;
            final int base = slot * maxGenes;
            for (int g = 0; g < count; g++) {
                genes[base + g] = in.readInt();
            }
            energy[slot] = in.readDouble();
            mass[slot] = in.readDouble();
            damage[slot] = in.readDouble();
            lineageId[slot] = in.readLong();
            generation[slot] = in.readInt();
            position[slot] = in.readInt();
        }
        for (int cell = 0; cell < unitCount; cell++) {
            for (int k = 0; k < state.chemCount; k++) {
                cellChem[k * unitCount + cell] = in.readDouble();
            }
            cellOccupant[cell] = in.readInt();
        }
        for (int i = 0; i < outputsPrev.length; i++) {
            outputsPrev[i] = in.readFloat();
        }
        Arrays.fill(outputsNext, 0.0f);
        for (int i = 0; i < delayMemory.length; i++) {
            delayMemory[i] = in.readFloat();
        }
        state.energySink = in.readDouble();
        state.cumulativeInflow = in.readDouble();
        state.birthsTotal = in.readLong();
        state.maxGeneration = in.readInt();

        Arrays.fill(activeThisTick, 0);
        for (int slot = 0; slot < unitCount; slot++) {
            SlotCompiler.compile(state, slot);
        }
    }
}