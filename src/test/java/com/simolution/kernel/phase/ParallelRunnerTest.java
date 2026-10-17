package com.simolution.kernel.phase;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
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

        // act + assert
        assertThrows(IllegalStateException.class, () -> runner.runTick(List.of(new Recording(-1))));
    }
}
