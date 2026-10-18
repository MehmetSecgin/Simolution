package com.simolution.kernel.phase;

/**
 * One declared step of the tick. A phase owns no state: it reads and writes the shared
 * {@link com.simolution.kernel.state.WorldState} it was built over, inside the limits of
 * its {@link Shape}. {@code run} must allocate nothing.
 * <p>
 * A runner calls {@link #run} over the whole {@code [0, extent())}
 * range (split into {@link Chunks} for the shapes that allow it, {@code chunk} being the
 * chunk index), then, for each of the {@link #resolvePasses()} passes {@code p} in order,
 * {@link #prepareResolve(int) prepareResolve(p)} on the caller and
 * {@link #resolve(int, int, int, int) resolve(p, …)} over the same chunks, then
 * {@link #finish()}. Every pass ends at the runner's barrier, so a pass sees every write of
 * the passes before it.
 */
public interface Phase {

    String name();

    Shape shape();

    void run(int from, int to, int chunk);

    /**
     * Split passes a {@link Shape#CLAIM} phase runs after {@link #run}, so a resolve can be
     * spread over chunks instead of running serially in {@link #finish()}.
     */
    default int resolvePasses() {
        return 0;
    }

    /** Serial step on the caller before resolve pass {@code pass}, e.g. a prefix over chunk counts. */
    default void prepareResolve(final int pass) {
    }

    default void resolve(final int pass, final int from, final int to, final int chunk) {
    }

    default void finish() {
    }

    /** Slot count for {@link Shape#PER_UNIT}, cell count for {@link Shape#PER_CELL}, either for {@link Shape#SERIAL}. */
    int extent();
}
