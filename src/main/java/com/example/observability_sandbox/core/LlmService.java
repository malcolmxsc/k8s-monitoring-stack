package com.example.observability_sandbox.core;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;


@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
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

    
    public GenerateResponse generate(String prompt){
        // start a dedicated span for the LLM call
        Span span = tracer.nextSpan().name("LlmService.generate").start();
        long start = System.nanoTime();

        try(Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // pull request info from the current span and put it in MDC for logging
            String endpoint = MDC.get("endpoint");
            String userId = MDC.get("userId");
            String region = MDC.get("region");

            // tag those attributes on this span too.
            if(endpoint != null) span.tag("endpoint", endpoint);
            if(userId != null) span.tag("userId", userId);
            if(region != null) span.tag("region", region);

            // -- simulate doing the LLM call --
            int reqTokens = prompt.length() / 4 + 1;    // rough estimate
            int respTokens = 10 + random.nextInt(90);  // random response length
            int latency = 100 + random.nextInt(900);   // 100ms to 1s

            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                span.error(e);
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            // Emit a single-line request log that Alloy/Loki can match
            log.info("generate_ok prompt_len={} req_tokens={} resp_tokens={} cache_hit={} latency_ms={}",
                     prompt.length(), reqTokens, respTokens, false, latency);

                // tag useful service-level metrics
                span.tag("req.tokens", String.valueOf(reqTokens));
                span.tag("resp.tokens", String.valueOf(respTokens));
                span.tag("latency.ms", String.valueOf(latency));
                span.tag("cache.hit", "false");

                // record app-level metrics
                promptsTotal.increment();
                reqTokensSummary.record(reqTokens);
                respTokensSummary.record(respTokens);

                // return response record.
                return new GenerateResponse("Generated response for: " + prompt, reqTokens, respTokens, false, latency);
            } finally {
                span.end();
            }
        }
    }