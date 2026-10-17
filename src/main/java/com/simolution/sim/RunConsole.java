package com.simolution.sim;

import java.nio.file.Path;

public final class RunConsole {

    private RunConsole() {
    }

    public static void announceObservability(final Path obsDir, final int checkpointEvery) {
        System.out.println("observability layer writing to " + obsDir + "/ (run.json, events.jsonl, "
                + "metrics.csv, ckpt/ every " + checkpointEvery + " ticks)");
    }

    public static void announceObservabilityWritten(final Path obsDir) {
        System.out.println("observability artifacts written to " + obsDir
                + "/ (replay: ./gradlew run --args=\"--replay " + obsDir + " --at <tick>\")");
    }

    public static void printReport(final String report, final long elapsedMillis) {
        System.out.println();
        System.out.print(report);
        System.out.println();
        System.out.println("(wall clock: " + elapsedMillis + " ms — console only, never part of the report)");
    }

    public static void printOutputPaths(final RunPaths paths, final boolean framesEnabled) {
        System.out.println("report written to " + paths.report());
        System.out.println("time series written to " + paths.timeSeries());
        System.out.println("lineage / births store written to " + paths.births()
                + " (query: duckdb -c \"SELECT * FROM '" + paths.births() + "'\")");
        if (framesEnabled) {
            System.out.println("spatial map frames written to " + paths.framesZst()
                    + " (chunked zstd; genome catalog: " + paths.catalog() + ")");
            System.out.println("  view live/scrub:  bash scripts/serve-live.sh  (serves runs/ — open the viewer, it tails these files)");
            System.out.println("  bake offline:     python3 tools/mapviz.py " + paths.framesZst() + "  (self-contained .map.html)");
        }
    }
}
