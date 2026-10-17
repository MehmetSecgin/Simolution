package com.simolution.kernel.phase;

/**
 * One declared step of the tick. A phase owns no state: it reads and writes the shared
 * {@link com.simolution.kernel.state.WorldState} it was built over, inside the limits of
 * its {@link Shape}. {@code run} must allocate nothing.
 * <p>
 * A runner calls {@link #prepare()}, then {@link #run} over the whole {@code [0, extent())}
 * range (split into chunks for the shapes that allow it), then {@link #finish()}.
 */
public interface Phase {

    String name();

    Shape shape();

    default void prepare() {
    }

    void run(int from, int to, int chunk);

    default void finish() {
    }

    /** Slot count for {@link Shape#PER_UNIT}, cell count for {@link Shape#PER_CELL}, either for {@link Shape#SERIAL}. */
    int extent();
}
