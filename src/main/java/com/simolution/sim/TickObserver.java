package com.simolution.sim;

import java.io.IOException;

import com.simolution.kernel.state.WorldState;

public interface TickObserver {

    void observe(WorldState snapshot) throws IOException;
}
