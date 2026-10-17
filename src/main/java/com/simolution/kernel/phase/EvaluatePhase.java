package com.simolution.kernel.phase;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.layout.NodeSpec;
import com.simolution.kernel.runtime.Noise;
import com.simolution.kernel.state.WorldState;

/**
 * Phase 3: evaluates every node of every slot into {@code outputsNext}. Sensors are raw
 * local taps (chemical 0 of the unit's own cell, its own energy and mass); a dead unit
 * computes nothing and is frozen at its last living configuration.
 */
public final class EvaluatePhase implements Phase {

    private static final NodeSpec.Role[] SENSOR_ROLES = sensorRoles();

    private final WorldState state;
    private final double chemicalZeroCapacity;
    private final double selfEnergyScale;
    private final double selfMassScale;

    public EvaluatePhase(final WorldState state, final EngineConfig config) {
        this.state = state;
        this.chemicalZeroCapacity = config.chemicals().get(0).capacity();
        this.selfEnergyScale = config.selfEnergyScale();
        this.selfMassScale = config.selfMassScale();
    }

    @Override
    public String name() {
        return "evaluate";
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

        for (int unit = from; unit < to; unit++) {
            if (energy[unit] <= 0.0) {
                freezeDeadUnit(unit);
            } else {
                evaluateUnit(unit);
            }
        }
    }

    private static NodeSpec.Role[] sensorRoles() {
        final NodeSpec.Role[] roles = new NodeSpec.Role[NodeLayout.Sensor.MEANINGFUL_COUNT];
        for (int s = 0; s < roles.length; s++) {
            roles[s] = NodeLayout.spec(NodeLayout.SENSOR_OFFSET + s * NodeLayout.Sensor.INSTANCES_PER_TYPE).role();
        }
        return roles;
    }

    /**
     * A dead unit computes nothing (contract v0 §9), but the double buffer
     * would otherwise surface its state from two ticks ago after the swap.
     * Carrying the previous outputs forward freezes the corpse at its last
     * living configuration: inert, unchanging.
     */
    private void freezeDeadUnit(final int unit) {
        final float[] outputsPrev = state.outputsPrev;
        final float[] outputsNext = state.outputsNext;

        final int base = unit * NodeLayout.TOTAL;
        System.arraycopy(outputsPrev, base, outputsNext, base, NodeLayout.TOTAL);
    }

    private void evaluateUnit(final int unit) {
        final double[] energy = state.energy;
        final double[] mass = state.mass;
        final int[] position = state.position;
        final float[] delayMemory = state.delayMemory;
        final float[] outputsNext = state.outputsNext;
        final double[] cellChem = state.cellChem;
        final int[] mulInDegree = state.mulInDegree;
        final float[] accumulators = state.accumulators;
        final float[] mulTop1 = state.mulTop1;
        final float[] mulTop2 = state.mulTop2;

        final int base = unit * NodeLayout.TOTAL;

        for (int s = 0; s < NodeLayout.Sensor.MEANINGFUL_COUNT; s++) {
            final int idx = base + NodeLayout.SENSOR_OFFSET + s * NodeLayout.Sensor.INSTANCES_PER_TYPE;
            switch (SENSOR_ROLES[s]) {
                case CONST -> outputsNext[idx] = 1.0f;
                case RAND -> outputsNext[idx] = (float) Noise.sample(Noise.LEGACY_RNG_SEED, unit, state.tick);
                // a unit senses only the cell it currently occupies (contract v3 §3)
                case CHEMICAL_HERE -> outputsNext[idx] = (float) Math.min(1.0, cellChem[position[unit]] / chemicalZeroCapacity);
                case SELF_ENERGY -> outputsNext[idx] = (float) Math.min(1.0, energy[unit] / selfEnergyScale);
                case SELF_MASS -> outputsNext[idx] = (float) Math.min(1.0, mass[unit] / selfMassScale);
                default -> throw new IllegalStateException("not a sensor role: " + SENSOR_ROLES[s]);
            }
        }

        final int addIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[addIdx] = accumulators[addIdx];

        // MUL by structural in-degree: 0 inputs -> 0; 1 input -> that signal
        // (the binding slice's own example wires DELAY -> MUL -> ADD, so a
        // lone input must pass through); 2+ inputs -> product of the two
        // strongest (slice section 2, ADR 0005)
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[mulIdx] = switch (mulInDegree[unit]) {
            case 0 -> 0.0f;
            case 1 -> mulTop1[unit];
            default -> mulTop1[unit] * mulTop2[unit];
        };

        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final double clampIn = accumulators[clampIdx];
        outputsNext[clampIdx] = (float) Math.max(-1.0, Math.min(1.0, clampIn));

        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[delayIdx] = delayMemory[delayIdx];
        delayMemory[delayIdx] = accumulators[delayIdx];

        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[threshIdx] = accumulators[threshIdx] > 0.0f ? 1.0f : -1.0f;

        for (int a = 0; a < NodeLayout.Action.MEANINGFUL_COUNT; a++) {
            final int idx = base + NodeLayout.ACTION_OFFSET + a * NodeLayout.Action.INSTANCES_PER_TYPE;
            outputsNext[idx] = accumulators[idx];
        }
    }
}
