package com.simolution.kernel.layout;

public record CompiledConnection(
        int sourceAbsoluteIndex,
        int destinationAbsoluteIndex,
        double weight
) {}
