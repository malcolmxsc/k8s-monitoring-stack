# Error Analysis Playbook

The application simulates multiple foundation models and injects realistic error patterns to showcase observability workflows. Use this playbook to explore failures and trace them through metrics, logs, and spans.

## Model Labelling

Requests optionally include an `X-Model` header. When absent, the service defaults to `gpt-4.0`. The value flows through:

- **Logs**: available as the `model` field in structured JSON output.
- **Traces**: recorded as a span attribute (`span.model`).
- **Metrics**: appended as the `model` label on custom counters.

Use the load generator's `--models` flag to pin requests to a subset while debugging.

## Simulated Error Rates

| Model | Approximate error rate | Notes |
| ----- | --------------------- | ----- |
| `gpt-4o` / `gpt-4.0` | 2% | Most reliable. |
| `claude-3.5-sonnet` | 3% | Very reliable. |
| `claude-3-opus` | 3% | Similar to sonnet. |
| `gemini-2.0-flash` | 4% | Moderate latency variance. |
| `gemini-1.5-pro` | 6% | Occasional throttling. |
| `gpt-3.5-turbo` | 8% | Legacy tier. |
| `llama-3.3-70b` | 12% | Noisy open-source baseline. |

## Investigative Workflow

1. **Start with Grafana** – Panels on the LLM Reliability dashboard highlight which model or error type is trending upward.
2. **Pivot to logs** – Use the following query in Grafana Explore:
   ```logql
   {job="observability-sandbox"} | json | model="llama-3.3-70b"
   ```
   Add filters for `error_type` or `traceId` to drill down.
3. **Open traces** – From a log line, copy the trace ID and search Tempo for `{ traceId = "..." }` to view the full span tree.
4. **Check metrics** – Compare `llm_errors_total` vs `llm_prompts_total` for the affected model to confirm impact over time.

## Common Failure Modes

- **Timeouts** – Longer durations visible in the `generate` span and latency histograms.
- **Rate limiting** – Bursts in `llm_errors_total{error_type="rate_limit"}` accompanied by HTTP 429 logs.
- **Service unavailable** – Simulated 503 responses that should trigger the error budget burn alerts when sustained.

## Remediation Tips

- Use the load generator with the `chaos` pattern to reproduce errors quickly.
- Adjust model rotation or concurrency if one simulated provider is intentionally noisy during demos.
- When presenting, keep the dashboard timespan to 15 or 30 minutes for a clear signal-to-noise ratio.
