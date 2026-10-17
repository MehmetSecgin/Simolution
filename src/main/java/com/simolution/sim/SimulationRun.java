package com.simolution.sim;

import java.io.IOException;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

public final class SimulationRun {

    private SimulationRun() {
    }

    public static RunResult execute(final Kernel kernel, final int ticks, final TickObserver[] observers)
            throws IOException {
        final long start = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            kernel.tick();
            final KernelSnapshot snapshot = kernel.snapshot();
            for (TickObserver observer : observers) {
                observer.observe(snapshot);
            }
        }
        final long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        return new RunResult(kernel.stateDigest(), elapsedMillis);
    }
}
