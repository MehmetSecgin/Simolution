package com.simolution.kernel.layout;

/**
 * One meaningful node of the substrate: its name and the
 * {@link Role} the engine gives it. {@code chemical} is the chemical index a
 * {@link Role#CHEMICAL_HERE} sensor taps, and {@code -1} for every other role.
 * Internal and action nodes have no role: their position in the block decides what the
 * engine does with them.
 */
public record NodeSpec(
        String name,
        Role role,
        int chemical
) {

    public enum Kind { SENSOR, INTERNAL, ACTION }

    public enum Role {
        CONST, RAND, CHEMICAL_HERE, SELF_ENERGY, SELF_MASS
    }

    public static NodeSpec node(final String name, final Role role) {
        return new NodeSpec(name, role, -1);
    }

    public static NodeSpec chemicalSensor(final String name, final int chemical) {
        return new NodeSpec(name, Role.CHEMICAL_HERE, chemical);
    }

    public static NodeSpec named(final String name) {
        return new NodeSpec(name, null, -1);
    }

}
