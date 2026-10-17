package com.simolution.kernel.config;

/**
 * One diffusing resource field of the world. {@code decayTarget == -1} dissipates the
 * decayed share to the energy sink; another chemical's index moves the decayed share into
 * that chemical.
 */
public record ChemicalSpec(
        String name,
        InflowConfig inflow,
        double capacity,
        double initial,
        double diffusionRate,
        double decayRate,
        int decayTarget) {

    public static final int SINK = -1;
}
