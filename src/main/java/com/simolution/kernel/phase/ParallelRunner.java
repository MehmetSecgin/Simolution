package com.simolution.kernel.phase;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the phase list on a fixed pool of persistent threads (engine v6 design §5.8).
 * <p>
 * A splittable phase ({@link Shape#PER_UNIT}, {@link Shape#PER_CELL}, {@link Shape#CLAIM})
 * is cut into {@link Chunks} and each chunk is run exactly once, by whichever thread claims
 * it from an atomic counter. Which thread runs a chunk never matters: a phase writes only its
 * own indices and its own {@code chunk} partial, and partials are merged in ascending chunk
 * index afterwards. {@link Shape#SERIAL} phases, phases of a single chunk and every phase when
 * {@code threads == 1} run on the caller, chunk by chunk in ascending order; with one thread
 * no worker is ever started.
 * <p>
 * The caller takes part as one of the {@code threads}. One {@link CyclicBarrier} over all
 * threads brackets each split phase: the caller publishes the phase and crosses it to release
 * the workers, everyone claims chunks, and everyone crosses it again to join before the
 * caller runs {@link Phase#finish()}. Memory visibility rests on the barrier's contract:
 * actions before {@code await()} in one thread happen-before actions after the matching
 * {@code await()} returns in every other thread. So the phase reference and chunk count
 * written by the caller before the release are seen by the workers, and every array write a
 * worker makes in a chunk is seen by the caller (and by all threads in later phases) after
 * the join. Plain fields suffice for everything but the chunk counter.
 * <p>
 * Nothing is allocated per tick. A {@link Throwable} thrown by a phase on any thread is
 * recorded, stops further chunk claims, and is rethrown on the caller after the join, so a
 * failure never leaves a thread stuck at the barrier. Interrupting the caller while it waits
 * breaks the barrier (interrupting any waiter breaks a {@link CyclicBarrier}), the workers
 * exit, and every later split phase throws. Workers are daemon threads, so a runner that is
 * never closed cannot keep the JVM alive; {@link #close()} stops them and waits for them.
 */
public final class ParallelRunner implements PhaseRunner, AutoCloseable {

    private final int threads;
    private final Thread[] workers;
    private final CyclicBarrier barrier;
    private final AtomicInteger nextChunk = new AtomicInteger();

    private Phase current;
    private int extent;
    private int chunkCount;
    private boolean closing;
    private Throwable failure;

    public ParallelRunner(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1, got " + threads);
        }
        this.threads = threads;
        this.workers = new Thread[threads - 1];
        this.barrier = threads > 1 ? new CyclicBarrier(threads) : null;
        for (int i = 0; i < workers.length; i++) {
            workers[i] = Thread.ofPlatform().daemon().name("simolution-worker-" + (i + 1)).start(this::workerLoop);
        }
    }

    @Override
    public void runTick(final List<Phase> phases) {
        for (int i = 0; i < phases.size(); i++) {
            final Phase phase = phases.get(i);
            phase.prepare();
            final int n = phase.extent();
            if (phase.shape() == Shape.SERIAL) {
                phase.run(0, n, 0);
            } else if (threads == 1 || Chunks.count(n) <= 1) {
                runInline(phase, n);
            } else {
                runSplit(phase, n);
            }
            phase.finish();
        }
    }

    private static void runInline(final Phase phase, final int n) {
        final int count = Chunks.count(n);
        for (int c = 0; c < count; c++) {
            final int from = c * Chunks.SIZE;
            phase.run(from, Math.min(n, from + Chunks.SIZE), c);
        }
    }

    private void runSplit(final Phase phase, final int n) {
        if (closing) {
            throw new IllegalStateException("runner is closed");
        }
        current = phase;
        extent = n;
        chunkCount = Chunks.count(n);
        nextChunk.set(0);
        failure = null;
        await();
        claimChunks();
        await();
        current = null;
        final Throwable thrown = failure;
        if (thrown != null) {
            throw new IllegalStateException("phase " + phase.name() + " failed", thrown);
        }
    }

    private void claimChunks() {
        final Phase phase = current;
        final int n = extent;
        final int count = chunkCount;
        try {
            int c;
            while ((c = nextChunk.getAndIncrement()) < count) {
                final int from = c * Chunks.SIZE;
                phase.run(from, Math.min(n, from + Chunks.SIZE), c);
            }
        } catch (final Throwable t) {
            failure = t;
            nextChunk.set(count);
        }
    }

    private void workerLoop() {
        try {
            while (true) {
                barrier.await();
                if (closing) {
                    return;
                }
                claimChunks();
                barrier.await();
            }
        } catch (final InterruptedException | BrokenBarrierException e) {
            return;
        }
    }

    private void await() {
        try {
            barrier.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for phase workers", e);
        } catch (final BrokenBarrierException e) {
            throw new IllegalStateException("phase workers are gone (barrier broken)", e);
        }
    }

    /** Stops and joins the workers. Idempotent; a no-op for one thread. */
    @Override
    public void close() {
        if (workers.length == 0 || closing) {
            return;
        }
        closing = true;
        try {
            barrier.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (final BrokenBarrierException e) {
            return;
        }
        for (final Thread worker : workers) {
            try {
                worker.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
