package com.simolution.sim;

import com.simolution.kernel.state.WorldState;

/** Keeps at most {@code BUCKETS} evenly spaced rows, so the footprint is constant in ticks. */
public class TimeSeriesSampler implements TickObserver {

    public static final int BUCKETS = 1000;
    public static final String HEADER = "tick,population,births,max_generation,unit_energy,reservoir";

    private final StringBuilder out = new StringBuilder(32 * (BUCKETS + 1));
    private final int step;
    private final int totalTicks;

    public TimeSeriesSampler(final int totalTicks) {
        this.totalTicks = totalTicks;
        this.step = Math.max(1, totalTicks / BUCKETS);
        out.append(HEADER).append('\n');
    }

    @Override
    public void observe(final WorldState snapshot) {
        if (isSampled(snapshot.tick - 1)) {
            appendRow(snapshot);
        }
    }

    private boolean isSampled(final int tickIndex) {
        return tickIndex % step == 0 || tickIndex == totalTicks - 1;
    }

    private void appendRow(final WorldState snapshot) {
        int population = 0;
        double unitEnergy = 0.0;
        for (double e : snapshot.energy) {
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
                .append(',').append(snapshot.reservoir())
                .append('\n');
    }

    public String render() {
        return out.toString();
    }
}
