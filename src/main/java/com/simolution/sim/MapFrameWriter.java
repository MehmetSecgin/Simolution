package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams sampled spatial frames of a run to a {@link Writer} for the map
 * scrub-player (report-v7, extended in report-v9, compacted in report-v11). One frame per sampled tick,
 * built whole and flushed in a single write — nothing is accumulated in memory
 * across frames (memory doctrine: footprint constant in tick count; the
 * trajectory lives on disk, like {@code --trace}).
 * <p>
 * The frame is a <b>list</b>: the resource field (for the heatmap) plus one line per
 * living unit carrying its cell, slot, lineage, generation, energy, mass, <b>genome</b>
 * (the raw genes) and per-tick node outputs — everything the viewer needs (map → cells
 * → ids → genomes → circuits) with no reconstruction and no server-side state.
 * <p>
 * report-v11 removes two wastes that made these files enormous (a long run was hundreds
 * of MB, over half of it redundant):
 * <ul>
 *   <li><b>Genome dedup.</b> A genome only changes at birth, yet report-v9 re-emitted the
 *       full gene list every frame. We keep the last genome emitted <i>per slot</i>
 *       ({@code lastGenes}, O(slots) — bounded, constant in tick count) and emit the genes
 *       only when they differ ({@code * <count> <genes>}); an unchanged unit emits the
 *       marker {@code ^} and the reader carries its genome forward for that slot.</li>
 *   <li><b>Output rounding.</b> Node outputs were written at full {@code double} precision
 *       (e.g. {@code -0.4094014230944101}); the viewer only needs them to light up
 *       connections carrying signal, so they are rounded to ≤4 decimals (exact zero →
 *       {@code 0}, non-finite → {@code NaN} → JSON {@code null} downstream).</li>
 * </ul>
 * The viewer ({@code src/main/resources/live.html}, report-v12) carries genomes forward
 * per slot when it parses the file client-side, so {@code ^} lines cost nothing while the
 * circuit inspector still sees every unit's full genome.
 * <p>
 * Format (line-oriented, parsed client-side by {@code live.html} and by {@code tools/mapviz.py}):
 * <pre>
 *   world &lt;W&gt;
 *   sample-every &lt;K&gt;
 *   t &lt;tick&gt;                                  --- one frame ---
 *   r &lt;W·W resource digits 0-9, quantized to CELL_CAPACITY&gt;
 *   u &lt;cell&gt; &lt;slot&gt; &lt;lineage&gt; &lt;generation&gt; &lt;energyRounded&gt; &lt;massRounded&gt; &lt;genome&gt; &lt;nodeOutput×NodeLayout.TOTAL&gt;
 *   ... (one u line per living unit)
 * </pre>
 * where {@code <genome>} is {@code * <geneCount> <gene×geneCount>} (defined/changed for this
 * slot) or the marker {@code ^} (unchanged since this slot's last definition). {@code r} is
 * dense (every cell); {@code u} lines are sparse (only living units). A frame is built and
 * written in one flush so a reader never sees it half-written. {@link #close()} appends a
 * final {@code end} line so a client tailing the file knows the run finished (report-v12).
 */
public final class MapFrameWriter implements Closeable {

    private final Writer out;
    private final int sampleEvery;
    private final int windowFrom;
    private final int windowTo;
    private final StringBuilder line;

    /**
     * Per-slot dedup of genomes (report-v11): the last gene list emitted for each slot,
     * so an unchanged genome is written once (as {@code *}) and thereafter referenced
     * (as {@code ^}). Lazily allocated on the first frame from {@code snapshot.maxGenes}
     * and the slot count — O(slots), bounded and constant in tick count (memory doctrine);
     * a single one-time allocation, never per tick.
     */
    private int[] lastGenes;
    private int[] lastCount;
    private boolean[] hasLast;
    private int cacheStride;

    /**
     * {@code windowFrom >= 0} selects <b>window mode</b>: a frame every tick in
     * {@code [windowFrom, windowTo]} and nothing outside, so any phase can be
     * tick-stepped without sampling the whole run (which, with genome-carrying
     * frames, is gigabytes). Otherwise the usual {@code targetFrames} downsampling
     * across the run applies.
     */
    public MapFrameWriter(final Writer out, final int worldWidth, final int totalTicks,
                          final int targetFrames, final int windowFrom, final int windowTo) throws IOException {
        this.out = out;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.sampleEvery = windowFrom >= 0
                ? 1
                : Math.max(1, targetFrames <= 0 ? totalTicks : totalTicks / targetFrames);
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
        if (windowFrom >= 0) {
            if (snapshot.tick < windowFrom || snapshot.tick > windowTo) {
                return;
            }
        } else if (snapshot.tick % sampleEvery != 0) {
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
        if (lastGenes == null) {
            cacheStride = maxGenes;
            lastGenes = new int[energy.length * maxGenes];
            lastCount = new int[energy.length];
            hasLast = new boolean[energy.length];
        }
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            final int count = geneCount[slot];
            final int base = slot * maxGenes;
            line.append('u')
                .append(' ').append(position[slot])
                .append(' ').append(slot)
                .append(' ').append(lineageId[slot])
                .append(' ').append(generation[slot])
                .append(' ').append(Math.round(energy[slot]))
                .append(' ').append(Math.round(snapshot.mass[slot] * 1000.0) / 1000.0);

            if (genomeUnchanged(slot, base, count, genes)) {
                line.append(" ^");
            } else {
                line.append(" * ").append(count);
                for (int g = 0; g < count; g++) {
                    line.append(' ').append(genes[base + g]);
                }
                rememberGenome(slot, base, count, genes);
            }

            final int nodeBase = slot * NodeLayout.TOTAL;
            for (int n = 0; n < NodeLayout.TOTAL; n++) {
                line.append(' ');
                appendRounded(line, snapshot.outputs[nodeBase + n]);
            }
            line.append('\n');
        }

        out.write(line.toString());
        out.flush();
    }

    /** True if this slot's current genome equals the last one emitted for it (→ emit {@code ^}). */
    private boolean genomeUnchanged(final int slot, final int base, final int count, final int[] genes) {
        if (!hasLast[slot] || lastCount[slot] != count) {
            return false;
        }
        final int cacheBase = slot * cacheStride;
        for (int g = 0; g < count; g++) {
            if (lastGenes[cacheBase + g] != genes[base + g]) {
                return false;
            }
        }
        return true;
    }

    /** Record this slot's genome as the last emitted, so later identical frames emit {@code ^}. */
    private void rememberGenome(final int slot, final int base, final int count, final int[] genes) {
        final int cacheBase = slot * cacheStride;
        for (int g = 0; g < count; g++) {
            lastGenes[cacheBase + g] = genes[base + g];
        }
        lastCount[slot] = count;
        hasLast[slot] = true;
    }

    /**
     * Append {@code v} rounded to at most 4 decimals, allocation-free, trailing zeros
     * stripped ({@code -0.4094014230944101} → {@code -0.4094}, {@code 0.0050} → {@code 0.005},
     * exact zero → {@code 0}). Non-finite → {@code NaN} (the readers map that to JSON
     * {@code null}). The fraction is rebuilt from the integer remainder so no floating-point
     * tail leaks through, as {@code append(double)} would print.
     */
    static void appendRounded(final StringBuilder sb, final double v) {
        if (!Double.isFinite(v)) {
            sb.append("NaN");
            return;
        }
        long scaled = Math.round(v * 10000.0);
        if (scaled == 0L) {
            sb.append('0');
            return;
        }
        if (scaled < 0L) {
            sb.append('-');
            scaled = -scaled;
        }
        sb.append(scaled / 10000L);
        final int fp = (int) (scaled % 10000L);
        if (fp != 0) {
            final int d0 = fp / 1000, d1 = (fp / 100) % 10, d2 = (fp / 10) % 10, d3 = fp % 10;
            sb.append('.').append((char) ('0' + d0));
            if (d1 != 0 || d2 != 0 || d3 != 0) {
                sb.append((char) ('0' + d1));
            }
            if (d2 != 0 || d3 != 0) {
                sb.append((char) ('0' + d2));
            }
            if (d3 != 0) {
                sb.append((char) ('0' + d3));
            }
        }
    }

    @Override
    public void close() throws IOException {
        out.write("end\n");
        out.flush();
        out.close();
    }
}
