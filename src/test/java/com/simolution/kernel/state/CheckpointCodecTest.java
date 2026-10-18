package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.FieldSpec.Persist;
import com.simolution.support.CheckpointBytes;
import com.simolution.support.Worlds;

class CheckpointCodecTest {

    private static final int WORLD = 32;
    private static final int FOUNDERS = 60;
    private static final int GENES = 16;
    private static final EngineConfig CONFIG = EngineConfig.defaults(7L, WORLD, GENES * 2);

    private static Kernel newKernel() {
        return Worlds.randomKernel(7L, FOUNDERS, GENES, CONFIG);
    }

    private static byte[] write(final Kernel kernel) throws IOException {
        return CheckpointBytes.of(kernel);
    }

    private static Kernel read(final byte[] bytes) throws IOException {
        return CheckpointBytes.restore(bytes, 1);
    }

    private static IllegalStateException refused(final byte[] bytes) {
        return assertThrows(IllegalStateException.class, () -> read(bytes));
    }

    private static void assertSavedStateEquals(final Kernel expected, final Kernel actual) {
        final WorldState a = expected.state();
        final WorldState b = actual.state();
        assertEquals(expected.config(), actual.config());
        assertEquals(a.tick, b.tick, "tick");
        assertEquals(a.energySink, b.energySink, "energySink");
        assertEquals(a.cumulativeInflow, b.cumulativeInflow, "cumulativeInflow");
        assertEquals(a.birthsTotal, b.birthsTotal, "birthsTotal");
        assertEquals(a.maxGeneration, b.maxGeneration, "maxGeneration");
        assertEquals(a.creditedInitialEnergy, b.creditedInitialEnergy, "creditedInitialEnergy");
        assertEquals(a.creditedInitialMass, b.creditedInitialMass, "creditedInitialMass");
        assertEquals(a.initialResourceTotal, b.initialResourceTotal, "initialResourceTotal");
        for (int i = 0; i < a.fields().size(); i++) {
            final FieldSpec left = a.fields().get(i);
            final FieldSpec right = b.fields().get(i);
            if (left.persist() != Persist.SAVED) {
                continue;
            }
            switch (left.array()) {
                case int[] x -> assertArrayEquals(x, (int[]) right.array(), left.name());
                case long[] x -> assertArrayEquals(x, (long[]) right.array(), left.name());
                case float[] x -> assertArrayEquals(x, (float[]) right.array(), left.name());
                case double[] x -> assertArrayEquals(x, (double[]) right.array(), left.name());
                default -> throw new AssertionError("unexpected saved type for " + left.name());
            }
        }
    }

    @Test
    void roundTripIsExact() throws IOException {
        final Kernel original = newKernel();
        original.step(300);
        assertTrue(original.state().birthsTotal > 0, "the world must have reproduced for the test to bite");

        final Kernel restored = read(write(original));
        assertSavedStateEquals(original, restored);

        original.step(200);
        restored.step(200);
        assertSavedStateEquals(original, restored);
    }

    @Test
    void refusesTruncated() throws IOException {
        final byte[] bytes = write(newKernel());

        final IllegalStateException e = refused(Arrays.copyOf(bytes, bytes.length - 10));

        assertTrue(e.getMessage().contains("truncated"), e.getMessage());
    }

    @Test
    void refusesOtherEngineVersion() throws IOException {
        final byte[] bytes = write(newKernel());
        final byte[] version = Kernel.ENGINE_VERSION.getBytes(StandardCharsets.UTF_8);
        final int at = indexOf(bytes, version);
        bytes[at + version.length - 1] = (byte) '9';

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("engine version"), e.getMessage());
    }

    @Test
    void refusesBadMagic() throws IOException {
        final byte[] bytes = write(newKernel());
        bytes[0] = 'X';

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("magic"), e.getMessage());
    }

    @Test
    void refusesOtherFormatVersion() throws IOException {
        final byte[] bytes = write(newKernel());
        bytes[7] = 1;

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("format version"), e.getMessage());
    }

    @Test
    void refusesCorruptedState() throws IOException {
        final byte[] bytes = write(newKernel());
        bytes[bytes.length - 1] ^= 1;

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("digest"), e.getMessage());
    }

    @Test
    void refusesOtherLayout() throws IOException {
        final byte[] bytes = write(newKernel());
        final byte[] marker = "\"offsets\"".getBytes(StandardCharsets.UTF_8);
        final int at = indexOf(bytes, marker);
        bytes[at + 1] = 'S';

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("layout"), e.getMessage());
    }

    private static int indexOf(final byte[] haystack, final byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError("marker not found in checkpoint bytes");
    }
}
