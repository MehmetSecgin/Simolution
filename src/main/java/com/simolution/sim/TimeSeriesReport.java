package com.simolution.sim;

import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * A bounded time series of population-level metrics for visualization. Samples
 * at most {@code BUCKETS} evenly-spaced ticks regardless of run length, so its
 * footprint is constant in ticks (memory discipline): the only population-vs-time
 * data the system keeps, downsampled rather than full-resolution. Unlike the
 * observer's running aggregates this is a small ordered trace, written to the
 * .timeseries.csv sidecar for charting.
 */
public final class TimeSeriesReport {

    public static final int BUCKETS = 1000;
    public static final String HEADER = "tick,population,births,max_generation,unit_energy,reservoir";

    private final StringBuilder out = new StringBuilder(32 * (BUCKETS + 1));
    private final int step;
    private final int totalTicks;

    public TimeSeriesReport(final int totalTicks) {
        this.totalTicks = totalTicks;
        this.step = Math.max(1, totalTicks / BUCKETS);
        out.append(HEADER).append('\n');
    }

    /** Call once per tick with {@code i} the 0-based tick index and the snapshot. */
    public void sample(final int i, final KernelSnapshot snapshot) {
        if (i % step != 0 && i != totalTicks - 1) {
            return;
        }
        int population = 0;
        double unitEnergy = 0.0;
        for (final double e : snapshot.energy) {
            if (e > 0.0) {
                population++;
            }
            unitEnergy += e;
        }
        out.append(snapshot.tick)
           .append(',').append(population)
           .append(',').append(snapshot.birthsTotal)
           .append(',').append(snapshot.maxGeneration)
           .append(',').append(unitEnergy)
           .append(',').append(snapshot.reservoir)
           .append('\n');
    }

    public String render() {
        return out.toString();
    }
}
