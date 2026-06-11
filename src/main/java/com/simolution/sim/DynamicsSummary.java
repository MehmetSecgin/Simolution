package com.simolution.sim;

public record DynamicsSummary(
        int ticks,
        long ticksWithAnyActivity,
        long propagationsTotal,
        long junkSinkPropagations,
        long clampSaturationEvents,
        long threshFlipsTotal,
        int[] firstActivityTick,
        boolean[] dormantAtEnd,
        boolean[] endedAtFixedPoint,
        boolean[] reachedNonFinite,
        double[] maxAbsOutput,
        double[] finalAbsAction,
        int[] deathTick,
        double finalEnergyTotal,
        double finalEnergySink,
        double initialEnergyTotal,
        long stateDigest
) {

    public static final double DIVERGENCE_CUTOFF = 1.0e6;

    public enum Regime { FIXED_POINT, BOUNDED, DIVERGENT }

    /**
     * Non-finite state is divergence by definition: the spec puts no bound
     * on signals, so explosive feedback overflows IEEE doubles to ±Infinity
     * and then NaN (Inf - Inf). NaN compares false against any cutoff, so
     * it must be caught via the explicit flag, not the magnitude test.
     */
    public Regime regime(int unit) {
        if (reachedNonFinite[unit] || maxAbsOutput[unit] > DIVERGENCE_CUTOFF) {
            return Regime.DIVERGENT;
        }
        if (endedAtFixedPoint[unit]) {
            return Regime.FIXED_POINT;
        }
        return Regime.BOUNDED;
    }

    public double junkSinkPropagationFraction() {
        return propagationsTotal == 0 ? 0.0 : (double) junkSinkPropagations / propagationsTotal;
    }

    public int countRegime(Regime which) {
        int n = 0;
        for (int unit = 0; unit < firstActivityTick.length; unit++) {
            if (regime(unit) == which) {
                n++;
            }
        }
        return n;
    }

    public int countDormantFromBirth() {
        int n = 0;
        for (int tick : firstActivityTick) {
            if (tick < 0) {
                n++;
            }
        }
        return n;
    }

    public int countDormantAtEnd() {
        int n = 0;
        for (boolean dormant : dormantAtEnd) {
            if (dormant) {
                n++;
            }
        }
        return n;
    }

    public int countAliveAtEnd() {
        int alive = 0;
        for (int tick : deathTick) {
            if (tick < 0) {
                alive++;
            }
        }
        return alive;
    }

    /**
     * Sorted ascending death ticks of units that died; living units (death
     * tick -1) are excluded. Empty when nobody died.
     */
    public int[] sortedDeathTicks() {
        int dead = deathTick.length - countAliveAtEnd();
        int[] ticks = new int[dead];
        int cursor = 0;
        for (int tick : deathTick) {
            if (tick >= 0) {
                ticks[cursor++] = tick;
            }
        }
        java.util.Arrays.sort(ticks);
        return ticks;
    }

    /**
     * Closed-system audit (contract v0 §3): energy is never created, only
     * moved to the sink. Remaining live energy plus the sink must equal the
     * energy the system started with, to floating-point exactness.
     */
    public double energyAuditError() {
        return Math.abs(initialEnergyTotal - (finalEnergyTotal + finalEnergySink));
    }
}
