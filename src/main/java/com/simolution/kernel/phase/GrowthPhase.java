package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 7: growth (contract v5 §2). Each living unit drives its {@code GROW}
 * effector to convert spendable energy into structural mass (anabolism). The
 * commitment is {@code GROW_MAX · σ(output)} with the saturating shape shared
 * by harvest and the division trigger (a runaway signal only approaches the
 * ceiling), clamped to available energy. Conversion is lossy: the unit gains
 * {@code GROW_YIELD} of the commitment as mass and the remainder dissipates to
 * the sink, so no energy is created: mass is energy in structural form
 * (contract v5 §1) and the audit (contract v5 §6) stays exact. Growth is an
 * evolved choice, never automatic: a unit with no positive {@code GROW} drive
 * grows nothing. A unit that pours its last energy into mass dies here (its
 * fresh mass then dissipates via {@link WorldState#die}); the kernel does not
 * protect against suicidal growth wiring, selection does.
 * <p>
 * Shape is {@link Shape#PER_UNIT}: a unit writes only its own energy and mass and (on
 * death) its own slot's death fields and its own cell's occupancy. The lossy remainder and
 * death mass go to the chunk's {@code sinkPartial}, merged in {@link #finish()}.
 */
public class GrowthPhase implements Phase {

    private final WorldState state;
    private final double growMax;
    private final double growHalfSaturation;
    private final double growYield;

    public GrowthPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.growMax = config.growth().max();
        this.growHalfSaturation = config.growth().halfSaturation();
        this.growYield = config.growth().yield();
    }

    @Override
    public String name() {
        return "growth";
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
        final float[] outputsPrev = state.outputsPrev;
        double sink = 0.0;

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                continue;
            }
            final int growIdx = unit * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET
                    + (NodeLayout.Action.GROW * NodeLayout.Action.INSTANCES_PER_TYPE);
            final double output = outputsPrev[growIdx];
            if (!Double.isFinite(output) || output <= 0.0) {
                continue;
            }
            double commit = growMax
                    / (1.0 + growHalfSaturation / output);
            if (commit > energy[unit]) {
                commit = energy[unit];
            }
            energy[unit] -= commit;
            final double gained = growYield * commit;
            mass[unit] += gained;
            sink += commit - gained;
            if (energy[unit] <= 0.0) {
                sink += state.die(unit);
            }
        }
        state.sinkPartial[chunk] += sink;
    }

    @Override
    public void finish() {
        state.mergePartials();
    }
}
