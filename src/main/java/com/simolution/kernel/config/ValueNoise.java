package com.simolution.kernel.config;

import com.simolution.kernel.runtime.Noise;

/**
 * Deterministic, trig-free <b>value noise</b> for the {@link InflowSource.NoiseField}
 * source (contract v7). Coherent pseudo-random scalar field in {@code [0, 1)}:
 * integer-lattice corner hashes (the {@link Noise#mix} SplitMix64 finalizer reused
 * verbatim) interpolated with a quintic fade. No transcendental functions, so the
 * result is bit-identical across JVMs/platforms — the determinism invariant holds
 * for the whole field, not just the RNG.
 * <p>
 * Sampling is 3D: {@code (x, y)} are world coordinates scaled by frequency, and
 * {@code z} is a slow time axis (a noise field that morphs over a tick period when
 * animated, frozen at {@code z = 0} when static). Trilinear interpolation of the 8
 * surrounding lattice corners.
 */
public final class ValueNoise {

    private ValueNoise() {}

    /**
     * Coherent value noise in {@code [0, 1)} at the real point {@code (x, y, z)},
     * seeded by {@code seed}. Lattice corners are hashed with three large odd
     * per-axis multipliers so the three dimensions decorrelate; the quintic fade
     * {@code 6t^5 - 15t^4 + 10t^3} (Perlin's improved curve) gives continuous
     * first and second derivatives, so thresholded regions have smooth borders.
     */
    public static double at(final long seed, final double x, final double y, final double z) {
        final int x0 = fastFloor(x);
        final int y0 = fastFloor(y);
        final int z0 = fastFloor(z);

        final double u = fade(x - x0);
        final double v = fade(y - y0);
        final double w = fade(z - z0);

        final double c000 = corner(seed, x0, y0, z0);
        final double c100 = corner(seed, x0 + 1, y0, z0);
        final double c010 = corner(seed, x0, y0 + 1, z0);
        final double c110 = corner(seed, x0 + 1, y0 + 1, z0);
        final double c001 = corner(seed, x0, y0, z0 + 1);
        final double c101 = corner(seed, x0 + 1, y0, z0 + 1);
        final double c011 = corner(seed, x0, y0 + 1, z0 + 1);
        final double c111 = corner(seed, x0 + 1, y0 + 1, z0 + 1);

        final double x00 = lerp(c000, c100, u);
        final double x10 = lerp(c010, c110, u);
        final double x01 = lerp(c001, c101, u);
        final double x11 = lerp(c011, c111, u);

        final double y0z = lerp(x00, x10, v);
        final double y1z = lerp(x01, x11, v);

        return lerp(y0z, y1z, w);
    }

    private static double corner(final long seed, final int ix, final int iy, final int iz) {
        long h = Noise.mix(seed + Noise.GOLDEN_GAMMA * (ix * 73856093L));
        h = Noise.mix(h + Noise.GOLDEN_GAMMA * (iy * 19349663L));
        h = Noise.mix(h + Noise.GOLDEN_GAMMA * (iz * 83492791L));
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double fade(final double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(final double a, final double b, final double t) {
        return a + t * (b - a);
    }

    private static int fastFloor(final double v) {
        final int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
