package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.KernelSnapshot;

/** Writes the spatial map frames of a run into a chunked zstd stream; format in docs/specs/report-v14.md. */
public final class MapFrameWriter implements TickObserver, Closeable {

    private final ChunkedFrameStream stream;
    private final FrameSampler sampler;
    private final SlotGenomeCache genomes;
    private final ResourceLevels resourceLevels;
    private final int cellCount;

    public MapFrameWriter(final OutputStream out, final Writer idx, final GenomeCatalogWriter catalog,
            final int totalTicks, final MapSampling sampling, final EngineConfig config)
            throws IOException {
        final int worldWidth = config.world().worldWidth();
        this.cellCount = worldWidth * worldWidth;
        this.stream = new ChunkedFrameStream(out, idx, worldWidth, sampling.sampleEvery(totalTicks));
        this.sampler = FrameSampler.of(sampling, totalTicks);
        this.genomes = new SlotGenomeCache(cellCount, config.world().maxGenes(), catalog);
        this.resourceLevels = new ResourceLevels(config.chemicals().get(0).capacity());
    }

    @Override
    public void observe(final KernelSnapshot snapshot) throws IOException {
        if (!sampler.samples(snapshot.tick)) {
            return;
        }
        appendFrameHeader(snapshot.tick);
        appendResourceRuns(snapshot.resourceField);
        appendLivingUnits(snapshot);
        stream.frameWritten(snapshot.tick);
    }

    private void appendFrameHeader(final int tick) {
        stream.chunkText().append("t ").append(tick).append('\n');
    }

    private void appendResourceRuns(final double[] field) {
        final StringBuilder text = stream.chunkText();
        text.append('r');
        int runLevel = -1;
        int runLength = 0;
        for (int cell = 0; cell < cellCount; cell++) {
            final int level = resourceLevels.level(field[cell]);
            if (level == runLevel) {
                runLength++;
            } else {
                appendRun(runLevel, runLength);
                runLevel = level;
                runLength = 1;
            }
        }
        appendRun(runLevel, runLength);
        text.append('\n');
    }

    private void appendRun(final int level, final int length) {
        if (length > 0) {
            stream.chunkText().append(' ').append((char) ('0' + level)).append('x').append(length);
        }
    }

    private void appendLivingUnits(final KernelSnapshot snapshot) throws IOException {
        final double[] energy = snapshot.energy;
        final StringBuilder text = stream.chunkText();
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            text.append('u')
                    .append(' ').append(snapshot.position[slot])
                    .append(' ').append(slot)
                    .append(' ').append(genomes.catalogIdFor(snapshot, slot))
                    .append('\n');
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
