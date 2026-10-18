package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.WorldState;

class RunArtifactsTest {

    @TempDir
    Path dir;

    @Test
    void throwDuringRunStillLeavesReadableSideFiles() throws Exception {
        // arrange
        final RunPaths paths = new RunPaths(dir.resolve("run.txt"));
        final RunConfig config = RunConfig.parse(new String[] {"--units", "20", "--ticks", "5", "--seed", "1",
                "--world", "10", "--map-frames", "0", "--observe", "--out", paths.report().toString()});
        final Founders founders = Founders.create(config);

        // act
        assertThrows(IllegalStateException.class, () -> {
            try (Kernel kernel = founders.newKernel(1);
                    RunArtifacts artifacts = RunArtifacts.open(config, founders, kernel)) {
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
        final RunPaths paths = new RunPaths(dir.resolve("run.txt"));
        Files.createDirectories(paths.framesIdx());
        final RunConfig config = RunConfig.parse(new String[] {"--units", "20", "--ticks", "5", "--seed", "1",
                "--world", "10", "--map-frames", "0", "--out", paths.report().toString()});
        final Founders founders = Founders.create(config);

        // act
        try (Kernel kernel = founders.newKernel(1)) {
            assertThrows(IOException.class, () -> RunArtifacts.open(config, founders, kernel));
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
