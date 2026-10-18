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
        final FrameReader reader = new FrameReader();

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
        final FrameReader reader = new FrameReader();

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
        final byte[] frame = new FrameReader().frame(dir, 10);

        // assert
        assertNull(frame);
    }
}
