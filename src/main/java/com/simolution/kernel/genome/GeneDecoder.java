package com.simolution.kernel.genome;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

/**
 * Gene layout {@code [SrcType:1 | SrcID:7 | DstType:1 | DstID:7 | Weight:16]}.
 * Ids wrap modulo the block's type count, so every int decodes.
 */
public final class GeneDecoder {

    public static final double MAX_WEIGHT = 4.0;
    public static final double WEIGHT_MULTIPLIER = MAX_WEIGHT / Short.MAX_VALUE;

    private GeneDecoder() {}

    public static CompiledConnection decode(final int rawGene, final int unitNodeOffset) {
        return new CompiledConnection(
                unitNodeOffset + sourceLocal(rawGene),
                unitNodeOffset + destinationLocal(rawGene),
                weight(rawGene));
    }

    public static int sourceLocal(final int rawGene) {
        final int sourceId = (rawGene >>> 24) & 0x7F;
        if ((rawGene >>> 31) == 0) {
            return NodeLayout.SENSOR_OFFSET
                    + (sourceId % NodeLayout.Sensor.TYPE_COUNT) * NodeLayout.Sensor.INSTANCES_PER_TYPE;
        }
        return NodeLayout.INTERNAL_OFFSET
                + (sourceId % NodeLayout.Internal.TYPE_COUNT) * NodeLayout.Internal.INSTANCES_PER_TYPE;
    }

    public static int destinationLocal(final int rawGene) {
        final int destinationId = (rawGene >>> 16) & 0x7F;
        if (((rawGene >>> 23) & 0b1) == 0) {
            return NodeLayout.INTERNAL_OFFSET
                    + (destinationId % NodeLayout.Internal.TYPE_COUNT) * NodeLayout.Internal.INSTANCES_PER_TYPE;
        }
        return NodeLayout.ACTION_OFFSET
                + (destinationId % NodeLayout.Action.TYPE_COUNT) * NodeLayout.Action.INSTANCES_PER_TYPE;
    }

    public static double weight(final int rawGene) {
        return (short) (rawGene & 0xFFFF) * WEIGHT_MULTIPLIER;
    }

}
