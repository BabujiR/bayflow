# Observability: logs, metrics, traces and alerts

What Ramplight gives an operator, and how to turn each part on.

## Request ids

nginx gives every request an id (`X-Request-Id`; one sent by a proxy in front is kept). Each
service puts it in the log context, returns it in the response's `X-Request-Id` header and
passes it on when it calls another service. Admin > Activity events record it too.

So when a user reports a problem, ask for the time (or the `X-Request-Id` from the browser's
network tab) and search the logs of every service for that id.

## Logs

Every log line carries `[service,traceId,spanId,requestId]`. In production
(`deploy/gcp/docker-compose.yml`) the services log JSON, one object per line, in the Elastic
Common Schema (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), which Cloud Logging, Loki and Elastic
read directly. Locally they log plain text.

```bash
docker compose logs auth-service | grep 'the-request-id'
```

## Metrics

Each service publishes Prometheus metrics at `/actuator/prometheus` (port 8081 to 8084). It is
reachable inside the Docker network only: nginx never routes `/actuator`, and the production
compose publishes no service ports.

Among them:

| Metric | What it is |
|---|---|
| `http_server_requests_seconds_*` | Requests by URI, method and status, with latency buckets (p95, p99) |
| `bayflow_outbox_pending` | Outbox messages waiting to be delivered (appointment, workshop) |
| `bayflow_outbox_failed` | Messages given up on: someone has to look (README, "Outboxes") |
| `bayflow_outbox_oldest_pending_seconds` | Age of the oldest message waiting |
| `jvm_*`, `hikaricp_*`, `process_*` | Memory, GC, threads, database pool, CPU |

### Prometheus on the VM

```bash
cd deploy/gcp
docker compose --profile monitoring up -d
# From your computer: open http://localhost:9090 through an SSH tunnel
gcloud compute ssh <vm> -- -L 9090:localhost:9090
```

The configuration is in `deploy/monitoring/prometheus.yml` and the alert rules in
`deploy/monitoring/alerts.yml`: a service down, more than 2% of requests failing, p95 latency
above 1 s, outbox messages given up on or waiting over an hour, heap above 90%. Prometheus shows
firing alerts under *Alerts*; to be e-mailed or messaged, add an Alertmanager (commented out in
`prometheus.yml`). A managed alternative: Grafana Cloud or Google Managed Prometheus scraping the
same endpoints.

## Traces

Micrometer Tracing with OpenTelemetry: trace and span ids are always in the log lines. Spans are
exported (OTLP over HTTP) only when a collector is set up:

| Setting | Default | |
|---|---|---|
| `BAYFLOW_TRACING_EXPORT` | `false` | `true` to export spans |
| `BAYFLOW_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | the collector (Jaeger, Tempo, Grafana Cloud, an OTel collector) |
| `BAYFLOW_TRACING_SAMPLING` | `0.1` | share of requests traced |

Calls between the services carry the trace (`traceparent`) as well as the request id, so one
request is one trace across every service it touches.
