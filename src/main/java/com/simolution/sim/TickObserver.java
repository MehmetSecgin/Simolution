package com.simolution.sim;

import java.io.IOException;

import com.simolution.kernel.runtime.KernelSnapshot;

public interface TickObserver {

    void observe(KernelSnapshot snapshot) throws IOException;
}
