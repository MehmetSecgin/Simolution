package com.simolution.kernel.state;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.FieldSpec.Dim;
import com.simolution.kernel.state.FieldSpec.OnBirth;
import com.simolution.kernel.state.FieldSpec.OnDeath;
import com.simolution.kernel.state.FieldSpec.Persist;

/**
 * Every array and scalar the kernel mutates, allocated once from an
 * {@link EngineConfig}. The kernel holds the laws; this class holds the state, and
 * {@link #fields()} declares each array so generic code (birth reset, death,
 * checkpoint codecs) can walk them without a second list to keep in sync.
 * <p>
 * The grid is the slot pool, so slots and cells are both {@code worldWidth²}.
 * Founders are installed by the kernel; a fresh state has every slot dead
 * ({@code energy 0}), no lineage ({@code -1}), no position ({@code -1}) and an empty
 * cell occupancy ({@code -1}).
 */
public final class WorldState {

    public final int worldWidth;
    public final int maxGenes;
    public final int unitCount;
    public final int chemCount;

    public final int[] genes;
    public final int[] geneCount;
    public final double[] energy;
    public final double[] mass;
    public final double[] damage;
    public final long[] lineageId;
    public final int[] generation;
    public final int[] position;
    public final int[] cellOccupant;
    public final float[] delayMemory;
    public final double[] unitChem;

    public float[] outputsPrev;
    public float[] outputsNext;
    public double[] cellChem;
    public double[] cellChemNext;

    public final short[] connSrc;
    public final short[] connDst;
    public final double[] connWeight;
    public final int[] sumStart;
    public final int[] sumEnd;
    public final int[] mulStart;
    public final int[] mulEnd;
    public final int[] harvestConnCount;
    public final int[] mulInDegree;

    public final float[] accumulators;
    public final float[] mulTop1;
    public final float[] mulTop2;
    public final int[] activeThisTick;

    public int tick;
    public double energySink;
    public double cumulativeInflow;
    public long birthsTotal;
    public int maxGeneration;
    public double creditedInitialEnergy;
    public double creditedInitialMass;
    public final double initialResourceTotal;

    private final List<FieldSpec> resetOnBirth;
    private final List<FieldSpec> zeroOnDeath;

    public WorldState(final EngineConfig config) {
        this.worldWidth = config.worldWidth();
        this.maxGenes = config.maxGenes();
        this.unitCount = worldWidth * worldWidth;
        this.chemCount = config.chemicals().size();
        final int slots = unitCount;
        final int totalNodes = slots * NodeLayout.TOTAL;
        final int geneCapacity = slots * maxGenes;

        this.genes = new int[geneCapacity];
        this.geneCount = new int[slots];
        this.energy = new double[slots];
        this.mass = new double[slots];
        this.damage = new double[slots];
        this.lineageId = new long[slots];
        Arrays.fill(lineageId, -1L);
        this.generation = new int[slots];
        this.position = new int[slots];
        Arrays.fill(position, -1);
        this.cellOccupant = new int[slots];
        Arrays.fill(cellOccupant, -1);
        this.delayMemory = new float[totalNodes];
        this.unitChem = new double[chemCount * slots];

        this.outputsPrev = new float[totalNodes];
        this.outputsNext = new float[totalNodes];
        this.cellChem = new double[chemCount * slots];
        this.cellChemNext = new double[chemCount * slots];
        double initialTotal = 0.0;
        for (int k = 0; k < chemCount; k++) {
            final ChemicalSpec chemical = config.chemicals().get(k);
            Arrays.fill(cellChem, k * slots, (k + 1) * slots, chemical.initial());
            initialTotal += chemical.initial() * slots;
        }
        this.initialResourceTotal = initialTotal;

        this.connSrc = new short[geneCapacity];
        this.connDst = new short[geneCapacity];
        this.connWeight = new double[geneCapacity];
        this.sumStart = new int[slots];
        this.sumEnd = new int[slots];
        this.mulStart = new int[slots];
        this.mulEnd = new int[slots];
        this.harvestConnCount = new int[slots];
        this.mulInDegree = new int[slots];

        this.accumulators = new float[totalNodes];
        this.mulTop1 = new float[slots];
        this.mulTop2 = new float[slots];
        this.activeThisTick = new int[slots];

        final List<FieldSpec> reset = new ArrayList<>();
        final List<FieldSpec> zero = new ArrayList<>();
        for (final FieldSpec spec : fields()) {
            if (spec.onBirth() == OnBirth.RESET_ZERO) {
                zeroStride(spec);
                reset.add(spec);
            }
            if (spec.onDeath() == OnDeath.ZERO) {
                zeroStride(spec);
                zero.add(spec);
            }
        }
        this.resetOnBirth = List.copyOf(reset);
        this.zeroOnDeath = List.copyOf(zero);
    }

    public void swapOutputs() {
        final float[] tmp = outputsPrev;
        outputsPrev = outputsNext;
        outputsNext = tmp;
    }

    public void swapChem() {
        final double[] tmp = cellChem;
        cellChem = cellChemNext;
        cellChemNext = tmp;
    }

