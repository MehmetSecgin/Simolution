package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.CheckpointBytes;
import com.simolution.support.Worlds;

class DeterminismMatrixTest {

    private static final long SEED = 7L;
    private static final int GENES = 32;
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};

    private static Kernel kernel(final EngineConfig config, final int founders, final int threads) {
        return Worlds.randomKernel(SEED, founders, GENES, config, threads);
    }

    private static EngineConfig config(final int world) {
        return EngineConfig.defaults(SEED, world, GENES * 2);
    }

    private static long[] perTickDigests(final EngineConfig config, final int founders, final int ticks,
            final int threads) {
        final long[] digests = new long[ticks];
        try (Kernel k = kernel(config, founders, threads)) {
            for (int t = 0; t < ticks; t++) {
                k.tick();
                digests[t] = k.stateDigest();
            }
        }
        return digests;
    }

    private static long population(final Kernel k) {
        long alive = 0;
        for (final double e : k.state().energy) {
            if (e > 0.0) {
                alive++;
            }
        }
        return alive;
    }

    @ParameterizedTest(name = "W={0}, founders={1}")
    @CsvSource({"64, 300", "128, 1200"})
    void identicalAcrossThreadCounts(final int world, final int founders) {
        // arrange
        final EngineConfig config = config(world);
        final long[] expected = perTickDigests(config, founders, 400, 1);

        for (final int threads : THREAD_COUNTS) {
            // act
            final long[] actual = perTickDigests(config, founders, 400, threads);

            // assert
            assertArrayEquals(expected, actual, "threads " + threads);
        }
    }

    @ParameterizedTest(name = "W={0}, founders={1}")
    @CsvSource({"64, 300", "128, 1200"})
    void resumeAcrossThreadCounts(final int world, final int founders) throws IOException {
        // arrange
        final EngineConfig config = config(world);
        final long uninterrupted;
        try (Kernel k = kernel(config, founders, 1)) {
            k.step(400);
            uninterrupted = k.stateDigest();
        }
        final byte[] checkpoint;
        try (Kernel first = kernel(config, founders, 2)) {
            first.step(150);
            checkpoint = CheckpointBytes.of(first);
        }

        // act
        final long resumed;
        try (Kernel second = CheckpointBytes.restore(checkpoint, 4)) {
            second.step(250);
            resumed = second.stateDigest();
        }

        // assert
        assertEquals(uninterrupted, resumed);
    }

    @ParameterizedTest(name = "W={0}, founders={1}, inflow={2}")
    @CsvSource({"1, 1, true", "3, 9, true", "128, 2000, false"})
    void parallelMatchesSerialInDegenerateWorlds(final int world, final int founders, final boolean fed) {
        // arrange
        final EngineConfig base = config(world);
        final EngineConfig config = fed ? base : base.withResourceInflow(InflowConfig.uniform(0.0));

        // act
        final long[] serial = perTickDigests(config, founders, 2000, 1);
        final long[] parallel = perTickDigests(config, founders, 2000, 8);

        // assert
        assertArrayEquals(serial, parallel);
        if (!fed) {
            try (Kernel k = kernel(config, founders, 8)) {
                k.step(2000);
                assertEquals(0, population(k), "starved run must go extinct");
            }
        }
    }
}
