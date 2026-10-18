package com.simolution.sim;

class FrameSampler {

    private final int every;
    private final int fromTick;
    private final int toTick;

    private FrameSampler(final int every, final int fromTick, final int toTick) {
        this.every = every;
        this.fromTick = fromTick;
        this.toTick = toTick;
    }

    static FrameSampler of(final MapSampling sampling, final int totalTicks) {
        return switch (sampling) {
            case MapSampling.Off _ -> new FrameSampler(1, 1, 0);
            case MapSampling.EveryTick _ -> new FrameSampler(1, 0, Integer.MAX_VALUE);
            case MapSampling.Spread spread -> new FrameSampler(spread.sampleEvery(totalTicks), 0, Integer.MAX_VALUE);
            case MapSampling.Window window -> new FrameSampler(1, window.fromTick(), window.toTick());
        };
    }

    boolean samples(final int tick) {
        return tick >= fromTick && tick <= toTick && tick % every == 0;
    }
}
