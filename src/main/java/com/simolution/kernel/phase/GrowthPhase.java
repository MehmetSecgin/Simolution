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
 * Shape is {@link Shape#SERIAL} because the lossy remainder adds to the shared
 * {@code energySink} in slot order.
 */
public final class GrowthPhase implements Phase {

    private final WorldState state;
    private final double growMax;
    private final double growHalfSaturation;
    private final double growYield;

    public GrowthPhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.growMax = config.growMax();
        this.growHalfSaturation = config.growHalfSaturation();
        this.growYield = config.growYield();
    }

    @Override
    public String name() {
        return "growth";
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
        final double[] mass = state.mass;
        final float[] outputsPrev = state.outputsPrev;

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
            state.energySink += commit - gained;
            if (energy[unit] <= 0.0) {
                state.die(unit);
            }
        }
    }
}
