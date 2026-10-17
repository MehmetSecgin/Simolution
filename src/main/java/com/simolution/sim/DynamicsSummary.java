package com.simolution.sim;

public record DynamicsSummary(
        Activity activity,
        Population population,
        EnergyLedger energy,
        Mass mass,
        long stateDigest
) {

    public record Activity(
            long ticksWithAnyActivity,
            long propagationsTotal
    ) {}

    public record Population(
            int peak,
            int finalCount,
            int distinctLineagesAlive,
            long birthsTotal,
            int maxGeneration
    ) {}

    public record EnergyLedger(
            double initialEnergy,
            double finalEnergy,
            double finalSink,
            double initialResource,
            double finalResource,
            double cumulativeInflow
    ) {

        public double intakeTotal() {
            return initialResource + cumulativeInflow - finalResource;
        }
    }

    public record Mass(
            double initialMass,
            double finalTotal,
            double finalMax
    ) {}

    public static DynamicsSummary empty(final long stateDigest) {
        return new DynamicsSummary(
                new Activity(0, 0),
                new Population(0, 0, 0, 0, 0),
                new EnergyLedger(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                new Mass(0.0, 0.0, 0.0),
                stateDigest);
    }

    /** Sums in this order on purpose: the report's last digits depend on it. */
    public double energyAuditError() {
        final double credited = energy.initialEnergy() + energy.initialResource() + mass.initialMass()
                + energy.cumulativeInflow();
        final double held = energy.finalEnergy() + energy.finalResource() + mass.finalTotal() + energy.finalSink();
        return Math.abs(credited - held);
    }

    public double finalMassMean() {
        final int living = population.finalCount();
        return living == 0 ? 0.0 : mass.finalTotal() / living;
    }
}
