package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.Founders;
import com.simolution.sim.RunConfig;
import com.simolution.support.RunDirs;

class SeekerTest {

    @TempDir
    Path runs;

    @Test
    void seeksMatchAStraightRunForwardAndBack() throws IOException {
        // arrange
        final RunConfig config = RunDirs.config(runs.resolve("run"), 600, "--checkpoint-every", "500");
        final Path dir = RunDirs.cliRun(config);
        final int[] ticks = {137, 499, 500, 501, 137};
        final Map<Integer, Long> straight = new HashMap<>();
        try (Kernel kernel = Founders.create(config).newKernel(1)) {
            while (kernel.currentTick() < 501) {
                kernel.tick();
                straight.put(kernel.currentTick(), kernel.stateDigest());
            }
        }

        try (Seeker seeker = new Seeker(2, 4)) {
            for (int tick : ticks) {
                // act
                final long digest = seeker.at(dir, tick, Kernel::stateDigest);

                // assert
                assertEquals(straight.get(tick), digest, "tick " + tick);
            }
        }
    }
}
