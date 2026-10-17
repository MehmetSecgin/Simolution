package com.simolution.kernel.genome;

import com.simolution.kernel.layout.NodeSpec.Kind;

public final class GeneBuilder {

    private GeneBuilder() {}

    public static SourceStage fromSensor(int sensorTypeId) {
        return new SourceStage(Kind.SENSOR, sensorTypeId);
    }

    public static SourceStage fromInternal(int internalTypeId) {
        return new SourceStage(Kind.INTERNAL, internalTypeId);
    }

    public record SourceStage(Kind sourceKind, int sourceId) {

        public DestinationStage toInternal(int internalTypeId) {
            return new DestinationStage(this, Kind.INTERNAL, internalTypeId);
        }

        public DestinationStage toAction(int actionTypeId) {
            return new DestinationStage(this, Kind.ACTION, actionTypeId);
        }
    }

    public record DestinationStage(SourceStage source, Kind destinationKind, int destinationId) {

        public WeightStage weightRaw(short weightRaw) {
            return new WeightStage(this, weightRaw);
        }
    }

    public record WeightStage(DestinationStage destination, short weightRaw) {

        public int build() {
            return GeneLayout.pack(
                    destination.source().sourceKind() == Kind.INTERNAL,
                    destination.source().sourceId(),
                    destination.destinationKind() == Kind.ACTION,
                    destination.destinationId(),
                    weightRaw);
        }
    }
}
