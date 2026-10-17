package com.simolution.sim;

public record DynamicsSummary(
        int ticks,
        long ticksWithAnyActivity,
        long propagationsTotal,
        double finalEnergyTotal,
        double finalEnergySink,
        double initialEnergyTotal,
        double initialReservoir,
        double finalReservoir,
        double cumulativeInflow,
        long stateDigest,
        int peakPopulation,
        int finalPopulation,
        int distinctLineagesAlive,
        long birthsTotal,
        int maxGeneration,
        double creditedInitialMass,
        double finalMassTotal,
        double finalMassMax
) {

    /**
     * Open-system audit (contract v1 §7): energy flows reservoir → units →
     * sink, and a fixed inflow tops up the reservoir. Inflow is the accounted
     * source, so the closed-system balance generalises: starting energy plus
     * everything admitted by inflow must equal live energy + reservoir + sink,
     * to floating-point exactness.
     */
    public double energyAuditError() {
        final double credited = initialEnergyTotal + initialReservoir + creditedInitialMass + cumulativeInflow;
        final double held = finalEnergyTotal + finalReservoir + finalMassTotal + finalEnergySink;
        return Math.abs(credited - held);
    }

    /** Energy drawn out of the reservoir into units over the whole run. */
    public double intakeTotal() {
        return initialReservoir + cumulativeInflow - finalReservoir;
    }
}
