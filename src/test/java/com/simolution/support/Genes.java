package com.simolution.support;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GeneDecoder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.sim.GenomeFactory;

public final class Genes {

    public static final int[] FEEDBACK_LOOP = GenomeFactory.demo();

    private Genes() {}

    public static int sensorToInternal(final int sensor, final int internal, final int weightRaw) {
        return GeneBuilder.fromSensor(sensor).toInternal(internal).weightRaw((short) weightRaw).build();
    }

    public static int sensorToAction(final int sensor, final int action, final int weightRaw) {
        return GeneBuilder.fromSensor(sensor).toAction(action).weightRaw((short) weightRaw).build();
    }

    public static int internalToInternal(final int source, final int destination, final int weightRaw) {
        return GeneBuilder.fromInternal(source).toInternal(destination).weightRaw((short) weightRaw).build();
    }

    public static int internalToAction(final int internal, final int action, final int weightRaw) {
        return GeneBuilder.fromInternal(internal).toAction(action).weightRaw((short) weightRaw).build();
    }

    public static int constTo(final int action, final int weightRaw) {
        return sensorToAction(NodeLayout.Sensor.CONST, action, weightRaw);
    }

    public static double weight(final int raw) {
        return raw * GeneDecoder.WEIGHT_PER_RAW_UNIT;
    }
}
