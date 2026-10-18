package com.simolution.kernel.config;

/**
 * One diffusing resource field of the world. Its decayed share goes to the energy sink.
 */
public record ChemicalSpec(
        String name,
        InflowConfig inflow,
        double capacity,
        double initial,
        double diffusionRate,
        double decayRate
) {}
