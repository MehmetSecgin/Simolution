package com.simolution.kernel.config;

import java.util.Arrays;
import java.util.Objects;

public sealed interface InflowSource permits
        InflowSource.Disk, InflowSource.Rect, InflowSource.Points, InflowSource.Noise, InflowSource.Ring {

    double capAt(int x, int y, int tick, int worldWidth);

    boolean isStatic();

    enum Motion { STATIC, ORBIT, DRIFT }

    record Disk(double fx, double fy, double radius, double peak,
                int periodTicks, int phaseTicks,
                Motion motion, double m0, double m1, int motionPeriod) implements InflowSource {

        public double orbitRadiusFraction() {
            return m0;
        }

        public double driftX() {
            return m0;
        }

        public double driftY() {
            return m1;
        }

        @Override
        public double capAt(final int x, final int y, final int tick, final int worldWidth) {
            final double dx = wrapDelta(x - centreX(tick, worldWidth), worldWidth);
            final double dy = wrapDelta(y - centreY(tick, worldWidth), worldWidth);
            return dx * dx + dy * dy > radius * radius ? 0.0 : pulse(peak, tick, periodTicks, phaseTicks);
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0 && motion == Motion.STATIC;
        }

        private double centreX(final int tick, final int worldWidth) {
            return switch (motion) {
                case ORBIT -> fx * worldWidth + orbitRadiusFraction() * worldWidth * StrictMath.cos(orbitAngle(tick));
                case DRIFT -> fx * worldWidth + driftX() * tick;
                case STATIC -> fx * worldWidth;
            };
        }

        private double centreY(final int tick, final int worldWidth) {
            return switch (motion) {
                case ORBIT -> fy * worldWidth + orbitRadiusFraction() * worldWidth * StrictMath.sin(orbitAngle(tick));
                case DRIFT -> fy * worldWidth + driftY() * tick;
                case STATIC -> fy * worldWidth;
            };
        }

        private double orbitAngle(final int tick) {
            return 2.0 * StrictMath.PI * tick / motionPeriod;
        }
    }

    record Rect(double fx0, double fy0, double fx1, double fy1, double peak,
                int periodTicks, int phaseTicks, double vx, double vy) implements InflowSource {

        @Override
        public double capAt(final int x, final int y, final int tick, final int worldWidth) {
            final boolean covered = vx == 0.0 && vy == 0.0
                    ? coversStaticBox(x, y, worldWidth)
                    : coversDriftingBand(x, y, tick, worldWidth);
            return covered ? pulse(peak, tick, periodTicks, phaseTicks) : 0.0;
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0 && vx == 0.0 && vy == 0.0;
        }

        private boolean coversStaticBox(final int x, final int y, final int worldWidth) {
            final double x0 = fx0 * worldWidth;
            final double y0 = fy0 * worldWidth;
            final double x1 = fx1 * worldWidth;
            final double y1 = fy1 * worldWidth;
            return !(x < x0 || x > x1 || y < y0 || y > y1);
        }

        private boolean coversDriftingBand(final int x, final int y, final int tick, final int worldWidth) {
            final double bandWidthX = (fx1 - fx0) * worldWidth;
            final double bandWidthY = (fy1 - fy0) * worldWidth;
            return inBand(x, fx0 * worldWidth + vx * tick, bandWidthX, worldWidth)
                    && inBand(y, fy0 * worldWidth + vy * tick, bandWidthY, worldWidth);
        }
    }

    record Points(int[] cells, double peak, int periodTicks, int phaseTicks) implements InflowSource {

        @Override
        public boolean equals(final Object o) {
            return o instanceof Points other
                    && Arrays.equals(cells, other.cells)
                    && Double.compare(peak, other.peak) == 0
                    && periodTicks == other.periodTicks
                    && phaseTicks == other.phaseTicks;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(cells), peak, periodTicks, phaseTicks);
        }

        @Override
        public double capAt(final int x, final int y, final int tick, final int worldWidth) {
            final int cell = y * worldWidth + x;
            for (final int c : cells) {
                if (c == cell) {
                    return pulse(peak, tick, periodTicks, phaseTicks);
                }
            }
            return 0.0;
        }

        @Override
        public boolean isStatic() {
            return periodTicks == 0;
        }
    }

    record Noise(long seed, double frequency, double threshold, double gain,
                 double peak, int animPeriod) implements InflowSource {

        @Override
        public double capAt(final int x, final int y, final int tick, final int worldWidth) {
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

    record Ring(double fx, double fy, double wavelength, double speed,
                double peak, double width) implements InflowSource {

        @Override
        public double capAt(final int x, final int y, final int tick, final int worldWidth) {
            final double dx = wrapDelta(x - fx * worldWidth, worldWidth);
            final double dy = wrapDelta(y - fy * worldWidth, worldWidth);
            final double d = Math.sqrt(dx * dx + dy * dy);
            final double phase = d - speed * tick;
            final double r = ((phase % wavelength) + wavelength) % wavelength;
            final double distToCrest = Math.min(r, wavelength - r);
            final double ridge = 1.0 - distToCrest / width;
            return ridge <= 0.0 ? 0.0 : peak * ridge;
        }

        @Override
        public boolean isStatic() {
            return speed == 0.0;
        }
    }

    static double pulse(final double peak, final int tick, final int periodTicks, final int phaseTicks) {
        return peak * triangle(tick, periodTicks, phaseTicks);
    }

    // Phase 0 starts at the trough; periodTicks == 0 is steady (always 1).
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

    static boolean inBand(final int coord, final double leftEdge, final double width, final int worldWidth) {
        if (width >= worldWidth) {
            return true;
        }
        final double fwd = (((coord - leftEdge) % worldWidth) + worldWidth) % worldWidth;
        return fwd <= width;
    }

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
