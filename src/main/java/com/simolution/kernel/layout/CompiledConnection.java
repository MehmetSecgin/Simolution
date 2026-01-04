package com.simolution.kernel.layout;

public final class CompiledConnection {

    public final int sourceAbsoluteIndex;
    public final int destinationAbsoluteIndex;
    public final double weight;

    public CompiledConnection(int sourceAbsoluteIndex, int destinationAbsoluteIndex, double weight) {
        this.sourceAbsoluteIndex = sourceAbsoluteIndex;
        this.destinationAbsoluteIndex = destinationAbsoluteIndex;
        this.weight = weight;
    }

}
