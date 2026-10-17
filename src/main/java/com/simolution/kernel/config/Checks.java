package com.simolution.kernel.config;

final class Checks {

    private Checks() {
    }

    static void nonNegative(final String field, final double value) {
        if (!(value >= 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and at least 0, got " + value);
        }
    }

    static void positive(final String field, final double value) {
        if (!(value > 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite and above 0, got " + value);
        }
    }

    static void unitInterval(final String field, final double value) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(field + " must be in [0, 1], got " + value);
        }
    }
}
