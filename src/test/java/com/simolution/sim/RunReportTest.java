package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

class RunReportTest {

    private static String runAndRender(RunConfig config) {
        int[][] genomes = GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());
        Kernel kernel = new Kernel(genomes, java.util.stream.IntStream.range(0, genomes.length).toArray(),
                config.engine(), 1);
        DynamicsObserver observer = new DynamicsObserver(config.units(), connections, config.engine());
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            observer.observe(kernel.snapshot());
        }
        return RunReport.render(config, structure, observer.summarize());
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() {
        // arrange
        RunConfig config = new RunConfig(25, 200, 16, false, false, null, 0, false, 0, -1, -1, EngineConfig.v5Defaults(42L, 64, 16), 1);

        // act
        String first = runAndRender(config);
        String second = runAndRender(config);

        // assert
        assertEquals(first, second);
    }

    @Test
    void reportDoesNotInlinePerUnitDetail() {
        // act
        String report = runAndRender(new RunConfig(25, 50, 8, false, false, null, 0, false, 0, -1, -1, EngineConfig.v5Defaults(1L, 64, 8), 1));

        // assert
        assertFalse(report.contains("## units"));
        // report-v13 retired the per-unit CSV sidecar; the report no longer points to one
        assertFalse(report.contains("per-unit-detail"));
    }

    @Test
    void reportContainsAllSections() {
        // act
        String report = runAndRender(new RunConfig(5, 50, 8, false, false, null, 0, false, 0, -1, -1, EngineConfig.v5Defaults(9L, 64, 8), 1));

        // assert
        assertTrue(report.contains("schema: report-v14"));
        assertTrue(report.contains("## structure"));
        assertTrue(report.contains("## dynamics"));
        assertTrue(report.contains("## energy"));
        assertTrue(report.contains("## mass"));
        assertTrue(report.contains("final-mass-total: "));
        assertTrue(report.contains("## intake"));
        assertTrue(report.contains("intake-total: "));
        assertTrue(report.contains("## burn-rate"));
        assertTrue(report.contains("## terminal-regimes"));
        assertTrue(report.contains("## action-channel"));
        assertTrue(report.contains("energy-audit-error: "));
        assertTrue(report.contains("state-digest: "));
    }
}
