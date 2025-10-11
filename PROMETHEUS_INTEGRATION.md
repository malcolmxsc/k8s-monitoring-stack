# Prometheus Integration & Dashboard Fix

## Summary

Fixed the "too many outstanding requests" error and created a professional Prometheus-based dashboard for LLM error monitoring.

## Issues Fixed

### 1. **"Too many outstanding requests" Error**
- **Root Cause**: The Loki-based dashboard was making expensive queries that computed error rates by parsing all logs in real-time
- **Solution**: Created a new Prometheus-based dashboard that uses pre-aggregated metrics instead of log parsing

### 2. **Missing Prometheus Metrics Tags**
- **Root Cause**: The `llm_errors_total` counter was pre-registered at startup with only the `service` tag, preventing dynamic tags (model, error_type) from being added later
- **Solution**: Removed the pre-registration of `errorsTotal` counter, allowing it to be created dynamically with full tags when errors occur

## Changes Made

### Code Changes

**File**: `src/main/java/com/example/observability_sandbox/core/LlmService.java`

```java
// REMOVED: Pre-registered counter (conflicted with dynamic tags)
// this.errorsTotal = Counter.builder("llm_errors_total")
//         .description("Total number of LLM errors")
//         .tag(SERVICE_TAG, SERVICE_NAME)
//         .register(registry);

// NOW: Counter is created dynamically with full tags when errors occur
private void incrementErrorCounter(String model, String errorType) {
    registry.counter("llm_errors_total",
            "model", model != null ? model : "unknown",
            "error_type", errorType,
            SERVICE_TAG, SERVICE_NAME)
            .increment();
}
```

### New Dashboard

**File**: `observability/grafana/provisioning/dashboards/llm-prometheus-dashboard.json`

Created a new professional dashboard called **"LLM Model Reliability (Prometheus)"** with:

1. **Model Error Rates (%)** - Stat panel with color-coded thresholds
   - Green: <5% error rate
   - Yellow: 5-10%
   - Orange: 10-15%
   - Red: >15%

2. **Total Errors by Model** - Vertical bar chart showing error counts per model

3. **Errors by Type** - Donut chart showing distribution of error types (timeout, rate_limit, context_length_exceeded, etc.)

4. **Error Rate Over Time** - Time series showing error trends for each model + error type combination

5. **Request Success Rate** - Gauge showing overall system health (0-100%)

6. **Total Requests** - Stat panel showing successful vs failed requests

7. **Recent Error Logs** - Loki logs panel (only for detailed log viewing, not aggregation)

## Prometheus Metrics Now Available

```promql
# Error counts by model and error type
llm_errors_total{model="llama-3.3-70b", error_type="timeout", service="los-app"}

# Total prompts processed
llm_prompts_total{service="los-app"}

# Error rate calculation
rate(llm_errors_total[5m]) / (rate(llm_prompts_total[5m]) + rate(llm_errors_total[5m]))
```

## Why Prometheus is Better for This Use Case

| Aspect | Loki (Log-based) | Prometheus (Metrics-based) |
|--------|------------------|---------------------------|
| **Query Performance** | Slow - must parse all logs | Fast - pre-aggregated |
| **Resource Usage** | High - scans log files | Low - queries indexed metrics |
| **Aggregation** | Computed at query time | Pre-computed at scrape time |
| **Time Series** | Not optimized | Optimized for time series |
| **Dashboard Load** | Can cause "too many requests" | Handles high refresh rates |

## How to Access

1. Open Grafana: http://localhost:3000
2. Navigate to Dashboards
3. Select **"LLM Model Reliability (Prometheus)"**

## Testing

Generate test traffic:
```bash
# Generate traffic across multiple models
for model in "llama-3.3-70b" "gpt-3.5-turbo" "gemini-1.5-pro" "gpt-4o" "claude-3-opus"; do 
  for i in {1..20}; do 
    curl -s -X POST http://localhost:8080/generate \
      -H "Content-Type: application/json" \
      -H "X-User-Id: user$i" \
      -H "X-Region: us-east-1" \
      -H "X-Model: $model" \
      -d "{\"prompt\":\"test $i\"}" > /dev/null
  done
done
```

## Verification

Check Prometheus metrics:
```bash
# View error counts
curl -s 'http://localhost:9090/api/v1/query?query=llm_errors_total' | jq

# View metrics in Prometheus UI
open http://localhost:9090
```

## Architecture

```
┌─────────────────┐
│  Spring Boot    │
│   Application   │
│                 │
│  - LlmService   │──┐
│  - Error Sim    │  │ Increments counters
└─────────────────┘  │ with tags (model, error_type)
                     │
                     ▼
              ┌─────────────┐
              │ Micrometer  │
              │  Registry   │
              └─────────────┘
                     │
                     │ Exposes at
                     │ /actuator/prometheus
                     ▼
              ┌─────────────┐
              │ Prometheus  │◀──── Scrapes every 5s
              │   (port     │
              │    9090)    │
              └─────────────┘
                     │
                     │ Queries
                     ▼
              ┌─────────────┐
              │   Grafana   │
              │  Dashboard  │
              │  (port 3000)│
              └─────────────┘
```

## Expected Error Rates (Simulated)

Based on the model reliability simulation in `LlmService.java`:

- **gpt-4.0, gpt-4o**: 2% error rate (most reliable)
- **claude-3-opus**: 3% error rate
- **gemini-2.0-flash**: 4% error rate
- **gemini-1.5-pro**: 6% error rate
- **gpt-3.5-turbo**: 8% error rate
- **llama-3.3-70b**: 12% error rate (least reliable)

The dashboard will color-code these automatically:
- Green: gpt-4.0, gpt-4o, claude-3-opus
- Yellow: gemini-2.0-flash, gemini-1.5-pro
- Orange/Red: gpt-3.5-turbo, llama-3.3-70b
