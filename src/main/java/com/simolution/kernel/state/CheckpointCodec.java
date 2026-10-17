package com.simolution.kernel.state;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.simolution.kernel.EngineVersion;
import com.simolution.kernel.config.ConfigJson;
import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.genome.SlotCompiler;
import com.simolution.kernel.layout.NodeLayout;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.FieldSpec.Persist;

/**
 * Self-sufficient, versioned checkpoint of a {@link Kernel} (design §8). Big-endian layout:
 * magic {@code "SIMO"}, {@code int formatVersion}, engine version, config JSON, node layout
 * JSON, the saved scalars (tick, energy sink, cumulative inflow, births, max generation,
 * credited initial energy and mass, initial resource total), then every
 * {@link Persist#SAVED} field of {@link WorldState#fields()} in declared order, each as an
 * {@code int} length followed by its elements.
 * <p>
 * The three strings are {@code int} byte length plus UTF-8 (not {@code writeUTF}, whose
 * 64 KiB cap a config with many inflow cells can exceed). Structure-derived fields are
 * rebuilt from the genes by {@link SlotCompiler}; scratch is never written. No thread count
 * is stored, so checkpoint bytes are a pure function of the simulation state.
 * <p>
 * Every refusal is an {@link IllegalStateException} naming its cause.
 */
public final class CheckpointCodec {

    public static final int FORMAT_VERSION = 1;

    private static final byte[] MAGIC = {'S', 'I', 'M', 'O'};
    private static final int MAX_STRING_BYTES = 1 << 26;

    private CheckpointCodec() {}

    public static void write(final Kernel kernel, final OutputStream out) throws IOException {
        final DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out));
        final WorldState state = kernel.state();
        data.write(MAGIC);
        data.writeInt(FORMAT_VERSION);
        writeString(data, EngineVersion.ID);
        writeString(data, ConfigJson.write(kernel.config()));
        writeString(data, NodeLayout.toJson());
        data.writeInt(state.tick);
        data.writeDouble(state.energySink);
        data.writeDouble(state.cumulativeInflow);
        data.writeLong(state.birthsTotal);
        data.writeInt(state.maxGeneration);
        data.writeDouble(state.creditedInitialEnergy);
        data.writeDouble(state.creditedInitialMass);
        data.writeDouble(state.initialResourceTotal);
        for (final FieldSpec spec : state.fields()) {
            if (spec.persist() == Persist.SAVED) {
                writeArray(data, spec);
            }
        }
        data.flush();
    }

    public static Kernel read(final InputStream in) throws IOException {
        return read(in, null);
    }

    /**
     * Restores the checkpoint under {@code override} instead of the config it was written with.
     * Refused unless the override keeps every shape-defining value ({@link EngineConfig#branchCompatible}).
     */
    public static Kernel readBranch(final InputStream in, final EngineConfig override) throws IOException {
        return read(in, override);
    }

    private static Kernel read(final InputStream in, final EngineConfig override) throws IOException {
        final DataInputStream data = new DataInputStream(new BufferedInputStream(in));
        try {
            final byte[] magic = new byte[MAGIC.length];
            data.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalStateException("not a checkpoint: bad magic");
            }
            final int format = data.readInt();
            if (format != FORMAT_VERSION) {
                throw new IllegalStateException(
                        "unsupported checkpoint format version " + format + ", this engine reads " + FORMAT_VERSION);
            }
            final String engine = readString(data);
            if (!engine.equals(EngineVersion.ID)) {
                throw new IllegalStateException(
                        "checkpoint engine version " + engine + " differs from running engine " + EngineVersion.ID);
            }
            final EngineConfig written = ConfigJson.read(readString(data));
            final String layout = readString(data);
            if (!layout.equals(NodeLayout.toJson())) {
                throw new IllegalStateException("checkpoint node layout differs from this engine's node layout");
            }
            if (override != null && !written.branchCompatible(override)) {
                throw new IllegalStateException(
                        "branch config changes the state shape (world width, max genes or chemical count)");
            }
            final Kernel kernel = new Kernel(new int[0][], new int[0], override == null ? written : override);
            readState(data, kernel.state());
            return kernel;
        } catch (final EOFException e) {
            throw new IllegalStateException("checkpoint truncated", e);
        }
    }

    private static void readState(final DataInputStream data, final WorldState state) throws IOException {
        state.tick = data.readInt();
        state.energySink = data.readDouble();
        state.cumulativeInflow = data.readDouble();
        state.birthsTotal = data.readLong();
        state.maxGeneration = data.readInt();
        state.creditedInitialEnergy = data.readDouble();
        state.creditedInitialMass = data.readDouble();
        state.initialResourceTotal = data.readDouble();
        for (final FieldSpec spec : state.fields()) {
            if (spec.persist() == Persist.SAVED) {
                readArray(data, spec);
            }
        }
        for (int slot = 0; slot < state.unitCount; slot++) {
            SlotCompiler.compile(state, slot);
        }
    }

    private static void writeString(final DataOutputStream out, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IllegalStateException("corrupt checkpoint: string length " + length);
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeArray(final DataOutputStream out, final FieldSpec spec) throws IOException {
        switch (spec.array()) {
            case int[] a -> {
                out.writeInt(a.length);
                for (final int v : a) {
                    out.writeInt(v);
                }
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (final long v : a) {
                    out.writeLong(v);
                }
            }
            case float[] a -> {
                out.writeInt(a.length);
                for (final float v : a) {
                    out.writeFloat(v);
                }
            }
            case double[] a -> {
                out.writeInt(a.length);
                for (final double v : a) {
                    out.writeDouble(v);
                }
            }
            default -> throw new IllegalStateException("cannot save field " + spec.name());
        }
    }

    private static void readArray(final DataInputStream in, final FieldSpec spec) throws IOException {
        final int length = in.readInt();
        switch (spec.array()) {
            case int[] a -> {
                expectLength(spec, length, a.length);
                for (int i = 0; i < length; i++) {
                    a[i] = in.readInt();
                }
            }
            case long[] a -> {
                expectLength(spec, length, a.length);
                for (int i = 0; i < length; i++) {
                    a[i] = in.readLong();
                }
            }
            case float[] a -> {
                expectLength(spec, length, a.length);
                for (int i = 0; i < length; i++) {
                    a[i] = in.readFloat();
                }
            }
            case double[] a -> {
                expectLength(spec, length, a.length);
                for (int i = 0; i < length; i++) {
                    a[i] = in.readDouble();
                }
            }
            default -> throw new IllegalStateException("cannot load field " + spec.name());
        }
    }

    private static void expectLength(final FieldSpec spec, final int found, final int expected) {
        if (found != expected) {
            throw new IllegalStateException(
                    "corrupt checkpoint: field " + spec.name() + " has " + found + " entries, expected " + expected);
        }
    }
}
