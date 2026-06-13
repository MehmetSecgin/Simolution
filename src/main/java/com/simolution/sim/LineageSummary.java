package com.simolution.sim;

/**
 * Per-lineage running aggregates, one entry per founder (indexed by the
 * lineageId a lineage descends from). Bounded by the founder count, never by
 * ticks (memory discipline). A lineage that never had living members has
 * {@code firstTick = -1}; one still alive at the end has {@code extinctTick = -1}.
 */
public record LineageSummary(
        int[] peak,
        int[] finalMembers,
        int[] maxGen,
        int[] firstTick,
        int[] extinctTick,
        double[] finalEnergy
) {

    public int lineageCount() {
        return peak.length;
    }

    public boolean everLived(final int lineage) {
        return firstTick[lineage] >= 0;
    }

    public boolean aliveAtEnd(final int lineage) {
        return firstTick[lineage] >= 0 && extinctTick[lineage] < 0;
    }

    /** Ticks from first appearance to extinction (or to {@code ticks} if alive). */
    public int lifespan(final int lineage, final int ticks) {
        if (firstTick[lineage] < 0) {
            return 0;
        }
        final int end = extinctTick[lineage] < 0 ? ticks : extinctTick[lineage];
        return end - firstTick[lineage];
    }
}
