package com.simolution.sim;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * A one-shot, self-contained text dump of a replayed tick T (report-v8
 * time-travel surface): the lattice (resource heatmap + per-cell occupancy),
 * a per-unit table, and every living unit's evolved circuit. Rendered to HTML by
 * {@code tools/timetravel.py}; aggregate panels come from {@code metrics.csv} /
 * {@code events.jsonl} via DuckDB. Line-oriented so the Python parser stays
 * trivial:
 * <pre>
 *   tick &lt;T&gt;
 *   world &lt;W&gt;
 *   resource &lt;W·W digits 0-9, quantized to CELL_CAPACITY&gt;
 *   unit &lt;slot&gt; &lt;cell&gt; &lt;energy&gt; &lt;damage&gt; &lt;generation&gt; &lt;lineage&gt;
 *   ... (one per living unit, slot order)
 *   wire &lt;slot&gt; &lt;srcLocal&gt; &lt;srcName&gt; &lt;dstLocal&gt; &lt;dstName&gt; &lt;weight&gt;
 *   ... (one per connection of every living unit, slot order)
 * </pre>
 */
public final class SnapshotDump {

    private SnapshotDump() {}

    public static String render(final KernelSnapshot snapshot, final CompiledConnection[] liveConnections) {
        final StringBuilder out = new StringBuilder(4096);
        out.append("tick ").append(snapshot.tick).append('\n');
        out.append("world ").append(snapshot.worldWidth).append('\n');

        out.append("resource ");
        for (final double r : snapshot.resourceField) {
            int level = (int) Math.round(9.0 * r / KernelConfig.CELL_CAPACITY);
            if (level < 0) {
                level = 0;
            } else if (level > 9) {
                level = 9;
            }
            out.append((char) ('0' + level));
        }
        out.append('\n');

        final double[] energy = snapshot.energy;
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            out.append("unit ").append(slot)
               .append(' ').append(snapshot.position[slot])
               .append(' ').append(energy[slot])
               .append(' ').append(snapshot.damage[slot])
               .append(' ').append(snapshot.generation[slot])
               .append(' ').append(snapshot.lineageId[slot])
               .append('\n');
        }

        for (final CompiledConnection c : liveConnections) {
            final int slot = c.sourceAbsoluteIndex / NodeLayout.TOTAL;
            final int srcLocal = c.sourceAbsoluteIndex % NodeLayout.TOTAL;
            final int dstLocal = c.destinationAbsoluteIndex % NodeLayout.TOTAL;
            out.append("wire ").append(slot)
               .append(' ').append(srcLocal)
               .append(' ').append(NodeLayout.localName(srcLocal))
               .append(' ').append(dstLocal)
               .append(' ').append(NodeLayout.localName(dstLocal))
               .append(' ').append(c.weight)
               .append('\n');
        }
        return out.toString();
    }
}
