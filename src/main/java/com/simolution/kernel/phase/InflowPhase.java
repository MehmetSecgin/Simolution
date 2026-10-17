package com.simolution.kernel.phase;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.CompiledInflowField;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 5, second half: per-chemical inflow admission, right after intake (engine v6 split it
 * out of {@link IntakePhase} so each half has one shape; the contract phase numbers stay). Every cell of every
 * chemical admits up to that chemical's inflow cap, limited by its capacity. The summed
 * admission is the sole energy the audit treats as entering the system
 * ({@code cumulativeInflow}), so {@code initial energy + initial resource + inflow ==
 * Σ energy + Σ chemicals + sink} stays exact (contract v3 §7). Diffusion relaxes the field
 * afterward.
 * <p>
 * Shape is {@link Shape#PER_CELL}: a cell reads and writes only its own chemicals, in place,
 * after every unit's harvest (the phase boundary orders the two). Admissions go to the
 * chunk's {@code inflowPartial}, merged into {@code cumulativeInflow} in {@link #finish()}.
 */
public class InflowPhase implements Phase {

    private final WorldState state;
    private final CompiledInflowField[] inflow;
    private final double[] capacity;

    public InflowPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        final int chemCount = config.chemicals().size();
        this.inflow = new CompiledInflowField[chemCount];
        this.capacity = new double[chemCount];
        for (int k = 0; k < chemCount; k++) {
            final ChemicalSpec chemical = config.chemicals().get(k);
            inflow[k] = chemical.inflow().compile(state.worldWidth);
            capacity[k] = chemical.capacity();
        }
    }

    @Override
    public String name() {
        return "inflow";
    }

    @Override
    public Shape shape() {
        return Shape.PER_CELL;
    }

    @Override
    public int extent() {
        return state.unitCount;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        final double[] cellChem = state.cellChem;
        double admittedTotal = 0.0;
        final int cells = state.unitCount;
        final int tick = state.tick;
        for (int k = 0; k < inflow.length; k++) {
            final int offset = k * cells;
            for (int cell = from; cell < to; cell++) {
                final double cap = inflow[k].cap(cell, tick);
                final double admitted = Math.min(cap,
                        Math.max(0.0, capacity[k] - cellChem[offset + cell]));
                cellChem[offset + cell] += admitted;
                admittedTotal += admitted;
            }
        }
        state.inflowPartial[chunk] += admittedTotal;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
