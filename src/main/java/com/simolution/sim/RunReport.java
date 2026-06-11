package com.simolution.sim;

import java.util.Arrays;

/**
 * Renders a run into the report-v1 text format
 * (docs/specs/report-v1.md). Every value is deterministic: same code +
 * same config produce a byte-identical file. No wall-clock data belongs
 * here — timing goes to the console, never the report.
 */
public final class RunReport {

    public static final String SCHEMA = "report-v1";
    private static final int PER_UNIT_LINE_LIMIT = 20;

    private RunReport() {}

    public static String render(final RunConfig config, final StructuralStats structure, final DynamicsSummary dynamics) {
        final StringBuilder out = new StringBuilder(2048);
        final int units = config.units();

        out.append("# Simolution run report\n");
        out.append("schema: ").append(SCHEMA).append('\n');
        out.append("kernel: v0.1\n");
        out.append("seed: ").append(config.seed()).append('\n');
        out.append("units: ").append(units).append('\n');
        out.append("ticks: ").append(config.ticks()).append('\n');
        out.append("genome-source: ").append(config.demo() ? "builtin-demo" : "random").append('\n');
        out.append("genes-per-unit: ").append(config.demo() ? 4 : config.genesPerUnit()).append('\n');

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

        if (units <= PER_UNIT_LINE_LIMIT) {
            out.append("\n## units\n");
            out.append("unit | regime | reachable | rand-wired | first-activity-tick | final-abs-y | max-abs-output\n");
            for (int unit = 0; unit < units; unit++) {
                out.append(unit)
                   .append(" | ").append(regimeName(dynamics.regime(unit)))
                   .append(" | ").append(structure.sensorActionReachable()[unit] ? "yes" : "no")
                   .append(" | ").append(structure.randWired()[unit] ? "yes" : "no")
                   .append(" | ").append(dynamics.firstActivityTick()[unit])
                   .append(" | ").append(dynamics.finalAbsAction()[unit])
                   .append(" | ").append(dynamics.maxAbsOutput()[unit])
                   .append('\n');
            }
        }

        out.append("\nstate-digest: ").append(String.format("%016x", dynamics.stateDigest())).append('\n');
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

    private static String regimeName(final DynamicsSummary.Regime regime) {
        return switch (regime) {
            case FIXED_POINT -> "fixed-point";
            case BOUNDED -> "bounded";
            case DIVERGENT -> "divergent";
        };
    }
}
