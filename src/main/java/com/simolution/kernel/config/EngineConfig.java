package com.simolution.kernel.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Every tunable law constant of one run, as data. A config plus a seed and founder
 * genomes fully determines a run; two configs may share a branch point only if
 * {@link #branchCompatible} says their state arrays have the same shape.
 * <p>
 * Weight scaling and node-layout junk counts are not here: they define the gene
 * encoding and the substrate, not the experiment.
 */
public record EngineConfig(
        long seed,
        int worldWidth,
        int maxGenes,
        List<ChemicalSpec> chemicals,
        double initialEnergy,
        double basalCost,
        double costPerPropagation,
        double harvestCapacityPerConnection,
        double harvestHalfSaturation,
        double harvestMassExponent,
        double moveCostBase,
        double moveCostPerMass,
        double moveDeadzone,
        double selfEnergyScale,
        double selfMassScale,
        double initialMass,
        double maintPerMass,
        double growMax,
        double growHalfSaturation,
        double growYield,
        double buildCost,
        double buildCostPerGene,
        double mutationRateWeight,
        double mutationRateStruct,
        double indelRateDup,
        double indelRateDel,
        double agingCost) {

    public static EngineConfig v5Defaults(final long seed, final int worldWidth, final int maxGenes) {
        return new EngineConfig(
                seed, worldWidth, maxGenes,
                List.of(new ChemicalSpec("resource", InflowConfig.uniform(1.0), 100.0, 0.0, 0.1, 0.02, -1)),
                1000.0, 0.5, 0.06,
                1.5, 1.0, 0.5,
                0.2, 0.1, 0.1,
                2000.0, 10.0,
                1.0, 0.5, 5.0, 1.0, 0.8,
                10.0, 0.25,
                0.000025, 0.000005,
                0.0001, 0.0001,
                0.0005);
    }

    public EngineConfig withChemical0Inflow(final InflowConfig inflow) {
        final ChemicalSpec first = chemicals.get(0);
        final List<ChemicalSpec> updated = new ArrayList<>(chemicals);
        updated.set(0, new ChemicalSpec(first.name(), inflow, first.capacity(), first.initial(),
                first.diffusionRate(), first.decayRate(), first.decayTarget()));
        return copy(seed, worldWidth, maxGenes, List.copyOf(updated));
    }

    public EngineConfig withSeed(final long newSeed) {
        return copy(newSeed, worldWidth, maxGenes, chemicals);
    }

    public EngineConfig withWorldWidth(final int newWorldWidth) {
        return copy(seed, newWorldWidth, maxGenes, chemicals);
    }

    public EngineConfig withMaxGenes(final int newMaxGenes) {
        return copy(seed, worldWidth, newMaxGenes, chemicals);
    }

    private EngineConfig copy(final long newSeed, final int newWorldWidth, final int newMaxGenes,
            final List<ChemicalSpec> newChemicals) {
        return new EngineConfig(newSeed, newWorldWidth, newMaxGenes, newChemicals, initialEnergy, basalCost,
                costPerPropagation, harvestCapacityPerConnection, harvestHalfSaturation, harvestMassExponent,
                moveCostBase, moveCostPerMass, moveDeadzone, selfEnergyScale, selfMassScale, initialMass,
                maintPerMass, growMax, growHalfSaturation, growYield, buildCost, buildCostPerGene,
                mutationRateWeight, mutationRateStruct, indelRateDup, indelRateDel, agingCost);
    }

    public boolean branchCompatible(final EngineConfig other) {
        return worldWidth == other.worldWidth
                && maxGenes == other.maxGenes
                && chemicals.size() == other.chemicals.size();
    }
}
