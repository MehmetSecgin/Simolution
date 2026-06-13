package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Conformance for the report-v8 observability layer: replay is exact, the
 * sink never perturbs the run, and the manifest round-trips. These are the
 * guarantees the spec calls out as must-hold.
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

    private static Kernel freshKernel() {
        return new Kernel(genomes(), WORLD, MAX_GENES, founderCells());
    }

    // replay from a checkpoint reproduces the canonical history exactly:
    // seekTo(T) state == ticking a fresh kernel from 0 to T, bit for bit.
    @Test
    void replaySeekMatchesCanonicalAtEveryTick(@TempDir Path obsDir) throws IOException {
        recordRun(obsDir);
        Replayer replayer = Replayer.open(obsDir);

        for (int t : new int[] {0, 1, 137, 200, 400, 401, 600}) {
            Kernel canonical = freshKernel();
            for (int i = 0; i < t; i++) {
                canonical.tick();
            }
            assertSnapshotsEqual(canonical.snapshot(), replayer.seekTo(t), t);
        }
    }

    // the observability sink only reads snapshots; attaching the writers must
    // not perturb the kernel's history (the live arrays handed out must not be
    // mutated). Digest with and without the writers attached is identical.
    @Test
    void attachingSinkLeavesRunByteIdentical(@TempDir Path obsDir) throws IOException {
        long bare = digestOfRun(false, obsDir.resolve("a"));
        long observed = digestOfRun(true, obsDir.resolve("b"));
        assertEquals(bare, observed, "observability sink changed the run");
    }

    @Test
    void manifestRoundTrips() {
        RunManifest m = new RunManifest("v3", 7L, WORLD, 2,
                new int[] {3, 17}, new int[][] {{1, -2, 3}, {-4, 5}},
                7L, 3, MAX_GENES, TICKS, ConfigHash.compute(), CHECKPOINT_EVERY, 5);
        RunManifest parsed = RunManifest.parse(m.toJson());
        assertEquals(m.kernel(), parsed.kernel());
        assertEquals(m.seed(), parsed.seed());
        assertEquals(m.worldWidth(), parsed.worldWidth());
        assertEquals(m.founderCount(), parsed.founderCount());
        assertArrayEquals(m.founderCells(), parsed.founderCells());
        assertArrayEquals(m.founderGenomes()[0], parsed.founderGenomes()[0]);
        assertArrayEquals(m.founderGenomes()[1], parsed.founderGenomes()[1]);
        assertEquals(m.configHash(), parsed.configHash());
        assertEquals(m.checkpointEvery(), parsed.checkpointEvery());
        assertEquals(m.mapSampleEvery(), parsed.mapSampleEvery());
    }

    @Test
    void replayRefusesOnConfigHashMismatch(@TempDir Path obsDir) throws IOException {
        recordRun(obsDir);
        String json = Files.readString(obsDir.resolve("manifest.json"));
        String tampered = json.replaceAll("\"configHash\": \"[0-9a-f]+\"",
                "\"configHash\": \"deadbeef\"");
        Files.writeString(obsDir.resolve("manifest.json"), tampered);
        assertThrows(IllegalStateException.class, () -> Replayer.open(obsDir));
    }

    private void recordRun(Path obsDir) throws IOException {
        Files.createDirectories(obsDir);
        Kernel kernel = freshKernel();
        try (CheckpointWriter ckpt = new CheckpointWriter(obsDir.resolve("ckpt"), CHECKPOINT_EVERY)) {
            for (int i = 0; i < TICKS; i++) {
                kernel.tick();
                ckpt.maybeCheckpoint(kernel, kernel.snapshot());
            }
        }
        RunManifest manifest = new RunManifest("v3", SEED, WORLD, UNITS,
                founderCells(), genomes(), SEED, GENES, MAX_GENES, TICKS,
                ConfigHash.compute(), CHECKPOINT_EVERY, 0);
        Files.writeString(obsDir.resolve("manifest.json"), manifest.toJson());
    }

    private long digestOfRun(boolean observe, Path obsDir) throws IOException {
        Kernel kernel = freshKernel();
        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        if (observe) {
            Files.createDirectories(obsDir);
            eventLog = new EventLogWriter(new BufferedWriter(new StringWriter()),
                    WORLD * WORLD, UNITS, WORLD, founderCells());
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
