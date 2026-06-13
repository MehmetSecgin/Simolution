package com.simolution.kernel.runtime;

public final class KernelSnapshot {
    public final int tick;
    public final double[] outputs;
    public final double[] delayMemory;
    public final double[] energy;
    public final double energySink;
    public final double reservoir;
    public final double cumulativeInflow;
    public final long[] lineageId;
    public final int[] generation;
    public final long birthsTotal;
    public final int maxGeneration;
    public final double creditedInitialEnergy;

    public KernelSnapshot(int tick, double[] outputs, double[] delayMemory, double[] energy,
                          double energySink, double reservoir, double cumulativeInflow,
                          long[] lineageId, int[] generation, long birthsTotal,
                          int maxGeneration, double creditedInitialEnergy) {
        this.tick = tick;
        this.outputs = outputs;
        this.delayMemory = delayMemory;
        this.energy = energy;
        this.energySink = energySink;
        this.reservoir = reservoir;
        this.cumulativeInflow = cumulativeInflow;
        this.lineageId = lineageId;
        this.generation = generation;
        this.birthsTotal = birthsTotal;
        this.maxGeneration = maxGeneration;
        this.creditedInitialEnergy = creditedInitialEnergy;
    }
}
