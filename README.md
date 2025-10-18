# Observability Sandbox

A production-ready observability stack demonstrating distributed tracing, structured logging, metrics, and SLO-based alerting for a Spring Boot application simulating LLM API interactions.

## 🏗️ Architecture

```
┌─────────────────┐
│  Spring Boot    │
│  Application    │──┐
│  (Port 8080)    │  │
└─────────────────┘  │
                     │
         ┌───────────┴─────────────┬──────────────┬─────────────┐
         │                         │              │             │
         ▼                         ▼              ▼             ▼
  ┌────────────┐          ┌──────────────┐  ┌─────────┐  ┌─────────┐
  │   Alloy    │          │  Prometheus  │  │  Loki   │  │  Tempo  │
  │  (OTLP)    │          │  (Metrics)   │  │ (Logs)  │  │ (Traces)│
  └────────────┘          └──────────────┘  └─────────┘  └─────────┘
         │                         │              │             │
         └───────────┬─────────────┴──────────────┴─────────────┘
                     │
                     ▼
              ┌───────────┐
              │  Grafana  │
              │(Port 3000)│
              └───────────┘
```

## 🚀 Quick Start (Kubernetes)

### Prerequisites
- kubectl configured for your cluster
- Grafana/Prometheus/Loki/Tempo (provided by the manifests in `k8s/`)
- Java 17+ and Gradle if you want to build locally (not required to run)

### Deploy

1) Namespace + Config
```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/logback-configmap.yaml
kubectl apply -f k8s/promtail-configmap.yaml
```

Create the application Secret (choose one):

- Quick one‑liner (recommended for demos):
```bash
kubectl create secret generic los-app-demo-credentials \
  -n observability-sandbox \
  --from-literal=app-user=demo \
  --from-literal=app-password='observability!'
```

- Or from the example manifest (edit password first):
```bash
cp k8s/secret-demo-credentials.example.yaml k8s/secret-demo-credentials.yaml
kubectl apply -f k8s/secret-demo-credentials.yaml
```

2) App + Service + Monitoring
```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/servicemonitor.yaml
```

3) Grafana provisioning (optional if you have an existing Grafana)
```bash
kubectl apply -f k8s/grafana-datasource-configmap.yaml
kubectl apply -f k8s/grafana-dashboard-provider-configmap.yaml
kubectl apply -f k8s/grafana-dashboard-llm-configmap.yaml
kubectl rollout restart deployment/grafana -n observability-sandbox
```

### Access the app
- The app listens on port 8080 via the `los-app` Service (LoadBalancer in the sample). Retrieve the external IP and test:
```bash
kubectl get svc los-app -n observability-sandbox
curl -u demo:observability! -X POST http://<LOS_APP_LB_IP>:8080/generate \
  -H "Content-Type: application/json" \
  -d '{"prompt":"hello"}'
```

### Interview Quick Start (clickable)

