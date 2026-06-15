package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.github.luben.zstd.Zstd;

import com.simolution.kernel.runtime.Kernel;

/**
 * report-v14 frames: the {@code u} line is lean ({@code cell slot genomeId} — mass and
 * action dropped, no genome bytes, no node outputs); the raw genes live in a
 * {@link GenomeCatalogWriter} sidecar; and the frame stream is a binary sequence of
 * independently zstd-compressed {@code CHUNK_FRAMES}-frame chunks, indexed per chunk in
 * {@code .frames.idx}. The tests decode the chunks (via the idx) back to text to assert.
 */
class MapFrameWriterTest {

    /** Decompress every chunk listed in the idx and concatenate the decoded text. */
    private static String decode(ByteArrayOutputStream frames, String idxText) {
        byte[] bytes = frames.toByteArray();
        StringBuilder all = new StringBuilder();
        for (String row : idxText.lines().toList()) {
            if (row.startsWith("format ") || row.startsWith("end")) {
                continue;
            }
            String[] f = row.split(" ");
            int off = Integer.parseInt(f[2]);
            int len = Integer.parseInt(f[3]);
            byte[] comp = new byte[len];
            System.arraycopy(bytes, off, comp, 0, len);
            byte[] raw = Zstd.decompress(comp, (int) Zstd.getFrameContentSize(comp));
            all.append(new String(raw, StandardCharsets.UTF_8));
        }
        return all.toString();
    }

    private static List<String> tickLines(String out) {
        List<String> ticks = new ArrayList<>();
        out.lines().filter(l -> l.startsWith("t ")).forEach(ticks::add);
        return ticks;
    }

    private static MapFrameWriter writer(ByteArrayOutputStream frames, StringWriter idx,
                                         StringWriter catalog, int world, int ticks,
                                         int targetFrames, int from, int to) throws Exception {
        return new MapFrameWriter(frames, idx, new GenomeCatalogWriter(catalog),
                world, ticks, targetFrames, from, to);
    }

    @Test
    void windowModeEmitsEveryTickOnlyInsideTheWindow() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(frames, idx, new StringWriter(), 4, 8, 0, 3, 5);
        for (int i = 0; i < 8; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();

        assertTrue(idx.toString().contains("sample-every 1"), "window mode samples every tick");
        assertEquals(List.of("t 3", "t 4", "t 5"), tickLines(decode(frames, idx.toString())),
                "only ticks inside [3,5] emit, one frame each");
    }

    @Test
    void noWindowEveryTickByDefault() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        // targetFrames 0 -> sample every tick (default)
        MapFrameWriter w = writer(frames, idx, new StringWriter(), 4, 10, 0, -1, -1);
        for (int i = 0; i < 10; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();

        assertTrue(idx.toString().contains("sample-every 1"), "targetFrames 0 = every tick");
        assertEquals(10, tickLines(decode(frames, idx.toString())).size(),
                "a frame for every one of the 10 ticks");
    }

    @Test
    void positiveTargetFramesDownsamples() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        // 10 ticks, 5 target frames -> sample-every 2 -> ticks 2,4,6,8,10
        MapFrameWriter w = writer(frames, idx, new StringWriter(), 4, 10, 5, -1, -1);
        for (int i = 0; i < 10; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();

        assertTrue(idx.toString().contains("sample-every 2"), "10 ticks / 5 frames = every 2");
        assertEquals(List.of("t 2", "t 4", "t 6", "t 8", "t 10"),
                tickLines(decode(frames, idx.toString())));
    }

    @Test
    void genomesGoToTheCatalogNotTheFrame() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        StringWriter catalog = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(frames, idx, catalog, 4, 8, 0, 1, 5);
        for (int i = 0; i < 6; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();
        String framesText = decode(frames, idx.toString());

        assertTrue(catalog.toString().lines().anyMatch(l -> l.startsWith("g ")),
                "distinct genomes are written to the catalog as 'g <id> <count> <genes>'");
        assertFalse(framesText.contains(" * ") || framesText.contains(" ^"),
                "the frame no longer carries raw genome markers — only a genomeId");
        // u line is lean: u <cell> <slot> <genomeId> = 4 whitespace tokens
        assertTrue(framesText.lines().filter(l -> l.startsWith("u "))
                        .allMatch(l -> l.trim().split("\\s+").length == 4),
                "u line carries exactly cell, slot, genomeId");
    }

    @Test
    void noMassOrActionOrNodeOutputsInFrame() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(7L, 9, 12), 6, 12, Kernel.scatterFounders(9, 6));
        MapFrameWriter w = writer(frames, idx, new StringWriter(), 6, 8, 0, 1, 4);
        for (int i = 0; i < 5; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();
        String out = decode(frames, idx.toString());

        assertFalse(Pattern.compile("\\.[0-9]").matcher(out).find(),
                "no decimals: mass dropped, node outputs gone; cells and ids are integers");
        // no action letters: a lean u line is three integer tokens only
        assertTrue(out.lines().filter(l -> l.startsWith("u "))
                        .noneMatch(l -> l.matches(".*[HRNSEWGy.].*")),
                "no fired-action tag on the u line");
    }

    @Test
    void chunkIndexOffsetsPointAtChunkStarts() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = new MapFrameWriter(frames, idx, new GenomeCatalogWriter(new StringWriter()),
                4, 6, 0, 1, 4);
        for (int i = 0; i < 5; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        w.close();
        byte[] bytes = frames.toByteArray();

        // each chunk idx line: <firstTick> <lastTick> <off> <len> <count> — [off,off+len)
        // must zstd-decompress to a block starting at frame t <firstTick>
        boolean sawChunk = false;
        for (String row : idx.toString().lines().toList()) {
            if (row.startsWith("format ") || row.startsWith("end")) {
                continue;
            }
            sawChunk = true;
            String[] f = row.split(" ");
            int firstTick = Integer.parseInt(f[0]);
            int off = Integer.parseInt(f[2]), len = Integer.parseInt(f[3]);
            byte[] comp = new byte[len];
            System.arraycopy(bytes, off, comp, 0, len);
            String block = new String(Zstd.decompress(comp, (int) Zstd.getFrameContentSize(comp)),
                    StandardCharsets.UTF_8);
            assertTrue(block.startsWith("t " + firstTick + "\n"),
                    "chunk at off " + off + " starts at frame t " + firstTick);
            assertTrue(block.contains("\nr "), "the decoded chunk holds whole frames (t + r + units)");
        }
        assertTrue(sawChunk, "at least one chunk was sealed");
        assertTrue(idx.toString().lines().anyMatch(l -> l.startsWith("end ")), "idx has an end trailer");
    }

    @Test
    void resourceFieldIsRunLengthEncoded() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        StringWriter idx = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(frames, idx, new StringWriter(), 4, 8, 0, 1, 1);
        k.tick();
        w.maybeFrame(k.snapshot());
        w.close();
        String out = decode(frames, idx.toString());

        String rLine = out.lines().filter(l -> l.startsWith("r")).findFirst().orElseThrow();
        assertTrue(Pattern.compile("[0-9]x[0-9]+").matcher(rLine).find(),
                "resource field is DxN run-length tokens, not dense digits");
    }
}
