package com.simolution.sim;

class ArgCursor {

    private final String[] args;
    private int position;

    ArgCursor(final String[] args) {
        this.args = args;
    }

    boolean hasNext() {
        return position < args.length;
    }

    String next() {
        return args[position++];
    }

    String value(final String flag) {
        if (!hasNext()) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return next();
    }

    int intValue(final String flag) {
        return Integer.parseInt(value(flag));
    }

    long longValue(final String flag) {
        return Long.parseLong(value(flag));
    }

    double doubleValue(final String flag) {
        return Double.parseDouble(value(flag));
    }
}
