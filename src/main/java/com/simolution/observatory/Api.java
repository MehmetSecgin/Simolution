package com.simolution.observatory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.state.FieldSpec;
import com.simolution.kernel.state.FieldSpec.Dim;
import com.simolution.kernel.state.FieldSpec.Persist;
import com.simolution.kernel.state.WorldState;
import com.simolution.sim.RunPaths;
import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunState;
import com.simolution.sim.run.RunStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

class Api implements HttpHandler {

    record BranchRequest(
            int tick,
            Map<String, JsonNode> overrides,
            int targetTicks,
            String name
    ) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EMPTY_CELL = 255;
    private static final int IDLE = 254;
    private static final int DEFAULT_POINTS = 600;
    private static final Set<RunState> ENDED =
            Set.of(RunState.FINISHED, RunState.EXTINCT, RunState.STOPPED, RunState.FAILED);
    private static final Set<Dim> UNIT_DIMS = Set.of(Dim.UNIT, Dim.UNIT_NODE, Dim.GENE);

    private final RunStore store;
    private final RunManager manager;
    private final FrameReader frames;
    private final Seeker seeker;
    private final EventStream events;

    Api(final RunStore store, final RunManager manager, final FrameReader frames, final Seeker seeker,
            final EventStream events) {
        this.store = store;
        this.manager = manager;
        this.frames = frames;
        this.seeker = seeker;
        this.events = events;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (NoSuchElementException e) {
            error(exchange, 404, e.getMessage());
        } catch (JsonProcessingException e) {
            error(exchange, 400, "bad request body: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            error(exchange, 400, e.getMessage());
        } catch (IllegalStateException e) {
            error(exchange, 409, e.getMessage());
        } catch (IOException | RuntimeException e) {
            error(exchange, 500, String.valueOf(e));
        }
    }

    private void route(final HttpExchange exchange) throws IOException {
        final String method = exchange.getRequestMethod();
        final String[] parts = exchange.getRequestURI().getPath().substring("/api/".length()).split("/");
        final Map<String, String> query = query(exchange);
        if (parts.length == 1) {
            switch (method + " " + parts[0]) {
                case "GET runs" -> json(exchange, 200, list());
                case "POST runs" -> json(exchange, 200, manager.create(body(exchange, RunRequest.class)));
                case "GET compare" -> json(exchange, 200,
                        Comparison.compare(store, List.of(required(query, "runs").split(","))));
                case "GET events" -> events.open(exchange);
                case "GET configs" -> json(exchange, 200, manager.configs());
                case "GET layout" -> send(exchange, 200, "application/json",
                        NodeLayout.toJson().getBytes(StandardCharsets.UTF_8));
                default -> throw new NoSuchElementException("no endpoint " + method + " " + parts[0]);
            }
            return;
        }
        if (!parts[0].equals("runs") || parts.length > 3) {
            throw new NoSuchElementException("no endpoint " + exchange.getRequestURI().getPath());
        }
        final String dir = parts[1];
        final Path runDir = store.path(dir);
        final String action = parts.length == 3 ? parts[2] : "";
        switch (method + " " + action) {
            case "GET " -> json(exchange, 200, detail(store.get(dir)));
            case "DELETE " -> {
                store.delete(dir);
                seeker.forget(runDir);
                json(exchange, 200, Map.of("deleted", dir));
            }
            case "POST pause" -> {
                manager.pause(dir);
                json(exchange, 200, manager.status(dir));
            }
            case "POST resume" -> {
                manager.resume(dir);
                json(exchange, 200, manager.status(dir));
            }
            case "POST stop" -> {
                manager.stop(dir);
                json(exchange, 200, manager.status(dir));
            }
            case "POST branch" -> {
                final BranchRequest branch = body(exchange, BranchRequest.class);
                json(exchange, 200, manager.branch(dir, branch.tick(), branch.overrides(), branch.targetTicks(),
                        branch.name()));
            }
            case "POST rerun" -> json(exchange, 200, manager.rerun(dir));
            case "GET metrics" -> json(exchange, 200, MetricsTable.forRun(runDir)
                    .downsample(Integer.parseInt(query.getOrDefault("points", String.valueOf(DEFAULT_POINTS)))));
            case "GET frame" -> send(exchange, 200, "application/octet-stream", frame(dir, runDir,
                    required(query, "tick")));
            case "GET state" -> send(exchange, 200, "application/octet-stream",
                    seeker.at(runDir, tick(dir, required(query, "tick")), kernel -> state(kernel.state())));
            case "GET unit" -> {
                final int slot = Integer.parseInt(required(query, "slot"));
                json(exchange, 200,
                        seeker.at(runDir, tick(dir, required(query, "tick")), kernel -> unit(kernel.state(), slot)));
            }
            default -> throw new NoSuchElementException("no endpoint " + method + " " + action);
        }
    }

    private List<Map<String, Object>> list() throws IOException {
        final List<Map<String, Object>> runs = new ArrayList<>();
        for (RunEntry entry : store.list()) {
            runs.add(summary(entry));
        }
        return runs;
    }

    private Map<String, Object> summary(final RunEntry entry) throws IOException {
        final Map<String, Object> run = new LinkedHashMap<>();
        final RunManifest manifest = entry.manifest();
        run.put("dir", entry.dir());
        run.put("name", manifest == null ? entry.dir() : manifest.name());
        run.put("parent", manifest == null ? null : manifest.parent());
        run.put("branchTick", manifest == null ? null : manifest.branchTick());
        run.put("twinOf", manifest == null ? null : manifest.twinOf());
        run.put("targetTicks", manifest == null ? 0 : manifest.targetTicks());
        run.put("modified", entry.modifiedMillis());
        run.put("unreadable", entry.unreadable());
        if (entry.unreadable() != null) {
            run.put("status", entry.status());
            return run;
        }
        final RunStatus status = manager.status(entry.dir());
        final MetricsTable metrics = MetricsTable.forRun(store.path(entry.dir()));
        run.put("status", status);
        run.put("peakPopulation", (long) metrics.max("population"));
        run.put("health", Health.worst(health(entry, status, metrics)));
        return run;
    }

    private Map<String, Object> detail(final RunEntry entry) throws IOException {
        final Map<String, Object> run = summary(entry);
        if (entry.unreadable() != null) {
            return run;
        }
        final Path dir = store.path(entry.dir());
        final MetricsTable metrics = MetricsTable.forRun(dir);
        run.put("manifest", entry.manifest());
        run.put("config", config(dir));
        run.put("health", health(entry, manager.status(entry.dir()), metrics));
        run.put("moments", moments(entry, metrics));
        return run;
    }

    private List<Health.Check> health(final RunEntry entry, final RunStatus status, final MetricsTable metrics)
            throws IOException {
        final Path dir = store.path(entry.dir());
        final int width = config(dir).get("worldWidth").asInt();
        Twins.TwinResult twin = null;
        if (ENDED.contains(status.status())) {
            for (String partner : twinPartners(entry)) {
                twin = Twins.compare(dir, store.path(partner));
                if (!twin.identical()) {
                    break;
                }
            }
        }
        final boolean pending = manager.twinRunning(entry.dir())
                || entry.manifest().twinOf() != null && manager.isActive(entry.dir());
        return Health.of(status, width * width, metrics, twin, pending);
    }

    private List<String> twinPartners(final RunEntry entry) throws IOException {
        final List<String> partners = new ArrayList<>();
        for (RunEntry other : store.list()) {
            final boolean twins = other.manifest() != null && (entry.dir().equals(other.manifest().twinOf())
                    || other.dir().equals(entry.manifest().twinOf()));
            if (twins && other.unreadable() == null && !manager.isActive(other.dir())
                    && ENDED.contains(other.status().status())) {
                partners.add(other.dir());
            }
        }
        return partners;
    }

    private List<Moments.Moment> moments(final RunEntry entry, final MetricsTable metrics) throws IOException {
        final RunManifest manifest = entry.manifest();
        final List<Moments.Moment> moments =
                new ArrayList<>(Moments.detect(metrics, manifest.targetTicks(), manifest.founders()));
        if (manifest.parent() != null && manifest.twinOf() == null) {
            moments.add(new Moments.Moment(manifest.branchTick(), "B", "Branched from " + manifest.parent()));
        }
        for (RunEntry other : store.list()) {
            final RunManifest child = other.manifest();
            if (child != null && entry.dir().equals(child.parent()) && child.twinOf() == null) {
                moments.add(new Moments.Moment(child.branchTick(), "B", "Branch " + child.name()));
            }
        }
        moments.sort(Comparator.comparingInt(Moments.Moment::tick));
        return moments;
    }

    private byte[] frame(final String dir, final Path runDir, final String tickValue) throws IOException {
        if (tickValue.equals("live")) {
            final LatestFrame latest = manager.latest(dir);
            final byte[] live = latest == null ? null : latest.read();
            if (live != null) {
                return live;
            }
        }
        final int tick = tick(dir, tickValue.equals("live") ? String.valueOf(manager.ticksDone(dir)) : tickValue);
        final byte[] stored = frames.frame(runDir, tick);
        return stored != null
                ? stored
                : seeker.at(runDir, tick, kernel -> FrameReader.encode(kernel.state(),
                        kernel.config()));
    }

    private int tick(final String dir, final String value) throws IOException {
        final int tick = Integer.parseInt(value);
        final int done = manager.ticksDone(dir);
        if (tick < 0 || tick > done) {
            throw new NoSuchElementException("tick " + tick + " is outside " + dir + " (0.." + done + ")");
        }
        return tick;
    }

    private static byte[] state(final WorldState state) {
        final int cells = state.worldWidth * state.worldWidth;
        final ByteBuffer out = ByteBuffer.allocate(4 + 4 + 5 * cells).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(state.tick).putInt(state.worldWidth);
        for (int cell = 0; cell < cells; cell++) {
            final int slot = state.cellOccupant[cell];
            out.putFloat(slot < 0 ? 0.0f : (float) state.mass[slot]);
        }
        for (int cell = 0; cell < cells; cell++) {
            final int slot = state.cellOccupant[cell];
            out.put((byte) (slot < 0 ? EMPTY_CELL : strongestAction(state, slot)));
        }
        return out.array();
    }

    private static int strongestAction(final WorldState state, final int slot) {
        int best = IDLE;
        float strongest = 0.0f;
        for (int action = 0; action < NodeLayout.Action.MEANINGFUL_COUNT; action++) {
            final float output = state.outputsPrev[NodeLayout.actionIndex(slot, action)];
            if (output > strongest) {
                strongest = output;
                best = action;
            }
        }
        return best;
    }

    private static Map<String, Object> unit(final WorldState state, final int slot) {
        if (slot < 0 || slot >= state.unitCount || state.energy[slot] <= 0.0) {
            throw new NoSuchElementException("no living unit in slot " + slot + " at tick " + state.tick);
        }
        final Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldSpec spec : state.fields()) {
            if (spec.persist() == Persist.SAVED && UNIT_DIMS.contains(spec.dim())) {
                fields.put(spec.name(), slice(spec, state, slot));
            }
        }
        final List<Map<String, Object>> connections = new ArrayList<>();
        for (int i = state.sumStart[slot]; i < state.mulEnd[slot]; i++) {
            connections.add(Map.of("source", (int) state.connSrc[i], "destination", (int) state.connDst[i],
                    "weight", state.connWeight[i]));
        }
        final Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("tick", state.tick);
        unit.put("slot", slot);
        unit.put("fields", fields);
        unit.put("connections", connections);
        unit.put("outputs", fields.get("outputsPrev"));
        return unit;
    }

