package com.simolution.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The replay key (report-v8 {@code manifest.json}): everything needed to
 * reconstruct a run's canonical history exactly, given a matching build.
 * Determinism (contract-v3 §1) turns "store the past" into "recompute the
 * past", so this small object plus the kernel is the whole time machine.
 * <p>
 * Founder genomes and cells are stored <b>verbatim</b> (not regenerated from
 * {@code genomeSeed}): {@code configHash} covers {@link com.simolution.kernel.config.KernelConfig}
 * constants but not {@code GenomeFactory}'s algorithm, so a factory change would
 * otherwise silently produce different founders with no mismatch caught. The
 * founding state is data, like the scatter placement (ADR 0022). {@code genomeSeed}
 * is kept only as provenance.
 */
public record RunManifest(
        String kernel,
        long seed,
        int worldWidth,
        int founderCount,
        int[] founderCells,
        int[][] founderGenomes,
        long genomeSeed,
        int genesPerUnit,
        int maxGenes,
        int ticks,
        String configHash,
        int checkpointEvery,
        int mapSampleEvery
) {

    public String toJson() {
        final StringBuilder b = new StringBuilder(64 + founderCount * 8);
        b.append("{\n");
        b.append("  \"kernel\": \"").append(kernel).append("\",\n");
        b.append("  \"seed\": ").append(seed).append(",\n");
        b.append("  \"worldWidth\": ").append(worldWidth).append(",\n");
        b.append("  \"founderCount\": ").append(founderCount).append(",\n");
        b.append("  \"genomeSeed\": ").append(genomeSeed).append(",\n");
        b.append("  \"genesPerUnit\": ").append(genesPerUnit).append(",\n");
        b.append("  \"maxGenes\": ").append(maxGenes).append(",\n");
        b.append("  \"ticks\": ").append(ticks).append(",\n");
        b.append("  \"configHash\": \"").append(configHash).append("\",\n");
        b.append("  \"checkpointEvery\": ").append(checkpointEvery).append(",\n");
        b.append("  \"mapSampleEvery\": ").append(mapSampleEvery).append(",\n");
        b.append("  \"founderCells\": ");
        appendIntArray(b, founderCells);
        b.append(",\n");
        b.append("  \"founderGenomes\": [");
        for (int i = 0; i < founderGenomes.length; i++) {
            if (i > 0) {
                b.append(',');
            }
            appendIntArray(b, founderGenomes[i]);
        }
        b.append("]\n");
        b.append("}\n");
        return b.toString();
    }

    private static void appendIntArray(final StringBuilder b, final int[] a) {
        b.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(a[i]);
        }
        b.append(']');
    }

    @SuppressWarnings("unchecked")
    public static RunManifest parse(final String json) {
        final Object root = new Json(json).parseValue();
        final Map<String, Object> m = (Map<String, Object>) root;
        return new RunManifest(
                (String) m.get("kernel"),
                ((Number) m.get("seed")).longValue(),
                ((Number) m.get("worldWidth")).intValue(),
                ((Number) m.get("founderCount")).intValue(),
                toIntArray((List<Object>) m.get("founderCells")),
                toIntMatrix((List<Object>) m.get("founderGenomes")),
                ((Number) m.get("genomeSeed")).longValue(),
                ((Number) m.get("genesPerUnit")).intValue(),
                ((Number) m.get("maxGenes")).intValue(),
                ((Number) m.get("ticks")).intValue(),
                (String) m.get("configHash"),
                ((Number) m.get("checkpointEvery")).intValue(),
                ((Number) m.get("mapSampleEvery")).intValue());
    }

    private static int[] toIntArray(final List<Object> list) {
        final int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = ((Number) list.get(i)).intValue();
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    private static int[][] toIntMatrix(final List<Object> list) {
        final int[][] a = new int[list.size()][];
        for (int i = 0; i < a.length; i++) {
            a[i] = toIntArray((List<Object>) list.get(i));
        }
        return a;
    }

    /**
     * Minimal JSON-subset reader for our own manifest: objects, arrays, strings,
     * numbers (integral and negative), booleans, null. Sufficient because we are
     * the only producer; not a general-purpose parser.
     */
    private static final class Json {
        private final String s;
        private int i;

        Json(final String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            final char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBool();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            final Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skipWs();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                final String key = parseString();
                skipWs();
                i++;
                final Object value = parseValue();
                m.put(key, value);
                skipWs();
                final char c = s.charAt(i++);
                if (c == '}') {
                    return m;
                }
            }
        }

        private List<Object> parseArray() {
            final List<Object> list = new ArrayList<>();
            i++;
            skipWs();
            if (s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                final char c = s.charAt(i++);
                if (c == ']') {
                    return list;
                }
            }
        }

        private String parseString() {
            final StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                final char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    b.append(s.charAt(i++));
                } else {
                    b.append(c);
                }
            }
        }

        private Object parseNumber() {
            final int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            final String tok = s.substring(start, i);
            if (tok.indexOf('.') >= 0 || tok.indexOf('e') >= 0 || tok.indexOf('E') >= 0) {
                return Double.parseDouble(tok);
            }
            return Long.parseLong(tok);
        }

        private Boolean parseBool() {
            if (s.charAt(i) == 't') {
                i += 4;
                return Boolean.TRUE;
            }
            i += 5;
            return Boolean.FALSE;
        }

        private Object parseNull() {
            i += 4;
            return null;
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
