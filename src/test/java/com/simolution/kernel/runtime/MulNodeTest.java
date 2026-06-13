package com.simolution.kernel.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;

class MulNodeTest {

    private static final int MUL_IDX = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;

    private static double weight(short raw) {
        return raw * KernelConfig.WEIGHT_MULTIPLIER;
    }

    private static int constToMul(short raw) {
        return GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                          .toInternal(NodeLayout.Internal.MUL)
                          .weightRaw(raw)
                          .build();
    }

    private static double mulOutputAfterTwoTicks(int... genome) {
        Kernel kernel = new Kernel(new int[][] {genome}, 1, 32);
        kernel.tick();
        kernel.tick();
        return kernel.snapshot().outputs[MUL_IDX];
    }

    @Test
    void twoInputsMultiply() {
        // arrange
        short rawA = 8192;
        short rawB = -4096;

        // act
        double output = mulOutputAfterTwoTicks(constToMul(rawA), constToMul(rawB));

        // assert
        assertEquals(weight(rawA) * weight(rawB), output, 0.0);
    }

    @Test
    void singleInputPassesThrough() {
        // arrange
        short raw = 4096;

        // act
        double output = mulOutputAfterTwoTicks(constToMul(raw));

        // assert
        assertEquals(weight(raw), output, 0.0);
    }

    @Test
    void noInputOutputsZero() {
        // act
        double output = mulOutputAfterTwoTicks(
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw((short) 8192)
                           .build());

        // assert
        assertEquals(0.0, output, 0.0);
    }

    @Test
    void threeInputsUseTwoStrongestByMagnitude() {
        // arrange
        short rawSmall = 1024;
        short rawBig = -16384;
        short rawMid = 8192;

        // act
        double output = mulOutputAfterTwoTicks(
                constToMul(rawSmall), constToMul(rawBig), constToMul(rawMid));

        // assert
        assertEquals(weight(rawBig) * weight(rawMid), output, 0.0);
    }

    @Test
    void tiesResolveToEarlierConnection() {
        // arrange
        short rawPositive = 8192;
        short rawNegative = -8192;

        // act
        double output = mulOutputAfterTwoTicks(
                constToMul(rawPositive), constToMul(rawNegative), constToMul(rawPositive));

        // assert
        assertEquals(weight(rawPositive) * weight(rawNegative), output, 0.0);
    }

    @Test
    void specSliceFeedbackLoopStaysAlive() {
        // arrange
        short unit = 8192;
        int[] specSliceGenome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(unit)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(unit)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.MUL)
                           .weightRaw(unit)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.MUL)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(unit)
                           .build()
        };
        Kernel kernel = new Kernel(new int[][] {specSliceGenome}, 1, 32);
        int addIdx = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD;

        // act
        double previousAdd = 0.0;
        boolean mulEverNonZero = false;
        boolean addGrew = false;
        for (int i = 0; i < 20; i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            if (snapshot.outputs[MUL_IDX] != 0.0) {
                mulEverNonZero = true;
            }
            if (snapshot.outputs[addIdx] > previousAdd) {
                addGrew = true;
            }
            previousAdd = snapshot.outputs[addIdx];
        }

        // assert
        assertTrue(mulEverNonZero, "spec slice loop must carry signal through MUL");
        assertTrue(addGrew, "feedback through MUL must feed ADD");
    }
}
