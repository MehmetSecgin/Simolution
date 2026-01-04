package com.simolution.kernel.runtime;

import java.util.Arrays;
import java.util.Random;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class Kernel {

    private double[] outputsPrev;
    private double[] outputsNext;
    private final double[] accumulators;
    private final double[] delayMemory;

    private final CompiledConnection[] connections;
    private final Random random;

    private int tick = 0;

    private boolean headerPrinted = false;

    public Kernel(final CompiledConnection[] connections) {

        final int unitCount = KernelConfig.UNIT_COUNT;
        final int nodesPerUnit = NodeLayout.TOTAL;

        final int totalNodes = unitCount * nodesPerUnit;

        this.outputsPrev = new double[totalNodes];
        this.outputsNext = new double[totalNodes];
        this.accumulators = new double[totalNodes];
        this.delayMemory = new double[totalNodes];

        this.connections = connections;
        this.random = new Random(KernelConfig.RANDOM_SEED);
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
     * v0.1: single-unit evaluation (base = 0).
     * <p>
     * IMPORTANT:
     * - NodeLayout.* constants are TYPE INDICES (0..TYPE_COUNT-1), not absolute indices.
     * - Absolute indices must be computed using OFFSETS.
     * - With INSTANCES_PER_TYPE == 1, base index is offset + typeIndex.
     * <p>
     * TODO (later):
     * - When UNIT_COUNT > 1, evaluate each unit with base = unitIndex * NodeLayout.TOTAL.
     * - When INSTANCES_PER_TYPE > 1, choose instance indices (for now instance = 0).
     */
    private void evaluateNodes() {
        final int base = 0;

        // ---- Sensors (meaningful only; junk sensors stay at 0) ----
        final int constIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        outputsNext[constIdx] = 1.0;
        outputsNext[randIdx] = random.nextDouble() * 2.0 - 1.0;

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

    public void debugDump() {
        final int base = 0;

        final int constIdx = base + NodeLayout.SENSOR_OFFSET
                             + (NodeLayout.Sensor.CONST * NodeLayout.Sensor.INSTANCES_PER_TYPE);
        final int randIdx = base + NodeLayout.SENSOR_OFFSET
                            + (NodeLayout.Sensor.RAND * NodeLayout.Sensor.INSTANCES_PER_TYPE);

        final int addIdx = base + NodeLayout.INTERNAL_OFFSET
                           + (NodeLayout.Internal.ADD * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int mulIdx = base + NodeLayout.INTERNAL_OFFSET
                           + (NodeLayout.Internal.MUL * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int clampIdx = base + NodeLayout.INTERNAL_OFFSET
                             + (NodeLayout.Internal.CLAMP * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int delayIdx = base + NodeLayout.INTERNAL_OFFSET
                             + (NodeLayout.Internal.DELAY * NodeLayout.Internal.INSTANCES_PER_TYPE);
        final int threshIdx = base + NodeLayout.INTERNAL_OFFSET
                              + (NodeLayout.Internal.THRESH * NodeLayout.Internal.INSTANCES_PER_TYPE);

        final int actionIdx = base + NodeLayout.ACTION_OFFSET
                              + (NodeLayout.Action.Y * NodeLayout.Action.INSTANCES_PER_TYPE);

        if (!headerPrinted) {
            System.out.printf(
                    "%5s | %7s %7s | %7s %7s %7s %7s %7s | %7s | %7s%n",
                    "Tick",
                    "CONST", "RAND",
                    "ADD", "MUL", "CLAMP", "DELAY", "THRESH",
                    "ACTION",
                    "DELAY_M"
            );
            System.out.println(
                    "-----+-----------------+---------------------------------------+---------+---------"
            );
            headerPrinted = true;
        }

        System.out.printf(
                "%5d | %7.4f %7.4f | %7.4f %7.4f %7.4f %7.4f %7.4f | %7.4f | %7.4f%n",
                tick,
                outputsPrev[constIdx],
                outputsPrev[randIdx],
                outputsPrev[addIdx],
                outputsPrev[mulIdx],
                outputsPrev[clampIdx],
                outputsPrev[delayIdx],
                outputsPrev[threshIdx],
                outputsPrev[actionIdx],
                delayMemory[delayIdx]
        );
    }

    private static String nodeName(final int absoluteIndex) {
        // Single-unit debug mapping only (base=0). This intentionally labels meaningful nodes by index.
        // With junk nodes enabled, unknown indices will show SENSOR?/INTERNAL?/ACTION? which is fine for now.
        if (absoluteIndex < NodeLayout.Internal.COUNT + NodeLayout.INTERNAL_OFFSET) {
            // Sensors block
            if (absoluteIndex < NodeLayout.INTERNAL_OFFSET) {
                final int sensorLocal = absoluteIndex - NodeLayout.SENSOR_OFFSET;
                return switch (sensorLocal) {
                    case NodeLayout.Sensor.CONST -> "CONST";
                    case NodeLayout.Sensor.RAND -> "RAND";
                    default -> "SENSOR?" + sensorLocal;
                };
            }

            // Internals block
            final int internalLocal = absoluteIndex - NodeLayout.INTERNAL_OFFSET;
            return switch (internalLocal) {
                case NodeLayout.Internal.ADD -> "ADD";
                case NodeLayout.Internal.MUL -> "MUL";
                case NodeLayout.Internal.CLAMP -> "CLAMP";
                case NodeLayout.Internal.DELAY -> "DELAY";
                case NodeLayout.Internal.THRESH -> "THRESH";
                default -> "INTERNAL?" + internalLocal;
            };
        }

        // Actions block
        final int actionLocal = absoluteIndex - NodeLayout.ACTION_OFFSET;
        return switch (actionLocal) {
            case NodeLayout.Action.Y -> "ACTION";
            default -> "ACTION?" + actionLocal;
        };
    }

    public void dumpWiring() {
        System.out.println("=== Compiled Wiring ===");

        for (int i = 0; i < connections.length; i++) {
            final CompiledConnection c = connections[i];

            final String src = nodeName(c.sourceAbsoluteIndex);
            final String dst = nodeName(c.destinationAbsoluteIndex);

            System.out.printf(
                    "%2d: %-6s -> %-10s w=%+.6f%n",
                    i, src, dst, c.weight
            );
        }

        System.out.println();
    }

}