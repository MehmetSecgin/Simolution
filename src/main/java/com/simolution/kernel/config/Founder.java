package com.simolution.kernel.config;

public record Founder(double initialEnergy, double initialMass) {

    public Founder {
        Checks.nonNegative("initialEnergy", initialEnergy);
        Checks.nonNegative("initialMass", initialMass);
    }
}
