package com.simolution.kernel.phase;

/**
 * What a {@link Phase} may read and write, which fixes how it may be split across
 * threads (engine v6 design §5.3).
 * <ul>
 *   <li>{@link #PER_UNIT}: reads anything not written in this phase, writes only its own
 *       slot's fields and its own cell's cell fields; extent is the slot count.</li>
 *   <li>{@link #PER_CELL}: reads the previous buffer of any cell field and writes only the next
 *       buffer of its own cell, or reads and writes only its own cell in place; extent is the
 *       cell count.</li>
 *   <li>{@link #CLAIM}: proposes intents in {@code run}, resolves the winners in
 *       {@link Phase#finish()} in a fixed order.</li>
 *   <li>{@link #SERIAL}: runs once over {@code [0, extent)} with {@code chunk == 0}; used for
 *       phases that write shared scalars or resolve order-dependent conflicts.</li>
 * </ul>
 * A split phase may also add into its own chunk's entry of the partial totals
 * ({@code sinkPartial}, {@code inflowPartial}); its {@link Phase#finish()} merges them in
 * ascending chunk index.
 */
public enum Shape {
    PER_UNIT,
    PER_CELL,
    CLAIM,
    SERIAL
}
