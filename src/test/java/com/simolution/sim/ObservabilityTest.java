package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.WorldState;

class ObservabilityTest {

    private static final long SEED = 42L;
    private static final int UNITS = 30;
    private static final int WORLD = 20;
    private static final int MAX_GENES = 16;
    private static final int GENES = 16;
    private static final int TICKS = 600;

    private static int[][] genomes() {
        return GenomeFactory.random(SEED, UNITS, GENES);
    }

    private static int[] founderCells() {
        return Kernel.scatterFounders(SEED, UNITS, WORLD);
    }

    private static final EngineConfig ENGINE = EngineConfig.defaults(0L, WORLD, MAX_GENES);

    private static Kernel freshKernel() {
        return new Kernel(genomes(), founderCells(), ENGINE, 1);
    }

    @Test
    @DisplayName("attaching the event and metrics writers leaves the run's history unchanged")
    void attachingSinkLeavesRunByteIdentical() throws IOException {
        // act
        final long bare = digestOfRun(false);
        final long observed = digestOfRun(true);

        // assert
        assertEquals(bare, observed, "observability sink changed the run");
    }

    private long digestOfRun(final boolean observe) throws IOException {
        final Kernel kernel = freshKernel();
        EventLogWriter eventLog = null;
        MetricsWriter metrics = null;
        if (observe) {
            eventLog = new EventLogWriter(new BufferedWriter(new StringWriter()),
                    WORLD * WORLD, UNITS, founderCells(), ENGINE.founder().initialEnergy());
            metrics = new MetricsWriter(new BufferedWriter(new StringWriter()), UNITS);
        }
        long digest = 0L;
        for (int i = 0; i < TICKS; i++) {
            kernel.tick();
            final WorldState s = kernel.snapshot();
            if (observe) {
                eventLog.observe(s);
                metrics.observe(s);
            }
            digest = fold(digest, s);
        }
        if (observe) {
            eventLog.close();
            metrics.close();
        }
        return digest;
    }

    @SuppressWarnings("checkstyle:FinalParameters")
    private static long fold(long h, final WorldState s) {
        for (double v : s.outputsPrev) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (double v : s.energy) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (double v : s.cellChem) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ Double.doubleToLongBits(v));
        }
        for (int p : s.position) {
            h = com.simolution.kernel.runtime.Noise.mix(h ^ p);
        }
        return h;
    }
}