    /**
     * Every array in a fixed order: saved arrays first, then derived, then scratch.
     * Built fresh on each call so double-buffered names bind to the current buffers.
     */
    public List<FieldSpec> fields() {
        final Persist saved = Persist.SAVED;
        final Persist derived = Persist.DERIVED;
        final Persist scratch = Persist.SCRATCH;
        final OnBirth explicit = OnBirth.EXPLICIT;
        final OnBirth zeroed = OnBirth.RESET_ZERO;
        return List.of(
                new FieldSpec("genes", Dim.GENE, saved, explicit, OnDeath.KEEP, genes),
                new FieldSpec("geneCount", Dim.UNIT, saved, explicit, OnDeath.KEEP, geneCount),
                new FieldSpec("energy", Dim.UNIT, saved, explicit, OnDeath.KEEP, energy),
                new FieldSpec("mass", Dim.UNIT, saved, explicit, OnDeath.ZERO, mass),
                new FieldSpec("damage", Dim.UNIT, saved, zeroed, OnDeath.KEEP, damage),
                new FieldSpec("lineageId", Dim.UNIT, saved, explicit, OnDeath.KEEP, lineageId),
                new FieldSpec("generation", Dim.UNIT, saved, explicit, OnDeath.KEEP, generation),
                new FieldSpec("position", Dim.UNIT, saved, explicit, OnDeath.KEEP, position),
                new FieldSpec("cellChem", Dim.CELL_CHEM, saved, explicit, OnDeath.KEEP, cellChem),
                new FieldSpec("cellOccupant", Dim.CELL, saved, explicit, OnDeath.KEEP, cellOccupant),
                new FieldSpec("outputsPrev", Dim.UNIT_NODE, saved, zeroed, OnDeath.KEEP, outputsPrev),
                new FieldSpec("delayMemory", Dim.UNIT_NODE, saved, zeroed, OnDeath.KEEP, delayMemory),
                new FieldSpec("unitChem", Dim.CELL_CHEM, saved, explicit, OnDeath.KEEP, unitChem),
                new FieldSpec("connSrc", Dim.GENE, derived, explicit, OnDeath.KEEP, connSrc),
                new FieldSpec("connDst", Dim.GENE, derived, explicit, OnDeath.KEEP, connDst),
                new FieldSpec("connWeight", Dim.GENE, derived, explicit, OnDeath.KEEP, connWeight),
                new FieldSpec("sumStart", Dim.UNIT, derived, explicit, OnDeath.KEEP, sumStart),
                new FieldSpec("sumEnd", Dim.UNIT, derived, explicit, OnDeath.KEEP, sumEnd),
                new FieldSpec("mulStart", Dim.UNIT, derived, explicit, OnDeath.KEEP, mulStart),
                new FieldSpec("mulEnd", Dim.UNIT, derived, explicit, OnDeath.KEEP, mulEnd),
                new FieldSpec("harvestConnCount", Dim.UNIT, derived, explicit, OnDeath.KEEP, harvestConnCount),
                new FieldSpec("mulInDegree", Dim.UNIT, derived, explicit, OnDeath.KEEP, mulInDegree),
                new FieldSpec("accumulators", Dim.UNIT_NODE, scratch, explicit, OnDeath.KEEP, accumulators),
                new FieldSpec("mulTop1", Dim.UNIT, scratch, explicit, OnDeath.KEEP, mulTop1),
                new FieldSpec("mulTop2", Dim.UNIT, scratch, explicit, OnDeath.KEEP, mulTop2),
                new FieldSpec("activeThisTick", Dim.UNIT, scratch, zeroed, OnDeath.KEEP, activeThisTick),
                new FieldSpec("outputsNext", Dim.UNIT_NODE, scratch, zeroed, OnDeath.KEEP, outputsNext),
                new FieldSpec("cellChemNext", Dim.CELL_CHEM, scratch, explicit, OnDeath.KEEP, cellChemNext));
    }

    /**
     * Zeroes every {@link OnBirth#RESET_ZERO} entry of a slot. The reset list was fixed at
     * construction; both buffers of a swapped pair are in it, so a swap cannot make it stale.
     */
    public void resetForBirth(final int slot) {
        for (int i = 0; i < resetOnBirth.size(); i++) {
            zeroSlot(resetOnBirth.get(i), slot);
        }
    }

    /** Zeroes every {@link OnDeath#ZERO} entry of a slot. */
    public void applyDeath(final int slot) {
        for (int i = 0; i < zeroOnDeath.size(); i++) {
            zeroSlot(zeroOnDeath.get(i), slot);
        }
    }

    /**
     * Entries per slot of a field that is zeroed per slot. Only {@link Dim#UNIT} and
     * {@link Dim#UNIT_NODE} have a per-slot extent here, so any other shape declared
     * with a zeroing policy is a declaration error.
     */
    static int zeroStride(final FieldSpec spec) {
        return switch (spec.dim()) {
            case UNIT -> 1;
            case UNIT_NODE -> NodeLayout.TOTAL;
            default -> throw new IllegalStateException(
                    "field " + spec.name() + " has dim " + spec.dim() + " and cannot be zeroed per slot");
        };
    }

    private static void zeroSlot(final FieldSpec spec, final int slot) {
        final int stride = zeroStride(spec);
        final int from = slot * stride;
        final int to = from + stride;
        switch (spec.array()) {
            case double[] a -> Arrays.fill(a, from, to, 0.0);
            case float[] a -> Arrays.fill(a, from, to, 0.0f);
            case int[] a -> Arrays.fill(a, from, to, 0);
            case long[] a -> Arrays.fill(a, from, to, 0L);
            default -> throw new IllegalStateException("cannot zero field " + spec.name());
        }
    }
}
