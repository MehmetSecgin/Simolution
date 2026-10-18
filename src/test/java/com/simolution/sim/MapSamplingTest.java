package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MapSamplingTest {

    private static List<Integer> sampledTicks(final MapSampling sampling, final int totalTicks) {
        final int every = sampling.sampleEvery(totalTicks);
        final List<Integer> ticks = new ArrayList<>();
        for (int tick = 1; tick <= totalTicks; tick++) {
            if (sampling.samples(tick, every)) {
                ticks.add(tick);
            }
        }
        return ticks;
    }

    @Test
    void matchesTodaysSchedule() {
        // arrange
        final int totalTicks = 10;

        // act
        final List<Integer> spread = sampledTicks(MapSampling.spread(5), totalTicks);
        final List<Integer> window = sampledTicks(MapSampling.window(3, 5), totalTicks);
        final List<Integer> everyTick = sampledTicks(MapSampling.EVERY_TICK, totalTicks);
        final List<Integer> off = sampledTicks(MapSampling.OFF, totalTicks);

        // assert
        assertEquals(List.of(2, 4, 6, 8, 10), spread);
        assertEquals(List.of(3, 4, 5), window);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), everyTick);
        assertEquals(List.of(), off);
    }
}
