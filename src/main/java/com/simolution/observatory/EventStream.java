package com.simolution.observatory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

public class EventStream implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<HttpExchange> clients = new CopyOnWriteArrayList<>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());

    public void open(final HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
        clients.add(exchange);
    }

    public void send(final String event, final Object payload) {
        final byte[] message;
        try {
            message = ("event: " + event + "\ndata: " + JSON.writeValueAsString(payload) + "\n\n")
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("cannot encode event " + event, e);
        }
        sender.execute(() -> {
            for (HttpExchange client : clients) {
                try {
                    final OutputStream out = client.getResponseBody();
                    out.write(message);
                    out.flush();
                } catch (IOException gone) {
                    clients.remove(client);
                    client.close();
                }
            }
        });
    }

    @Override
    public void close() {
        sender.shutdownNow();
        clients.forEach(HttpExchange::close);
        clients.clear();
    }
}
