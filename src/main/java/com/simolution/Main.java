package com.simolution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.simolution.kernel.genome.GeneBuilder;
import com.simolution.kernel.genome.GenomeCompiler;
import com.simolution.kernel.layout.CompiledConnection;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.logging.ConsoleTableLogger;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.runtime.KernelSnapshot;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.GenomeFactory;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunReport;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;

public class Main {

    private static final int MAX_TRACED_UNITS = 8;

    static void main(String[] args) throws IOException {
        RunConfig config = RunConfig.parse(args);

        int[][] genomes = config.demo()
                ? new int[][] {demoGenome()}
                : GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());

        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());

        Kernel kernel = new Kernel(config.units(), connections);
        DynamicsObserver observer = new DynamicsObserver(config.units(), connections);
        ConsoleTableLogger trace = config.trace() ? new ConsoleTableLogger() : null;
        int unitsToTrace = Math.min(config.units(), MAX_TRACED_UNITS);

        long startNanos = System.nanoTime();
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            observer.observe(snapshot);
            if (trace != null) {
                trace.log(snapshot, unitsToTrace);
            }
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        String report = RunReport.render(config, structure, observer.summarize());
        System.out.println();
        System.out.print(report);
        System.out.println();
        System.out.println("(wall clock: " + elapsedMillis + " ms — console only, never part of the report)");

        if (config.outPath() != null) {
            Path out = Path.of(config.outPath());
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, report);
            System.out.println("report written to " + out);
        }
    }

    private static int[] demoGenome() {
        short wConstToAdd = 2048;
        short wAddToDelay = 512;
        short wDelayToAdd = 512;
        short wAddToAction = 512;

        return new int[] {
                GeneBuilder.fromSensor(NodeLayout.Sensor.CONST)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wConstToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toInternal(NodeLayout.Internal.DELAY)
                           .weightRaw(wAddToDelay)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.DELAY)
                           .toInternal(NodeLayout.Internal.ADD)
                           .weightRaw(wDelayToAdd)
                           .build(),
                GeneBuilder.fromInternal(NodeLayout.Internal.ADD)
                           .toAction(NodeLayout.Action.Y)
                           .weightRaw(wAddToAction)
                           .build()
        };
    }
}
