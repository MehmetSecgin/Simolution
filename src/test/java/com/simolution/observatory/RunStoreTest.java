package com.simolution.observatory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;

class RunStoreTest {

    @TempDir
    Path root;

    private RunPaths run(final String dir, final String parent, final RunState state, final int ticksDone)
            throws IOException {
        final RunPaths paths = new RunPaths(root.resolve(dir));
        Files.createDirectories(paths.checkpoints());
        new RunManifest("id-" + dir, Kernel.ENGINE_VERSION, dir, parent, parent == null ? null : 10, null, 1000,
                500, 4, 8).write(paths.manifest());
        new RunStatus(state, ticksDone, null, null).write(paths.status());
        return paths;
    }

    @Test
    void brokenDirectoryIsListedWithItsReasonAndNeverBreaksTheList() throws IOException {
        // arrange
        run("good", null, RunState.FINISHED, 1000);
        Files.createDirectories(root.resolve("broken"));
        Files.writeString(root.resolve("broken/run.json"), "{");
        run("old", null, RunState.FINISHED, 1000);
        new RunManifest("x", "v5", "old", null, null, null, 1000, 500, 4, 8).write(root.resolve("old/run.json"));
        Files.createDirectories(root.resolve("not-a-run"));

        // act
        final List<RunEntry> entries = new RunStore(root).list();

        // assert
        assertEquals(List.of("broken", "good", "old"), entries.stream().map(RunEntry::dir).toList());
        assertTrue(entries.get(0).unreadable().startsWith("run.json: "), entries.get(0).unreadable());
        assertNull(entries.get(1).unreadable());
        assertEquals("engine v5, this build runs " + Kernel.ENGINE_VERSION, entries.get(2).unreadable());
    }

    @Test
    void runningRunBecomesStoppedAtItsLastCheckpoint() throws IOException {
        // arrange
        final RunPaths paths = run("live", null, RunState.RUNNING, 730);
        Files.write(paths.checkpoints().resolve("0.ckpt"), new byte[0]);
        Files.write(paths.checkpoints().resolve("500.ckpt"), new byte[0]);
        run("held", null, RunState.PAUSED, 20);
        final RunStore store = new RunStore(root);

        // act
        store.markInterrupted();

        // assert
        assertEquals(new RunStatus(RunState.STOPPED, 500, null, null), store.get("live").status());
        assertEquals(RunState.STOPPED, store.get("held").status().status());
    }

    @Test
    void deleteRefusesARunWithBranchesOrARunningRun() throws IOException {
        // arrange
        run("parent", null, RunState.FINISHED, 1000);
        run("child", "parent", RunState.FINISHED, 1000);
        run("busy", null, RunState.RUNNING, 10);
        final RunStore store = new RunStore(root);

        // act
        final String branches = assertThrows(IllegalStateException.class, () -> store.delete("parent")).getMessage();
        final String running = assertThrows(IllegalStateException.class, () -> store.delete("busy")).getMessage();
        store.delete("child");

        // assert
        assertEquals("parent has branches; delete them first", branches);
        assertEquals("busy is running; stop it first", running);
        assertTrue(Files.notExists(root.resolve("child")));
        store.delete("parent");
        assertTrue(Files.notExists(root.resolve("parent")));
    }

    @Test
    void newDirectoriesAreSlugsWithTheLowestFreeCounter() throws IOException {
        // arrange
        final RunStore store = new RunStore(root);

        // act
        final Path first = store.newDir("Baseline, Seed 42!");
        final Path second = store.newDir("Baseline, Seed 42!");
        final Path capped = store.newDir("a".repeat(60));

        // assert
        assertEquals("baseline-seed-42-1", first.getFileName().toString());
        assertEquals("baseline-seed-42-2", second.getFileName().toString());
        assertEquals("a".repeat(40) + "-1", capped.getFileName().toString());
    }

    @Test
    void findByRunIdPrefersTheOriginalOverATwin() throws IOException {
        // arrange
        run("orig", null, RunState.FINISHED, 1000);
        final RunPaths twin = run("twin", null, RunState.FINISHED, 1000);
        new RunManifest("id-orig", Kernel.ENGINE_VERSION, "twin", null, null, "orig", 1000, 500, 4, 8)
                .write(twin.manifest());

        // act
        final RunEntry found = new RunStore(root).findByRunId("id-orig");

        // assert
        assertNotNull(found);
        assertEquals("orig", found.dir());
    }

    @Test
    void unknownOrUnsafeNamesAreNotFound() {
        // arrange
        final RunStore store = new RunStore(root);

        // assert
        assertThrows(NoSuchElementException.class, () -> store.get("missing"));
        assertThrows(NoSuchElementException.class, () -> store.get(".."));
    }
}
