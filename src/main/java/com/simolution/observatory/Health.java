package com.simolution.observatory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

public final class Health {

    static final double WORLD_FULL = 0.95;
    static final double AUDIT_WARN = 1e-6;
    static final double AUDIT_BAD = 1e-3;

    public enum Level {
        OK, WARN, BAD;

        @JsonValue
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Check(
            String name,
            Level level,
            String text
    ) {}

    private Health() {}

    public static List<Check> of(final RunStatus status, final int cells, final MetricsTable metrics,
            final Twins.TwinResult twin, final boolean twinPending) {
        return List.of(alive(status, cells, metrics), audit(metrics), lineages(metrics),
                determinism(twin, twinPending));
    }

    public static Level worst(final List<Check> checks) {
        return checks.stream().map(Check::level).max(Comparator.naturalOrder()).orElse(Level.OK);
    }

    private static Check alive(final RunStatus status, final int cells, final MetricsTable metrics) {
        final long population = (long) metrics.last("population");
        if (status.status() == RunState.EXTINCT || metrics.rows() > 0 && population == 0) {
            return new Check("Alive", Level.BAD, "Extinct at tick " + (long) metrics.last("tick") + ".");
        }
        if (population >= WORLD_FULL * cells) {
            return new Check("Alive", Level.WARN,
                    "World full: " + population + " units on " + cells + " cells.");
        }
        return new Check("Alive", Level.OK, population + " units alive.");
    }

    private static Check audit(final MetricsTable metrics) {
        final double[] error = metrics.column("audit_error");
        final double[] energy = metrics.column("energy_total");
        final double[] field = metrics.column("field_total");
        final double[] sink = metrics.column("sink");
        final double[] mass = metrics.column("mass_total");
        double worst = 0.0;
        for (int r = 0; r < metrics.rows(); r++) {
            final double system = energy[r] + field[r] + sink[r] + mass[r];
            if (system > 0.0) {
                worst = Math.max(worst, Math.abs(error[r]) / system);
            }
        }
        final String share = String.format(Locale.ROOT, "%.1e", worst);
        if (worst > AUDIT_BAD) {
            return new Check("Energy audit", Level.BAD, "Energy leaks: error up to " + share + " of system energy.");
        }
        if (worst > AUDIT_WARN) {
            return new Check("Energy audit", Level.WARN, "Energy error up to " + share + " of system energy.");
        }
        return new Check("Energy audit", Level.OK, "Energy balances (error up to " + share + ").");
    }

    private static Check lineages(final MetricsTable metrics) {
        final long alive = (long) metrics.last("lineages_alive");
        if (alive >= 2) {
            return new Check("Lineages", Level.OK, alive + " lineages alive.");
        }
        if (alive == 1) {
            return new Check("Lineages", Level.WARN, "One lineage left: a monoculture.");
        }
        return new Check("Lineages", Level.WARN, "No lineage alive.");
    }

    private static Check determinism(final Twins.TwinResult twin, final boolean twinPending) {
        if (twin != null && twin.identical()) {
            return new Check("Determinism", Level.OK,
                    "A re-run matched at all " + twin.checkpoints() + " checkpoints and the final state.");
        }
        if (twin != null) {
            return new Check("Determinism", Level.BAD,
                    "A re-run differs from checkpoint tick " + twin.firstDifferentTick() + ": a determinism bug.");
        }
        if (twinPending) {
            return new Check("Determinism", Level.WARN, "Re-run in progress.");
        }
        return new Check("Determinism", Level.WARN, "Not checked yet. Re-run to check.");
    }
}
