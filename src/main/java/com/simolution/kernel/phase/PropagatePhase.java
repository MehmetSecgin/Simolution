package com.simolution.kernel.phase;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 2: per-unit propagation. Dead units (energy &lt;= 0) are skipped entirely: the one
 * alive-check per unit replaces what would be a branch per connection, and a dying
 * population literally costs less to simulate. Counts non-zero propagations per unit; that
 * count is the activity the cost phase charges for (contract v0 §6, fork 1a).
 */
public final class PropagatePhase implements Phase {

    private final WorldState state;

    public PropagatePhase(final WorldState state) {
        this.state = state;
    }

    @Override
    public String name() {
        return "propagate";
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
        final float[] outputsPrev = state.outputsPrev;
        final short[] connSrc = state.connSrc;
        final short[] connDst = state.connDst;
        final double[] connWeight = state.connWeight;
        final int[] sumStart = state.sumStart;
        final int[] sumEnd = state.sumEnd;
        final int[] mulStart = state.mulStart;
        final int[] mulEnd = state.mulEnd;
        final float[] accumulators = state.accumulators;
        final float[] mulTop1 = state.mulTop1;
        final float[] mulTop2 = state.mulTop2;
        final int[] activeThisTick = state.activeThisTick;

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                activeThisTick[unit] = 0;
                continue;
            }

            final int unitOffset = unit * NodeLayout.TOTAL;
            int active = 0;

            for (int i = sumStart[unit]; i < sumEnd[unit]; i++) {
                final float signal = (float) (outputsPrev[unitOffset + connSrc[i]] * connWeight[i]);
                accumulators[unitOffset + connDst[i]] += signal;
                if (signal != 0.0f) {
                    active++;
                }
            }

            for (int i = mulStart[unit]; i < mulEnd[unit]; i++) {
                final float signal = (float) (outputsPrev[unitOffset + connSrc[i]] * connWeight[i]);
                if (Math.abs(signal) > Math.abs(mulTop1[unit])) {
                    mulTop2[unit] = mulTop1[unit];
                    mulTop1[unit] = signal;
                } else if (Math.abs(signal) > Math.abs(mulTop2[unit])) {
                    mulTop2[unit] = signal;
                }
                if (signal != 0.0f) {
                    active++;
                }
            }

            activeThisTick[unit] = active;
        }
    }
}
