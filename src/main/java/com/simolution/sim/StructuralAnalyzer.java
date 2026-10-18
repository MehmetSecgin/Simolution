package com.simolution.sim;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class StructuralAnalyzer {

    private StructuralAnalyzer() {}

    private record UnitWiring(
            long meaningful,
            boolean reachable,
            boolean randWired,
            long duplicates
    ) {}

    private static class WeightTally {
        private double absSum;
        private double absMax;
        private long positives;

        void addAll(final CompiledConnection[] connections, final int from, final int to) {
            for (int i = from; i < to; i++) {
                final double weight = connections[i].weight();
                absSum += Math.abs(weight);
                absMax = Math.max(absMax, Math.abs(weight));
                if (weight > 0.0) {
                    positives++;
                }
            }
        }

        double absMean(final long total) {
            return total == 0 ? 0.0 : absSum / total;
        }

        double positiveFraction(final long total) {
            return total == 0 ? 0.0 : (double) positives / total;
        }
    }

    public static StructuralStats analyze(final CompiledConnection[] connections, final int founderCount) {
        final WeightTally weights = new WeightTally();
        int reachable = 0;
        int randWired = 0;
        long meaningful = 0;
        long duplicates = 0;

        int from = 0;
        for (int unit = 0; unit < founderCount; unit++) {
            final int to = endOfUnit(connections, from, unit);
            weights.addAll(connections, from, to);
            final UnitWiring wiring = wiringOf(connections, from, to);
            meaningful += wiring.meaningful();
            duplicates += wiring.duplicates();
            if (wiring.reachable()) {
                reachable++;
            }
            if (wiring.randWired()) {
                randWired++;
            }
            from = to;
        }

        final long total = connections.length;
        return new StructuralStats(total, meaningful, duplicates, weights.absMean(total), weights.absMax,
                weights.positiveFraction(total), reachable, randWired);
    }

    private static UnitWiring wiringOf(final CompiledConnection[] connections, final int from, final int to) {
        final boolean[] seenPair = new boolean[NodeLayout.TOTAL * NodeLayout.TOTAL];
        final boolean[][] adjacency = new boolean[NodeLayout.TOTAL][NodeLayout.TOTAL];
        long meaningful = 0;
        long duplicates = 0;
        boolean randWired = false;

        for (int i = from; i < to; i++) {
            final int srcLocal = connections[i].sourceAbsoluteIndex() % NodeLayout.TOTAL;
            final int dstLocal = connections[i].destinationAbsoluteIndex() % NodeLayout.TOTAL;

            final int pairKey = srcLocal * NodeLayout.TOTAL + dstLocal;
            if (seenPair[pairKey]) {
                duplicates++;
            } else {
                seenPair[pairKey] = true;
            }

            if (NodeLayout.isMeaningful(srcLocal) && NodeLayout.isMeaningful(dstLocal)) {
                meaningful++;
                adjacency[srcLocal][dstLocal] = true;
                randWired |= srcLocal == NodeLayout.SENSOR_OFFSET + NodeLayout.Sensor.RAND;
            }
        }
        return new UnitWiring(meaningful, sensorReachesAction(adjacency), randWired, duplicates);
    }

    private static int endOfUnit(final CompiledConnection[] connections, final int start, final int unit) {
        int i = start;
        while (i < connections.length
                && connections[i].sourceAbsoluteIndex() / NodeLayout.TOTAL == unit) {
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
