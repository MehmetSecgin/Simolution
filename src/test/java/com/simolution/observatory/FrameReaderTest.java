package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectoryReader;
import com.simolution.support.RunDirs;

class FrameReaderTest {

    private static final long BUDGET = 64L << 20;

    @TempDir
    Path runs;

    private static byte[] seeked(final Path dir, final int tick) throws IOException {
        try (Kernel kernel = RunDirectoryReader.seek(dir, tick, 1)) {
            return FrameReader.encode(kernel.state(), kernel.config());
        }
    }

    @Test
    void framesAtChunkEdgesMatchTheState() throws IOException {
        // arrange
        final Path dir = RunDirs.cliRun(RunDirs.config(runs.resolve("ref"), 600, "--map-frames", "0"));
        final FrameReader reader = new FrameReader(BUDGET);

        for (int tick : new int[] {1, 256, 257, 512, 600}) {
            // act
            final byte[] frame = reader.frame(dir, tick);

            // assert
            assertArrayEquals(seeked(dir, tick), frame, "tick " + tick);
        }
    }

    @Test
    void branchPrefixIsReadFromTheParent() throws IOException {
        // arrange
        final Path parent = RunDirs.cliRun(RunDirs.config(runs.resolve("parent"), 400, "--map-frames", "0"));
        final Path branch = RunDirs.branch(parent, 300,
                RunDirs.config(runs.resolve("branch"), 350, "--map-frames", "0"));
        final FrameReader reader = new FrameReader(BUDGET);

        // act
        final byte[] prefix = reader.frame(branch, 100);
        final byte[] own = reader.frame(branch, 340);

        // assert
        assertArrayEquals(reader.frame(parent, 100), prefix);
        assertArrayEquals(seeked(branch, 340), own);
    }

    @Test
    void runWithoutFramesHasNone() throws IOException {
        // arrange
        final Path dir = RunDirs.cliRun(RunDirs.config(runs.resolve("bare"), 20));

        // act
        final byte[] frame = new FrameReader(BUDGET).frame(dir, 10);

        // assert
        assertNull(frame);
    }

    @Test
    void recreatedRunDirectoryIsReadAfresh() throws IOException {
        // arrange
        final Path dir = RunDirs.cliRun(RunDirs.config(runs.resolve("again"), 40, "--map-frames", "0"));
        final FrameReader reader = new FrameReader(BUDGET);
        reader.frame(dir, 20);
        RunDirs.delete(dir);
        RunDirs.cliRun(RunDirs.config(dir, 40, "--map-frames", "0", "--seed", "8"));

        // act
        final byte[] frame = reader.frame(dir, 20);

        // assert
        assertArrayEquals(seeked(dir, 20), frame);
    }

    @Test
    void chunkLargerThanTheBudgetIsStillServed() throws IOException {
        // arrange
        final Path dir = RunDirs.cliRun(RunDirs.config(runs.resolve("tiny"), 300, "--map-frames", "0"));
        final FrameReader reader = new FrameReader(1);

        for (int tick : new int[] {5, 260, 5}) {
            // act
            final byte[] frame = reader.frame(dir, tick);

            // assert
            assertArrayEquals(seeked(dir, tick), frame, "tick " + tick);
        }
    }
}
