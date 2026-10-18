package com.simolution.sim;

import com.simolution.kernel.state.WorldState;

public class DynamicsObserver implements TickObserver {

    private long ticksWithAnyActivity;
    private long propagationsTotal;
    private int peakPopulation;
    private int population;
    private WorldState last;

    @Override
    public void observe(final WorldState snapshot) {
        last = snapshot;

        final int[] active = snapshot.activeThisTick;
        final double[] energy = snapshot.energy;
        long propagations = 0;
        population = 0;
        for (int slot = 0; slot < active.length; slot++) {
            propagations += active[slot];
            if (energy[slot] > 0.0) {
                population++;
            }
        }
        propagationsTotal += propagations;
        if (propagations > 0) {
            ticksWithAnyActivity++;
        }
        if (population > peakPopulation) {
            peakPopulation = population;
        }
    }

    /** Reads the last observed snapshot's live kernel arrays, so call it before the kernel ticks again. */
    public DynamicsSummary summarize(final long finalStateDigest) {
        if (last == null) {
            return DynamicsSummary.empty(finalStateDigest);
        }
        return summaryOf(last, finalStateDigest);
    }

    private DynamicsSummary summaryOf(final WorldState snapshot, final long finalStateDigest) {
        return new DynamicsSummary(
                new DynamicsSummary.Activity(ticksWithAnyActivity, propagationsTotal),
                new DynamicsSummary.Population(peakPopulation, population,
                        distinctLineagesAlive(snapshot), snapshot.birthsTotal, snapshot.maxGeneration),
                new DynamicsSummary.EnergyLedger(snapshot.creditedInitialEnergy, sum(snapshot.energy),
                        snapshot.energySink, snapshot.initialResourceTotal, snapshot.reservoir(),
                        snapshot.cumulativeInflow),
                new DynamicsSummary.Mass(snapshot.creditedInitialMass, snapshot.massTotal(),
                        maxLivingMass(snapshot)),
                finalStateDigest);
    }

    private static double sum(final double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static int distinctLineagesAlive(final WorldState snapshot) {
        final int slots = snapshot.energy.length;
        final boolean[] seen = new boolean[slots];
        int distinct = 0;
        for (int slot = 0; slot < slots; slot++) {
            final long lineage = snapshot.lineageId[slot];
            if (snapshot.energy[slot] > 0.0 && lineage >= 0 && lineage < slots && !seen[(int) lineage]) {
                seen[(int) lineage] = true;
                distinct++;
            }
        }
        return distinct;
    }

    private static double maxLivingMass(final WorldState snapshot) {
        double max = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] > 0.0 && snapshot.mass[slot] > max) {
                max = snapshot.mass[slot];
            }
        }
        return max;
    }
}
