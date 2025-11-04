package com.example.observability_sandbox.evaluation;

import java.time.Duration;

public record EvaluationResult(
        EvaluationCase evaluationCase,
        String predictedLabel,
        double confidence,
        boolean passed,
        Duration latency,
        String errorMessage
) {
}
