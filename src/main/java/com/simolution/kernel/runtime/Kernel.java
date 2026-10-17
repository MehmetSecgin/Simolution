package com.simolution.kernel.runtime;

import java.util.List;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.SlotCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.phase.ClearPhase;
import com.simolution.kernel.phase.CostPhase;
import com.simolution.kernel.phase.DecayPhase;
import com.simolution.kernel.phase.DiffusionPhase;
import com.simolution.kernel.phase.EvaluatePhase;
import com.simolution.kernel.phase.GrowthPhase;
import com.simolution.kernel.phase.InflowPhase;
import com.simolution.kernel.phase.IntakePhase;
import com.simolution.kernel.phase.MovementPhase;
import com.simolution.kernel.phase.ParallelRunner;
import com.simolution.kernel.phase.Phase;
import com.simolution.kernel.phase.PropagatePhase;
import com.simolution.kernel.phase.ReproductionPhase;
import com.simolution.kernel.phase.SwapPhase;
import com.simolution.kernel.state.StateDigest;
import com.simolution.kernel.state.WorldState;

/**
 * The kernel and its {@link ParallelRunner} are single-caller: {@link #tick}, {@link #step},
 * {@link #stateDigest}, {@link #snapshot} and {@link #close} must all come from one thread at a
 * time. A concurrent call corrupts the state or hangs the runner's barrier.
 */
public class Kernel implements AutoCloseable {

    private final EngineConfig config;
    private final WorldState state;
    private final int unitCount;
    /**
     * The tick as an ordered list of declared phases, built once at construction
     * (contract v0 §8, the locked pipeline order, twelve entries): clear, propagate,
     * evaluate, swap, intake, inflow, cost, growth, reproduction, movement, diffusion, decay.
     * Intake is two entries, intake (per unit) then inflow (per cell), so each entry has
     * one {@link com.simolution.kernel.phase.Shape}.
     */
    private final List<Phase> phases;
    private final ParallelRunner runner;
    private final StateDigest digest;

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
     * <p>
     * {@code threads} sizes the {@link ParallelRunner} the kernel owns (close the kernel to
     * stop it). It is an execution setting, not part of the config or of checkpoints: the
     * state after any tick is identical for every thread count.
     */
    public Kernel(final int[][] initialGenomes, final int[] founderCells, final EngineConfig config,
            final int threads) {
        this.config = config;
        final int worldWidth = config.world().worldWidth();
        final int maxGenes = config.world().maxGenes();
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

        this.state = new WorldState(config);
        state.creditedInitialEnergy = config.founder().initialEnergy() * seededCount;
        state.creditedInitialMass = config.founder().initialMass() * seededCount;

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
                energy[unit] = config.founder().initialEnergy();
                mass[unit] = config.founder().initialMass();
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
                new InflowPhase(state, config),
                new CostPhase(state, config),
                new GrowthPhase(state, config),
                new ReproductionPhase(state, config),
                new MovementPhase(state, config),
                new DiffusionPhase(state, config),
                new DecayPhase(state, config));
        this.runner = new ParallelRunner(threads);
        this.digest = new StateDigest(state);
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
     * ({@link Noise#placementUniform}, keyed by the run seed
     * like RAND and mutation). This replaces the old slot==cell fill, which
     * packed founders into a contiguous row-major strip at one edge and imposed
     * an artificial density gradient on every run. The scatter is uniform and
     * reproducible: same count + grid → identical layout. Placement is a setup
     * policy, not a kernel law, so it lives here as a static helper the harness
     * calls, leaving the constructor to take explicit cells.
     */
    public static int[] scatterFounders(final long seed, final int seededCount, final int worldWidth) {
        final int cellCount = worldWidth * worldWidth;
        final int[] cell = new int[cellCount];
        for (int i = 0; i < cellCount; i++) {
            cell[i] = i;
        }
        final int[] founderCell = new int[seededCount];
        for (int i = 0; i < seededCount; i++) {
            final double u = Noise.placementUniform(seed, i);
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

    public void step(final int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    public int currentTick() {
        return state.tick;
    }

    public EngineConfig config() {
        return config;
    }

    public WorldState state() {
        return state;
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

    private CompiledConnection connectionAt(final int unit, final int i) {
        final int unitOffset = unit * NodeLayout.TOTAL;
        return new CompiledConnection(
                unitOffset + state.connSrc[i], unitOffset + state.connDst[i], state.connWeight[i]);
    }

    public KernelSnapshot snapshot() {
        return new KernelSnapshot(state);
    }

    /** {@link StateDigest} of the current state, folded on this kernel's own runner. */
    public long stateDigest() {
        return digest.compute(runner);
    }

    /** Stops the runner's worker threads. Idempotent. */
    @Override
    public void close() {
        runner.close();
    }
}
