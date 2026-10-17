package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.run.RunDirectory;

/**
 * Conformance for the observability layer: replay is exact and the sink never
 * perturbs the run. These are the guarantees the spec calls out as must-hold.
 */
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
        return Kernel.scatterFounders(UNITS, WORLD);
    }

    private static final EngineConfig ENGINE = EngineConfig.v5Defaults(0L, WORLD, MAX_GENES);

    private static Kernel freshKernel() {
        return new Kernel(genomes(), founderCells(), ENGINE, 1);
    }

    // replay from a checkpoint reproduces the canonical history exactly:
    // seekTo(T) state == ticking a fresh kernel from 0 to T, bit for bit.
    @Test
    void replaySeekMatchesCanonicalAtEveryTick(@TempDir Path obsDir) throws IOException {
        recordRun(obsDir);
        for (int t : new int[] {0, 1, 137, 200, 400, 401, 600}) {
            Kernel canonical = freshKernel();
            for (int i = 0; i < t; i++) {
                canonical.tick();
            }
            assertSnapshotsEqual(canonical.snapshot(), RunDirectory.seek(obsDir, t, 1).snapshot(), t);
        }
    }

    // the observability sink only reads snapshots; attaching the writers must
    // not perturb the kernel's history (the live arrays handed out must not be
    // mutated). Digest with and without the writers attached is identical.
    @Test
    void attachingSinkLeavesRunByteIdentical() throws IOException {
        long bare = digestOfRun(false);
        long observed = digestOfRun(true);
        assertEquals(bare, observed, "observability sink changed the run");
    }

    private void recordRun(Path obsDir) throws IOException {
        Kernel kernel = freshKernel();
        RunDirectory run = RunDirectory.create(obsDir, kernel, CHECKPOINT_EVERY);
        for (int i = 0; i < TICKS; i++) {
            kernel.tick();
            run.afterTick(kernel);
        }
    }

    private long digestOfRun(boolean observe) throws IOException {
        Kernel kernel = freshKernel();
        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        if (observe) {
            eventLog = new EventLogWriter(new BufferedWriter(new StringWriter()),
                    WORLD * WORLD, UNITS, founderCells(), ENGINE);
            metrics = new MetricsWriter(new BufferedWriter(new StringWriter()), UNITS, WORLD * WORLD);
        }
        long digest = 0L;
        for (int i = 0; i < TICKS; i++) {
            kernel.tick();
            KernelSnapshot s = kernel.snapshot();
            if (observe) {
                eventLog.observe(s);
                metrics.sample(s);
            }
            digest = fold(digest, s);
        }
        if (observe) {
            eventLog.close();
            metrics.close();
        }
        return digest;
    }

    private static long fold(long h, KernelSnapshot s) {
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

    private static void assertSnapshotsEqual(KernelSnapshot expected, KernelSnapshot actual, int tick) {
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
