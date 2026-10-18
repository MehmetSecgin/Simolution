package com.simolution.sim;

import java.nio.file.Path;

public record RunPaths(Path dir) {

    public Path report() {
        return dir.resolve("report.txt");
    }

    public Path config() {
        return dir.resolve("config.json");
    }

    public Path manifest() {
        return dir.resolve("run.json");
    }

    public Path status() {
        return dir.resolve("status.json");
    }

    public Path metrics() {
        return dir.resolve("metrics.csv");
    }

    public Path events() {
        return dir.resolve("events.jsonl");
    }

    public Path checkpoints() {
        return dir.resolve("ckpt");
    }

    public Path framesZst() {
        return dir.resolve("frames.zst");
    }

    public Path framesIdx() {
        return dir.resolve("frames.idx");
    }

    public Path catalog() {
        return dir.resolve("catalog.gz");
    }

    public Path births() {
        return dir.resolve("births.csv.gz");
    }

    public Path layoutJson() {
        return dir.resolve("layout.json");
    }
}
