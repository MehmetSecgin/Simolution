package com.simolution.sim;

import java.io.IOException;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.WorldState;

public final class SimulationRun {

    private SimulationRun() {
    }

    public static long execute(final Kernel kernel, final int ticks, final TickObserver[] observers)
            throws IOException {
        final long start = System.nanoTime();
        for (int i = 0; i < ticks; i++) {
            kernel.tick();
            final WorldState snapshot = kernel.snapshot();
            for (TickObserver observer : observers) {
                observer.observe(snapshot);
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
