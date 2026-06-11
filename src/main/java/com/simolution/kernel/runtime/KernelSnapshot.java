package com.simolution.kernel.runtime;

public final class KernelSnapshot {
    public final int tick;
    public final double[] outputs;
    public final double[] delayMemory;
    public final double[] energy;
    public final double energySink;

    public KernelSnapshot(int tick, double[] outputs, double[] delayMemory, double[] energy, double energySink) {
        this.tick = tick;
        this.outputs = outputs;
        this.delayMemory = delayMemory;
        this.energy = energy;
        this.energySink = energySink;
    }
}
