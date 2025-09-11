# 🧩 LLM Observability Sandbox (LOS)

A learning project to simulate observability for an AI system.  
Built with **Java + Spring Boot**, it exposes a fake LLM `/generate` endpoint that returns mock completions and ships metrics to **Prometheus + Grafana**.

This project grows step by step:
1. **Metrics** → Prometheus + Grafana ✅  
2. **Logs** → JSON logs, Loki (next)  
3. **Traces** → OpenTelemetry, Tempo  
4. **SLOs + Alerts** → Prometheus rules + Grafana dashboards  
5. **Kubernetes** → Deploy on kind/minikube for a “prod-like” stack  

---

## 🚀 Features (current progress)
- `POST /generate` → simulates an LLM completion:
  - Adds random latency (fast if cache hit, slow otherwise)
  - Fake token counting (input + output)
- Custom **Micrometer metrics**:
  - `llm_requests_total`
  - `llm_request_tokens_total`
  - `llm_response_tokens_total`
  - `llm_cache_hit_ratio`
- Prometheus scrape endpoint at `/actuator/prometheus`
- Works with Grafana dashboards (RPS, latency, tokens/sec)

---

## 📦 Getting Started

### Prerequisites
- Java 21  
- Gradle (wrapper included)  
- Docker Desktop (for Prometheus + Grafana stack)  

### Clone the repo
```bash
git clone git@github.com:malcolmxsc/observability-sandbox.git
cd observability-sandbox
```

### Run the app
```bash
./gradlew bootRun
```

### Test the LLM endpoint
```bash
curl -s -X POST http://localhost:8080/generate   -H "Content-Type: application/json"   -d '{"prompt":"Write a haiku about observability"}'
```

### Check metrics
```bash
curl -s http://localhost:8080/actuator/prometheus | grep llm_
```

---

## 📊 Observability Stack

Start Prometheus + Grafana with Docker Compose:
```bash
docker compose up -d
```

- Prometheus → [http://localhost:9090](http://localhost:9090)  
- Grafana → [http://localhost:3000](http://localhost:3000) (admin / admin)  

### Example Grafana queries
**Request rate (RPS):**
```promql
sum(rate(http_server_requests_seconds_count{uri="/generate"}[1m]))
```

**p95 latency:**
```promql
histogram_quantile(0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))
)
```

**Request tokens/sec:**
```promql
rate(llm_request_tokens_total[1m])
```

---

## 🛠 Roadmap
- [ ] Add structured JSON logs (`logback-spring.xml`)  
- [ ] Ship logs to **Loki**  
- [ ] Export traces to **Tempo** via OpenTelemetry  
- [ ] Define **SLOs** (p95 latency < 500ms, error rate < 1%)  
- [ ] Add **error budget burn alerts** with Alertmanager  
- [ ] Deploy on **Kubernetes (kind/minikube)**  

---

## 📚 Learning Goals
- Understand the **three pillars of observability**: metrics, logs, traces  
- Practice instrumenting services with **Micrometer + OpenTelemetry**  
- Build intuition around **SLOs, error budgets, and alerts**  
- Get hands-on with the **Grafana ecosystem**  
