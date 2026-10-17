package com.simolution.kernel.genome;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class GeneDecoder {

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
        final int id = GeneLayout.sourceId(rawGene);
        return GeneLayout.sourceIsInternal(rawGene)
                ? nodeIndex(NodeLayout.INTERNAL_OFFSET, id, NodeLayout.Internal.TYPE_COUNT,
                        NodeLayout.Internal.INSTANCES_PER_TYPE)
                : nodeIndex(NodeLayout.SENSOR_OFFSET, id, NodeLayout.Sensor.TYPE_COUNT,
                        NodeLayout.Sensor.INSTANCES_PER_TYPE);
    }

    public static int destinationNodeIndex(final int rawGene) {
        final int id = GeneLayout.destinationId(rawGene);
        return GeneLayout.destinationIsAction(rawGene)
                ? nodeIndex(NodeLayout.ACTION_OFFSET, id, NodeLayout.Action.TYPE_COUNT,
                        NodeLayout.Action.INSTANCES_PER_TYPE)
                : nodeIndex(NodeLayout.INTERNAL_OFFSET, id, NodeLayout.Internal.TYPE_COUNT,
                        NodeLayout.Internal.INSTANCES_PER_TYPE);
    }

    public static double weight(final int rawGene) {
        return GeneLayout.weightRaw(rawGene) * WEIGHT_PER_RAW_UNIT;
    }

    private static int nodeIndex(final int blockOffset, final int id, final int typeCount, final int instancesPerType) {
        return blockOffset + (id % typeCount) * instancesPerType;
    }
}
