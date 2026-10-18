package com.simolution.sim.run;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;
import com.simolution.sim.RunPaths;

/** Reconstructs any tick of a run directory from its nearest earlier checkpoint. */
public final class RunDirectoryReader {

    private static final String CHECKPOINT_SUFFIX = ".ckpt";

    private RunDirectoryReader() {}

    public static String checkpointFileName(final int tick) {
        return tick + CHECKPOINT_SUFFIX;
    }

    public static Kernel seek(final Path runDir, final int tick, final int threads) throws IOException {
        if (tick < 0) {
            throw new IllegalArgumentException("tick " + tick + " is negative");
        }
        final Path holder = holderOf(runDir, tick);
        final int checkpointTick = checkpointTickAtOrBefore(holder, tick);
        final Kernel kernel = loadCheckpoint(new RunPaths(holder).checkpoints(), checkpointTick, threads);
        return stepOrClose(kernel, tick - checkpointTick);
    }

    public static Path holderOf(final Path runDir, final int tick) throws IOException {
        final RunManifest manifest = RunManifest.read(new RunPaths(runDir).manifest());
        if (manifest.branchTick() != null && tick < manifest.branchTick()) {
            return holderOf(runDir.resolveSibling(manifest.parent()), tick);
        }
        return runDir;
    }

    public static List<Integer> checkpointTicks(final Path runDir) throws IOException {
        try (Stream<Path> files = Files.list(new RunPaths(runDir).checkpoints())) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(CHECKPOINT_SUFFIX))
                    .map(name -> Integer.parseInt(name.substring(0, name.length() - CHECKPOINT_SUFFIX.length())))
                    .sorted().toList();
        }
    }

    public static int checkpointTickAtOrBefore(final Path runDir, final int tick) throws IOException {
        return checkpointTicks(runDir).stream().filter(at -> at <= tick).max(Integer::compare)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no checkpoint at or before tick " + tick + " in " + runDir));
    }

    private static Kernel loadCheckpoint(final Path ckptDir, final int checkpointTick, final int threads)
            throws IOException {
        final Kernel kernel;
        try (InputStream in = Files.newInputStream(ckptDir.resolve(checkpointFileName(checkpointTick)))) {
            kernel = CheckpointCodec.read(in, threads);
        }
        if (kernel.currentTick() != checkpointTick) {
            kernel.close();
            throw new IllegalStateException(
                    "checkpoint " + checkpointFileName(checkpointTick) + " holds tick " + kernel.currentTick());
        }
        return kernel;
    }

    private static Kernel stepOrClose(final Kernel kernel, final int ticks) {
        try {
            kernel.step(ticks);
        } catch (RuntimeException e) {
            kernel.close();
            throw e;
        }
        return kernel;
    }
}
