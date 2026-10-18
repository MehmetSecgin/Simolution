package com.simolution.sim.run;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

public record RunStatus(
        RunState status,
        int ticksDone,
        String finalDigest,
        String error
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RunStatus read(final Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), RunStatus.class);
    }

    public void write(final Path file) throws IOException {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), this);
    }
}
