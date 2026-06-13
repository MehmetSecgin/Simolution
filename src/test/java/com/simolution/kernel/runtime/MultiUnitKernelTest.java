package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;

class MultiUnitKernelTest {

    private static final int[] FEEDBACK_GENOME = {
            GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 2048)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                       .toInternal(NodeLayout.Internal.DELAY)
                       .weightRaw((short) 512)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 512)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                       .toAction(NodeLayout.Action.Y)
                       .weightRaw((short) 512)
                       .build()
    };

    private static final int[] EMPTY_GENOME = {};

    @Test
    void unitsDoNotLeakSignalsIntoEachOther() {
        // arrange
        Kernel kernel = new Kernel(
                new int[][] {EMPTY_GENOME, FEEDBACK_GENOME, EMPTY_GENOME}, 2, 32, new int[] {0, 1, 2});
        int addIdx = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
        int actionIdx = NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;

        // act
        for (int tick = 0; tick < 10; tick++) {
            kernel.tick();
        }
        KernelSnapshot snapshot = kernel.snapshot();

        // assert
        for (int unit : new int[] {0, 2}) {
            int base = unit * NodeLayout.TOTAL;
            assertEquals(0.0, snapshot.outputs[base + addIdx], 0.0, "ADD of empty unit " + unit);
            assertEquals(0.0, snapshot.outputs[base + actionIdx], 0.0, "ACTION of empty unit " + unit);
        }
        int activeBase = NodeLayout.TOTAL;
        assertNotEquals(0.0, snapshot.outputs[activeBase + addIdx]);
    }

    @Test
    void unitZeroIsInvariantUnderPopulationSize() {
        // arrange
        Kernel alone = new Kernel(
                new int[][] {FEEDBACK_GENOME}, 1, 32, new int[] {0});
        Kernel crowded = new Kernel(
                new int[][] {FEEDBACK_GENOME, FEEDBACK_GENOME, EMPTY_GENOME, FEEDBACK_GENOME, EMPTY_GENOME}, 3, 32,
                new int[] {0, 1, 2, 3, 4});

        // act + assert
        for (int tick = 0; tick < 20; tick++) {
            alone.tick();
            crowded.tick();
            double[] aloneOutputs = alone.snapshot().outputs;
            double[] crowdedOutputs = crowded.snapshot().outputs;
            for (int node = 0; node < NodeLayout.TOTAL; node++) {
                assertEquals(aloneOutputs[node], crowdedOutputs[node], 0.0, "node " + node + " at tick " + tick);
            }
        }
    }

    @Test
    void unitsReceiveDistinctRandStreams() {
        // arrange
        Kernel kernel = new Kernel(
                new int[][] {EMPTY_GENOME, EMPTY_GENOME}, 2, 32, new int[] {0, 1});
        int randIdx = NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;

        // act
        kernel.tick();
        KernelSnapshot snapshot = kernel.snapshot();

        // assert
        assertNotEquals(snapshot.outputs[randIdx], snapshot.outputs[NodeLayout.TOTAL + randIdx]);
    }
}
