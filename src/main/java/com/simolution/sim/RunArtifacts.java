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
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectoryWriter;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

public class RunArtifacts implements Closeable {

    private final RunPaths paths;
    private final Kernel kernel;
    private final List<Closeable> writers = new ArrayList<>();
    private final List<TickObserver> observers = new ArrayList<>();

    private RunArtifacts(final RunPaths paths, final Kernel kernel) {
        this.paths = paths;
        this.kernel = kernel;
    }

    public static RunArtifacts open(final RunConfig config, final Kernel kernel, final RunOrigin origin)
            throws IOException {
        if (config.runDir() == null) {
            return new RunArtifacts(null, kernel);
        }
        final RunArtifacts artifacts = new RunArtifacts(new RunPaths(config.runDir()), kernel);
        try {
            artifacts.openWriters(config, origin);
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

    public void finish(final RunState state, final long finalDigest, final String report) throws IOException {
        close();
        if (paths == null) {
            return;
        }
        Files.writeString(paths.report(), report);
        new RunStatus(state, kernel.currentTick(), String.format("%016x", finalDigest), null).write(paths.status());
    }

    public void status(final RunState state) throws IOException {
        if (paths != null) {
            new RunStatus(state, kernel.currentTick(), null, null).write(paths.status());
        }
    }

    public void fail(final String error) throws IOException {
        close();
        if (paths != null) {
            new RunStatus(RunState.FAILED, kernel.currentTick(), null, error).write(paths.status());
        }
    }

    @Override
    public void close() throws IOException {
        final List<Closeable> toClose = List.copyOf(writers);
        writers.clear();
        closeInReverseOrder(toClose);
    }

    private void openWriters(final RunConfig config, final RunOrigin origin) throws IOException {
        Files.createDirectories(paths.dir());
        dropPreviousRun();
        Files.writeString(paths.config(), ConfigJson.write(kernel.config()));
        Files.writeString(paths.layoutJson(), NodeLayout.toJson());
        final RunDirectoryWriter checkpoints = RunDirectoryWriter.create(paths, kernel, draft(config, origin));
        status(RunState.RUNNING);
        final GenomeCatalogWriter catalog = track(new GenomeCatalogWriter(openGzipText(paths.catalog())));
        addWriter(new BirthLogWriter(openGzipText(paths.births()), catalog));
        if (config.mapSampling().enabled()) {
            addWriter(new MapFrameWriter(openBytes(paths.framesZst()), openText(paths.framesIdx()), catalog,
                    config.ticks(), config.mapSampling(), kernel.config()));
        }
        addWriter(new EventLogWriter(openText(paths.events()), kernel.state(), config.founderCount()));
        addWriter(new MetricsWriter(openText(paths.metrics()), config.founderCount()));
        observers.add(snapshot -> checkpoints.checkpointIfDue(kernel));
    }

    private void dropPreviousRun() throws IOException {
        if (Files.isDirectory(paths.checkpoints())) {
            try (Stream<Path> files = Files.list(paths.checkpoints())) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }
        }
        Files.deleteIfExists(paths.framesZst());
        Files.deleteIfExists(paths.framesIdx());
    }

    private static RunManifest draft(final RunConfig config, final RunOrigin origin) {
        return new RunManifest(null, null, origin.name(), origin.parent(), origin.branchTick(), origin.twinOf(),
                config.ticks(), config.checkpointEvery(), config.founderCount(), config.genesPerFounder());
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
