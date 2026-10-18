package com.simolution.observatory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.run.RunDirectoryReader;

public class Seeker implements AutoCloseable {

    private record Viewer(
            Path holder,
            Kernel kernel
    ) {}

    private final int threads;
    private final int capacity;
    private final List<Viewer> viewers = new ArrayList<>();

    public Seeker(final int threads, final int capacity) {
        this.threads = threads;
        this.capacity = capacity;
    }

    public synchronized <R> R at(final Path runDir, final int tick, final Function<Kernel, R> read)
            throws IOException {
        final Path holder = RunDirectoryReader.holderOf(runDir, tick);
        final int checkpoint = RunDirectoryReader.checkpointTickAtOrBefore(holder, tick);
        Viewer viewer = null;
        for (Viewer candidate : viewers) {
            final int at = candidate.kernel().currentTick();
            if (candidate.holder().equals(holder) && at <= tick && at >= checkpoint
                    && (viewer == null || at > viewer.kernel().currentTick())) {
                viewer = candidate;
            }
        }
        if (viewer == null) {
            viewer = new Viewer(holder, RunDirectoryReader.seek(holder, tick, threads));
        } else {
            viewers.remove(viewer);
            viewer.kernel().step(tick - viewer.kernel().currentTick());
        }
        viewers.addFirst(viewer);
        while (viewers.size() > capacity) {
            viewers.removeLast().kernel().close();
        }
        return read.apply(viewer.kernel());
    }

    public synchronized void forget(final Path runDir) {
        viewers.removeIf(viewer -> {
            final boolean stale = viewer.holder().equals(runDir);
            if (stale) {
                viewer.kernel().close();
            }
            return stale;
        });
    }

    @Override
    public synchronized void close() {
        viewers.forEach(viewer -> viewer.kernel().close());
        viewers.clear();
    }
}
