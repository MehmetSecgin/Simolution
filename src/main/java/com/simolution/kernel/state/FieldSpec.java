package com.simolution.kernel.state;

/**
 * Declaration of one state array of the {@link WorldState}: its shape, whether it
 * is saved in checkpoints, and how a slot's entries behave at birth and death.
 * <p>
 * {@code array} is the live array at the time {@link WorldState#fields()} was
 * called; double-buffered arrays swap references, so a spec must not be kept
 * across ticks.
 */
public record FieldSpec(String name, Dim dim, Persist persist, OnBirth onBirth, OnDeath onDeath, Object array) {

    /**
     * Shape of the array: per slot, per cell, per slot node ({@code slot * NodeLayout.TOTAL + node}),
     * per chemical and cell ({@code k * cells + cell}), per chemical and slot
     * ({@code k * slots + slot}), per slot gene region ({@code slot * maxGenes + g}), per
     * work chunk ({@link com.simolution.kernel.phase.Chunks}), or a single value.
     */
    public enum Dim { UNIT, CELL, UNIT_NODE, CELL_CHEM, UNIT_CHEM, GENE, CHUNK, SCALAR }

    /**
     * {@code SAVED} is written to checkpoints, {@code DERIVED} is rebuilt from the genes
     * at restore, {@code SCRATCH} is cleared or overwritten before it is read and never saved.
     */
    public enum Persist { SAVED, DERIVED, SCRATCH }

    /** {@code RESET_ZERO} is zeroed by {@link WorldState#resetForBirth}; {@code EXPLICIT} is set by the birth code. */
    public enum OnBirth { RESET_ZERO, EXPLICIT }

    /** {@code ZERO} is zeroed by {@link WorldState#applyDeath}. */
    public enum OnDeath { KEEP, ZERO }
}
