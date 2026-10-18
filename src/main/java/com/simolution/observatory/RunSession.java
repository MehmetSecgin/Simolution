package com.simolution.observatory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.RunArtifacts;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunReport;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TickObserver;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;

final class RunSession {

    interface Listener {

        void changed(RunSession session);

        void progress(RunSession session);
    }

    private static final long PROGRESS_NANOS = 250_000_000L;

    private final String dir;
    private final RunConfig config;
    private final RunOrigin origin;
    private final Kernel kernel;
    private final RunArtifacts artifacts;
    private final StructuralStats structure;
    private final DynamicsObserver dynamics = new DynamicsObserver();
    private final LatestFrame latest;
    private final TickObserver[] observers;
    private final Listener listener;
    private volatile RunState state = RunState.QUEUED;
    private volatile int tick;
    private volatile boolean pauseRequested;
    private volatile boolean stopRequested;
    private long lastProgressNanos;

    RunSession(final String dir, final RunConfig config, final RunOrigin origin, final Kernel kernel,
            final RunArtifacts artifacts, final StructuralStats structure, final Listener listener) {
        this.dir = dir;
        this.config = config;
        this.origin = origin;
        this.kernel = kernel;
        this.artifacts = artifacts;
        this.structure = structure;
        this.listener = listener;
        this.latest = new LatestFrame(kernel.config());
        final List<TickObserver> all = new ArrayList<>();
        all.add(dynamics);
        all.add(latest);
        all.addAll(artifacts.observers());
        this.observers = all.toArray(TickObserver[]::new);
        this.tick = kernel.currentTick();
    }

    String dir() {
        return dir;
    }

    RunOrigin origin() {
        return origin;
    }

    RunState state() {
        return state;
    }

    int tick() {
        return tick;
    }

    int targetTicks() {
        return config.ticks();
    }

    LatestFrame latest() {
        return latest;
    }

    boolean done() {
        return state.ended();
    }

    void requestPause() {
        pauseRequested = true;
    }

    void requestStop() {
        stopRequested = true;
    }

    void become(final RunState next) throws IOException {
        state = next;
        artifacts.status(next);
        listener.changed(this);
    }

    void run() {
        try {
            become(RunState.RUNNING);
            while (kernel.currentTick() < config.ticks()) {
                kernel.tick();
                final WorldState snapshot = kernel.snapshot();
                for (TickObserver observer : observers) {
                    observer.observe(snapshot);
                }
                tick = kernel.currentTick();
                progress();
                if (RunState.endOf(snapshot) == RunState.EXTINCT) {
                    end(RunState.EXTINCT);
                    return;
                }
                if (stopRequested) {
                    end(RunState.STOPPED);
                    return;
                }
                if (pauseRequested) {
                    pauseRequested = false;
                    latest.publish(snapshot);
                    become(RunState.PAUSED);
                    return;
                }
            }
            end(RunState.FINISHED);
        } catch (IOException | RuntimeException e) {
            fail(e);
        }
    }

    void end(final RunState finalState) throws IOException {
        latest.publish(kernel.snapshot());
        final long digest = kernel.stateDigest();
        final String report = RunReport.render(config, origin, structure, dynamics.summarize(digest));
        artifacts.finish(finalState, digest, report);
        kernel.close();
        state = finalState;
        listener.changed(this);
    }

    void abandon() throws IOException {
        artifacts.close();
        kernel.close();
    }

    private void fail(final Exception e) {
        try {
            artifacts.fail(String.valueOf(e.getMessage()));
        } catch (IOException | RuntimeException suppressed) {
            e.addSuppressed(suppressed);
        }
        kernel.close();
        state = RunState.FAILED;
        listener.changed(this);
    }

    private void progress() {
        final long now = System.nanoTime();
        if (now - lastProgressNanos >= PROGRESS_NANOS) {
            lastProgressNanos = now;
            listener.progress(this);
        }
    }
}
