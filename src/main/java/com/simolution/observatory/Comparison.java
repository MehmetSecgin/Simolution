package com.simolution.observatory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunManifest;

public final class Comparison {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MIN_RUNS = 2;
    private static final int MAX_RUNS = 4;

    public record Setting(
            String key,
            List<String> values,
            boolean differs
    ) {}

    public record Outcome(
            String name,
            List<Double> values,
            List<Double> deltaPercent
    ) {}

    public record Divergence(
            String kind,
            int tick,
            Integer checkpoints,
            Boolean identical
    ) {}

    public record Result(
            List<RunEntry> runs,
            List<Setting> settings,
            List<Outcome> outcomes,
            Divergence divergence
    ) {}

    private record Link(
            String dir,
            Integer branchTick
    ) {}

    private Comparison() {}

    public static Result compare(final RunStore store, final List<String> dirs) throws IOException {
        if (dirs.size() < MIN_RUNS || dirs.size() > MAX_RUNS) {
            throw new IllegalArgumentException("compare takes 2 to 4 runs, got " + dirs.size());
        }
        final List<RunEntry> runs = new ArrayList<>();
        final List<MetricsTable> metrics = new ArrayList<>();
        final List<Map<String, String>> settings = new ArrayList<>();
        for (String dir : dirs) {
            final RunEntry entry = store.get(dir);
            if (entry.unreadable() != null) {
                throw new IllegalStateException(dir + " cannot be read: " + entry.unreadable());
            }
            runs.add(entry);
            metrics.add(MetricsTable.forRun(store.path(dir)));
            settings.add(settingsOf(store.path(dir), entry.manifest()));
        }
        return new Result(runs, settingRows(settings), outcomes(metrics), divergence(store, runs));
    }

    private static Map<String, String> settingsOf(final Path dir, final RunManifest manifest) throws IOException {
        final Map<String, String> values = new LinkedHashMap<>();
        final JsonNode config = JSON.readTree(Files.readString(new RunPaths(dir).config()));
        for (Iterator<Map.Entry<String, JsonNode>> it = config.fields(); it.hasNext();) {
            final Map.Entry<String, JsonNode> field = it.next();
            values.put(field.getKey(), field.getValue().isValueNode()
                    ? field.getValue().asText()
                    : field.getValue().toString());
        }
        values.put("founders", String.valueOf(manifest.founders()));
        values.put("genesPerFounder", String.valueOf(manifest.genesPerFounder()));
        values.put("targetTicks", String.valueOf(manifest.targetTicks()));
        values.put("checkpointEvery", String.valueOf(manifest.checkpointEvery()));
        return values;
    }

    private static List<Setting> settingRows(final List<Map<String, String>> settings) {
        final List<Setting> rows = new ArrayList<>();
        for (String key : settings.get(0).keySet()) {
            final List<String> values = settings.stream().map(s -> s.get(key)).toList();
            rows.add(new Setting(key, values, values.stream().distinct().count() > 1));
        }
        return rows;
    }

    private static List<Outcome> outcomes(final List<MetricsTable> metrics) {
        return List.of(
                outcome("Peak population", metrics.stream().map(m -> m.max("population")).toList()),
                outcome("Final population", metrics.stream().map(m -> m.last("population")).toList()),
                outcome("Births", metrics.stream().map(m -> m.last("births_cum")).toList()),
                outcome("Lineages alive", metrics.stream().map(m -> m.last("lineages_alive")).toList()),
                outcome("Total mass", metrics.stream().map(m -> m.last("mass_total")).toList()));
    }

    private static Outcome outcome(final String name, final List<Double> values) {
        final double base = values.get(0);
        final List<Double> deltas = values.stream().map(v -> base == 0.0 ? null : (v - base) / base * 100.0)
                .toList();
        return new Outcome(name, values, deltas);
    }

    private static Divergence divergence(final RunStore store, final List<RunEntry> runs) throws IOException {
        final RunEntry first = runs.get(0);
        final List<Divergence> pairs = new ArrayList<>();
        for (RunEntry other : runs.subList(1, runs.size())) {
            pairs.add(pair(store, first, other));
        }
        if (pairs.stream().allMatch(p -> p.kind().equals("twin"))) {
            final boolean identical = pairs.stream().allMatch(Divergence::identical);
            final int checkpoints = pairs.stream().mapToInt(Divergence::checkpoints).min().orElse(0);
            final int tick = pairs.stream().filter(p -> !p.identical()).mapToInt(Divergence::tick).min()
                    .orElse(first.status().ticksDone());
            return new Divergence("twin", tick, checkpoints, identical);
        }
        if (pairs.stream().anyMatch(p -> p.kind().equals("none"))) {
            return new Divergence("none", 0, null, null);
        }
        return new Divergence("ancestor", pairs.stream().mapToInt(Divergence::tick).min().orElse(0), null, null);
    }

    private static Divergence pair(final RunStore store, final RunEntry a, final RunEntry b) throws IOException {
        final RunManifest left = a.manifest();
        final RunManifest right = b.manifest();
        if (a.dir().equals(right.twinOf()) || b.dir().equals(left.twinOf())
                || left.twinOf() != null && left.twinOf().equals(right.twinOf())) {
            final Twins.TwinResult twin = Twins.compare(store.path(a.dir()), store.path(b.dir()));
            final int tick = twin.identical() ? a.status().ticksDone() : twin.firstDifferentTick();
            return new Divergence("twin", tick, twin.checkpoints(), twin.identical());
        }
        final List<Link> leftChain = chain(store, a.dir());
        final List<Link> rightChain = chain(store, b.dir());
        for (int i = 0; i < leftChain.size(); i++) {
            for (int j = 0; j < rightChain.size(); j++) {
                if (leftChain.get(i).dir().equals(rightChain.get(j).dir())) {
                    final int tick = Math.min(earliestBranch(leftChain.subList(0, i)),
                            earliestBranch(rightChain.subList(0, j)));
                    return new Divergence("ancestor", tick == Integer.MAX_VALUE ? a.status().ticksDone() : tick,
                            null, null);
                }
            }
        }
        return new Divergence("none", 0, null, null);
    }

    private static List<Link> chain(final RunStore store, final String dir) throws IOException {
        final List<Link> chain = new ArrayList<>();
        String at = dir;
        while (at != null) {
            final RunManifest manifest = store.get(at).manifest();
            chain.add(new Link(at, manifest.branchTick()));
            at = manifest.parent();
        }
        return chain;
    }

    private static int earliestBranch(final List<Link> below) {
        return below.stream().mapToInt(Link::branchTick).min().orElse(Integer.MAX_VALUE);
    }
}
