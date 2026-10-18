package com.simolution.support;

import java.util.stream.IntStream;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;
import com.simolution.kernel.config.InflowSource;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.GenomeFactory;

public final class Worlds {

    public static final int DEFAULT_MAX_GENES = 32;
    private static final double FOOD_RICH_INFLOW = 8.0;

    private Worlds() {}

    public static Kernel kernel(final int worldWidth, final int[]... genomes) {
        return kernel(EngineConfig.defaults(0L, worldWidth, DEFAULT_MAX_GENES), genomes);
    }

    public static Kernel kernel(final EngineConfig config, final int[]... genomes) {
        return new Kernel(genomes, IntStream.range(0, genomes.length).toArray(), config, 1);
    }

    public static Kernel kernelAt(final int worldWidth, final int[][] genomes, final int... cells) {
        return new Kernel(genomes, cells, EngineConfig.defaults(0L, worldWidth, DEFAULT_MAX_GENES), 1);
    }

    public static Kernel kernelAt(final EngineConfig config, final int[][] genomes, final int... cells) {
        return new Kernel(genomes, cells, config, 1);
    }

    public static Kernel randomKernel(final long seed, final int founders, final int genes,
            final EngineConfig config) {
        return randomKernel(seed, founders, genes, config, 1);
    }

    public static Kernel randomKernel(final long seed, final int founders, final int genes,
            final EngineConfig config, final int threads) {
        return new Kernel(GenomeFactory.random(seed, founders, genes),
                Kernel.scatterFounders(config.seed(), founders, config.world().worldWidth()), config, threads);
    }

    public static EngineConfig foodRich(final int worldWidth, final int maxGenes) {
        return EngineConfig.defaults(0L, worldWidth, maxGenes)
                .withResourceInflow(InflowConfig.field(FOOD_RICH_INFLOW, InflowConfig.Combine.MAX));
    }

    public static InflowConfig centralPulse(final int periodTicks, final double radius, final double peak) {
        return InflowConfig.field(0.0, InflowConfig.Combine.MAX, new InflowSource.Disk(
                0.5, 0.5, radius, peak, periodTicks, 0, InflowSource.Motion.STATIC, 0.0, 0.0, 0));
    }
}
