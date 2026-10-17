package com.simolution.sim;

import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.InflowConfig;

public record RunConfig(
        int founderCount,
        int ticks,
        GenomeSource genomes,
        boolean trace,
        Path reportPath,
        MapSampling mapSampling,
        Observation observation,
        EngineConfig engine,
        int threads,
        boolean digestEveryTick
) {

    static final int DEMO_TICKS = 10;
    static final int DEFAULT_TICKS = 1000;
    static final int DEFAULT_FOUNDERS = 1;
    static final int DEFAULT_GENES = 32;
    static final int DEFAULT_CHECKPOINT_EVERY = 2000;
    static final int DEFAULT_CYCLE_PERIOD = 2000;
    static final double DEFAULT_CYCLE_PEAK = 6.0;

    private static final int DEMO_FOUNDERS = 1;
    private static final int DEMO_WORLD_WIDTH = 1;
    private static final int DEMO_MAX_GENES = 0;
    private static final long DEFAULT_SEED = 0L;
    private static final int CELLS_PER_FOUNDER = 4;

    public static int allCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static RunConfig parse(final String[] args) {
        final RunOptions options = RunOptions.read(args);
        options.validate();
        return options.isDemo() ? demo(options) : population(options);
    }

    private static RunConfig demo(final RunOptions options) {
        final EngineConfig engine = options.configPath == null
                ? EngineConfig.defaults(requireNonNullElse(options.seed, DEFAULT_SEED), DEMO_WORLD_WIDTH, DEMO_MAX_GENES)
                : applyOverrides(loadConfig(options.configPath), options.seed, null, null);
        return new RunConfig(DEMO_FOUNDERS, requireNonNullElse(options.ticks, DEMO_TICKS), new GenomeSource.Demo(),
                true, options.reportPath, options.mapFramesSampling(), observation(options), engine,
                options.threadCount(), options.digestEveryTick);
    }

    private static RunConfig population(final RunOptions options) {
        final int founders = requireNonNullElse(options.founders, DEFAULT_FOUNDERS);
        final int genes = requireNonNullElse(options.genes, DEFAULT_GENES);
        final EngineConfig engine = populationEngine(options, founders, genes);
        requireFoundersFit(engine.world().worldWidth(), founders);
        return new RunConfig(founders, requireNonNullElse(options.ticks, DEFAULT_TICKS),
                new GenomeSource.Random(genes), options.trace, options.reportPath, options.mapSampling(),
                observation(options), withInflow(engine, options), options.threadCount(), options.digestEveryTick);
    }

    private static EngineConfig populationEngine(final RunOptions options, final int founders, final int genes) {
        if (options.configPath != null) {
            return applyOverrides(loadConfig(options.configPath), options.seed, options.world, options.maxGenes);
        }
        final int world = options.world == null ? squareSideFor(founders * CELLS_PER_FOUNDER) : options.world;
        final int maxGenes = options.maxGenes == null ? defaultMaxGenes(genes) : options.maxGenes;
        return EngineConfig.defaults(requireNonNullElse(options.seed, DEFAULT_SEED), world, maxGenes);
    }

    private static void requireFoundersFit(final int worldWidth, final int founders) {
        final long cells = (long) worldWidth * worldWidth;
        if (cells < founders) {
            throw new IllegalArgumentException(
                    "world " + worldWidth + "x" + worldWidth + " (" + cells + " cells) cannot hold "
                    + founders + " founders");
        }
    }

    private static EngineConfig withInflow(final EngineConfig engine, final RunOptions options) {
        if (options.inflowSpec != null) {
            return engine.withResourceInflow(InflowSpecParser.parse(options.inflowSpec));
        }
        if (!options.resourceCycle) {
            return engine;
        }
        final int radius = options.cycleRadius == null ? defaultCycleRadius(engine.world().worldWidth()) : options.cycleRadius;
        return engine.withResourceInflow(InflowConfig.cyclic(
                requireNonNullElse(options.cyclePeriod, DEFAULT_CYCLE_PERIOD),
                radius,
                requireNonNullElse(options.cyclePeak, DEFAULT_CYCLE_PEAK)));
    }

    private static int defaultMaxGenes(final int genesPerFounder) {
        return genesPerFounder * 2;
    }

    private static int defaultCycleRadius(final int worldWidth) {
        return Math.max(1, worldWidth / 5);
    }

    private static Observation observation(final RunOptions options) {
        return new Observation(options.observe, requireNonNullElse(options.checkpointEvery, DEFAULT_CHECKPOINT_EVERY));
    }

    private static EngineConfig loadConfig(final String path) {
        try {
            return ConfigJson.read(Files.readString(Path.of(path)));
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read config " + path, e);
        }
    }

    private static EngineConfig applyOverrides(final EngineConfig base, final Long seed, final Integer world,
            final Integer maxGenes) {
        EngineConfig engine = base;
        if (seed != null) {
            engine = engine.withSeed(seed);
        }
        if (world != null) {
            engine = engine.withWorldWidth(world);
        }
        if (maxGenes != null) {
            engine = engine.withMaxGenes(maxGenes);
        }
        return engine;
    }

    private static int squareSideFor(final int cells) {
        final int side = (int) Math.ceil(Math.sqrt(cells));
        return Math.max(1, side);
    }
}
