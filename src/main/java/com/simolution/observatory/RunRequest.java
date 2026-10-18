package com.simolution.observatory;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public record RunRequest(
        String name,
        String configFile,
        Map<String, JsonNode> overrides,
        int founders,
        int genesPerFounder,
        int targetTicks,
        int checkpointEvery
) {}
