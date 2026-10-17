package com.simolution.sim;

public record Observation(boolean enabled, int checkpointEvery) {

    public static final Observation OFF = new Observation(false, RunConfig.DEFAULT_CHECKPOINT_EVERY);
}
