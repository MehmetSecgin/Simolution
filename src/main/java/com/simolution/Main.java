package com.simolution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.logging.ConsoleTableLogger;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.BirthLogWriter;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.DynamicsSummary;
import com.simolution.sim.EventLogWriter;
import com.simolution.sim.Founders;
import com.simolution.sim.GenomeCatalogWriter;
import com.simolution.sim.MapFrameWriter;
import com.simolution.sim.MetricsWriter;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunPaths;
import com.simolution.sim.RunReport;
import com.simolution.sim.SnapshotDump;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TimeSeriesReport;
import com.simolution.sim.run.RunDirectory;

public class Main {

    private static final int MAX_TRACED_UNITS = 8;

    static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--replay")) {
            runReplay(args);
            return;
        }
        RunConfig config = RunConfig.parse(args);

        Founders founders = Founders.create(config);
        StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.units());
        EngineConfig engine = founders.engine();
        int worldWidth = engine.worldWidth();
        int maxUnits = founders.slotCount();
        int[][] genomes = founders.genomes();
        int[] founderCells = founders.cells();
        DynamicsObserver observer = new DynamicsObserver();
        ConsoleTableLogger trace = config.trace() ? new ConsoleTableLogger() : null;
        int unitsToTrace = Math.min(config.units(), MAX_TRACED_UNITS);

        TimeSeriesReport timeSeries = new TimeSeriesReport(config.ticks());

        RunPaths paths = null;
        MapFrameWriter mapWriter = null;
        GenomeCatalogWriter catalogWriter = null;
        BirthLogWriter birthLog = null;
        if (config.outPath() != null) {
            paths = new RunPaths(Path.of(config.outPath()));
            paths.createParentDirectory();
            Files.writeString(paths.layoutJson(), NodeLayout.toJson());
            catalogWriter = new GenomeCatalogWriter(
                    new java.io.OutputStreamWriter(
                            new java.util.zip.GZIPOutputStream(Files.newOutputStream(paths.catalog())),
                            java.nio.charset.StandardCharsets.UTF_8));
            birthLog = new BirthLogWriter(
                    new java.io.OutputStreamWriter(
                            new java.util.zip.GZIPOutputStream(Files.newOutputStream(paths.births())),
                            java.nio.charset.StandardCharsets.UTF_8),
                    catalogWriter);
            if (config.mapFrames() >= 0 || config.mapFrom() >= 0) {
                mapWriter = new MapFrameWriter(
                        Files.newOutputStream(paths.framesZst()),
                        Files.newBufferedWriter(paths.framesIdx()), catalogWriter,
                        worldWidth, config.ticks(), config.mapFrames(), config.mapFrom(), config.mapTo(), engine);
            }
        }

        Kernel kernel = founders.newKernel(config.threads());
        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        RunDirectory runDirectory = null;
        Path obsDir = null;
        long startNanos;
        long finalDigest;
        try {
            if (config.observe()) {
                obsDir = paths.obsDir();
                Files.createDirectories(obsDir);
                eventLog = new EventLogWriter(Files.newBufferedWriter(obsDir.resolve("events.jsonl")),
                        maxUnits, genomes.length, founderCells, engine);
                metrics = new MetricsWriter(Files.newBufferedWriter(obsDir.resolve("metrics.csv")),
                        genomes.length, maxUnits);
                runDirectory = RunDirectory.create(obsDir, kernel, config.checkpointEvery());
                System.out.println("observability layer writing to " + obsDir + "/ (run.json, events.jsonl, "
                        + "metrics.csv, ckpt/ every " + config.checkpointEvery() + " ticks)");
            }

            startNanos = System.nanoTime();
            for (int i = 0; i < config.ticks(); i++) {
                kernel.tick();
                KernelSnapshot snapshot = kernel.snapshot();
                observer.observe(snapshot);
                if (config.digestEveryTick()) {
                    System.out.printf("digest %d %016x%n", kernel.currentTick(), kernel.stateDigest());
                }
                timeSeries.sample(i, snapshot);
                if (birthLog != null) {
                    birthLog.observe(snapshot);
                }
                if (mapWriter != null) {
                    mapWriter.maybeFrame(snapshot);
                }
                if (eventLog != null) {
                    eventLog.observe(snapshot);
                    metrics.sample(snapshot);
                    runDirectory.afterTick(kernel);
                }
                if (trace != null) {
                    trace.log(snapshot, unitsToTrace);
                }
            }
            finalDigest = kernel.stateDigest();
        } finally {
            kernel.close();
        }
        if (mapWriter != null) {
            mapWriter.close();
        }
        if (birthLog != null) {
            birthLog.close();
            catalogWriter.close();
        }
        if (eventLog != null) {
            eventLog.close();
            metrics.close();
            System.out.println("observability artifacts written to " + obsDir
                    + "/ (replay: ./gradlew run --args=\"--replay " + obsDir + " --at <tick>\")");
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        DynamicsSummary summary = observer.summarize(finalDigest);
        String report = RunReport.render(config, structure, summary);
        System.out.println();
        System.out.print(report);
        System.out.println();
        System.out.println("(wall clock: " + elapsedMillis + " ms — console only, never part of the report)");

        if (paths != null) {
            Path out = paths.report();
            Files.writeString(out, report);
            System.out.println("report written to " + out);

            Path series = paths.timeSeries();
            Files.writeString(series, timeSeries.render());
            System.out.println("time series written to " + series);

            Path birthsGz = paths.births();
            System.out.println("lineage / births store written to " + birthsGz
                    + " (query: duckdb -c \"SELECT * FROM '" + birthsGz + "'\")");

            if (config.mapFrames() >= 0 || config.mapFrom() >= 0) {
                Path framesZst = paths.framesZst();
                Path catalogGz = paths.catalog();
                System.out.println("spatial map frames written to " + framesZst + " (chunked zstd; genome catalog: " + catalogGz + ")");
                System.out.println("  view live/scrub:  bash scripts/serve-live.sh  (serves runs/ — open the viewer, it tails these files)");
                System.out.println("  bake offline:     python3 tools/mapviz.py " + framesZst + "  (self-contained .map.html)");
            }
        }
    }

    private static void runReplay(final String[] args) throws IOException {
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
        String dump;
        try (Kernel kernel = RunDirectory.seek(Path.of(obs), at, threads)) {
            dump = SnapshotDump.render(kernel.snapshot(), kernel.liveConnections(), kernel.config());
        }
        if (snapshotOut == null) {
            System.out.print(dump);
        } else {
            Path out = Path.of(snapshotOut);
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, dump);
            System.out.println("snapshot at tick " + at + " written to " + out);
        }
    }
}
