package com.simolution.bench;

import java.io.IOException;
import java.util.Arrays;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.Founders;
import com.simolution.sim.MapSampling;
import com.simolution.sim.Observation;
import com.simolution.sim.RunConfig;
import com.simolution.sim.SimulationRun;
import com.simolution.sim.TickObserver;
import com.simolution.sim.TimeSeriesSampler;

public final class Bench {

    private static final int TIMED_RUNS = 3;
    private static final long SEED = 42L;
    private static final int GENES_PER_FOUNDER = 32;
    private static final int MAX_GENES = 64;
    private static final int DEFAULT_THREADS = 1;

    private record Row(String name, int ticks, long wallMillis, long heapMegabytes) {
    }

    private Bench() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 0 && !(args.length == 2 && args[0].equals("--threads"))) {
            throw new IllegalArgumentException("usage: bench [--threads N]");
        }
        final int threads = args.length == 2 ? Integer.parseInt(args[1]) : DEFAULT_THREADS;
        System.out.println("threads " + threads + ", " + RunConfig.allCores() + " cores");
        System.out.println("config | ticks | wall ms | ticks/s | heap MB");
        row("small", config(64, 200, 2000, threads));
        row("dense200", config(200, 4000, 3000, threads));
        row("big500", config(500, 20000, 1000, threads));
    }

    static RunConfig config(final int world, final int founders, final int ticks, final int threads) {
        return new RunConfig(founders, ticks, GENES_PER_FOUNDER, false, null,
                MapSampling.OFF, Observation.OFF, EngineConfig.defaults(SEED, world, MAX_GENES), threads, false);
    }

    private static void row(final String name, final RunConfig config) throws IOException {
        runOnce(config, "warmup");
        final Row[] runs = new Row[TIMED_RUNS];
        for (int i = 0; i < TIMED_RUNS; i++) {
            runs[i] = runOnce(config, name);
        }
        Arrays.sort(runs, (a, b) -> Long.compare(a.wallMillis(), b.wallMillis()));
        final Row median = runs[TIMED_RUNS / 2];
        System.out.printf("%s | %d | %d | %d | %d%n", median.name(), median.ticks(), median.wallMillis(),
                median.ticks() * 1000L / Math.max(1L, median.wallMillis()), median.heapMegabytes());
    }

    private static Row runOnce(final RunConfig config, final String name) throws IOException {
        final Kernel kernel = Founders.create(config).newKernel(config.threads());
        final TickObserver[] observers = {new DynamicsObserver(), new TimeSeriesSampler(config.ticks())};
        final long wallMillis = SimulationRun.execute(kernel, config.ticks(), observers).elapsedMillis();
        kernel.close();
        final Runtime runtime = Runtime.getRuntime();
        final long heapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return new Row(name, config.ticks(), wallMillis, heapMegabytes);
    }
}
