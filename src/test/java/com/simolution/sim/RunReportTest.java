package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.Worlds;

class RunReportTest {

    private static String runAndRender(final RunConfig config) {
        final int[][] genomes =
                GenomeFactory.random(config.engine().seed(), config.founderCount(), config.genesPerFounder());
        final CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        final StructuralStats structure = StructuralAnalyzer.analyze(connections, config.founderCount());
        try (Kernel kernel = Worlds.kernel(config.engine(), genomes)) {
            final DynamicsObserver observer = new DynamicsObserver();
            for (int i = 0; i < config.ticks(); i++) {
                kernel.tick();
                observer.observe(kernel.snapshot());
            }
            return RunReport.render(config, structure, observer.summarize(kernel.stateDigest()));
        }
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() {
        // arrange
        final RunConfig config =
                new RunConfig(25, 200, 16, false, null, MapSampling.EVERY_TICK, new Observation(false, 0),
                        EngineConfig.defaults(42L, 64, 16), 1, false);

        // act
        final String first = runAndRender(config);
        final String second = runAndRender(config);

        // assert
        assertEquals(first, second);
    }

    @Test
    void reportDoesNotInlinePerUnitDetail() {
        // act
        final String report = runAndRender(new RunConfig(25, 50, 8, false, null, MapSampling.EVERY_TICK,
                new Observation(false, 0), EngineConfig.defaults(1L, 64, 8), 1, false));

        // assert
        assertFalse(report.contains("## units"));
        assertFalse(report.contains("per-unit-detail"));
    }

    @Test
    void reportContainsAllSections() {
        // act
        final String report = runAndRender(new RunConfig(5, 50, 8, false, null, MapSampling.EVERY_TICK,
                new Observation(false, 0), EngineConfig.defaults(9L, 64, 8), 1, false));

        // assert
        assertTrue(report.contains("schema: report-v15"));
        assertTrue(report.contains("kernel: v6"));
        assertTrue(report.contains("## structure"));
        assertTrue(report.contains("## dynamics"));
        assertTrue(report.contains("ticks-with-any-activity: "));
        assertTrue(report.contains("signal-propagations-total: "));
        assertTrue(report.contains("## reproduction"));
        assertTrue(report.contains("## energy"));
        assertTrue(report.contains("intake-total: "));
        assertTrue(report.contains("## mass"));
        assertTrue(report.contains("final-mass-total: "));
        assertTrue(report.contains("energy-audit-error: "));
        assertTrue(report.contains("state-digest: "));
    }

    @Test
    void sectionsAppearInTodaysOrder() {
        // act
        final String report = runAndRender(new RunConfig(5, 50, 8, false, null, MapSampling.EVERY_TICK,
                new Observation(false, 0), EngineConfig.defaults(9L, 64, 8), 1, false));

        // assert
        final String[] headers =
                {"# Simolution run report", "\n## structure\n", "\n## dynamics\n", "\n## reproduction\n",
                        "\n## energy\n", "\n## mass\n", "\nstate-digest: "};
        int previous = -1;
        for (String header : headers) {
            final int at = report.indexOf(header);
            assertTrue(at > previous, header);
            previous = at;
        }
        assertTrue(report.endsWith("\n"));
    }
}
