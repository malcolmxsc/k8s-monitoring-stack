# SLOs and Alerting

This document summarizes the service level objectives tracked by the demo and how alerts are configured.

## Service Level Indicators

| SLI | Target | Notes |
| --- | ------ | ----- |
| Availability | 99.9% success rate over 30 days | Calculated from `llm_prompts_total` and `llm_errors_total`. |
| Latency (P90) | < 500 ms | Based on `http_server_requests_seconds` histogram filtered to `/generate`. |
| Latency (P95) | < 1,000 ms | Same histogram; higher threshold for paging alerts. |
| Client Errors | < 10% of requests | Tracks 4xx responses to spot misbehaving clients. |

## Prometheus Rules

Recording and alerting rules live in `observability/prometheus/rules/`. Key examples:

- `sli:llm_error_ratio` – Fraction of failed requests.
- `sli:llm_latency_p90` / `sli:llm_latency_p95` – Quantiles computed from histogram buckets.
- Burn-rate alert pairs at 5m/1h and 30m/6h windows.

When modifying alert logic:

1. Update the rule file.
2. Reload Prometheus (`/-/reload`) or wait for the next automatic refresh.
3. Validate in Grafana's alert panel or through the Prometheus `/alerts` endpoint.

## Grafana Dashboard

Panels labelled **"Error Budget Burn"** and **"Latency SLO"** read directly from the recording rules above. Use them to confirm alerts are behaving before enabling notifications.

## Testing Alerts

1. Run the load generator in chaos mode:
   ```bash
   ./load-generator.sh --pattern chaos --duration 300
   ```
2. Watch `sli:llm_error_ratio` climb above thresholds.
3. Confirm alert transitions from `inactive` → `pending` → `firing` in Prometheus.

## Operational Tips

- Keep sampling probability at 1.0 during demos to ensure traces back alerts.
- When deploying to low-resource clusters, adjust concurrency in the load generator before testing burn-rate rules.
- Document any threshold adjustments in `observability/prometheus/rules/README.md` (create the file if needed) to maintain history outside of git commits.
