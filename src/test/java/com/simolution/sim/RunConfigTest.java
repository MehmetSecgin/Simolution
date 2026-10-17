package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.InflowConfig;

class RunConfigTest {

    @Test
    void resourceCycleAndInflowAreMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class, () -> RunConfig.parse(new String[] {
                "--units", "100", "--world", "100", "--resource-cycle", "--inflow", "disk r=10"
        }));
    }

    @Test
    void inflowFlagBuildsNonUniformConfig() {
        final RunConfig cfg = RunConfig.parse(new String[] {
                "--units", "100", "--world", "100", "--inflow", "noise seed=1 peak=5"
        });
        assertEquals(1, cfg.engine().chemicals().get(0).inflow().sources().size());
        final RunConfig uniform = RunConfig.parse(new String[] {"--units", "100", "--world", "100"});
        assertEquals(InflowConfig.uniform(1.0), uniform.engine().chemicals().get(0).inflow());
    }

    @Test
    void mapFramesDefaultsOff() {
        assertEquals(MapSampling.OFF, RunConfig.parse(new String[] {"--out", "x.txt"}).mapSampling());
        assertEquals(MapSampling.EVERY_TICK,
                RunConfig.parse(new String[] {"--out", "x.txt", "--map-frames", "0"}).mapSampling());
    }

    @Test
    void mapWindowWinsOverMapFrames() {
        // act
        final RunConfig config = RunConfig.parse(new String[] {"--units", "4", "--map-frames", "5",
                "--map-from", "3", "--map-to", "9"});

        // assert
        assertEquals(new MapSampling.Window(3, 9), config.mapSampling());
    }

    @Test
    void negativeMapFromFallsBackToMapFrames() {
        // act
        final RunConfig config = RunConfig.parse(new String[] {"--units", "4", "--map-frames", "5",
                "--map-from", "-2", "--map-to", "9"});

        // assert
        assertEquals(new MapSampling.Spread(5), config.mapSampling());
    }

    @Test
    void spreadSamplesEveryTicksOverFramesAtLeastOne() {
        // assert
        assertEquals(2, new MapSampling.Spread(5).sampleEvery(10));
        assertEquals(1, new MapSampling.Spread(50).sampleEvery(10));
        assertEquals(1, MapSampling.EVERY_TICK.sampleEvery(10));
        assertEquals(1, new MapSampling.Window(3, 9).sampleEvery(10));
    }

    @Test
    void noArgumentsRunOneFounderForTheDefaultTicks() {
        // act
        final RunConfig config = RunConfig.parse(new String[0]);

        // assert
        assertEquals(1, config.founderCount());
        assertEquals(RunConfig.DEFAULT_TICKS, config.ticks());
        assertEquals(RunConfig.DEFAULT_GENES, config.genesPerFounder());
    }

    @Test
    void digestEveryTickDefaultsOff() {
        assertEquals(false, RunConfig.parse(new String[] {"--units", "10"}).digestEveryTick());
        assertEquals(true, RunConfig.parse(new String[] {"--units", "10", "--digest-every-tick"}).digestEveryTick());
    }
}
