package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams sampled spatial frames of a run to a {@link Writer} for the map
 * scrub-player (report-v7). One frame per sampled tick, written and flushed
 * immediately — nothing is accumulated in memory (memory doctrine: footprint
 * constant in tick count; the trajectory lives on disk, like {@code --trace}).
 * <p>
 * Format (line-oriented, parsed by {@code tools/mapviz.py}):
 * <pre>
 *   world &lt;W&gt;
 *   sample-every &lt;K&gt;
 *   t &lt;tick&gt;
 *   r &lt;W·W resource digits 0-9, quantized to CELL_CAPACITY&gt;
 *   o &lt;space-separated cell:lineageId for each living unit&gt;
 *   ... (t/r/o repeated per sampled frame)
 * </pre>
 * The resource line is dense (every cell, for the heatmap); occupants are sparse
 * (only living cells), since most of the grid is usually empty.
 */
public final class MapFrameWriter implements Closeable {

    private final Writer out;
    private final int sampleEvery;
    private final StringBuilder line;

    public MapFrameWriter(final Writer out, final int worldWidth, final int totalTicks,
                          final int targetFrames) throws IOException {
        this.out = out;
        this.sampleEvery = Math.max(1, targetFrames <= 0 ? totalTicks : totalTicks / targetFrames);
        this.line = new StringBuilder(2 * worldWidth * worldWidth + 64);
        out.write("world " + worldWidth + "\n");
        out.write("sample-every " + sampleEvery + "\n");
    }

    /**
     * Write a frame if this tick is a sample point. Call once per tick after
     * {@code observe}; reads the snapshot's resource field and per-unit position
     * / lineage (slot index irrelevant — a cell is {@code position[slot]}).
     */
    public void maybeFrame(final KernelSnapshot snapshot) throws IOException {
        if (snapshot.tick % sampleEvery != 0) {
            return;
        }
        line.setLength(0);
        line.append("t ").append(snapshot.tick).append('\n');

        line.append('r');
        line.append(' ');
        final double[] field = snapshot.resourceField;
        for (final double r : field) {
            int level = (int) Math.round(9.0 * r / KernelConfig.CELL_CAPACITY);
            if (level < 0) {
                level = 0;
            } else if (level > 9) {
                level = 9;
            }
            line.append((char) ('0' + level));
        }
        line.append('\n');

        line.append('o');
        final double[] energy = snapshot.energy;
        final int[] position = snapshot.position;
        final long[] lineageId = snapshot.lineageId;
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            line.append(' ').append(position[slot]).append(':').append(lineageId[slot]);
        }
        line.append('\n');

        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
