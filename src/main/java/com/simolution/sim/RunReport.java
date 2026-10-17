package com.simolution.sim;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.config.EngineConfig;

/** Same code, config and seed render a byte-identical report: no wall-clock data belongs here. */
public final class RunReport {

    public static final String SCHEMA = "report-v15";
    private static final String GENOME_SOURCE = "random";

    private RunReport() {}

    public static String render(final RunConfig config, final StructuralStats structure, final DynamicsSummary dynamics) {
        final StringBuilder out = new StringBuilder(2048);
        appendHeader(out, config);
        appendStructure(out, structure);
        appendDynamics(out, dynamics);
        appendReproduction(out, dynamics.population());
        appendEnergy(out, dynamics);
        appendMass(out, dynamics);
        line(out, "state-digest", String.format("%016x", dynamics.stateDigest()));
        return out.toString();
    }

    private static void appendHeader(final StringBuilder out, final RunConfig config) {
        final EngineConfig engine = config.engine();
        final int worldWidth = engine.world().worldWidth();
        out.append("# Simolution run report\n");
        line(out, "schema", SCHEMA);
        line(out, "kernel", EngineVersion.ID);
        line(out, "seed", engine.seed());
        line(out, "units", config.founderCount());
        line(out, "world-width", worldWidth);
        line(out, "grid-cells", worldWidth * worldWidth);
        line(out, "ticks", config.ticks());
        line(out, "genome-source", GENOME_SOURCE);
        line(out, "genes-per-unit", config.genesPerFounder());
        line(out, "max-genes", engine.world().maxGenes());
    }

    private static void appendStructure(final StringBuilder out, final StructuralStats structure) {
        section(out, "structure");
        line(out, "connections-compiled", structure.connectionsCompiled());
        line(out, "connections-meaningful", structure.connectionsMeaningful());
        line(out, "connections-junk-touching", structure.connectionsJunkTouching());
        line(out, "duplicate-connection-pairs", structure.duplicatePairs());
        line(out, "weight-abs-mean", structure.weightAbsMean());
        line(out, "weight-abs-max", structure.weightAbsMax());
        line(out, "weight-positive-fraction", structure.weightPositiveFraction());
        line(out, "units-sensor-action-reachable", structure.countReachable());
        line(out, "units-rand-wired", structure.countRandWired());
    }

    private static void appendDynamics(final StringBuilder out, final DynamicsSummary dynamics) {
        section(out, "dynamics");
        line(out, "ticks-with-any-activity", dynamics.activity().ticksWithAnyActivity());
        line(out, "signal-propagations-total", dynamics.activity().propagationsTotal());
    }

    private static void appendReproduction(final StringBuilder out, final DynamicsSummary.Population population) {
        section(out, "reproduction");
        line(out, "births-total", population.birthsTotal());
        line(out, "max-generation", population.maxGeneration());
        line(out, "peak-population", population.peak());
        line(out, "final-population", population.finalCount());
        line(out, "distinct-lineages-alive", population.distinctLineagesAlive());
    }

    private static void appendEnergy(final StringBuilder out, final DynamicsSummary dynamics) {
        final DynamicsSummary.EnergyLedger energy = dynamics.energy();
        section(out, "energy");
        line(out, "initial-energy-total", energy.initialEnergy());
        line(out, "final-energy-total", energy.finalEnergy());
        line(out, "energy-sink", energy.finalSink());
        line(out, "initial-reservoir", energy.initialResource());
        line(out, "final-reservoir", energy.finalResource());
        line(out, "cumulative-inflow", energy.cumulativeInflow());
        line(out, "intake-total", energy.intakeTotal());
        line(out, "energy-audit-error", dynamics.energyAuditError());
    }

    private static void appendMass(final StringBuilder out, final DynamicsSummary dynamics) {
        final DynamicsSummary.Mass mass = dynamics.mass();
        section(out, "mass");
        line(out, "initial-mass-total", mass.initialMass());
        line(out, "final-mass-total", mass.finalTotal());
        line(out, "final-mass-mean", dynamics.finalMassMean());
        line(out, "final-mass-max", mass.finalMax());
    }

    private static void section(final StringBuilder out, final String title) {
        out.append("\n## ").append(title).append('\n');
    }

    private static void line(final StringBuilder out, final String key, final Object value) {
        out.append(key).append(": ").append(value).append('\n');
    }
}
