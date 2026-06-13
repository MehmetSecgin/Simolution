package com.simolution.sim;

/**
 * Per-lineage detail as CSV — one row per founder lineage that ever had a
 * living member, in lineage-id order, so two runs of the same config produce a
 * byte-identical file. The reproduction companion to the per-unit .units.csv:
 * it tracks the descendants the founder-scoped CSV cannot see. Bounded by the
 * founder count, not ticks. Columns documented in docs/specs/report-v6.md.
 */
public final class LineageReport {

    public static final String HEADER =
            "lineage,first_tick,extinct_tick,alive_at_end,peak_members,"
            + "final_members,max_generation,lifespan,final_energy";

    private LineageReport() {}

    public static String render(final LineageSummary lineages, final int ticks) {
        final StringBuilder out = new StringBuilder(64 * (lineages.lineageCount() + 1));
        out.append(HEADER).append('\n');

        for (int l = 0; l < lineages.lineageCount(); l++) {
            if (!lineages.everLived(l)) {
                continue;
            }
            out.append(l)
               .append(',').append(lineages.firstTick()[l])
               .append(',').append(lineages.extinctTick()[l])
               .append(',').append(lineages.aliveAtEnd(l) ? 1 : 0)
               .append(',').append(lineages.peak()[l])
               .append(',').append(lineages.finalMembers()[l])
               .append(',').append(lineages.maxGen()[l])
               .append(',').append(lineages.lifespan(l, ticks))
               .append(',').append(lineages.finalEnergy()[l])
               .append('\n');
        }
        return out.toString();
    }
}
