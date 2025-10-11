# Service Level Objectives (SLOs) and Alerts

## Overview
This document defines the Service Level Indicators (SLIs), Service Level Objectives (SLOs), and alerting strategy for the observability-sandbox application.

---

## SLO Definitions

### 1. **Latency SLO**

#### **Objective**
- **P90 Latency**: 90% of requests should complete within **500ms**
- **P95 Latency**: 95% of requests should complete within **1 second**

#### **Rationale**
These thresholds ensure a responsive user experience while allowing for occasional slower requests due to cold starts or network variability.

#### **Measurement**
```promql
# P90 Latency
histogram_quantile(0.90, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))

# P95 Latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))
```

#### **Alert Conditions**
- **Warning**: P90 > 500ms for 2 minutes
- **Warning**: P95 > 1s for 2 minutes

---

### 2. **Availability SLO**

#### **Objective**
- **Availability**: 99.9% uptime (error rate < 0.1%)

#### **Rationale**
Ensures high reliability with allowance for approximately 43 minutes of downtime per month.

#### **Measurement**
```promql
# Error Rate
(
  sum(rate(http_server_requests_seconds_count{uri="/generate",status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/generate"}[5m]))
)
```

#### **Alert Conditions**
- **Critical**: Error rate > 0.1% for 2 minutes
- **Warning**: Client error rate > 5% for 5 minutes

---

### 3. **Service Health**

#### **Objectives**
- Service must be reachable and responding
- Request rate within expected bounds
- Resource utilization within safe limits

#### **Measurements**
```promql
# Service Up
up{job="spring-app"}

# Request Rate
rate(http_server_requests_seconds_count{uri="/generate"}[1m])

# Memory Usage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"})
```

#### **Alert Conditions**
- **Critical**: Service down for > 1 minute
- **Warning**: Request rate > 100 req/s for 5 minutes
- **Info**: Request rate < 0.01 req/s for 10 minutes
- **Warning**: Heap memory > 85% for 5 minutes

---

## Alert Severity Levels

### **Critical**
Immediate action required. Service is down or severely degraded.
- Service Down
- High Error Rate (> 0.1%)

### **Warning**
Action required soon. SLO breach or approaching resource limits.
- High P90/P95 Latency
- High Client Error Rate
- High Memory/Thread Usage
- High GC Time

### **Info**
Informational alerts for monitoring anomalies.
- Low Request Rate
- Low LLM Throughput
- High Token Usage

---

## Alert Rules File

All alerts are defined in: `observability/prometheus/alert-rules.yml`

### Alert Groups
1. **slo_alerts**: SLO-related alerts for latency and availability
2. **service_health**: General service health monitoring
3. **llm_service_alerts**: LLM-specific metrics and anomalies
4. **resource_alerts**: JVM and resource utilization

---

## Monitoring Queries

### Key Metrics Dashboard Queries

#### Request Rate
```promql
rate(http_server_requests_seconds_count{uri="/generate"}[5m])
```

#### Error Rate Percentage
```promql
100 * (
  sum(rate(http_server_requests_seconds_count{uri="/generate",status=~"5.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/generate"}[5m]))
)
```

#### Latency Percentiles
```promql
# P50
histogram_quantile(0.50, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))

# P90
histogram_quantile(0.90, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))

# P95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))

# P99
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{uri="/generate"}[5m]))
```

#### LLM Performance
```promql
# Prompts per second
rate(llm_prompts_total[5m])

# Average request tokens
rate(llm_request_tokens_sum[5m]) / rate(llm_request_tokens_count[5m])

# Average response tokens
rate(llm_response_tokens_sum[5m]) / rate(llm_response_tokens_count[5m])
```

#### Resource Utilization
```promql
# Heap memory percentage
100 * (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"})

# Thread count
jvm_threads_live_threads

# GC time percentage
100 * rate(jvm_gc_pause_seconds_sum[5m])
```

---

## SLO Burn Rate

### Error Budget
With 99.9% availability SLO:
- **Error Budget**: 0.1% of requests can fail
- **Monthly Budget**: ~43 minutes of downtime
- **Daily Budget**: ~1.4 minutes

### Burn Rate Alerts (Future Enhancement)
```promql
# Fast burn: 14.4x rate over 1 hour
(
  sum(rate(http_server_requests_seconds_count{uri="/generate",status=~"5.."}[1h]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/generate"}[1h]))
) > 0.00144

# Slow burn: 3x rate over 6 hours
(
  sum(rate(http_server_requests_seconds_count{uri="/generate",status=~"5.."}[6h]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/generate"}[6h]))
) > 0.0003
```

---

## Testing Alerts

### Trigger High Latency Alert
Send requests that take longer than expected:
```bash
# The application simulates 100-1000ms latency
# To breach P90 > 500ms, you need consistent high latency
for i in {1..100}; do
  curl -X POST http://localhost:8080/generate \
    -H "Content-Type: application/json" \
    -d '{"prompt":"test"}' &
done
wait
```

### Trigger Error Rate Alert
Send requests that cause errors:
```bash
# This would require modifying the app to have error conditions
# Or simulating 5xx responses at the load balancer level
```

### Check Alert Status
1. Open Prometheus UI: http://localhost:9090/alerts
2. Check "Firing" alerts
3. View alert history and states

---

## Integration with Grafana

### Viewing Alerts in Grafana
1. Navigate to **Alerting** → **Alert rules**
2. Add Prometheus as a data source for alerting
3. Import the SLO dashboard (to be created)

### Creating Grafana Managed Alerts (Optional)
Grafana can also manage alerts directly:
- More advanced notification routing
- Silences and inhibition rules
- Integration with PagerDuty, Slack, email, etc.

---

## Next Steps

1. ✅ **Defined SLOs**: Latency and availability objectives
2. ✅ **Created Alert Rules**: Comprehensive alerting in Prometheus
3. ⏭️ **Configure Notifications**: Set up Alertmanager or Grafana alerting
4. ⏭️ **Create SLO Dashboard**: Visualize SLO compliance in Grafana
5. ⏭️ **Test Alerts**: Validate alert triggers and notifications
6. ⏭️ **Document Runbooks**: Create response procedures for each alert

---

## References

- [Prometheus Alerting Rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [Google SRE Book - Service Level Objectives](https://sre.google/sre-book/service-level-objectives/)
- [Grafana Alerting Documentation](https://grafana.com/docs/grafana/latest/alerting/)
