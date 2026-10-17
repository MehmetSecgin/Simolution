package com.simolution.kernel.config;

public record Metabolism(double basalCost, double costPerPropagation, double maintenancePerMass, double agingCost) {

    public Metabolism {
        Checks.nonNegative("basalCost", basalCost);
        Checks.nonNegative("costPerPropagation", costPerPropagation);
        Checks.nonNegative("maintPerMass", maintenancePerMass);
        Checks.nonNegative("agingCost", agingCost);
    }
}
