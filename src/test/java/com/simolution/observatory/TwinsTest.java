package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.sim.RunPaths;
import com.simolution.support.RunDirs;

class TwinsTest {

    @TempDir
    Path runs;

    private Path run(final String name, final String threads) throws IOException {
        return RunDirs.cliRun(RunDirs.config(runs.resolve(name), 300, "--checkpoint-every", "100", "--threads",
                threads));
    }

    @Test
    void sameSeedAtAnotherThreadCountMatchesEverywhere() throws IOException {
        // arrange
        final Path a = run("a", "1");
        final Path b = run("b", "3");

        // act
        final Twins.TwinResult result = Twins.compare(a, b);

        // assert
        assertEquals(new Twins.TwinResult(true, 4, null), result);
    }

    @Test
    void corruptedDigestNamesItsTick() throws IOException {
        // arrange
        final Path a = run("a", "1");
        final Path b = run("b", "2");
        final Path checkpoints = new RunPaths(b).checkpoints();
        Files.copy(checkpoints.resolve("100.ckpt"), checkpoints.resolve("200.ckpt"),
                StandardCopyOption.REPLACE_EXISTING);

        // act
        final Twins.TwinResult result = Twins.compare(a, b);

        // assert
        assertEquals(new Twins.TwinResult(false, 4, 200), result);
    }
}
