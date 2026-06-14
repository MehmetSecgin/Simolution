package com.simolution.sim;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny live viewer server (report-v7 live mode, report-v9 frame format). Serves
 * a player page and a frames endpoint that <b>tails the streamed {@code .map.txt}</b>
 * — so the run can be watched as it ticks, then scrubbed once it ends. The frames
 * live on disk (written by {@link MapFrameWriter}); the server re-reads the file on
 * request and holds nothing per-tick in memory, so the memory doctrine is preserved.
 * <p>
 * There is <b>no reconstruction</b>: each frame already carries its own list of
 * living units and their genomes, so the viewer computes distinct genomes and
 * decodes circuits entirely client-side. The server just turns the line-oriented
 * frame file into JSON. JDK built-in {@code com.sun.net.httpserver} — no dependency.
 * <ul>
 *   <li>{@code GET /} → the live player page (classpath resource live.html).</li>
 *   <li>{@code GET /frames?since=N} → JSON {@code {world, sampleEvery, total, done,
 *       frames:[{t, r, units:[{cell,slot,lineage,gen,energy,genes:[...]}]}]}} for
 *       frames at index ≥ N. While the run is live the final (possibly half-written)
 *       frame is withheld; once done, every frame is served.</li>
 * </ul>
 */
public final class LiveServer {

    private final HttpServer http;
    private final Path mapFile;
    private volatile boolean done;

    public LiveServer(final int port, final Path mapFile) throws IOException {
        this.mapFile = mapFile;
        this.http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/frames", this::handleFrames);
        http.createContext("/", this::handleRoot);
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(0);
    }

    /** Mark the run finished so the player stops polling and the last frame is served. */
    public void markDone() {
        this.done = true;
    }

    private void handleRoot(final HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        final byte[] page;
        try (var in = LiveServer.class.getResourceAsStream("/live.html")) {
            if (in == null) {
                respond(ex, 500, "text/plain", "live.html resource missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            page = in.readAllBytes();
        }
        respond(ex, 200, "text/html; charset=utf-8", page);
    }

    private void handleFrames(final HttpExchange ex) throws IOException {
        int since = 0;
        final String query = ex.getRequestURI().getQuery();
        if (query != null && query.startsWith("since=")) {
            try {
                since = Integer.parseInt(query.substring("since=".length()));
            } catch (NumberFormatException ignored) {
                since = 0;
            }
        }
        final byte[] body = buildFramesJson(since).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        respond(ex, 200, "application/json", body);
    }

    /**
     * Parse the map file into frames and serialise those at index ≥ {@code since}
     * as JSON. Re-reads the file per request (O(file)); fine at poll cadence and
     * keeps server state at zero. A frame closes when the next {@code t} line
     * begins (or at EOF); while the run is live the trailing frame is withheld so
     * a half-written frame is never served.
     */
    private String buildFramesJson(final int since) throws IOException {
        int world = 0;
        int sampleEvery = 1;
        final List<Frame> frames = new ArrayList<>();
        Frame cur = null;
        if (Files.exists(mapFile)) {
            for (final String ln : Files.readAllLines(mapFile)) {
                if (ln.isEmpty()) {
                    continue;
                }
                final int sp = ln.indexOf(' ');
                final String tag = sp < 0 ? ln : ln.substring(0, sp);
                final String rest = sp < 0 ? "" : ln.substring(sp + 1);
                switch (tag) {
                    case "world" -> world = parseIntSafe(rest, 0);
                    case "sample-every" -> sampleEvery = parseIntSafe(rest, 1);
                    case "t" -> {
                        if (cur != null && cur.r != null) {
                            frames.add(cur);
                        }
                        cur = new Frame(rest);
                    }
                    case "r" -> {
                        if (cur != null) {
                            cur.r = rest;
                        }
                    }
                    case "u" -> {
                        if (cur != null) {
                            cur.units.add(rest);
                        }
                    }
                    default -> { }
                }
            }
            if (cur != null && cur.r != null) {
                frames.add(cur);
            }
        }
        final int served = done ? frames.size() : Math.max(0, frames.size() - 1);

        final StringBuilder json = new StringBuilder(8192);
        json.append("{\"world\":").append(world)
                .append(",\"sampleEvery\":").append(sampleEvery)
                .append(",\"total\":").append(served)
                .append(",\"done\":").append(done)
                .append(",\"frames\":[");
        final int from = Math.max(0, since);
        for (int i = from; i < served; i++) {
            if (i > from) {
                json.append(',');
            }
            appendFrameJson(json, frames.get(i));
        }
        json.append("]}");
        return json.toString();
    }

    /** One frame: {@code {"t":, "r":"...", "units":[{cell,slot,lineage,gen,energy,genes:[...]}]}}. */
    private void appendFrameJson(final StringBuilder json, final Frame f) {
        json.append("{\"t\":").append(f.t)
                .append(",\"r\":\"").append(f.r).append('"')
                .append(",\"units\":[");
        for (int i = 0; i < f.units.size(); i++) {
            final String[] tok = f.units.get(i).split(" ");
            if (tok.length < 6) {
                continue;
            }
            if (i > 0) {
                json.append(',');
            }
            // u line rest: cell slot lineage gen energy geneCount gene...
            json.append("{\"cell\":").append(tok[0])
                    .append(",\"slot\":").append(tok[1])
                    .append(",\"lineage\":").append(tok[2])
                    .append(",\"gen\":").append(tok[3])
                    .append(",\"energy\":").append(tok[4])
                    .append(",\"genes\":[");
            final int geneCount = parseIntSafe(tok[5], 0);
            for (int g = 0; g < geneCount && 6 + g < tok.length; g++) {
                if (g > 0) {
                    json.append(',');
                }
                json.append(tok[6 + g]);
            }
            json.append("],\"o\":[");
            for (int t = 6 + geneCount, oi = 0; t < tok.length; t++, oi++) {
                if (oi > 0) {
                    json.append(',');
                }
                json.append(jnum(tok[t]));
            }
            json.append("]}");
        }
        json.append("]}");
    }

    /** A frame token as a JSON number, or {@code null} if it is non-finite/unparseable (NaN/Inf are not valid JSON). */
    private static String jnum(final String token) {
        try {
            return Double.isFinite(Double.parseDouble(token)) ? token : "null";
        } catch (NumberFormatException e) {
            return "null";
        }
    }

    private static int parseIntSafe(final String s, final int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void respond(final HttpExchange ex, final int code, final String contentType,
                                final byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static final class Frame {
        final String t;
        String r;
        final List<String> units = new ArrayList<>();

        Frame(final String t) {
            this.t = t;
        }
    }
}
