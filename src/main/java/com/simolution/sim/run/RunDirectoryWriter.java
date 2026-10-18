package com.simolution.sim.run;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

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

    public static RunDirectoryWriter create(final Path dir, final Kernel kernel, final int checkpointEvery)
            throws IOException {
        if (kernel.currentTick() != 0) {
            throw new IllegalArgumentException(
                    "a new run starts at tick 0, kernel is at tick " + kernel.currentTick());
        }
        final Path ckptDir = dir.resolve("ckpt");
        Files.createDirectories(ckptDir);
        final byte[] first = checkpointBytes(kernel);
        final String runId = sha256Hex(first);
        Files.write(ckptDir.resolve(RunDirectoryReader.checkpointFileName(kernel.currentTick())), first);
        Files.writeString(dir.resolve("run.json"), runManifestJson(runId));
        return new RunDirectoryWriter(ckptDir, checkpointEvery);
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

    private static String runManifestJson(final String runId) {
        return "{\"runId\": \"" + runId + "\", \"engine\": \"" + EngineVersion.ID + "\", \"parent\": null}\n";
    }

    private static byte[] checkpointBytes(final Kernel kernel) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CheckpointCodec.write(kernel, bytes);
        return bytes.toByteArray();
    }

    private static String sha256Hex(final byte[] bytes) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
