package com.simolution.observatory;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class Observatory implements AutoCloseable {

    private static final int DEFAULT_PORT = 8090;
    private static final int SEEKER_KERNELS = 4;
    private static final long FRAME_CACHE_BYTES = 512L << 20;
    private static final Map<String, String> TYPES = Map.of("html", "text/html; charset=utf-8", "css",
            "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8", "svg", "image/svg+xml");

    private final HttpServer server;
    private final ExecutorService executor;
    private final RunManager manager;
    private final Seeker seeker;
    private final EventStream events;

    private Observatory(final HttpServer server, final ExecutorService executor, final RunManager manager,
            final Seeker seeker, final EventStream events) {
        this.server = server;
        this.executor = executor;
        this.manager = manager;
        this.seeker = seeker;
        this.events = events;
    }

    public static Observatory start(final Path runs, final Path configs, final int port, final int cores)
            throws IOException {
        Files.createDirectories(runs);
        final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        final RunStore store = new RunStore(runs);
        store.markInterrupted();
        final EventStream events = new EventStream();
        final RunManager manager = new RunManager(store, configs, events, cores);
        final Seeker seeker = new Seeker(Math.max(1, cores / 2), SEEKER_KERNELS);
        final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext("/api/", new Api(store, manager, new FrameReader(FRAME_CACHE_BYTES), seeker, events));
        server.createContext("/", Observatory::resource);
        server.setExecutor(executor);
        server.start();
        return new Observatory(server, executor, manager, seeker, events);
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        events.close();
        manager.close();
        seeker.close();
        executor.shutdownNow();
    }

    private static void resource(final HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        final String name = path.equals("/") ? "index.html" : path.substring(1);
        try (InputStream in =
                name.contains("..") ? null : Observatory.class.getResourceAsStream("/observatory/" + name)) {
            if (in == null) {
                Api.send(exchange, 404, "text/plain; charset=utf-8",
                        ("no file " + path).getBytes(StandardCharsets.UTF_8));
                return;
            }
            final String extension = name.substring(name.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            Api.send(exchange, 200, TYPES.getOrDefault(extension, "application/octet-stream"), in.readAllBytes());
        }
    }

    public static void main(final String[] args) throws IOException {
        int port = DEFAULT_PORT;
        Path runs = Path.of("runs");
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException(args[i] + " needs a value");
            }
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--runs" -> runs = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        final Observatory observatory;
        try {
            observatory = start(runs, Path.of("examples"), port, Runtime.getRuntime().availableProcessors());
        } catch (BindException e) {
            System.err.println("port " + port + " is in use; pass --port");
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(observatory::close));
        System.out.println("Observatory at http://localhost:" + observatory.address().getPort());
    }
}
