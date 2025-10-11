package com.example.observability_sandbox.core;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@RestController
public class GenerateController {

    private final LlmService llmService;
    private final Tracer tracer;

    public GenerateController(LlmService llmService, Tracer tracer) {
        this.llmService = llmService;
        this.tracer = tracer;
    }
    
        @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Region", required = false) String region,
            @RequestHeader(value = "X-Model", required = false) String model
    ) {
        String prompt = String.valueOf(payload.getOrDefault("prompt", "hello"));
        String endpoint = "/generate";
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : "demo-user";
        String effectiveRegion = (region != null && !region.isBlank()) ? region : "us-west-1";
        String effectiveModel = (model != null && !model.isBlank()) ? model : "gpt-4.0";

        MDC.put("endpoint", endpoint);
        MDC.put("userId", effectiveUser);
        MDC.put("region", effectiveRegion);
        MDC.put("model", effectiveModel);

        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("endpoint", endpoint);
            span.tag("userId", effectiveUser);
            span.tag("region", effectiveRegion);
            span.tag("model", effectiveModel);
            
            if (span.context() != null) {
                MDC.put("traceId", span.context().traceId());
                MDC.put("spanId", span.context().spanId());
            }
        }

        try {
            GenerateResponse resp = llmService.generate(prompt, effectiveModel);
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            // Log error with context
            org.slf4j.LoggerFactory.getLogger(GenerateController.class)
                .error("Request failed for model={} user={} region={}: {}", 
                       effectiveModel, effectiveUser, effectiveRegion, e.getMessage());
            return ResponseEntity.status(500)
                .body(new GenerateResponse("Error: " + e.getMessage(), 0, 0, false, 0));
        } finally {
            MDC.clear();
        }
    }
}