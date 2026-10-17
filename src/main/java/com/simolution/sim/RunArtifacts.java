package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
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
import com.simolution.sim.run.RunDirectory;

public final class RunArtifacts implements Closeable {

    private final RunPaths paths;
    private final boolean framesEnabled;
    private final Path obsDir;
    private final List<Closeable> writers = new ArrayList<>();
    private final List<TickObserver> observers = new ArrayList<>();
    private boolean closed;

    private RunArtifacts(final RunPaths paths, final boolean framesEnabled, final Path obsDir) {
        this.paths = paths;
        this.framesEnabled = framesEnabled;
        this.obsDir = obsDir;
    }

    public static RunArtifacts open(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        if (config.outPath() == null) {
            return new RunArtifacts(null, false, null);
        }
        final RunPaths paths = new RunPaths(Path.of(config.outPath()));
        final boolean framesEnabled = config.mapFrames() >= 0 || config.mapFrom() >= 0;
        final RunArtifacts artifacts = new RunArtifacts(paths, framesEnabled, config.observe() ? paths.obsDir() : null);
        try {
            artifacts.openWriters(config, founders, kernel);
        } catch (IOException | RuntimeException e) {
            try {
                artifacts.closeInReverseOpenOrder();
            } catch (IOException suppressed) {
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
        RunConsole.printOutputPaths(paths, framesEnabled);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        closeInReverseOpenOrder();
        if (obsDir != null) {
            RunConsole.announceObservabilityWritten(obsDir);
        }
    }

    private void openWriters(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        paths.createParentDirectory();
        Files.writeString(paths.layoutJson(), NodeLayout.toJson());
        final GenomeCatalogWriter catalog = new GenomeCatalogWriter(gzipWriter(paths.catalog()));
        writers.add(catalog);
        addWriter(new BirthLogWriter(gzipWriter(paths.births()), catalog));
        if (framesEnabled) {
            addWriter(new MapFrameWriter(Files.newOutputStream(paths.framesZst()),
                    Files.newBufferedWriter(paths.framesIdx()), catalog, founders.engine().worldWidth(),
                    config.ticks(), config.mapFrames(), config.mapFrom(), config.mapTo(), founders.engine()));
        }
        if (obsDir != null) {
            openObservability(config, founders, kernel);
        }
    }

    private void openObservability(final RunConfig config, final Founders founders, final Kernel kernel)
            throws IOException {
        Files.createDirectories(obsDir);
        final int founderCount = founders.genomes().length;
        addWriter(new EventLogWriter(Files.newBufferedWriter(obsDir.resolve("events.jsonl")),
                founders.slotCount(), founderCount, founders.cells(), founders.engine()));
        addWriter(new MetricsWriter(Files.newBufferedWriter(obsDir.resolve("metrics.csv")),
                founderCount, founders.slotCount()));
        final RunDirectory runDirectory = RunDirectory.create(obsDir, kernel, config.checkpointEvery());
        observers.add(snapshot -> runDirectory.checkpointIfDue(kernel));
        RunConsole.announceObservability(obsDir, config.checkpointEvery());
    }

    private <W extends TickObserver & Closeable> void addWriter(final W writer) {
        writers.add(writer);
        observers.add(writer);
    }

    private void closeInReverseOpenOrder() throws IOException {
        IOException failure = null;
        for (int i = writers.size() - 1; i >= 0; i--) {
            try {
                writers.get(i).close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        writers.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static Writer gzipWriter(final Path file) throws IOException {
        return new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8);
    }
}
