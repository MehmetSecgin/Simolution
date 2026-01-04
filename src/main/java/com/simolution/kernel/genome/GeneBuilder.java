package com.simolution.kernel.genome;

import com.simolution.kernel.layout.NodeType;

/**
 * Human-facing helper for constructing genes safely and readably.
 * <p>
 * This is ONLY for manual construction / testing / debugging.
 * Random genome generation remains the primary evolutionary mechanism.
 * <p>
 * Encodes exactly the same 32-bit gene format used everywhere else.
 */
public final class GeneBuilder {

    private GeneBuilder() {}

    public static SourceStage fromSensor(int sensorTypeId) {
        return new SourceStage(NodeType.SENSOR, sensorTypeId);
    }

    public static SourceStage fromInternal(int internalTypeId) {
        return new SourceStage(NodeType.INTERNAL, internalTypeId);
    }

    public static final class SourceStage {

        private final NodeType sourceType;
        private final int sourceId;

        private SourceStage(NodeType sourceType, int sourceId) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
        }

        public DestinationStage toInternal(int internalTypeId) {
            return new DestinationStage(
                    sourceType, sourceId,
                    NodeType.INTERNAL, internalTypeId
            );
        }

        public DestinationStage toAction(int actionTypeId) {
            return new DestinationStage(
                    sourceType, sourceId,
                    NodeType.ACTION, actionTypeId
            );
        }

    }

    public static final class DestinationStage {

        private final NodeType sourceType;
        private final int sourceId;
        private final NodeType destinationType;
        private final int destinationId;

        private DestinationStage(
                NodeType sourceType,
                int sourceId,
                NodeType destinationType,
                int destinationId
        ) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.destinationType = destinationType;
            this.destinationId = destinationId;
        }

        public WeightStage weightRaw(short weightRaw) {
            return new WeightStage(
                    sourceType, sourceId,
                    destinationType, destinationId,
                    weightRaw
            );
        }

    }

    public static final class WeightStage {

        private final NodeType sourceType;
        private final int sourceId;
        private final NodeType destinationType;
        private final int destinationId;
        private final short weightRaw;

        private WeightStage(
                NodeType sourceType,
                int sourceId,
                NodeType destinationType,
                int destinationId,
                short weightRaw
        ) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.destinationType = destinationType;
            this.destinationId = destinationId;
            this.weightRaw = weightRaw;
        }

        /**
         * Builds the final 32-bit gene.
         */
        public int build() {
            int raw = 0;

            // Source
            raw |= (sourceType == NodeType.INTERNAL ? 1 : 0) << 31;
            raw |= (sourceId & 0x7F) << 24;

            // Destination
            raw |= (destinationType == NodeType.ACTION ? 1 : 0) << 23;
            raw |= (destinationId & 0x7F) << 16;

            // Weight
            raw |= (weightRaw & 0xFFFF);

            return raw;
        }

    }

}