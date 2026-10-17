package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.runtime.KernelSnapshot;

/** Streams one row of run aggregates per tick to metrics.csv; format in docs/specs/report-v8.md. */
public class MetricsWriter implements TickObserver, Closeable {

    private final Writer out;
    private final int founderCount;
    private final StringBuilder line = new StringBuilder(160);
    private final boolean[] lineageSeen;

    private int population;
    private int lineagesAlive;
    private double energyTotal;

    public MetricsWriter(final Writer out, final int founderCount) throws IOException {
        this.out = out;
        this.founderCount = founderCount;
        this.lineageSeen = new boolean[founderCount];
        out.write("tick,population,lineages_alive,births_cum,deaths_cum,energy_total,"
                + "field_total,sink,inflow_cum,audit_error,max_generation,mass_total\n");
    }

    @Override
    public void observe(final KernelSnapshot snapshot) throws IOException {
        tally(snapshot);
        final double fieldTotal = snapshot.reservoir();
        final double massTotal = snapshot.massTotal();
        appendRow(snapshot, fieldTotal, massTotal, auditError(snapshot, fieldTotal, massTotal));
    }

    private void tally(final KernelSnapshot snapshot) {
        Arrays.fill(lineageSeen, false);
        population = 0;
        lineagesAlive = 0;
        energyTotal = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] <= 0.0) {
                continue;
            }
            population++;
            energyTotal += snapshot.energy[slot];
            final long lineageId = snapshot.lineageId[slot];
            if (lineageId >= 0 && lineageId < founderCount && !lineageSeen[(int) lineageId]) {
                lineageSeen[(int) lineageId] = true;
                lineagesAlive++;
            }
        }
    }

    private double auditError(final KernelSnapshot snapshot, final double fieldTotal, final double massTotal) {
        return (snapshot.creditedInitialEnergy + snapshot.initialResourceTotal
                + snapshot.creditedInitialMass + snapshot.cumulativeInflow)
                - (energyTotal + fieldTotal + snapshot.energySink + massTotal);
    }

    private void appendRow(final KernelSnapshot snapshot, final double fieldTotal, final double massTotal,
            final double auditError) throws IOException {
        final long deaths = founderCount + snapshot.birthsTotal - population;
        line.setLength(0);
        line.append(snapshot.tick).append(',')
                .append(population).append(',')
                .append(lineagesAlive).append(',')
                .append(snapshot.birthsTotal).append(',')
                .append(deaths).append(',')
                .append(energyTotal).append(',')
                .append(fieldTotal).append(',')
                .append(snapshot.energySink).append(',')
                .append(snapshot.cumulativeInflow).append(',')
                .append(auditError).append(',')
                .append(snapshot.maxGeneration).append(',')
                .append(massTotal).append('\n');
        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
