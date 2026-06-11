package com.simolution.kernel.runtime;

public final class KernelSnapshot {
    public final int tick;
    public final double[] outputs;
    public final double[] delayMemory;

    public KernelSnapshot(int tick, double[] outputs, double[] delayMemory) {
        this.tick = tick;
        this.outputs = outputs;
        this.delayMemory = delayMemory;
    }
}
