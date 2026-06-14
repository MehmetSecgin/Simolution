package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams sampled spatial frames of a run to a {@link Writer} for the map
 * scrub-player (report-v7, extended in report-v9). One frame per sampled tick,
 * built whole and flushed in a single write — nothing is accumulated in memory
 * across frames (memory doctrine: footprint constant in tick count; the
 * trajectory lives on disk, like {@code --trace}).
 * <p>
 * The frame is just a <b>list</b>: the resource field (for the heatmap) plus one
 * line per living unit carrying its cell, slot id, lineage, generation, energy and
 * its <b>genome</b> (the raw genes). That is everything the viewer needs — map →
 * cells → unit ids → genomes — with no reconstruction and no server-side state:
 * grouping living units by genome gives the distinct genomes alive at a tick, and
 * decoding a genome gives its circuit. The cost is that an unchanged genome is
 * repeated each frame (genomes only change at birth); for long runs, sample fewer
 * frames.
 * <p>
 * Format (line-oriented, parsed by {@link LiveServer} and {@code tools/mapviz.py}):
 * <pre>
 *   world &lt;W&gt;
 *   sample-every &lt;K&gt;
 *   t &lt;tick&gt;                                  --- one frame ---
 *   r &lt;W·W resource digits 0-9, quantized to CELL_CAPACITY&gt;
 *   u &lt;cell&gt; &lt;slot&gt; &lt;lineage&gt; &lt;generation&gt; &lt;energyRounded&gt; &lt;massRounded&gt; &lt;geneCount&gt; &lt;gene×geneCount&gt; &lt;nodeOutput×NodeLayout.TOTAL&gt;
 *   ... (one u line per living unit)
 * </pre>
 * {@code r} is dense (every cell); {@code u} lines are sparse (only living units).
 * After the {@code geneCount} genes come the unit's {@code NodeLayout.TOTAL} node
 * output values for this tick — enough for the viewer to compute each connection's
 * live signal ({@code output[src] · weight}) and show which connections are actually
 * carrying signal, with no reconstruction. (Non-finite outputs serialise to JSON
 * {@code null} downstream.)
 * A frame is built and written in one flush so a reader never sees it half-written.
 */
public final class MapFrameWriter implements Closeable {

    private final Writer out;
    private final int sampleEvery;
    private final StringBuilder line;

    public MapFrameWriter(final Writer out, final int worldWidth, final int totalTicks,
                          final int targetFrames) throws IOException {
        this.out = out;
        this.sampleEvery = Math.max(1, targetFrames <= 0 ? totalTicks : totalTicks / targetFrames);
        this.line = new StringBuilder(4 * worldWidth * worldWidth + 64);
        out.write("world " + worldWidth + "\n");
        out.write("sample-every " + sampleEvery + "\n");
    }

    /**
     * Write a frame if this tick is a sample point. Call once per tick after
     * {@code observe}; reads the snapshot's resource field and, for each living
     * unit, its cell ({@code position[slot]}), slot, lineage, generation, energy
     * and genome ({@code genes[slot·maxGenes .. +geneCount]}).
     */
    public void maybeFrame(final KernelSnapshot snapshot) throws IOException {
        if (snapshot.tick % sampleEvery != 0) {
            return;
        }
        line.setLength(0);
        line.append("t ").append(snapshot.tick).append('\n');

        line.append('r').append(' ');
        for (final double r : snapshot.resourceField) {
            int level = (int) Math.round(9.0 * r / KernelConfig.CELL_CAPACITY);
            if (level < 0) {
                level = 0;
            } else if (level > 9) {
                level = 9;
            }
            line.append((char) ('0' + level));
        }
        line.append('\n');

        final double[] energy = snapshot.energy;
        final int[] position = snapshot.position;
        final long[] lineageId = snapshot.lineageId;
        final int[] generation = snapshot.generation;
        final int[] genes = snapshot.genes;
        final int[] geneCount = snapshot.geneCount;
        final int maxGenes = snapshot.maxGenes;
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            final int count = geneCount[slot];
            line.append('u')
                .append(' ').append(position[slot])
                .append(' ').append(slot)
                .append(' ').append(lineageId[slot])
                .append(' ').append(generation[slot])
                .append(' ').append(Math.round(energy[slot]))
                .append(' ').append(Math.round(snapshot.mass[slot] * 1000.0) / 1000.0)
                .append(' ').append(count);
            final int base = slot * maxGenes;
            for (int g = 0; g < count; g++) {
                line.append(' ').append(genes[base + g]);
            }
            final int nodeBase = slot * NodeLayout.TOTAL;
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                line.append(' ').append(snapshot.outputs[nodeBase + n]);
            }
            line.append('\n');
        }

        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
