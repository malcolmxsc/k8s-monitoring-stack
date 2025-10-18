# Observability Stack Health Check

Use this checklist to confirm that the demo stack is healthy after deployments or before interviews/demos. Each section lists a command or Grafana view to verify.

## 1. Application

- `kubectl get pods -n observability-sandbox` → all pods `Running`.
- `curl -u demo:observability! http://<host>:8080/actuator/health` → returns `"status":"UP"`.
- Logs (`kubectl logs`) include `traceId` / `spanId` fields.

## 2. Metrics (Prometheus)

- `kubectl port-forward svc/prometheus 9090:9090 -n observability-sandbox`.
- Visit `http://localhost:9090/targets` and ensure the Spring Boot scrape job shows `UP`.
- Run sample queries:
  - Request volume: `rate(http_server_requests_seconds_count{uri="/generate"}[1m])`
  - Error rate: `sum(rate(llm_errors_total[5m])) / sum(rate(llm_prompts_total[5m]))`

## 3. Logs (Loki)

- `kubectl port-forward svc/loki 3100:3100 -n observability-sandbox`.
- In Grafana Explore, run: `{job="observability-sandbox"} | json`.
- Confirm entries include `model`, `region`, and `userId` fields.

## 4. Traces (Tempo)

- `kubectl port-forward svc/tempo 3200:3200 -n observability-sandbox`.
- In Grafana Explore → Tempo, search for `{ span.service.name = "observability-sandbox" }`.
- Ensure recent traces show `/generate` spans with model attributes.

## 5. Dashboards (Grafana)

- Port-forward Grafana: `kubectl port-forward svc/grafana 3000:3000 -n observability-sandbox`.
- Open `http://localhost:3000/d/llm-prometheus` (LLM Model Reliability dashboard).
- Validate panels for error rates, latency, and recent logs are populated.

## 6. Alerting

- Prometheus rules are located in `observability/prometheus/rules`. Confirm they are loaded from the Prometheus UI (`/rules`).
- Trigger a quick alert test by running the load generator with the `chaos` pattern and watching the burn-rate panels in Grafana.

## 7. Cleanup

- Stop any port-forwards.
- If you generated test load, scale the deployment down/up to clear long-running requests if needed.
