package com.example.observability_sandbox.core;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;


@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String SERVICE_TAG = "service";
    private static final String SERVICE_NAME = "los-app";
    private final Tracer tracer;
    private final Random random = new Random();
    private final DistributionSummary reqTokensSummary;
    private final DistributionSummary respTokensSummary;
    private final MeterRegistry registry;

    public LlmService(Tracer tracer, MeterRegistry registry) {
        this.tracer = tracer;
        this.registry = registry;
        // Note: promptsTotal and errorsTotal are NOT pre-registered to allow dynamic tags (model, error_type)
        this.reqTokensSummary = DistributionSummary.builder("llm_request_tokens")
                .description("Number of tokens in LLM requests")
                .baseUnit("tokens")
                .tag(SERVICE_TAG, SERVICE_NAME)
                .publishPercentiles(0.5, 0.9, 0.95)
                .register(registry);
        this.respTokensSummary = DistributionSummary.builder("llm_response_tokens")
                .description("Number of tokens in LLM responses")
                .baseUnit("tokens")
                .tag(SERVICE_TAG, SERVICE_NAME)
                .publishPercentiles(0.5, 0.9, 0.95)
                .register(registry);
    }

    public GenerateResponse generate(String prompt, String model) {
        Span span = tracer.nextSpan().name("LlmService.generate").start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            String endpoint = MDC.get("endpoint");
            String userId = MDC.get("userId");
            String region = MDC.get("region");
            String effectiveModel = (model != null && !model.isBlank()) ? model : MDC.get("model");

            if (endpoint != null) span.tag("endpoint", endpoint);
            if (userId != null) span.tag("userId", userId);
            if (region != null) span.tag("region", region);
            if (effectiveModel != null) span.tag("model", effectiveModel);

            int reqTokens = prompt.length() / 4 + 1;
            int respTokens = 10 + random.nextInt(90);
            int latency = 100 + random.nextInt(900);

            // Simulate model-specific error rates for realistic observability
            double errorRate = getModelErrorRate(effectiveModel);
            boolean shouldFail = random.nextDouble() < errorRate;

            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                span.error(e);
                Thread.currentThread().interrupt();
                incrementErrorCounter(effectiveModel, "interrupted");
                throw new RuntimeException(e);
            }

            if (shouldFail) {
                String errorType = simulateModelError(effectiveModel);
                span.tag("error", "true");
                span.tag("error.type", errorType);
                log.error("generate_error model={} prompt_len={} error_type={} latency_ms={}",
                         effectiveModel, prompt.length(), errorType, latency);
                incrementErrorCounter(effectiveModel, errorType);
                throw new RuntimeException("LLM Error [" + effectiveModel + "]: " + errorType);
            }

            log.info("generate_ok model={} prompt_len={} req_tokens={} resp_tokens={} cache_hit={} latency_ms={}",
                     effectiveModel, prompt.length(), reqTokens, respTokens, false, latency);

            span.tag("req.tokens", String.valueOf(reqTokens));
            span.tag("resp.tokens", String.valueOf(respTokens));
            span.tag("latency.ms", String.valueOf(latency));
            span.tag("cache.hit", "false");
            span.tag("error", "false");

            // Increment success counter with model tag
            String modelTag = effectiveModel != null ? effectiveModel : "unknown";
            String regionTag = region != null ? region : "unknown";
            
            // Successful prompts by model (for dashboard)
            registry.counter("llm_prompts_success_total",
                    "model", modelTag,
                    "region", regionTag,
                    SERVICE_TAG, SERVICE_NAME)
                    .increment();
            
            reqTokensSummary.record(reqTokens);
            respTokensSummary.record(respTokens);

            return new GenerateResponse("Generated response for: " + prompt, reqTokens, respTokens, false, latency);
        } finally {
            span.end();
        }
    }

    /**
     * Returns error rate for each model (simulates real-world reliability differences)
     * 
     * NOTE: Error rates increased for demonstration purposes to populate dashboard charts.
     * Production rates would typically be: 0.5-2% for premium models, 2-5% for standard models.
     * These elevated rates (15-35%) are intentionally high to showcase observability features.
     */
    private double getModelErrorRate(String model) {
        if (model == null) return 0.22;  // 22% for unknown (demo purposes)
        return switch (model) {
            case "gpt-4.0", "gpt-4o" -> 0.18;           // Slightly higher for testing
            case "claude-3.5-sonnet", "claude-3-opus" -> 0.21;
            case "gemini-2.0-flash" -> 0.27;
            case "gemini-1.5-pro" -> 0.30;
            case "gpt-3.5-turbo" -> 0.34;
            case "llama-3.3-70b" -> 0.38;
            default -> 0.22;
        };
    }

    /**
     * Simulates realistic error types for different models
     */
    private String simulateModelError(String model) {
        String[] errors = {"rate_limit", "timeout", "context_length_exceeded", "content_filter", "service_unavailable"};
        // Different models have different common error patterns
        if (model != null && (model.contains("llama") || model.contains("gemini-1.5"))) {
            // Open source and older models timeout more
            return random.nextDouble() < 0.5 ? "timeout" : errors[random.nextInt(errors.length)];
        }
        return errors[random.nextInt(errors.length)];
    }

    /**
     * Records error metrics with model and error type tags
     */
    private void incrementErrorCounter(String model, String errorType) {
        String modelTag = model != null ? model : "unknown";
        String regionTag = MDC.get("region") != null ? MDC.get("region") : "unknown";
        registry.counter("llm_errors_total",
                "model", modelTag,
                "error_type", errorType,
                "region", regionTag,
                SERVICE_TAG, SERVICE_NAME)
                .increment();
    }
}
