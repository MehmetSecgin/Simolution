package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;
import com.simolution.support.Genes;
import com.simolution.support.Worlds;

class KernelVerticalSliceTest {

    private static final int W_CONST_TO_ADD = 2048;
    private static final int W_ADD_TO_DELAY = 512;
    private static final int W_DELAY_TO_ADD = 512;
    private static final int W_ADD_TO_ACTION = 512;

    private static final int CONST_IDX = NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.CONST;
    private static final int ADD_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;
    private static final int DELAY_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.DELAY;
    private static final int ACTION_IDX = NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y;

    @Test
    void matchesSpecReferenceModelForTenTicks() {
        // arrange
        final int[] genome = {
                Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.ADD, W_CONST_TO_ADD),
                Genes.internalToInternal(NodeLayout.Internal.ADD, NodeLayout.Internal.DELAY, W_ADD_TO_DELAY),
                Genes.internalToInternal(NodeLayout.Internal.DELAY, NodeLayout.Internal.ADD, W_DELAY_TO_ADD),
                Genes.internalToAction(NodeLayout.Internal.ADD, NodeLayout.Action.Y, W_ADD_TO_ACTION)
        };
        final Kernel kernel = Worlds.kernel(1, genome);

        final double wConstToAdd = Genes.weight(W_CONST_TO_ADD);
        final double wAddToDelay = Genes.weight(W_ADD_TO_DELAY);
        final double wDelayToAdd = Genes.weight(W_DELAY_TO_ADD);
        final double wAddToAction = Genes.weight(W_ADD_TO_ACTION);

        float constPrev = 0.0f;
        float addPrev = 0.0f;
        float delayPrev = 0.0f;
        float delayMemory = 0.0f;

        for (int tick = 1; tick <= 10; tick++) {
            // act
            kernel.tick();
            final WorldState snapshot = kernel.snapshot();

            final float addAccumulator = (float) (constPrev * wConstToAdd) + (float) (delayPrev * wDelayToAdd);
            final float delayAccumulator = (float) (addPrev * wAddToDelay);
            final float actionAccumulator = (float) (addPrev * wAddToAction);

            final float constNext = 1.0f;
            final float addNext = addAccumulator;
            final float delayNext = delayMemory;
            delayMemory = delayAccumulator;
            final float actionNext = actionAccumulator;

            // assert
            assertEquals(tick, snapshot.tick);
            assertEquals(constNext, snapshot.outputsPrev[CONST_IDX], 0.0f, "CONST at tick " + tick);
            assertEquals(addNext, snapshot.outputsPrev[ADD_IDX], 0.0f, "ADD at tick " + tick);
            assertEquals(delayNext, snapshot.outputsPrev[DELAY_IDX], 0.0f, "DELAY at tick " + tick);
            assertEquals(actionNext, snapshot.outputsPrev[ACTION_IDX], 0.0f, "ACTION at tick " + tick);
            assertEquals(delayMemory, snapshot.delayMemory[DELAY_IDX], 0.0f, "DELAY memory at tick " + tick);

            constPrev = constNext;
            addPrev = addNext;
            delayPrev = delayNext;
        }
    }
}
