package com.simolution.kernel.config;

public final class KernelConfig {

    private static final double MAX_WEIGHT = 4.0;
    public static final double WEIGHT_MULTIPLIER = MAX_WEIGHT / Short.MAX_VALUE;

    public static final long RANDOM_SEED = 0L;

    public static final double INITIAL_ENERGY = 1000.0;
    public static final double DECAY_PER_CONNECTION = 0.02;
    public static final double COST_PER_PROPAGATION = 0.06;

    public static final double HARVEST_INTAKE_MAX = 5.0;
    public static final double HARVEST_HALF_SATURATION = 1.0;
    public static final double STORAGE_LEAK_RATE = 0.002;
    public static final double RESOURCE_CAPACITY = 100000.0;
    public static final double RESOURCE_INITIAL = RESOURCE_CAPACITY;
    public static final double RESOURCE_INFLOW = 50.0;

    public static final int SENSOR_JUNK_COUNT = 2;
    public static final int INTERNAL_JUNK_COUNT = 3;
    public static final int ACTION_JUNK_COUNT = 1;

}
