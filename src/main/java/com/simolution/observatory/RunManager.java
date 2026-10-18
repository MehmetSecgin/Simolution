package com.simolution.observatory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.Founders;
import com.simolution.sim.MapSampling;
import com.simolution.sim.RunArtifacts;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunPaths;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.run.Branching;
import com.simolution.sim.run.RunDirectoryWriter;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

public class RunManager implements AutoCloseable, RunSession.Listener {

    public record Launch(
            String dir,
            boolean existing
    ) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CONFIG_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.json");
    private static final long CLOSE_WAIT_MILLIS = 10_000;

    private final RunStore store;
    private final Path configs;
    private final EventStream events;
    private final int cores;
    private final Deque<RunSession> queue = new ArrayDeque<>();
    private final Map<String, RunSession> sessions = new ConcurrentHashMap<>();
    private final Thread worker;
    private RunSession running;
    private boolean closed;

    public RunManager(final RunStore store, final Path configs, final EventStream events, final int cores) {
        this.store = store;
        this.configs = configs;
        this.events = events;
        this.cores = cores;
        this.worker = Thread.ofPlatform().name("observatory-runs").daemon().start(this::work);
    }

    public List<String> configs() throws IOException {
        try (Stream<Path> files = Files.list(configs)) {
            return files.map(f -> f.getFileName().toString()).filter(n -> CONFIG_NAME.matcher(n).matches())
                    .sorted().toList();
        }
    }

