package com.simolution.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record RunPaths(Path report) {

    public Path layoutJson() {
        return sibling(".layout.json");
    }

    public Path catalog() {
        return sibling(".catalog.gz");
    }

    public Path births() {
        return sibling(".births.csv.gz");
    }

    public Path framesZst() {
        return sibling(".frames.zst");
    }

    public Path framesIdx() {
        return sibling(".frames.idx");
    }

    public Path timeSeries() {
        return sibling(".timeseries.csv");
    }

    public Path obsDir() {
        return sibling(".obs");
    }

    public static void createParentDirectory(final Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
    }

    private Path sibling(final String suffix) {
        final String name = report.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        final String base = dot < 0 ? name : name.substring(0, dot);
        return report.resolveSibling(base + suffix);
    }
}
