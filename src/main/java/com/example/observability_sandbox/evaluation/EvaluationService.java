package com.example.observability_sandbox.evaluation;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.util.Progress;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationProperties properties;
    private final MeterRegistry meterRegistry;
    private final List<EvaluationCase> evaluationCases;

    private final Counter passCounter;
    private final Counter failCounter;
    private final Timer requestTimer;
    private final Timer inferenceTimer;
    private final Timer batchTimer;
    private final AtomicInteger lastRunCount = new AtomicInteger();
    private final AtomicInteger lastRunPasses = new AtomicInteger();
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<EvaluationBatchSummary> lastSummary = new AtomicReference<>(
            new EvaluationBatchSummary(Instant.EPOCH, Duration.ZERO, 0, 0, 0, List.of()));
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    private final AtomicReference<ZooModel<String, Classifications>> modelRef = new AtomicReference<>();

    public EvaluationService(EvaluationProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.evaluationCases = EvaluationDataset.defaultCases();

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
                .description("Latency of individual evaluation inferences")
                .tag("model", properties.getModel())
                .register(meterRegistry);
        this.inferenceTimer = Timer.builder("djl_inference_latency_seconds")
                .description("Time spent inside predictor.predict (DJL only)")
                .tag("model", properties.getModel())
                .publishPercentileHistogram()
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

    @PostConstruct
    public void warmup() {
        if (properties.isEnabled()) {
            initializePredictor();
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public EvaluationBatchSummary runBatch() {
        return runBatch("manual");
    }

    public EvaluationBatchSummary runBatch(String trigger) {
        if (!properties.isEnabled()) {
            log.debug("Evaluation disabled; skipping run [{}]", trigger);
            return emptySummary();
        }
        if (!batchRunning.compareAndSet(false, true)) {
            log.warn("Evaluation already running; skipping overlapping trigger [{}]", trigger);
            return emptySummary();
        }

        Map<String, String> batchContext = new LinkedHashMap<>();
        try {
            initializePredictor();
            ZooModel<String, Classifications> zooModel = modelRef.get();
            if (zooModel == null) {
                log.error("Predictor unavailable; skipping evaluation run [{}]", trigger);
                return emptySummary();
            }

            List<EvaluationCase> casesToRun = evaluationCases.stream()
                    .limit(Math.max(1, properties.getBatchSize()))
                    .collect(Collectors.toList());

            Instant start = Instant.now();
            batchContext.put("evaluation_batch_trigger", trigger);
            batchContext.put("evaluation_model", properties.getModel());
            batchContext.put("evaluation_batch_size", Integer.toString(casesToRun.size()));
            batchContext.put("evaluation_batch_started_at", start.toString());
            applyMdc(batchContext);

            log.info("evaluation_batch_start cases={}", casesToRun.size());

            Sample sample = Timer.start(meterRegistry);
            List<EvaluationResult> results = new ArrayList<>(casesToRun.size());
            int passed = 0;
            int failed = 0;
            try (Predictor<String, Classifications> predictor = zooModel.newPredictor()) {
                for (EvaluationCase caseItem : casesToRun) {
                    EvaluationResult result = executeCase(predictor, caseItem);
                    results.add(result);
                    if (result.passed()) {
                        passCounter.increment();
                        passed++;
                    } else {
                        failCounter.increment();
                        failed++;
                    }
                }
            }

            long elapsedNanos = sample.stop(batchTimer);
            Duration batchDuration = Duration.ofNanos(elapsedNanos);
            lastRunCount.set(results.size());
            lastRunPasses.set(passed);
            lastRunAt.set(start);

            Map<String, String> summaryContext = new LinkedHashMap<>();
            summaryContext.put("evaluation_batch_total", Integer.toString(results.size()));
            summaryContext.put("evaluation_batch_passed", Integer.toString(passed));
            summaryContext.put("evaluation_batch_failed", Integer.toString(failed));
            summaryContext.put("evaluation_batch_duration_ms", Long.toString(batchDuration.toMillis()));
            summaryContext.put("evaluation_model", properties.getModel());
            applyMdc(summaryContext);
            try {
                log.info("evaluation_batch_complete total={} passed={} failed={} duration_ms={}",
                        results.size(), passed, failed, batchDuration.toMillis());
            } finally {
                clearMdc(summaryContext);
            }

            EvaluationBatchSummary summary = new EvaluationBatchSummary(start, batchDuration, results.size(), passed, failed, results);
            lastSummary.set(summary);

            Map<String, String> completedContext = new LinkedHashMap<>();
            completedContext.put("evaluation_model", properties.getModel());
            completedContext.put("evaluation_batch_total", Integer.toString(results.size()));
            completedContext.put("evaluation_batch_passed", Integer.toString(passed));
            completedContext.put("evaluation_batch_failed", Integer.toString(failed));
            completedContext.put("evaluation_batch_duration_ms", Long.toString(batchDuration.toMillis()));
            applyMdc(completedContext);
            try {
                log.info("Completed evaluation batch [{}]: total={} passed={} failed={} duration={}ms",
                        trigger, results.size(), passed, failed, batchDuration.toMillis());
            } finally {
                clearMdc(completedContext);
            }

            return summary;
        } finally {
            clearMdc(batchContext);
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

    private EvaluationResult executeCase(Predictor<String, Classifications> predictor, EvaluationCase evaluationCase) {
        long startNanos = System.nanoTime();
        long inferenceStart = System.nanoTime();
        try {
            Classifications classifications = predictor.predict(evaluationCase.prompt());
            inferenceTimer.record(Duration.ofNanos(System.nanoTime() - inferenceStart));
            Classifications.Classification best = classifications.best();
            if (best == null) {
                EvaluationResult failure = failureResult(evaluationCase, startNanos, "No classification returned");
                logCaseResult(failure, null);
                return failure;
            }

            String predictedLabel = normalizeLabel(best.getClassName());
            boolean passed = predictedLabel.equalsIgnoreCase(evaluationCase.expectedLabel());
            Duration latency = recordLatency(startNanos);
            EvaluationResult result = new EvaluationResult(evaluationCase, predictedLabel, best.getProbability(), passed, latency, null);
            logCaseResult(result, null);
            return result;
        } catch (TranslateException ex) {
            inferenceTimer.record(Duration.ofNanos(System.nanoTime() - inferenceStart));
            EvaluationResult failure = failureResult(evaluationCase, startNanos, ex.getMessage());
            logCaseResult(failure, ex);
            return failure;
        }
    }

    private void logCaseResult(EvaluationResult result, Throwable throwable) {
        EvaluationCase evaluationCase = result.evaluationCase();
        Map<String, String> context = new LinkedHashMap<>();
        context.put("evaluation_model", properties.getModel());
        context.put("evaluation_prompt", evaluationCase.prompt());
        context.put("evaluation_case_id", evaluationCase.id());
        context.put("evaluation_expected_label", evaluationCase.expectedLabel());
        context.put("evaluation_predicted_label", result.predictedLabel());
        context.put("evaluation_confidence", String.format(Locale.ROOT, "%.6f", result.confidence()));
        context.put("evaluation_latency_ms", Long.toString(result.latency().toMillis()));
        context.put("evaluation_passed", Boolean.toString(result.passed()));
        if (result.errorMessage() != null) {
            context.put("evaluation_error", result.errorMessage());
        }

        applyMdc(context);
        try {
            if (result.errorMessage() != null) {
                if (throwable != null) {
                    log.error("evaluation_case_error", throwable);
                } else {
                    log.error("evaluation_case_error");
                }
            } else if (result.passed()) {
                log.info("evaluation_case_pass");
            } else {
                log.warn("evaluation_case_fail");
            }
        } finally {
            clearMdc(context);
        }
    }

    private static void applyMdc(Map<String, String> context) {
        context.forEach((key, value) -> {
            if (value != null) {
                MDC.put(key, value);
            } else {
                MDC.remove(key);
            }
        });
    }

    private static void clearMdc(Map<String, String> context) {
        context.keySet().forEach(MDC::remove);
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

    private void initializePredictor() {
        if (modelRef.get() != null) {
            return;
        }
        try {
            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optApplication(Application.NLP.SENTIMENT_ANALYSIS)
                    .optGroupId("ai.djl.pytorch")
                    .optArtifactId(properties.getModel())
                    .optEngine("PyTorch")
                    .optProgress(new SilentProgress())
                    .build();

            ZooModel<String, Classifications> model = criteria.loadModel();
            modelRef.set(model);
            log.info("Loaded DJL packaged model {} for evaluation", properties.getModel());
        } catch (ModelNotFoundException | MalformedModelException | IOException ex) {
            log.error("Failed to load evaluation model {}: {}", properties.getModel(), ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.error("Unexpected failure loading evaluation model {}: {}", properties.getModel(), ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        ZooModel<String, Classifications> model = modelRef.getAndSet(null);
        if (model != null) {
            model.close();
        }
    }

    private static final class SilentProgress implements Progress {
        @Override
        public void reset(String task, long max) {
            // no-op
        }

        @Override
        public void reset(String task, long max, String status) {
            // no-op
        }

        @Override
        public void update(long current) {
            // no-op
        }

        @Override
        public void update(long current, String status) {
            // no-op
        }

        @Override
        public void increment(long current) {
            // no-op
        }

        @Override
        public void end() {
            // no-op
        }

        @Override
        public void start(long max) {
            // no-op
        }
    }
}
