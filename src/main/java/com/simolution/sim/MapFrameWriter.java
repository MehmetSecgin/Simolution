package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.github.luben.zstd.Zstd;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams every-tick spatial frames of a run for the map scrub/live player, as a
 * <b>chunked, zstd-compressed</b> binary stream (report-v14). Nothing accumulates
 * across the whole run — at most one open chunk lives in memory (memory doctrine:
 * footprint constant in tick count; the trajectory lives on disk, like {@code --trace}).
 * <p>
 * <b>Lean row (report-v14).</b> The frame {@code u} line is now
 * {@code u <cell> <slot> <genomeId>} — {@code mass} and the fired {@code action} are
 * <b>gone</b> (the two high-entropy, fast-churning fields; not needed to seek — the
 * owner watches map + location + genome/lineage). The genome is a small {@code genomeId}
 * into {@link GenomeCatalogWriter}; the per-slot cache below resolves the id only when a
 * slot's genome actually changes (birth), not every frame.
 * <p>
 * <b>Chunked zstd (report-v14).</b> Frames are grouped into {@link #CHUNK_FRAMES}-frame
 * chunks, each independently {@code zstd-19}-compressed and appended to
 * {@code <base>.frames.zst}. A chunk decompresses to the familiar text block:
 * <pre>
 *   t &lt;tick&gt;
 *   r &lt;DxN RLE resource tokens&gt;
 *   u &lt;cell&gt; &lt;slot&gt; &lt;genomeId&gt;
 *   ... (one u line per living unit)
 * </pre>
 * Multi-frame chunks are what let zstd's window span frames and reach the run's ~30×
 * redundancy (a single frame compresses only ~3×). The {@code <base>.frames.idx} sidecar
 * is ASCII, one header line then one line per <b>chunk</b>:
 * <pre>
 *   format v14 world &lt;W&gt; sample-every &lt;K&gt; chunk &lt;CHUNK_FRAMES&gt;
 *   &lt;firstTick&gt; &lt;lastTick&gt; &lt;byteOffset&gt; &lt;byteLen&gt; &lt;frameCount&gt;
 *   ...
 *   end &lt;finalTick&gt;
 * </pre>
 * A viewer maps a tick to the chunk whose {@code [firstTick,lastTick]} contains it,
 * HTTP-Range-fetches {@code [byteOffset, byteOffset+byteLen)}, zstd-decompresses, and
 * scans the {@code t} lines to the frame — browser RAM is one decompressed chunk,
 * independent of run length. Live tail: the writer seals a chunk every
 * {@link #CHUNK_FRAMES} frames and flushes the idx line; a follower polls the idx for
 * new chunk lines (so follow-live lags by at most {@link #CHUNK_FRAMES} ticks). The open
 * chunk is sealed by {@link #close()}, which also writes the {@code end <finalTick>} idx
 * trailer (report-v12 trailer semantics).
 */
public final class MapFrameWriter implements Closeable {

    /** Frames per zstd chunk — the cross-frame window zstd compresses over (also seek/live granularity). */
    public static final int CHUNK_FRAMES = 256;

    private static final int ZSTD_LEVEL = 19;

    private final OutputStream out;
    private final Writer idx;
    private final GenomeCatalogWriter catalog;
    private final int sampleEvery;
    private final int windowFrom;
    private final int windowTo;

    private final StringBuilder chunk;
    private int firstTickInChunk = -1;
    private int lastTickInChunk = -1;
    private int framesInChunk;
    private int lastTickOverall = -1;
    private long byteOffset;

    /**
     * Per-slot dedup of genomes: the last gene list emitted for each slot and the catalog
     * id it resolved to, so the id is re-resolved (and hashed) only when a slot's genome
     * changes (birth), never every frame. Lazily allocated on the first frame — O(slots),
     * bounded and constant in tick count (memory doctrine).
     */
    private int[] lastGenes;
    private int[] lastCount;
    private boolean[] hasLast;
    private int[] lastGenomeId;
    private int cacheStride;

    /**
     * {@code windowFrom >= 0} selects window mode: a frame every tick in
     * {@code [windowFrom, windowTo]} and nothing outside. Otherwise {@code targetFrames}
     * sets the across-run downsampling — {@code <= 0} means a frame <b>every tick</b>
     * (default; per-tick is the owner's requirement, the size win is the lean row + zstd).
     *
     * @param out the {@code .frames.zst} stream — appended zstd chunks (binary).
     * @param idx the {@code .frames.idx} stream — one ASCII line per chunk.
     */
    public MapFrameWriter(final OutputStream out, final Writer idx, final GenomeCatalogWriter catalog,
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
        this.chunk = new StringBuilder(8 * worldWidth + 4096);
        idx.write("format v14 world " + worldWidth + " sample-every " + sampleEvery
                + " chunk " + CHUNK_FRAMES + "\n");
        idx.flush();
    }

    /**
     * Buffer a frame if this tick is a sample point; seal the chunk when it fills. Call
     * once per tick after {@code observe}; reads the snapshot's resource field and, for
     * each living unit, its cell ({@code position[slot]}), slot and genome id.
     */
    public void maybeFrame(final KernelSnapshot snapshot) throws IOException {
        if (windowFrom >= 0) {
            if (snapshot.tick < windowFrom || snapshot.tick > windowTo) {
                return;
            }
        } else if (snapshot.tick % sampleEvery != 0) {
            return;
        }
        chunk.append("t ").append(snapshot.tick).append('\n');
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
            chunk.append('u')
                .append(' ').append(position[slot])
                .append(' ').append(slot)
                .append(' ').append(genomeId)
                .append('\n');
        }

        if (firstTickInChunk < 0) {
            firstTickInChunk = snapshot.tick;
        }
        lastTickInChunk = snapshot.tick;
        lastTickOverall = snapshot.tick;
        framesInChunk++;
        if (framesInChunk >= CHUNK_FRAMES) {
            sealChunk();
        }
    }

    /** zstd-compress the open chunk, append it to the frame stream and record its index line. */
    private void sealChunk() throws IOException {
        if (framesInChunk == 0) {
            return;
        }
        final byte[] raw = chunk.toString().getBytes(StandardCharsets.UTF_8);
        final byte[] compressed = Zstd.compress(raw, ZSTD_LEVEL);
        out.write(compressed);
        out.flush();
        idx.write(firstTickInChunk + " " + lastTickInChunk + " " + byteOffset
                + " " + compressed.length + " " + framesInChunk + "\n");
        idx.flush();
        byteOffset += compressed.length;
        chunk.setLength(0);
        firstTickInChunk = -1;
        lastTickInChunk = -1;
        framesInChunk = 0;
    }

    /**
     * Append the resource field as run-length-encoded digit tokens {@code DxN}: each cell
     * quantized to 0-9 of {@code CELL_CAPACITY}, consecutive equal levels collapsed. A
     * uniform field → one token; a smooth gradient → a few.
     */
    private void appendResourceRle(final double[] field) {
        chunk.append('r');
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
                    chunk.append(' ').append((char) ('0' + runLevel)).append('x').append(runLen);
                }
                runLevel = level;
                runLen = 1;
            }
        }
        if (runLen > 0) {
            chunk.append(' ').append((char) ('0' + runLevel)).append('x').append(runLen);
        }
        chunk.append('\n');
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
        sealChunk();
        out.flush();
        out.close();
        idx.write("end " + lastTickOverall + "\n");
        idx.flush();
        idx.close();
    }
}
