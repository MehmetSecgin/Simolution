package com.simolution.kernel.config;

import java.util.List;

public record InflowConfig(
        double baseline,
        List<InflowSource> sources,
        Combine combine
) {

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

    public CompiledInflowField compile(final int worldWidth) {
        return new CompiledInflowField(this, worldWidth);
    }
}
