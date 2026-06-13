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
        double[] finalEnergy,
        double[] peakBurn,
        long[] propagationsByUnit,
        int[] ticksActiveByUnit,
        long[] clampByUnit,
        long[] threshFlipsByUnit,
        int[] harvestTicksByUnit,
        double finalEnergyTotal,
        double finalEnergySink,
        double initialEnergyTotal,
        double initialEnergyPerUnit,
        double initialReservoir,
        double finalReservoir,
        double cumulativeInflow,
        long harvestActiveTicksTotal,
        long stateDigest,
        int peakPopulation,
        int finalPopulation,
        int distinctLineagesAlive,
        long birthsTotal,
        int maxGeneration
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
     * Open-system audit (contract v1 §7): energy flows reservoir → units →
     * sink, and a fixed inflow tops up the reservoir. Inflow is the accounted
     * source, so the closed-system balance generalises: starting energy plus
     * everything admitted by inflow must equal live energy + reservoir + sink,
     * to floating-point exactness.
     */
    public double energyAuditError() {
        final double credited = initialEnergyTotal + initialReservoir + cumulativeInflow;
        final double held = finalEnergyTotal + finalReservoir + finalEnergySink;
        return Math.abs(credited - held);
    }

    /** Energy drawn out of the reservoir into units over the whole run. */
    public double intakeTotal() {
        return initialReservoir + cumulativeInflow - finalReservoir;
    }

    public int countEverHarvested() {
        int n = 0;
        for (int ticks : harvestTicksByUnit) {
            if (ticks > 0) {
                n++;
            }
        }
        return n;
    }

    /**
     * Ticks the unit was alive: its death tick, or the whole run if it never
     * died. Born at tick 0, so this is also age at death.
     */
    public int lifespan(int unit) {
        return deathTick[unit] < 0 ? ticks : deathTick[unit];
    }

    public double energyConsumed(int unit) {
        return initialEnergyPerUnit - finalEnergy[unit];
    }

    /** Mean energy burned per tick of life; 0 for a unit that lived 0 ticks. */
    public double meanBurnRate(int unit) {
        int life = lifespan(unit);
        return life == 0 ? 0.0 : energyConsumed(unit) / life;
    }

    public double[] meanBurnRates() {
        double[] rates = new double[deathTick.length];
        for (int unit = 0; unit < rates.length; unit++) {
            rates[unit] = meanBurnRate(unit);
        }
        return rates;
    }
}
