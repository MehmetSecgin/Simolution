package com.simolution.bench;

import java.util.Arrays;

import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.GenomeFactory;
import com.simolution.sim.RunConfig;
import com.simolution.sim.TimeSeriesReport;

public final class Bench {

    private static final int TIMED_RUNS = 3;

    private record Row(String name, int ticks, long wallMillis, long heapMegabytes) {
    }

    private Bench() {
    }

    public static void main(final String[] args) {
        if (args.length != 0 && !(args.length == 2 && args[0].equals("--threads"))) {
            throw new IllegalArgumentException("usage: bench [--threads N]");
        }
        final String threads = args.length == 2 ? args[1] : "1";
        System.out.println("threads " + threads + ", " + Runtime.getRuntime().availableProcessors() + " cores");
        System.out.println("config | ticks | wall ms | ticks/s | heap MB");
        row("small", 64, 200, 2000, threads);
        row("dense200", 200, 4000, 3000, threads);
        row("big500", 500, 20000, 1000, threads);
    }

    private static void row(final String name, final int world, final int units, final int ticks,
            final String threads) {
        final RunConfig config = RunConfig.parse(new String[] {
                "--units", Integer.toString(units), "--ticks", Integer.toString(ticks),
                "--seed", "42", "--world", Integer.toString(world), "--threads", threads});
        runOnce(config);
        final Row[] runs = new Row[TIMED_RUNS];
        for (int i = 0; i < TIMED_RUNS; i++) {
            runs[i] = runOnce(config, name);
        }
        Arrays.sort(runs, (a, b) -> Long.compare(a.wallMillis(), b.wallMillis()));
        final Row median = runs[TIMED_RUNS / 2];
        System.out.printf("%s | %d | %d | %d | %d%n", median.name(), median.ticks(), median.wallMillis(),
                median.ticks() * 1000L / Math.max(1L, median.wallMillis()), median.heapMegabytes());
    }

    private static void runOnce(final RunConfig config) {
        runOnce(config, "warmup");
    }

    private static Row runOnce(final RunConfig config, final String name) {
        final int[][] genomes = GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());
        final CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        final int[] founderCells = Kernel.scatterFounders(genomes.length, config.worldWidth());
        final Kernel kernel = new Kernel(genomes, founderCells, config.engine(), config.threads());
        final DynamicsObserver observer = new DynamicsObserver(config.units(), connections, config.engine());
        final TimeSeriesReport timeSeries = new TimeSeriesReport(config.ticks());

        final long start = System.nanoTime();
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            final KernelSnapshot snapshot = kernel.snapshot();
            observer.observe(snapshot);
            timeSeries.sample(i, snapshot);
        }
        final long wallMillis = (System.nanoTime() - start) / 1_000_000;
        kernel.close();
        final Runtime runtime = Runtime.getRuntime();
        final long heapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return new Row(name, config.ticks(), wallMillis, heapMegabytes);
    }
}
