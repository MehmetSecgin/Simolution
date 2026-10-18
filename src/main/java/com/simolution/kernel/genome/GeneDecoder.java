package com.simolution.kernel.genome;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class GeneDecoder {

    public static final int SOURCE_KIND_SHIFT = 31;
    public static final int SOURCE_ID_SHIFT = 24;
    public static final int DESTINATION_KIND_SHIFT = 23;
    public static final int DESTINATION_ID_SHIFT = 16;
    public static final int ID_MASK = 0x7F;
    public static final int WEIGHT_MASK = 0xFFFF;

    static boolean sourceIsInternal(final int gene) {
        return (gene >>> SOURCE_KIND_SHIFT) != 0;
    }

    static int sourceId(final int gene) {
        return (gene >>> SOURCE_ID_SHIFT) & ID_MASK;
    }

    static boolean destinationIsAction(final int gene) {
        return ((gene >>> DESTINATION_KIND_SHIFT) & 1) != 0;
    }

    static int destinationId(final int gene) {
        return (gene >>> DESTINATION_ID_SHIFT) & ID_MASK;
    }

    static short weightRaw(final int gene) {
        return (short) (gene & WEIGHT_MASK);
    }

    public static final double NOMINAL_MAX_WEIGHT = 4.0;
    public static final double WEIGHT_PER_RAW_UNIT = NOMINAL_MAX_WEIGHT / Short.MAX_VALUE;

    private GeneDecoder() {}

    public static CompiledConnection decode(final int rawGene, final int unitNodeOffset) {
        return new CompiledConnection(
                unitNodeOffset + sourceNodeIndex(rawGene),
                unitNodeOffset + destinationNodeIndex(rawGene),
                weight(rawGene));
    }

    public static int sourceNodeIndex(final int rawGene) {
        final int id = sourceId(rawGene);
        return sourceIsInternal(rawGene)
                ? nodeIndex(NodeLayout.INTERNAL_OFFSET, id, NodeLayout.Internal.TYPE_COUNT)
                : nodeIndex(NodeLayout.SENSOR_OFFSET, id, NodeLayout.Sensor.TYPE_COUNT);
    }

    public static int destinationNodeIndex(final int rawGene) {
        final int id = destinationId(rawGene);
        return destinationIsAction(rawGene)
                ? nodeIndex(NodeLayout.ACTION_OFFSET, id, NodeLayout.Action.TYPE_COUNT)
                : nodeIndex(NodeLayout.INTERNAL_OFFSET, id, NodeLayout.Internal.TYPE_COUNT);
    }

    public static double weight(final int rawGene) {
        return weightRaw(rawGene) * WEIGHT_PER_RAW_UNIT;
    }

    private static int nodeIndex(final int blockOffset, final int id, final int typeCount) {
        return blockOffset + id % typeCount;
    }
}
