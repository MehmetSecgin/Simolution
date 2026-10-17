package com.simolution.sim;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.config.EngineConfig;

/**
 * Renders a run into the report text format. Every value is deterministic: same
 * code + same config produce a byte-identical file. No wall-clock data belongs
 * here — timing goes to the console, never the report. report-v13 retired the
 * per-unit/per-wiring CSV sidecars (re-derivable, off the inspection path); the
 * lineage/birth history now lives in the {@code .births.csv.gz} query store.
 */
public final class RunReport {

    public static final String SCHEMA = "report-v15";

    private RunReport() {}

    public static String render(final RunConfig config, final StructuralStats structure, final DynamicsSummary dynamics) {
        final StringBuilder out = new StringBuilder(2048);
        final int units = config.founderCount();
        final EngineConfig engine = config.engine();

        out.append("# Simolution run report\n");
        out.append("schema: ").append(SCHEMA).append('\n');
        out.append("kernel: ").append(EngineVersion.ID).append('\n');
        out.append("seed: ").append(engine.seed()).append('\n');
        out.append("units: ").append(units).append('\n');
        out.append("world-width: ").append(engine.worldWidth()).append('\n');
        out.append("grid-cells: ").append(engine.worldWidth() * engine.worldWidth()).append('\n');
        out.append("ticks: ").append(config.ticks()).append('\n');
        out.append("genome-source: ").append(genomeSourceName(config.genomes())).append('\n');
        out.append("genes-per-unit: ").append(config.genomes().genesPerFounder()).append('\n');
        out.append("max-genes: ").append(engine.maxGenes()).append('\n');

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

        out.append("\n## mass\n");
        final int massPopulation = dynamics.finalPopulation();
        out.append("initial-mass-total: ").append(dynamics.creditedInitialMass()).append('\n');
        out.append("final-mass-total: ").append(dynamics.finalMassTotal()).append('\n');
        out.append("final-mass-mean: ")
           .append(massPopulation == 0 ? 0.0 : dynamics.finalMassTotal() / massPopulation).append('\n');
        out.append("final-mass-max: ").append(dynamics.finalMassMax()).append('\n');

        out.append("state-digest: ").append(String.format("%016x", dynamics.stateDigest())).append('\n');
        return out.toString();
    }

    private static String genomeSourceName(final GenomeSource genomes) {
        return switch (genomes) {
            case GenomeSource.Demo _ -> "builtin-demo";
            case GenomeSource.Random _ -> "random";
        };
    }
}
