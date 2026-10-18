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
import com.simolution.sim.RunReport;
import com.simolution.sim.SimulationRun;
import com.simolution.sim.StructuralAnalyzer;
import com.simolution.sim.StructuralStats;
import com.simolution.sim.TickObserver;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;

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
        final RunOrigin origin =
                RunOrigin.named(config.runDir() == null ? "" : config.runDir().getFileName().toString());
        try (Kernel kernel = founders.newKernel(config.threads());
                RunArtifacts artifacts = RunArtifacts.open(config, kernel, origin)) {
            final TickObserver[] observers = observers(config, kernel, dynamics, artifacts);
            final long elapsedMillis = SimulationRun.execute(kernel, config.ticks(), observers);
            final long finalDigest = kernel.stateDigest();
            final String report = RunReport.render(config, origin, structure, dynamics.summarize(finalDigest));
            artifacts.finish(RunState.endOf(kernel.snapshot()), finalDigest, report);
            System.out.println();
            System.out.print(report);
            System.out.println();
            System.out.println("(wall clock: " + elapsedMillis + " ms — console only, never part of the report)");
            if (config.runDir() != null) {
                System.out.println("run written to " + config.runDir());
            }
        }
    }

    private static TickObserver[] observers(final RunConfig config, final Kernel kernel,
            final DynamicsObserver dynamics, final RunArtifacts artifacts) {
        final List<TickObserver> observers = new ArrayList<>();
        observers.add(dynamics);
        if (config.digestEveryTick()) {
            observers.add(
                    snapshot -> System.out.printf("digest %d %016x%n", kernel.currentTick(), kernel.stateDigest()));
        }
        observers.addAll(artifacts.observers());
        if (config.trace()) {
            final ConsoleTableLogger trace = new ConsoleTableLogger();
            final int unitsToTrace = Math.min(config.founderCount(), MAX_TRACED_UNITS);
            observers.add(snapshot -> trace.log(snapshot, unitsToTrace));
        }
        return observers.toArray(TickObserver[]::new);
    }
}
