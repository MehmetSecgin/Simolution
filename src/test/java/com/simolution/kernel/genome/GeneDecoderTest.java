package com.simolution.kernel.genome;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

class GeneDecoderTest {

    @Test
    void roundTripsBuilderGene() {
        // arrange
        int gene = GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                              .toInternal(NodeLayout.Internal.DELAY)
                              .weightRaw((short) -16384)
                              .build();

        // act
        CompiledConnection connection = GeneDecoder.decode(gene, 0);

        // assert
        assertEquals(NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.CONST, connection.sourceAbsoluteIndex);
        assertEquals(NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.DELAY, connection.destinationAbsoluteIndex);
        assertEquals(-16384 * KernelConfig.WEIGHT_MULTIPLIER, connection.weight, 0.0);
    }

    @Test
    void wrapsOutOfRangeIdsWithModulo() {
        // arrange
        int gene = GeneBuilder.fromSensor(127)
                              .toInternal(127)
                              .weightRaw((short) 1)
                              .build();

        // act
        CompiledConnection connection = GeneDecoder.decode(gene, 0);

        // assert
        assertEquals(NodeLayout.SENSOR_OFFSET + (127 % NodeLayout.Sensor.TYPE_COUNT), connection.sourceAbsoluteIndex);
        assertEquals(NodeLayout.INTERNAL_OFFSET + (127 % NodeLayout.Internal.TYPE_COUNT), connection.destinationAbsoluteIndex);
    }

    @Test
    void appliesUnitOffsetToAbsoluteIndices() {
        // arrange
        int gene = GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                              .toAction(NodeLayout.Action.Y)
                              .weightRaw((short) 1)
                              .build();
        int unitOffset = NodeLayout.TOTAL * 3;

        // act
        CompiledConnection connection = GeneDecoder.decode(gene, unitOffset);

        // assert
        assertEquals(unitOffset + NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.ADD, connection.sourceAbsoluteIndex);
        assertEquals(unitOffset + NodeLayout.ACTION_OFFSET + NodeLayout.Action.Y, connection.destinationAbsoluteIndex);
    }

    @Test
    void anyRandomIntDecodesToConnection() {
        // arrange
        java.util.Random random = new java.util.Random(42);

        for (int i = 0; i < 10_000; i++) {
            int gene = random.nextInt();

            // act
            CompiledConnection connection = GeneDecoder.decode(gene, 0);

            // assert
            assertEquals(true, connection != null, "gene " + Integer.toHexString(gene) + " must decode");
            assertEquals(true, Math.abs(connection.weight) <= 4.0, "weight bounded for gene " + Integer.toHexString(gene));
        }
    }
}
