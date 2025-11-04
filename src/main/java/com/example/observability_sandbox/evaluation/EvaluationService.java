package com.example.observability_sandbox.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final ParameterizedTypeReference<List<HuggingFacePrediction>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final EvaluationProperties properties;
    private final RestClient restClient;
    private final MeterRegistry meterRegistry;
    private final List<EvaluationCase> evaluationCases;
    private final String authToken;

    private final Counter passCounter;
    private final Counter failCounter;
    private final Timer requestTimer;
    private final Timer batchTimer;
    private final AtomicInteger lastRunCount = new AtomicInteger();
    private final AtomicInteger lastRunPasses = new AtomicInteger();
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<EvaluationBatchSummary> lastSummary = new AtomicReference<>(
            new EvaluationBatchSummary(Instant.EPOCH, Duration.ZERO, 0, 0, 0, List.of()));
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    public EvaluationService(RestClient.Builder restClientBuilder,
                             EvaluationProperties properties,
                             MeterRegistry meterRegistry,
                             Environment environment) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.evaluationCases = EvaluationDataset.defaultCases();
        this.authToken = resolveToken(properties, environment);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.getRequestTimeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        this.restClient = restClientBuilder
                .baseUrl("https://api-inference.huggingface.co/models/" + properties.getModel())
                .requestFactory(requestFactory)
                .build();

        this.passCounter = Counter.builder("llm_evaluation_tests_total")
                .tag("result", "pass")
                .tag("model", properties.getModel())
                .description("LLM evaluation tests that matched the expected outcome")
                .register(meterRegistry);
        this.failCounter = Counter.builder("llm_evaluation_tests_total")
                .tag("result", "fail")
                .tag("model", properties.getModel())
                .description("LLM evaluation tests that failed to match the expected outcome")
                .register(meterRegistry);
        this.requestTimer = Timer.builder("llm_evaluation_request_duration")
                .description("Latency of individual evaluation requests against Hugging Face")
                .tag("model", properties.getModel())
                .register(meterRegistry);
        this.batchTimer = Timer.builder("llm_evaluation_batch_duration")
                .description("Total duration for running the evaluation batch")
                .tag("model", properties.getModel())
                .register(meterRegistry);

        Gauge.builder("llm_evaluation_last_run_total", lastRunCount, AtomicInteger::get)
                .tag("model", properties.getModel())
                .description("Number of prompts evaluated in the last batch")
                .register(meterRegistry);
        Gauge.builder("llm_evaluation_last_run_passes", lastRunPasses, AtomicInteger::get)
                .tag("model", properties.getModel())
                .description("Number of prompts that passed in the last batch")
                .register(meterRegistry);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean hasToken() {
        return StringUtils.hasText(authToken);
    }

    public EvaluationBatchSummary runBatch() {
        return runBatch("manual");
    }

    public EvaluationBatchSummary runBatch(String trigger) {
        if (!properties.isEnabled()) {
            log.debug("Evaluation disabled; skipping run [{}]", trigger);
            return emptySummary();
        }
        if (!StringUtils.hasText(authToken)) {
            log.warn("No Hugging Face token configured; skipping evaluation run [{}]", trigger);
            return emptySummary();
        }
        if (!batchRunning.compareAndSet(false, true)) {
            log.warn("Evaluation already running; skipping overlapping trigger [{}]", trigger);
            return emptySummary();
        }

        try {
            Instant start = Instant.now();
            log.info("Starting evaluation batch [{}] cases={} model={}", trigger, properties.getBatchSize(), properties.getModel());
            Sample sample = Timer.start(meterRegistry);
            List<EvaluationCase> casesToRun = evaluationCases.stream()
                    .limit(Math.max(1, properties.getBatchSize()))
                    .collect(Collectors.toList());

            int passed = 0;
            int failed = 0;
            List<EvaluationResult> results = casesToRun.stream()
                    .map(this::executeCase)
                    .peek(result -> {
                        if (result.passed()) {
                            passCounter.increment();
                        } else {
                            failCounter.increment();
                        }
                    })
                    .toList();

            for (EvaluationResult result : results) {
                if (result.passed()) {
                    passed++;
                } else {
                    failed++;
                }
            }

            long elapsedNanos = sample.stop(batchTimer);
            Duration batchDuration = Duration.ofNanos(elapsedNanos);
            lastRunCount.set(results.size());
            lastRunPasses.set(passed);
            lastRunAt.set(start);

            EvaluationBatchSummary summary = new EvaluationBatchSummary(start, batchDuration, results.size(), passed, failed, results);
            lastSummary.set(summary);

            log.info("Completed evaluation batch [{}]: total={} passed={} failed={} duration={}ms",
                    trigger, results.size(), passed, failed, batchDuration.toMillis());

            return summary;
        } finally {
            batchRunning.set(false);
        }
    }

    @Async("evaluationExecutor")
    public CompletableFuture<EvaluationBatchSummary> runBatchAsync(String trigger) {
        return CompletableFuture.completedFuture(runBatch(trigger));
    }

    @Scheduled(initialDelayString = "#{T(java.time.Duration).parse('${evaluation.interval:PT10M}').toMillis()}",
            fixedDelayString = "#{T(java.time.Duration).parse('${evaluation.interval:PT10M}').toMillis()}")
    public void scheduledBatch() {
        runBatch("scheduled");
    }

    public Instant lastRunAt() {
        return lastRunAt.get();
    }

    public EvaluationBatchSummary lastSummary() {
        return lastSummary.get();
    }

    public boolean isRunning() {
        return batchRunning.get();
    }

    private EvaluationBatchSummary emptySummary() {
        return new EvaluationBatchSummary(Instant.now(), Duration.ZERO, 0, 0, 0, List.of());
    }

    private EvaluationResult executeCase(EvaluationCase evaluationCase) {
        long startNanos = System.nanoTime();
        try {
            List<HuggingFacePrediction> predictions = restClient.post()
                    .headers(headers -> {
                        headers.setBearerAuth(authToken);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    })
                    .body(HuggingFaceRequest.of(evaluationCase.prompt()))
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (predictions == null || predictions.isEmpty()) {
                return failureResult(evaluationCase, startNanos, "Empty response from Hugging Face");
            }

            HuggingFacePrediction best = predictions.stream()
                    .max(Comparator.comparingDouble(HuggingFacePrediction::score))
                    .orElse(null);
            if (best == null) {
                return failureResult(evaluationCase, startNanos, "No prediction scores returned");
            }

            String predictedLabel = normalizeLabel(best.label());
            boolean passed = predictedLabel.equalsIgnoreCase(evaluationCase.expectedLabel());
            Duration latency = recordLatency(startNanos);
            if (passed) {
                log.info("Evaluation PASS [{}] expected={} predicted={} confidence={}",
                        evaluationCase.id(), evaluationCase.expectedLabel(), predictedLabel, best.score());
            } else {
                log.warn("Evaluation FAIL [{}] expected={} predicted={} confidence={}",
                        evaluationCase.id(), evaluationCase.expectedLabel(), predictedLabel, best.score());
            }
            return new EvaluationResult(evaluationCase, predictedLabel, best.score(), passed, latency, null);
        } catch (RestClientException ex) {
            log.error("Evaluation ERROR [{}]: {}", evaluationCase.id(), ex.getMessage(), ex);
            return failureResult(evaluationCase, startNanos, ex.getMessage());
        }
    }

    private EvaluationResult failureResult(EvaluationCase evaluationCase, long startNanos, String message) {
        Duration latency = recordLatency(startNanos);
        return new EvaluationResult(evaluationCase, "ERROR", 0.0, false, latency, message);
    }

    private Duration recordLatency(long startNanos) {
        Duration latency = Duration.ofNanos(System.nanoTime() - startNanos);
        requestTimer.record(latency);
        return latency;
    }

    private static String normalizeLabel(String label) {
        return label == null ? "UNKNOWN" : label.trim().toUpperCase(Locale.ROOT);
    }

    private static String resolveToken(EvaluationProperties properties, Environment environment) {
        if (StringUtils.hasText(properties.getApiToken())) {
            return properties.getApiToken().trim();
        }
        String envToken = environment.getProperty("HUGGINGFACE_TOKEN");
        if (StringUtils.hasText(envToken)) {
            return envToken.trim();
        }
        return "";
    }

    private record HuggingFacePrediction(String label, double score) {
    }

    private record HuggingFaceRequest(String inputs, Options options) {

        static HuggingFaceRequest of(String prompt) {
            return new HuggingFaceRequest(prompt, new Options(true, false));
        }
    }

    @SuppressWarnings("unused")
    private record Options(boolean wait_for_model, boolean use_cache) {
    }
}
