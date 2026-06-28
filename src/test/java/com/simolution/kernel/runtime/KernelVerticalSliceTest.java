package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;

class KernelVerticalSliceTest {

    private static final short W_CONST_TO_ADD = 2048;
    private static final short W_ADD_TO_DELAY = 512;
    private static final short W_DELAY_TO_ADD = 512;
    private static final short W_ADD_TO_ACTION = 512;

    private static final int CONST_IDX = NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.CONST;
    private static final int ADD_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
    private static final int DELAY_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.DELAY;
    private static final int ACTION_IDX = NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;

    @Test
    void matchesSpecReferenceModelForTenTicks() {
        // arrange
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(W_CONST_TO_ADD)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(W_ADD_TO_DELAY)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(W_DELAY_TO_ADD)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw(W_ADD_TO_ACTION)
                           .build()
        };
        Kernel kernel = new Kernel(new int[][] {genome}, 1, 32, new int[] {0});

        double wConstToAdd = W_CONST_TO_ADD * KernelConfig.WEIGHT_MULTIPLIER;
        double wAddToDelay = W_ADD_TO_DELAY * KernelConfig.WEIGHT_MULTIPLIER;
        double wDelayToAdd = W_DELAY_TO_ADD * KernelConfig.WEIGHT_MULTIPLIER;
        double wAddToAction = W_ADD_TO_ACTION * KernelConfig.WEIGHT_MULTIPLIER;

        float constPrev = 0.0f;
        float addPrev = 0.0f;
        float delayPrev = 0.0f;
        float delayMemory = 0.0f;

        for (int tick = 1; tick <= 10; tick++) {
            // act
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();

            float addAccumulator = (float) (constPrev * wConstToAdd) + (float) (delayPrev * wDelayToAdd);
            float delayAccumulator = (float) (addPrev * wAddToDelay);
            float actionAccumulator = (float) (addPrev * wAddToAction);

            float constNext = 1.0f;
            float addNext = addAccumulator;
            float delayNext = delayMemory;
            delayMemory = delayAccumulator;
            float actionNext = actionAccumulator;

            // assert
            assertEquals(tick, snapshot.tick);
            assertEquals(constNext, snapshot.outputs[CONST_IDX], 0.0f, "CONST at tick " + tick);
            assertEquals(addNext, snapshot.outputs[ADD_IDX], 0.0f, "ADD at tick " + tick);
            assertEquals(delayNext, snapshot.outputs[DELAY_IDX], 0.0f, "DELAY at tick " + tick);
            assertEquals(actionNext, snapshot.outputs[ACTION_IDX], 0.0f, "ACTION at tick " + tick);
            assertEquals(delayMemory, snapshot.delayMemory[DELAY_IDX], 0.0f, "DELAY memory at tick " + tick);

            constPrev = constNext;
            addPrev = addNext;
            delayPrev = delayNext;
        }
    }

    @Test
    void isDeterministicAcrossRuns() {
        // arrange
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.RAND)
                           .toInternal(NodeLayout.Internal.CLAMP)
                           .weightRaw((short) 16384)
                           .build()
        };
        Kernel first = new Kernel(new int[][] {genome}, 1, 32, new int[] {0});
        Kernel second = new Kernel(new int[][] {genome}, 1, 32, new int[] {0});

        // act + assert
        for (int tick = 0; tick < 50; tick++) {
            first.tick();
            second.tick();
            float[] firstOutputs = first.snapshot().outputs;
            float[] secondOutputs = second.snapshot().outputs;
            for (int node = 0; node < firstOutputs.length; node++) {
                assertEquals(firstOutputs[node], secondOutputs[node], 0.0);
            }
        }
    }
}
