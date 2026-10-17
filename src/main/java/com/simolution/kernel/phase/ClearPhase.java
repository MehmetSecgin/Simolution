package com.simolution.kernel.phase;

import java.util.Arrays;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/** Phase 1: zeroes the per-tick accumulators and MUL top-two scratch of each slot. */
public final class ClearPhase implements Phase {

    private final WorldState state;

    public ClearPhase(final WorldState state) {
        this.state = state;
    }

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public Shape shape() {
        return Shape.PER_UNIT;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        Arrays.fill(state.accumulators, from * NodeLayout.TOTAL, to * NodeLayout.TOTAL, 0.0f);
        Arrays.fill(state.mulTop1, from, to, 0.0f);
        Arrays.fill(state.mulTop2, from, to, 0.0f);
    }
}
