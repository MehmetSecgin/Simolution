package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

class RunReportTest {

    private static String runAndRender(RunConfig config) {
        int[][] genomes = GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());
        Kernel kernel = new Kernel(config.units(), connections);
        DynamicsObserver observer = new DynamicsObserver(config.units(), connections);
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            observer.observe(kernel.snapshot());
        }
        return RunReport.render(config, structure, observer.summarize());
    }

    @Test
    void reportIsByteIdenticalAcrossRuns() {
        // arrange
        RunConfig config = new RunConfig(25, 200, 42L, 16, false, false, null);

        // act
        String first = runAndRender(config);
        String second = runAndRender(config);

        // assert
        assertEquals(first, second);
    }

    @Test
    void smallRunsIncludePerUnitLines() {
        // act
        String small = runAndRender(new RunConfig(3, 50, 1L, 8, false, false, null));
        String large = runAndRender(new RunConfig(25, 50, 1L, 8, false, false, null));

        // assert
        assertTrue(small.contains("## units"));
        assertFalse(large.contains("## units"));
    }

    @Test
    void reportContainsAllSections() {
        // act
        String report = runAndRender(new RunConfig(5, 50, 9L, 8, false, false, null));

        // assert
        assertTrue(report.contains("schema: report-v2"));
        assertTrue(report.contains("## structure"));
        assertTrue(report.contains("## dynamics"));
        assertTrue(report.contains("## energy"));
        assertTrue(report.contains("## terminal-regimes"));
        assertTrue(report.contains("## action-channel"));
        assertTrue(report.contains("energy-audit-error: "));
        assertTrue(report.contains("state-digest: "));
    }
}
