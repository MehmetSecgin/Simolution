package com.simolution.sim;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

public record Founders(int[][] genomes, int[] cells, EngineConfig engine) {

    public static Founders create(RunConfig config) {
        int[][] genomes = config.demo()
                ? new int[][] {GenomeFactory.demo()}
                : GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());
        int longest = config.maxGenes();
        for (int[] genome : genomes) {
            longest = Math.max(longest, genome.length);
        }
        EngineConfig engine = config.engine().withMaxGenes(longest);
        int[] cells = Kernel.scatterFounders(config.seed(), genomes.length, engine.worldWidth());
        return new Founders(genomes, cells, engine);
    }

    public CompiledConnection[] connections() {
        return GenomeCompiler.compileAll(genomes);
    }

    public int slotCount() {
        return engine.worldWidth() * engine.worldWidth();
    }

    public Kernel newKernel(int threads) {
        return new Kernel(genomes, cells, engine, threads);
    }
}
