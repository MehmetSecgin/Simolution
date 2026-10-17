package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.support.Genes;
import com.simolution.support.Worlds;

class MulNodeTest {

    private static final int MUL_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;

    private static final int ADD_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;

    private static int constToMul(int raw) {
        return Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.MUL, raw);
    }

    private static int junkToMul(int raw) {
        return Genes.sensorToInternal(NodeLayout.Sensor.MEANINGFUL_COUNT, NodeLayout.Internal.MUL, raw);
    }

    private static double mulOutputAfterTwoTicks(int... genome) {
        Kernel kernel = Worlds.kernel(1, genome);
        kernel.step(2);
        return kernel.snapshot().outputs[MUL_IDX];
    }

    @Test
    void twoInputsMultiply() {
        // arrange
        int rawA = 8192;
        int rawB = -4096;

        // act
        double output = mulOutputAfterTwoTicks(constToMul(rawA), constToMul(rawB));

        // assert
        assertEquals(Genes.weight(rawA) * Genes.weight(rawB), output, 1.0e-6);
    }

    @Test
    void singleInputPassesThrough() {
        // arrange
        int raw = 4096;

        // act
        double output = mulOutputAfterTwoTicks(constToMul(raw));

        // assert
        assertEquals(Genes.weight(raw), output, 1.0e-6);
    }

    @Test
    void noInputOutputsZero() {
        // act
        double output = mulOutputAfterTwoTicks(
                Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.ADD, 8192));

        // assert
        assertEquals(0.0, output, 0.0);
    }

    @Test
    void threeInputsUseTwoStrongestByMagnitude() {
        // arrange
        int rawSmall = 1024;
        int rawBig = -16384;
        int rawMid = 8192;

        // act
        double output = mulOutputAfterTwoTicks(
                constToMul(rawSmall), constToMul(rawBig), constToMul(rawMid));

        // assert
        assertEquals(Genes.weight(rawBig) * Genes.weight(rawMid), output, 1.0e-6);
    }

    @Test
    void tiesResolveToEarlierConnection() {
        // arrange
        int rawPositive = 8192;
        int rawNegative = -8192;

        // act
        double output = mulOutputAfterTwoTicks(
                constToMul(rawPositive), constToMul(rawNegative), constToMul(rawPositive));

        // assert
        assertEquals(Genes.weight(rawPositive) * Genes.weight(rawNegative), output, 1.0e-6);
    }

    @Test
    void realPlusJunkInputCountsJunkInDegreeAndOutputsZero() {
        // arrange
        int[] genome = {constToMul(8192), junkToMul(8192)};
        Kernel kernel = Worlds.kernel(1, genome);

        // act
        kernel.step(2);

        // assert
        assertEquals(2, kernel.snapshot().state.mulInDegree[0]);
        assertEquals(1, kernel.state().mulEnd[0] - kernel.state().sumStart[0]);
        assertEquals(0.0, kernel.snapshot().outputs[MUL_IDX], 0.0);
    }

    @Test
    void junkOnlyInputCountsInDegreeAndOutputsZero() {
        // arrange
        int[] genome = {junkToMul(8192)};
        Kernel kernel = Worlds.kernel(1, genome);

        // act
        kernel.step(2);

        // assert
        assertEquals(1, kernel.snapshot().state.mulInDegree[0]);
        assertEquals(0, kernel.state().mulEnd[0] - kernel.state().sumStart[0]);
        assertEquals(0.0, kernel.snapshot().outputs[MUL_IDX], 0.0);
    }

    @Test
    void feedbackThroughMulKeepsSignalAlive() {
        // arrange
        int unit = 8192;
        int[] feedbackThroughMul = {
                Genes.sensorToInternal(NodeLayout.Sensor.CONST, NodeLayout.Internal.ADD, unit),
                Genes.internalToInternal(NodeLayout.Internal.ADD, NodeLayout.Internal.DELAY, unit),
                Genes.internalToInternal(NodeLayout.Internal.DELAY, NodeLayout.Internal.MUL, unit),
                Genes.internalToInternal(NodeLayout.Internal.MUL, NodeLayout.Internal.ADD, unit)
        };
        Kernel kernel = Worlds.kernel(1, feedbackThroughMul);

        // act
        double previousAdd = 0.0;
        boolean mulEverNonZero = false;
        boolean addGrew = false;
        for (int i = 0; i < 20; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            mulEverNonZero |= snapshot.outputs[MUL_IDX] != 0.0;
            addGrew |= snapshot.outputs[ADD_IDX] > previousAdd;
            previousAdd = snapshot.outputs[ADD_IDX];
        }

        // assert
        assertTrue(mulEverNonZero, "the loop must carry signal through MUL");
        assertTrue(addGrew, "feedback through MUL must feed ADD");
    }
}
