package com.simolution.kernel.runtime;

import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class Kernel {

    private double[] outputsPrev;
    private double[] outputsNext;
    private final double[] accumulators;
    private final double[] delayMemory;

    private final CompiledConnection[] connections;
    private final int unitCount;

    private int tick = 0;

    public Kernel(final int unitCount, final CompiledConnection[] connections) {

        final int totalNodes = unitCount * NodeLayout.TOTAL;

        this.outputsPrev = new double[totalNodes];
        this.outputsNext = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.delayMemory = new double[totalNodes];

        this.connections = connections;
        this.unitCount = unitCount;
    }

    public void tick() {
        // Phase 1
        clearAccumulators();
        // Phase 2
        propagateConnections();
        // Phase 3
        evaluateNodes();
        // Phase 4
        swapBuffers();

        tick++;
    }

    private void clearAccumulators() {
        Arrays.fill(accumulators, 0.0);
    }

    private void propagateConnections() {
        for (final CompiledConnection connection : connections) {
            final double signal = outputsPrev[connection.sourceAbsoluteIndex] * connection.weight;
            accumulators[connection.destinationAbsoluteIndex] += signal;
        }
    }

    /**
     * IMPORTANT:
     * - NodeLayout.* constants are TYPE INDICES (0..TYPE_COUNT-1), not absolute indices.
     * - Absolute indices must be computed using OFFSETS.
     * - With INSTANCES_PER_TYPE == 1, base index is offset + typeIndex.
     * <p>
     * TODO (later):
     * - When INSTANCES_PER_TYPE > 1, choose instance indices (for now instance = 0).
     */
    private void evaluateNodes() {
        for (int unit = 0; unit < unitCount; unit++) {
            evaluateUnit(unit);
        }
    }

    private void evaluateUnit(final int unit) {
        final int base = unit * NodeLayout.TOTAL;

        // ---- Sensors (meaningful only; junk sensors stay at 0) ----
        final int constIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        outputsNext[constIdx] = 1.0;
        outputsNext[randIdx] = Noise.sample(KernelConfig.RANDOM_SEED, unit, tick);

        // ---- Internals (meaningful only; junk internals stay at 0) ----
        final int addIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[addIdx] = accumulators[addIdx];

        // v0.1 MUL: pass-through accumulated signal (true multi-input MUL comes later)
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[mulIdx] = accumulators[mulIdx];

        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final double clampIn = accumulators[clampIdx];
        outputsNext[clampIdx] = Math.max(-1.0, Math.min(1.0, clampIn));

        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[delayIdx] = delayMemory[delayIdx];
        delayMemory[delayIdx] = accumulators[delayIdx];

        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[threshIdx] = accumulators[threshIdx] > 0.0 ? 1.0 : -1.0;

        // ---- Action (meaningful only; junk actions stay at 0) ----
        final int actionIdx = base + NodeLayout.ACTION_OFFSET + (NodeLayout.Action.Y * NodeLayout.Action.INSTANCES_PER_TYPE);
        outputsNext[actionIdx] = accumulators[actionIdx];

        // NOTE:
        // We do not clear outputsNext here because for v0.1 we overwrite all meaningful nodes every tick.
        // If later you make evaluation sparse / conditional, you must clear outputsNext each tick.
    }

    private void swapBuffers() {
        final double[] tmp = outputsPrev;
        outputsPrev = outputsNext;
        outputsNext = tmp;
    }

    public KernelSnapshot snapshot() {
        return new KernelSnapshot(tick, outputsPrev, delayMemory);
    }
}
