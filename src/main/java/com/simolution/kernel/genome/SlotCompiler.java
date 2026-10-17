package com.simolution.kernel.genome;

import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/**
 * Decodes a slot's live genes into its connection region of the {@link WorldState} and
 * recomputes the structure-derived per-slot data (ranges, harvest transporter count, MUL
 * in-degree). Runs once per slot at construction, at every birth (contract v2 §6/§12) and at
 * checkpoint restore; never per tick, and allocation free: it reads the genes through the
 * primitive {@link GeneDecoder} accessors and writes unit-local source/destination indices and
 * the weight into the slot's flat region. Two passes place sum-destined connections (in gene
 * order) then mul-destined ones (in gene order) contiguously without a scratch buffer.
 * <p>
 * A connection whose source is a junk node is not stored: junk outputs are exactly
 * {@code +0.0}, so such a connection can neither move an accumulator (adding a signed zero to
 * a sum that starts at {@code +0.0} never changes it) nor displace a MUL top-two entry (the
 * comparisons are strict) nor count as active. {@code mulInDegree} still counts every
 * MUL-destined gene, junk source included, and is computed from the genes before any pruning.
 * A "transporter" (a stored connection feeding HARVEST) is by construction one with a
 * meaningful source, the unit's structural uptake investment (contract v1 §5, ADR 0012).
 */
public final class SlotCompiler {

    private static final int MUL_LOCAL = NodeLayout.INTERNAL_OFFSET + NodeLayout.Internal.MUL;
    private static final int HARVEST_LOCAL = NodeLayout.ACTION_OFFSET + NodeLayout.Action.HARVEST;

    private SlotCompiler() {}

    public static void compile(final WorldState state, final int unit) {
        final int[] genes = state.genes;
        final short[] connSrc = state.connSrc;
        final short[] connDst = state.connDst;
        final double[] connWeight = state.connWeight;

        final int base = unit * state.maxGenes;
        final int count = state.geneCount[unit];

        int cursor = base;
        int harvest = 0;
        for (int g = 0; g < count; g++) {
            final int gene = genes[base + g];
            final int dst = GeneDecoder.destinationLocal(gene);
            if (dst == MUL_LOCAL) {
                continue;
            }
            final int src = GeneDecoder.sourceLocal(gene);
            if (!NodeLayout.isMeaningful(src)) {
                continue;
            }
            connSrc[cursor] = (short) src;
            connDst[cursor] = (short) dst;
            connWeight[cursor] = GeneDecoder.weight(gene);
            cursor++;
            if (dst == HARVEST_LOCAL) {
                harvest++;
            }
        }
        state.sumStart[unit] = base;
        state.sumEnd[unit] = cursor;

        state.mulStart[unit] = cursor;
        int mulDegree = 0;
        for (int g = 0; g < count; g++) {
            final int gene = genes[base + g];
            if (GeneDecoder.destinationLocal(gene) != MUL_LOCAL) {
                continue;
            }
            mulDegree = Math.min(2, mulDegree + 1);
            final int src = GeneDecoder.sourceLocal(gene);
            if (!NodeLayout.isMeaningful(src)) {
                continue;
            }
            connSrc[cursor] = (short) src;
            connDst[cursor] = (short) MUL_LOCAL;
            connWeight[cursor] = GeneDecoder.weight(gene);
            cursor++;
        }
        state.mulEnd[unit] = cursor;

        state.harvestConnCount[unit] = harvest;
        state.mulInDegree[unit] = mulDegree;
    }
}
