package com.simolution.kernel.phase;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * The "lowest slot wins" tie rule of the claim phases (contract v9 §1, §2) as an atomic
 * minimum, so proposers on any thread can claim a cell during the parallel propose pass.
 * <p>
 * {@code cellWinner[cell]} is {@code -1} (unclaimed) or the lowest slot that has claimed the
 * cell so far. A claim only ever lowers it, and the minimum of a set does not depend on the
 * order its members arrive in, so after the propose barrier every entry holds the lowest
 * claimant whatever the thread count and the schedule. The compare-and-set loop retries only
 * when another thread changed the entry in between; it allocates nothing (the
 * {@link VarHandle} call sites are exact, so the JIT inlines them).
 */
final class CellClaim {

    private static final VarHandle CELL = MethodHandles.arrayElementVarHandle(int[].class);

    private CellClaim() {}

    static void claim(final int[] cellWinner, final int cell, final int slot) {
        int seen = (int) CELL.getVolatile(cellWinner, cell);
        while ((seen < 0 || slot < seen) && !CELL.compareAndSet(cellWinner, cell, seen, slot)) {
            seen = (int) CELL.getVolatile(cellWinner, cell);
        }
    }
}
