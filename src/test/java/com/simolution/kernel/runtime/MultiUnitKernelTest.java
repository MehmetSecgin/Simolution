package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.support.Genes;
import com.simolution.support.Worlds;

class MultiUnitKernelTest {

    private static final int[] FEEDBACK_GENOME = Genes.FEEDBACK_LOOP;

    private static final int[] EMPTY_GENOME = {};

    @Test
    void unitsDoNotLeakSignalsIntoEachOther() {
        // arrange
        final Kernel kernel = Worlds.kernel(2, EMPTY_GENOME, FEEDBACK_GENOME, EMPTY_GENOME);
        final int addIdx = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
        final int actionIdx = NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;

        // act
        kernel.step(10);
        final KernelSnapshot snapshot = kernel.snapshot();

        // assert
        for (int unit : new int[] {0, 2}) {
            final int base = unit * NodeLayout.TOTAL;
            assertEquals(0.0, snapshot.outputs[base + addIdx], 0.0, "ADD of empty unit " + unit);
            assertEquals(0.0, snapshot.outputs[base + actionIdx], 0.0, "ACTION of empty unit " + unit);
        }
        final int activeBase = NodeLayout.TOTAL;
        assertNotEquals(0.0, snapshot.outputs[activeBase + addIdx]);
    }

    @Test
    void unitZeroIsInvariantUnderPopulationSize() {
        // arrange
        final Kernel alone = Worlds.kernel(1, FEEDBACK_GENOME);
        final Kernel crowded =
                Worlds.kernel(3, FEEDBACK_GENOME, FEEDBACK_GENOME, EMPTY_GENOME, FEEDBACK_GENOME, EMPTY_GENOME);

        for (int tick = 0; tick < 20; tick++) {
            // act
            alone.tick();
            crowded.tick();

            // assert
            final float[] aloneOutputs = alone.snapshot().outputs;
            final float[] crowdedOutputs = crowded.snapshot().outputs;
            for (int node = 0; node < NodeLayout.TOTAL; node++) {
                assertEquals(aloneOutputs[node], crowdedOutputs[node], 0.0, "node " + node + " at tick " + tick);
            }
        }
    }

    @Test
    void unitsReceiveDistinctRandStreams() {
        // arrange
        final Kernel kernel = Worlds.kernel(2, EMPTY_GENOME, EMPTY_GENOME);
        final int randIdx = NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;

        // act
        kernel.tick();
        final KernelSnapshot snapshot = kernel.snapshot();

        // assert
        assertNotEquals(snapshot.outputs[randIdx], snapshot.outputs[NodeLayout.TOTAL + randIdx]);
    }
}
