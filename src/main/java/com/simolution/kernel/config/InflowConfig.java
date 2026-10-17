package com.simolution.kernel.config;

import java.util.List;

public record InflowConfig(double baseline, List<InflowSource> sources, Combine combine) {

    private static final double CENTRE = 0.5;

    public InflowConfig {
        sources = List.copyOf(sources);
    }

    public enum Combine {
        MAX {
            @Override
            public double apply(final double a, final double b) {
                return Math.max(a, b);
            }
        },
        ADD {
            @Override
            public double apply(final double a, final double b) {
                return a + b;
            }
        };

        public abstract double apply(double a, double b);
    }

    public static InflowConfig uniform(final double baseline) {
        return new InflowConfig(baseline, List.of(), Combine.MAX);
    }

    public static InflowConfig field(final double baseline, final Combine combine, final InflowSource... sources) {
        return new InflowConfig(baseline, List.of(sources), combine);
    }

    public static InflowConfig cyclic(final int periodTicks, final int radius, final double peak) {
        if (periodTicks < 1) {
            throw new IllegalArgumentException("cyclic inflow periodTicks must be >= 1");
        }
        final InflowSource disk = new InflowSource.Disk(
                CENTRE, CENTRE, radius, peak, periodTicks, 0, InflowSource.Motion.STATIC, 0.0, 0.0, 0);
        return new InflowConfig(0.0, List.of(disk), Combine.MAX);
    }

    public CompiledInflowField compile(final int worldWidth) {
        return new CompiledInflowField(this, worldWidth);
    }
}
