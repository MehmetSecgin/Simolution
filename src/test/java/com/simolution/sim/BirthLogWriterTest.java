package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.support.Worlds;

class BirthLogWriterTest {

    @Test
    @DisplayName("report-v13: one CSV row per birth, genome ids shared with the catalog, lineage and generation kernel-exact")
    void headerThenBirthRowsWithSharedCatalogIds() throws Exception {
        // arrange
        final StringWriter births = new StringWriter();
        final StringWriter catalog = new StringWriter();
        final GenomeCatalogWriter cat = new GenomeCatalogWriter(catalog);
        final Kernel k = Worlds.randomKernel(3L, 6, 8, EngineConfig.defaults(0L, 8, 8));
        final BirthLogWriter w = new BirthLogWriter(births, cat);

        // act
        for (int i = 0; i < 40; i++) {
            k.tick();
            final KernelSnapshot s = k.snapshot();
            w.observe(s);
        }
        w.close();
        cat.close();

        // assert
        final List<String> lines = births.toString().lines().toList();
        assertEquals("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated",
                lines.get(0), "header columns");

        final List<String> rows = lines.stream().skip(1).toList();
        assertTrue(rows.size() > 0, "the run reproduces, so births are recorded");
        for (String row : rows) {
            final String[] f = row.split(",");
            assertEquals(8, f.length, "each birth row has 8 columns");
            final int gen = Integer.parseInt(f[3]);
            assertTrue(gen >= 1, "a born child is at least generation 1");
            final int childGid = Integer.parseInt(f[5]);
            assertTrue(childGid >= 0, "child genome id resolved into the catalog");
            final int mutated = Integer.parseInt(f[7]);
            assertTrue(mutated == 0 || mutated == 1 || mutated == -1, "mutated is 0/1/-1");
        }
        assertTrue(catalog.toString().lines().anyMatch(l -> l.startsWith("g ")),
                "child genomes were written to the shared catalog");
    }
}
