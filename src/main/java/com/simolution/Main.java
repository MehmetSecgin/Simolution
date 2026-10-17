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

public final class Main {

    private static final int MAX_TRACED_UNITS = 8;

    private Main() {}

    static void main(final String[] args) throws IOException {
        if (ReplayCommand.requested(args)) {
            ReplayCommand.run(args);
            return;
        }
        final RunConfig config = RunConfig.parse(args);
        final Founders founders = Founders.create(config);
        final StructuralStats structure = StructuralAnalyzer.analyze(founders.connections(), config.founderCount());
        final DynamicsObserver dynamics = new DynamicsObserver();
        final TimeSeriesSampler timeSeries = new TimeSeriesSampler(config.ticks());
        try (Kernel kernel = founders.newKernel(config.threads());
                RunArtifacts artifacts = RunArtifacts.open(config, founders, kernel)) {
            final TickObserver[] observers = observers(config, kernel, dynamics, timeSeries, artifacts);
            final RunResult result = SimulationRun.execute(kernel, config.ticks(), observers);
            artifacts.close();
            artifacts.announceObservabilityWritten();
            final String report = RunReport.render(config, structure, dynamics.summarize(result.finalDigest()));
            RunConsole.printReport(report, result.elapsedMillis());
            artifacts.writeReport(report, timeSeries.render());
        }
    }

    private static TickObserver[] observers(final RunConfig config, final Kernel kernel,
            final DynamicsObserver dynamics,
            final TimeSeriesSampler timeSeries, final RunArtifacts artifacts) {
        final List<TickObserver> observers = new ArrayList<>();
        observers.add(dynamics);
        if (config.digestEveryTick()) {
            observers.add(
                    snapshot -> System.out.printf("digest %d %016x%n", kernel.currentTick(), kernel.stateDigest()));
        }
        observers.add(timeSeries);
        observers.addAll(artifacts.observers());
        if (config.trace()) {
            final ConsoleTableLogger trace = new ConsoleTableLogger();
            final int unitsToTrace = Math.min(config.founderCount(), MAX_TRACED_UNITS);
            observers.add(snapshot -> trace.log(snapshot, unitsToTrace));
        }
        return observers.toArray(TickObserver[]::new);
    }
}
