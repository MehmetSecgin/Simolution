package com.simolution.kernel.runtime;

public final class Noise {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Noise() {}

    public static double sample(long seed, int unitIndex, int tick) {
        long h = mix(seed + GOLDEN_GAMMA * (unitIndex + 1L));
        h = mix(h + GOLDEN_GAMMA * (tick + 1L));
        return (h >>> 11) * 0x1.0p-52 - 1.0;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
