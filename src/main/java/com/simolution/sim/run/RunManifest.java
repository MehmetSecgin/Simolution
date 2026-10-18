package com.simolution.sim.run;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public record RunManifest(
        String runId,
        String engine,
        String name,
        String parent,
        Integer branchTick,
        String twinOf,
        int targetTicks,
        int checkpointEvery,
        int founders,
        int genesPerFounder
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RunManifest read(final Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), RunManifest.class);
    }

    public void write(final Path file) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), this);
    }
}
