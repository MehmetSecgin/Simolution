package com.simolution.kernel.genome;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

public final class SlotCompiler {

    private static final int MUL_NODE = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
    private static final int HARVEST_NODE = NodeLayout.ACTION_OFFSET + NodeLayout.Action.HARVEST;

    private SlotCompiler() {}

    public static void compile(final WorldState state, final int unit) {
        final int base = unit * state.maxGenes;
        final int count = state.geneCount[unit];

        final int sumEnd = placeSumConnections(state, base, count);
        final int mulEnd = placeMulConnections(state, sumEnd, base, count);

        state.sumStart[unit] = base;
        state.sumEnd[unit] = sumEnd;
        state.mulStart[unit] = sumEnd;
        state.mulEnd[unit] = mulEnd;
        state.harvestConnCount[unit] = countHarvestTransporters(state, base, sumEnd);
        state.mulInDegree[unit] = mulInDegreeIncludingJunk(state, base, count);
    }

    private static int placeSumConnections(final WorldState state, final int base, final int count) {
        int cursor = base;
        for (int g = 0; g < count; g++) {
            final int gene = state.genes[base + g];
            final int dst = GeneDecoder.destinationNodeIndex(gene);
            if (dst == MUL_NODE) {
                continue;
            }
            final int src = GeneDecoder.sourceNodeIndex(gene);
            if (isStored(src)) {
                storeConnection(state, cursor++, src, dst, gene);
            }
        }
        return cursor;
    }

    private static int placeMulConnections(final WorldState state, final int start, final int base, final int count) {
        int cursor = start;
        for (int g = 0; g < count; g++) {
            final int gene = state.genes[base + g];
            if (GeneDecoder.destinationNodeIndex(gene) != MUL_NODE) {
                continue;
            }
            final int src = GeneDecoder.sourceNodeIndex(gene);
            if (isStored(src)) {
                storeConnection(state, cursor++, src, MUL_NODE, gene);
            }
        }
        return cursor;
    }

    private static int countHarvestTransporters(final WorldState state, final int base, final int sumEnd) {
        int transporters = 0;
        for (int c = base; c < sumEnd; c++) {
            if (state.connDst[c] == HARVEST_NODE) {
                transporters++;
            }
        }
        return transporters;
    }

    // Counts every MUL-destined gene, junk source included, before pruning; saturates at 2 (ADR 0005).
    private static int mulInDegreeIncludingJunk(final WorldState state, final int base, final int count) {
        int degree = 0;
        for (int g = 0; g < count; g++) {
            if (GeneDecoder.destinationNodeIndex(state.genes[base + g]) == MUL_NODE) {
                degree = Math.min(2, degree + 1);
            }
        }
        return degree;
    }

    // Junk outputs are exactly +0.0, so a junk-source connection never changes a sum
    // (adding a signed zero to +0.0), a MUL top-two entry (strict compares) or activity.
    private static boolean isStored(final int src) {
        return NodeLayout.isMeaningful(src);
    }

    private static void storeConnection(final WorldState state, final int index, final int src, final int dst, final int gene) {
        state.connSrc[index] = (short) src;
        state.connDst[index] = (short) dst;
        state.connWeight[index] = GeneDecoder.weight(gene);
    }
}
