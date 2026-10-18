package com.simolution.sim.run;

public record RunOrigin(
        String name,
        String parent,
        Integer branchTick,
        String twinOf
) {

    public static RunOrigin named(final String name) {
        return new RunOrigin(name, null, null, null);
    }
}
