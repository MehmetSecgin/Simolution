package com.simolution.kernel.phase;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Runs the phase list on a fixed pool of persistent threads (engine v6 design §5.8).
 * <p>
 * A splittable phase ({@link Shape#PER_UNIT}, {@link Shape#PER_CELL}, {@link Shape#CLAIM})
 * is cut into {@link Chunks} and each chunk of each pass ({@link Phase#run} and every
 * {@link Phase#resolve} pass) is run exactly once, by whichever thread claims
 * it from an atomic counter. Which thread runs a chunk never matters: a phase writes only its
 * own indices and its own {@code chunk} partial, and partials are merged in ascending chunk
 * index afterwards. {@link Shape#SERIAL} phases, phases of a single chunk and every phase when
 * {@code threads == 1} run on the caller, chunk by chunk in ascending order; with one thread
 * no worker is ever started.
 * <p>
 * <b>Handshake.</b> The caller takes part as one of the {@code threads}. For a split phase it
 * writes the phase, extent, chunk count, resets the chunk counter and sets {@code remaining}
 * to the worker count, then publishes by incrementing the volatile {@code generation} and
 * unparking every worker. Each worker waits for a generation it has not seen, claims chunks,
 * and decrements {@code remaining}; the worker that brings it to zero unparks the caller. The
 * caller claims chunks too, then waits for {@code remaining == 0} before it runs
 * {@link Phase#finish()}. That wait is the one barrier between phases; the release is a
 * single volatile publish. Waiters spin briefly ({@link Thread#onSpinWait()}) and then
 * {@link LockSupport#park}; nothing in the handshake allocates (unlike {@code CyclicBarrier}
 * and {@code Phaser}, which allocate a generation or wait node per crossing).
 * <p>
 * <b>Visibility.</b> The caller's plain writes happen before its volatile write of
 * {@code generation}, which happens before a worker's volatile read that sees the new value,
 * so a released worker sees the published phase. A worker's chunk writes happen before its
 * {@code decrementAndGet} on {@code remaining}; the atomic read-modify-writes form one chain,
 * so the caller's read of zero happens after every worker's writes, and every thread sees
 * them in later phases. Only {@code generation} and the two counters need to be volatile.
 * <p>
 * <b>No lost wakeup.</b> A waiter re-checks its condition (a volatile read) before every
 * {@code park}, and the signaller makes the condition true before it calls {@code unpark}.
 * If the unpark lands before the park, the thread's permit makes that park return at once; if
 * it lands after, it wakes the parked thread. Either way the waiter re-checks and proceeds.
 * Spurious returns from {@code park} are absorbed by the same loop.
 * <p>
 * <b>Failures and shutdown.</b> A {@link Throwable} thrown by a phase on any thread is
 * recorded, stops further chunk claims, and every thread still reaches the join, so the caller
 * rethrows it (wrapped, with the phase name) instead of hanging; the runner stays usable.
 * Interrupts are not honoured inside a phase: the waits run to completion and the caller's
 * interrupt status is left set. Workers are daemon threads, so a runner that is never closed
 * cannot keep the JVM alive; {@link #close()} releases them with {@code closing} set and
 * joins them, after which split phases are refused.
 */
public final class ParallelRunner implements PhaseRunner, AutoCloseable {

    private static final int SPINS = 1 << 12;
    private static final int RUN = -1;

    private final int threads;
    private final Thread[] workers;
    private final AtomicInteger nextChunk = new AtomicInteger();
    private final AtomicInteger remaining = new AtomicInteger();

    private volatile int generation;
    private Thread caller;
    private Phase current;
    private int currentPass;
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
            } else {
                runPass(phase, RUN, n);
                for (int pass = 0; pass < phase.resolvePasses(); pass++) {
                    phase.prepareResolve(pass);
                    runPass(phase, pass, n);
                }
            }
            phase.finish();
        }
    }

    private void runPass(final Phase phase, final int pass, final int n) {
        if (threads == 1 || Chunks.count(n) <= 1) {
            runInline(phase, pass, n);
        } else {
            runSplit(phase, pass, n);
        }
    }

    private static void runInline(final Phase phase, final int pass, final int n) {
        final int count = Chunks.count(n);
        for (int c = 0; c < count; c++) {
            final int from = c * Chunks.SIZE;
            runChunk(phase, pass, from, Math.min(n, from + Chunks.SIZE), c);
        }
    }

    private static void runChunk(final Phase phase, final int pass, final int from, final int to, final int chunk) {
        if (pass == RUN) {
            phase.run(from, to, chunk);
        } else {
            phase.resolve(pass, from, to, chunk);
        }
    }

    private void runSplit(final Phase phase, final int pass, final int n) {
        if (closing) {
            throw new IllegalStateException("runner is closed");
        }
        caller = Thread.currentThread();
        current = phase;
        currentPass = pass;
        extent = n;
        chunkCount = Chunks.count(n);
        failure = null;
        nextChunk.set(0);
        remaining.set(workers.length);
        release();
        claimChunks();
        awaitWorkers();
        current = null;
        final Throwable thrown = failure;
        if (thrown != null) {
            throw new IllegalStateException("phase " + phase.name() + " failed", thrown);
        }
    }

    private void release() {
        generation++;
        for (final Thread worker : workers) {
            LockSupport.unpark(worker);
        }
    }

    private void awaitWorkers() {
        int spins = SPINS;
        while (remaining.get() != 0) {
            if (spins > 0) {
                spins--;
                Thread.onSpinWait();
            } else {
                LockSupport.park(this);
            }
        }
    }

    private void claimChunks() {
        final Phase phase = current;
        final int pass = currentPass;
        final int n = extent;
        final int count = chunkCount;
        try {
            int c;
            while ((c = nextChunk.getAndIncrement()) < count) {
                final int from = c * Chunks.SIZE;
                runChunk(phase, pass, from, Math.min(n, from + Chunks.SIZE), c);
            }
        } catch (final Throwable t) {
            failure = t;
            nextChunk.set(count);
        }
    }

    private void workerLoop() {
        int seen = 0;
        while (true) {
            int spins = SPINS;
            int now;
            while ((now = generation) == seen) {
                if (spins > 0) {
                    spins--;
                    Thread.onSpinWait();
                } else {
                    LockSupport.park(this);
                }
            }
            seen = now;
            if (closing) {
                return;
            }
            claimChunks();
            if (remaining.decrementAndGet() == 0) {
                LockSupport.unpark(caller);
            }
        }
    }

    /** Stops and joins the workers. Idempotent; a no-op for one thread. */
    @Override
    public void close() {
        if (workers.length == 0 || closing) {
            return;
        }
        closing = true;
        release();
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
