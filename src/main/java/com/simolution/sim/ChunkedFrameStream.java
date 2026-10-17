package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.github.luben.zstd.Zstd;

/** Groups frame text into zstd chunks of {@link #CHUNK_FRAMES} frames and indexes them; format in docs/specs/report-v14.md. */
class ChunkedFrameStream implements Closeable {

    static final int CHUNK_FRAMES = 256;

    private static final int ZSTD_LEVEL = 19;

    private final OutputStream out;
    private final Writer idx;
    private final StringBuilder chunk;
    private int firstTickInChunk = -1;
    private int lastTickInChunk = -1;
    private int framesInChunk;
    private int lastTickOverall = -1;
    private long byteOffset;

    ChunkedFrameStream(final OutputStream out, final Writer idx, final int worldWidth, final int sampleEvery)
            throws IOException {
        this.out = out;
        this.idx = idx;
        this.chunk = new StringBuilder(8 * worldWidth + 4096);
        idx.write("format v14 world " + worldWidth + " sample-every " + sampleEvery
                + " chunk " + CHUNK_FRAMES + "\n");
        idx.flush();
    }

    StringBuilder chunkText() {
        return chunk;
    }

    void frameWritten(final int tick) throws IOException {
        if (firstTickInChunk < 0) {
            firstTickInChunk = tick;
        }
        lastTickInChunk = tick;
        lastTickOverall = tick;
        framesInChunk++;
        if (framesInChunk >= CHUNK_FRAMES) {
            sealChunk();
        }
    }

    private void sealChunk() throws IOException {
        if (framesInChunk == 0) {
            return;
        }
        final byte[] compressed = Zstd.compress(chunk.toString().getBytes(StandardCharsets.UTF_8), ZSTD_LEVEL);
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

    @Override
    public void close() throws IOException {
        try (idx; out) {
            sealChunk();
            out.flush();
            idx.write("end " + lastTickOverall + "\n");
            idx.flush();
        }
    }
}
