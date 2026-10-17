package com.simolution.kernel.config;

public record Motility(double fixedCost, double costPerMass, double deadzone) {

    public Motility {
        Checks.nonNegative("moveCostBase", fixedCost);
        Checks.nonNegative("moveCostPerMass", costPerMass);
        Checks.nonNegative("moveDeadzone", deadzone);
    }
}
