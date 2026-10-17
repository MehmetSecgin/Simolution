package com.simolution.kernel.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Strict JSON form of an {@link EngineConfig}. Unknown keys and missing keys are
 * both errors (unknown keys are reported first, so a misspelt key is named), so a typo never silently falls back to a default. Output uses a
 * fixed {@code \n} line separator and record-component order, so
 * {@link #write} is byte-stable and safe to hash.
 */
public final class ConfigJson {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = InflowSource.Disk.class, name = "disk"),
        @JsonSubTypes.Type(value = InflowSource.Rect.class, name = "rect"),
        @JsonSubTypes.Type(value = InflowSource.Points.class, name = "points"),
        @JsonSubTypes.Type(value = InflowSource.Noise.class, name = "noise"),
        @JsonSubTypes.Type(value = InflowSource.Ring.class, name = "ring")
    })
    private interface InflowSourceMixin {

        @JsonIgnore
        boolean isStatic();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .addMixIn(InflowSource.class, InflowSourceMixin.class)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ObjectMapper STRICT_MAPPER = MAPPER.copy()
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);

    private static final DefaultPrettyPrinter PRINTER =
            new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));

    private ConfigJson() {
    }

    public static String write(final EngineConfig config) {
        try {
            return MAPPER.writer(PRINTER).writeValueAsString(config) + "\n";
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("cannot serialize config: " + e.getOriginalMessage(), e);
        }
    }

    public static EngineConfig read(final String json) {
        try {
            MAPPER.readValue(json, EngineConfig.class);
            return STRICT_MAPPER.readValue(json, EngineConfig.class);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("invalid config: " + e.getOriginalMessage(), e);
        }
    }
}
