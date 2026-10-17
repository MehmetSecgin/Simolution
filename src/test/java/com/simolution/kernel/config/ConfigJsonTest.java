package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

class ConfigJsonTest {

    private static final long PRE_REFACTOR_STATE_DIGEST = 0xca21f0260e9bd931L;

    @Test
    @DisplayName("the default config serialises byte for byte as it did before the domain records")
    void defaultsSerialiseExactlyAsBefore() throws IOException {
        // arrange
        final String expected = Files.readString(Path.of("src/test/resources/engine-config-defaults.json"));

        // act
        final String written = ConfigJson.write(EngineConfig.defaults(42L, 100, 32));

        // assert
        assertEquals(expected, written);
    }

    @Test
    @DisplayName("a checkpoint written before the domain records still loads to the same state")
    void checkpointFromBeforeTheRefactorStillLoads() throws IOException {
        // arrange
        final byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/checkpoint-pre-readability.ckpt"));

        // act
        try (Kernel kernel = CheckpointCodec.read(new ByteArrayInputStream(bytes), 1)) {

            // assert
            assertEquals(PRE_REFACTOR_STATE_DIGEST, kernel.stateDigest());
        }
    }

    @Test
    void exampleConfigRoundTrips() throws IOException {
        // arrange
        final EngineConfig first = ConfigJson.read(Files.readString(Path.of("examples/baseline.json")));

        // act
        final EngineConfig second = ConfigJson.read(ConfigJson.write(first));

        // assert
        assertEquals(first, second);
    }
}
