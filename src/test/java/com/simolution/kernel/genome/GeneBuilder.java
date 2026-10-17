package com.simolution.kernel.genome;

import com.simolution.kernel.layout.NodeSpec.Kind;

public final class GeneBuilder {

    private GeneBuilder() {}

    public static SourceStage fromSensor(final int sensorTypeId) {
        return new SourceStage(Kind.SENSOR, sensorTypeId);
    }

    public static SourceStage fromInternal(final int internalTypeId) {
        return new SourceStage(Kind.INTERNAL, internalTypeId);
    }

    static int pack(
            final boolean sourceInternal,
            final int sourceId,
            final boolean destinationAction,
            final int destinationId,
            final short weightRaw) {
        return (sourceInternal ? 1 : 0) << GeneLayout.SOURCE_KIND_SHIFT
                | (sourceId & GeneLayout.ID_MASK) << GeneLayout.SOURCE_ID_SHIFT
                | (destinationAction ? 1 : 0) << GeneLayout.DESTINATION_KIND_SHIFT
                | (destinationId & GeneLayout.ID_MASK) << GeneLayout.DESTINATION_ID_SHIFT
                | (weightRaw & GeneLayout.WEIGHT_MASK);
    }

    public record SourceStage(
            Kind sourceKind,
            int sourceId
    ) {

        public DestinationStage toInternal(final int internalTypeId) {
            return new DestinationStage(this, Kind.INTERNAL, internalTypeId);
        }

        public DestinationStage toAction(final int actionTypeId) {
            return new DestinationStage(this, Kind.ACTION, actionTypeId);
        }
    }

    public record DestinationStage(
            SourceStage source,
            Kind destinationKind,
            int destinationId
    ) {

        public WeightStage weightRaw(final short weightRaw) {
            return new WeightStage(this, weightRaw);
        }
    }

    public record WeightStage(
            DestinationStage destination,
            short weightRaw
    ) {

        public int build() {
            return pack(
                    destination.source().sourceKind() == Kind.INTERNAL,
                    destination.source().sourceId(),
                    destination.destinationKind() == Kind.ACTION,
                    destination.destinationId(),
                    weightRaw);
        }
    }
}
