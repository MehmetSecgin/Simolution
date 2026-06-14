package com.simolution.kernel.config;

public final class KernelConfig {

    private static final double MAX_WEIGHT = 4.0;
    public static final double WEIGHT_MULTIPLIER = MAX_WEIGHT / Short.MAX_VALUE;

    public static final long RANDOM_SEED = 0L;

    public static final double INITIAL_ENERGY = 1000.0;
    public static final double BASAL_COST = 0.5;
    public static final double COST_PER_PROPAGATION = 0.06;

    public static final double HARVEST_CAPACITY_PER_CONNECTION = 1.5;
    public static final double HARVEST_HALF_SATURATION = 1.0;
    public static final double STORAGE_LEAK_RATE = 0.002;

    public static final double CELL_CAPACITY = 100.0;
    public static final double CELL_INITIAL = CELL_CAPACITY;
    public static final double CELL_INFLOW = 1.0;
    public static final double DIFFUSION_RATE = 0.1;

    public static final double MOVE_COST = 0.2;
    public static final double MOVE_DEADZONE = 0.1;

    public static final double SELF_ENERGY_SCALE = 2000.0;

    public static final double REPRODUCE_MAX = 200.0;
    public static final double REPRODUCE_HALF_SATURATION = 1.0;
    public static final double REPRODUCE_YIELD = 0.7;
    public static final double BUILD_COST = 10.0;
    public static final double BUILD_COST_PER_GENE = 0.25;
    public static final double MUTATION_RATE_WEIGHT = 0.00025;
    public static final double MUTATION_RATE_STRUCT = 0.00005;
    public static final double INDEL_RATE_DUP = 0.001;
    public static final double INDEL_RATE_DEL = 0.001;
    public static final double AGING_COST = 0.0005;

    public static final int SENSOR_JUNK_COUNT = 2;
    public static final int INTERNAL_JUNK_COUNT = 3;
    public static final int ACTION_JUNK_COUNT = 1;

}
