package com.simolution.kernel;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.FieldSpec;
import com.simolution.kernel.state.FieldSpec.Persist;
import com.simolution.kernel.state.WorldState;

/**
 * Serial FNV-1a style fold over the saved scalars and every {@link Persist#SAVED} field of a
 * kernel's state, in declared order, using raw IEEE bits for floating-point values. Two kernels
 * with equal digests hold the same checkpointable state (up to hash collision).
 */
public final class TestDigest {

    private static final long OFFSET = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private TestDigest() {}

    public static long of(final Kernel kernel) {
        final WorldState state = kernel.state();
        long h = OFFSET;
        h = mix(h, state.tick);
        h = mix(h, Double.doubleToRawLongBits(state.energySink));
        h = mix(h, Double.doubleToRawLongBits(state.cumulativeInflow));
        h = mix(h, state.birthsTotal);
        h = mix(h, state.maxGeneration);
        for (final FieldSpec spec : state.fields()) {
            if (spec.persist() != Persist.SAVED) {
                continue;
            }
            switch (spec.array()) {
                case int[] a -> {
                    for (final int v : a) {
                        h = mix(h, v);
                    }
                }
                case long[] a -> {
                    for (final long v : a) {
                        h = mix(h, v);
                    }
                }
                case float[] a -> {
                    for (final float v : a) {
                        h = mix(h, Float.floatToRawIntBits(v));
                    }
                }
                case double[] a -> {
                    for (final double v : a) {
                        h = mix(h, Double.doubleToRawLongBits(v));
                    }
                }
                default -> throw new IllegalStateException("cannot digest field " + spec.name());
            }
        }
        return h;
    }

    private static long mix(final long h, final long v) {
        return (h ^ v) * PRIME;
    }
}
