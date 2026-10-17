package com.simolution.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectory;

public final class ReplayCommand {

    record ReplayArgs(Path obsDir, int tick, Path snapshotOut, int threads) {

        static ReplayArgs parse(final String[] args) {
            String obs = null;
            Integer at = null;
            String snapshotOut = null;
            int threads = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--replay" -> obs = args[++i];
                    case "--at" -> at = Integer.parseInt(args[++i]);
                    case "--snapshot-out" -> snapshotOut = args[++i];
                    case "--threads" -> threads = Integer.parseInt(args[++i]);
                    default -> throw new IllegalArgumentException("Unknown replay argument: " + args[i]);
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
        try (Kernel kernel = RunDirectory.seek(replay.obsDir(), replay.tick(), replay.threads())) {
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
