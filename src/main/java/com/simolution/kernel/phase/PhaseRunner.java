package com.simolution.kernel.phase;

import java.util.List;

/** Executes the declared phase list once, in list order. */
public interface PhaseRunner {

    void runTick(List<Phase> phases);
}
