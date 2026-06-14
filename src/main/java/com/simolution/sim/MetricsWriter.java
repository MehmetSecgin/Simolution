package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.runtime.KernelSnapshot;

/**
 * Streams per-tick run aggregates to {@code metrics.csv} (report-v8). One
 * fixed-width row per tick, written and flushed immediately — nothing
 * accumulates in memory (memory doctrine: footprint constant in tick count).
 * Every quantity is read straight off the snapshot or derived in O(units) with
 * no allocation (a reused {@link StringBuilder} and a reused lineage-seen
 * scratch buffer, both sized once in the constructor).
 * <p>
 * {@code deaths_cum} is derived, not stored: every individual ever alive is a
 * founder or a birth, and a living slot is one that has not died, so
 * {@code deaths = founderCount + births_cum − population}. {@code audit_error}
 * is the closed-system residual (contract v3 §7) and must stay ~0.
 */
public final class MetricsWriter implements Closeable {

    private final Writer out;
    private final int founderCount;
    private final StringBuilder line;
    private final boolean[] lineageSeen;

    public MetricsWriter(final Writer out, final int founderCount, final int maxUnits) throws IOException {
        this.out = out;
        this.founderCount = founderCount;
        this.line = new StringBuilder(160);
        this.lineageSeen = new boolean[founderCount];
        out.write("tick,population,lineages_alive,births_cum,deaths_cum,energy_total,"
                + "field_total,sink,inflow_cum,audit_error,max_generation,mass_total\n");
    }

    public void sample(final KernelSnapshot snapshot) throws IOException {
        Arrays.fill(lineageSeen, false);
        int population = 0;
        int lineagesAlive = 0;
        double energyTotal = 0.0;
        final double[] energy = snapshot.energy;
        final long[] lineageId = snapshot.lineageId;
        for (int slot = 0; slot < energy.length; slot++) {
            if (energy[slot] <= 0.0) {
                continue;
            }
            population++;
            energyTotal += energy[slot];
            final long l = lineageId[slot];
            if (l >= 0 && l < founderCount && !lineageSeen[(int) l]) {
                lineageSeen[(int) l] = true;
                lineagesAlive++;
            }
        }
        final long births = snapshot.birthsTotal;
        final long deaths = founderCount + births - population;
        final double fieldTotal = snapshot.reservoir;
        final double sink = snapshot.energySink;
        final double inflow = snapshot.cumulativeInflow;
        final double auditError = (snapshot.creditedInitialEnergy + snapshot.initialResourceTotal
                + snapshot.creditedInitialMass + inflow)
                - (energyTotal + fieldTotal + sink + snapshot.massTotal);

        line.setLength(0);
        line.append(snapshot.tick).append(',')
            .append(population).append(',')
            .append(lineagesAlive).append(',')
            .append(births).append(',')
            .append(deaths).append(',')
            .append(energyTotal).append(',')
            .append(fieldTotal).append(',')
            .append(sink).append(',')
            .append(inflow).append(',')
            .append(auditError).append(',')
            .append(snapshot.maxGeneration).append(',')
            .append(snapshot.massTotal).append('\n');
        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
