package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Kernel;

class DynamicsObserverTest {

    private static final int[] FEEDBACK_GENOME = {
            GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 2048)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                       .toInternal(NodeLayout.Internal.DELAY)
                       .weightRaw((short) 512)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                       .toInternal(NodeLayout.Internal.ADD)
                       .weightRaw((short) 512)
                       .build(),
            GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                       .toAction(NodeLayout.Action.Y)
                       .weightRaw((short) 512)
                       .build()
    };

    private static final int WORLD = 64;

    private static Kernel kernelOf(int[][] genomes) {
        EngineConfig engine = EngineConfig.v5Defaults(0L, WORLD, 32);
        return new Kernel(genomes, java.util.stream.IntStream.range(0, genomes.length).toArray(), engine, 1);
    }

    private static DynamicsSummary runAndSummarize(int[][] genomes, int ticks) {
        try (Kernel kernel = kernelOf(genomes)) {
            DynamicsObserver observer = new DynamicsObserver();
            for (int i = 0; i < ticks; i++) {
                kernel.tick();
                observer.observe(kernel.snapshot());
            }
            return observer.summarize(kernel.stateDigest());
        }
    }

    @Test
    void emptyGenomeUnitNeverPropagates() {
        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {{}}, 10);

        // assert
        assertEquals(10, summary.ticks());
        assertEquals(0, summary.ticksWithAnyActivity());
        assertEquals(0, summary.propagationsTotal());
    }

    @Test
    void feedbackGenomePropagatesFromTheThirdTick() {
        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {FEEDBACK_GENOME}, 50);

        // assert
        assertTrue(summary.ticksWithAnyActivity() > 0);
        assertTrue(summary.ticksWithAnyActivity() < 50);
        assertTrue(summary.propagationsTotal() >= summary.ticksWithAnyActivity());
    }

    @Test
    void countsEveryLivingUnitIncludingDescendants() {
        // arrange
        int[][] genomes = GenomeFactory.random(5L, 200, 16);
        long expectedPropagations = 0;
        long expectedActiveTicks = 0;
        long peak = 0;
        DynamicsSummary summary;

        // act
        try (Kernel kernel = kernelOf(genomes)) {
            DynamicsObserver observer = new DynamicsObserver();
            for (int i = 0; i < 300; i++) {
                kernel.tick();
                long tickTotal = 0;
                long alive = 0;
                for (int slot = 0; slot < kernel.state().unitCount; slot++) {
                    tickTotal += kernel.state().activeThisTick[slot];
                    if (kernel.state().energy[slot] > 0.0) {
                        alive++;
                    }
                }
                expectedPropagations += tickTotal;
                expectedActiveTicks += tickTotal > 0 ? 1 : 0;
                peak = Math.max(peak, alive);
                observer.observe(kernel.snapshot());
            }
            assertTrue(kernel.state().birthsTotal > 0, "descendants must exist for the test to bite");
            summary = observer.summarize(kernel.stateDigest());
        }

        // assert
        assertEquals(expectedPropagations, summary.propagationsTotal());
        assertEquals(expectedActiveTicks, summary.ticksWithAnyActivity());
        assertEquals(peak, summary.peakPopulation());
    }

    @Test
    void summaryCarriesTheGivenDigest() {
        // act
        DynamicsSummary summary;
        try (Kernel kernel = kernelOf(new int[][] {FEEDBACK_GENOME})) {
            DynamicsObserver observer = new DynamicsObserver();
            kernel.tick();
            observer.observe(kernel.snapshot());
            summary = observer.summarize(0x1234L);
        }

        // assert
        assertEquals(0x1234L, summary.stateDigest());
    }
}
