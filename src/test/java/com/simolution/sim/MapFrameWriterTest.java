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
 * The windowed map sampler (tick-by-tick inspection of one phase without
 * sampling the whole run): {@code windowFrom >= 0} emits a frame every tick in
 * {@code [from, to]} and nothing else; otherwise the usual down-sampling holds.
 */
class MapFrameWriterTest {

    private static List<String> tickLines(String out) {
        return out.lines().filter(l -> l.startsWith("t ")).toList();
    }

    @Test
    void windowModeEmitsEveryTickOnlyInsideTheWindow() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = new MapFrameWriter(sw, 4, 8, 0, 3, 5);
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
    void noWindowDownsamplesAcrossTheRun() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        // 10 ticks, 5 target frames -> sample-every 2 -> ticks 2,4,6,8,10
        MapFrameWriter w = new MapFrameWriter(sw, 4, 10, 5, -1, -1);
        for (int i = 0; i < 10; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertTrue(out.contains("sample-every 2"), "10 ticks / 5 frames = every 2");
        assertEquals(List.of("t 2", "t 4", "t 6", "t 8", "t 10"), tickLines(out));
    }

    @Test
    void genomeIsDefinedOnceThenCarriedForward() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(1L, 4, 8), 4, 8, Kernel.scatterFounders(4, 4));
        MapFrameWriter w = new MapFrameWriter(sw, 4, 8, 0, 1, 5);
        for (int i = 0; i < 6; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        // arrange/act above; assert below
        assertTrue(out.contains(" * "), "a slot's first sighting defines its genome (report-v11 '*')");
        assertTrue(out.contains(" ^ ") || out.lines().anyMatch(l -> l.endsWith(" ^")),
                "a surviving slot's unchanged genome is carried forward (report-v11 '^')");
    }

    @Test
    void nodeOutputsAreRoundedNotFullPrecision() throws Exception {
        StringWriter sw = new StringWriter();
        Kernel k = new Kernel(GenomeFactory.random(7L, 9, 12), 6, 12, Kernel.scatterFounders(9, 6));
        MapFrameWriter w = new MapFrameWriter(sw, 6, 8, 0, 1, 4);
        for (int i = 0; i < 5; i++) {
            k.tick();
            w.maybeFrame(k.snapshot());
        }
        String out = sw.toString();

        assertFalse(Pattern.compile("\\.[0-9]{5,}").matcher(out).find(),
                "no value keeps 5+ decimal digits — outputs round to <=4, mass to 3 (report-v11)");
    }
}
