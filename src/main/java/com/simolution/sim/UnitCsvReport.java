package com.simolution.sim;

/**
 * Per-unit detail as CSV — one row per unit, fixed unit order, so two runs of
 * the same config produce a byte-identical file (diffable like the report).
 * This is the per-unit companion to the aggregate report; it scales to any
 * unit count because it is bounded by units, not ticks (memory discipline).
 * Columns are documented in docs/specs/report-v3.md.
 */
public final class UnitCsvReport {

    public static final String HEADER =
            "unit,connections,meaningful_connections,reachable,rand_wired,"
            + "first_activity_tick,death_tick,lifespan,alive,"
            + "energy_consumed,final_energy,mean_burn_rate,peak_burn,"
            + "total_propagations,ticks_active,clamp_saturations,thresh_flips,"
            + "regime,final_abs_y,max_abs_output";

    private UnitCsvReport() {}

    public static String render(final int units, final StructuralStats structure, final DynamicsSummary dynamics) {
        final StringBuilder out = new StringBuilder(64 * (units + 1));
        out.append(HEADER).append('\n');

        for (int unit = 0; unit < units; unit++) {
            final boolean alive = dynamics.deathTick()[unit] < 0;
            out.append(unit)
               .append(',').append(structure.perUnitConnections()[unit])
               .append(',').append(structure.perUnitMeaningful()[unit])
               .append(',').append(structure.sensorActionReachable()[unit] ? 1 : 0)
               .append(',').append(structure.randWired()[unit] ? 1 : 0)
               .append(',').append(dynamics.firstActivityTick()[unit])
               .append(',').append(dynamics.deathTick()[unit])
               .append(',').append(dynamics.lifespan(unit))
               .append(',').append(alive ? 1 : 0)
               .append(',').append(dynamics.energyConsumed(unit))
               .append(',').append(dynamics.finalEnergy()[unit])
               .append(',').append(dynamics.meanBurnRate(unit))
               .append(',').append(dynamics.peakBurn()[unit])
               .append(',').append(dynamics.propagationsByUnit()[unit])
               .append(',').append(dynamics.ticksActiveByUnit()[unit])
               .append(',').append(dynamics.clampByUnit()[unit])
               .append(',').append(dynamics.threshFlipsByUnit()[unit])
               .append(',').append(regimeName(dynamics.regime(unit)))
               .append(',').append(dynamics.finalAbsAction()[unit])
               .append(',').append(dynamics.maxAbsOutput()[unit])
               .append('\n');
        }
        return out.toString();
    }

    private static String regimeName(final DynamicsSummary.Regime regime) {
        return switch (regime) {
            case FIXED_POINT -> "fixed-point";
            case BOUNDED -> "bounded";
            case DIVERGENT -> "divergent";
        };
    }
}
