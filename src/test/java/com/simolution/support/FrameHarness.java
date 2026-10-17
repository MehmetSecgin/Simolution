package com.simolution.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.github.luben.zstd.Zstd;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.GenomeCatalogWriter;
import com.simolution.sim.MapFrameWriter;
import com.simolution.sim.MapSampling;

public final class FrameHarness {

    private static final long FOUNDER_SEED = 1L;
    private static final int DEFAULT_FOUNDERS = 4;
    private static final int DEFAULT_GENES = 8;

    public record Chunk(
            int firstTick,
            int offset,
            String text
    ) {}

    private final int width;
    private int founders = DEFAULT_FOUNDERS;
    private int genes = DEFAULT_GENES;
    private long genomeSeed = FOUNDER_SEED;
    private int ticks = 1;
    private MapSampling sampling = MapSampling.EVERY_TICK;
    private UnaryOperator<EngineConfig> configuration = UnaryOperator.identity();
    private final ByteArrayOutputStream frames = new ByteArrayOutputStream();
    private final StringWriter idx = new StringWriter();
    private final StringWriter catalog = new StringWriter();

    private FrameHarness(final int width) {
        this.width = width;
    }

    public static FrameHarness forWorld(final int width) {
        return new FrameHarness(width);
    }

    public FrameHarness founders(final long seed, final int count, final int genesPerFounder) {
        this.genomeSeed = seed;
        this.founders = count;
        this.genes = genesPerFounder;
        return this;
    }

    public FrameHarness ticks(final int ticks) {
        this.ticks = ticks;
        return this;
    }

    public FrameHarness sampling(final MapSampling sampling) {
        this.sampling = sampling;
        return this;
    }

    public FrameHarness configure(final UnaryOperator<EngineConfig> configuration) {
        this.configuration = configuration;
        return this;
    }

    public FrameHarness run() throws IOException {
        final EngineConfig config = configuration.apply(EngineConfig.defaults(0L, width, genes));
        try (Kernel kernel = Worlds.randomKernel(genomeSeed, founders, genes, config);
                MapFrameWriter writer = new MapFrameWriter(frames, idx, new GenomeCatalogWriter(catalog),
                        ticks, sampling, config)) {
            for (int i = 0; i < ticks; i++) {
                kernel.tick();
                writer.observe(kernel.snapshot());
            }
        }
        return this;
    }

    public String idx() {
        return idx.toString();
    }

    public String catalog() {
        return catalog.toString();
    }

    public List<Chunk> chunks() {
        final byte[] bytes = frames.toByteArray();
        final List<Chunk> chunks = new ArrayList<>();
        for (final String row : idx().lines().toList()) {
            if (row.startsWith("format ") || row.startsWith("end")) {
                continue;
            }
            final String[] fields = row.split(" ");
            final int offset = Integer.parseInt(fields[2]);
            final byte[] compressed = new byte[Integer.parseInt(fields[3])];
            System.arraycopy(bytes, offset, compressed, 0, compressed.length);
            final byte[] raw = Zstd.decompress(compressed, (int) Zstd.getFrameContentSize(compressed));
            chunks.add(new Chunk(Integer.parseInt(fields[0]), offset, new String(raw, StandardCharsets.UTF_8)));
        }
        return chunks;
    }

    public String framesText() {
        final StringBuilder all = new StringBuilder();
        chunks().forEach(chunk -> all.append(chunk.text()));
        return all.toString();
    }

    public List<String> tickLines() {
        return framesText().lines().filter(line -> line.startsWith("t ")).toList();
    }
}
