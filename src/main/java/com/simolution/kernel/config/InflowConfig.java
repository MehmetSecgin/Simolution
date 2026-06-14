package com.simolution.kernel.config;

/**
 * The world's resource-inflow pattern (contract v6). The default,
 * {@link #UNIFORM}, is the v1–v5 law: every cell admits {@code CELL_INFLOW} each
 * tick. {@link #cyclic} switches to a <b>patchy, oscillating</b> source: only a
 * central disk is fed, and its inflow pulses {@code peak · triangle(tick)} over a
 * fixed tick period, going 0 → peak → 0; the periphery is barren (0). Diffusion
 * (the existing phase) then spreads the central pulse outward into a breathing
 * radial gradient.
 * <p>
 * The oscillation is a pure function of {@code tick} — no wall-clock — so
 * determinism (contract v3 §1) holds. The disk is defined in plain cell
 * coordinates centred on {@code (W/2, W/2)} (not toroidal distance): a disk in the
 * middle of the displayed lattice. Inflow stays the audited source
 * (cumulativeInflow), so conservation (contract v5 §6) is unaffected — only the
 * spatial/temporal <i>distribution</i> of inflow changes.
 */
public record InflowConfig(boolean cyclic, int periodTicks, int radius, double peak) {

    public static final InflowConfig UNIFORM = new InflowConfig(false, 0, 0, 0.0);

    public static InflowConfig cyclic(final int periodTicks, final int radius, final double peak) {
        if (periodTicks < 1) {
            throw new IllegalArgumentException("cyclic inflow periodTicks must be >= 1");
        }
        return new InflowConfig(true, periodTicks, radius, peak);
    }

    /**
     * The inflow ceiling for {@code cell} at {@code tick}. Uniform: {@code
     * CELL_INFLOW} everywhere. Cyclic: {@code peak · triangle} inside the central
     * disk, {@code 0} outside. The triangle rises 0 → 1 over the first half of the
     * period and falls 1 → 0 over the second, so a run starts at the trough and
     * fills.
     */
    public double inflowCap(final int cell, final int tick, final int worldWidth) {
        if (!cyclic) {
            return KernelConfig.CELL_INFLOW;
        }
        final int x = cell % worldWidth;
        final int y = cell / worldWidth;
        final int dx = x - worldWidth / 2;
        final int dy = y - worldWidth / 2;
        if (dx * dx + dy * dy > radius * radius) {
            return 0.0;
        }
        final int phase = ((tick % periodTicks) + periodTicks) % periodTicks;
        final int half = Math.max(1, periodTicks / 2);
        final double wave = phase < half
                ? (double) phase / half
                : 2.0 - (double) phase / half;
        return peak * Math.max(0.0, wave);
    }
}
