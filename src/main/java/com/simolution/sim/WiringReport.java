package com.simolution.sim;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

/**
 * A unit's signature: its decoded wiring, one row per connection. This is the
 * raw structure behind every other per-unit metric — what the genome actually
 * built. Deterministic, fixed order (units ascending, connections in genome
 * order), so it diffs cleanly. Columns documented in docs/specs/report-v6.md.
 * <p>
 * Bounded by total connections (units × genes), constant in ticks — memory
 * discipline holds.
 */
public final class WiringReport {

    public static final String HEADER =
            "unit,conn_index,src_local,src_name,dst_local,dst_name,weight,meaningful";

    private WiringReport() {}

    public static String render(final CompiledConnection[] connections) {
        final StringBuilder out = new StringBuilder(64 * (connections.length + 1));
        out.append(HEADER).append('\n');

        int unit = -1;
        int connIndex = 0;
        for (final CompiledConnection c : connections) {
            final int srcUnit = c.sourceAbsoluteIndex / NodeLayout.TOTAL;
            if (srcUnit != unit) {
                unit = srcUnit;
                connIndex = 0;
            }
            final int srcLocal = c.sourceAbsoluteIndex % NodeLayout.TOTAL;
            final int dstLocal = c.destinationAbsoluteIndex % NodeLayout.TOTAL;
            final boolean meaningful =
                    NodeLayout.isMeaningful(srcLocal) && NodeLayout.isMeaningful(dstLocal);

            out.append(unit)
               .append(',').append(connIndex)
               .append(',').append(srcLocal)
               .append(',').append(NodeLayout.localName(srcLocal))
               .append(',').append(dstLocal)
               .append(',').append(NodeLayout.localName(dstLocal))
               .append(',').append(c.weight)
               .append(',').append(meaningful ? 1 : 0)
               .append('\n');
            connIndex++;
        }
        return out.toString();
    }
}
