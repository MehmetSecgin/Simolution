package com.simolution.kernel.config;

import com.simolution.kernel.runtime.Noise;

public final class ValueNoise {

    private static final long X_PRIME = 73856093L;
    private static final long Y_PRIME = 19349663L;
    private static final long Z_PRIME = 83492791L;

    private ValueNoise() {}

    // Trig-free so the field is bit-identical across JVMs; the result lies in [0, 1).
    public static double at(final long seed, final double x, final double y, final double z) {
        final int x0 = fastFloor(x);
        final int y0 = fastFloor(y);
        final int z0 = fastFloor(z);

        final double u = fade(x - x0);
        final double v = fade(y - y0);
        final double w = fade(z - z0);

        final double y0z0 = alongX(seed, x0, y0, z0, u);
        final double y1z0 = alongX(seed, x0, y0 + 1, z0, u);
        final double y0z1 = alongX(seed, x0, y0, z0 + 1, u);
        final double y1z1 = alongX(seed, x0, y0 + 1, z0 + 1, u);

        return lerp(lerp(y0z0, y1z0, v), lerp(y0z1, y1z1, v), w);
    }

    private static double alongX(final long seed, final int x0, final int y, final int z, final double u) {
        return lerp(corner(seed, x0, y, z), corner(seed, x0 + 1, y, z), u);
    }

    private static double corner(final long seed, final int ix, final int iy, final int iz) {
        long h = Noise.mix(seed + Noise.GOLDEN_GAMMA * (ix * X_PRIME));
        h = Noise.mix(h + Noise.GOLDEN_GAMMA * (iy * Y_PRIME));
        h = Noise.mix(h + Noise.GOLDEN_GAMMA * (iz * Z_PRIME));
        return (h >>> 11) * 0x1.0p-53;
    }

    // Perlin's quintic 6t^5 - 15t^4 + 10t^3: continuous first and second derivatives.
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
