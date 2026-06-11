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

    private final CompiledConnection[] sumConnections;
    private final CompiledConnection[] mulConnections;
    private final double[] mulTop1;
    private final double[] mulTop2;
    private final int[] mulInDegree;
    private final int unitCount;

    private int tick = 0;

    /**
     * Connections are split at construction (a structure-only precompute,
     * allowed by the cache spec): MUL needs its two strongest individual
     * input signals, which the summing accumulator destroys. MUL-destined
     * connections therefore run in their own loop that maintains a per-unit
     * top-2 instead of a sum. The split keeps the main propagation loop
     * branch-free (ADR 0005).
     */
    public Kernel(final int unitCount, final CompiledConnection[] connections) {

        final int totalNodes = unitCount * NodeLayout.TOTAL;

        this.outputsPrev = new double[totalNodes];
        this.outputsNext = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.delayMemory = new double[totalNodes];

        this.unitCount = unitCount;
        this.mulTop1 = new double[unitCount];
        this.mulTop2 = new double[unitCount];
        this.mulInDegree = new int[unitCount];

        int mulCount = 0;
        for (final CompiledConnection connection : connections) {
            if (isMulDestination(connection)) {
                mulCount++;
            }
        }

        this.sumConnections = new CompiledConnection[connections.length - mulCount];
        this.mulConnections = new CompiledConnection[mulCount];

        int sumCursor = 0;
        int mulCursor = 0;
        for (final CompiledConnection connection : connections) {
            if (isMulDestination(connection)) {
                mulConnections[mulCursor++] = connection;
                final int unit = connection.destinationAbsoluteIndex / NodeLayout.TOTAL;
                mulInDegree[unit] = Math.min(2, mulInDegree[unit] + 1);
            } else {
                sumConnections[sumCursor++] = connection;
            }
        }
    }

    private static boolean isMulDestination(final CompiledConnection connection) {
        final int dstLocal = connection.destinationAbsoluteIndex % NodeLayout.TOTAL;
        return dstLocal == NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
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
        Arrays.fill(mulTop1, 0.0);
        Arrays.fill(mulTop2, 0.0);
    }

    private void propagateConnections() {
        for (final CompiledConnection connection : sumConnections) {
            final double signal = outputsPrev[connection.sourceAbsoluteIndex] * connection.weight;
            accumulators[connection.destinationAbsoluteIndex] += signal;
        }

        propagateMulConnections();
    }

    /**
     * Maintains the two strongest (by |signal|, signed values kept) inputs
     * per MUL node. Ties resolve to the earlier connection in genome order.
     * Non-finite signals lose every comparison (NaN compares false), so they
     * never enter the top-2 — divergent units are the observer's concern.
     */
    private void propagateMulConnections() {
        for (final CompiledConnection connection : mulConnections) {
            final double signal = outputsPrev[connection.sourceAbsoluteIndex] * connection.weight;
            final int unit = connection.destinationAbsoluteIndex / NodeLayout.TOTAL;

            if (Math.abs(signal) > Math.abs(mulTop1[unit])) {
                mulTop2[unit] = mulTop1[unit];
                mulTop1[unit] = signal;
            } else if (Math.abs(signal) > Math.abs(mulTop2[unit])) {
                mulTop2[unit] = signal;
            }
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

        // MUL by structural in-degree: 0 inputs -> 0; 1 input -> that signal
        // (the binding slice's own example wires DELAY -> MUL -> ADD, so a
        // lone input must pass through); 2+ inputs -> product of the two
        // strongest (slice section 2, ADR 0005)
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        outputsNext[mulIdx] = switch (mulInDegree[unit]) {
            case 0 -> 0.0;
            case 1 -> mulTop1[unit];
            default -> mulTop1[unit] * mulTop2[unit];
        };

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
