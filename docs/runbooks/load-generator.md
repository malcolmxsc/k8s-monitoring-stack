# Load Generator Runbook

The repository ships with [`load-generator.sh`](../../load-generator.sh), a lightweight client for producing repeatable traffic against the `/generate` endpoint. Use this runbook as a reference when demoing the system or seeding traces, metrics, and logs.

## Prerequisites

- Bash 5+
- `curl`
- Access to a running instance of the application (local or remote)

## Basic Usage

```bash
./load-generator.sh --pattern steady --base-url "http://localhost:8080"
```

The script will authenticate with the default credentials (`demo` / `observability!`), send requests in a loop, and print a summary of successes and failures.

### Common Flags

| Flag | Description |
| ---- | ----------- |
| `--pattern <steady|bursty|chaos>` | Selects a request distribution. `steady` is safe for demos, `bursty` generates short spikes, and `chaos` introduces random latency and errors. |
| `--base-url <url>` | Target application URL. Default: `http://localhost:8080`. |
| `--duration <seconds>` | Total runtime for the generator. Default: runs until interrupted. |
| `--concurrency <n>` | Number of concurrent workers. Use cautiously in small environments. |
| `--skip-health-check` | Bypass the initial `/actuator/health` probe. |
| `--models <m1,m2,...>` | Override the default rotation of models sent in the `X-Model` header. |

## Example Scenarios

### Local smoke test

```bash
./gradlew bootRun &
./load-generator.sh --pattern steady --base-url "http://localhost:8080" --duration 60
```

### Generate labelled traces for a specific model

```bash
./load-generator.sh --pattern steady \
  --models "claude-3.5-sonnet" \
  --base-url "http://localhost:8080" \
  --duration 120
```

### Exercise error paths

```bash
./load-generator.sh --pattern chaos --base-url "http://localhost:8080" --duration 120
```

## Operational Tips

- The generator prints a summary every 30 seconds. Watch for spikes in error percentage while validating alert rules.
- Keep concurrency low when pointing at shared demo clusters to avoid noisy-neighbour effects.
- Combine the script with `jq` to inspect responses for latency and token statistics:

  ```bash
  ./load-generator.sh --pattern steady --duration 30 | jq .
  ```

- When running against Kubernetes, set `BASE_URL` to the load balancer or port-forward URL before executing the script.

## Troubleshooting

- **401 Unauthorized**: confirm the credentials exported as `APP_USER` and `APP_PASSWORD` match the environment.
- **Connection refused**: ensure the application is reachable (use `kubectl port-forward` or double-check the service address).
- **No traces appear**: the generator intentionally staggers requests. Use the `chaos` pattern or increase `--concurrency` to produce more spans quickly.
