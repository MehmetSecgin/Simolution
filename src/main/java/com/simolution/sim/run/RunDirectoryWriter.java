package com.simolution.sim.run;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;
import com.simolution.sim.RunPaths;

/**
 * Writes {@code <dir>/run.json} and {@code <dir>/ckpt/<tick>.ckpt}. {@code runId} is the SHA-256
 * of the first checkpoint's bytes, which hold the config, the layout and the founders and never
 * a thread count, so it identifies the run and nothing about how it was executed.
 * {@code checkpointEvery <= 0} keeps only the first checkpoint.
 */
public class RunDirectoryWriter {

    private final Path ckptDir;
    private final int checkpointEvery;

    private RunDirectoryWriter(final Path ckptDir, final int checkpointEvery) {
        this.ckptDir = ckptDir;
        this.checkpointEvery = checkpointEvery;
    }

    public static RunDirectoryWriter create(final RunPaths paths, final Kernel kernel, final RunManifest draft)
            throws IOException {
        if (kernel.currentTick() != 0 && draft.parent() == null) {
            throw new IllegalArgumentException(
                    "a new run starts at tick 0, kernel is at tick " + kernel.currentTick());
        }
        final Path ckptDir = paths.checkpoints();
        Files.createDirectories(ckptDir);
        final byte[] first = checkpointBytes(kernel);
        Files.write(ckptDir.resolve(RunDirectoryReader.checkpointFileName(kernel.currentTick())), first);
        new RunManifest(sha256Hex(first), Kernel.ENGINE_VERSION, draft.name(), draft.parent(), draft.branchTick(),
                draft.twinOf(), draft.targetTicks(), draft.checkpointEvery(), draft.founders(),
                draft.genesPerFounder()).write(paths.manifest());
        return new RunDirectoryWriter(ckptDir, draft.checkpointEvery());
    }

    public void checkpointIfDue(final Kernel kernel) throws IOException {
        final int tick = kernel.currentTick();
        if (checkpointEvery > 0 && tick % checkpointEvery == 0) {
            try (OutputStream out = Files.newOutputStream(
                    ckptDir.resolve(RunDirectoryReader.checkpointFileName(tick)))) {
                CheckpointCodec.write(kernel, out);
            }
        }
    }

    private static byte[] checkpointBytes(final Kernel kernel) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CheckpointCodec.write(kernel, bytes);
        return bytes.toByteArray();
    }

    private static String sha256Hex(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
