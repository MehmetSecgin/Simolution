package com.simolution.sim;

import java.io.IOException;
import java.util.Arrays;

import com.simolution.kernel.runtime.KernelSnapshot;

class SlotGenomeCache {

    private final int maxGenes;
    private final GenomeCatalogWriter catalog;
    private final int[] genes;
    private final int[] geneCounts;
    private final int[] catalogIds;

    SlotGenomeCache(final int slots, final int maxGenes, final GenomeCatalogWriter catalog) {
        this.maxGenes = maxGenes;
        this.catalog = catalog;
        this.genes = new int[slots * maxGenes];
        this.geneCounts = new int[slots];
        Arrays.fill(geneCounts, -1);
        this.catalogIds = new int[slots];
    }

    int catalogIdFor(final KernelSnapshot snapshot, final int slot) throws IOException {
        final int count = snapshot.geneCount[slot];
        final int base = slot * maxGenes;
        if (geneCounts[slot] == count && sameGenes(snapshot.genes, base, count)) {
            return catalogIds[slot];
        }
        final int id = catalog.idOf(snapshot.genes, base, count);
        System.arraycopy(snapshot.genes, base, genes, base, count);
        geneCounts[slot] = count;
        catalogIds[slot] = id;
        return id;
    }

    private boolean sameGenes(final int[] current, final int base, final int count) {
        for (int g = 0; g < count; g++) {
            if (genes[base + g] != current[base + g]) {
                return false;
            }
        }
        return true;
    }
}