    public synchronized Launch create(final RunRequest request) throws IOException {
        requirePositive("founders", request.founders());
        requirePositive("targetTicks", request.targetTicks());
        requirePositive("checkpointEvery", request.checkpointEvery());
        if (request.genesPerFounder() < 0) {
            throw new IllegalArgumentException("genesPerFounder must be 0 or more");
        }
        final EngineConfig engine = withOverrides(readConfig(request.configFile()), request.overrides());
        final RunConfig config = runConfig(request.founders(), request.genesPerFounder(), request.targetTicks(),
                request.checkpointEvery(), engine, null, cores);
        final Founders founders = Founders.create(config);
        final StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.founderCount());
        return launch(founders.newKernel(cores), config, new RunOrigin(request.name(), null, null, null), structure,
                true);
    }

    public synchronized Launch branch(final String dir, final int tick, final Map<String, JsonNode> overrides,
            final int targetTicks, final String name) throws IOException {
        final RunEntry parent = store.readable(dir);
        final int done = ticksDone(dir);
        if (tick < 0 || tick > done) {
            throw new IllegalArgumentException("tick " + tick + " is outside 0.." + done);
        }
        if (targetTicks <= tick) {
            throw new IllegalArgumentException("targetTicks must be after the branch tick " + tick);
        }
        final Path parentDir = store.path(dir);
        final EngineConfig engine =
                withOverrides(Files.readString(new RunPaths(parentDir).config()), overrides);
        final RunManifest manifest = parent.manifest();
        final RunConfig config = runConfig(manifest.founders(), manifest.genesPerFounder(), targetTicks,
                manifest.checkpointEvery(), engine, null, cores);
        final Kernel kernel = Branching.start(parentDir, tick, engine, cores);
        final String branchName = name == null || name.isBlank() ? manifest.name() + " branch" : name;
        return launch(kernel, config, new RunOrigin(branchName, dir, tick, null), liveStructure(kernel), true);
    }

    public synchronized Launch rerun(final String dir) throws IOException {
        final RunEntry original = store.readable(dir);
        if (sessions.containsKey(dir) || !original.status().status().ended()) {
            throw new IllegalStateException(dir + " has not ended; a re-run needs an ended run");
        }
        final RunManifest manifest = original.manifest();
        final EngineConfig engine = ConfigJson.read(Files.readString(new RunPaths(store.path(dir)).config()));
        final int threads = manifest.twinOf() == null ? Math.max(1, cores / 2) : cores;
        final RunConfig config = runConfig(manifest.founders(), manifest.genesPerFounder(),
                original.status().ticksDone(), manifest.checkpointEvery(), engine, null, threads);
        final RunOrigin origin =
                new RunOrigin(manifest.name() + " re-run", manifest.parent(), manifest.branchTick(), dir);
        if (manifest.parent() == null) {
            final Founders founders = Founders.create(config);
            final StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.founderCount());
            return launch(founders.newKernel(threads), config, origin, structure, false);
        }
        final Kernel kernel = Branching.start(store.path(manifest.parent()), manifest.branchTick(), engine, threads);
        return launch(kernel, config, origin, liveStructure(kernel), false);
    }

    public synchronized void pause(final String dir) throws IOException {
        final RunSession session = active(dir);
        if (session == running && session.state() != RunState.PAUSED) {
            session.requestPause();
        } else if (session.state() == RunState.QUEUED) {
            queue.remove(session);
            session.become(RunState.PAUSED);
        } else {
            throw new IllegalStateException(dir + " is already paused");
        }
    }

    public synchronized void resume(final String dir) throws IOException {
        final RunSession session = active(dir);
        if (session.state() != RunState.PAUSED) {
            throw new IllegalStateException(dir + " is not paused");
        }
        session.become(RunState.QUEUED);
        queue.addFirst(session);
        notifyAll();
    }

    public synchronized void stop(final String dir) throws IOException {
        final RunSession session = active(dir);
        if (session == running && session.state() != RunState.PAUSED) {
            session.requestStop();
            return;
        }
        queue.remove(session);
        sessions.remove(dir);
        session.end(RunState.STOPPED);
    }

    public RunStatus status(final String dir) throws IOException {
        final RunSession session = sessions.get(dir);
        if (session != null) {
            return new RunStatus(session.state(), session.tick(), null, null);
        }
        return store.get(dir).status();
    }

    public int ticksDone(final String dir) throws IOException {
        final RunStatus status = status(dir);
        return status == null ? 0 : status.ticksDone();
    }

    public LatestFrame latest(final String dir) {
        final RunSession session = sessions.get(dir);
        return session == null ? null : session.latest();
    }

    public boolean isActive(final String dir) {
        return sessions.containsKey(dir);
    }

    public boolean twinRunning(final String original) {
        return sessions.values().stream().anyMatch(session -> original.equals(session.origin().twinOf()));
    }

    @Override
    public void changed(final RunSession session) {
        events.send("run", Map.of("dir", session.dir(), "status", session.state().id(), "ticksDone",
                session.tick()));
    }

    @Override
    public void progress(final RunSession session) {
        events.send("progress", Map.of("dir", session.dir(), "tick", session.tick(), "targetTicks",
                session.targetTicks()));
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            if (running != null) {
                running.requestStop();
            }
            notifyAll();
        }
        try {
            worker.join(CLOSE_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            for (RunSession session : sessions.values()) {
                if (session != running) {
                    try {
                        session.abandon();
                    } catch (IOException | RuntimeException ignored) {
                        continue;
                    }
                }
            }
            sessions.clear();
            queue.clear();
        }
    }

    private Launch launch(final Kernel kernel, final RunConfig draft, final RunOrigin origin,
            final StructuralStats structure, final boolean reuseExisting) throws IOException {
        try {
            if (reuseExisting) {
                final RunEntry existing = store.findByRunId(RunDirectoryWriter.runIdOf(kernel));
                if (existing != null) {
                    kernel.close();
                    return new Launch(existing.dir(), true);
                }
            }
            final Path dir = store.newDir(origin.name());
            final RunConfig config = runConfig(draft.founderCount(), draft.genesPerFounder(), draft.ticks(),
                    draft.checkpointEvery(), draft.engine(), dir, draft.threads());
            final RunArtifacts artifacts = RunArtifacts.open(config, kernel, origin);
            final RunSession session = new RunSession(dir.getFileName().toString(), config, origin, kernel,
                    artifacts, structure, this);
            sessions.put(session.dir(), session);
            session.become(RunState.QUEUED);
            queue.addLast(session);
            notifyAll();
            return new Launch(session.dir(), false);
        } catch (IOException | RuntimeException e) {
            kernel.close();
            throw e;
        }
    }

    private void work() {
        while (true) {
            final RunSession session;
            synchronized (this) {
                while (queue.isEmpty() && !closed) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (closed) {
                    return;
                }
                session = queue.removeFirst();
                running = session;
            }
            session.run();
            synchronized (this) {
                running = null;
                if (session.done()) {
                    sessions.remove(session.dir());
                }
            }
        }
    }

    private RunSession active(final String dir) {
        store.path(dir);
        final RunSession session = sessions.get(dir);
        if (session == null || session.done()) {
            throw new IllegalStateException(dir + " is not running");
        }
        return session;
    }

    public String readConfig(final String name) throws IOException {
        if (name == null || !CONFIG_NAME.matcher(name).matches() || !Files.isRegularFile(configs.resolve(name))) {
            throw new IllegalArgumentException("unknown config file: " + name);
        }
        return Files.readString(configs.resolve(name));
    }

    private static EngineConfig withOverrides(final String json, final Map<String, JsonNode> overrides)
            throws IOException {
        final ObjectNode root = (ObjectNode) JSON.readTree(json);
        if (overrides != null) {
            for (Map.Entry<String, JsonNode> override : overrides.entrySet()) {
                if (!root.has(override.getKey())) {
                    throw new IllegalArgumentException("unknown setting: " + override.getKey());
                }
                root.set(override.getKey(), override.getValue());
            }
        }
        return ConfigJson.read(JSON.writeValueAsString(root));
    }

    private static RunConfig runConfig(final int founders, final int genesPerFounder, final int targetTicks,
            final int checkpointEvery, final EngineConfig engine, final Path dir, final int threads) {
        return new RunConfig(founders, targetTicks, genesPerFounder, false, dir, MapSampling.EVERY_TICK,
                checkpointEvery, engine, threads, false);
    }

    private static StructuralStats liveStructure(final Kernel kernel) {
        final CompiledConnection[] live = kernel.liveConnections();
        final CompiledConnection[] compact = new CompiledConnection[live.length];
        int units = 0;
        int lastUnit = -1;
        for (int i = 0; i < live.length; i++) {
            final int unit = live[i].sourceAbsoluteIndex() / NodeLayout.TOTAL;
            if (unit != lastUnit) {
                lastUnit = unit;
                units++;
            }
            final int shift = (unit - (units - 1)) * NodeLayout.TOTAL;
            compact[i] = new CompiledConnection(live[i].sourceAbsoluteIndex() - shift,
                    live[i].destinationAbsoluteIndex() - shift, live[i].weight());
        }
        return StructuralAnalyzer.analyze(compact, units);
    }

    private static void requirePositive(final String field, final int value) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be at least 1");
        }
    }
}
