package com.simolution.kernel.genome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Noise;

class GeneDecoderTest {

    @Test
    void roundTripsBuilderGene() {
        // arrange
        final int gene = GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                .toInternal(NodeLayout.Internal.DELAY)
                .weightRaw((short) -16384)
                .build();

        // act
        final CompiledConnection connection = GeneDecoder.decode(gene, 0);

        // assert
        assertEquals(NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.CONST, connection.sourceAbsoluteIndex());
        assertEquals(NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.DELAY, connection.destinationAbsoluteIndex());
        assertEquals(-16384 * GeneDecoder.WEIGHT_PER_RAW_UNIT, connection.weight(), 0.0);
    }

    @Test
    void wrapsOutOfRangeIdsWithModulo() {
        // arrange
        final int gene = GeneBuilder.fromSensor(127)
                .toInternal(127)
                .weightRaw((short) 1)
                .build();

        // act
        final CompiledConnection connection = GeneDecoder.decode(gene, 0);

        // assert
        assertEquals(NodeLayout.SENSOR_OFFSET + (127 % NodeLayout.Sensor.TYPE_COUNT), connection.sourceAbsoluteIndex());
        assertEquals(NodeLayout.INTERNAL_OFFSET + (127 % NodeLayout.Internal.TYPE_COUNT),
                connection.destinationAbsoluteIndex());
    }

    @Test
    void appliesUnitOffsetToAbsoluteIndices() {
        // arrange
        final int gene = GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                .toAction(NodeLayout.Action.Y)
                .weightRaw((short) 1)
                .build();
        final int unitOffset = NodeLayout.TOTAL * 3;

        // act
        final CompiledConnection connection = GeneDecoder.decode(gene, unitOffset);

        // assert
        assertEquals(unitOffset + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD,
                connection.sourceAbsoluteIndex());
        assertEquals(unitOffset + NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y,
                connection.destinationAbsoluteIndex());
    }

    @Test
    void anyRandomIntDecodesToConnection() {
        // arrange
        final java.util.Random random = new java.util.Random(42);

        for (int i = 0; i < 10_000; i++) {
            final int gene = random.nextInt();

            // act
            final CompiledConnection connection = GeneDecoder.decode(gene, 0);

            // assert
            assertTrue(Math.abs(connection.weight()) <= 32768 * GeneDecoder.WEIGHT_PER_RAW_UNIT,
                    "weight bounded for gene " + Integer.toHexString(gene));
        }
    }

    @Test
    void primitiveAccessorsMatchIndependentReference() {
        for (int i = 0; i < 10_000; i++) {
            // arrange
            final int gene = (int) Noise.mix(i * Noise.GOLDEN_GAMMA);
            final int srcType = (gene >>> 31) & 0x1;
            final int srcId = (gene >>> 24) & 0x7F;
            final int dstType = (gene >>> 23) & 0x1;
            final int dstId = (gene >>> 16) & 0x7F;
            final int low16 = gene & 0xFFFF;
            final int expectedSource = srcType == 0
                    ? NodeLayout.SENSOR_OFFSET
                            + (srcId % NodeLayout.Sensor.TYPE_COUNT)
                    : NodeLayout.INTERNAL_OFFSET
                            + (srcId % NodeLayout.Internal.TYPE_COUNT);
            final int expectedDestination = dstType == 0
                    ? NodeLayout.INTERNAL_OFFSET
                            + (dstId % NodeLayout.Internal.TYPE_COUNT)
                    : NodeLayout.ACTION_OFFSET
                            + (dstId % NodeLayout.Action.TYPE_COUNT);
            final double expectedWeight = (short) low16 * (4.0 / 32767);

            // act
            final int source = GeneDecoder.sourceNodeIndex(gene);
            final int destination = GeneDecoder.destinationNodeIndex(gene);
            final double weight = GeneDecoder.weight(gene);

            // assert
            assertEquals(expectedSource, source);
            assertEquals(expectedDestination, destination);
            assertEquals(expectedWeight, weight, 0.0);
        }
    }

    @Test
    void packThenReadReturnsEveryField() {
        // arrange
        final Random random = new Random(7);

        for (int i = 0; i < 10_000; i++) {
            final boolean sourceInternal = random.nextBoolean();
            final int sourceId = random.nextInt();
            final boolean destinationAction = random.nextBoolean();
            final int destinationId = random.nextInt();
            final short weightRaw = (short) random.nextInt();

            // act
            final int gene = GeneBuilder.pack(sourceInternal, sourceId, destinationAction, destinationId, weightRaw);

            // assert
            assertEquals(sourceInternal, GeneDecoder.sourceIsInternal(gene));
            assertEquals(sourceId & GeneDecoder.ID_MASK, GeneDecoder.sourceId(gene));
            assertEquals(destinationAction, GeneDecoder.destinationIsAction(gene));
            assertEquals(destinationId & GeneDecoder.ID_MASK, GeneDecoder.destinationId(gene));
            assertEquals(weightRaw, GeneDecoder.weightRaw(gene));
        }
    }

    @Test
    void minRawWeightDecodesJustBeyondMinusFour() {
        // arrange
        final int gene = GeneBuilder.pack(false, 0, false, 0, Short.MIN_VALUE);

        // act
        final double weight = GeneDecoder.weight(gene);

        // assert
        assertEquals(-32768 * GeneDecoder.WEIGHT_PER_RAW_UNIT, weight, 0.0);
        assertTrue(weight < -4.0);
    }
}
