package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.FieldSpec.Persist;
import com.simolution.sim.GenomeFactory;

class CheckpointCodecTest {

    private static final int WORLD = 32;
    private static final int FOUNDERS = 60;
    private static final int GENES = 16;
    private static final EngineConfig CONFIG = EngineConfig.v5Defaults(7L, WORLD, GENES * 2);

    private static Kernel newKernel() {
        return new Kernel(GenomeFactory.random(7L, FOUNDERS, GENES), Kernel.scatterFounders(CONFIG.seed(), FOUNDERS, WORLD), CONFIG, 1);
    }

    private static byte[] write(final Kernel kernel) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CheckpointCodec.write(kernel, bytes);
        return bytes.toByteArray();
    }

    private static Kernel read(final byte[] bytes) throws IOException {
        return CheckpointCodec.read(new ByteArrayInputStream(bytes), 1);
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
        final byte[] version = EngineVersion.ID.getBytes(StandardCharsets.UTF_8);
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
        bytes[7] = 2;

        final IllegalStateException e = refused(bytes);

        assertTrue(e.getMessage().contains("format version"), e.getMessage());
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

    @Test
    void branchRefusesShapeChange() throws IOException {
        final byte[] bytes = write(newKernel());

        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CheckpointCodec.readBranch(new ByteArrayInputStream(bytes), CONFIG.withWorldWidth(40), 1));

        assertTrue(e.getMessage().contains("shape"), e.getMessage());
    }

    @Test
    void branchAllowsRateChange() throws IOException {
        final Kernel original = newKernel();
        original.step(100);
        final byte[] bytes = write(original);

        final Kernel branched = CheckpointCodec.readBranch(new ByteArrayInputStream(bytes), withBasalCost(0.6), 1);
        final Kernel unbranched = read(bytes);

        assertEquals(0.6, branched.config().basalCost());
        assertSavedStateEquals(original, unbranched);
        branched.step(100);
        unbranched.step(100);
        assertFalse(Arrays.equals(branched.state().energy, unbranched.state().energy),
                "a changed basal cost must change the trajectory");
    }

    private static EngineConfig withBasalCost(final double basalCost) {
        final EngineConfig base = CONFIG;
        return new EngineConfig(base.seed(), base.worldWidth(), base.maxGenes(), base.chemicals(),
                base.initialEnergy(), basalCost, base.costPerPropagation(), base.harvestCapacityPerConnection(),
                base.harvestHalfSaturation(), base.harvestMassExponent(), base.moveCostBase(),
                base.moveCostPerMass(), base.moveDeadzone(), base.selfEnergyScale(), base.selfMassScale(),
                base.initialMass(), base.maintPerMass(), base.growMax(), base.growHalfSaturation(),
                base.growYield(), base.buildCost(), base.buildCostPerGene(), base.mutationRateWeight(),
                base.mutationRateStruct(), base.indelRateDup(), base.indelRateDel(), base.agingCost());
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