- App base URL: [http://136.115.91.255:8080](http://136.115.91.255:8080)
- Health check: [http://136.115.91.255:8080/actuator/health](http://136.115.91.255:8080/actuator/health)

Auth for app endpoints: `demo / observability!`

Run a one‑shot request (ready to copy/paste):

```bash
curl -s -u demo:observability! \
  -X POST "http://136.115.91.255:8080/generate" \
  -H "Content-Type: application/json" \
  -d '{"prompt":"hello"}' | jq .
```

Generate safe demo traffic:

```bash
BASE_URL="http://136.115.91.255:8080" \
APP_USER="demo" APP_PASSWORD="observability!" \
./load-generator.sh --pattern steady --base-url "$BASE_URL" --skip-health-check
```

### Generate traffic (safe defaults)
```bash
BASE_URL="http://<LOS_APP_LB_IP>:8080" \
APP_USER="demo" APP_PASSWORD="observability!" \
./load-generator.sh --pattern steady --base-url "$BASE_URL"
```

## 📊 Access Points

| Service | URL | Credentials | Purpose |
|---------|-----|-------------|---------|
| **Application** | http://<LOS_APP_LB_IP>:8080 | Basic Auth (demo/observability!) | Spring Boot REST API |
| **Grafana** | [http://35.223.226.27/](http://35.223.226.27/) | your Grafana creds | Dashboards & visualization |
| **Prometheus** | cluster service | - | Metrics & alerts |
| **Loki** | cluster service | - | Log aggregation (API) |
| **Tempo** | cluster service | - | Distributed tracing (API) |

## 🔍 Finding Your Data

### Logs (Grafana → Explore → Loki)

**Example queries:**
```logql
# All logs from the application
{job="los-app"}

# Logs for a specific user
{job="los-app"} |= "user123"

# Logs with errors
{job="los-app"} | json | level="ERROR"

# Logs for /generate endpoint
{job="los-app"} | json | endpoint="/generate"

# High latency requests (>500ms)
{job="los-app"} | json | latencyMs > 500
```

### Traces (Grafana → Explore → Tempo)

**Example queries:**
```
# Search by trace ID
<trace-id-from-logs>

# Search by HTTP URL
{ span.http.url = "/generate" }

# Search by user ID
{ resource.service.name = "observability-sandbox" && span.userId = "user123" }

# Search by region
{ span.region = "us-east" }

# Find slow traces (duration > 500ms)
{ duration > 500ms }
```

**Finding traces from logs:**
1. Go to Grafana → Explore → Loki
2. Run a log query and expand an entry
3. Copy a `traceId` value and open Grafana → Explore → Tempo
4. Paste the `traceId` to view the end‑to‑end trace

### Metrics (Grafana → Explore → Prometheus)

These match the dashboard panels and respect the Grafana time picker via `$__range`:

```promql
# Model error rate per model
100 * (sum(increase(llm_errors_total[$__range])) by (model))
    / (sum(increase(llm_errors_total[$__range])) by (model)
      + sum(increase(llm_prompts_success_total[$__range])) by (model))

# Total errors by type
sum(increase(llm_errors_total[$__range])) by (error_type)

# Errors by region
sum(increase(llm_errors_total[$__range])) by (region)

# Request success rate (overall)
sum(increase(llm_prompts_success_total[$__range]))
  / (sum(increase(llm_prompts_success_total[$__range]))
    + sum(increase(llm_errors_total[$__range]))) * 100

# Total requests (success + error)
sum(increase(llm_prompts_success_total[$__range]))
  + sum(increase(llm_errors_total[$__range]))
```

Classic HTTP metrics from Micrometer are also available if you need them (request rate, latency histograms, JVM memory). Example:

```promql
# HTTP request rate
rate(http_server_requests_seconds_count[5m])

# HTTP P95 latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# JVM heap utilisation
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

## 📈 Dashboard Overview

This project ships a single “LLM Model Reliability (Prometheus)” dashboard. Panels and data sources:

- Model Error Rates (%) — Prometheus
  - Formula (per model, selected range):
    `100 * (sum(increase(llm_errors_total[$__range])) by (model)) / (sum(increase(llm_errors_total[$__range])) by (model) + sum(increase(llm_prompts_success_total[$__range])) by (model))`
  - Shows per‑model error ratio aligned with the overall success rate.

- Total Errors by Model (range) — Prometheus
  - `sum(increase(llm_errors_total[$__range])) by (model)`

- Errors by Type (range) — Prometheus
  - `sum(increase(llm_errors_total[$__range])) by (error_type)`

- Errors by Region (range) — Prometheus
  - `sum(increase(llm_errors_total[$__range])) by (region)`

- Top Errors by Model (range) — Prometheus (bar gauge)
  - `topk(8, sum(increase(llm_errors_total[$__range])) by (model, error_type))`

- Request Success Rate — Prometheus (gauge)
  - `sum(increase(llm_prompts_success_total[$__range])) / (sum(increase(llm_prompts_success_total[$__range])) + sum(increase(llm_errors_total[$__range]))) * 100`

- Total Requests (range) — Prometheus (stat)
  - `sum(increase(llm_prompts_success_total[$__range])) + sum(increase(llm_errors_total[$__range]))`

- Recent Error Logs — Loki
  - JSON logs from the app with MDC fields (traceId, spanId, model, region, endpoint, latency).
  - Use Explore → Loki to filter by `job="los-app"` and fields (`| json | level="ERROR"`, etc.).

- Traces — Tempo (ingested via Grafana Alloy / OTLP)
  - The app exports OTLP traces to Tempo (`/v1/traces`). In Explore → Tempo you can open traces by ID or filter by attributes (endpoint, region, model).

Got “No data”? Run the load generator for a minute:
```bash
BASE_URL="http://136.115.91.255:8080" \
APP_USER="demo" APP_PASSWORD="observability!" \
./load-generator.sh --pattern steady --base-url "$BASE_URL" --skip-health-check
```

See [SLOs and Alerting](docs/runbooks/slo-and-alerts.md) for runbooks covering the underlying metrics and burn-rate alerts.

## 🧪 Testing & Development (local, optional)

### Generate Load
```bash
# Quick test
for i in {1..20}; do 
  curl -X POST http://localhost:8080/generate \
    -H "Content-Type: application/json" \
    -d '{"userId":"user'$i'","region":"us-east"}' 
  sleep 0.5
done

# Continuous load
watch -n 1 'curl -X POST http://localhost:8080/generate \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"user$RANDOM\",\"region\":\"us-east\"}"'
```

### View Application Logs
```bash
# Tail JSON logs
tail -f logs/application.log | jq

# Watch for errors
tail -f logs/application.log | jq 'select(.level=="ERROR")'
```

### Check Service Health
```bash
# Application health
curl http://localhost:8080/actuator/health

# Prometheus targets
curl http://localhost:9090/api/v1/targets | jq

## 🔒 Security
- App endpoints are protected with HTTP Basic (demo / observability! by default).
- Prometheus scraping on `/actuator/prometheus` is open to the cluster.
- Credentials are stored in the K8s Secret `los-app-demo-credentials`; rotate by editing the Secret and restarting the Deployment.

## 🧹 Housekeeping
- Ephemeral docs (internal change logs, fix summaries) are ignored via `.gitignore` to keep the repo presentation‑ready.

# Check if services are up
docker-compose ps
```

## 🏗️ Project Structure

```
observability-sandbox/
├── src/main/java/com/example/observability_sandbox/
│   ├── GenerateController.java      # REST endpoint with tracing
│   ├── LlmService.java              # LLM simulation with metrics
│   └── ObservabilitySandboxApplication.java
├── src/main/resources/
│   ├── application.properties       # Spring Boot config
│   └── logback-spring.xml          # Structured logging config
├── observability/
│   ├── alloy/
│   │   └── config.river            # OpenTelemetry collector config
│   ├── grafana/
│   │   └── provisioning/
│   │       ├── datasources/        # Loki, Tempo, Prometheus
│   │       └── dashboards/         # SLO Dashboard
│   ├── prometheus/
│   │   ├── prometheus.yml          # Scrape & rule configuration
│   │   └── alert-rules.yml         # SLO-based alert rules
│   └── tempo/
│       └── tempo.yaml              # Trace storage config
├── docker-compose.yml              # Full observability stack
└── logs/                           # Application JSON logs
```

## 🎯 Key Features

### Distributed Tracing
- **Automatic instrumentation** via Spring Boot Actuator
- **Custom spans** for LLM service calls
- **Context propagation** across service boundaries
- **TraceId injection** into logs for correlation

### Structured Logging
- **JSON format** with Logstash encoder
- **MDC context**: traceId, spanId, userId, region, endpoint
- **Clickable trace links** in Grafana
- **UTC timestamps** with @timestamp field

### Metrics & Monitoring
- **RED metrics**: Rate, Errors, Duration
- **Custom metrics**: LLM token usage (request/response)
- **Percentile histograms**: P50, P90, P95, P99
- **JVM metrics**: Memory, threads, GC

### SLO-Based Alerting
- **Latency SLOs**: P90 < 500ms, P95 < 1s
- **Availability SLO**: 99.9% uptime
- **Multi-window alerts**: 2min, 5min, 10min
- **Severity levels**: Critical, Warning, Info

## 📚 Additional Documentation

- [SLOs and Alerting](docs/runbooks/slo-and-alerts.md) – Recording rules, burn-rate alerts, and test procedures.
- [Prometheus Dashboard Notes](docs/runbooks/prometheus-dashboard.md) – Key metrics and query snippets powering the Grafana dashboard.
- [Error Analysis Playbook](docs/runbooks/error-analysis.md) – How to investigate model-specific failures across logs, metrics, and traces.
- [Load Generator Runbook](docs/runbooks/load-generator.md) – Traffic patterns and operational tips for the bundled client script.
- [Observability Stack Health Check](docs/runbooks/health-check.md) – Pre-demo checklist to confirm all components are healthy.

## 🛠️ Tech Stack

**Application:**
- Spring Boot 3.5.6
- Micrometer (metrics)
- Micrometer Tracing (OpenTelemetry bridge)
- Logback with Logstash encoder

**Observability:**
- Grafana 12.2.0 (visualization)
- Prometheus 3.5.0 (metrics)
- Loki 2.9.8 (logs)
- Tempo 2.5.0 (traces)
- Grafana Alloy (OpenTelemetry collector)

## 🐛 Troubleshooting

### No traces in Tempo
```bash
# Check Alloy is receiving OTLP data
docker logs alloy

# Verify app is sending traces
curl http://localhost:8080/actuator/metrics/http.server.requests

# Check Tempo ingestion
curl http://localhost:3200/status
```

### Logs not showing in Loki
```bash
# Verify log files exist
ls -la logs/

# Check Alloy log collection
docker logs alloy | grep loki

# Query Loki directly
curl -G http://localhost:3100/loki/api/v1/query \
  --data-urlencode 'query={job="los-app"}'
```

### Metrics not in Prometheus
```bash
# Check Prometheus targets
open http://localhost:9090/targets

# Verify app metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Check Prometheus logs
docker logs prometheus
```

## 📝 License

MIT License - See [LICENSE](LICENSE) file

## 🤝 Contributing

This is a learning/portfolio project. Feel free to:
- Fork and experiment
- Submit issues for bugs
- Suggest improvements via PRs

---

**Built with ❤️ for learning modern observability practices**  
