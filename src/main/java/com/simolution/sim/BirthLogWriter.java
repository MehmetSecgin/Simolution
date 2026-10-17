package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.kernel.state.WorldState;

/** Streams one CSV row per birth, with genome ids from the shared catalog; format in docs/specs/report-v13.md. */
public final class BirthLogWriter implements TickObserver, Closeable {

    private final Writer out;
    private final GenomeCatalogWriter catalog;
    private final StringBuilder line = new StringBuilder(64);

    public BirthLogWriter(final Writer out, final GenomeCatalogWriter catalog) throws IOException {
        this.out = out;
        this.catalog = catalog;
        out.write("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated\n");
    }

    @Override
    public void observe(final KernelSnapshot snapshot) throws IOException {
        final WorldState state = snapshot.state;
        for (int i = 0; i < state.birthCount; i++) {
            appendBirth(snapshot, state.birthChild[i], state.birthParent[i]);
        }
    }

    private void appendBirth(final KernelSnapshot snapshot, final int child, final int parent)
            throws IOException {
        final int childId = catalog.idOf(snapshot.genes, child * snapshot.maxGenes, snapshot.geneCount[child]);
        final int parentId = catalog.idOf(snapshot.genes, parent * snapshot.maxGenes, snapshot.geneCount[parent]);
        final int mutated = childId == parentId ? 0 : 1;
        line.setLength(0);
        line.append(snapshot.tick).append(',').append(child)
            .append(',').append(snapshot.lineageId[child]).append(',').append(snapshot.generation[child])
            .append(',').append(parent)
            .append(',').append(childId).append(',').append(parentId)
            .append(',').append(mutated).append('\n');
        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
