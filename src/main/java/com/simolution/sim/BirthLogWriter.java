package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * The lineage / "what happened to who" store (report-v13). One CSV row per birth:
 * <pre>
 *   tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated
 * </pre>
 * queried by DuckDB (no JVM). {@code lineage} + {@code generation} are kernel-exact,
 * so "evolution of lineage L" is robust ({@code SELECT DISTINCT generation,
 * child_genome_id … WHERE lineage=L}, then diff catalog genes hop to hop);
 * {@code parent_slot} is the best-effort Moore-neighbour match (often ambiguous in the
 * colonization boom → {@code -1}), good for exact-parent CTEs when unique. Every birth
 * is kept (no pruning — only ~1–2 k per run; pruning is a {@code WHERE}); the writer
 * streams it gzip-wrapped (DuckDB reads {@code .csv.gz} directly), a few KB on disk.
 * <p>
 * The kernel exposes no event hook (it stays pure); births are <b>derived</b> from
 * snapshot deltas exactly as {@link EventLogWriter} does: a slot that was empty (or
 * held a different {@code (lineage, generation)}) last tick and now holds a living
 * unit is a birth; the parent is the unique prev-tick occupant of a Moore neighbour
 * of the child's cell with the same lineage and one-less generation (ambiguous →
 * {@code -1}, an honest unknown). Genome ids come from the <b>shared</b>
 * {@link GenomeCatalogWriter}, so they match the frame stream; {@code mutated} is
 * exact ({@code child_genome_id != parent_genome_id}, content-addressed ids).
 * <p>
 * All buffers are sized once in the constructor; {@link #observe} allocates only on
 * an actual birth (rare relative to the O(maxUnits) scan). Memory is O(maxUnits),
 * constant in tick count (memory doctrine).
 */
public final class BirthLogWriter implements Closeable {

    private final Writer out;
    private final GenomeCatalogWriter catalog;
    private final BirthTracker births;
    private final StringBuilder line;

    public BirthLogWriter(final Writer out, final GenomeCatalogWriter catalog,
                          final BirthTracker births) throws IOException {
        this.out = out;
        this.catalog = catalog;
        this.births = births;
        this.line = new StringBuilder(64);

        out.write("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated\n");
    }

    public void observe(final KernelSnapshot snapshot) throws IOException {
        for (int i = 0; i < births.birthCount(); i++) {
            emitBirth(snapshot.tick, births.birthChild(i), births.birthParent(i), snapshot);
        }
    }

    private void emitBirth(final int tick, final int child, final int parent,
                           final KernelSnapshot s) throws IOException {
        final int childId = catalog.idOf(s.genes, child * s.maxGenes, s.geneCount[child]);
        final int parentId = parent < 0 ? -1 : catalog.idOf(s.genes, parent * s.maxGenes, s.geneCount[parent]);
        final int mutated = parent < 0 ? -1 : (childId == parentId ? 0 : 1);

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
