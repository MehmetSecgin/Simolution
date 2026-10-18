package com.simolution.observatory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.simolution.kernel.state.CheckpointCodec;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunStatus;

public final class Twins {

    private static final String SUFFIX = ".ckpt";

    public record TwinResult(
            boolean identical,
            int checkpoints,
            Integer firstDifferentTick
    ) {}

    private Twins() {}

    public static TwinResult compare(final Path a, final Path b) throws IOException {
        final RunPaths left = new RunPaths(a);
        final RunPaths right = new RunPaths(b);
        int compared = 0;
        Integer firstDifferent = null;
        for (int tick : checkpointTicks(left.checkpoints())) {
            final Path other = right.checkpoints().resolve(tick + SUFFIX);
            if (!Files.exists(other)) {
                continue;
            }
            compared++;
            if (firstDifferent == null && digest(left.checkpoints().resolve(tick + SUFFIX)) != digest(other)) {
                firstDifferent = tick;
            }
        }
        if (firstDifferent != null) {
            return new TwinResult(false, compared, firstDifferent);
        }
        final RunStatus leftStatus = RunStatus.read(left.status());
        final RunStatus rightStatus = RunStatus.read(right.status());
        if (leftStatus.finalDigest() != null && rightStatus.finalDigest() != null
                && leftStatus.ticksDone() == rightStatus.ticksDone()
                && !leftStatus.finalDigest().equals(rightStatus.finalDigest())) {
            return new TwinResult(false, compared, leftStatus.ticksDone());
        }
        return new TwinResult(true, compared, null);
    }

    private static List<Integer> checkpointTicks(final Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(SUFFIX))
                    .map(n -> Integer.parseInt(n.substring(0, n.length() - SUFFIX.length()))).sorted().toList();
        }
    }

    private static long digest(final Path checkpoint) throws IOException {
        try (InputStream in = Files.newInputStream(checkpoint)) {
            return CheckpointCodec.readDigest(in);
        }
    }
}
