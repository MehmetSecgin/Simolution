package com.simolution.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.simolution.kernel.state.WorldState;

public final class EnergyAudit {

    private EnergyAudit() {}

    public static double totalEnergy(final WorldState snapshot) {
        double total = 0.0;
        for (double energy : snapshot.energy) {
            total += energy;
        }
        return total;
    }

    public static double credited(final WorldState snapshot) {
        return snapshot.creditedInitialEnergy + snapshot.initialResourceTotal
                + snapshot.creditedInitialMass + snapshot.cumulativeInflow;
    }

    public static double held(final WorldState snapshot) {
        return totalEnergy(snapshot) + snapshot.reservoir() + snapshot.massTotal() + snapshot.energySink;
    }

    public static void assertConserved(final WorldState snapshot, final double tolerance) {
        assertEquals(credited(snapshot), held(snapshot), tolerance,
                "units + field + mass + sink must equal initial + inflow at tick " + snapshot.tick);
    }

    public static void assertConserved(final WorldState snapshot, final double initialCredit,
            final double tolerance) {
        assertEquals(initialCredit + snapshot.cumulativeInflow, held(snapshot), tolerance,
                "units + field + mass + sink must equal initial + inflow at tick " + snapshot.tick);
    }
}
