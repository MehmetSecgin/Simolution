package com.simolution.sim;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Reconstructs any tick of a recorded run (report-v8). Determinism is the time
 * machine: the {@link RunManifest} is the replay key, and {@link #seekTo} loads
 * the greatest checkpoint ≤ T then ticks forward the remainder. The result is
 * full fidelity — per-node outputs, delay memory, the evolved per-slot circuit —
 * which no sampled log can hold.
 * <p>
 * Replay is refused unless the build's {@link ConfigHash} matches the manifest's:
 * replaying under changed constants would fabricate a wrong "past" rather than
 * reproduce the real one.
 */
public final class Replayer {

    private final RunManifest manifest;
    private final Path ckptDir;
    private final int[] checkpointTicks;
    private Kernel kernel;

    private Replayer(final RunManifest manifest, final Path ckptDir, final int[] checkpointTicks) {
        this.manifest = manifest;
        this.ckptDir = ckptDir;
        this.checkpointTicks = checkpointTicks;
    }

    public static Replayer open(final Path obsDir) throws IOException {
        final RunManifest manifest = RunManifest.parse(Files.readString(obsDir.resolve("manifest.json")));
        final String current = ConfigHash.compute();
        if (!current.equals(manifest.configHash())) {
            throw new IllegalStateException(
                    "configHash mismatch: run recorded under " + manifest.configHash()
                    + " but this build is " + current + " — replay refused (constants changed)");
        }
        final Path ckptDir = obsDir.resolve("ckpt");
        final int[] ticks = indexCheckpoints(ckptDir);
        return new Replayer(manifest, ckptDir, ticks);
    }

    private static int[] indexCheckpoints(final Path ckptDir) throws IOException {
        if (!Files.isDirectory(ckptDir)) {
            return new int[0];
        }
        final List<Integer> ticks = new ArrayList<>();
        try (Stream<Path> files = Files.list(ckptDir)) {
            files.forEach(p -> {
                final String name = p.getFileName().toString();
                if (name.endsWith(".ckpt")) {
                    ticks.add(Integer.parseInt(name.substring(0, name.length() - ".ckpt".length())));
                }
            });
        }
        ticks.sort(Integer::compareTo);
        final int[] out = new int[ticks.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ticks.get(i);
        }
        return out;
    }

    public RunManifest manifest() {
        return manifest;
    }

    /**
     * The kernel snapshot at tick {@code t} (0 ≤ t ≤ recorded ticks). Builds a
     * fresh kernel from the founders, loads the greatest checkpoint ≤ t (if any),
     * and ticks forward the remainder — worst case {@code checkpointEvery} ticks.
     * The returned kernel is retained so {@link #circuitOf} can read evolved
     * wiring at the same tick.
     */
    public KernelSnapshot seekTo(final int t) throws IOException {
        if (t < 0 || t > manifest.ticks()) {
            throw new IllegalArgumentException(
                    "tick " + t + " out of recorded range [0, " + manifest.ticks() + "]");
        }
        final Kernel k = new Kernel(manifest.founderGenomes(), manifest.worldWidth(),
                manifest.maxGenes(), manifest.founderCells());
        int from = 0;
        final int ckpt = greatestCheckpointAtMost(t);
        if (ckpt >= 0) {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(ckptDir.resolve(ckpt + ".ckpt"))))) {
                k.loadState(in);
            }
            from = ckpt;
        }
        for (int i = from; i < t; i++) {
            k.tick();
        }
        this.kernel = k;
        return k.snapshot();
    }

    public CompiledConnection[] circuitOf(final int slot) {
        if (kernel == null) {
            throw new IllegalStateException("call seekTo(t) before circuitOf(slot)");
        }
        return kernel.connectionsOf(slot);
    }

    public Kernel kernel() {
        return kernel;
    }

    private int greatestCheckpointAtMost(final int t) {
        int best = -1;
        for (final int c : checkpointTicks) {
            if (c <= t) {
                best = c;
            } else {
                break;
            }
        }
        return best;
    }
}
