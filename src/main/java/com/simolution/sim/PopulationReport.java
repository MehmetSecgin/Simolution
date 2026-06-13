package com.simolution.sim;

import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * The final living population as CSV — one row per alive slot (energy &gt; 0) at
 * the last tick, in slot order, so byte-deterministic. Maps a slot to the
 * lineage and generation it belongs to, pairing with the .popwiring.csv sidecar
 * (the slots' evolved circuits) so a descendant can be tied back to its founder
 * lineage. Bounded by the slot pool, written once at end of run.
 * Columns documented in docs/specs/report-v6.md.
 */
public final class PopulationReport {

    public static final String HEADER = "slot,lineage,generation,energy,damage";

    private PopulationReport() {}

    public static String render(final KernelSnapshot snapshot) {
        final StringBuilder out = new StringBuilder(2048);
        out.append(HEADER).append('\n');
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] <= 0.0) {
                continue;
            }
            out.append(slot)
               .append(',').append(snapshot.lineageId[slot])
               .append(',').append(snapshot.generation[slot])
               .append(',').append(snapshot.energy[slot])
               .append(',').append(snapshot.damage[slot])
               .append('\n');
        }
        return out.toString();
    }
}
