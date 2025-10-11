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
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Region", required = false) String region
    ) {
        String prompt = String.valueOf(body.getOrDefault("prompt", "hello"));
        String endpoint = "/generate";
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : "demo-user";
        String effectiveRegion = (region != null && !region.isBlank()) ? region : "us-west-1";

        // Put useful context into logs
        MDC.put("endpoint", endpoint);
        MDC.put("userId", effectiveUser);
        MDC.put("region", effectiveRegion);

        // Get the CURRENT span created by Spring Boot's auto-instrumentation
        Span span = tracer.currentSpan();

        // Add custom tags to the existing HTTP span
        if (span != null) {
            span.tag("endpoint", endpoint);
            span.tag("userId", effectiveUser);
            span.tag("region", effectiveRegion);
            
            // Ensure logs include trace identifiers
            if (span.context() != null) {
                MDC.put("traceId", span.context().traceId());
                MDC.put("spanId", span.context().spanId());
            }
        }

        try {
            GenerateResponse resp = llmService.generate(prompt);
            return ResponseEntity.ok(resp);
        } finally {
            // Clean MDC so threads don't leak context
            // Don't call span.end() - let Spring Boot's instrumentation handle that
            MDC.clear();
        }
    }
}