package com.simolution.sim.run;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import com.simolution.kernel.state.WorldState;

public enum RunState {
    QUEUED, RUNNING, PAUSED, FINISHED, EXTINCT, STOPPED, FAILED;

    @JsonValue
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean ended() {
        return this != QUEUED && this != RUNNING && this != PAUSED;
    }

    public static RunState endOf(final WorldState state) {
        for (double energy : state.energy) {
            if (energy > 0.0) {
                return FINISHED;
            }
        }
        return EXTINCT;
    }
}
