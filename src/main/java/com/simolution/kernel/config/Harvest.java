package com.simolution.kernel.config;

public record Harvest(double capacityPerConnection, double halfSaturation, double massExponent) {

    public Harvest {
        Checks.nonNegative("harvestCapacityPerConnection", capacityPerConnection);
        Checks.nonNegative("harvestHalfSaturation", halfSaturation);
        Checks.nonNegative("harvestMassExponent", massExponent);
    }
}
