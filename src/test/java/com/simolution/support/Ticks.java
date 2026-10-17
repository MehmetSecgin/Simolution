package com.simolution.support;

import java.util.function.BooleanSupplier;

import com.simolution.kernel.runtime.Kernel;

public final class Ticks {

    private Ticks() {}

    public static int until(final Kernel kernel, final int maxTicks, final BooleanSupplier done) {
        for (int ticks = 1; ticks <= maxTicks; ticks++) {
            kernel.tick();
            if (done.getAsBoolean()) {
                return ticks;
            }
        }
        return -1;
    }
}
