package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.support.FrameHarness;

class MapFrameWriterTest {

    private static final int WORLD = 4;
    private static final int FOUNDERS = 4;
    private static final int GENES = 8;
    private static final long GENOME_SEED = 1L;

    private static FrameHarness smallWorld() {
        return FrameHarness.forWorld(WORLD).founders(GENOME_SEED, FOUNDERS, GENES);
    }

    @Test
    void windowModeEmitsEveryTickOnlyInsideTheWindow() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(8).sampling(new MapSampling.Window(3, 5));

        // act
        harness.run();

        // assert
        assertTrue(harness.idx().contains("sample-every 1"), "window mode samples every tick");
        assertEquals(List.of("t 3", "t 4", "t 5"), harness.tickLines(), "only ticks inside [3,5] emit, one frame each");
    }

    @Test
    void noWindowEveryTickByDefault() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(10).sampling(MapSampling.EVERY_TICK);

        // act
        harness.run();

        // assert
        assertTrue(harness.idx().contains("sample-every 1"), "every-tick sampling");
        assertEquals(10, harness.tickLines().size(), "a frame for every one of the 10 ticks");
    }

    @Test
    void positiveTargetFramesDownsamples() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(10).sampling(new MapSampling.Spread(5));

        // act
        harness.run();

        // assert
        assertTrue(harness.idx().contains("sample-every 2"), "10 ticks / 5 frames = every 2");
        assertEquals(List.of("t 2", "t 4", "t 6", "t 8", "t 10"), harness.tickLines());
    }

    @Test
    void genomesGoToTheCatalogNotTheFrame() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(6).sampling(new MapSampling.Window(1, 5));

        // act
        harness.run();

        // assert
        assertTrue(harness.catalog().lines().anyMatch(l -> l.startsWith("g ")),
                "distinct genomes are written to the catalog as 'g <id> <count> <genes>'");
        assertFalse(harness.framesText().contains(" * ") || harness.framesText().contains(" ^"),
                "the frame carries only a genomeId, never raw genome markers");
        assertTrue(harness.framesText().lines().filter(l -> l.startsWith("u "))
                        .allMatch(l -> l.trim().split("\\s+").length == 4),
                "u line carries exactly cell, slot, genomeId");
    }

    @Test
    void noMassOrActionOrNodeOutputsInFrame() throws Exception {
        // arrange
        FrameHarness harness = FrameHarness.forWorld(6).founders(7L, 9, 12)
                .ticks(5).sampling(new MapSampling.Window(1, 4));

        // act
        harness.run();

        // assert
        assertFalse(Pattern.compile("\\.[0-9]").matcher(harness.framesText()).find(),
                "no decimals: cells and ids are integers");
        assertTrue(harness.framesText().lines().filter(l -> l.startsWith("u "))
                        .noneMatch(l -> l.matches(".*[HRNSEWGy.].*")),
                "no fired-action tag on the u line");
    }

    @Test
    void chunkIndexOffsetsPointAtChunkStarts() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(5).sampling(new MapSampling.Window(1, 4));

        // act
        harness.run();

        // assert
        assertFalse(harness.chunks().isEmpty(), "at least one chunk was sealed");
        for (FrameHarness.Chunk chunk : harness.chunks()) {
            assertTrue(chunk.text().startsWith("t " + chunk.firstTick() + "\n"),
                    "chunk at off " + chunk.offset() + " starts at frame t " + chunk.firstTick());
            assertTrue(chunk.text().contains("\nr "), "the decoded chunk holds whole frames (t + r + units)");
        }
        assertTrue(harness.idx().lines().anyMatch(l -> l.startsWith("end ")), "idx has an end trailer");
    }

    @Test
    void resourceFieldIsRunLengthEncoded() throws Exception {
        // arrange
        FrameHarness harness = smallWorld().ticks(1).sampling(new MapSampling.Window(1, 1));

        // act
        harness.run();

        // assert
        String resourceLine = harness.framesText().lines().filter(l -> l.startsWith("r")).findFirst().orElseThrow();
        assertTrue(Pattern.compile("[0-9]x[0-9]+").matcher(resourceLine).find(),
                "resource field is DxN run-length tokens, not dense digits");
    }

    @Test
    void secondChemicalDoesNotWidenTheResourceField() throws Exception {
        // arrange
        ChemicalSpec first = new ChemicalSpec("first", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1);
        ChemicalSpec second = new ChemicalSpec("second", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1);
        FrameHarness harness = smallWorld().ticks(1).sampling(new MapSampling.Window(1, 1))
                .configure(config -> config.withChemicals(List.of(first, second)));

        // act
        harness.run();

        // assert
        String resourceLine = harness.framesText().lines().filter(l -> l.startsWith("r")).findFirst().orElseThrow();
        int cells = Pattern.compile("[0-9]x([0-9]+)").matcher(resourceLine).results()
                .mapToInt(m -> Integer.parseInt(m.group(1))).sum();
        assertEquals(WORLD * WORLD, cells);
    }
}
