package com.simolution.sim;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import com.simolution.kernel.state.WorldState;

/** Streams one row of run aggregates per tick to metrics.csv; format in docs/specs/run-directory-v1.md. */
public class MetricsWriter implements TickObserver, Closeable {

    private final Writer out;
    private final int founderCount;
    private final StringBuilder line = new StringBuilder(160);
    private final int[] lineageMembers;

    private int population;
    private int lineagesAlive;
    private int topLineage;
    private double energyTotal;

    public MetricsWriter(final Writer out, final int founderCount) throws IOException {
        this.out = out;
        this.founderCount = founderCount;
        this.lineageMembers = new int[founderCount];
        out.write("tick,population,lineages_alive,births_cum,deaths_cum,energy_total,"
                + "field_total,sink,inflow_cum,audit_error,max_generation,mass_total,"
                + "top_lineage,top_lineage_share\n");
    }

    @Override
    public void observe(final WorldState snapshot) throws IOException {
        tally(snapshot);
        final double fieldTotal = snapshot.reservoir();
        final double massTotal = snapshot.massTotal();
        appendRow(snapshot, fieldTotal, massTotal, auditError(snapshot, fieldTotal, massTotal));
    }

    private void tally(final WorldState snapshot) {
        Arrays.fill(lineageMembers, 0);
        population = 0;
        lineagesAlive = 0;
        topLineage = -1;
        energyTotal = 0.0;
        for (int slot = 0; slot < snapshot.energy.length; slot++) {
            if (snapshot.energy[slot] <= 0.0) {
                continue;
            }
            population++;
            energyTotal += snapshot.energy[slot];
            final long lineageId = snapshot.lineageId[slot];
            if (lineageId >= 0 && lineageId < founderCount && lineageMembers[(int) lineageId]++ == 0) {
                lineagesAlive++;
            }
        }
        for (int lineage = 0; lineage < founderCount; lineage++) {
            if (lineageMembers[lineage] > 0
                    && (topLineage < 0 || lineageMembers[lineage] > lineageMembers[topLineage])) {
                topLineage = lineage;
            }
        }
    }

    private double auditError(final WorldState snapshot, final double fieldTotal, final double massTotal) {
        return (snapshot.creditedInitialEnergy + snapshot.initialResourceTotal
                + snapshot.creditedInitialMass + snapshot.cumulativeInflow)
                - (energyTotal + fieldTotal + snapshot.energySink + massTotal);
    }

    private void appendRow(final WorldState snapshot, final double fieldTotal, final double massTotal,
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
                .append(massTotal).append(',')
                .append(topLineage).append(',');
        if (topLineage < 0) {
            line.append('0');
        } else {
            line.append((double) lineageMembers[topLineage] / population);
        }
        line.append('\n');
        out.write(line.toString());
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
