package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NoiseTest {

    @Test
    void mutationUniformMatchesIndependentReference() {
        // arrange
        final long seed = 42L;

        for (int i = 0; i < 1_000; i++) {
            final int slot = i * 3;
            final int tick = i * 5;
            final int index = i * 11;
            final long stream = Noise.mix(seed + Noise.GOLDEN_GAMMA * Noise.STREAM_MUTATION);
            long h = Noise.mix(stream + Noise.GOLDEN_GAMMA * (slot + 1L));
            h = Noise.mix(h + Noise.GOLDEN_GAMMA * (tick + 1L));
            h = Noise.mix(h + Noise.GOLDEN_GAMMA * (index + 1L));
            final double expected = (h >>> 11) * 0x1.0p-53;

            // act
            final double actual = Noise.mutationUniformFromPrefix(Noise.mutationPrefix(seed, slot, tick), index);

            // assert
            assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
        }
    }
}
