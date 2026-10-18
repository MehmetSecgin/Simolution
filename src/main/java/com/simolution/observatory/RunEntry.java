package com.simolution.observatory;

import com.simolution.sim.run.RunManifest;
import com.simolution.sim.run.RunStatus;

public record RunEntry(
        String dir,
        RunManifest manifest,
        RunStatus status,
        long modifiedMillis,
        String unreadable
) {}
