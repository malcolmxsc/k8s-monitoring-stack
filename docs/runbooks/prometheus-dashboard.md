# Prometheus Dashboard Notes

This guide captures the current Prometheus and Grafana configuration for monitoring model reliability. Use it when updating dashboards or explaining the observability story.

## Metrics of Interest

| Metric | Description | Labels |
| ------ | ----------- | ------ |
| `llm_prompts_total` | Successful `/generate` requests. | `model`, `region`, `userId`, `service` |
| `llm_errors_total` | Failed `/generate` requests partitioned by error type. | `model`, `region`, `error_type`, `service` |
| `http_server_requests_seconds_*` | Standard Spring Boot latency histogram. | `uri`, `status`, `method`, `outcome` |

All metrics are emitted via Micrometer and scraped from `/actuator/prometheus`.

## Dashboard Highlights

Located at `observability/grafana/provisioning/dashboards/llm-prometheus-dashboard.json`.

Key panels:

1. **Model Error Rates** – Stat panel with thresholds (<5% green, 5–10% yellow, 10–15% orange, >15% red).
2. **Errors by Type** – Donut chart using `sum by (error_type)`.
3. **Errors per Minute by Model** – Time series over `sum by (model)` rates.
4. **Request Success Rate** – Gauge based on successful vs total requests.
5. **Recent Error Logs** – Embedded Loki panel filtered by trace IDs.

## Query Snippets

```promql
# Error rate per model (5m window)
sum by (model) (rate(llm_errors_total[5m]))
  /
sum by (model) (rate(llm_prompts_total[5m]) + rate(llm_errors_total[5m]))
```

```promql
# Latency (p95) by model
histogram_quantile(
  0.95,
  sum by (model, le) (
    rate(http_server_requests_seconds_bucket{uri="/generate"}[5m])
  )
)
```

```promql
# Requests per minute split by outcome
sum by (model, status) (
  rate(http_server_requests_seconds_count{uri="/generate"}[1m])
)
```

## Maintenance Checklist

- Update the provisioning JSON when dashboards change in Grafana.
- Keep metric labels in the application consistent with the dashboard expectations.
- For large demos, consider reducing scrape intervals or adding recording rules under `observability/prometheus/rules/` for expensive queries.
