package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams lean every-tick spatial frames of a run for the map scrub/live player
 * (report-v13). One frame per sampled tick, built whole and flushed in a single
 * write — nothing accumulates in memory across frames (memory doctrine: footprint
 * constant in tick count; the trajectory lives on disk, like {@code --trace}).
 * <p>
 * report-v13 makes the frame <b>lean</b>. The churn measurement that drove the
 * redesign: standing population is tiny (~33–56) while births run to thousands per
 * tick, so a <i>full</i> frame every tick is cheap, but the old {@code u} line was
 * fat — it re-emitted the unit's 32-gene genome and all 24 node-output floats every
 * frame (~84 MB/run, 96 % of it here). Now:
 * <ul>
 *   <li><b>Genome → catalog id.</b> The raw genes leave the frame entirely; the
 *       {@code u} line carries a small {@code genomeId} into {@link GenomeCatalogWriter}
 *       (global write-once dedup). The per-slot cache below resolves the id only when
 *       a slot's genome actually changes (birth), not every frame.</li>
 *   <li><b>No node outputs.</b> The viewer does not need per-node activations
 *       ("not extreme details"); they are gone.</li>
 *   <li><b>Fired action.</b> A single tag per unit — the dominant action node
 *       (argmax over the meaningful effectors, {@code .} if none drives) — derived in
 *       the harness from the post-evaluate snapshot. This is interpretation, not a
 *       kernel concept; the kernel still privileges no effector.</li>
 *   <li><b>RLE resource field.</b> The dense {@code r} digit field is run-length
 *       encoded ({@code DxN} tokens), so a uniform or smoothly-graded field costs a
 *       handful of tokens instead of {@code W·W} digits.</li>
 * </ul>
 * Format (line-oriented, parsed client-side by {@code live.html} / {@code tools/mapviz.py}):
 * <pre>
 *   format v13
 *   world &lt;W&gt;
 *   sample-every &lt;K&gt;
 *   t &lt;tick&gt;                                  --- one frame ---
 *   r &lt;DxN tokens, digit 0-9 quantized to CELL_CAPACITY, run-length encoded&gt;
 *   u &lt;cell&gt; &lt;slot&gt; &lt;massRounded&gt; &lt;action&gt; &lt;genomeId&gt;
 *   ... (one u line per living unit)
 * </pre>
 * A frame is built and written in one flush so a reader never sees it half-written.
 * {@link #close()} appends a final {@code end} line so a client tailing the file knows
 * the run finished (report-v12 trailer semantics).
 */
public final class MapFrameWriter implements Closeable {

    private final Writer out;
    private final Writer idx;
    private final GenomeCatalogWriter catalog;
    private final int sampleEvery;
    private final int windowFrom;
    private final int windowTo;
    private final StringBuilder line;
    private long byteOffset;

    /**
     * Per-slot dedup of genomes: the last gene list emitted for each slot and the
     * catalog id it resolved to, so the id is re-resolved (and hashed) only when a
     * slot's genome changes (birth), never every frame. Lazily allocated on the
     * first frame — O(slots), bounded and constant in tick count (memory doctrine).
     */
    private int[] lastGenes;
    private int[] lastCount;
    private boolean[] hasLast;
    private int[] lastGenomeId;
    private int cacheStride;

    /**
     * {@code windowFrom >= 0} selects <b>window mode</b>: a frame every tick in
     * {@code [windowFrom, windowTo]} and nothing outside. Otherwise
     * {@code targetFrames} sets the across-run downsampling — {@code <= 0} means a
     * frame <b>every tick</b> (report-v13 default; cheap now that frames are lean).
     */
    /**
     * @param idx the {@code .frames.idx} stream — one line per frame
     *            {@code <tick> <byteOffset> <byteLen>} into {@code .frames}, so a
     *            viewer can Range-fetch a single frame on demand (report-v13 §index)
     *            instead of loading the whole run. All content is ASCII, so char
     *            length == byte length; offsets start after the header block.
     */
    public MapFrameWriter(final Writer out, final Writer idx, final GenomeCatalogWriter catalog,
                          final int worldWidth, final int totalTicks, final int targetFrames,
                          final int windowFrom, final int windowTo) throws IOException {
        this.out = out;
        this.idx = idx;
        this.catalog = catalog;
        this.windowFrom = windowFrom;
        this.windowTo = windowTo;
        this.sampleEvery = windowFrom >= 0
                ? 1
                : (targetFrames <= 0 ? 1 : Math.max(1, totalTicks / targetFrames));
        this.line = new StringBuilder(4 * worldWidth + 256);
        final String header = "format v13\nworld " + worldWidth + "\nsample-every " + sampleEvery + "\n";
        out.write(header);
        byteOffset = header.length();
    }

    /**
     * Write a frame if this tick is a sample point. Call once per tick after
     * {@code observe}; reads the snapshot's resource field and, for each living
     * unit, its cell ({@code position[slot]}), slot, mass, fired action and genome id.
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

        appendResourceRle(snapshot.resourceField);

        final double[] energy = snapshot.energy;
        final int[] position = snapshot.position;
        final int[] genes = snapshot.genes;
        final int[] geneCount = snapshot.geneCount;
        final int maxGenes = snapshot.maxGenes;
        if (lastGenes == null) {
            cacheStride = maxGenes;
            lastGenes = new int[energy.length * maxGenes];
            lastCount = new int[energy.length];
            hasLast = new boolean[energy.length];
            lastGenomeId = new int[energy.length];
        }
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            final int count = geneCount[slot];
            final int base = slot * maxGenes;
            final int genomeId;
            if (genomeUnchanged(slot, base, count, genes)) {
                genomeId = lastGenomeId[slot];
            } else {
                genomeId = catalog.idOf(genes, base, count);
                rememberGenome(slot, base, count, genes, genomeId);
            }
            line.append('u')
                .append(' ').append(position[slot])
                .append(' ').append(slot)
                .append(' ').append(Math.round(snapshot.mass[slot] * 1000.0) / 1000.0)
                .append(' ').append(firedAction(snapshot.outputs, slot))
                .append(' ').append(genomeId)
                .append('\n');
        }

        final String frame = line.toString();
        idx.write(snapshot.tick + " " + byteOffset + " " + frame.length() + "\n");
        idx.flush();
        byteOffset += frame.length();
        out.write(frame);
        out.flush();
    }

    /**
     * Append the resource field as run-length-encoded digit tokens {@code DxN}: each
     * cell quantized to 0-9 of {@code CELL_CAPACITY}, consecutive equal levels collapsed.
     * A uniform field → one token; a smooth gradient → a few.
     */
    private void appendResourceRle(final double[] field) {
        line.append('r');
        int runLevel = -1;
        int runLen = 0;
        for (final double r : field) {
            int level = (int) Math.round(9.0 * r / KernelConfig.CELL_CAPACITY);
            if (level < 0) {
                level = 0;
            } else if (level > 9) {
                level = 9;
            }
            if (level == runLevel) {
                runLen++;
            } else {
                if (runLen > 0) {
                    line.append(' ').append((char) ('0' + runLevel)).append('x').append(runLen);
                }
                runLevel = level;
                runLen = 1;
            }
        }
        if (runLen > 0) {
            line.append(' ').append((char) ('0' + runLevel)).append('x').append(runLen);
        }
        line.append('\n');
    }

    /**
     * The dominant action this tick: argmax over the meaningful effector outputs of
     * this slot, mapped to a single letter ({@code y H R N S E W G}); {@code .} when
     * no effector carries a positive signal. A harness-side summary, not a kernel concept.
     */
    private static char firedAction(final double[] outputs, final int slot) {
        final int base = slot * NodeLayout.TOTAL + NodeLayout.ACTION_OFFSET;
        double best = 0.0;
        int bestA = -1;
        for (int a = 0; a < NodeLayout.Action.MEANINGFUL_COUNT; a++) {
            final double v = outputs[base + a];
            if (v > best) {
                best = v;
                bestA = a;
            }
        }
        return switch (bestA) {
            case NodeLayout.Action.Y -> 'y';
            case NodeLayout.Action.HARVEST -> 'H';
            case NodeLayout.Action.REPRODUCE -> 'R';
            case NodeLayout.Action.MOVE_N -> 'N';
            case NodeLayout.Action.MOVE_S -> 'S';
            case NodeLayout.Action.MOVE_E -> 'E';
            case NodeLayout.Action.MOVE_W -> 'W';
            case NodeLayout.Action.GROW -> 'G';
            default -> '.';
        };
    }

    /** True if this slot's current genome equals the last one resolved for it (→ reuse cached id). */
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

    /** Record this slot's genome + its resolved catalog id for the unchanged-check next frame. */
    private void rememberGenome(final int slot, final int base, final int count,
                                final int[] genes, final int genomeId) {
        final int cacheBase = slot * cacheStride;
        for (int g = 0; g < count; g++) {
            lastGenes[cacheBase + g] = genes[base + g];
        }
        lastCount[slot] = count;
        hasLast[slot] = true;
        lastGenomeId[slot] = genomeId;
    }

    @Override
    public void close() throws IOException {
        out.write("end\n");
        out.flush();
        out.close();
        idx.flush();
        idx.close();
    }
}
