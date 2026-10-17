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
        RunConfig cfg = RunConfig.parse(new String[] {
                "--units", "100", "--world", "100", "--inflow", "noise seed=1 peak=5"
        });
        assertEquals(1, cfg.engine().chemicals().get(0).inflow().sources().size());
        RunConfig uniform = RunConfig.parse(new String[] { "--units", "100", "--world", "100" });
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
        RunConfig config = RunConfig.parse(new String[] {"--units", "4", "--map-frames", "5",
                "--map-from", "3", "--map-to", "9"});

        // assert
        assertEquals(new MapSampling.Window(3, 9), config.mapSampling());
    }

    @Test
    void negativeMapFromFallsBackToMapFrames() {
        // act
        RunConfig config = RunConfig.parse(new String[] {"--units", "4", "--map-frames", "5",
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
    void demoKeepsMapFramesButIgnoresTheWindow() {
        // act
        RunConfig config = RunConfig.parse(new String[] {"--map-frames", "4", "--map-from", "1", "--map-to", "2"});

        // assert
        assertEquals(new MapSampling.Spread(4), config.mapSampling());
        assertEquals(new GenomeSource.Demo(), config.genomes());
    }

    @Test
    void digestEveryTickDefaultsOff() {
        assertEquals(false, RunConfig.parse(new String[] {"--units", "10"}).digestEveryTick());
        assertEquals(true, RunConfig.parse(new String[] {"--units", "10", "--digest-every-tick"}).digestEveryTick());
    }
}
