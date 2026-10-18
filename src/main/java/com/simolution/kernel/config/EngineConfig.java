package com.simolution.kernel.config;

import java.util.ArrayList;
import java.util.List;

public record EngineConfig(
        long seed,
        World world,
        List<ChemicalSpec> chemicals,
        Founder founder,
        Metabolism metabolism,
        Harvest harvest,
        Motility motility,
        Growth growth,
        Sensing sensing,
        Reproduction reproduction,
        Mutation mutation
) {

    public EngineConfig {
        validateChemicals(chemicals);
    }

    static void validateChemicals(final List<ChemicalSpec> chemicals) {
        if (chemicals.isEmpty()) {
            throw new IllegalArgumentException(
                    "chemicals must hold at least one entry (units sense and harvest chemical 0)");
        }
        for (int k = 0; k < chemicals.size(); k++) {
            final ChemicalSpec chemical = chemicals.get(k);
            final String at = "chemicals[" + k + "].";
            Checks.positive(at + "capacity", chemical.capacity());
            Checks.nonNegative(at + "initial", chemical.initial());
            Checks.unitInterval(at + "diffusionRate", chemical.diffusionRate());
            Checks.unitInterval(at + "decayRate", chemical.decayRate());
        }
    }

    public static EngineConfig defaults(final long seed, final int worldWidth, final int maxGenes) {
        return new EngineConfig(
                seed,
                new World(worldWidth, maxGenes),
                List.of(new ChemicalSpec("resource", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02)),
                new Founder(1000.0, 1.0),
                new Metabolism(0.5, 0.06, 0.5, 0.0005),
                new Harvest(1.5, 1.0),
                new Motility(0.2, 0.1, 0.1),
                new Growth(5.0, 1.0, 0.8),
                new Sensing(2000.0, 10.0),
                new Reproduction(10.0, 0.25),
                new Mutation(0.000025, 0.000005, 0.0001, 0.0001));
    }

    public EngineConfig withSeed(final long newSeed) {
        return new EngineConfig(newSeed, world, chemicals, founder, metabolism, harvest, motility, growth,
                sensing, reproduction, mutation);
    }

    public EngineConfig withWorldWidth(final int newWorldWidth) {
        return withWorld(new World(newWorldWidth, world.maxGenes()));
    }

    public EngineConfig withMaxGenes(final int newMaxGenes) {
        return withWorld(new World(world.worldWidth(), newMaxGenes));
    }

    public EngineConfig withChemicals(final List<ChemicalSpec> newChemicals) {
        return new EngineConfig(seed, world, List.copyOf(newChemicals), founder, metabolism, harvest, motility,
                growth, sensing, reproduction, mutation);
    }

    public EngineConfig withResourceInflow(final InflowConfig inflow) {
        final ChemicalSpec first = chemicals.get(0);
        final List<ChemicalSpec> updated = new ArrayList<>(chemicals);
        updated.set(0, new ChemicalSpec(first.name(), inflow, first.capacity(), first.initial(),
                first.diffusionRate(), first.decayRate()));
        return withChemicals(updated);
    }

    private EngineConfig withWorld(final World newWorld) {
        return new EngineConfig(seed, newWorld, chemicals, founder, metabolism, harvest, motility, growth,
                sensing, reproduction, mutation);
    }
}
