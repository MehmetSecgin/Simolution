package com.simolution.kernel;

/**
 * The one engine version constant. The report header, every checkpoint and every
 * {@code run.json} read it, and a checkpoint is refused by an engine whose id differs.
 */
public final class EngineVersion {

    public static final String ID = "v6";

    private EngineVersion() {}
}
