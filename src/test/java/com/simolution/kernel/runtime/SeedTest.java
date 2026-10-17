package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.sim.GenomeFactory;

class SeedTest {

    private static final int FOUNDERS = 400;
    private static final int WORLD = 40;
    private static final int GENES = 16;
    private static final int TICKS = 300;

    private static Kernel kernel(final long seed, final int[] founderCells) {
        return new Kernel(GenomeFactory.random(11L, FOUNDERS, GENES), founderCells,
                EngineConfig.v5Defaults(seed, WORLD, GENES * 2)
                        .withChemical0Inflow(InflowConfig.field(8.0, InflowConfig.Combine.MAX)), 1);
    }

    @Test
    void seedReachesAllStreams() {
        // arrange
        final int[] cells = Kernel.scatterFounders(0L, FOUNDERS, WORLD);
        final int randIndex = NodeLayout.SENSOR_OFFSET
                + NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE;

        // act
        final Kernel one = kernel(1L, cells);
        final Kernel two = kernel(2L, cells);
        one.tick();
        two.tick();
        final float randOne = one.state().outputsPrev[randIndex];
        final float randTwo = two.state().outputsPrev[randIndex];
        one.step(TICKS - 1);
        two.step(TICKS - 1);

        // assert
        assertNotEquals(randOne, randTwo, "RAND sensor must follow the run seed");
        assertFalse(Arrays.equals(Kernel.scatterFounders(1L, FOUNDERS, WORLD), Kernel.scatterFounders(2L, FOUNDERS, WORLD)),
                "founder placement must follow the run seed");
        assertTrue(one.state().birthsTotal > 0 && two.state().birthsTotal > 0, "both runs reproduce");
        assertFalse(Arrays.equals(one.state().genes, two.state().genes), "child genomes must follow the run seed");
        assertArrayEquals(Kernel.scatterFounders(1L, FOUNDERS, WORLD), Kernel.scatterFounders(1L, FOUNDERS, WORLD));
    }
}
