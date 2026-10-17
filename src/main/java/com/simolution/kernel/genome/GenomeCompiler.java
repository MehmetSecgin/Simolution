package com.simolution.kernel.genome;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class GenomeCompiler {

    private GenomeCompiler() {}

    /**
     * Compiles raw genes into compiled connections, one per gene (every int
     * decodes). Allocation is fine here (precompute phase); nothing decodes
     * during ticks.
     */
    public static CompiledConnection[] compile(final int[] rawGenes, final int unitNodeOffset) {
        final CompiledConnection[] result = new CompiledConnection[rawGenes.length];
        for (int g = 0; g < rawGenes.length; g++) {
            result[g] = GeneDecoder.decode(rawGenes[g], unitNodeOffset);
        }
        return result;
    }

    /**
     * Compiles one genome per unit into a single flat connection array.
     * <p>
     * Unit {@code u}'s connections get absolute indices offset by
     * {@code u * NodeLayout.TOTAL}, which is the entire multi-unit
     * mechanism: propagation stays one unit-agnostic loop because
     * isolation is baked into the indices here, at compile time
     * (see ADR 0002).
     */
    public static CompiledConnection[] compileAll(final int[][] genomes) {

        final CompiledConnection[][] perUnit = new CompiledConnection[genomes.length][];

        int totalConnections = 0;
        for (int unit = 0; unit < genomes.length; unit++) {
            perUnit[unit] = compile(genomes[unit], unit * NodeLayout.TOTAL);
            totalConnections += perUnit[unit].length;
        }

        final CompiledConnection[] result = new CompiledConnection[totalConnections];

        int cursor = 0;
        for (final CompiledConnection[] unitConnections : perUnit) {
            System.arraycopy(unitConnections, 0, result, cursor, unitConnections.length);
            cursor += unitConnections.length;
        }

        return result;
    }
}