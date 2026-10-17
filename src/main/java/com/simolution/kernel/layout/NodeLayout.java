package com.simolution.kernel.layout;

import com.simolution.kernel.genome.GeneDecoder;
import com.simolution.kernel.genome.GeneLayout;
import com.simolution.kernel.layout.NodeSpec.Kind;

/**
 * Flat per-unit index space {@code [sensors | internals | actions]}, derived from
 * {@link NodeCatalog#V5} at class init. Every value is computed from the catalog,
 * so none is a compile-time constant: do not use them as {@code case} labels.
 * The nested classes read only the catalog (never this class's statics) so class
 * initialisation has no cycle.
 */
public final class NodeLayout {

    private static final NodeCatalog CATALOG = NodeCatalog.V5;

    public static final class Sensor {

        public static final int CONST = NodeCatalog.V5.indexOf(Kind.SENSOR, "CONST");
        public static final int RAND = NodeCatalog.V5.indexOf(Kind.SENSOR, "RAND");

        public static final int MEANINGFUL_COUNT = NodeCatalog.V5.nodes(Kind.SENSOR).size();

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + NodeCatalog.V5.junk(Kind.SENSOR);

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final class Internal {

        public static final int ADD = NodeCatalog.V5.indexOf(Kind.INTERNAL, "ADD");
        public static final int MUL = NodeCatalog.V5.indexOf(Kind.INTERNAL, "MUL");
        public static final int CLAMP = NodeCatalog.V5.indexOf(Kind.INTERNAL, "CLAMP");
        public static final int DELAY = NodeCatalog.V5.indexOf(Kind.INTERNAL, "DELAY");
        public static final int THRESH = NodeCatalog.V5.indexOf(Kind.INTERNAL, "THRESH");

        public static final int MEANINGFUL_COUNT = NodeCatalog.V5.nodes(Kind.INTERNAL).size();

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + NodeCatalog.V5.junk(Kind.INTERNAL);

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final class Action {

        public static final int Y = NodeCatalog.V5.indexOf(Kind.ACTION, "ACTION_Y");
        public static final int HARVEST = NodeCatalog.V5.indexOf(Kind.ACTION, "HARVEST");
        public static final int REPRODUCE = NodeCatalog.V5.indexOf(Kind.ACTION, "REPRODUCE");
        public static final int MOVE_N = NodeCatalog.V5.indexOf(Kind.ACTION, "MOVE_N");
        public static final int MOVE_S = NodeCatalog.V5.indexOf(Kind.ACTION, "MOVE_S");
        public static final int MOVE_E = NodeCatalog.V5.indexOf(Kind.ACTION, "MOVE_E");
        public static final int MOVE_W = NodeCatalog.V5.indexOf(Kind.ACTION, "MOVE_W");
        public static final int GROW = NodeCatalog.V5.indexOf(Kind.ACTION, "GROW");

        public static final int MEANINGFUL_COUNT = NodeCatalog.V5.nodes(Kind.ACTION).size();

        public static final int TYPE_COUNT =
                MEANINGFUL_COUNT + NodeCatalog.V5.junk(Kind.ACTION);

        public static final int INSTANCES_PER_TYPE = 1;
        public static final int COUNT = TYPE_COUNT * INSTANCES_PER_TYPE;

    }

    public static final int SENSOR_OFFSET = 0;
    public static final int INTERNAL_OFFSET = SENSOR_OFFSET + Sensor.COUNT;
    public static final int ACTION_OFFSET = INTERNAL_OFFSET + Internal.COUNT;

    public static final int TOTAL =
            Sensor.COUNT + Internal.COUNT + Action.COUNT;

    private static final NodeSpec[] SPECS = buildSpecs();

    private static NodeSpec[] buildSpecs() {
        final NodeSpec[] specs = new NodeSpec[TOTAL];
        fill(specs, Kind.SENSOR, SENSOR_OFFSET);
        fill(specs, Kind.INTERNAL, INTERNAL_OFFSET);
        fill(specs, Kind.ACTION, ACTION_OFFSET);
        return specs;
    }

    private static void fill(final NodeSpec[] specs, final Kind kind, final int offset) {
        int i = offset;
        for (final NodeSpec spec : CATALOG.nodes(kind)) {
            specs[i++] = spec;
        }
    }

    /** The node's spec, or {@code null} for junk padding. */
    public static NodeSpec spec(final int localIndex) {
        return SPECS[localIndex];
    }

    /**
     * True if the unit-local index refers to a meaningful node, one the
     * kernel actually evaluates. Junk nodes pad each block's tail; their
     * outputs stay 0 forever, so signals into them vanish (junk sinks)
     * and signals from them are always zero.
     */
    public static boolean isMeaningful(final int localIndex) {
        return SPECS[localIndex] != null;
    }

    /**
     * Human name for a unit-local node index. Meaningful nodes get their catalog
     * name (CONST, ADD, DELAY, ...); junk padding gets JUNK-S/I/A + block offset.
     */
    public static String localName(final int localIndex) {
        final NodeSpec spec = SPECS[localIndex];
        if (spec != null) {
            return spec.name();
        }
        if (localIndex < INTERNAL_OFFSET) {
            return "JUNK-S" + (localIndex - SENSOR_OFFSET);
        }
        if (localIndex < ACTION_OFFSET) {
            return "JUNK-I" + (localIndex - INTERNAL_OFFSET);
        }
        return "JUNK-A" + (localIndex - ACTION_OFFSET);
    }

    /**
     * The layout as JSON for the viewers: node names by local index, block
     * offsets, type counts (meaningful + junk, the modulus genes wrap by),
     * junk ranges as {@code [first, endExclusive)} local indices, and the gene
     * bit layout. Written as {@code <base>.layout.json} beside every {@code --out} run.
     */
    public static String toJson() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"total\":").append(TOTAL);
        sb.append(",\"names\":[");
        for (int i = 0; i < TOTAL; i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(localName(i)).append('"');
        }
        sb.append("],\"offsets\":{\"sensor\":").append(SENSOR_OFFSET)
                .append(",\"internal\":").append(INTERNAL_OFFSET)
                .append(",\"action\":").append(ACTION_OFFSET);
        sb.append("},\"typeCounts\":{\"sensor\":").append(Sensor.TYPE_COUNT)
                .append(",\"internal\":").append(Internal.TYPE_COUNT)
                .append(",\"action\":").append(Action.TYPE_COUNT);
        sb.append("},\"junk\":{\"sensor\":[").append(SENSOR_OFFSET + Sensor.MEANINGFUL_COUNT).append(',')
                .append(INTERNAL_OFFSET)
                .append("],\"internal\":[").append(INTERNAL_OFFSET + Internal.MEANINGFUL_COUNT).append(',')
                .append(ACTION_OFFSET)
                .append("],\"action\":[").append(ACTION_OFFSET + Action.MEANINGFUL_COUNT).append(',').append(TOTAL);
        sb.append("]},\"gene\":{\"bits\":32,\"srcType\":{\"shift\":").append(GeneLayout.SOURCE_KIND_SHIFT)
                .append(",\"width\":1}")
                .append(",\"srcId\":{\"shift\":").append(GeneLayout.SOURCE_ID_SHIFT).append(",\"width\":7}")
                .append(",\"dstType\":{\"shift\":").append(GeneLayout.DESTINATION_KIND_SHIFT).append(",\"width\":1}")
                .append(",\"dstId\":{\"shift\":").append(GeneLayout.DESTINATION_ID_SHIFT).append(",\"width\":7}")
                .append(",\"weight\":{\"shift\":0,\"width\":16,\"signed\":true,\"scale\":")
                .append(GeneDecoder.WEIGHT_PER_RAW_UNIT).append(",\"max\":").append(GeneDecoder.NOMINAL_MAX_WEIGHT)
                .append("}}}");
        return sb.toString();
    }

    private NodeLayout() {}

}
