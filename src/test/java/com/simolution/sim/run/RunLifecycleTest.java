package com.simolution.sim.run;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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

    private static String sha256Hex(final byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
    void checkpointsLandEveryNTicksAndRunJsonNamesTheRun(@TempDir final Path dir)
            throws IOException, NoSuchAlgorithmException {
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
        assertEquals(sha256Hex(Files.readAllBytes(dir.resolve("ckpt/0.ckpt"))), json.get("runId").asText());
        assertEquals(EngineVersion.ID, json.get("engine").asText());
        assertTrue(json.get("parent").isNull());
    }
}
