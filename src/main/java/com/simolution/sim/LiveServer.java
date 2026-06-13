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
 * A tiny live viewer server (report-v7, live mode). Serves a player page and a
 * frames endpoint that <b>tails the streamed {@code .map.txt}</b> — so the run
 * can be watched in the browser as it ticks, then scrubbed once it ends. The
 * frames live on disk (written by {@link MapFrameWriter}); the server only reads
 * the file on request and holds nothing per-tick in memory, so the memory
 * doctrine is preserved (footprint constant in tick count). JDK built-in
 * {@code com.sun.net.httpserver} — no dependency.
 * <ul>
 *   <li>{@code GET /} → the live player page (classpath resource live.html).</li>
 *   <li>{@code GET /frames?since=N} → JSON {@code {world, sampleEvery, done,
 *       frames:[{t,r,o}, ...]}} for frames at index ≥ N. Only <em>complete</em>
 *       frames (t+r+o all present) are returned, so a partially-written trailing
 *       frame is never served.</li>
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

    /** Mark the run finished so the player stops polling. */
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
     * Parse the map file into complete frames and serialise those at index ≥
     * {@code since} as JSON. Re-reads the file per request (O(frames)); fine at
     * poll cadence and keeps server state at zero.
     */
    private String buildFramesJson(final int since) throws IOException {
        int world = 0;
        int sampleEvery = 1;
        final List<String[]> frames = new ArrayList<>();
        String t = null;
        String r = null;
        if (Files.exists(mapFile)) {
            for (final String line : Files.readAllLines(mapFile)) {
                if (line.isEmpty()) {
                    continue;
                }
                final int sp = line.indexOf(' ');
                final String tag = sp < 0 ? line : line.substring(0, sp);
                final String rest = sp < 0 ? "" : line.substring(sp + 1);
                switch (tag) {
                    case "world" -> world = parseIntSafe(rest, 0);
                    case "sample-every" -> sampleEvery = parseIntSafe(rest, 1);
                    case "t" -> {
                        t = rest;
                        r = null;
                    }
                    case "r" -> r = rest;
                    case "o" -> {
                        if (t != null && r != null) {
                            frames.add(new String[] {t, r, rest});
                        }
                        t = null;
                        r = null;
                    }
                    default -> { }
                }
            }
        }

        final StringBuilder json = new StringBuilder(4096);
        json.append("{\"world\":").append(world)
                .append(",\"sampleEvery\":").append(sampleEvery)
                .append(",\"total\":").append(frames.size())
                .append(",\"done\":").append(done)
                .append(",\"frames\":[");
        final int from = Math.max(0, since);
        for (int i = from; i < frames.size(); i++) {
            final String[] f = frames.get(i);
            if (i > from) {
                json.append(',');
            }
            json.append("{\"t\":").append(f[0])
                    .append(",\"r\":\"").append(f[1]).append('"')
                    .append(",\"o\":[");
            final String occ = f[2];
            boolean first = true;
            if (!occ.isEmpty()) {
                for (final String token : occ.split(" ")) {
                    final int colon = token.indexOf(':');
                    if (colon < 0) {
                        continue;
                    }
                    if (!first) {
                        json.append(',');
                    }
                    first = false;
                    json.append('[').append(token, 0, colon)
                            .append(',').append(token.substring(colon + 1)).append(']');
                }
            }
            json.append("]}");
        }
        json.append("]}");
        return json.toString();
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
}
