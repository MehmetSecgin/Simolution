package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

class RunArtifactsTest {

    @TempDir
    Path dir;

    @Test
    void throwDuringRunStillLeavesReadableSideFiles() throws Exception {
        // arrange
        RunPaths paths = new RunPaths(dir.resolve("run.txt"));
        RunConfig config = RunConfig.parse(new String[] {"--units", "20", "--ticks", "5", "--seed", "1",
                "--world", "10", "--map-frames", "0", "--observe", "--out", paths.report().toString()});
        Founders founders = Founders.create(config);

        // act
        assertThrows(IllegalStateException.class, () -> {
            try (Kernel kernel = founders.newKernel(1);
                 RunArtifacts artifacts = RunArtifacts.open(config, founders, kernel)) {
                for (int i = 0; i < 5; i++) {
                    kernel.tick();
                    KernelSnapshot snapshot = kernel.snapshot();
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

    private static String gunzip(Path file) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
