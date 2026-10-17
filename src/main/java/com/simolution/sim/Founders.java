package com.simolution.sim;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;

public record Founders(int[][] genomes, int[] cells, EngineConfig engine) {

    public static Founders create(RunConfig config) {
        long seed = config.engine().seed();
        int[][] genomes = switch (config.genomes()) {
            case GenomeSource.Demo _ -> new int[][] {GenomeFactory.demo()};
            case GenomeSource.Random random -> GenomeFactory.random(seed, config.founderCount(), random.genesPerFounder());
        };
        int longest = config.engine().world().maxGenes();
        for (int[] genome : genomes) {
            longest = Math.max(longest, genome.length);
        }
        EngineConfig engine = config.engine().withMaxGenes(longest);
        int[] cells = Kernel.scatterFounders(seed, genomes.length, engine.world().worldWidth());
        return new Founders(genomes, cells, engine);
    }

    public CompiledConnection[] connections() {
        return GenomeCompiler.compileAll(genomes);
    }

    public int slotCount() {
        return engine.world().worldWidth() * engine.world().worldWidth();
    }

    public Kernel newKernel(int threads) {
        return new Kernel(genomes, cells, engine, threads);
    }
}
