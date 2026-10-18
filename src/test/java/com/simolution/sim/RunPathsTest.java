package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RunPathsTest {

    @Test
    void everyAccessorNamesItsFileInsideTheRunDirectory() {
        // arrange
        final Path dir = Path.of("runs/a");

        // act
        final RunPaths paths = new RunPaths(dir);

        // assert
        assertEquals(dir.resolve("report.txt"), paths.report());
        assertEquals(dir.resolve("config.json"), paths.config());
        assertEquals(dir.resolve("run.json"), paths.manifest());
        assertEquals(dir.resolve("status.json"), paths.status());
        assertEquals(dir.resolve("metrics.csv"), paths.metrics());
        assertEquals(dir.resolve("events.jsonl.gz"), paths.events());
        assertEquals(dir.resolve("ckpt"), paths.checkpoints());
        assertEquals(dir.resolve("frames.zst"), paths.framesZst());
        assertEquals(dir.resolve("frames.idx"), paths.framesIdx());
        assertEquals(dir.resolve("catalog.gz"), paths.catalog());
        assertEquals(dir.resolve("births.csv.gz"), paths.births());
        assertEquals(dir.resolve("layout.json"), paths.layoutJson());
    }
}
