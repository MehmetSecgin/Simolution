package com.simolution.kernel.config;

/**
 * The world's resource-inflow field as composable {@link InflowSource}s
 * (contract v7). The cap at a cell is {@code combine(baseline, source₀, source₁, …)}
 * — a pure function of {@code (x, y, tick)}. This is pure spec data; the kernel runs
 * the {@link #compile(int) compiled} form.
 * <p>
 * {@link #UNIFORM} (no sources, baseline {@code CELL_INFLOW}) is the v1–v5 law:
 * every cell admits the same inflow each tick. {@link #cyclic} reproduces the
 * contract-v6 single central pulsing disk exactly (one {@link InflowSource.Disk} at
 * the lattice centre, baseline 0). {@link #field} composes any sources for the
 * general patchy/noise/moving case.
 * <p>
 * Oscillation and motion are pure functions of {@code tick} (no wall-clock) and
 * noise is trig-free integer-lattice value noise, so determinism (contract v3 §1)
 * holds. Inflow stays the audited source (cumulativeInflow), so conservation
 * (contract v5 §6) is unaffected — only the spatial/temporal <i>distribution</i> of
 * inflow changes.
 */
public record InflowConfig(double baseline, InflowSource[] sources, Combine combine) {

    /** How overlapping sources (and the baseline) merge into a single cap. */
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

    public static final InflowConfig UNIFORM =
            new InflowConfig(KernelConfig.CELL_INFLOW, new InflowSource[0], Combine.MAX);

    /** The general case: a baseline plus any composed sources. */
    public static InflowConfig field(final double baseline, final Combine combine, final InflowSource... sources) {
        return new InflowConfig(baseline, sources, combine);
    }

    /**
     * The contract-v6 patchy/cyclic source: a single pulsing {@link InflowSource.Disk}
     * of {@code radius} cells at the lattice centre {@code (W/2, W/2)}, baseline 0,
     * barren periphery. For even worlds {@code 0.5·W == W/2} exactly, so the cap is
     * byte-identical to the v6 implementation.
     */
    public static InflowConfig cyclic(final int periodTicks, final int radius, final double peak) {
        if (periodTicks < 1) {
            throw new IllegalArgumentException("cyclic inflow periodTicks must be >= 1");
        }
        final InflowSource disk = new InflowSource.Disk(
                0.5, 0.5, radius, peak, periodTicks, 0, InflowSource.Motion.STATIC, 0.0, 0.0, 0);
        return new InflowConfig(0.0, new InflowSource[] { disk }, Combine.MAX);
    }

    /** Compile for a fixed world width: bake static sources once, keep dynamic ones live. */
    public CompiledInflowField compile(final int worldWidth) {
        return new CompiledInflowField(this, worldWidth);
    }
}
