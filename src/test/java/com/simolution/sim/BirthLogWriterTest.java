package com.simolution.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * The births / lineage store (report-v13): one CSV row per derived birth, genome
 * ids shared with the catalog, lineage + generation kernel-exact.
 */
class BirthLogWriterTest {

    @Test
    void headerThenBirthRowsWithSharedCatalogIds() throws Exception {
        StringWriter births = new StringWriter();
        StringWriter catalog = new StringWriter();
        GenomeCatalogWriter cat = new GenomeCatalogWriter(catalog, new StringWriter());
        int world = 8;
        int[] founders = Kernel.scatterFounders(6, world);
        Kernel k = new Kernel(GenomeFactory.random(3L, 6, 8), world, 8, founders);
        BirthLogWriter w = new BirthLogWriter(births, cat, world * world, 6, world, founders);

        for (int i = 0; i < 40; i++) {
            k.tick();
            KernelSnapshot s = k.snapshot();
            w.observe(s);
        }
        w.close();
        cat.close();

        List<String> lines = births.toString().lines().toList();
        assertEquals("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated",
                lines.get(0), "header columns");

        List<String> rows = lines.stream().skip(1).toList();
        assertTrue(rows.size() > 0, "the run reproduces, so births are recorded");
        for (String row : rows) {
            String[] f = row.split(",");
            assertEquals(8, f.length, "each birth row has 8 columns");
            int gen = Integer.parseInt(f[3]);
            assertTrue(gen >= 1, "a born child is at least generation 1");
            int childGid = Integer.parseInt(f[5]);
            assertTrue(childGid >= 0, "child genome id resolved into the catalog");
            int mutated = Integer.parseInt(f[7]);
            assertTrue(mutated == 0 || mutated == 1 || mutated == -1, "mutated is 0/1/-1");
        }
        assertTrue(catalog.toString().lines().anyMatch(l -> l.startsWith("g ")),
                "child genomes were written to the shared catalog");
    }
}
