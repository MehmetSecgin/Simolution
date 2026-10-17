package com.simolution.sim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;

/**
 * Stable fingerprint of an {@link EngineConfig} (report-v8 manifest {@code configHash}):
 * SHA-256 of its canonical {@link ConfigJson#write JSON}. A replay is only valid under
 * the same physical constants it was recorded with; {@link Replayer} compares this hash
 * against the manifest's instead of fabricating a wrong "past".
 */
public final class ConfigHash {

    private ConfigHash() {}

    public static String compute(final EngineConfig config) {
        try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            final byte[] digest = sha.digest(ConfigJson.write(config).getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
