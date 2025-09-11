package com.example.observability_sandbox.core;

import java.util.Random;

import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

// Simulated LLM service with metrics
@Service
public class LlmService {
    private final Counter reqCounter;
    private final Counter reqTokens;
    private final Counter respTokens;
    private final AtomicDouble cacheHitRatio;
    private final Random rnd = new Random();

    // Constructor to initialize metrics
    public LlmService(MeterRegistry registry) {
        this.reqCounter = Counter.builder("llm.requests.total")
                .description("Total number of LLM requests")
                .register(registry);
        // Request tokens counter
        this.reqTokens = Counter.builder("llm.request.tokens.total")
                .description("Total number of request tokens sent to LLM")
                .register(registry);

        // Response tokens counter
        this.respTokens = Counter.builder("llm.response.tokens.total")
                .description("Total number of response tokens received from LLM")
                .register(registry);

        // Cache hit ratio gauge
        this.cacheHitRatio = new AtomicDouble(0.0);
        Gauge.builder("llm.cache.hit.ratio", cacheHitRatio, AtomicDouble::get)
                .description("Cache hit ratio for LLM requests")
                .register(registry);
    }

    public Result generate(String prompt) throws InterruptedException {
        int promptTokens = prompt == null ? 0 : Math.max(1, prompt.length() / 6);
        boolean cacheHit = rnd.nextDouble() < 0.25; // 25% cache hit rate

        // Simulate token counts
        int responseTokens = cacheHit ? Math.max(8, promptTokens / 2) : Math.max(16, promptTokens);

        // Simulate latency for Grafana SLOs
        long latency = cacheHit ? 80L + rnd.nextInt(40) : 300L + rnd.nextInt(500);
        Thread.sleep(latency);

        // Update metrics for prometheus
        reqCounter.increment();
        reqTokens.increment(promptTokens);
        respTokens.increment(responseTokens);

        cacheHitRatio.set(cacheHit ? 1.0 : 0.0);
        
        String completion = cacheHit ? "Cached completion" : "Generated completion";

        
        return new Result(completion, promptTokens, responseTokens, cacheHit);
    }
    // Simple record to hold result data
    public record Result(String completion, int promptTokens, int responseTokens, boolean cacheHit) {}
}