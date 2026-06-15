package com.simolution.kernel.config;

/**
 * The inflow field {@link InflowConfig} compiled for a fixed {@code worldWidth}
 * (contract v7), the {@code GenomeCompiler} analogue for resource inflow. Built
 * once via {@link InflowConfig#compile(int)}; the kernel then asks only
 * {@link #cap(int, int)} per cell per tick.
 * <p>
 * Time-invariant sources are folded with the baseline into a precomputed
 * {@code double[cells]} layer at compile time, so the hot path reads an array;
 * time-varying sources (pulses, motion, animated noise) are evaluated per tick on
 * top. When the field is just the bare baseline (the {@link InflowConfig#UNIFORM}
 * default), {@code baked} stays {@code null} and {@code dynamic} empty — no
 * allocation, no per-cell work, byte-identical to the v1–v5 uniform law.
 */
public final class CompiledInflowField {

    private final double baseline;
    private final double[] baked;
    private final InflowSource[] dynamic;
    private final InflowConfig.Combine combine;
    private final int worldWidth;

    CompiledInflowField(final InflowConfig config, final int worldWidth) {
        this.baseline = config.baseline();
        this.combine = config.combine();
        this.worldWidth = worldWidth;

        final InflowSource[] sources = config.sources();
        int staticCount = 0;
        int dynamicCount = 0;
        for (final InflowSource s : sources) {
            if (s.isStatic()) {
                staticCount++;
            } else {
                dynamicCount++;
            }
        }

        this.dynamic = new InflowSource[dynamicCount];
        int di = 0;
        for (final InflowSource s : sources) {
            if (!s.isStatic()) {
                this.dynamic[di++] = s;
            }
        }

        if (staticCount == 0) {
            this.baked = null;
            return;
        }

        final int cells = worldWidth * worldWidth;
        this.baked = new double[cells];
        for (int cell = 0; cell < cells; cell++) {
            final int x = cell % worldWidth;
            final int y = cell / worldWidth;
            double acc = baseline;
            for (final InflowSource s : sources) {
                if (s.isStatic()) {
                    acc = combine.apply(acc, s.at(x, y, 0, worldWidth));
                }
            }
            this.baked[cell] = acc;
        }
    }

    /** Inflow cap at {@code cell} on {@code tick}: baked static layer combined with the dynamic sources. */
    public double cap(final int cell, final int tick) {
        double acc = baked != null ? baked[cell] : baseline;
        if (dynamic.length == 0) {
            return acc;
        }
        final int x = cell % worldWidth;
        final int y = cell / worldWidth;
        for (final InflowSource s : dynamic) {
            acc = combine.apply(acc, s.at(x, y, tick, worldWidth));
        }
        return acc;
    }
}
