package com.example.observability_sandbox.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record EvaluationBatchSummary(
        Instant startedAt,
        Duration duration,
        int total,
        int passed,
        int failed,
        List<EvaluationResult> results
) {
}
