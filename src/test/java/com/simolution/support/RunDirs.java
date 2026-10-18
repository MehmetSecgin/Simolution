package com.simolution.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.Founders;
import com.simolution.sim.RunArtifacts;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunPaths;
import com.simolution.sim.SimulationRun;
import com.simolution.sim.TickObserver;
import com.simolution.sim.run.Branching;
import com.simolution.sim.run.RunOrigin;
import com.simolution.sim.run.RunState;

public final class RunDirs {

    private RunDirs() {}

    public static RunConfig config(final Path dir, final int ticks, final String... extra) {
        final List<String> args = new ArrayList<>(List.of("--units", "60", "--genes", "16", "--seed", "7", "--world",
                "32", "--ticks", String.valueOf(ticks), "--out", dir.toString()));
        args.addAll(List.of(extra));
        return RunConfig.parse(args.toArray(String[]::new));
    }

    public static Path cliRun(final RunConfig config) throws IOException {
        try (Kernel kernel = Founders.create(config).newKernel(config.threads());
                RunArtifacts artifacts = RunArtifacts.open(config, kernel,
                        RunOrigin.named(config.runDir().getFileName().toString()))) {
            SimulationRun.execute(kernel, config.ticks(), artifacts.observers().toArray(TickObserver[]::new));
            artifacts.finish(RunState.endOf(kernel.snapshot()), kernel.stateDigest(), "report\n");
        }
        return config.runDir();
    }

    public static Path branch(final Path parent, final int tick, final RunConfig config) throws IOException {
        final EngineConfig engine = ConfigJson.read(Files.readString(new RunPaths(parent).config()));
        try (Kernel kernel = Branching.start(parent, tick, engine, 1);
                RunArtifacts artifacts = RunArtifacts.open(config, kernel,
                        new RunOrigin("branch", parent.getFileName().toString(), tick, null))) {
            SimulationRun.execute(kernel, config.ticks() - tick,
                    artifacts.observers().toArray(TickObserver[]::new));
            artifacts.finish(RunState.endOf(kernel.snapshot()), kernel.stateDigest(), "report\n");
        }
        return config.runDir();
    }
}
