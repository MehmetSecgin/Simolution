package com.simolution.support;

import java.io.IOException;
import java.io.StringReader;

import com.simolution.observatory.MetricsTable;

public final class MetricsCsv {

    private static final double ENERGY = 1000.0;

    private final StringBuilder csv = new StringBuilder("tick,population,lineages_alive,births_cum,deaths_cum,"
            + "energy_total,field_total,sink,inflow_cum,audit_error,max_generation,mass_total,"
            + "top_lineage,top_lineage_share\n");
    private int tick;

    public MetricsCsv populations(final int... populations) {
        for (int population : populations) {
            row(population, 2, 0.0, 0.0);
        }
        return this;
    }

    public MetricsCsv repeat(final int ticks, final int population) {
        for (int i = 0; i < ticks; i++) {
            row(population, 2, 0.0, 0.0);
        }
        return this;
    }

    public MetricsCsv row(final int population, final int lineages, final double topShare, final double auditError) {
        tick++;
        csv.append(tick).append(',').append(population).append(',').append(lineages).append(",0,0,")
                .append(ENERGY).append(",0.0,0.0,0.0,").append(auditError).append(",0,0.0,")
                .append(population == 0 ? -1 : 0).append(',').append(topShare).append('\n');
        return this;
    }

    public MetricsTable table() throws IOException {
        return MetricsTable.read(new StringReader(csv.toString()));
    }
}
