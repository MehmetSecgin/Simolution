package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 5: energy intake, <b>cell-local</b> (contract v3 §4, supersedes v1's shared-reservoir
 * allocation). Each alive unit draws from the cell it occupies ({@code position[slot]}) only,
 * from chemical 0: {@code intake = min(demand, cellResource)} with the unchanged saturating
 * {@code demand} (contract v1 §5). Because cells are exclusive (one unit per cell, contract
 * v3 §2) there is no within-cell contention. The cell is debited by exactly the intake.
 * Intake runs before cost so a unit can pay this tick's metabolism with this tick's harvest;
 * harvest output is read from {@code outputsPrev}, the just-swapped current tick.
 * {@link InflowPhase} then tops the cells up.
 * <p>
 * Shape is {@link Shape#PER_UNIT}: a unit writes only its own energy and its own cell's
 * chemical 0, and no other unit reads that cell in this phase. Nothing is added to a shared
 * total here, so no partials are needed.
 */
public class IntakePhase implements Phase {

    private final WorldState state;
    private final double harvestCapacityPerConnection;
    private final double harvestHalfSaturation;

    public IntakePhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.harvestCapacityPerConnection = config.harvest().capacityPerConnection();
        this.harvestHalfSaturation = config.harvest().halfSaturation();
    }

    @Override
    public String name() {
        return "intake";
    }

    @Override
    public Shape shape() {
        return Shape.PER_UNIT;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final int[] position = state.position;
        final double[] cellChem = state.cellChem;

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double drive = harvestDrive(unit);
            if (drive <= 0.0) {
                continue;
            }
            final int cell = position[unit];
            final double intake = Math.min(drive * StrictMath.sqrt(mass[unit]), cellChem[cell]);
            energy[unit] += intake;
            cellChem[cell] -= intake;
        }
    }

    /**
     * Saturating uptake with an <b>emergent</b> ceiling (contract v1 §5, ADR
     * 0012). Demand is {@code capacity · σ(output)} where:
     * <ul>
     *   <li>{@code capacity = CAPACITY_PER_CONNECTION · transporterCount · mass^α} — the
     *       intake ceiling, set by the unit's structural investment in harvest
     *       wiring (counted once when the slot is compiled) and its size (the
     *       surface-area law, contract v5 §3). Eating capacity is therefore a heritable,
     *       evolvable trait, not a global constant grant. It is bounded by
     *       the genome size, so it cannot diverge.</li>
     *   <li>{@code σ(output) = output / (HALF_SATURATION + output) ∈ [0,1)} — how
     *       far the cell is currently driving its mouth open. Saturating, so a
     *       runaway/divergent signal only approaches the ceiling, never exceeds
     *       it: there is no payoff to exploding a feedback loop into the mouth,
     *       and the demand total stays bounded.</li>
     * </ul>
     * A non-finite output is unreadable garbage, not a harvest action, so it
     * demands nothing; {@code max(0, …)} also means you cannot un-eat.
     * <p>
     * This returns the demand per unit of {@code mass^α}: the caller multiplies by
     * {@code sqrt(mass)} ({@code α = 1/2}, ADR 0052), which is correctly rounded on every machine.
     */
    private double harvestDrive(final int unit) {
        final float[] outputsPrev = state.outputsPrev;
        final int[] harvestConnCount = state.harvestConnCount;

        final int transporters = harvestConnCount[unit];
        if (transporters == 0) {
            return 0.0;
        }
        final int harvestIdx = NodeLayout.actionIndex(unit, NodeLayout.Action.HARVEST);
        final double output = outputsPrev[harvestIdx];
        if (!Double.isFinite(output) || output <= 0.0) {
            return 0.0;
        }
        final double harvestCapacity = harvestCapacityPerConnection * transporters;
        // capacity · output/(HALF+output), rearranged to avoid overflow when
        // output is a huge (divergent) finite value.
        return harvestCapacity / (1.0 + harvestHalfSaturation / output);
    }
}
