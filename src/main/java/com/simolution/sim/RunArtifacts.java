package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectoryWriter;

public class RunArtifacts implements Closeable {

    private final RunPaths paths;
    private final boolean framesEnabled;
    private final Path obsDir;
    private final List<Closeable> writers = new ArrayList<>();
    private final List<TickObserver> observers = new ArrayList<>();

    private RunArtifacts(final RunPaths paths, final boolean framesEnabled, final Path obsDir) {
        this.paths = paths;
        this.framesEnabled = framesEnabled;
        this.obsDir = obsDir;
    }

    public static RunArtifacts open(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        if (config.reportPath() == null) {
            return new RunArtifacts(null, false, null);
        }
        final RunPaths paths = new RunPaths(config.reportPath());
        final boolean framesEnabled = config.mapSampling().enabled();
        final RunArtifacts artifacts = new RunArtifacts(paths, framesEnabled,
                config.observation().enabled() ? paths.obsDir() : null);
        try {
            artifacts.openWriters(config, founders, kernel);
        } catch (IOException | RuntimeException e) {
            try {
                artifacts.close();
            } catch (IOException | RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        return artifacts;
    }

    public List<TickObserver> observers() {
        return List.copyOf(observers);
    }

    public void writeReport(final String report, final String timeSeriesCsv) throws IOException {
        if (paths == null) {
            return;
        }
        Files.writeString(paths.report(), report);
        Files.writeString(paths.timeSeries(), timeSeriesCsv);
        System.out.println("report written to " + paths.report());
        System.out.println("time series written to " + paths.timeSeries());
        System.out.println("lineage / births store written to " + paths.births()
                + " (query: duckdb -c \"SELECT * FROM '" + paths.births() + "'\")");
        if (framesEnabled) {
            System.out.println("spatial map frames written to " + paths.framesZst()
                    + " (chunked zstd; genome catalog: " + paths.catalog() + ")");
            System.out.println(
                    "  view live/scrub:  bash scripts/serve-live.sh  (serves runs/ — open the viewer, it tails these files)");
            System.out.println("  bake offline:     python3 tools/mapviz.py " + paths.framesZst()
                    + "  (self-contained .map.html)");
        }
    }

    @Override
    public void close() throws IOException {
        final List<Closeable> toClose = List.copyOf(writers);
        writers.clear();
        closeInReverseOrder(toClose);
    }

    public void announceObservabilityWritten() {
        if (obsDir != null) {
            System.out.println("observability artifacts written to " + obsDir
                    + "/ (replay: ./gradlew run --args=\"--replay " + obsDir + " --at <tick>\")");
        }
    }

    private void openWriters(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        RunPaths.createParentDirectory(paths.report());
        Files.writeString(paths.layoutJson(), NodeLayout.toJson());
        final GenomeCatalogWriter catalog = track(new GenomeCatalogWriter(openGzipText(paths.catalog())));
        addWriter(new BirthLogWriter(openGzipText(paths.births()), catalog));
        if (framesEnabled) {
            addWriter(new MapFrameWriter(openBytes(paths.framesZst()), openText(paths.framesIdx()), catalog,
                    config.ticks(), config.mapSampling(), founders.engine()));
        }
        if (obsDir != null) {
            openObservability(config, founders, kernel);
        }
    }

    private void openObservability(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        Files.createDirectories(obsDir);
        final int founderCount = founders.genomes().length;
        addWriter(new EventLogWriter(openText(obsDir.resolve("events.jsonl")), founders.slotCount(),
                founderCount, founders.cells(), founders.engine().founder().initialEnergy()));
        addWriter(new MetricsWriter(openText(obsDir.resolve("metrics.csv")), founderCount));
        final int checkpointEvery = config.observation().checkpointEvery();
        final RunDirectoryWriter runDirectory = RunDirectoryWriter.create(obsDir, kernel, checkpointEvery);
        observers.add(snapshot -> runDirectory.checkpointIfDue(kernel));
        System.out.println("observability layer writing to " + obsDir + "/ (run.json, events.jsonl, "
                + "metrics.csv, ckpt/ every " + checkpointEvery + " ticks)");
    }

    private <W extends TickObserver & Closeable> void addWriter(final W writer) {
        observers.add(track(writer));
    }

    private <C extends Closeable> C track(final C closeable) {
        writers.add(closeable);
        return closeable;
    }

    private OutputStream openBytes(final Path file) throws IOException {
        return track(Files.newOutputStream(file));
    }

    private Writer openText(final Path file) throws IOException {
        return track(Files.newBufferedWriter(file));
    }

    private Writer openGzipText(final Path file) throws IOException {
        final OutputStream bytes = openBytes(file);
        return track(new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8));
    }

    static void closeInReverseOrder(final List<? extends Closeable> closeables) throws IOException {
        Exception failure = null;
        for (int i = closeables.size() - 1; i >= 0; i--) {
            try {
                closeables.get(i).close();
            } catch (IOException | RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof IOException ioFailure) {
            throw ioFailure;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
    }
}
