package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RunPathsTest {

    @Test
    void siblingsShareTheReportBase() {
        // arrange
        final RunPaths paths = new RunPaths(Path.of("runs/a.txt"));

        // act
        final Path catalog = paths.catalog();
        final Path obsDir = paths.obsDir();

        // assert
        assertEquals(Path.of("runs/a.catalog.gz"), catalog);
        assertEquals(Path.of("runs/a.obs"), obsDir);
    }

    @Test
    void reportWithoutDotOrParent() {
        // arrange
        final RunPaths paths = new RunPaths(Path.of("a"));

        // act
        final Path births = paths.births();

        // assert
        assertEquals(Path.of("a.births.csv.gz"), births);
    }
}
