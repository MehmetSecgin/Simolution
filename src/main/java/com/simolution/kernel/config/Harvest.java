package com.simolution.kernel.config;

public record Harvest(
        double capacityPerConnection,
        double halfSaturation
) {

    public Harvest {
        Checks.nonNegative("harvestCapacityPerConnection", capacityPerConnection);
        Checks.nonNegative("harvestHalfSaturation", halfSaturation);
    }
}
