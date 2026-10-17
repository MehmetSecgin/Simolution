package com.simolution.kernel.phase;

/**
 * Fixed work chunks (engine v6 design §5.8). Chunk {@code c} of an extent covers
 * {@code [c·SIZE, min(extent, (c+1)·SIZE))}. Boundaries depend only on the extent and the
 * engine constant {@link #SIZE}, never on the thread count, so every per-chunk partial and
 * every merge in ascending chunk index is the same for any number of threads.
 */
public final class Chunks {

    public static final int SIZE = 4096;

    private Chunks() {}

    public static int count(final int extent) {
        return (extent + SIZE - 1) / SIZE;
    }

    /**
     * Sum of {@code values}, computed chunk by chunk (each chunk summed in index order) and
     * the chunk sums added in ascending chunk index: the same order a chunked parallel
     * reduction uses, so totals agree with partials merged by the phases.
     */
    public static double orderedSum(final double[] values) {
        double total = 0.0;
        for (int from = 0; from < values.length; from += SIZE) {
            final int to = Math.min(values.length, from + SIZE);
            double partial = 0.0;
            for (int i = from; i < to; i++) {
                partial += values[i];
            }
            total += partial;
        }
        return total;
    }
}
