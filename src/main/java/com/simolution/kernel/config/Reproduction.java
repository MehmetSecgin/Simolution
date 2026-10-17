package com.simolution.kernel.config;

public record Reproduction(double buildCost, double buildCostPerGene) {

    public Reproduction {
        Checks.nonNegative("buildCost", buildCost);
        Checks.nonNegative("buildCostPerGene", buildCostPerGene);
    }
}
