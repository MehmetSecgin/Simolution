package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RunPathsTest {

    @Test
    void siblingsShareTheReportBase() {
        // arrange
        RunPaths paths = new RunPaths(Path.of("runs/a.txt"));

        // act + assert
        assertEquals(Path.of("runs/a.catalog.gz"), paths.catalog());
        assertEquals(Path.of("runs/a.obs"), paths.obsDir());
    }

    @Test
    void reportWithoutDotOrParent() {
        // arrange
        RunPaths paths = new RunPaths(Path.of("a"));

        // act + assert
        assertEquals(Path.of("a.births.csv.gz"), paths.births());
    }
}
