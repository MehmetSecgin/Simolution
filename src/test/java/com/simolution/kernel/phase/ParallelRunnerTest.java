package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.support.Worlds;

@Timeout(60)
class ParallelRunnerTest {

    private static final int EXTENT = 5 * Chunks.SIZE + 7;

    private static final class Recording implements Phase {
        final AtomicIntegerArray runs = new AtomicIntegerArray(Chunks.count(EXTENT));
        final int[] from = new int[Chunks.count(EXTENT)];
        final int[] to = new int[Chunks.count(EXTENT)];
        final int failOn;

        Recording(final int failOn) {
            this.failOn = failOn;
        }

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public Shape shape() {
            return Shape.PER_UNIT;
        }

        @Override
        public int extent() {
            return EXTENT;
        }

        @Override
        public void run(final int start, final int end, final int chunk) {
            if (chunk == failOn) {
                throw new ArithmeticException("chunk " + chunk);
            }
            runs.incrementAndGet(chunk);
            from[chunk] = start;
            to[chunk] = end;
        }
    }

    @Test
    void everyChunkRunsOnceWithFixedBounds() {
        for (final int threads : new int[] {1, 3, 8}) {
            // arrange
            final Recording phase = new Recording(-1);

            // act
            try (ParallelRunner runner = new ParallelRunner(threads)) {
                runner.runTick(List.of(phase));
            }

            // assert
            for (int c = 0; c < Chunks.count(EXTENT); c++) {
                assertEquals(1, phase.runs.get(c), "chunk " + c + " with " + threads + " threads");
            }
            assertArrayEquals(new int[] {0, 4096, 8192, 12288, 16384, 20480}, phase.from);
            assertArrayEquals(new int[] {4096, 8192, 12288, 16384, 20480, EXTENT}, phase.to);
        }
    }

    @Test
    void workerFailureReachesTheCallerAndTheRunnerStaysUsable() {
        try (ParallelRunner runner = new ParallelRunner(4)) {
            // act
            final IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> runner.runTick(List.of(new Recording(3))));
            final Recording next = new Recording(-1);
            runner.runTick(List.of(next));

            // assert
            assertEquals(ArithmeticException.class, failure.getCause().getClass());
            assertEquals(1, next.runs.get(5));
        }
    }

    @Test
    void closedRunnerRefusesSplitPhases() {
        // arrange
        final ParallelRunner runner = new ParallelRunner(2);
        runner.close();

        // assert
        assertThrows(IllegalStateException.class, () -> runner.runTick(List.of(new Recording(-1))));
    }

    @Test
    @DisplayName("once the JIT settles, a full 1000-tick window allocates nothing on the caller and the workers")
    void steadyStateTickAllocatesNothingWithFourThreads() {
        // arrange
        final com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final int world = 128;
        final int founders = 1200;
        try (Kernel kernel = Worlds.randomKernel(7L, founders, 32, EngineConfig.defaults(7L, world, 64), 4)) {
            kernel.step(500);
            final long[] ids = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t == Thread.currentThread() || t.getName().startsWith("simolution-worker-"))
                    .mapToLong(Thread::threadId)
                    .toArray();
            assertEquals(4, ids.length);

            // act
            final long[] windows = new long[10];
            int measured = 0;
            while (measured < windows.length && (measured == 0 || windows[measured - 1] != 0L)) {
                final long before = allocated(bean, ids);
                kernel.step(1000);
                windows[measured++] = allocated(bean, ids) - before;
            }

            // assert
            assertEquals(0L, windows[measured - 1],
                    "no 1000-tick window without allocation on caller + workers: "
                            + java.util.Arrays.toString(windows));
        }
    }

    private static long allocated(final com.sun.management.ThreadMXBean bean, final long[] ids) {
        long total = 0L;
        for (final long id : ids) {
            total += bean.getThreadAllocatedBytes(id);
        }
        return total;
    }
}
