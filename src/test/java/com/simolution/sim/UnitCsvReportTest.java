package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.KernelConfig;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

class UnitCsvReportTest {

    private record Run(StructuralStats structure, DynamicsSummary dynamics) {}

    private static Run run(RunConfig config) {
        int[][] genomes = GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());
        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());
        Kernel kernel = new Kernel(genomes, config.genesPerUnit());
        DynamicsObserver observer = new DynamicsObserver(config.units(), connections);
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            observer.observe(kernel.snapshot());
        }
        return new Run(structure, observer.summarize());
    }

    @Test
    void csvIsDeterministicAndHasRowPerUnit() {
        // arrange
        RunConfig config = new RunConfig(30, 800, 42L, 16, false, false, null);

        // act
        Run a = run(config);
        Run b = run(config);
        String first = UnitCsvReport.render(30, a.structure(), a.dynamics());
        String second = UnitCsvReport.render(30, b.structure(), b.dynamics());

        // assert
        assertEquals(first, second);
        String[] lines = first.split("\n");
        assertEquals(31, lines.length, "header + 30 unit rows");
        assertTrue(lines[0].startsWith("unit,connections,"));
    }

    @Test
    void deadUnitConsumedAllEnergyAndHasDeathTick() {
        // arrange: long run so units die
        RunConfig config = new RunConfig(20, 1500, 7L, 32, false, false, null);
        Run r = run(config);
        DynamicsSummary dynamics = r.dynamics();

        // act + assert: every dead unit consumed exactly INITIAL_ENERGY and has a death tick
        boolean sawDead = false;
        for (int unit = 0; unit < 20; unit++) {
            if (dynamics.deathTick()[unit] >= 0) {
                sawDead = true;
                assertEquals(0.0, dynamics.finalEnergy()[unit], 0.0);
                assertEquals(KernelConfig.INITIAL_ENERGY, dynamics.energyConsumed(unit), 1.0e-9);
                assertEquals(dynamics.deathTick()[unit], dynamics.lifespan(unit));
            }
        }
        assertTrue(sawDead, "a 1500-tick run must kill some units");
    }

    @Test
    void burnRateIsConsumedOverLifespan() {
        // arrange
        RunConfig config = new RunConfig(10, 1000, 3L, 16, false, false, null);
        Run r = run(config);
        DynamicsSummary dynamics = r.dynamics();

        // act + assert
        for (int unit = 0; unit < 10; unit++) {
            double expected = dynamics.energyConsumed(unit) / dynamics.lifespan(unit);
            assertEquals(expected, dynamics.meanBurnRate(unit), 1.0e-12);
        }
    }
}
