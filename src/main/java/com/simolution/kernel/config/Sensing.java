package com.simolution.kernel.config;

public record Sensing(
        double selfEnergyScale,
        double selfMassScale
) {

    public Sensing {
        Checks.positive("selfEnergyScale", selfEnergyScale);
        Checks.positive("selfMassScale", selfMassScale);
    }
}
