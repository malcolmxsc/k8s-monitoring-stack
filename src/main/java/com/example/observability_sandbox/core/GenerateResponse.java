package com.example.observability_sandbox.core;

/**
 * Response payload for /generate.
 * Spring will JSON-serialize this automatically.
 */
public record GenerateResponse(
    String response,
    int reqTokens,
    int respTokens,
    boolean cacheHit,
    int latencyMs
) {}
