package com.simolution.kernel.runtime;

public final class KernelSnapshot {
    public final int tick;
    public final double[] outputs;
    public final double[] delayMemory;
    public final double[] energy;
    public final double[] damage;
    public final double energySink;
    public final double reservoir;
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
    public final double massTotal;
    public final double creditedInitialMass;

    public KernelSnapshot(int tick, double[] outputs, double[] delayMemory, double[] energy,
                          double[] damage, double energySink, double reservoir, double[] resourceField,
                          int worldWidth, int[] position, double initialResourceTotal, double cumulativeInflow,
                          long[] lineageId, int[] generation, long birthsTotal,
                          int maxGeneration, double creditedInitialEnergy,
                          int[] genes, int[] geneCount, int maxGenes,
                          double[] mass, double massTotal, double creditedInitialMass) {
        this.tick = tick;
        this.outputs = outputs;
        this.delayMemory = delayMemory;
        this.energy = energy;
        this.damage = damage;
        this.energySink = energySink;
        this.reservoir = reservoir;
        this.resourceField = resourceField;
        this.worldWidth = worldWidth;
        this.position = position;
        this.initialResourceTotal = initialResourceTotal;
        this.cumulativeInflow = cumulativeInflow;
        this.lineageId = lineageId;
        this.generation = generation;
        this.birthsTotal = birthsTotal;
        this.maxGeneration = maxGeneration;
        this.creditedInitialEnergy = creditedInitialEnergy;
        this.genes = genes;
        this.geneCount = geneCount;
        this.maxGenes = maxGenes;
        this.mass = mass;
        this.massTotal = massTotal;
        this.creditedInitialMass = creditedInitialMass;
    }
}
