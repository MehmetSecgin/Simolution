package com.simolution.sim.run;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import com.simolution.kernel.config.EngineConfig;
import com.simolution.kernel.runtime.Kernel;
import com.simolution.kernel.state.CheckpointCodec;

public final class Branching {

    private Branching() {}

    public static Kernel start(final Path parentDir, final int tick, final EngineConfig newConfig, final int threads)
            throws IOException {
        final ByteArrayOutputStream checkpoint = new ByteArrayOutputStream();
        try (Kernel parent = RunDirectoryReader.seek(parentDir, tick, threads)) {
            CheckpointCodec.write(parent, checkpoint);
        }
        return CheckpointCodec.readBranch(new ByteArrayInputStream(checkpoint.toByteArray()), threads, newConfig);
    }
}
