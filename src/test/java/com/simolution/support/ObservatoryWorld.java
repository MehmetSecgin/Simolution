package com.simolution.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;
import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.config.Founder;
import com.simolution.observatory.RunRequest;

public final class ObservatoryWorld {

    public static final String SMALL = "small.json";
    public static final String STARVING = "starving.json";
    public static final int FOUNDERS = 60;
    public static final int GENES = 16;
    public static final int WORLD = 32;
    public static final long SEED = 7L;

    private static final long TIMEOUT_MILLIS = 60_000;

    private ObservatoryWorld() {}

    public static Path configs(final Path dir) throws IOException {
        Files.createDirectories(dir);
        final EngineConfig small = EngineConfig.defaults(SEED, WORLD, GENES * 2);
        Files.writeString(dir.resolve(SMALL), ConfigJson.write(small));
        final EngineConfig starving = new EngineConfig(small.seed(), small.world(), small.chemicals(),
                new Founder(0.5, 1.0), small.metabolism(), small.harvest(), small.motility(), small.growth(),
                small.sensing(), small.reproduction(), small.mutation());
        Files.writeString(dir.resolve(STARVING), ConfigJson.write(starving));
        return dir;
    }

    public static RunRequest request(final String name, final int ticks) {
        return new RunRequest(name, SMALL, Map.of(), FOUNDERS, GENES, ticks, 100);
    }

    public static RunRequest request(final String name, final int ticks, final Map<String, JsonNode> overrides) {
        return new RunRequest(name, SMALL, overrides, FOUNDERS, GENES, ticks, 100);
    }

    public static void await(final Callable<Boolean> done) throws Exception {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!done.call()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting");
            }
            Thread.sleep(20);
        }
    }
}
