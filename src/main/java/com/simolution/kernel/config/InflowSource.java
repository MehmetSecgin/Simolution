package com.simolution.kernel.config;

/**
 * One source of the resource-inflow field (contract v7). The inflow cap at a cell
 * is a pure function {@code f(x, y, tick)}; a source draws part of that field, and
 * {@link InflowConfig} combines a list of them (MAX or ADD) over a baseline. The
 * disk is no longer privileged — it is one shape among rectangles, explicit
 * coordinate masks, and seeded noise maps.
 * <p>
 * Every source is a pure, deterministic function of {@code (x, y, tick, worldWidth)}.
 * {@link #isStatic()} reports whether the source is time-invariant so
 * {@link CompiledInflowField} can bake it once into a precomputed layer (the hot
 * path then reads an array instead of re-evaluating).
 */
public sealed interface InflowSource permits
        InflowSource.Disk, InflowSource.Rect, InflowSource.Points, InflowSource.Noise {

    /** This source's inflow-cap contribution at integer cell {@code (x, y)} on {@code tick}. */
    double at(int x, int y, int tick, int worldWidth);

    /** True if the source never varies with {@code tick} (bakeable into the static layer). */
    boolean isStatic();

    /** Centre motion of a {@link Disk} over time. All pure functions of {@code tick}. */
    enum Motion { STATIC, ORBIT, DRIFT }

    /**
     * A circular patch centred at {@code (fx·W, fy·W)} (fractional coordinates, so a
     * spec is world-size-independent). {@code periodTicks > 0} pulses the cap
     * {@code 0 → peak → 0} as a triangle wave; {@code motion} drifts or orbits the
     * centre. Toroidal-aware membership: the disk wraps across lattice edges.
     */
    record Disk(double fx, double fy, double radius, double peak,
                int periodTicks, int phaseTicks,
                Motion motion, double m0, double m1, int motionPeriod) implements InflowSource {

        @Override
        public double at(final int x, final int y, final int tick, final int worldWidth) {
            final double cx;
            final double cy;
            switch (motion) {
                case ORBIT -> {
                    final double ang = 2.0 * StrictMath.PI * tick / motionPeriod;
                    cx = fx * worldWidth + m0 * worldWidth * StrictMath.cos(ang);
                    cy = fy * worldWidth + m0 * worldWidth * StrictMath.sin(ang);
                }
                case DRIFT -> {
                    cx = fx * worldWidth + m0 * tick;
                    cy = fy * worldWidth + m1 * tick;
                }
                default -> {
                    cx = fx * worldWidth;
                    cy = fy * worldWidth;
                }
            }
            final double dx = wrapDelta(x - cx, worldWidth);
            final double dy = wrapDelta(y - cy, worldWidth);
            if (dx * dx + dy * dy > radius * radius) {
                return 0.0;
            }
            return peak * triangle(tick, periodTicks, phaseTicks);
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0 && motion == Motion.STATIC;
        }
    }

    /**
     * An axis-aligned rectangular region with fractional corners; optional pulse and
     * optional toroidal drift {@code (vx, vy)} in cells/tick. A drifting band wraps
     * across lattice edges, so a vertical stripe ({@code fy0=0, fy1=1}) with
     * {@code vx>0} sweeps left → right → left forever.
     */
    record Rect(double fx0, double fy0, double fx1, double fy1, double peak,
                int periodTicks, int phaseTicks, double vx, double vy) implements InflowSource {

        @Override
        public double at(final int x, final int y, final int tick, final int worldWidth) {
            if (vx == 0.0 && vy == 0.0) {
                final double x0 = fx0 * worldWidth;
                final double y0 = fy0 * worldWidth;
                final double x1 = fx1 * worldWidth;
                final double y1 = fy1 * worldWidth;
                if (x < x0 || x > x1 || y < y0 || y > y1) {
                    return 0.0;
                }
                return peak * triangle(tick, periodTicks, phaseTicks);
            }
            final double wx = (fx1 - fx0) * worldWidth;
            final double wy = (fy1 - fy0) * worldWidth;
            if (!inBand(x, fx0 * worldWidth + vx * tick, wx, worldWidth)
                    || !inBand(y, fy0 * worldWidth + vy * tick, wy, worldWidth)) {
                return 0.0;
            }
            return peak * triangle(tick, periodTicks, phaseTicks);
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0 && vx == 0.0 && vy == 0.0;
        }
    }

    /** An explicit set of cell indices ({@code y·W + x}); the "give coordinates" case. */
    record Points(int[] cells, double peak, int periodTicks, int phaseTicks) implements InflowSource {

        @Override
        public double at(final int x, final int y, final int tick, final int worldWidth) {
            final int cell = y * worldWidth + x;
            for (final int c : cells) {
                if (c == cell) {
                    return peak * triangle(tick, periodTicks, phaseTicks);
                }
            }
            return 0.0;
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0;
        }
    }

    /**
     * A seeded {@link ValueNoise} map thresholded into emergent regions
     * (Minecraft-style worldgen). The noise {@code n ∈ [0,1)} is sampled at
     * {@code (x·frequency, y·frequency, z)}; {@code (n − threshold)·gain} clamped to
     * {@code [0,1]} scales {@code peak}, so {@code threshold} sets coverage and
     * {@code gain} sets edge sharpness. {@code animPeriod > 0} feeds a slow time axis
     * {@code z = tick/animPeriod}, morphing the field; {@code 0} freezes it (static).
     */
    record Noise(long seed, double frequency, double threshold, double gain,
                 double peak, int animPeriod) implements InflowSource {

        @Override
        public double at(final int x, final int y, final int tick, final int worldWidth) {
            final double z = animPeriod == 0 ? 0.0 : (double) tick / animPeriod;
            final double n = ValueNoise.at(seed, x * frequency, y * frequency, z);
            double t = (n - threshold) * gain;
            if (t <= 0.0) {
                return 0.0;
            }
            if (t > 1.0) {
                t = 1.0;
            }
            return t * peak;
        }

        @Override
        public boolean isStatic() {
            return animPeriod == 0;
        }
    }

    /**
     * Triangle pulse in {@code [0, 1]}: rises 0 → 1 over the first half of
     * {@code periodTicks} and falls 1 → 0 over the second, offset by
     * {@code phaseTicks}. {@code periodTicks == 0} is steady (always 1). A run with
     * phase 0 starts at the trough and fills — the contract-v6 convention, kept
     * byte-identical.
     */
    static double triangle(final int tick, final int periodTicks, final int phaseTicks) {
        if (periodTicks == 0) {
            return 1.0;
        }
        final int phase = (((tick + phaseTicks) % periodTicks) + periodTicks) % periodTicks;
        final int half = Math.max(1, periodTicks / 2);
        final double wave = phase < half
                ? (double) phase / half
                : 2.0 - (double) phase / half;
        return Math.max(0.0, wave);
    }

    /**
     * True if integer coordinate {@code coord} lies in a band of width {@code width}
     * cells whose (possibly off-grid) left edge is {@code leftEdge}, on a toroidal
     * width-{@code W} axis. {@code width >= W} spans the whole axis.
     */
    static boolean inBand(final int coord, final double leftEdge, final double width, final int worldWidth) {
        if (width >= worldWidth) {
            return true;
        }
        final double fwd = (((coord - leftEdge) % worldWidth) + worldWidth) % worldWidth;
        return fwd <= width;
    }

    /** Signed toroidal offset of {@code d} on a width-{@code W} axis, in {@code (−W/2, W/2]}. */
    static double wrapDelta(final double d, final int worldWidth) {
        final double half = worldWidth / 2.0;
        double r = d;
        while (r > half) {
            r -= worldWidth;
        }
        while (r < -half) {
            r += worldWidth;
        }
        return r;
    }
}
