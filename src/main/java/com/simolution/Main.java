package com.simolution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.logging.ConsoleTableLogger;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.CheckpointWriter;
import com.simolution.sim.ConfigHash;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.DynamicsSummary;
import com.simolution.sim.EventLogWriter;
import com.simolution.sim.GenomeFactory;
import com.simolution.sim.LineageReport;
import com.simolution.sim.LiveServer;
import com.simolution.sim.MapFrameWriter;
import com.simolution.sim.MetricsWriter;
import com.simolution.sim.PopulationReport;
import com.simolution.sim.Replayer;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunManifest;
import com.simolution.sim.RunReport;
import com.simolution.sim.SnapshotDump;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TimeSeriesReport;
import com.simolution.sim.UnitCsvReport;
import com.simolution.sim.WiringReport;

public class Main {

    private static final int MAX_TRACED_UNITS = 8;

    static void main(String[] args) throws IOException {
        if (Arrays.asList(args).contains("--replay")) {
            runReplay(args);
            return;
        }
        RunConfig config = RunConfig.parse(args);

        int[][] genomes = config.demo()
                ? new int[][] {demoGenome()}
                : GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());

        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());

        int maxGenes = config.maxGenes();
        for (int[] genome : genomes) {
            maxGenes = Math.max(maxGenes, genome.length);
        }
        int worldWidth = config.worldWidth();
        int maxUnits = worldWidth * worldWidth;
        int[] founderCells = Kernel.scatterFounders(genomes.length, worldWidth);
        Kernel kernel = new Kernel(genomes, worldWidth, maxGenes, founderCells);
        kernel.configureInflow(config.inflow());
        DynamicsObserver observer = new DynamicsObserver(config.units(), maxUnits, connections);
        ConsoleTableLogger trace = config.trace() ? new ConsoleTableLogger() : null;
        int unitsToTrace = Math.min(config.units(), MAX_TRACED_UNITS);

        TimeSeriesReport timeSeries = new TimeSeriesReport(config.ticks());

        MapFrameWriter mapWriter = null;
        LiveServer liveServer = null;
        if (config.outPath() != null && config.mapFrames() > 0) {
            Path out = Path.of(config.outPath());
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Path mapPath = sibling(out, ".map.txt");
            mapWriter = new MapFrameWriter(
                    Files.newBufferedWriter(mapPath),
                    worldWidth, config.ticks(), config.mapFrames());
            if (config.servePort() > 0) {
                liveServer = new LiveServer(config.servePort(), mapPath);
                liveServer.start();
                System.out.println("live map serving at http://localhost:" + config.servePort()
                        + "  (watch it tick; Ctrl-C to stop)");
            }
        } else if (config.servePort() > 0) {
            throw new IllegalArgumentException("--serve requires --out (and --map-frames > 0) to stream frames");
        }

        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        CheckpointWriter checkpoints = null;
        Path obsDir = null;
        if (config.observe()) {
            obsDir = obsDir(Path.of(config.outPath()));
            Files.createDirectories(obsDir);
            eventLog = new EventLogWriter(Files.newBufferedWriter(obsDir.resolve("events.jsonl")),
                    maxUnits, genomes.length, worldWidth, founderCells);
            metrics = new MetricsWriter(Files.newBufferedWriter(obsDir.resolve("metrics.csv")),
                    genomes.length, maxUnits);
            checkpoints = new CheckpointWriter(obsDir.resolve("ckpt"), config.checkpointEvery());
            int mapSampleEvery = config.mapFrames() > 0
                    ? Math.max(1, config.ticks() / config.mapFrames()) : 0;
            RunManifest manifest = new RunManifest("v4", config.seed(), worldWidth, genomes.length,
                    founderCells, genomes, config.seed(), config.genesPerUnit(), maxGenes,
                    config.ticks(), ConfigHash.compute(), config.checkpointEvery(), mapSampleEvery);
            Files.writeString(obsDir.resolve("manifest.json"), manifest.toJson());
            System.out.println("observability layer writing to " + obsDir + "/ (manifest, events.jsonl, "
                    + "metrics.csv, ckpt/ every " + config.checkpointEvery() + " ticks)");
        }

        long startNanos = System.nanoTime();
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            observer.observe(snapshot);
            timeSeries.sample(i, snapshot);
            if (mapWriter != null) {
                mapWriter.maybeFrame(snapshot);
            }
            if (eventLog != null) {
                eventLog.observe(snapshot);
                metrics.sample(snapshot);
                checkpoints.maybeCheckpoint(kernel, snapshot);
            }
            if (trace != null) {
                trace.log(snapshot, unitsToTrace);
            }
        }
        if (mapWriter != null) {
            mapWriter.close();
        }
        if (eventLog != null) {
            eventLog.close();
            metrics.close();
            checkpoints.close();
            System.out.println("observability artifacts written to " + obsDir
                    + "/ (replay: ./gradlew run --args=\"--replay " + obsDir + " --at <tick>\")");
        }
        if (liveServer != null) {
            liveServer.markDone();
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        DynamicsSummary summary = observer.summarize();
        String report = RunReport.render(config, structure, summary);
        System.out.println();
        System.out.print(report);
        System.out.println();
        System.out.println("(wall clock: " + elapsedMillis + " ms — console only, never part of the report)");

        if (config.outPath() != null) {
            Path out = Path.of(config.outPath());
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, report);
            System.out.println("report written to " + out);

            Path csv = sibling(out, ".units.csv");
            Files.writeString(csv, UnitCsvReport.render(config.units(), structure, summary));
            System.out.println("per-unit detail written to " + csv);

            Path wiring = sibling(out, ".wiring.csv");
            Files.writeString(wiring, WiringReport.render(connections));
            System.out.println("per-unit wiring written to " + wiring);

            Path lineage = sibling(out, ".lineage.csv");
            Files.writeString(lineage, LineageReport.render(observer.lineageSummary(), config.ticks()));
            System.out.println("per-lineage detail written to " + lineage);

            Path series = sibling(out, ".timeseries.csv");
            Files.writeString(series, timeSeries.render());
            System.out.println("time series written to " + series);

            Path population = sibling(out, ".population.csv");
            Files.writeString(population, PopulationReport.render(kernel.snapshot()));
            System.out.println("final population written to " + population);

            Path popWiring = sibling(out, ".popwiring.csv");
            Files.writeString(popWiring, WiringReport.render(kernel.liveConnections()));
            System.out.println("evolved wiring written to " + popWiring);

            if (config.mapFrames() > 0) {
                System.out.println("spatial map frames written to " + sibling(out, ".map.txt")
                        + " (render: python3 tools/mapviz.py " + sibling(out, ".map.txt") + ")");
            }
        }

        if (liveServer != null) {
            System.out.println();
            System.out.println("run complete — live map still serving at http://localhost:"
                    + config.servePort() + " (scrub/replay). Ctrl-C to stop.");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                liveServer.stop();
            }
        }
    }

    /**
     * Replay surface (report-v8): {@code --replay <obsDir> --at <T> [--snapshot-out <file>]}.
     * Reconstructs tick T from the recorded run (checkpoint + forward ticks) and
     * writes a {@link SnapshotDump} (lattice + per-unit table + evolved circuits) —
     * the input {@code tools/timetravel.py} renders into an HTML state page. With
     * no {@code --snapshot-out}, the dump goes to stdout.
     */
    private static void runReplay(final String[] args) throws IOException {
        String obs = null;
        Integer at = null;
        String snapshotOut = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--replay" -> obs = args[++i];
                case "--at" -> at = Integer.parseInt(args[++i]);
                case "--snapshot-out" -> snapshotOut = args[++i];
                default -> throw new IllegalArgumentException("Unknown replay argument: " + args[i]);
            }
        }
        if (obs == null || at == null) {
            throw new IllegalArgumentException("replay needs --replay <obsDir> and --at <tick>");
        }
        Replayer replayer = Replayer.open(Path.of(obs));
        KernelSnapshot snapshot = replayer.seekTo(at);
        String dump = SnapshotDump.render(snapshot, replayer.kernel().liveConnections());
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

    private static Path obsDir(final Path reportPath) {
        final String name = reportPath.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        final String base = dot < 0 ? name : name.substring(0, dot);
        final Path parent = reportPath.getParent();
        final String dirName = base + ".obs";
        return parent == null ? Path.of(dirName) : parent.resolve(dirName);
    }

    private static Path sibling(final Path reportPath, final String suffix) {
        String name = reportPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        Path parent = reportPath.getParent();
        String siblingName = base + suffix;
        return parent == null ? Path.of(siblingName) : parent.resolve(siblingName);
    }

    private static int[] demoGenome() {
        short wConstToAdd = 2048;
        short wAddToDelay = 512;
        short wDelayToAdd = 512;
        short wAddToAction = 512;

        return new int[] {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wConstToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(wAddToDelay)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wDelayToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw(wAddToAction)
                           .build()
        };
    }
}
