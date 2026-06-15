package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;

/**
 * report-v13 lean frames: the windowed sampler still emits a frame every tick in
 * {@code [from, to]} (and nothing else) or downsamples across the run; the {@code u}
 * line is now lean (cell, slot, mass, action, genomeId — no genome bytes, no node
 * outputs) and the raw genes live in a {@link GenomeCatalogWriter} sidecar.
 */
class MapFrameWriterTest {

    private static List<String> tickLines(String out) {
        return out.lines().filter(l -> l.startsWith("t ")).toList();
    }

    private static MapFrameWriter writer(StringWriter frames, StringWriter catalog,
                                         int world, int ticks, int targetFrames,
                                         int from, int to) throws Exception {
        return new MapFrameWriter(frames, new GenomeCatalogWriter(catalog),
                world, ticks, targetFrames, from, to);
    }

    @Test
    void windowModeEmitsEveryTickOnlyInsideTheWindow() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(sw, new StringWriter(), 4, 8, 0, 3, 5);
        for (int i = 0; i < 8; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertTrue(out.contains("sample-every 1"), "window mode samples every tick");
        assertEquals(List.of("t 3", "t 4", "t 5"), tickLines(out),
                "only ticks inside [3,5] emit, one frame each");
    }

    @Test
    void noWindowEveryTickByDefault() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        // targetFrames 0 -> sample every tick (report-v13 default)
        MapFrameWriter w = writer(sw, new StringWriter(), 4, 10, 0, -1, -1);
        for (int i = 0; i < 10; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertTrue(out.contains("sample-every 1"), "targetFrames 0 = every tick");
        assertEquals(10, tickLines(out).size(), "a frame for every one of the 10 ticks");
    }

    @Test
    void positiveTargetFramesDownsamples() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        // 10 ticks, 5 target frames -> sample-every 2 -> ticks 2,4,6,8,10
        MapFrameWriter w = writer(sw, new StringWriter(), 4, 10, 5, -1, -1);
        for (int i = 0; i < 10; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertTrue(out.contains("sample-every 2"), "10 ticks / 5 frames = every 2");
        assertEquals(List.of("t 2", "t 4", "t 6", "t 8", "t 10"), tickLines(out));
    }

    @Test
    void genomesGoToTheCatalogNotTheFrame() throws Exception {
        StringWriter frames = new StringWriter();
        StringWriter catalog = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(frames, catalog, 4, 8, 0, 1, 5);
        for (int i = 0; i < 6; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String framesOut = frames.toString();
        String catalogOut = catalog.toString();

        assertTrue(catalogOut.lines().anyMatch(l -> l.startsWith("g ")),
                "distinct genomes are written to the catalog as 'g <id> <count> <genes>'");
        assertFalse(framesOut.contains(" * ") || framesOut.contains(" ^"),
                "the frame no longer carries raw genome markers — only a genomeId");
        // u line is lean: u <cell> <slot> <mass> <action> <genomeId> = 6 whitespace tokens
        assertTrue(framesOut.lines().filter(l -> l.startsWith("u "))
                        .allMatch(l -> l.trim().split("\\s+").length == 6),
                "u line carries exactly cell, slot, mass, action, genomeId");
    }

    @Test
    void noNodeOutputFloatsInFrame() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(7L, 9, 12), 6, 12, Kernel.scatterFounders(9, 6));
        MapFrameWriter w = writer(sw, new StringWriter(), 6, 8, 0, 1, 4);
        for (int i = 0; i < 5; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertFalse(Pattern.compile("\\.[0-9]{5,}").matcher(out).find(),
                "no value keeps 5+ decimal digits — mass rounds to 3, node outputs are gone");
    }

    @Test
    void resourceFieldIsRunLengthEncoded() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = writer(sw, new StringWriter(), 4, 8, 0, 1, 1);
        k.tick();
        w.maybeFrame(k.snapshot());
        String out = sw.toString();

        String rLine = out.lines().filter(l -> l.startsWith("r")).findFirst().orElseThrow();
        assertTrue(Pattern.compile("[0-9]x[0-9]+").matcher(rLine).find(),
                "resource field is DxN run-length tokens, not dense digits");
    }
}
