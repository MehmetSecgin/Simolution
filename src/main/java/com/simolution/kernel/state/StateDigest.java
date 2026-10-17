package com.simolution.kernel.state;

import java.util.List;

import com.simolution.kernel.phase.Chunks;
import com.simolution.kernel.phase.Phase;
import com.simolution.kernel.phase.PhaseRunner;
import com.simolution.kernel.phase.Shape;
import com.simolution.kernel.runtime.Noise;
import com.simolution.kernel.state.FieldSpec.Persist;

/**
 * Digest of everything a checkpoint saves (engine v6 design §7): the saved scalars, then every
 * {@link Persist#SAVED} field of {@link WorldState#fields()} in declared order. Two kernels
 * with equal digests hold the same checkpointable state, up to hash collision.
 * <p>
 * Each array is cut into the fixed {@link Chunks} of its length. A chunk is folded serially
 * over the raw bits of its elements ({@code h = Noise.mix(h ^ bits)}) into its own slot of
 * {@code chunkHash}; the chunk hashes are then combined in ascending chunk index
 * ({@code h = Noise.mix(h ^ chunkHash)}) into the running digest. Chunk boundaries depend only
 * on the array length, so the digest is the same for any thread count.
 * <p>
 * The per-chunk folds run on the caller's {@link PhaseRunner}: this class is itself a small
 * {@link Phase} whose {@code extent} is the length of the array being folded, run once per
 * saved array through a list built at construction. It owns every buffer it needs, so a call
 * allocates nothing in the fold itself (only {@link WorldState#fields()} builds its small
 * descriptor list). Not thread safe: one instance serves one kernel, one call at a time.
 */
public final class StateDigest implements Phase {

    private static final long OFFSET = 0xcbf29ce484222325L;

    private final WorldState state;
    private final long[] chunkHash;
    private final List<Phase> self = List.of(this);

    private Object array;
    private int length;

    public StateDigest(final WorldState state) {
        this.state = state;
        int longest = 0;
        for (final FieldSpec spec : state.fields()) {
            if (spec.persist() == Persist.SAVED) {
                longest = Math.max(longest, lengthOf(spec.array()));
            }
        }
        this.chunkHash = new long[Chunks.count(longest)];
    }

    public long compute(final PhaseRunner runner) {
        long h = OFFSET;
        h = Noise.mix(h ^ state.tick);
        h = Noise.mix(h ^ Double.doubleToRawLongBits(state.energySink));
        h = Noise.mix(h ^ Double.doubleToRawLongBits(state.cumulativeInflow));
        h = Noise.mix(h ^ state.birthsTotal);
        h = Noise.mix(h ^ state.maxGeneration);
        h = Noise.mix(h ^ Double.doubleToRawLongBits(state.creditedInitialEnergy));
        h = Noise.mix(h ^ Double.doubleToRawLongBits(state.creditedInitialMass));
        h = Noise.mix(h ^ Double.doubleToRawLongBits(state.initialResourceTotal));
        for (final FieldSpec spec : state.fields()) {
            if (spec.persist() != Persist.SAVED) {
                continue;
            }
            array = spec.array();
            length = lengthOf(array);
            runner.runTick(self);
            final int chunks = Chunks.count(length);
            for (int c = 0; c < chunks; c++) {
                h = Noise.mix(h ^ chunkHash[c]);
            }
        }
        array = null;
        return h;
    }

    @Override
    public String name() {
        return "state-digest";
    }

    /** Splittable like a per-unit phase: it only writes its own chunk's entry of {@code chunkHash}. */
    @Override
    public Shape shape() {
        return Shape.PER_UNIT;
    }

    @Override
    public int extent() {
        return length;
    }

    @Override
    public void run(final int from, final int to, final int chunk) {
        long h = OFFSET;
        switch (array) {
            case int[] a -> {
                for (int i = from; i < to; i++) {
                    h = Noise.mix(h ^ a[i]);
                }
            }
            case long[] a -> {
                for (int i = from; i < to; i++) {
                    h = Noise.mix(h ^ a[i]);
                }
            }
            case float[] a -> {
                for (int i = from; i < to; i++) {
                    h = Noise.mix(h ^ Float.floatToRawIntBits(a[i]));
                }
            }
            case double[] a -> {
                for (int i = from; i < to; i++) {
                    h = Noise.mix(h ^ Double.doubleToRawLongBits(a[i]));
                }
            }
            default -> throw new IllegalStateException("cannot digest saved field of " + array.getClass());
        }
        chunkHash[chunk] = h;
    }

    private static int lengthOf(final Object array) {
        return switch (array) {
            case int[] a -> a.length;
            case long[] a -> a.length;
            case float[] a -> a.length;
            case double[] a -> a.length;
            default -> throw new IllegalStateException("cannot digest saved field of " + array.getClass());
        };
    }
}
