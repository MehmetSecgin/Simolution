package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
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

    private static DynamicsSummary runAndSummarize(int[][] genomes, int ticks) {
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        Kernel kernel = new Kernel(genomes.length, connections);
        DynamicsObserver observer = new DynamicsObserver(genomes.length, connections);
        for (int i = 0; i < ticks; i++) {
            kernel.tick();
            observer.observe(kernel.snapshot());
        }
        return observer.summarize();
    }

    @Test
    void emptyGenomeUnitIsDormantFromBirthAtFixedPoint() {
        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {{}}, 10);

        // assert
        assertEquals(1, summary.countDormantFromBirth());
        assertEquals(1, summary.countDormantAtEnd());
        assertEquals(DynamicsSummary.Regime.FIXED_POINT, summary.regime(0));
        assertEquals(0, summary.propagationsTotal());
    }

    @Test
    void feedbackGenomeReachesExactFixedPoint() {
        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {FEEDBACK_GENOME}, 50);

        // assert
        assertEquals(DynamicsSummary.Regime.FIXED_POINT, summary.regime(0));
        assertEquals(2, summary.firstActivityTick()[0]);
        assertEquals(false, summary.dormantAtEnd()[0]);
        assertEquals(0, summary.countDormantFromBirth());
        assertTrue(summary.propagationsTotal() > 0);
        assertEquals(0, summary.clampSaturationEvents());
        assertEquals(0, summary.threshFlipsTotal());
    }

    @Test
    void junkSinkSignalsAreCounted() {
        // arrange
        int junkInternalTypeId = NodeLayout.Internal.MEANINGFUL_COUNT;
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(junkInternalTypeId)
                           .weightRaw((short) 8192)
                           .build()
        };

        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {genome}, 5);

        // assert
        assertTrue(summary.junkSinkPropagations() > 0);
        assertEquals(summary.propagationsTotal(), summary.junkSinkPropagations());
        assertEquals(1.0, summary.junkSinkPropagationFraction(), 0.0);
    }

    @Test
    void divergentFeedbackIsClassified() {
        // arrange
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw((short) 8192)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw((short) 16384)
                           .build()
        };

        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {genome}, 200);

        // assert
        assertEquals(DynamicsSummary.Regime.DIVERGENT, summary.regime(0));
    }

    @Test
    void overflowToNonFiniteStillClassifiesAsDivergent() {
        // arrange
        int[] genome = {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw((short) 8192)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(Short.MAX_VALUE)
                           .build()
        };

        // act
        DynamicsSummary summary = runAndSummarize(new int[][] {genome}, 2000);

        // assert
        assertTrue(summary.reachedNonFinite()[0]);
        assertEquals(DynamicsSummary.Regime.DIVERGENT, summary.regime(0));
    }

    @Test
    void digestIsDeterministicAndSeedSensitive() {
        // arrange
        int[][] genomesA = GenomeFactory.random(5L, 10, 16);
        int[][] genomesB = GenomeFactory.random(6L, 10, 16);

        // act
        DynamicsSummary first = runAndSummarize(genomesA, 100);
        DynamicsSummary second = runAndSummarize(genomesA, 100);
        DynamicsSummary other = runAndSummarize(genomesB, 100);

        // assert
        assertEquals(first.stateDigest(), second.stateDigest());
        assertNotEquals(first.stateDigest(), other.stateDigest());
    }
}
