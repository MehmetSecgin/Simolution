package com.simolution.kernel.genome;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class GenomeCompiler {

    private GenomeCompiler() {}

    /**
     * Compiles raw genes into a packed array of valid compiled connections.
     *
     * Notes:
     * - Allocations are allowed here (precompute phase).
     * - No decoding or validation should happen during ticks.
     * - Invalid genes are dropped from the compiled wiring but still exist in the genome.
     */
    public static CompiledConnection[] compile(final int[] rawGenes, final int unitNodeOffset) {

        // Worst case: every gene becomes a connection.
        final CompiledConnection[] temp = new CompiledConnection[rawGenes.length];

        int validConnectionCount = 0;

        for (final int rawGene : rawGenes) {
            final CompiledConnection connection = GeneDecoder.decode(rawGene, unitNodeOffset);

            if (connection != null) {
                temp[validConnectionCount++] = connection;
            }
        }

        // If everything was valid, avoid an extra copy.
        if (validConnectionCount == temp.length) {
            return temp;
        }

        final CompiledConnection[] result = new CompiledConnection[validConnectionCount];
        System.arraycopy(temp, 0, result, 0, validConnectionCount);
        return result;
    }

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