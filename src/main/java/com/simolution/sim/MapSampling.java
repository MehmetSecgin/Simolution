package com.simolution.sim;

public record MapSampling(
        int frames,
        int fromTick,
        int toTick
) {

    public static final MapSampling OFF = new MapSampling(0, 1, 0);
    public static final MapSampling EVERY_TICK = new MapSampling(0, 0, Integer.MAX_VALUE);

    public static MapSampling spread(final int frames) {
        return new MapSampling(frames, 0, Integer.MAX_VALUE);
    }

    public static MapSampling window(final int fromTick, final int toTick) {
        return new MapSampling(0, fromTick, toTick);
    }

    public boolean enabled() {
        return fromTick <= toTick;
    }

    public int sampleEvery(final int totalTicks) {
        return frames == 0 ? 1 : Math.max(1, totalTicks / frames);
    }

    boolean samples(final int tick, final int every) {
        return tick >= fromTick && tick <= toTick && tick % every == 0;
    }
}
