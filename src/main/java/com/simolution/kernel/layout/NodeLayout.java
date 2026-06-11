package com.simolution.kernel.layout;

import com.simolution.kernel.config.KernelConfig;

public final class NodeLayout {

    public static final class Sensor {

        public static final int CONST = 0;
        public static final int RAND = 1;

        public static final int MEANINGFUL_COUNT = 2;

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + KernelConfig.SENSOR_JUNK_COUNT;

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final class Internal {

        public static final int ADD = 0;
        public static final int MUL = 1;
        public static final int CLAMP = 2;
        public static final int DELAY = 3;
        public static final int THRESH = 4;

        public static final int MEANINGFUL_COUNT = 5;

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + KernelConfig.INTERNAL_JUNK_COUNT;

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final class Action {

        public static final int Y = 0;

        public static final int MEANINGFUL_COUNT = 1;

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + KernelConfig.ACTION_JUNK_COUNT;

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final int SENSOR_OFFSET = 0;
    public static final int INTERNAL_OFFSET = SENSOR_OFFSET + Sensor.COUNT;
    public static final int ACTION_OFFSET = INTERNAL_OFFSET + Internal.COUNT;

    public static final int TOTAL =
            Sensor.COUNT + Internal.COUNT + Action.COUNT;

    /**
     * True if the unit-local index refers to a meaningful node — one the
     * kernel actually evaluates. Junk nodes pad each block's tail; their
     * outputs stay 0 forever, so signals into them vanish (junk sinks)
     * and signals from them are always zero.
     */
    public static boolean isMeaningful(final int localIndex) {
        if (localIndex < INTERNAL_OFFSET) {
            return localIndex - SENSOR_OFFSET < Sensor.MEANINGFUL_COUNT;
        }
        if (localIndex < ACTION_OFFSET) {
            return localIndex - INTERNAL_OFFSET < Internal.MEANINGFUL_COUNT;
        }
        return localIndex - ACTION_OFFSET < Action.MEANINGFUL_COUNT;
    }

    private NodeLayout() {}

}