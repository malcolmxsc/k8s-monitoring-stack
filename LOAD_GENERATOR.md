# Load Generator Usage Guide

## 🚀 Quick Start

The load generator creates realistic, varied traffic with different users, regions, and request patterns.

### Run Interactive Menu
```bash
./load-generator.sh
```

### Run Specific Pattern
```bash
# Quick test (5 requests)
./load-generator.sh test

# Steady traffic (20 requests, 1s interval)
./load-generator.sh steady

# Burst traffic (traffic spikes)
./load-generator.sh burst

# Regional traffic (region-by-region)
./load-generator.sh regional

# User sessions (realistic user behavior)
./load-generator.sh session

# Continuous traffic (runs until Ctrl+C)
./load-generator.sh continuous

# All patterns sequentially
./load-generator.sh all
```

## 📊 Traffic Patterns

### 1. Steady Traffic
- **Duration**: ~20 seconds
- **Pattern**: 20 requests with 1-second intervals
- **Use case**: Baseline metrics, testing steady-state performance

### 2. Burst Traffic
- **Duration**: ~15 seconds (3 bursts)
- **Pattern**: 3 bursts of 5 parallel requests with cooldowns
- **Use case**: Testing spike handling, alert triggering

### 3. Regional Traffic
- **Duration**: ~10 seconds
- **Pattern**: Sequential requests from each region (7 regions × 3 requests)
- **Use case**: Regional analysis, geo-distribution testing

### 4. User Sessions
- **Duration**: ~30 seconds
- **Pattern**: 5 user sessions with 2-5 requests each
- **Use case**: Realistic user behavior, session-based analysis

### 5. Continuous Traffic
- **Duration**: Until stopped (Ctrl+C)
- **Pattern**: Varied requests with random 0.5-3s delays
- **Use case**: Long-running load, dashboard population

## 👥 Generated Users

The load generator uses 20 realistic usernames:
```
alice.smith, bob.johnson, carol.williams, david.brown, emma.jones,
frank.garcia, grace.martinez, henry.rodriguez, iris.lopez, jack.wilson,
karen.anderson, leo.thomas, maria.taylor, nathan.moore, olivia.jackson,
peter.martin, quinn.lee, rachel.perez, steve.thompson, tina.white
```

## 🌍 Regions

Traffic is distributed across 7 AWS-like regions:
```
us-east-1, us-west-1, us-west-2,
eu-west-1, eu-central-1,
ap-southeast-1, ap-northeast-1
```

## 🤖 AI Models

Requests are distributed across 10 different LLM models:
```
GPT:     gpt-4.0, gpt-4o, gpt-3.5-turbo
Claude:  claude-3.5-sonnet, claude-3-opus, claude-3-haiku
Gemini:  gemini-2.0-flash, gemini-1.5-pro
Other:   llama-3.3-70b, mistral-large
```

## 📝 Example Queries After Load Generation

### Grafana → Explore → Loki

**View all unique users:**
```logql
{job="los-app"} | json | userId != "demo-user" | distinct userId
```

**Traffic by region:**
```logql
sum by (region) (
  count_over_time({job="los-app"} | json [5m])
)
```

**Requests from specific user:**
```logql
{job="los-app"} | json | userId="alice.smith"
```

**Filter by model:**
```logql
{job="los-app"} | json | model="claude-3.5-sonnet"
```

**Regional latency analysis:**
```logql
{job="los-app"} | json | unwrap latencyMs | quantile_over_time(0.95, [5m]) by (region)
```

**Model latency comparison:**
```logql
{job="los-app"} | json | unwrap latencyMs | quantile_over_time(0.95, [5m]) by (model)
```

### Grafana → Explore → Tempo

**Search by user:**
```
{ span.userId = "alice.smith" }
```

**Search by region:**
```
{ span.region = "eu-west-1" }
```

**Search by model:**
```
{ span.model = "claude-3.5-sonnet" }
```

**All non-demo traffic:**
```
{ span.userId != "demo-user" }
```

### Grafana → Explore → Prometheus

**Requests per user (top 10):**
```promql
topk(10, 
  sum by (userId) (
    rate(http_server_requests_seconds_count{uri="/generate"}[5m])
  )
)
```

**Regional request distribution:**
```promql
sum by (region) (
  rate(http_server_requests_seconds_count{uri="/generate"}[5m])
)
```

**Model usage distribution:**
```promql
sum by (model) (
  rate(http_server_requests_seconds_count{uri="/generate"}[5m])
)
```

**Latency by model:**
```promql
histogram_quantile(0.95,
  sum by (model, le) (
    rate(http_server_requests_seconds_bucket{uri="/generate"}[5m])
  )
)
```

## 💡 Tips

### For Dashboard Testing
```bash
# Run continuous traffic in background
./load-generator.sh continuous &

# Let it run while you explore dashboards
# Stop with: pkill -f load-generator
```

### For Alert Testing
```bash
# Generate burst traffic to trigger HighLatencyP90 alert
./load-generator.sh burst

# Check alerts: http://localhost:9090/alerts
```

### For Demo/Presentation
```bash
# Run all patterns to show comprehensive data
./load-generator.sh all

# Then explore logs, traces, and metrics in Grafana
```

## 🎨 Output Example

```
[08:18:37] User: olivia.jackson | Region: eu-west-1 | Model: gpt-4o
  ✓ Status: 200 | Latency: 510ms | Tokens: 44

[08:18:38] User: nathan.moore | Region: us-west-1 | Model: gpt-4.0
  ✓ Status: 200 | Latency: 240ms | Tokens: 87

[08:18:39] User: quinn.lee | Region: us-west-2 | Model: claude-3-opus
  ✓ Status: 200 | Latency: 223ms | Tokens: 78
```

## 🔧 Customization

Edit `load-generator.sh` to customize:
- **USERS array**: Add/modify usernames
- **REGIONS array**: Change regions
- **MODELS array**: Add/modify AI models
- **PROMPTS array**: Modify request prompts
- **Timing**: Adjust `sleep` values for different intervals

## ⚠️ Requirements

- Application must be running: `./gradlew bootRun`
- Requires: `curl`, `jq` (for JSON parsing)
- Bash 4.0+

## 🐛 Troubleshooting

**Error: Service not running**
```bash
# Start the application first
./gradlew bootRun
```

**No color output**
```bash
# Disable colors in the script
COLORS=false ./load-generator.sh
```

**Permission denied**
```bash
# Make executable
chmod +x load-generator.sh
```
