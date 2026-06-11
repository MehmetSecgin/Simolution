package com.simolution.sim;

import java.util.Arrays;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

/**
 * Tick-independent statistics derived purely from compiled wiring.
 * Everything here is fixed for a given (seed, units, genesPerUnit) —
 * it is the structure that structural decay will one day tax
 * (contract v0 §4).
 */
public final class StructuralAnalyzer {

    private StructuralAnalyzer() {}

    public static StructuralStats analyze(final CompiledConnection[] connections, final int unitCount) {

        long meaningful = 0;
        long duplicates = 0;
        double absSum = 0.0;
        double absMax = 0.0;
        long positives = 0;

        final boolean[] seenPair = new boolean[NodeLayout.TOTAL * NodeLayout.TOTAL];
        final boolean[] reachable = new boolean[unitCount];
        final boolean[] randWired = new boolean[unitCount];
        final int[] perUnitConnections = new int[unitCount];
        final int[] perUnitMeaningful = new int[unitCount];

        int cursor = 0;
        for (int unit = 0; unit < unitCount; unit++) {
            final int unitEnd = endOfUnit(connections, cursor, unit);
            perUnitConnections[unit] = unitEnd - cursor;

            Arrays.fill(seenPair, false);
            final boolean[][] adjacency = new boolean[NodeLayout.TOTAL][NodeLayout.TOTAL];

            for (int i = cursor; i < unitEnd; i++) {
                final CompiledConnection c = connections[i];
                final int srcLocal = c.sourceAbsoluteIndex % NodeLayout.TOTAL;
                final int dstLocal = c.destinationAbsoluteIndex % NodeLayout.TOTAL;

                absSum += Math.abs(c.weight);
                absMax = Math.max(absMax, Math.abs(c.weight));
                if (c.weight > 0.0) {
                    positives++;
                }

                final int pairKey = srcLocal * NodeLayout.TOTAL + dstLocal;
                if (seenPair[pairKey]) {
                    duplicates++;
                } else {
                    seenPair[pairKey] = true;
                }

                final boolean bothMeaningful =
                        NodeLayout.isMeaningful(srcLocal) && NodeLayout.isMeaningful(dstLocal);
                if (bothMeaningful) {
                    meaningful++;
                    perUnitMeaningful[unit]++;
                    adjacency[srcLocal][dstLocal] = true;
                    if (srcLocal == NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND) {
                        randWired[unit] = true;
                    }
                }
            }

            reachable[unit] = sensorReachesAction(adjacency);
            cursor = unitEnd;
        }

        final long total = connections.length;
        return new StructuralStats(
                total,
                meaningful,
                duplicates,
                total == 0 ? 0.0 : absSum / total,
                absMax,
                total == 0 ? 0.0 : (double) positives / total,
                reachable,
                randWired,
                perUnitConnections,
                perUnitMeaningful
        );
    }

    private static int endOfUnit(final CompiledConnection[] connections, final int start, final int unit) {
        int i = start;
        while (i < connections.length
               && connections[i].sourceAbsoluteIndex / NodeLayout.TOTAL == unit) {
            i++;
        }
        return i;
    }

    /**
     * BFS over meaningful-node edges only. Junk nodes cannot transmit
     * (their outputs are pinned to 0), so a path through one is not a
     * signal path; such edges were never added to the adjacency.
     */
    private static boolean sensorReachesAction(final boolean[][] adjacency) {
        final boolean[] visited = new boolean[NodeLayout.TOTAL];
        final int[] queue = new int[NodeLayout.TOTAL];
        int head = 0;
        int tail = 0;

        for (int s = 0; s < NodeLayout.Sensor.MEANINGFUL_COUNT; s++) {
            visited[NodeLayout.SENSOR_OFFSET + s] = true;
            queue[tail++] = NodeLayout.SENSOR_OFFSET + s;
        }

        while (head < tail) {
            final int node = queue[head++];
            for (int next = 0; next < NodeLayout.TOTAL; next++) {
                if (adjacency[node][next] && !visited[next]) {
                    if (next >= NodeLayout.ACTION_OFFSET) {
                        return true;
                    }
                    visited[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        return false;
    }
}
