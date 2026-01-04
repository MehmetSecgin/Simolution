package com.simolution.kernel.genome;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.layout.NodeType;

public final class GeneDecoder {

    private GeneDecoder() {}

    public static CompiledConnection decode(int rawGene, int unitNodeOffset) {

        // --- 1. Extract fields ---
        int sourceTypeBit = (rawGene >>> 31) & 0b1;
        int sourceId = (rawGene >>> 24) & 0x7F;

        int destinationTypeBit = (rawGene >>> 23) & 0b1;
        int destinationId = (rawGene >>> 16) & 0x7F;

        short weightRaw = (short) (rawGene & 0xFFFF);

        // --- 2. Resolve types ---
        NodeType sourceType = (sourceTypeBit == 0)
                ? NodeType.SENSOR
                : NodeType.INTERNAL;

        NodeType destinationType = (destinationTypeBit == 0)
                ? NodeType.INTERNAL
                : NodeType.ACTION;

        // --- 3. Validate structure ---
        if (!isValid(sourceType, destinationType)) {
            return null;
        }

        // --- 4. Resolve local indices ---
        int sourceLocalIndex = resolveLocalIndex(sourceType, sourceId);
        int destinationLocalIndex = resolveLocalIndex(destinationType, destinationId);

        // --- 5. Apply unit offset (absolute indices) ---
        int sourceAbsoluteIndex = unitNodeOffset + sourceLocalIndex;
        int destinationAbsoluteIndex = unitNodeOffset + destinationLocalIndex;

        // --- 6. Scale weight ---
        double weight = weightRaw * KernelConfig.WEIGHT_MULTIPLIER;

        return new CompiledConnection(sourceAbsoluteIndex, destinationAbsoluteIndex, weight);
    }

    private static boolean isValid(NodeType sourceType, NodeType destinationType) {
        if (sourceType == NodeType.ACTION) return false;
        if (destinationType == NodeType.SENSOR) return false;
        return true;
    }

    private static int resolveLocalIndex(NodeType type, int rawId) {
        return switch (type) {
            case SENSOR -> {
                int typeIndex = rawId % NodeLayout.Sensor.TYPE_COUNT;
                yield NodeLayout.SENSOR_OFFSET
                      + typeIndex * NodeLayout.Sensor.INSTANCES_PER_TYPE;
            }

            case INTERNAL -> {
                int typeIndex = rawId % NodeLayout.Internal.TYPE_COUNT;
                yield NodeLayout.INTERNAL_OFFSET
                      + typeIndex * NodeLayout.Internal.INSTANCES_PER_TYPE;
            }

            case ACTION -> {
                int typeIndex = rawId % NodeLayout.Action.TYPE_COUNT;
                yield NodeLayout.ACTION_OFFSET
                      + typeIndex * NodeLayout.Action.INSTANCES_PER_TYPE;
            }
        };
    }

}