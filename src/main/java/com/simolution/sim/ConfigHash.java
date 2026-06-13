package com.simolution.sim;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simolution.kernel.config.KernelConfig;

/**
 * Stable fingerprint of every {@link KernelConfig} constant (report-v8 manifest
 * {@code configHash}). A replay is only valid under the same physical constants
 * it was recorded with; storing this hash lets {@link Replayer} refuse a silent
 * replay against a changed build instead of fabricating a wrong "past".
 * <p>
 * Computed by reflecting over all {@code public static final} fields of
 * {@code KernelConfig}, sorted by name (so field-declaration order never moves
 * the hash) into {@code name=value} lines, then SHA-256. Reflection is a
 * one-time setup cost, never on any tick path. Adding or retuning a constant
 * changes the hash automatically — there is no hand-maintained list to drift.
 */
public final class ConfigHash {

    private ConfigHash() {}

    public static String compute() {
        final List<String> lines = new ArrayList<>();
        for (final Field f : KernelConfig.class.getDeclaredFields()) {
            final int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isFinal(m) || !Modifier.isPublic(m)) {
                continue;
            }
            try {
                lines.add(f.getName() + "=" + f.get(null));
            } catch (final IllegalAccessException e) {
                throw new IllegalStateException("cannot read KernelConfig." + f.getName(), e);
            }
        }
        Collections.sort(lines);
        final String joined = String.join("\n", lines);
        try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            final byte[] digest = sha.digest(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
