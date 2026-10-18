package com.simolution.sim.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.Founders;
import com.simolution.sim.RunArtifacts;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunPaths;
import com.simolution.sim.SimulationRun;
import com.simolution.sim.TickObserver;

class BranchingTest {

    private static final int BRANCH_TICK = 250;
    private static final int BUSY_TICK = 20;

    @TempDir
    Path runs;

    private static RunConfig config(final Path runDir, final int ticks) {
        return RunConfig.parse(new String[] {"--units", "60", "--ticks", String.valueOf(ticks), "--seed", "7",
                "--world", "32", "--genes", "16", "--checkpoint-every", "100", "--out", runDir.toString()});
    }

    private Path parentRun() throws IOException {
        final Path dir = runs.resolve("parent");
        final RunConfig config = config(dir, 300);
        try (Kernel kernel = Founders.create(config).newKernel(1);
                RunArtifacts artifacts = RunArtifacts.open(config, kernel, RunOrigin.named("parent"))) {
            SimulationRun.execute(kernel, config.ticks(), artifacts.observers().toArray(TickObserver[]::new));
            artifacts.finish(RunState.endOf(kernel.snapshot()), kernel.stateDigest(), "report\n");
        }
        return dir;
    }

    private RunPaths branch(final Path parent, final String name, final int ticksAfter) throws IOException {
        return branch(parent, name, BRANCH_TICK, ticksAfter);
    }

    private RunPaths branch(final Path parent, final String name, final int tick, final int ticksAfter)
            throws IOException {
        final RunPaths paths = new RunPaths(runs.resolve(name));
        final RunConfig config = config(paths.dir(), tick + ticksAfter);
        final EngineConfig engine = ConfigJson.read(Files.readString(new RunPaths(parent).config()));
        try (Kernel kernel = Branching.start(parent, tick, engine, 1);
                RunArtifacts artifacts = RunArtifacts.open(config, kernel, new RunOrigin(name, "parent", tick, null))) {
            SimulationRun.execute(kernel, ticksAfter, artifacts.observers().toArray(TickObserver[]::new));
            artifacts.finish(RunState.endOf(kernel.snapshot()), kernel.stateDigest(), "report\n");
        }
        return paths;
    }

    private static String sha256Hex(final byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static List<String> eventLines(final Path events) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(events))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static List<String> eventsAfter(final Path events, final int tick) throws IOException {
        return eventLines(events).stream()
                .filter(line -> Integer.parseInt(line.substring(5, line.indexOf(','))) > tick)
                .map(line -> line.replaceAll(",\"(lifespan|peakMembers|maxGeneration)\":-?[0-9]+", ""))
                .toList();
    }

    @Test
    void branchStartsAtItsTickAndIsNamedByThatCheckpoint() throws Exception {
        // arrange
        final Path parent = parentRun();

        // act
        final RunPaths branch = branch(parent, "b", 0);

        // assert
        final Path first = branch.checkpoints().resolve(BRANCH_TICK + ".ckpt");
        final RunManifest manifest = RunManifest.read(branch.manifest());
        assertEquals(sha256Hex(Files.readAllBytes(first)), manifest.runId());
        assertEquals("parent", manifest.parent());
        assertEquals(BRANCH_TICK, manifest.branchTick());
        assertEquals(60, manifest.founders());
    }

    @Test
    void sameBranchTwiceHasTheSameRunId() throws Exception {
        // arrange
        final Path parent = parentRun();

        // act
        final RunPaths first = branch(parent, "b1", 0);
        final RunPaths second = branch(parent, "b2", 0);

        // assert
        assertEquals(RunManifest.read(first.manifest()).runId(), RunManifest.read(second.manifest()).runId());
    }

    @Test
    void branchLogsOnlyTheEventsTheParentSawAfterTheBranchTick() throws Exception {
        // arrange
        final Path parent = parentRun();

        // act
        final RunPaths branch = branch(parent, "b", BUSY_TICK, 30);

        // assert
        final List<String> branchEvents = eventsAfter(branch.events(), BUSY_TICK);
        assertFalse(branchEvents.isEmpty());
        assertEquals(eventsAfter(new RunPaths(parent).events(), BUSY_TICK).subList(0, branchEvents.size()),
                branchEvents);
        assertEquals(branchEvents.size(), eventLines(branch.events()).size());
    }

    @Test
    void branchMetricsContinueThePopulationCountOfTheParent() throws Exception {
        // arrange
        final Path parent = parentRun();

        // act
        final RunPaths branch = branch(parent, "b", 1);

        // assert
        final List<String> parentRows = Files.readAllLines(new RunPaths(parent).metrics());
        final List<String> branchRows = Files.readAllLines(branch.metrics());
        assertEquals(parentRows.get(0), branchRows.get(0));
        assertEquals(parentRows.get(BRANCH_TICK + 1), branchRows.get(1));
    }
}
