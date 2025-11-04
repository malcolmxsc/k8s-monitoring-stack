package com.example.observability_sandbox.evaluation;

public record EvaluationCase(String id, String prompt, String expectedLabel) {

    public EvaluationCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (expectedLabel == null || expectedLabel.isBlank()) {
            throw new IllegalArgumentException("expectedLabel must not be blank");
        }
    }
}
