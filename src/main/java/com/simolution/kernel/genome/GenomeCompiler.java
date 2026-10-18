package com.simolution.kernel.genome;

import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;

public final class GenomeCompiler {

    private GenomeCompiler() {}

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

        final List<CompiledConnection> connections = new ArrayList<>();
        for (int unit = 0; unit < genomes.length; unit++) {
            for (int gene : genomes[unit]) {
                connections.add(GeneDecoder.decode(gene, unit * NodeLayout.TOTAL));
            }
        }
        return connections.toArray(CompiledConnection[]::new);
    }
}
