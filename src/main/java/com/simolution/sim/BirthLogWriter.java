package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.config.KernelConfig;
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
    private final int maxUnits;
    private final int founderCount;
    private final int worldWidth;
    private final StringBuilder line;

    private final double[] prevEnergy;
    private final long[] prevLineage;
    private final int[] prevGeneration;
    private final int[] prevPosition;
    private final int[] prevCellOccupant;

    public BirthLogWriter(final Writer out, final GenomeCatalogWriter catalog, final int maxUnits,
                          final int founderCount, final int worldWidth,
                          final int[] founderCells) throws IOException {
        this.out = out;
        this.catalog = catalog;
        this.maxUnits = maxUnits;
        this.founderCount = founderCount;
        this.worldWidth = worldWidth;
        this.line = new StringBuilder(64);

        this.prevEnergy = new double[maxUnits];
        this.prevLineage = new long[maxUnits];
        Arrays.fill(prevLineage, -1L);
        this.prevGeneration = new int[maxUnits];
        this.prevPosition = new int[maxUnits];
        Arrays.fill(prevPosition, -1);
        this.prevCellOccupant = new int[maxUnits];
        Arrays.fill(prevCellOccupant, -1);

        for (int slot = 0; slot < founderCount; slot++) {
            prevEnergy[slot] = KernelConfig.INITIAL_ENERGY;
            prevLineage[slot] = slot;
            prevGeneration[slot] = 0;
            final int cell = founderCells[slot];
            prevPosition[slot] = cell;
            prevCellOccupant[cell] = slot;
        }

        out.write("tick,child_slot,lineage,generation,parent_slot,child_genome_id,parent_genome_id,mutated\n");
    }

    public void observe(final KernelSnapshot snapshot) throws IOException {
        final int tick = snapshot.tick;
        final double[] energy = snapshot.energy;
        final long[] lineage = snapshot.lineageId;
        final int[] generation = snapshot.generation;
        final int[] position = snapshot.position;

        for (int slot = 0; slot < maxUnits; slot++) {
            final boolean prevAlive = prevEnergy[slot] > 0.0;
            final boolean curAlive = energy[slot] > 0.0;
            final boolean idChanged = prevAlive && curAlive
                    && (prevLineage[slot] != lineage[slot] || prevGeneration[slot] != generation[slot]);
            if ((!prevAlive && curAlive) || idChanged) {
                emitBirth(tick, slot, snapshot);
            }
        }

        System.arraycopy(energy, 0, prevEnergy, 0, maxUnits);
        System.arraycopy(lineage, 0, prevLineage, 0, maxUnits);
        System.arraycopy(generation, 0, prevGeneration, 0, maxUnits);
        System.arraycopy(position, 0, prevPosition, 0, maxUnits);
        Arrays.fill(prevCellOccupant, -1);
        for (int slot = 0; slot < maxUnits; slot++) {
            if (energy[slot] > 0.0) {
                prevCellOccupant[position[slot]] = slot;
            }
        }
    }

    private void emitBirth(final int tick, final int child, final KernelSnapshot s) throws IOException {
        final int cell = s.position[child];
        final int parent = findParent(cell, s.lineageId[child], s.generation[child], s);
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

    /**
     * The unique prev-tick occupant of a Moore neighbour of {@code childCell} whose
     * lineage matches and generation is one less, or -1 if none or more than one
     * matches (ambiguous → unknown, not guessed). Mirrors {@link EventLogWriter}.
     */
    private int findParent(final int childCell, final long childLineage, final int childGen,
                           final KernelSnapshot s) {
        final int w = worldWidth;
        final int x = childCell % w;
        final int y = childCell / w;
        int found = -1;
        for (int dy = -1; dy <= 1; dy++) {
            final int ny = (((y + dy) % w) + w) % w;
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                final int nx = (((x + dx) % w) + w) % w;
                final int p = prevCellOccupant[ny * w + nx];
                if (p < 0) {
                    continue;
                }
                if (s.lineageId[p] == childLineage && s.generation[p] == childGen - 1) {
                    if (found >= 0) {
                        return -1;
                    }
                    found = p;
                }
            }
        }
        return found;
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
