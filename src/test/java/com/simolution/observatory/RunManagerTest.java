package com.simolution.observatory;

import static com.simolution.support.ObservatoryWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.simolution.sim.RunConfig;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;
import com.simolution.support.ObservatoryWorld;
import com.simolution.support.RunDirs;

class RunManagerTest {

    private static final int FOREVER = 1_000_000;
    private static final double IMMORTAL_ENERGY = 1.0e12;

    @TempDir
    Path temp;

    private RunStore store;
    private RunManager manager;

    @BeforeEach
    void start() throws Exception {
        store = new RunStore(Files.createDirectories(temp.resolve("runs")));
        manager = new RunManager(store, ObservatoryWorld.configs(temp.resolve("configs")), new EventStream(), 2);
    }

    @AfterEach
    void stop() {
        manager.close();
    }

    private static Map<String, JsonNode> immortal(final int seed) {
        return Map.of("seed", IntNode.valueOf(seed), "initialEnergy", DoubleNode.valueOf(IMMORTAL_ENERGY));
    }

    private RunState state(final String dir) throws Exception {
        return manager.status(dir).status();
    }

    private void awaitState(final String dir, final RunState expected) throws Exception {
        await(() -> state(dir) == expected);
    }

    @Test
    void runsQueueAndPauseHandsTheSlotOnWhileResumeGoesToTheFront() throws Exception {
        // arrange
        final String a = manager.create(ObservatoryWorld.request("a", FOREVER, immortal(7))).dir();
        final String b = manager.create(ObservatoryWorld.request("b", FOREVER, immortal(8))).dir();
        final String c = manager.create(ObservatoryWorld.request("c", FOREVER, immortal(9))).dir();
        awaitState(a, RunState.RUNNING);
        assertEquals(RunState.QUEUED, state(b));
        assertEquals(RunState.QUEUED, state(c));

        // act
        manager.pause(a);
        awaitState(b, RunState.RUNNING);
        manager.resume(a);
        manager.stop(b);

        // assert
        awaitState(a, RunState.RUNNING);
        assertEquals(RunState.STOPPED, state(b));
        assertEquals(RunState.QUEUED, state(c));
        assertEquals(RunState.STOPPED, RunStatus.read(new RunPaths(store.path(b)).status()).status());
    }

    @Test
    void pausedRunShowsTheFrameItPausedOn() throws Exception {
        // arrange
        final String dir = manager.create(ObservatoryWorld.request("p", FOREVER, immortal(7))).dir();
        awaitState(dir, RunState.RUNNING);

        // act
        manager.pause(dir);
        awaitState(dir, RunState.PAUSED);

        // assert
        final byte[] frame = manager.latest(dir).read();
        assertEquals(manager.status(dir).ticksDone(),
                ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).getInt(0));
    }

    @Test
    void stopDuringThePauseTransitionEndsTheRunStopped() throws Exception {
        // arrange
        final CountDownLatch pausing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<Object> states = new CopyOnWriteArrayList<>();
        final EventStream events = new EventStream() {
            @Override
            public void send(final String event, final Object payload) {
                if (!event.equals("run")) {
                    return;
                }
                final Object state = ((Map<?, ?>) payload).get("status");
                if (state.equals("paused") && pausing.getCount() > 0) {
                    pausing.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                states.add(state);
            }
        };
        manager.close();
        manager = new RunManager(store, temp.resolve("configs"), events, 2);
        final String dir = manager.create(ObservatoryWorld.request("p", FOREVER, immortal(7))).dir();
        awaitState(dir, RunState.RUNNING);
        manager.pause(dir);
        pausing.await();

        // act
        final Thread stopper = Thread.ofPlatform().start(() -> {
            try {
                manager.stop(dir);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        stopper.join(500);
        release.countDown();
        stopper.join();

        // assert
        assertEquals("stopped", states.getLast());
        assertEquals(RunState.STOPPED, state(dir));
        assertEquals(RunState.STOPPED, RunStatus.read(new RunPaths(store.path(dir)).status()).status());
    }

    @Test
    void extinctionEndsARunEarly() throws Exception {
        // arrange
        final RunRequest starving = new RunRequest("dead", ObservatoryWorld.STARVING, Map.of(),
                ObservatoryWorld.FOUNDERS, 0, FOREVER, 100);

        // act
        final String dir = manager.create(starving).dir();

        // assert
        awaitState(dir, RunState.EXTINCT);
        assertTrue(manager.status(dir).ticksDone() < 100);
    }

    @Test
    void unknownOverrideIsRefused() {
        // act
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> manager.create(ObservatoryWorld.request("x", 10, Map.of("bogus", IntNode.valueOf(1)))));

        // assert
        assertEquals("unknown setting: bogus", refused.getMessage());
    }

    @Test
    void nullOverrideIsRefused() {
        // arrange
        final Map<String, JsonNode> overrides = Map.of("initialEnergy", NullNode.getInstance());

        // act
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> manager.create(ObservatoryWorld.request("x", 10, overrides)));

        // assert
        assertEquals("setting initialEnergy needs a value, not null", refused.getMessage());
    }

    @Test
    void observatoryRunEqualsTheCliRun() throws Exception {
        // arrange
        final RunConfig cli = RunDirs.config(temp.resolve("cli"), 200, "--config",
                temp.resolve("configs").resolve(ObservatoryWorld.SMALL).toString(), "--checkpoint-every", "100");
        final RunPaths cliRun = new RunPaths(RunDirs.cliRun(cli));

        // act
        final String dir = manager.create(ObservatoryWorld.request("obs", 200)).dir();
        awaitState(dir, RunState.FINISHED);

        // assert
        final RunPaths observed = new RunPaths(store.path(dir));
        assertEquals(RunStatus.read(cliRun.status()).finalDigest(), RunStatus.read(observed.status()).finalDigest());
        assertEquals(Files.readString(cliRun.metrics()), Files.readString(observed.metrics()));
        assertFalse(Files.readString(observed.report()).isEmpty());
    }

    @Test
    void sameRequestTwiceOpensTheExistingRun() throws Exception {
        // act
        final RunManager.Launch first = manager.create(ObservatoryWorld.request("one", 50));
        final RunManager.Launch second = manager.create(ObservatoryWorld.request("other name", 50));

        // assert
        assertFalse(first.existing());
        assertTrue(second.existing());
        assertEquals(first.dir(), second.dir());
        assertEquals(1, store.list().size());
    }
}
