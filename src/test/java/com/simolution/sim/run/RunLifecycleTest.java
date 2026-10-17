package com.simolution.sim.run;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.CheckpointBytes;
import com.simolution.support.Worlds;

class RunLifecycleTest {

    private static final int WORLD = 32;
    private static final int FOUNDERS = 60;
    private static final int GENES = 16;
    private static final int TICKS = 300;
    private static final int EVERY = 50;

    private static Kernel newKernel(final EngineConfig config) {
        return Worlds.randomKernel(7L, FOUNDERS, GENES, config);
    }

    private static void assertSeekMatchesUninterrupted(final EngineConfig config, final Path dir) throws IOException {
        final Kernel recorded = newKernel(config);
        final RunDirectoryWriter run = RunDirectoryWriter.create(dir, recorded, EVERY);
        final List<byte[]> canonical = new ArrayList<>();
        canonical.add(CheckpointBytes.of(recorded));
        for (int t = 1; t <= TICKS; t++) {
            recorded.tick();
            run.checkpointIfDue(recorded);
            canonical.add(CheckpointBytes.of(recorded));
        }

        for (int t = 0; t <= TICKS; t++) {
            assertArrayEquals(canonical.get(t), CheckpointBytes.of(RunDirectoryReader.seek(dir, t, 1)), "state at tick " + t);
        }
    }

    @Test
    void seekMatchesUninterruptedAtEveryTick(@TempDir final Path dir) throws IOException {
        assertSeekMatchesUninterrupted(EngineConfig.defaults(7L, WORLD, GENES * 2), dir);
    }

    @Test
    void cyclicInflowRunCanBeSought(@TempDir final Path dir) throws IOException {
        final EngineConfig cyclic = EngineConfig.defaults(7L, WORLD, GENES * 2)
                .withResourceInflow(InflowConfig.cyclic(200, 10, 10));

        assertSeekMatchesUninterrupted(cyclic, dir);
    }

    @Test
    void checkpointsLandEveryNTicksAndRunJsonNamesTheRun(@TempDir final Path dir) throws IOException {
        final Kernel kernel = newKernel(EngineConfig.defaults(7L, WORLD, GENES * 2));
        final RunDirectoryWriter run = RunDirectoryWriter.create(dir, kernel, EVERY);
        for (int t = 1; t <= 120; t++) {
            kernel.tick();
            run.checkpointIfDue(kernel);
        }

        assertTrue(Files.exists(dir.resolve("ckpt/0.ckpt")));
        assertTrue(Files.exists(dir.resolve("ckpt/50.ckpt")));
        assertTrue(Files.exists(dir.resolve("ckpt/100.ckpt")));
        assertTrue(Files.notExists(dir.resolve("ckpt/120.ckpt")));
        final JsonNode json = new ObjectMapper().readTree(Files.readString(dir.resolve("run.json")));
        assertEquals(run.runId(), json.get("runId").asText());
        assertEquals(64, run.runId().length());
        assertEquals(EngineVersion.ID, json.get("engine").asText());
        assertTrue(json.get("parent").isNull());
    }

    @Test
    void branchRecordsItsParent(@TempDir final Path parentDir, @TempDir final Path branchDir) throws IOException {
        final Kernel kernel = newKernel(EngineConfig.defaults(7L, WORLD, GENES * 2));
        final RunDirectoryWriter parent = RunDirectoryWriter.create(parentDir, kernel, EVERY);
        for (int t = 1; t <= 100; t++) {
            kernel.tick();
            parent.checkpointIfDue(kernel);
        }

        final Kernel atParent = RunDirectoryReader.seek(parentDir, 100, 1);
        final RunDirectoryWriter branch = RunDirectoryWriter.createBranch(branchDir, atParent, EVERY, parent.runId(), 100);

        final JsonNode json = new ObjectMapper().readTree(Files.readString(branchDir.resolve("run.json")));
        assertEquals(parent.runId(), json.get("parent").get("runId").asText());
        assertEquals(100, json.get("parent").get("tick").asInt());
        assertTrue(Files.exists(branchDir.resolve("ckpt/100.ckpt")));
        assertNotNull(branch.runId());
        assertEquals(100, RunDirectoryReader.seek(branchDir, 100, 1).currentTick());
    }

    @Test
    void latestFrameKeepsTheMostRecentPublish() {
        final Kernel kernel = newKernel(EngineConfig.defaults(7L, WORLD, GENES * 2));
        final LatestFrame frames = new LatestFrame(kernel.config());
        assertEquals(null, frames.latest());

        kernel.step(5);
        frames.publish(kernel);
        final LatestFrame.Frame first = frames.latest();
        assertEquals(5, first.tick());
        assertArrayEquals(kernel.state().cellOccupant, first.cellOccupant());
        assertArrayEquals(kernel.state().cellChem, first.chemicals());

        kernel.step(5);
        frames.publish(kernel);
        final LatestFrame.Frame second = frames.latest();
        assertEquals(10, second.tick());
        assertArrayEquals(kernel.state().cellChem, second.chemicals());
        assertEquals(5, first.tick());
    }
}
