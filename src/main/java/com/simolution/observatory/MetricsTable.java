package com.simolution.observatory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunManifest;

public final class MetricsTable {

    private static final List<String> CHART_COLUMNS =
            List.of("tick", "population", "energy_total", "births_per_tick", "lineages_alive", "mass_total");
    private static final int INITIAL_ROWS = 1024;

    private final String[] names;
    private final double[][] columns;
    private final int rows;

    private MetricsTable(final String[] names, final double[][] columns, final int rows) {
        this.names = names;
        this.columns = columns;
        this.rows = rows;
    }

    public static MetricsTable read(final Reader in) throws IOException {
        final BufferedReader lines = new BufferedReader(in);
        final String header = lines.readLine();
        if (header == null) {
            return new MetricsTable(new String[0], new double[0][], 0);
        }
        final String[] names = header.split(",");
        double[][] columns = new double[names.length][INITIAL_ROWS];
        int rows = 0;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            final String[] cells = line.split(",");
            if (cells.length != names.length) {
                continue;
            }
            if (rows == columns[0].length) {
                columns = grown(columns);
            }
            for (int c = 0; c < names.length; c++) {
                columns[c][rows] = Double.parseDouble(cells[c]);
            }
            rows++;
        }
        return new MetricsTable(names, columns, rows);
    }

    public static MetricsTable forRun(final Path runDir) throws IOException {
        final RunPaths paths = new RunPaths(runDir);
        final MetricsTable own = Files.exists(paths.metrics()) ? readFile(paths.metrics()) : read(Reader.nullReader());
        final RunManifest manifest = RunManifest.read(paths.manifest());
        if (manifest.parent() == null) {
            return own;
        }
        final MetricsTable parent = forRun(runDir.resolveSibling(manifest.parent()));
        return parent.upTo(manifest.branchTick()).append(own);
    }

    public int rows() {
        return rows;
    }

    public double[] column(final String name) {
        if (name.equals("births_per_tick")) {
            final double[] cumulative = column("births_cum");
            final double[] perTick = new double[rows];
            for (int r = 0; r < rows; r++) {
                perTick[r] = cumulative[r] - (r == 0 ? 0.0 : cumulative[r - 1]);
            }
            return perTick;
        }
        for (int c = 0; c < names.length; c++) {
            if (names[c].equals(name)) {
                return Arrays.copyOf(columns[c], rows);
            }
        }
        return new double[rows];
    }

    public double last(final String name) {
        return rows == 0 ? 0.0 : column(name)[rows - 1];
    }

    public double max(final String name) {
        double max = 0.0;
        for (double value : column(name)) {
            max = Math.max(max, value);
        }
        return max;
    }

    public Map<String, double[]> downsample(final int points) {
        final int buckets = Math.max(1, Math.min(points, rows));
        final Map<String, double[]> out = new LinkedHashMap<>();
        for (String name : CHART_COLUMNS) {
            final double[] values = column(name);
            final double[] means = new double[rows == 0 ? 0 : buckets];
            for (int b = 0; b < means.length; b++) {
                final int from = (int) ((long) b * rows / buckets);
                final int to = (int) ((long) (b + 1) * rows / buckets);
                double sum = 0.0;
                for (int r = from; r < to; r++) {
                    sum += values[r];
                }
                means[b] = sum / (to - from);
            }
            out.put(name, means);
        }
        return out;
    }

    private static MetricsTable readFile(final Path file) throws IOException {
        try (Reader in = Files.newBufferedReader(file)) {
            return read(in);
        }
    }

    private MetricsTable upTo(final int tick) {
        final int tickColumn = Arrays.asList(names).indexOf("tick");
        int kept = 0;
        while (kept < rows && columns[tickColumn][kept] <= tick) {
            kept++;
        }
        return new MetricsTable(names, columns, kept);
    }

    private MetricsTable append(final MetricsTable tail) {
        if (rows == 0) {
            return tail;
        }
        final double[][] joined = new double[names.length][];
        for (int c = 0; c < names.length; c++) {
            final int tailColumn = Arrays.asList(tail.names).indexOf(names[c]);
            joined[c] = Arrays.copyOf(columns[c], rows + tail.rows);
            if (tailColumn >= 0) {
                System.arraycopy(tail.columns[tailColumn], 0, joined[c], rows, tail.rows);
            }
        }
        return new MetricsTable(names, joined, rows + tail.rows);
    }

    private static double[][] grown(final double[][] columns) {
        final double[][] bigger = new double[columns.length][];
        for (int c = 0; c < columns.length; c++) {
            bigger[c] = Arrays.copyOf(columns[c], columns[c].length * 2);
        }
        return bigger;
    }
}
