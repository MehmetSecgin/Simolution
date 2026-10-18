package com.simolution.observatory;

import static com.simolution.support.ObservatoryWorld.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simolution.support.ObservatoryWorld;

class ApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int WIDTH = ObservatoryWorld.WORLD;

    @TempDir
    Path temp;

    private Observatory observatory;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        observatory = Observatory.start(Files.createDirectories(temp.resolve("runs")),
                ObservatoryWorld.configs(temp.resolve("configs")), 0, 2);
    }

    @AfterEach
    void stop() {
        observatory.close();
    }

    private URI uri(final String path) {
        return URI.create("http://127.0.0.1:" + observatory.address().getPort() + path);
    }

    private HttpResponse<String> get(final String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> bytes(final String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> send(final String method, final String path, final String body)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(uri(path)).method(method, BodyPublishers.ofString(body)).build(),
                BodyHandlers.ofString());
    }

    private JsonNode json(final HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body());
    }

    private static String runBody(final String name, final int ticks) {
        return "{\"name\":\"" + name + "\",\"configFile\":\"" + ObservatoryWorld.SMALL + "\",\"overrides\":{},"
                + "\"founders\":" + ObservatoryWorld.FOUNDERS + ",\"genesPerFounder\":" + ObservatoryWorld.GENES
                + ",\"targetTicks\":" + ticks + ",\"checkpointEvery\":50}";
    }

    private String create(final String name, final int ticks) throws Exception {
        return json(send("POST", "/api/runs", runBody(name, ticks))).get("dir").asText();
    }

    private void awaitStatus(final String dir, final String status) throws Exception {
        await(() -> json(get("/api/runs/" + dir)).at("/status/status").asText().equals(status));
    }

    private String finishedRun(final String name, final int ticks) throws Exception {
        final String dir = create(name, ticks);
        awaitStatus(dir, "finished");
        return dir;
    }

    @Test
    void everyEndpointAnswersItsHappyPath() throws Exception {
        // arrange
        final String dir = finishedRun("happy", 120);

        // act
        final JsonNode list = json(get("/api/runs"));
        final JsonNode run = json(get("/api/runs/" + dir));
        final JsonNode metrics = json(get("/api/runs/" + dir + "/metrics?points=10"));
        final ByteBuffer frame = ByteBuffer.wrap(bytes("/api/runs/" + dir + "/frame?tick=60").body())
                .order(ByteOrder.LITTLE_ENDIAN);
        final byte[] live = bytes("/api/runs/" + dir + "/frame?tick=live").body();
        final byte[] state = bytes("/api/runs/" + dir + "/state?tick=60").body();
        final int slot = frame.getInt(4 + 4 + WIDTH * WIDTH + 4 + 4);
        final JsonNode unit = json(get("/api/runs/" + dir + "/unit?tick=60&slot=" + slot));
        final JsonNode configs = json(get("/api/configs"));
        final JsonNode config = json(get("/api/configs/" + ObservatoryWorld.SMALL));
        final JsonNode layout = json(get("/api/layout"));
        final String branch = json(send("POST", "/api/runs/" + dir + "/branch",
                "{\"tick\":60,\"overrides\":{\"harvestCapacityPerConnection\":3.0},\"targetTicks\":100,"
                        + "\"name\":\"rich\"}"))
                .get("dir").asText();
        awaitStatus(branch, "finished");
        final JsonNode compare = json(get("/api/compare?runs=" + dir + "," + branch));
        final HttpResponse<String> index = get("/");

        // assert
        assertEquals(dir, list.get(0).get("dir").asText());
        assertTrue(list.get(0).get("peakPopulation").asInt() > 0);
        assertEquals("warn", list.get(0).get("health").asText());
        assertEquals(4, run.get("health").size());
        assertTrue(run.get("moments").size() >= 1);
        assertTrue(run.get("config").has("worldWidth"));
        assertTrue(metrics.get("tick").size() <= 10);
        assertEquals(6, metrics.size());
        assertEquals(60, frame.getInt(0));
        assertEquals(WIDTH, frame.getInt(4));
        assertEquals(frame.capacity(), 4 + 4 + WIDTH * WIDTH + 4 + 12 * frame.getInt(4 + 4 + WIDTH * WIDTH));
        assertEquals(120, ByteBuffer.wrap(live).order(ByteOrder.LITTLE_ENDIAN).getInt(0));
        assertEquals(4 + 4 + 5 * WIDTH * WIDTH, state.length);
        assertEquals(slot, unit.get("slot").asInt());
        assertTrue(unit.get("fields").has("energy"));
        assertEquals(unit.at("/fields/geneCount").asInt(), unit.at("/fields/genes").size());
        assertTrue(unit.has("connections"));
        assertEquals(ObservatoryWorld.SMALL, configs.get(0).asText());
        assertEquals(ObservatoryWorld.WORLD, config.get("worldWidth").asInt());
        assertEquals(24, layout.get("total").asInt());
        assertEquals("ancestor", compare.at("/divergence/kind").asText());
        assertEquals(60, compare.at("/divergence/tick").asInt());
        assertTrue(compare.get("settings").size() > 10);
        assertEquals(5, compare.get("outcomes").size());
        assertEquals(200, index.statusCode());
    }

    @Test
    void errorsAnswer400404And409WithJson() throws Exception {
        // arrange
        final String dir = finishedRun("errs", 60);
        send("POST", "/api/runs/" + dir + "/branch", "{\"tick\":30,\"overrides\":{},\"targetTicks\":60}");

        // act
        final HttpResponse<String> unknownRun = get("/api/runs/nope");
        final HttpResponse<String> unknownSetting = send("POST", "/api/runs",
                runBody("x", 10).replace("\"overrides\":{}", "\"overrides\":{\"bogus\":1}"));
        final HttpResponse<String> badJson = send("POST", "/api/runs", "{");
        final HttpResponse<String> hasBranches = send("DELETE", "/api/runs/" + dir, "");
        final HttpResponse<String> pastEnd = get("/api/runs/" + dir + "/frame?tick=61");
        final HttpResponse<String> resumeFinished = send("POST", "/api/runs/" + dir + "/resume", "");

        // assert
        assertEquals(404, unknownRun.statusCode());
        assertEquals(400, unknownSetting.statusCode());
        assertEquals("unknown setting: bogus", json(unknownSetting).get("error").asText());
        assertEquals(400, badJson.statusCode());
        assertEquals(409, hasBranches.statusCode());
        assertEquals(404, pastEnd.statusCode());
        assertTrue(json(pastEnd).has("error"));
        assertEquals(409, resumeFinished.statusCode());
    }

    @Test
    void doubleCreateAndDoubleBranchReturnOneRun() throws Exception {
        // arrange
        final String dir = finishedRun("twice", 60);
        final String branch = "{\"tick\":30,\"overrides\":{\"seed\":99},\"targetTicks\":60,\"name\":\"b\"}";

        // act
        final JsonNode first = json(send("POST", "/api/runs", runBody("twice", 60)));
        final JsonNode b1 = json(send("POST", "/api/runs/" + dir + "/branch", branch));
        final JsonNode b2 = json(send("POST", "/api/runs/" + dir + "/branch", branch));

        // assert
        assertEquals(dir, first.get("dir").asText());
        assertTrue(first.get("existing").asBoolean());
        assertFalse(b1.get("existing").asBoolean());
        assertTrue(b2.get("existing").asBoolean());
        assertEquals(b1.get("dir"), b2.get("dir"));
        assertEquals(2, json(get("/api/runs")).size());
    }

    @Test
    void rerunTurnsDeterminismOk() throws Exception {
        // arrange
        final String dir = finishedRun("orig", 120);

        // act
        final String twin = json(send("POST", "/api/runs/" + dir + "/rerun", "")).get("dir").asText();
        awaitStatus(twin, "finished");

        // assert
        final JsonNode health = json(get("/api/runs/" + dir)).get("health");
        final JsonNode determinism = health.get(3);
        assertEquals("Determinism", determinism.get("name").asText());
        assertEquals("ok", determinism.get("level").asText());
        assertEquals("twin", json(get("/api/compare?runs=" + dir + "," + twin)).at("/divergence/kind").asText());
    }

    @Test
    void branchChangingTheWorldShapeIs400() throws Exception {
        // arrange
        final String dir = finishedRun("shape", 60);

        // act
        final HttpResponse<String> response = send("POST", "/api/runs/" + dir + "/branch",
                "{\"tick\":30,\"overrides\":{\"worldWidth\":64},\"targetTicks\":60}");

        // assert
        assertEquals(400, response.statusCode());
        assertEquals("a branch cannot change worldWidth (32 -> 64)", json(response).get("error").asText());
    }

    @Test
    void eventStreamDeliversFinished() throws Exception {
        // arrange
        final CompletableFuture<HttpResponse<Stream<String>>> stream = client.sendAsync(
                HttpRequest.newBuilder(uri("/api/events")).build(), BodyHandlers.ofLines());
        final HttpResponse<Stream<String>> response = stream.get();

        // act
        final String dir = create("events", 60);

        // assert
        assertTrue(response.headers().firstValue("content-type").orElseThrow().startsWith("text/event-stream"));
        assertTrue(response.body().anyMatch(line -> line.startsWith("data: ") && line.contains(dir)
                && line.contains("\"finished\"")));
    }

    @Test
    void serverBindsToLoopbackOnly() {
        assertEquals(InetAddress.getLoopbackAddress(), observatory.address().getAddress());
    }
}
