package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.Genes;
import com.simolution.support.Worlds;

class DynamicsObserverTest {

    private static final int[] FEEDBACK_GENOME = Genes.FEEDBACK_LOOP;

    private static final int WORLD = 64;

    private static Kernel kernelOf(final int[][] genomes) {
        return Worlds.kernel(WORLD, genomes);
    }

    private static DynamicsSummary runAndSummarize(final int[][] genomes, final int ticks) {
        try (Kernel kernel = kernelOf(genomes)) {
            final DynamicsObserver observer = new DynamicsObserver();
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
        final DynamicsSummary summary = runAndSummarize(new int[][] {{}}, 10);

        // assert
        assertEquals(0, summary.activity().ticksWithAnyActivity());
        assertEquals(0, summary.activity().propagationsTotal());
    }

    @Test
    void feedbackGenomePropagatesFromTheThirdTick() {
        // act
        final DynamicsSummary summary = runAndSummarize(new int[][] {FEEDBACK_GENOME}, 50);

        // assert
        assertTrue(summary.activity().ticksWithAnyActivity() > 0);
        assertTrue(summary.activity().ticksWithAnyActivity() < 50);
        assertTrue(summary.activity().propagationsTotal() >= summary.activity().ticksWithAnyActivity());
    }

    @Test
    void countsEveryLivingUnitIncludingDescendants() {
        // arrange
        final int[][] genomes = GenomeFactory.random(5L, 200, 16);
        long expectedPropagations = 0;
        long expectedActiveTicks = 0;
        long peak = 0;
        final DynamicsSummary summary;

        // act
        try (Kernel kernel = kernelOf(genomes)) {
            final DynamicsObserver observer = new DynamicsObserver();
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
        assertEquals(expectedPropagations, summary.activity().propagationsTotal());
        assertEquals(expectedActiveTicks, summary.activity().ticksWithAnyActivity());
        assertEquals(peak, summary.population().peak());
    }

    @Test
    void summaryCarriesTheGivenDigest() {
        // act
        final DynamicsSummary summary;
        try (Kernel kernel = kernelOf(new int[][] {FEEDBACK_GENOME})) {
            final DynamicsObserver observer = new DynamicsObserver();
            kernel.tick();
            observer.observe(kernel.snapshot());
            summary = observer.summarize(0x1234L);
        }

        // assert
        assertEquals(0x1234L, summary.stateDigest());
    }
}
