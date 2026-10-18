package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 11: chemical decay (contract v7.1/v8). Standing chemical is not permanent: each tick
 * every cell converts the fraction {@code decayRate} of each chemical, so unconsumed resource
 * fades instead of accumulating forever. The decayed amount goes to the energy sink, so the
 * total is conserved and the closed-system balance (contract v5 §6) is untouched.
 * <p>
 * Shape is {@link Shape#PER_CELL}: each cell reads and writes only its own chemicals, in
 * place. Decay to the sink goes to the chunk's {@code sinkPartial}, merged in
 * {@link #finish()}.
 */
public class DecayPhase implements Phase {

    private final WorldState state;
    private final double[] rate;

    public DecayPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.rate = new double[state.chemCount];
        for (int k = 0; k < rate.length; k++) {
            rate[k] = config.chemicals().get(k).decayRate();
        }
    }

    @Override
    public String name() {
        return "decay";
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
        double sink = 0.0;

        final int cells = state.unitCount;
        for (int k = 0; k < rate.length; k++) {
            final double r = rate[k];
            if (r <= 0.0) {
                continue;
            }
            final int offset = k * cells;
            for (int cell = from; cell < to; cell++) {
                final double lost = cellChem[offset + cell] * r;
                cellChem[offset + cell] -= lost;
                sink += lost;
            }
        }
        state.sinkPartial[chunk] += sink;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
