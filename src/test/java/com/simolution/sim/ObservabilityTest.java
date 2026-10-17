package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.run.RunDirectoryReader;
import com.simolution.sim.run.RunDirectoryWriter;

class ObservabilityTest {

    private static final long SEED = 42L;
    private static final int UNITS = 30;
    private static final int WORLD = 20;
    private static final int MAX_GENES = 16;
    private static final int GENES = 16;
    private static final int TICKS = 600;
    private static final int CHECKPOINT_EVERY = 200;

    private static int[][] genomes() {
        return GenomeFactory.random(SEED, UNITS, GENES);
    }

    private static int[] founderCells() {
        return Kernel.scatterFounders(SEED, UNITS, WORLD);
    }

    private static final EngineConfig ENGINE = EngineConfig.defaults(0L, WORLD, MAX_GENES);

    private static Kernel freshKernel() {
        return new Kernel(genomes(), founderCells(), ENGINE, 1);
    }

    @Test
    @DisplayName("seek to tick T equals ticking a fresh kernel from 0 to T, bit for bit")
    void replaySeekMatchesCanonicalAtEveryTick(final @TempDir Path obsDir) throws IOException {
        // arrange
        recordRun(obsDir);
        for (int t : new int[] {0, 1, 137, 200, 400, 401, 600}) {
            final Kernel canonical = freshKernel();
            for (int i = 0; i < t; i++) {
                canonical.tick();
            }

            // act
            final Kernel seeked = RunDirectoryReader.seek(obsDir, t, 1);

            // assert
            assertSnapshotsEqual(canonical.snapshot(), seeked.snapshot(), t);
        }
    }

    @Test
    @DisplayName("attaching the event and metrics writers leaves the run's history unchanged")
    void attachingSinkLeavesRunByteIdentical() throws IOException {
        // act
        final long bare = digestOfRun(false);
        final long observed = digestOfRun(true);

        // assert
        assertEquals(bare, observed, "observability sink changed the run");
    }

    private void recordRun(final Path obsDir) throws IOException {
        final Kernel kernel = freshKernel();
        final RunDirectoryWriter run = RunDirectoryWriter.create(obsDir, kernel, CHECKPOINT_EVERY);
        for (int i = 0; i < TICKS; i++) {
            kernel.tick();
            run.checkpointIfDue(kernel);
        }
    }

    private long digestOfRun(final boolean observe) throws IOException {
        final Kernel kernel = freshKernel();
        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        if (observe) {
            eventLog = new EventLogWriter(new BufferedWriter(new StringWriter()),
                    WORLD * WORLD, UNITS, founderCells(), ENGINE.founder().initialEnergy());
            metrics = new MetricsWriter(new BufferedWriter(new StringWriter()), UNITS);
        }
        long digest = 0L;
        for (int i = 0; i < TICKS; i++) {
            kernel.tick();
            final KernelSnapshot s = kernel.snapshot();
            if (observe) {
                eventLog.observe(s);
                metrics.observe(s);
            }
            digest = fold(digest, s);
        }
        if (observe) {
            eventLog.close();
            metrics.close();
        }
        return digest;
    }

    @SuppressWarnings("checkstyle:FinalParameters")
    private static long fold(long h, final KernelSnapshot s) {
        for (double v : s.outputs) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (double v : s.energy) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (double v : s.resourceField) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (int p : s.position) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ p);
        }
        return h;
    }

    private static void assertSnapshotsEqual(final KernelSnapshot expected, final KernelSnapshot actual,
            final int tick) {
        assertEquals(expected.tick, actual.tick, "tick");
        assertArrayEquals(expected.energy, actual.energy, "energy at tick " + tick);
        assertArrayEquals(expected.damage, actual.damage, "damage at tick " + tick);
        assertArrayEquals(expected.position, actual.position, "position at tick " + tick);
        assertArrayEquals(expected.resourceField, actual.resourceField, "resourceField at tick " + tick);
        assertArrayEquals(expected.outputs, actual.outputs, "outputs at tick " + tick);
        assertArrayEquals(expected.delayMemory, actual.delayMemory, "delayMemory at tick " + tick);
        assertArrayEquals(expected.lineageId, actual.lineageId, "lineageId at tick " + tick);
        assertArrayEquals(expected.generation, actual.generation, "generation at tick " + tick);
        assertEquals(expected.energySink, actual.energySink, "energySink at tick " + tick);
        assertEquals(expected.cumulativeInflow, actual.cumulativeInflow, "inflow at tick " + tick);
        assertEquals(expected.birthsTotal, actual.birthsTotal, "births at tick " + tick);
        assertEquals(expected.maxGeneration, actual.maxGeneration, "maxGen at tick " + tick);
    }
}
