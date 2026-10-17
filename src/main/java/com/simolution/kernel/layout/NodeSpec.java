package com.simolution.kernel.layout;

/**
 * One meaningful node of the substrate: its name and the
 * {@link Role} the engine gives it. {@code chemical} is the chemical index a
 * {@link Role#CHEMICAL_HERE} sensor taps, and {@code -1} for every other role.
 * Action nodes are {@link Role#PASSTHROUGH} (output = accumulator); their meaning
 * lives in the settle phases that read them.
 */
public record NodeSpec(
        String name,
        Role role,
        int chemical
) {

    public enum Kind { SENSOR, INTERNAL, ACTION }

    public enum Role {
        CONST, RAND, CHEMICAL_HERE, SELF_ENERGY, SELF_MASS,
        ADD, MUL, CLAMP, DELAY, THRESH,
        PASSTHROUGH
    }

    public static NodeSpec node(final String name, final Role role) {
        return new NodeSpec(name, role, -1);
    }

    public static NodeSpec chemicalSensor(final String name, final int chemical) {
        return new NodeSpec(name, Role.CHEMICAL_HERE, chemical);
    }

    public static NodeSpec action(final String name) {
        return new NodeSpec(name, Role.PASSTHROUGH, -1);
    }

}
