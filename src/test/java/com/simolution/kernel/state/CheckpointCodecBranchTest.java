package com.simolution.kernel.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.Harvest;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.CheckpointBytes;
import com.simolution.support.Worlds;

class CheckpointCodecBranchTest {

    private static final int WORLD = 32;
    private static final int GENES = 16;
    private static final int BRANCH_TICK = 250;
    private static final EngineConfig CONFIG = EngineConfig.defaults(7L, WORLD, GENES * 2);

    private static byte[] parentCheckpointAtBranchTick() throws IOException {
        try (Kernel parent = Worlds.randomKernel(7L, 60, GENES, CONFIG)) {
            parent.step(BRANCH_TICK);
            return CheckpointBytes.of(parent);
        }
    }

    private static Kernel branch(final byte[] checkpoint, final int threads, final EngineConfig config)
            throws IOException {
        return CheckpointCodec.readBranch(new ByteArrayInputStream(checkpoint), threads, config);
    }

    private static String refusal(final EngineConfig config) throws IOException {
        final byte[] checkpoint = parentCheckpointAtBranchTick();
        return assertThrows(IllegalArgumentException.class, () -> branch(checkpoint, 1, config)).getMessage();
    }

    @Test
    void branchWithUnchangedConfigReproducesTheParentAtAnyThreadCount() throws IOException {
        // arrange
        final byte[] checkpoint = parentCheckpointAtBranchTick();
        final long at300;
        final long at400;
        try (Kernel parent = Worlds.randomKernel(7L, 60, GENES, CONFIG)) {
            parent.step(300);
            at300 = parent.stateDigest();
            parent.step(100);
            at400 = parent.stateDigest();
        }

        for (int threads : new int[] {1, 4}) {
            try (Kernel branch = branch(checkpoint, threads, CONFIG)) {
                // act
                branch.step(50);
                final long branch300 = branch.stateDigest();
                branch.step(100);

                // assert
                assertEquals(at300, branch300, threads + " threads, tick 300");
                assertEquals(at400, branch.stateDigest(), threads + " threads, tick 400");
            }
        }
    }

    @Test
    void branchRefusesAWorldWidthChange() throws IOException {
        assertEquals("a branch cannot change worldWidth (32 -> 64)", refusal(CONFIG.withWorldWidth(64)));
    }

    @Test
    void branchRefusesAMaxGenesChange() throws IOException {
        assertEquals("a branch cannot change maxGenes (32 -> 48)", refusal(CONFIG.withMaxGenes(48)));
    }

    @Test
    void branchRefusesAChemicalCountChange() throws IOException {
        // arrange
        final ChemicalSpec first = CONFIG.chemicals().get(0);

        // act
        final String message = refusal(CONFIG.withChemicals(List.of(first, first)));

        // assert
        assertEquals("a branch cannot change chemical count (1 -> 2)", message);
    }

    @Test
    void branchWithDoubledHarvestDivergesWithinFiftyTicks() throws IOException {
        // arrange
        final byte[] checkpoint = parentCheckpointAtBranchTick();
        final EngineConfig doubled = new EngineConfig(CONFIG.seed(), CONFIG.world(), CONFIG.chemicals(),
                CONFIG.founder(), CONFIG.metabolism(), new Harvest(3.0, CONFIG.harvest().halfSaturation()),
                CONFIG.motility(), CONFIG.growth(), CONFIG.sensing(), CONFIG.reproduction(), CONFIG.mutation());

        try (Kernel same = branch(checkpoint, 1, CONFIG); Kernel changed = branch(checkpoint, 1, doubled)) {
            // act
            same.step(50);
            changed.step(50);

            // assert
            assertEquals(doubled, changed.config());
            assertNotEquals(same.stateDigest(), changed.stateDigest());
        }
    }

    @Test
    void branchKeepsTheCorruptCheckpointCheck() throws IOException {
        // arrange
        final byte[] checkpoint = parentCheckpointAtBranchTick();
        checkpoint[checkpoint.length - 1] ^= 1;

        // act
        final IllegalStateException refused =
                assertThrows(IllegalStateException.class, () -> branch(checkpoint, 1, CONFIG));

        // assert
        assertEquals("corrupt checkpoint: state digest mismatch", refused.getMessage());
    }

    @Test
    void readDigestReturnsTheHeaderDigest() throws IOException {
        // arrange
        try (Kernel kernel = Worlds.randomKernel(7L, 60, GENES, CONFIG)) {
            kernel.step(10);

            // act
            final long digest = CheckpointCodec.readDigest(new ByteArrayInputStream(CheckpointBytes.of(kernel)));

            // assert
            assertEquals(kernel.stateDigest(), digest);
        }
    }
}
