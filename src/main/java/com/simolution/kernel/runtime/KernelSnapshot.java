package com.simolution.kernel.runtime;

import com.simolution.kernel.phase.Chunks;
import com.simolution.kernel.state.WorldState;

/**
 * A read view of the state after a tick. Scalars are copied; arrays are the live kernel arrays,
 * so read them before the kernel ticks again. The two totals are computed on demand, since most
 * ticks no reader asks for them.
 */
public class KernelSnapshot {
    public final WorldState state;
    public final int tick;
    public final float[] outputs;
    public final float[] delayMemory;
    public final double[] energy;
    public final double[] damage;
    public final double energySink;
    public final double[] resourceField;
    public final int worldWidth;
    public final int[] position;
    public final double initialResourceTotal;
    public final double cumulativeInflow;
    public final long[] lineageId;
    public final int[] generation;
    public final long birthsTotal;
    public final int maxGeneration;
    public final double creditedInitialEnergy;
    public final int[] genes;
    public final int[] geneCount;
    public final int maxGenes;
    public final double[] mass;
    public final double creditedInitialMass;
    public final int[] activeThisTick;

    public KernelSnapshot(final WorldState state) {
        this.state = state;
        this.tick = state.tick;
        this.outputs = state.outputsPrev;
        this.delayMemory = state.delayMemory;
        this.energy = state.energy;
        this.damage = state.damage;
        this.energySink = state.energySink;
        this.resourceField = state.cellChem;
        this.worldWidth = state.worldWidth;
        this.position = state.position;
        this.initialResourceTotal = state.initialResourceTotal;
        this.cumulativeInflow = state.cumulativeInflow;
        this.lineageId = state.lineageId;
        this.generation = state.generation;
        this.birthsTotal = state.birthsTotal;
        this.maxGeneration = state.maxGeneration;
        this.creditedInitialEnergy = state.creditedInitialEnergy;
        this.genes = state.genes;
        this.geneCount = state.geneCount;
        this.maxGenes = state.maxGenes;
        this.mass = state.mass;
        this.creditedInitialMass = state.creditedInitialMass;
        this.activeThisTick = state.activeThisTick;
    }

    /** Resource held by every cell field, as a {@link Chunks#orderedSum chunk-ordered sum}. */
    public double reservoir() {
        return Chunks.orderedSum(resourceField);
    }

    /** Biomass of every slot (dead slots hold 0), as a {@link Chunks#orderedSum chunk-ordered sum}. */
    public double massTotal() {
        return Chunks.orderedSum(mass);
    }
}
