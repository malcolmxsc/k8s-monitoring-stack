# Evaluation Service Integration Plan

This document tracks the agreed plan for extending the existing Spring Boot
application with a Hugging Facepowered evaluation workflow while keeping a
single-service deployment. Refer back to these steps whenever you need to
regain context.

## Goal & Rationale

- Showcase hands-on experience with **model evaluation** and **AI observability**
  for applied ML interviews.
- Call a real Hugging Face sentiment model via the hosted inference API instead
  of maintaining a local DJL runtime, keeping the Docker image small and
  compatible with Kubernetes.
- Record structured logs, metrics, and traces for each evaluation run so
  Prometheus and Grafana reflect evaluation quality (pass rate, latency, volume).

## System Overview

1. **Evaluation prompts** curated set of labelled sentences (positive and
   negative sentiments) stored in code or a resource file.
2. **EvaluationService** Spring `@Service` that:
   - Calls Hugging Face’s inference API using the model configured in
     `application.properties`.
   - Compares the returned label to the expected label.
   - Emits metrics (`llm_evaluation_tests_total` counters, latency timers),
     logs, and tracing attributes per prompt.
3. **Scheduler & API trigger** scheduled batch runs plus an optional manual
   trigger endpoint.
4. **Observability** existing Prometheus scrape discovers the new metrics;
   Grafana dashboard adds panels for pass rate and recent evaluations; logs
   stream to Loki with prompt metadata.

## Implementation Phases

### Phase 1 Requirements & Configuration

- Define the evaluation dataset (start with ~10 positive & ~10 negative prompts).
- Add configuration keys under `evaluation.*` in
  `src/main/resources/application.properties`:
  - `evaluation.enabled`
  - `evaluation.model`
  - `evaluation.interval`
  - `evaluation.batchSize` (optional)
- Plan secret management: Hugging Face token supplied via environment variable
  `HUGGINGFACE_TOKEN`; document updates for Docker Compose and Kubernetes
  secrets (`k8s/deployment.yaml`).

### Phase 2 Service Implementation

- Create `EvaluationService`:
  - Inject Spring’s `RestClient`/`WebClient`, `MeterRegistry`, and configuration.
  - Implement `evaluatePrompt(prompt, expectation)` returning an immutable
    result record with latency, label, confidence, and pass/fail flag.
  - Implement `runBatch()` that loops over all prompts, accumulates results, and
    pushes metrics/logs.
  - Emit metrics:
    - Counter `llm_evaluation_tests_total{result="pass|fail",model="..."}`.
    - Timer for batch duration (`llm_evaluation_batch_duration`).
    - Gauge or counter for total prompts evaluated.
  - Add structured logging (`logger.info/ warn`) with MDC fields (`prompt_id`,
    `expected_label`, `actual_label`, `score`).
- Handle API errors gracefully with retries/backoff.

### Phase 3  Scheduling & API Exposure

- Enable scheduling in `ObservabilitySandboxApplication` (`@EnableScheduling`).
- Add `@Scheduled` method in `EvaluationService` (or dedicated scheduler bean)
  triggered by `evaluation.interval`.
- Optional: Expose `POST /api/evaluations/run` endpoint that submits a batch via
  `@Async` executor; respond immediately with a run identifier.
- Capture trace context around each evaluation call for Tempo integration.

### Phase 4 Observability Integration

- Ensure Prometheus scrape discovers new metrics (no changes required if the
  service remains in the main app, but verify queries).
- Update Grafana dashboard JSON:
  - Panel for pass rate (%): `sum(rate(llm_evaluation_tests_total{result="pass"}[5m])) / sum(rate(llm_evaluation_tests_total[5m])) * 100`.
  - Panel for total evaluations per model: `sum by (model) (increase(llm_evaluation_tests_total[1h]))`.
  - Table or log panel for recent failures (using Loki query filtered by
    `evaluation` tag).
- Document the new panels under `docs/runbooks/prometheus-dashboard.md`.

### Phase 5  Deployment & Secrets

- Update Docker Compose service definition to inject `HUGGINGFACE_TOKEN`.
- Update Kubernetes manifests:
  - Add `HUGGINGFACE_TOKEN` entry to the secret and environment variable in
    `k8s/deployment.yaml`.
  - Confirm imagePullSecret and config references remain valid.
- Validate end-to-end:
  - Run evaluation locally (`curl -X POST http://localhost:8080/api/evaluations/run`).
  - Check `/actuator/prometheus` for new metrics.
  - Confirm Grafana panels display data after the first scheduled batch.

## Stretch Enhancements

- Move evaluation cases to a YAML/JSON resource and allow hot reload via ConfigMap.
- Store evaluation results in a database or S3 for historical analysis.
- Support multiple models (loop over model IDs, label metrics with `model` tag).
- Add alerting rules (Prometheus) for sustained failure rate spikes.
- Expose evaluation traces with span attributes for prompt, expectation, and
  outcome to drill down in Tempo.

Keep this plan updated as work progresses so you can quickly refresh your memory
and demonstrate structured thinking in interviews.
