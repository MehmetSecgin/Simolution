package com.simolution.kernel.phase;

import java.util.List;

/** Runs every phase on the calling thread as one chunk over its whole extent. */
public final class SerialRunner implements PhaseRunner {

    @Override
    public void runTick(final List<Phase> phases) {
        for (int i = 0; i < phases.size(); i++) {
            final Phase phase = phases.get(i);
            phase.prepare();
            phase.run(0, phase.extent(), 0);
            phase.finish();
        }
    }
}
