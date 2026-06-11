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
}
