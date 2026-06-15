package com.simolution.sim;

import java.util.Arrays;

/**
 * Renders a run into the report text format. Every value is deterministic: same
 * code + same config produce a byte-identical file. No wall-clock data belongs
 * here — timing goes to the console, never the report. report-v13 retired the
 * per-unit/per-wiring CSV sidecars (re-derivable, off the inspection path); the
 * lineage/birth history now lives in the {@code .births.csv.gz} query store.
 */
public final class RunReport {

    public static final String SCHEMA = "report-v10";

    private RunReport() {}

    public static String render(final RunConfig config, final StructuralStats structure, final DynamicsSummary dynamics) {
        final StringBuilder out = new StringBuilder(2048);
        final int units = config.units();

        out.append("# Simolution run report\n");
        out.append("schema: ").append(SCHEMA).append('\n');
        out.append("kernel: v5\n");
        out.append("seed: ").append(config.seed()).append('\n');
        out.append("units: ").append(units).append('\n');
        out.append("world-width: ").append(config.worldWidth()).append('\n');
        out.append("grid-cells: ").append(config.worldWidth() * config.worldWidth()).append('\n');
        out.append("ticks: ").append(config.ticks()).append('\n');
        out.append("genome-source: ").append(config.demo() ? "builtin-demo" : "random").append('\n');
        out.append("genes-per-unit: ").append(config.demo() ? 4 : config.genesPerUnit()).append('\n');
        out.append("max-genes: ").append(config.maxGenes()).append('\n');

        out.append("\n## structure\n");
        out.append("connections-compiled: ").append(structure.connectionsCompiled()).append('\n');
        out.append("connections-meaningful: ").append(structure.connectionsMeaningful()).append('\n');
        out.append("connections-junk-touching: ")
           .append(structure.connectionsCompiled() - structure.connectionsMeaningful()).append('\n');
        out.append("duplicate-connection-pairs: ").append(structure.duplicatePairs()).append('\n');
        out.append("weight-abs-mean: ").append(structure.weightAbsMean()).append('\n');
        out.append("weight-abs-max: ").append(structure.weightAbsMax()).append('\n');
        out.append("weight-positive-fraction: ").append(structure.weightPositiveFraction()).append('\n');
        out.append("units-sensor-action-reachable: ").append(structure.countReachable()).append('\n');
        out.append("units-rand-wired: ").append(structure.countRandWired()).append('\n');

        out.append("\n## dynamics\n");
        out.append("ticks-with-any-activity: ").append(dynamics.ticksWithAnyActivity()).append('\n');
        out.append("signal-propagations-total: ").append(dynamics.propagationsTotal()).append('\n');
        out.append("junk-sink-propagations: ").append(dynamics.junkSinkPropagations()).append('\n');
        out.append("junk-sink-propagation-fraction: ").append(dynamics.junkSinkPropagationFraction()).append('\n');
        out.append("clamp-saturation-events: ").append(dynamics.clampSaturationEvents()).append('\n');
        out.append("thresh-flips-total: ").append(dynamics.threshFlipsTotal()).append('\n');
        out.append("units-dormant-from-birth: ").append(dynamics.countDormantFromBirth()).append('\n');
        out.append("units-dormant-at-end: ").append(dynamics.countDormantAtEnd()).append('\n');

        out.append("\n## reproduction\n");
        out.append("births-total: ").append(dynamics.birthsTotal()).append('\n');
        out.append("max-generation: ").append(dynamics.maxGeneration()).append('\n');
        out.append("peak-population: ").append(dynamics.peakPopulation()).append('\n');
        out.append("final-population: ").append(dynamics.finalPopulation()).append('\n');
        out.append("distinct-lineages-alive: ").append(dynamics.distinctLineagesAlive()).append('\n');

        out.append("\n## energy\n");
        out.append("initial-energy-total: ").append(dynamics.initialEnergyTotal()).append('\n');
        out.append("final-energy-total: ").append(dynamics.finalEnergyTotal()).append('\n');
        out.append("energy-sink: ").append(dynamics.finalEnergySink()).append('\n');
        out.append("initial-reservoir: ").append(dynamics.initialReservoir()).append('\n');
        out.append("final-reservoir: ").append(dynamics.finalReservoir()).append('\n');
        out.append("cumulative-inflow: ").append(dynamics.cumulativeInflow()).append('\n');
        out.append("intake-total: ").append(dynamics.intakeTotal()).append('\n');
        out.append("energy-audit-error: ").append(dynamics.energyAuditError()).append('\n');
        out.append("units-alive-at-end: ").append(dynamics.countAliveAtEnd()).append('\n');
        out.append("units-dead-at-end: ").append(units - dynamics.countAliveAtEnd()).append('\n');
        final int[] deathTicks = dynamics.sortedDeathTicks();
        out.append("death-tick-first: ").append(deathTicks.length == 0 ? -1 : deathTicks[0]).append('\n');
        out.append("death-tick-median: ")
           .append(deathTicks.length == 0 ? -1 : deathTicks[deathTicks.length / 2]).append('\n');
        out.append("death-tick-last: ")
           .append(deathTicks.length == 0 ? -1 : deathTicks[deathTicks.length - 1]).append('\n');

        out.append("\n## mass\n");
        final int massPopulation = dynamics.finalPopulation();
        out.append("initial-mass-total: ").append(dynamics.creditedInitialMass()).append('\n');
        out.append("final-mass-total: ").append(dynamics.finalMassTotal()).append('\n');
        out.append("final-mass-mean: ")
           .append(massPopulation == 0 ? 0.0 : dynamics.finalMassTotal() / massPopulation).append('\n');
        out.append("final-mass-max: ").append(dynamics.finalMassMax()).append('\n');

        out.append("\n## intake\n");
        out.append("units-ever-harvested: ").append(dynamics.countEverHarvested()).append('\n');
        out.append("harvest-active-ticks-total: ").append(dynamics.harvestActiveTicksTotal()).append('\n');

        out.append("\n## burn-rate\n");
        final double[] burnRates = dynamics.meanBurnRates().clone();
        Arrays.sort(burnRates);
        out.append("mean-burn-rate-p0: ").append(quantile(burnRates, 0)).append('\n');
        out.append("mean-burn-rate-p50: ").append(quantile(burnRates, 50)).append('\n');
        out.append("mean-burn-rate-p90: ").append(quantile(burnRates, 90)).append('\n');
        out.append("mean-burn-rate-p100: ").append(quantile(burnRates, 100)).append('\n');

        out.append("\n## terminal-regimes\n");
        out.append("fixed-point: ").append(dynamics.countRegime(DynamicsSummary.Regime.FIXED_POINT)).append('\n');
        out.append("bounded: ").append(dynamics.countRegime(DynamicsSummary.Regime.BOUNDED)).append('\n');
        out.append("divergent: ").append(dynamics.countRegime(DynamicsSummary.Regime.DIVERGENT)).append('\n');
        out.append("divergence-cutoff: ").append(DynamicsSummary.DIVERGENCE_CUTOFF).append('\n');

        out.append("\n## action-channel\n");
        final double[] finiteFinalY = Arrays.stream(dynamics.finalAbsAction())
                                            .filter(Double::isFinite)
                                            .sorted()
                                            .toArray();
        final int nonFiniteCount = units - finiteFinalY.length;
        out.append("units-final-y-nonfinite: ").append(nonFiniteCount).append('\n');
        out.append("final-abs-y-p0: ").append(quantile(finiteFinalY, 0)).append('\n');
        out.append("final-abs-y-p50: ").append(quantile(finiteFinalY, 50)).append('\n');
        out.append("final-abs-y-p90: ").append(quantile(finiteFinalY, 90)).append('\n');
        out.append("final-abs-y-p100: ").append(quantile(finiteFinalY, 100)).append('\n');

        out.append("state-digest: ").append(String.format("%016x", dynamics.stateDigest())).append('\n');
        return out.toString();
    }

    /**
     * Nearest-rank-style quantile on the sorted array:
     * index = floor(p/100 * (n-1)). Deterministic, no interpolation.
     * Computed over finite values only — non-finite units are counted
     * separately and classified divergent.
     */
    private static double quantile(final double[] sorted, final int p) {
        if (sorted.length == 0) {
            return 0.0;
        }
        final int index = (int) Math.floor(p / 100.0 * (sorted.length - 1));
        return sorted[index];
    }
}
