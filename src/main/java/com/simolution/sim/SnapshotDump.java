package com.simolution.sim;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.WorldState;

/** One-shot text dump of a replayed tick, read by tools/timetravel.py. */
public final class SnapshotDump {

    private SnapshotDump() {}

    public static String render(final WorldState snapshot, final CompiledConnection[] liveConnections,
            final EngineConfig config) {
        final StringBuilder out = new StringBuilder(4096);
        appendHeader(out, snapshot);
        appendHeatmap(out, snapshot, config.chemicals().get(0).capacity());
        appendUnits(out, snapshot);
        appendWires(out, liveConnections);
        return out.toString();
    }

    private static void appendHeader(final StringBuilder out, final WorldState snapshot) {
        out.append("tick ").append(snapshot.tick).append('\n');
        out.append("world ").append(snapshot.worldWidth).append('\n');
    }

    private static void appendHeatmap(final StringBuilder out, final WorldState snapshot,
            final double capacity) {
        out.append("resource ");
        final int cellCount = snapshot.worldWidth * snapshot.worldWidth;
        for (int cell = 0; cell < cellCount; cell++) {
            out.append((char) ('0' + ResourceLevels.level(snapshot.cellChem[cell], capacity)));
        }
        out.append('\n');
    }

    private static void appendUnits(final StringBuilder out, final WorldState snapshot) {
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] <= 0.0) {
                continue;
            }
            out.append("unit ").append(slot)
                    .append(' ').append(snapshot.position[slot])
                    .append(' ').append(snapshot.energy[slot])
                    .append(' ').append(snapshot.damage[slot])
                    .append(' ').append(snapshot.generation[slot])
                    .append(' ').append(snapshot.lineageId[slot])
                    .append('\n');
        }
    }

    private static void appendWires(final StringBuilder out, final CompiledConnection[] liveConnections) {
        for (CompiledConnection c : liveConnections) {
            final int slot = c.sourceAbsoluteIndex() / NodeLayout.TOTAL;
            final int srcLocal = c.sourceAbsoluteIndex() % NodeLayout.TOTAL;
            final int dstLocal = c.destinationAbsoluteIndex() % NodeLayout.TOTAL;
            out.append("wire ").append(slot)
                    .append(' ').append(srcLocal)
                    .append(' ').append(NodeLayout.localName(srcLocal))
                    .append(' ').append(dstLocal)
                    .append(' ').append(NodeLayout.localName(dstLocal))
                    .append(' ').append(c.weight())
                    .append('\n');
        }
    }
}
