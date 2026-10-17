package com.simolution.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

public final class CheckpointBytes {

    private CheckpointBytes() {}

    public static byte[] of(final Kernel kernel) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CheckpointCodec.write(kernel, bytes);
        return bytes.toByteArray();
    }

    public static Kernel restore(final byte[] bytes, final int threads) throws IOException {
        return CheckpointCodec.read(new ByteArrayInputStream(bytes), threads);
    }
}
