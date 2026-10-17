package com.simolution.kernel.phase;

import com.simolution.kernel.state.WorldState;

/** Phase 4: flips the double-buffered node outputs so this tick's results become the previous buffer. */
public class SwapPhase implements Phase {

    private final WorldState state;

    public SwapPhase(final WorldState state) {
        this.state = state;
    }

    @Override
    public String name() {
        return "swap";
    }

    @Override
    public Shape shape() {
        return Shape.SERIAL;
    }

    @Override
    public int extent() {
        return 1;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        state.swapOutputs();
    }
}
