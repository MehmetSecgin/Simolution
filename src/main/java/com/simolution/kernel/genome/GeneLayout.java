package com.simolution.kernel.genome;

public final class GeneLayout {

    public static final int SOURCE_KIND_SHIFT = 31;
    public static final int SOURCE_ID_SHIFT = 24;
    public static final int DESTINATION_KIND_SHIFT = 23;
    public static final int DESTINATION_ID_SHIFT = 16;
    public static final int ID_MASK = 0x7F;
    public static final int WEIGHT_MASK = 0xFFFF;

    private GeneLayout() {}

    static boolean sourceIsInternal(final int gene) {
        return (gene >>> SOURCE_KIND_SHIFT) != 0;
    }

    static int sourceId(final int gene) {
        return (gene >>> SOURCE_ID_SHIFT) & ID_MASK;
    }

    static boolean destinationIsAction(final int gene) {
        return ((gene >>> DESTINATION_KIND_SHIFT) & 1) != 0;
    }

    static int destinationId(final int gene) {
        return (gene >>> DESTINATION_ID_SHIFT) & ID_MASK;
    }

    static short weightRaw(final int gene) {
        return (short) (gene & WEIGHT_MASK);
    }

    static int pack(
            final boolean sourceInternal,
            final int sourceId,
            final boolean destinationAction,
            final int destinationId,
            final short weightRaw) {
        return (sourceInternal ? 1 : 0) << SOURCE_KIND_SHIFT
                | (sourceId & ID_MASK) << SOURCE_ID_SHIFT
                | (destinationAction ? 1 : 0) << DESTINATION_KIND_SHIFT
                | (destinationId & ID_MASK) << DESTINATION_ID_SHIFT
                | (weightRaw & WEIGHT_MASK);
    }
}
