package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.phase.ParallelRunner;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.Worlds;

class StateDigestTest {

    private static final long SEED = 7L;
    private static final int WORLD = 128;
    private static final int FOUNDERS = 1500;
    private static final int GENES = 16;

    private static Kernel kernel(final int threads) {
        final EngineConfig config = EngineConfig.defaults(SEED, WORLD, GENES * 2);
        return Worlds.randomKernel(SEED, FOUNDERS, GENES, config, threads);
    }

    private static long finalDigest(final int threads) {
        try (Kernel k = kernel(threads)) {
            k.step(200);
            return k.stateDigest();
        }
    }

    @Test
    void digestIndependentOfThreads() {
        // act
        final long serial = finalDigest(1);
        final long parallel = finalDigest(8);

        // assert
        assertEquals(serial, parallel);
    }

    @Test
    void digestFromAnyRunnerMatchesTheKernelsOwn() {
        try (Kernel k = kernel(1); ParallelRunner other = new ParallelRunner(4)) {
            // act
            k.step(50);

            // assert
            assertEquals(k.stateDigest(), new StateDigest(k.state()).compute(other));
        }
    }

    @Test
    void digestDetectsOneBitFlip() {
        try (Kernel k = kernel(2)) {
            // arrange
            k.step(50);
            final long before = k.stateDigest();
            final double[] energy = k.state().energy;
            final long bits = Double.doubleToRawLongBits(energy[3]);

            // act
            energy[3] = Double.longBitsToDouble(bits ^ 1L);
            final long flipped = k.stateDigest();
            energy[3] = Double.longBitsToDouble(bits);

            // assert
            assertNotEquals(before, flipped);
            assertEquals(before, k.stateDigest());
        }
    }

    @Test
    void digestDetectsAChangeInTheLastChunk() {
        try (Kernel k = kernel(2)) {
            // arrange
            k.step(10);
            final long before = k.stateDigest();
            final int[] occupant = k.state().cellOccupant;

            // act
            occupant[occupant.length - 1] ^= 1;

            // assert
            assertNotEquals(before, k.stateDigest());
        }
    }

    @Test
    void digestFollowsTheSeed() {
        // arrange
        final EngineConfig other = EngineConfig.defaults(SEED + 1, WORLD, GENES * 2);

        // act
        final long first = finalDigest(1);
        final long second;
        try (Kernel k = Worlds.randomKernel(SEED + 1, FOUNDERS, GENES, other)) {
            k.step(200);
            second = k.stateDigest();
        }

        // assert
        assertNotEquals(first, second);
    }
}
