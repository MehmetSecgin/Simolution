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
import com.simolution.sim.DynamicsSummary;
import com.simolution.sim.GenomeFactory;
import com.simolution.sim.LineageReport;
import com.simolution.sim.PopulationReport;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunReport;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TimeSeriesReport;
import com.simolution.sim.UnitCsvReport;
import com.simolution.sim.WiringReport;

public class Main {

    private static final int MAX_TRACED_UNITS = 8;

    static void main(String[] args) throws IOException {
        RunConfig config = RunConfig.parse(args);

        int[][] genomes = config.demo()
                ? new int[][] {demoGenome()}
                : GenomeFactory.random(config.seed(), config.units(), config.genesPerUnit());

        CompiledConnection[] connections = GenomeCompiler.compileAll(genomes);
        StructuralStats structure = StructuralAnalyzer.analyze(connections, config.units());

        int maxGenes = config.maxGenes();
        for (int[] genome : genomes) {
            maxGenes = Math.max(maxGenes, genome.length);
        }
        int maxUnits = Math.max(config.maxUnits(), genomes.length);
        Kernel kernel = new Kernel(genomes, maxUnits, maxGenes);
        DynamicsObserver observer = new DynamicsObserver(config.units(), maxUnits, connections);
        ConsoleTableLogger trace = config.trace() ? new ConsoleTableLogger() : null;
        int unitsToTrace = Math.min(config.units(), MAX_TRACED_UNITS);

        TimeSeriesReport timeSeries = new TimeSeriesReport(config.ticks());

        long startNanos = System.nanoTime();
        for (int i = 0; i < config.ticks(); i++) {
            kernel.tick();
            KernelSnapshot snapshot = kernel.snapshot();
            observer.observe(snapshot);
            timeSeries.sample(i, snapshot);
            if (trace != null) {
                trace.log(snapshot, unitsToTrace);
            }
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        DynamicsSummary summary = observer.summarize();
        String report = RunReport.render(config, structure, summary);
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

            Path csv = sibling(out, ".units.csv");
            Files.writeString(csv, UnitCsvReport.render(config.units(), structure, summary));
            System.out.println("per-unit detail written to " + csv);

            Path wiring = sibling(out, ".wiring.csv");
            Files.writeString(wiring, WiringReport.render(connections));
            System.out.println("per-unit wiring written to " + wiring);

            Path lineage = sibling(out, ".lineage.csv");
            Files.writeString(lineage, LineageReport.render(observer.lineageSummary(), config.ticks()));
            System.out.println("per-lineage detail written to " + lineage);

            Path series = sibling(out, ".timeseries.csv");
            Files.writeString(series, timeSeries.render());
            System.out.println("time series written to " + series);

            Path population = sibling(out, ".population.csv");
            Files.writeString(population, PopulationReport.render(kernel.snapshot()));
            System.out.println("final population written to " + population);

            Path popWiring = sibling(out, ".popwiring.csv");
            Files.writeString(popWiring, WiringReport.render(kernel.liveConnections()));
            System.out.println("evolved wiring written to " + popWiring);
        }
    }

    private static Path sibling(final Path reportPath, final String suffix) {
        String name = reportPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        Path parent = reportPath.getParent();
        String siblingName = base + suffix;
        return parent == null ? Path.of(siblingName) : parent.resolve(siblingName);
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
