package com.simolution.sim.run;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

/**
 * A run on disk (design §8): {@code <dir>/run.json} plus {@code <dir>/ckpt/<tick>.ckpt}, one
 * self-sufficient {@link CheckpointCodec} file per {@code checkpointEvery} ticks, with tick 0
 * (or the branch point) written at creation. {@code runId} is the SHA-256 of the first
 * checkpoint's bytes, which hold the config, the layout and the founders and never a thread
 * count, so it identifies the run and nothing about how it was executed.
 * <p>
 * {@link #seek} loads the nearest checkpoint at or before the tick and steps forward, so it
 * needs nothing but the directory. {@code checkpointEvery <= 0} keeps only the first checkpoint.
 */
public final class RunDirectory {

    private final Path ckptDir;
    private final int checkpointEvery;
    private final String runId;

    private RunDirectory(final Path ckptDir, final int checkpointEvery, final String runId) {
        this.ckptDir = ckptDir;
        this.checkpointEvery = checkpointEvery;
        this.runId = runId;
    }

    public static RunDirectory create(final Path dir, final Kernel kernel, final int checkpointEvery)
            throws IOException {
        if (kernel.currentTick() != 0) {
            throw new IllegalArgumentException(
                    "a new run starts at tick 0, kernel is at tick " + kernel.currentTick() + "; use createBranch");
        }
        return open(dir, kernel, checkpointEvery, "null");
    }

    public static RunDirectory createBranch(final Path dir, final Kernel branched, final int checkpointEvery,
            final String parentRunId, final int parentTick) throws IOException {
        return open(dir, branched, checkpointEvery,
                "{\"runId\": \"" + parentRunId + "\", \"tick\": " + parentTick + "}");
    }

    private static RunDirectory open(final Path dir, final Kernel kernel, final int checkpointEvery,
            final String parentJson) throws IOException {
        final Path ckptDir = dir.resolve("ckpt");
        Files.createDirectories(ckptDir);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CheckpointCodec.write(kernel, bytes);
        final byte[] first = bytes.toByteArray();
        final String runId = sha256Hex(first);
        Files.write(ckptDir.resolve(kernel.currentTick() + ".ckpt"), first);
        Files.writeString(dir.resolve("run.json"),
                "{\"runId\": \"" + runId + "\", \"engine\": \"" + EngineVersion.ID + "\", \"parent\": "
                + parentJson + "}\n");
        return new RunDirectory(ckptDir, checkpointEvery, runId);
    }

    public String runId() {
        return runId;
    }

    public void afterTick(final Kernel kernel) throws IOException {
        final int tick = kernel.currentTick();
        if (checkpointEvery > 0 && tick % checkpointEvery == 0) {
            try (OutputStream out = Files.newOutputStream(ckptDir.resolve(tick + ".ckpt"))) {
                CheckpointCodec.write(kernel, out);
            }
        }
    }

    public static Kernel seek(final Path dir, final int tick) throws IOException {
        if (tick < 0) {
            throw new IllegalArgumentException("tick " + tick + " is negative");
        }
        final Path ckptDir = dir.resolve("ckpt");
        int best = -1;
        try (Stream<Path> files = Files.list(ckptDir)) {
            for (final Path file : files.toList()) {
                final String name = file.getFileName().toString();
                if (name.endsWith(".ckpt")) {
                    final int at = Integer.parseInt(name.substring(0, name.length() - ".ckpt".length()));
                    if (at <= tick && at > best) {
                        best = at;
                    }
                }
            }
        }
        if (best < 0) {
            throw new IllegalArgumentException("no checkpoint at or before tick " + tick + " in " + ckptDir);
        }
        final Kernel kernel;
        try (InputStream in = Files.newInputStream(ckptDir.resolve(best + ".ckpt"))) {
            kernel = CheckpointCodec.read(in);
        }
        if (kernel.currentTick() != best) {
            throw new IllegalStateException(
                    "checkpoint " + best + ".ckpt holds tick " + kernel.currentTick());
        }
        kernel.step(tick - best);
        return kernel;
    }

    private static String sha256Hex(final byte[] bytes) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
