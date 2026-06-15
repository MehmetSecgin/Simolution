package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

/**
 * The genome dictionary for report-v13 frames. A genome changes only at birth,
 * yet report-v9/v11 carried the raw genes in (or alongside) every frame; this
 * writer dedups them <b>globally</b> across the whole run: each distinct gene
 * list is written once as {@code g <id> <count> <gene×count>} and thereafter
 * referenced by its small integer {@code id} on the lean {@code u} frame line.
 * <p>
 * The viewer joins {@code u.genomeId → catalog → genes → circuit} client-side, so
 * the heavy genome bytes leave the per-tick frame stream entirely. Living distinct
 * genomes are few (dozens) even when births number in the millions — most births
 * are clones — so the catalog stays small.
 * <p>
 * Memory: an {@code O(distinct genomes)} map, the irreducible cost of dedup (it is
 * the catalog's whole purpose) and constant in tick count, not per-tick history.
 * This is a sidecar disk writer (like {@code --trace}), not the kernel hot path, so
 * the per-call key {@code String} allocation is acceptable; callers further avoid it
 * by caching the last id per slot and only re-resolving when a slot's genome changes.
 */
public final class GenomeCatalogWriter implements Closeable {

    private final Writer out;
    private final StringBuilder key = new StringBuilder(256);
    private final HashMap<String, Integer> idByGenome = new HashMap<>();
    private int nextId;

    public GenomeCatalogWriter(final Writer out) {
        this.out = out;
    }

    /**
     * Resolve a genome (the {@code count} genes at {@code genes[base..base+count]})
     * to its catalog id, writing a new {@code g} line the first time it is seen.
     */
    public int idOf(final int[] genes, final int base, final int count) throws IOException {
        key.setLength(0);
        key.append(count);
        for (int g = 0; g < count; g++) {
            key.append(' ').append(genes[base + g]);
        }
        final String k = key.toString();
        final Integer existing = idByGenome.get(k);
        if (existing != null) {
            return existing;
        }
        final int id = nextId++;
        idByGenome.put(k, id);
        out.write("g " + id + ' ' + k + '\n');
        out.flush();
        return id;
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
