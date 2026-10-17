package com.simolution.sim;

public sealed interface MapSampling {

    MapSampling OFF = new Off();
    MapSampling EVERY_TICK = new EveryTick();

    default boolean enabled() {
        return true;
    }

    default int sampleEvery(final int totalTicks) {
        return 1;
    }

    default boolean samples(final int tick, final int totalTicks) {
        return tick % sampleEvery(totalTicks) == 0;
    }

    record Off() implements MapSampling {

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public boolean samples(final int tick, final int totalTicks) {
            return false;
        }
    }

    record EveryTick() implements MapSampling {
    }

    record Spread(int frames) implements MapSampling {

        @Override
        public int sampleEvery(final int totalTicks) {
            return Math.max(1, totalTicks / frames);
        }
    }

    record Window(int fromTick, int toTick) implements MapSampling {

        @Override
        public boolean samples(final int tick, final int totalTicks) {
            return tick >= fromTick && tick <= toTick;
        }
    }
}
