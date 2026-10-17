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

    public EngineConfig {
        if (worldWidth < 1) {
            throw new IllegalArgumentException("worldWidth must be at least 1, got " + worldWidth);
        }
        if (maxGenes < 0) {
            throw new IllegalArgumentException("maxGenes must be at least 0, got " + maxGenes);
        }
        if ((long) worldWidth * worldWidth * maxGenes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxGenes " + maxGenes + " with worldWidth " + worldWidth
                    + " needs more gene slots than an int array holds");
        }
        validateChemicals(chemicals);
        nonNegative("initialEnergy", initialEnergy);
        nonNegative("basalCost", basalCost);
        nonNegative("costPerPropagation", costPerPropagation);
        nonNegative("harvestCapacityPerConnection", harvestCapacityPerConnection);
        nonNegative("harvestHalfSaturation", harvestHalfSaturation);
        nonNegative("harvestMassExponent", harvestMassExponent);
        nonNegative("moveCostBase", moveCostBase);
        nonNegative("moveCostPerMass", moveCostPerMass);
        nonNegative("moveDeadzone", moveDeadzone);
        positive("selfEnergyScale", selfEnergyScale);
        positive("selfMassScale", selfMassScale);
        nonNegative("initialMass", initialMass);
        nonNegative("maintPerMass", maintPerMass);
        nonNegative("growMax", growMax);
        nonNegative("growHalfSaturation", growHalfSaturation);
        unitInterval("growYield", growYield);
        nonNegative("buildCost", buildCost);
        nonNegative("buildCostPerGene", buildCostPerGene);
        unitInterval("mutationRateWeight", mutationRateWeight);
        unitInterval("mutationRateStruct", mutationRateStruct);
        unitInterval("indelRateDup", indelRateDup);
        unitInterval("indelRateDel", indelRateDel);
        nonNegative("agingCost", agingCost);
    }

    private static void validateChemicals(final List<ChemicalSpec> chemicals) {
        if (chemicals.isEmpty()) {
            throw new IllegalArgumentException("chemicals must hold at least one entry (units sense and harvest chemical 0)");
        }
        for (int k = 0; k < chemicals.size(); k++) {
            final ChemicalSpec chemical = chemicals.get(k);
            final String at = "chemicals[" + k + "].";
            positive(at + "capacity", chemical.capacity());
            nonNegative(at + "initial", chemical.initial());
            unitInterval(at + "diffusionRate", chemical.diffusionRate());
            unitInterval(at + "decayRate", chemical.decayRate());
            final int target = chemical.decayTarget();
            if (target < -1 || target >= chemicals.size()) {
                throw new IllegalArgumentException(at + "decayTarget " + target
                        + " is neither -1 (sink) nor a chemical index");
            }
            if (target == k) {
                throw new IllegalArgumentException(at + "decayTarget cannot be the chemical itself");
            }
        }
    }

    private static void nonNegative(final String field, final double value) {
        if (!(value >= 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and at least 0, got " + value);
        }
    }

    private static void positive(final String field, final double value) {
        if (!(value > 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and above 0, got " + value);
        }
    }

    private static void unitInterval(final String field, final double value) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(field + " must be in [0, 1], got " + value);
        }
    }

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

    public EngineConfig withChemicals(final List<ChemicalSpec> newChemicals) {
        return copy(seed, worldWidth, maxGenes, List.copyOf(newChemicals));
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
