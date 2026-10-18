package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.Founder;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

class RunArtifactsTest {

    @TempDir
    Path dir;

    private static RunConfig config(final Path runDir, final String... extra) {
        final List<String> args = new ArrayList<>(List.of("--units", "20", "--ticks", "30", "--seed", "1",
                "--world", "10", "--checkpoint-every", "10", "--out", runDir.toString()));
        args.addAll(List.of(extra));
        return RunConfig.parse(args.toArray(String[]::new));
    }

    private static long run(final RunConfig config) throws IOException {
        try (Kernel kernel = Founders.create(config).newKernel(1);
                RunArtifacts artifacts = RunArtifacts.open(config, kernel, RunOrigin.named("test"))) {
            SimulationRun.execute(kernel, config.ticks(), artifacts.observers().toArray(TickObserver[]::new));
            final long digest = kernel.stateDigest();
            artifacts.finish(RunState.endOf(kernel.snapshot()), digest, "report\n");
            return digest;
        }
    }

    private static List<Path> filesUnder(final Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).map(root::relativize).sorted().toList();
        }
    }

    @Test
    void reusedDirectoryDropsTheOldRunsCheckpointsAndFrames() throws Exception {
        // arrange
        final Path runDir = dir.resolve("reused");
        run(config(runDir, "--map-frames", "0"));

        // act
        run(config(runDir, "--ticks", "15"));

        // assert
        assertEquals(List.of(Path.of("0.ckpt"), Path.of("10.ckpt")), filesUnder(runDir.resolve("ckpt")));
        assertFalse(Files.exists(runDir.resolve("frames.zst")));
        assertFalse(Files.exists(runDir.resolve("frames.idx")));
    }

    @Test
    void runWritesTheWholeRunDirectory() throws Exception {
        // arrange
        final RunPaths paths = new RunPaths(dir.resolve("a"));

        // act
        final long digest = run(config(paths.dir()));

        // assert
        for (Path file : List.of(paths.report(), paths.config(), paths.manifest(), paths.status(), paths.metrics(),
                paths.events(), paths.catalog(), paths.births(), paths.layoutJson())) {
            assertTrue(Files.isRegularFile(file), file.toString());
        }
        assertEquals(List.of(Path.of("0.ckpt"), Path.of("10.ckpt"), Path.of("20.ckpt"), Path.of("30.ckpt")),
                filesUnder(paths.checkpoints()));
        assertTrue(Files.notExists(paths.framesZst()));
        assertEquals(new RunStatus(RunState.FINISHED, 30, String.format("%016x", digest), null),
                RunStatus.read(paths.status()));
    }

    @Test
    void dyingWorldEndsExtinct() throws Exception {
        // arrange
        final EngineConfig base = EngineConfig.defaults(1L, 10, 64);
        final EngineConfig starving = new EngineConfig(base.seed(), base.world(), base.chemicals(),
                new Founder(0.5, 1.0), base.metabolism(), base.harvest(), base.motility(), base.growth(),
                base.sensing(), base.reproduction(), base.mutation());
        final Path configFile = dir.resolve("starving.json");
        Files.writeString(configFile, ConfigJson.write(starving));
        final RunPaths paths = new RunPaths(dir.resolve("b"));

        // act
        run(config(paths.dir(), "--genes", "0", "--config", configFile.toString()));

        // assert
        assertEquals(RunState.EXTINCT, RunStatus.read(paths.status()).status());
    }

    @Test
    void sameRunTwiceIsByteIdentical() throws Exception {
        // arrange
        final Path first = dir.resolve("x");
        final Path second = dir.resolve("y");

        // act
        run(config(first, "--map-frames", "0"));
        run(config(second, "--map-frames", "0"));

        // assert
        final List<Path> files = filesUnder(first);
        assertEquals(files, filesUnder(second));
        for (Path file : files) {
            assertArrayEquals(Files.readAllBytes(first.resolve(file)), Files.readAllBytes(second.resolve(file)),
                    file.toString());
        }
    }

    @Test
    void throwDuringRunStillLeavesReadableSideFiles() throws Exception {
        // arrange
        final RunPaths paths = new RunPaths(dir.resolve("run"));
        final RunConfig config = RunConfig.parse(new String[] {"--units", "20", "--ticks", "5", "--seed", "1",
                "--world", "10", "--map-frames", "0", "--out", paths.dir().toString()});

        // act
        assertThrows(IllegalStateException.class, () -> {
            try (Kernel kernel = Founders.create(config).newKernel(1);
                    RunArtifacts artifacts = RunArtifacts.open(config, kernel, RunOrigin.named("run"))) {
                for (int i = 0; i < 5; i++) {
                    kernel.tick();
                    final WorldState snapshot = kernel.snapshot();
                    for (TickObserver observer : artifacts.observers()) {
                        observer.observe(snapshot);
                    }
                }
                throw new IllegalStateException("simulated failure mid-run");
            }
        });

        // assert
        assertTrue(gunzip(paths.births()).startsWith("tick,child_slot"));
        assertTrue(gunzip(paths.catalog()).startsWith("g "));
        assertTrue(Files.readString(paths.framesIdx()).endsWith("end 5\n"));
        assertEquals(RunState.RUNNING, RunStatus.read(paths.status()).status());
    }

    @Test
    void closeRunsEveryCloseableAndRethrowsTheFirstFailureWithTheRestSuppressed() {
        // arrange
        final List<String> closed = new ArrayList<>();
        final IllegalStateException framesFailure = new IllegalStateException("frames close failed");
        final IOException birthsFailure = new IOException("disk full");
        final List<Closeable> inOpenOrder = List.of(
                () -> closed.add("catalog"),
                () -> {
                    closed.add("births");
                    throw birthsFailure;
                },
                () -> {
                    closed.add("frames");
                    throw framesFailure;
                });

        // act
        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> RunArtifacts.closeInReverseOrder(inOpenOrder));

        // assert
        assertEquals(List.of("frames", "births", "catalog"), closed);
        assertSame(framesFailure, thrown);
        assertSame(birthsFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void failedOpenFinishesTheStreamsItAlreadyOpened() throws Exception {
        // arrange
        final RunPaths paths = new RunPaths(dir.resolve("run"));
        Files.createDirectories(paths.events());
        final RunConfig config = RunConfig.parse(new String[] {"--units", "20", "--ticks", "5", "--seed", "1",
                "--world", "10", "--map-frames", "0", "--out", paths.dir().toString()});

        // act
        try (Kernel kernel = Founders.create(config).newKernel(1)) {
            assertThrows(IOException.class, () -> RunArtifacts.open(config, kernel, RunOrigin.named("run")));
        }

        // assert
        assertTrue(gunzip(paths.births()).startsWith("tick,child_slot"));
    }

    private static String gunzip(final Path file) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
