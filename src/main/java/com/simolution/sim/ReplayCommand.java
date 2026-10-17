package com.simolution.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectoryReader;

public final class ReplayCommand {

    record ReplayArgs(
            Path obsDir,
            int tick,
            Path snapshotOut,
            int threads
    ) {

        static ReplayArgs parse(final String[] args) {
            String obs = null;
            Integer at = null;
            String snapshotOut = null;
            int threads = RunConfig.allCores();
            final ArgCursor cursor = new ArgCursor(args);
            while (cursor.hasNext()) {
                final String flag = cursor.next();
                switch (flag) {
                    case "--replay" -> obs = cursor.value(flag);
                    case "--at" -> at = cursor.intValue(flag);
                    case "--snapshot-out" -> snapshotOut = cursor.value(flag);
                    case "--threads" -> threads = cursor.intValue(flag);
                    default -> throw new IllegalArgumentException("Unknown replay argument: " + flag);
                }
            }
            if (obs == null || at == null) {
                throw new IllegalArgumentException("replay needs --replay <obsDir> and --at <tick>");
            }
            return new ReplayArgs(Path.of(obs), at, snapshotOut == null ? null : Path.of(snapshotOut), threads);
        }
    }

    private ReplayCommand() {
    }

    public static boolean requested(final String[] args) {
        return Arrays.asList(args).contains("--replay");
    }

    public static void run(final String[] args) throws IOException {
        final ReplayArgs replay = ReplayArgs.parse(args);
        final String dump;
        try (Kernel kernel = RunDirectoryReader.seek(replay.obsDir(), replay.tick(), replay.threads())) {
            dump = SnapshotDump.render(kernel.snapshot(), kernel.liveConnections(), kernel.config());
        }
        if (replay.snapshotOut() == null) {
            System.out.print(dump);
        } else {
            writeSnapshot(replay.snapshotOut(), dump);
            System.out.println("snapshot at tick " + replay.tick() + " written to " + replay.snapshotOut());
        }
    }

    private static void writeSnapshot(final Path out, final String dump) throws IOException {
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, dump);
    }
}
