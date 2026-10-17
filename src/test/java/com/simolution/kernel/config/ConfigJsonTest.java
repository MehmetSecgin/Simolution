package com.simolution.kernel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

class ConfigJsonTest {

    private static final long PRE_REFACTOR_STATE_DIGEST = 0xca21f0260e9bd931L;

    private static byte[] resource(final String name) throws IOException {
        try (InputStream in = ConfigJsonTest.class.getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("the default config serialises byte for byte as it did before the domain records")
    void defaultsSerialiseExactlyAsBefore() throws IOException {
        // arrange
        final String expected = new String(resource("/engine-config-defaults.json"), StandardCharsets.UTF_8);

        // act
        final String written = ConfigJson.write(EngineConfig.defaults(42L, 100, 32));

        // assert
        assertEquals(expected, written);
    }

    @Test
    @DisplayName("a checkpoint written before the domain records still loads to the same state")
    void checkpointFromBeforeTheRefactorStillLoads() throws IOException {
        // arrange
        final byte[] bytes = resource("/checkpoint-pre-readability.ckpt");

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

    private static String withoutKey(final String json, final String key) {
        return json.replaceAll("\\s*\"" + key + "\"[^\\n]*\\n", "\n");
    }

    private static String withValue(final String json, final String key, final String value) {
        return json.replaceAll("\"" + key + "\"\\s*:\\s*[^,\\n]*", "\"" + key + "\" : " + value);
    }

    private static String messageOf(final String json) {
        return assertThrows(IllegalArgumentException.class, () -> ConfigJson.read(json)).getMessage();
    }

    @Test
    void unknownKeyIsNamedInTheMessage() {
        // arrange
        final String json =
                ConfigJson.write(EngineConfig.defaults(1L, 10, 8)).replace("\"basalCost\"", "\"basalCostt\"");

        // act
        final String message = messageOf(json);

        // assert
        assertTrue(message.startsWith("invalid config: Unrecognized field \"basalCostt\""), message);
    }

    @Test
    void missingWorldWidthReportsItsDefaultedValue() {
        // arrange
        final String json = withoutKey(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)), "worldWidth");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: worldWidth must be at least 1, got 0", message);
    }

    @Test
    void missingPositiveKeyReportsItsDefaultedValue() {
        // arrange
        final String json = withoutKey(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)), "selfEnergyScale");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: selfEnergyScale must be finite and above 0, got 0.0", message);
    }

    @Test
    void missingKeyWithAValidDefaultIsReportedAsMissing() {
        // arrange
        final String json = withoutKey(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)), "growYield");

        // act
        final String message = messageOf(json);

        // assert
        assertTrue(message.startsWith("invalid config: Missing creator property 'growYield'"), message);
    }

    @Test
    void invalidChemicalIsReportedBeforeInvalidScalar() {
        // arrange
        final String json = withValue(withValue(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)),
                "capacity", "0.0"), "basalCost", "-1.0");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: chemicals[0].capacity must be finite and above 0, got 0.0", message);
    }

    @Test
    void invalidWorldIsReportedBeforeInvalidChemical() {
        // arrange
        final String json = withValue(withValue(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)),
                "capacity", "0.0"), "worldWidth", "0");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: worldWidth must be at least 1, got 0", message);
    }

    @Test
    void missingInflowSourcesIsNamedInTheMessage() {
        // arrange
        final String json = withoutKey(ConfigJson.write(EngineConfig.defaults(1L, 10, 8)), "sources");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: Missing creator property 'sources' (index 1); "
                + "`DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES` enabled", message);
    }

    @Test
    void nullInflowSourcesIsNamedInTheMessage() {
        // arrange
        final String json = ConfigJson.write(EngineConfig.defaults(1L, 10, 8)).replaceAll("\"sources\"\\s*:\\s*\\[ \\]",
                "\"sources\" : null");

        // act
        final String message = messageOf(json);

        // assert
        assertEquals("invalid config: Null value for creator property 'sources' (index 1); "
                + "`DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES` enabled", message);
    }
}
