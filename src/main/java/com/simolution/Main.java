package com.simolution;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.logging.ConsoleTableLogger;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.DynamicsObserver;
import com.simolution.sim.Founders;
import com.simolution.sim.ReplayCommand;
import com.simolution.sim.RunArtifacts;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunConsole;
import com.simolution.sim.RunReport;
import com.simolution.sim.RunResult;
import com.simolution.sim.SimulationRun;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TickObserver;
import com.simolution.sim.TimeSeriesSampler;

public class Main {

    private static final int MAX_TRACED_UNITS = 8;

    static void main(String[] args) throws IOException {
        if (ReplayCommand.requested(args)) {
            ReplayCommand.run(args);
            return;
        }
        RunConfig config = RunConfig.parse(args);
        Founders founders = Founders.create(config);
        StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.founderCount());
        DynamicsObserver dynamics = new DynamicsObserver();
        TimeSeriesSampler timeSeries = new TimeSeriesSampler(config.ticks());
        try (Kernel kernel = founders.newKernel(config.threads());
             RunArtifacts artifacts = RunArtifacts.open(config, founders, kernel)) {
            TickObserver[] observers = observers(config, kernel, dynamics, timeSeries, artifacts);
            RunResult result = SimulationRun.execute(kernel, config.ticks(), observers);
            artifacts.close();
            artifacts.announceObservabilityWritten();
            String report = RunReport.render(config, structure, dynamics.summarize(result.finalDigest()));
            RunConsole.printReport(report, result.elapsedMillis());
            artifacts.writeReport(report, timeSeries.render());
        }
    }

    private static TickObserver[] observers(RunConfig config, Kernel kernel, DynamicsObserver dynamics,
            TimeSeriesSampler timeSeries, RunArtifacts artifacts) {
        List<TickObserver> observers = new ArrayList<>();
        observers.add(dynamics);
        if (config.digestEveryTick()) {
            observers.add(snapshot -> System.out.printf("digest %d %016x%n", kernel.currentTick(), kernel.stateDigest()));
        }
        observers.add(timeSeries);
        observers.addAll(artifacts.observers());
        if (config.trace()) {
            ConsoleTableLogger trace = new ConsoleTableLogger();
            int unitsToTrace = Math.min(config.founderCount(), MAX_TRACED_UNITS);
            observers.add(snapshot -> trace.log(snapshot, unitsToTrace));
        }
        return observers.toArray(TickObserver[]::new);
    }
}
