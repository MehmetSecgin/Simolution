package com.simolution.sim;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Writes full-state kernel checkpoints to {@code <obs>/ckpt/<tick>.ckpt} every
 * {@code C} ticks (report-v8). A checkpoint is the replay accelerator: rather
 * than ticking from 0 to inspect tick T, {@link Replayer} loads the greatest
 * checkpoint ≤ T and ticks forward only the remainder (≤ C). Each file is
 * written and closed immediately — no in-memory accumulation; disk cost is
 * {@code (#checkpoints) × per-slot-state}, traded against jump latency by C.
 * {@code C = 0} disables checkpoints entirely (pure O(T) replay).
 */
public final class CheckpointWriter implements Closeable {

    private final Path ckptDir;
    private final int every;

    public CheckpointWriter(final Path ckptDir, final int every) throws IOException {
        this.ckptDir = ckptDir;
        this.every = every;
        if (every > 0) {
            Files.createDirectories(ckptDir);
        }
    }

    public void maybeCheckpoint(final Kernel kernel, final KernelSnapshot snapshot) throws IOException {
        if (every <= 0 || snapshot.tick % every != 0) {
            return;
        }
        final Path file = ckptDir.resolve(snapshot.tick + ".ckpt");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            kernel.saveState(out);
        }
    }

    @Override
    public void close() {
        // each checkpoint owns its stream; nothing to release here
    }
}
