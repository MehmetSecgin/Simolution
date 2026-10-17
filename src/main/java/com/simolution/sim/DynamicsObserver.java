package com.simolution.sim;

import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Observes a run from outside the kernel (contract v0 §12: interpretation is external to the
 * laws). The kernel exposes only snapshots; this class keeps running totals over them, never a
 * trajectory, and holds no per-unit state, so it costs one pass over the slot pool per tick.
 * <p>
 * Activity is the kernel's own count: {@link KernelSnapshot#activeThisTick} holds, per slot, the
 * non-zero propagations that unit made this tick (0 for a slot that was dead at the start of the
 * tick), so the totals cover every living unit, descendants included.
 */
public final class DynamicsObserver {

    private int ticksObserved;
    private long ticksWithAnyActivity;
    private long propagationsTotal;
    private int peakPopulation;
    private KernelSnapshot last;

    /** Call once after every kernel.tick(), with that tick's snapshot. */
    public void observe(final KernelSnapshot snapshot) {
        ticksObserved++;
        last = snapshot;

        final int[] active = snapshot.activeThisTick;
        final double[] energy = snapshot.energy;
        long propagations = 0;
        int population = 0;
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

    /**
     * Reads the last observed snapshot's live kernel arrays, so call it before the kernel ticks
     * again. {@code stateDigest} is the final-state {@link com.simolution.kernel.state.StateDigest}.
     */
    public DynamicsSummary summarize(final long stateDigest) {
        final int finalPopulation = last == null ? 0 : livingCount(last);
        final int distinctLineagesAlive = last == null ? 0 : distinctLineagesAlive(last);
        final double finalEnergyTotal = last == null ? 0.0 : sum(last.energy);
        final double finalMassTotal = last == null ? 0.0 : last.massTotal();
        final double finalMassMax = last == null ? 0.0 : maxLivingMass(last);
        final double finalReservoir = last == null ? 0.0 : last.reservoir();

        return new DynamicsSummary(
                ticksObserved,
                ticksWithAnyActivity,
                propagationsTotal,
                finalEnergyTotal,
                last == null ? 0.0 : last.energySink,
                last == null ? 0.0 : last.creditedInitialEnergy,
                last == null ? 0.0 : last.initialResourceTotal,
                finalReservoir,
                last == null ? 0.0 : last.cumulativeInflow,
                stateDigest,
                peakPopulation,
                finalPopulation,
                distinctLineagesAlive,
                last == null ? 0L : last.birthsTotal,
                last == null ? 0 : last.maxGeneration,
                last == null ? 0.0 : last.creditedInitialMass,
                finalMassTotal,
                finalMassMax
        );
    }

    private static double sum(final double[] values) {
        double total = 0.0;
        for (final double value : values) {
            total += value;
        }
        return total;
    }

    private static int livingCount(final KernelSnapshot snapshot) {
        int count = 0;
        for (final double energy : snapshot.energy) {
            if (energy > 0.0) {
                count++;
            }
        }
        return count;
    }

    private static int distinctLineagesAlive(final KernelSnapshot snapshot) {
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

    private static double maxLivingMass(final KernelSnapshot snapshot) {
        double max = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] > 0.0 && snapshot.mass[slot] > max) {
                max = snapshot.mass[slot];
            }
        }
        return max;
    }
}
