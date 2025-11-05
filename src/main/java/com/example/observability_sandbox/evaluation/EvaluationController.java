package com.example.observability_sandbox.evaluation;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> triggerEvaluation(@RequestParam(name = "trigger", defaultValue = "manual") String trigger) {
        if (!evaluationService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("message", "Evaluation service disabled via configuration"));
        }
        String runId = UUID.randomUUID().toString();
        String effectiveTrigger = StringUtils.hasText(trigger) ? trigger : "manual";
        evaluationService.runBatchAsync(effectiveTrigger + "-" + runId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", runId);
        payload.put("trigger", effectiveTrigger);
        payload.put("status", "scheduled");

        return ResponseEntity.accepted()
                .location(URI.create("/api/evaluations/last"))
                .body(payload);
    }

    @GetMapping("/last")
    public ResponseEntity<EvaluationBatchSummary> lastSummary() {
        EvaluationBatchSummary summary = evaluationService.lastSummary();
        if (summary.total() == 0 && summary.startedAt().equals(java.time.Instant.EPOCH)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(summary);
    }
}
