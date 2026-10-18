package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;
import com.simolution.support.RunDirs;

class ComparisonTest {

    @TempDir
    Path runs;

    @Test
    void aTwinStillRunningHasNoVerdictYet() throws IOException {
        // arrange
        RunDirs.cliRun(RunDirs.config(runs.resolve("a"), 100));
        final RunPaths twin = new RunPaths(RunDirs.cliRun(RunDirs.config(runs.resolve("b"), 100)));
        final RunManifest manifest = RunManifest.read(twin.manifest());
        new RunManifest(manifest.runId(), manifest.engine(), manifest.name(), null, null, "a",
                manifest.targetTicks(), manifest.checkpointEvery(), manifest.founders(), manifest.genesPerFounder())
                .write(twin.manifest());
        new RunStatus(RunState.RUNNING, 40, null, null).write(twin.status());

        // act
        final Comparison.Divergence divergence =
                Comparison.compare(new RunStore(runs), List.of("a", "b")).divergence();

        // assert
        assertEquals("twin", divergence.kind());
        assertNull(divergence.identical());
    }
}
