package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.kernel.state.WorldState;

/**
 * The lineage / "what happened to who" store (report-v13). One CSV row per birth:
 * <pre>
 *   tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated
 * </pre>
 * queried by DuckDB (no JVM). {@code lineage} + {@code generation} are kernel-exact,
 * so "evolution of lineage L" is robust ({@code SELECT DISTINCT generation,
 * child_genome_id … WHERE lineage=L}, then diff catalog genes hop to hop).
 * {@code parent_slot} is exact too: rows come from the engine's per-tick birth list
 * ({@code WorldState.birthParent}/{@code birthChild}, engine v6 design §5.6), in ascending
 * child slot order, so nothing is inferred from geometry. Every birth is kept (pruning is a
 * {@code WHERE}); the writer streams it gzip-wrapped (DuckDB reads {@code .csv.gz} directly).
 * Genome ids come from the <b>shared</b> {@link GenomeCatalogWriter}, so they match the frame
 * stream; {@code mutated} is exact ({@code child_genome_id != parent_genome_id},
 * content-addressed ids).
 * <p>
 * {@link #observe} allocates only on an actual birth and touches only this tick's births.
 * Memory is constant in tick count (memory doctrine).
 */
public final class BirthLogWriter implements TickObserver, Closeable {

    private final Writer out;
    private final GenomeCatalogWriter catalog;
    private final StringBuilder line;

    public BirthLogWriter(final Writer out, final GenomeCatalogWriter catalog) throws IOException {
        this.out = out;
        this.catalog = catalog;
        this.line = new StringBuilder(64);

        out.write("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated\n");
    }

    @Override
    public void observe(final KernelSnapshot snapshot) throws IOException {
        final WorldState state = snapshot.state;
        for (int i = 0; i < state.birthCount; i++) {
            emitBirth(snapshot.tick, state.birthChild[i], state.birthParent[i], snapshot);
        }
    }

    private void emitBirth(final int tick, final int child, final int parent,
                           final KernelSnapshot s) throws IOException {
        final int childId = catalog.idOf(s.genes, child * s.maxGenes, s.geneCount[child]);
        final int parentId = catalog.idOf(s.genes, parent * s.maxGenes, s.geneCount[parent]);
        final int mutated = childId == parentId ? 0 : 1;

        line.setLength(0);
        line.append(tick).append(',').append(child)
            .append(',').append(s.lineageId[child]).append(',').append(s.generation[child])
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
