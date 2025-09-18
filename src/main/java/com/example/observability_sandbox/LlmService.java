package com.example.observability_sandbox;

import java.util.Random;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;


@Service
public class LlmService {
    private static final String SERVICE_TAG = "service";
    private static final String SERVICE_NAME = "los-app";
    private final Tracer tracer;
    private final Random random = new Random();

    // New custom metrics
    private final Counter promptsTotal;
    private final DistributionSummary reqTokensSummary;
    private final DistributionSummary respTokensSummary;

    public LlmService(Tracer tracer, MeterRegistry registry) {
        this.tracer = tracer;
        // Initialize custom metrics
        this.promptsTotal = Counter.builder("llm.prompts.total")
                .description("Total number of prompts processed")
                .tag(SERVICE_TAG, SERVICE_NAME)
                .register(registry);
        this.reqTokensSummary = DistributionSummary.builder("llm.request.tokens")
                .description("Number of tokens in LLM requests")
                .baseUnit("tokens")
                .tag(SERVICE_TAG, SERVICE_NAME)
                .publishPercentiles(0.5, 0.9, 0.95) // p50/p90/p95 in Prometheus
                .register(registry);
        this.respTokensSummary = DistributionSummary.builder("llm.response.tokens")
                .description("Number of tokens in LLM responses")
                .baseUnit("tokens")
                .tag(SERVICE_TAG, SERVICE_NAME)

                .publishPercentiles(0.5, 0.9, 0.95) // p50/p90/p95 in Prometheus
                .register(registry);
    }

    public GenerateResponse generate(String prompt) {
        // Start a new span for the LLM call
        Span span = tracer.nextSpan().name("llm.generate");
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            // Simulate variable latency (100-500ms)
            int latency = 100 + random.nextInt(400);
            // Simulate token counts
            int reqTokens = prompt.length() / 4 + 1; // Roughly 4 chars per token
            int respTokens = 20 + random.nextInt(60); // Random response length between 5-25 tokens
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                span.error(e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("LLM generation interrupted", e);
            }
            // Update custom metrics
            promptsTotal.increment();
            reqTokensSummary.record(reqTokens);
            respTokensSummary.record(respTokens);

            // Add relevant tags to the span
            span.tag("llm.prompt.length", String.valueOf(prompt.length()));
            span.tag("llm.request.tokens", String.valueOf(reqTokens));
            span.tag("llm.response.tokens", String.valueOf(respTokens));
            span.tag("llm.latency.ms", String.valueOf(latency));

            // Simulate a response
            return new GenerateResponse("Generated response for:  " + prompt,reqTokens, respTokens, false, latency);
            } finally {
                span.end();
               
        }
    }
}