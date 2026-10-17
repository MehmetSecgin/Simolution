package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.ChemicalSpec;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

class SnapshotDumpTest {

    @Test
    void resourceRowHoldsOneDigitPerCellWithTwoChemicals() {
        // arrange
        final ChemicalSpec first = new ChemicalSpec("first", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1);
        final ChemicalSpec second = new ChemicalSpec("second", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1);
        final EngineConfig config = EngineConfig.defaults(0L, 4, 8).withChemicals(List.of(first, second));
        final Kernel kernel = new Kernel(GenomeFactory.random(1L, 4, 8), Kernel.scatterFounders(0L, 4, 4), config, 1);
        kernel.tick();

        // act
        final String dump = SnapshotDump.render(kernel.snapshot(), new CompiledConnection[0], config);

        // assert
        final String row = dump.lines().filter(l -> l.startsWith("resource ")).findFirst().orElseThrow();
        assertEquals("resource ".length() + 16, row.length());
        kernel.close();
    }
}
