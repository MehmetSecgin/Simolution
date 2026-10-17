package com.simolution.kernel.phase;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.CompiledInflowField;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 5: energy intake, <b>cell-local</b> (contract v3 §4, supersedes v1's shared-reservoir
 * allocation), then per-chemical inflow admission. Each alive unit draws from the cell it
 * occupies ({@code position[slot]}) only, from chemical 0: {@code intake = min(demand,
 * cellResource)} with the unchanged saturating {@code demand} (contract v1 §5). Because cells
 * are exclusive (one unit per cell, contract v3 §2) there is no within-cell contention.
 * The cell is debited by exactly the intake.
 * <p>
 * Every cell of every chemical then admits up to that chemical's inflow cap, limited by its
 * capacity; the summed admission is the sole energy the audit treats as entering the system
 * ({@code cumulativeInflow}), so {@code initial energy + initial resource + inflow ==
 * Σ energy + Σ chemicals + sink} stays exact (contract v3 §7). Intake runs before cost so a
 * unit can pay this tick's metabolism with this tick's harvest; harvest output is read from
 * {@code outputsPrev}, the just-swapped current tick. Diffusion (phase 10) relaxes the field
 * afterward.
 * <p>
 * Shape is {@link Shape#SERIAL}: the unit pass must finish before inflow tops the cells up,
 * and {@code cumulativeInflow} is an ordered floating-point sum.
 */
public final class IntakePhase implements Phase {

    private final WorldState state;
    private final CompiledInflowField[] inflow;
    private final double[] capacity;
    private final double harvestCapacityPerConnection;
    private final double harvestHalfSaturation;
    private final double harvestMassExponent;

    public IntakePhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        final int chemCount = config.chemicals().size();
        this.inflow = new CompiledInflowField[chemCount];
        this.capacity = new double[chemCount];
        for (int k = 0; k < chemCount; k++) {
            final ChemicalSpec chemical = config.chemicals().get(k);
            inflow[k] = chemical.inflow().compile(state.worldWidth);
            capacity[k] = chemical.capacity();
        }
        this.harvestCapacityPerConnection = config.harvestCapacityPerConnection();
        this.harvestHalfSaturation = config.harvestHalfSaturation();
        this.harvestMassExponent = config.harvestMassExponent();
    }

    @Override
    public String name() {
        return "intake";
    }

    @Override
    public Shape shape() {
        return Shape.SERIAL;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] energy = state.energy;
        final int[] position = state.position;
        final double[] cellChem = state.cellChem;

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final double demand = harvestDemand(unit);
            if (demand <= 0.0) {
                continue;
            }
            final int cell = position[unit];
            final double intake = Math.min(demand, cellChem[cell]);
            energy[unit] += intake;
            cellChem[cell] -= intake;
        }

        final int cells = state.unitCount;
        for (int k = 0; k < state.chemCount; k++) {
            final int offset = k * cells;
            for (int cell = from; cell < to; cell++) {
                final double cap = inflow[k].cap(cell, state.tick);
                final double admitted = Math.min(cap,
                        Math.max(0.0, capacity[k] - cellChem[offset + cell]));
                cellChem[offset + cell] += admitted;
                state.cumulativeInflow += admitted;
            }
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
     */
    private double harvestDemand(final int unit) {
        final float[] outputsPrev = state.outputsPrev;
        final int[] harvestConnCount = state.harvestConnCount;

        final int transporters = harvestConnCount[unit];
        if (transporters == 0) {
            return 0.0;
        }
        final int harvestIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                + (NodeLayout.Action.HARVEST * NodeLayout.Action.INSTANCES_PER_TYPE);
        final double output = outputsPrev[harvestIdx];
        if (!Double.isFinite(output) || output <= 0.0) {
            return 0.0;
        }
        final double harvestCapacity = harvestCapacityPerConnection * transporters
                * Math.pow(state.mass[unit], harvestMassExponent);
        // capacity · output/(HALF+output), rearranged to avoid overflow when
        // output is a huge (divergent) finite value.
        return harvestCapacity / (1.0 + harvestHalfSaturation / output);
    }
}
