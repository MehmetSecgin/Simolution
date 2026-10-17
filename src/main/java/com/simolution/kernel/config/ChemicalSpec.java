package com.simolution.kernel.config;

/**
 * One diffusing resource field of the world. {@code decayTarget == -1} dissipates the
 * decayed share to the energy sink; other values are reserved for chemical-to-chemical
 * conversion.
 */
public record ChemicalSpec(
        String name,
        InflowConfig inflow,
        double capacity,
        double initial,
        double diffusionRate,
        double decayRate,
        int decayTarget) {
}
