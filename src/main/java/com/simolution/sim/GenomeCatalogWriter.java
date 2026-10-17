package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

/** Writes each distinct genome once and hands out its id; format in docs/specs/report-v14.md. */
public class GenomeCatalogWriter implements Closeable {

    private final Writer out;
    private final StringBuilder key = new StringBuilder(256);
    private final HashMap<String, Integer> idByGenome = new HashMap<>();
    private int nextId;

    public GenomeCatalogWriter(final Writer out) {
        this.out = out;
    }

    public int idOf(final int[] genes, final int base, final int count) throws IOException {
        key.setLength(0);
        key.append(count);
        for (int g = 0; g < count; g++) {
            key.append(' ').append(genes[base + g]);
        }
        final String genome = key.toString();
        final Integer existing = idByGenome.get(genome);
        if (existing != null) {
            return existing;
        }
        final int id = nextId++;
        idByGenome.put(genome, id);
        out.write("g " + id + ' ' + genome + '\n');
        out.flush();
        return id;
    }

    @Override
    public void close() throws IOException {
        out.flush();
        out.close();
    }
}