    private static Object slice(final FieldSpec spec, final WorldState state, final int slot) {
        final Object array = spec.array();
        if (spec.dim() == Dim.UNIT) {
            return Array.get(array, slot);
        }
        final int from = spec.dim() == Dim.GENE ? slot * state.maxGenes : slot * NodeLayout.TOTAL;
        final int length = spec.dim() == Dim.GENE ? state.geneCount[slot] : NodeLayout.TOTAL;
        final List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            values.add(Array.get(array, from + i));
        }
        return values;
    }

    private static JsonNode config(final Path dir) throws IOException {
        return JSON.readTree(Files.readString(new RunPaths(dir).config()));
    }

    private static <T> T body(final HttpExchange exchange, final Class<T> type) throws IOException {
        final byte[] bytes = exchange.getRequestBody().readAllBytes();
        return JSON.readValue(bytes.length == 0 ? "{}".getBytes(StandardCharsets.UTF_8) : bytes, type);
    }

    private static Map<String, String> query(final HttpExchange exchange) {
        final Map<String, String> values = new HashMap<>();
        final String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                final int eq = pair.indexOf('=');
                if (eq > 0) {
                    values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return values;
    }

    private static String required(final Map<String, String> query, final String name) {
        final String value = query.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing query parameter " + name);
        }
        return value;
    }

    private static void json(final HttpExchange exchange, final int status, final Object value) throws IOException {
        send(exchange, status, "application/json", JSON.writeValueAsBytes(value));
    }

    private static void error(final HttpExchange exchange, final int status, final String message)
            throws IOException {
        json(exchange, status, Map.of("error", String.valueOf(message)));
    }

    static void send(final HttpExchange exchange, final int status, final String type, final byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
