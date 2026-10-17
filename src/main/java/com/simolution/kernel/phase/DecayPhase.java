package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 11: chemical decay (contract v7.1/v8). Standing chemical is not permanent: each tick
 * every cell converts the fraction {@code decayRate} of each chemical, so unconsumed resource
 * fades instead of accumulating forever. The decayed amount goes to the energy sink when the
 * chemical's {@code decayTarget} is {@code -1}, else into the target chemical of the same
 * cell (rotting). Either way the total is conserved, so the closed-system balance
 * (contract v5 §6) is untouched.
 * <p>
 * Chemicals are processed in ascending index, in place, so a chemical that decays into a
 * higher-indexed one has its product decay in the same tick, and into a lower-indexed one
 * from the next tick. A chemical that decays into itself is rejected at construction.
 * <p>
 * Shape is {@link Shape#PER_CELL}: each cell reads and writes only its own chemicals, in
 * place. Decay to the sink goes to the chunk's {@code sinkPartial}, merged in
 * {@link #finish()}.
 */
public class DecayPhase implements Phase {

    private final WorldState state;
    private final double[] rate;
    private final int[] target;

    public DecayPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.rate = new double[state.chemCount];
        this.target = new int[state.chemCount];
        for (int k = 0; k < rate.length; k++) {
            rate[k] = config.chemicals().get(k).decayRate();
            target[k] = config.chemicals().get(k).decayTarget();
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
            final int targetOffset = target[k] * cells;
            final boolean toSink = target[k] < 0;
            for (int cell = from; cell < to; cell++) {
                final double lost = cellChem[offset + cell] * r;
                cellChem[offset + cell] -= lost;
                if (toSink) {
                    sink += lost;
                } else {
                    cellChem[targetOffset + cell] += lost;
                }
            }
        }
        state.sinkPartial[chunk] += sink;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
