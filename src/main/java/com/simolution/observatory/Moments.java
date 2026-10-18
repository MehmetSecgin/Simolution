package com.simolution.observatory;

import java.util.ArrayList;
import java.util.List;

public final class Moments {

    static final double CRASH_DROP = 0.30;
    static final double CRASH_WINDOW = 0.05;
    static final double CRASH_SPACING = 0.10;
    static final int CRASH_MIN_RUNNING_MAX = 30;
    static final double TAKEOVER_SHARE = 0.5;
    static final int TAKEOVER_MIN_POPULATION = 30;
    static final int TAKEOVER_MIN_FOUNDERS = 2;

    public record Moment(
            int tick,
            String mark,
            String label
    ) {}

    private Moments() {}

    public static List<Moment> detect(final MetricsTable metrics, final int targetTicks, final int founders) {
        final List<Moment> moments = new ArrayList<>();
        final double[] ticks = metrics.column("tick");
        final double[] population = metrics.column("population");
        if (metrics.rows() == 0) {
            return moments;
        }
        peak(moments, ticks, population);
        crashes(moments, ticks, population, targetTicks);
        takeover(moments, ticks, population, metrics.column("top_lineage"), metrics.column("top_lineage_share"),
                founders);
        extinction(moments, ticks, population);
        return moments;
    }

    private static void peak(final List<Moment> moments, final double[] ticks, final double[] population) {
        int best = 0;
        for (int r = 1; r < population.length; r++) {
            if (population[r] > population[best]) {
                best = r;
            }
        }
        moments.add(new Moment((int) ticks[best], "P", "Peak population " + (long) population[best]));
    }

    private static void crashes(final List<Moment> moments, final double[] ticks, final double[] population,
            final int targetTicks) {
        final int window = Math.max(1, (int) (CRASH_WINDOW * targetTicks));
        final int spacing = Math.max(1, (int) (CRASH_SPACING * targetTicks));
        int nextAllowed = Integer.MIN_VALUE;
        for (int r = 1; r < population.length; r++) {
            final int tick = (int) ticks[r];
            if (tick < nextAllowed) {
                continue;
            }
            double recentMax = 0.0;
            for (int back = r - 1; back >= 0 && ticks[back] >= tick - window; back--) {
                recentMax = Math.max(recentMax, population[back]);
            }
            if (recentMax >= CRASH_MIN_RUNNING_MAX && population[r] <= (1.0 - CRASH_DROP) * recentMax) {
                moments.add(new Moment(tick, "C",
                        "Crash from " + (long) recentMax + " to " + (long) population[r] + " units"));
                nextAllowed = tick + spacing;
            }
        }
    }

    private static void takeover(final List<Moment> moments, final double[] ticks, final double[] population,
            final double[] topLineage, final double[] topShare, final int founders) {
        if (founders < TAKEOVER_MIN_FOUNDERS) {
            return;
        }
        for (int r = 0; r < population.length; r++) {
            if (population[r] >= TAKEOVER_MIN_POPULATION && topShare[r] > TAKEOVER_SHARE) {
                moments.add(new Moment((int) ticks[r], "T",
                        "Lineage " + (long) topLineage[r] + " holds over half the units"));
                return;
            }
        }
    }

    private static void extinction(final List<Moment> moments, final double[] ticks, final double[] population) {
        for (int r = 0; r < population.length; r++) {
            if (population[r] == 0.0) {
                moments.add(new Moment((int) ticks[r], "X", "Extinct"));
                return;
            }
        }
    }
}
