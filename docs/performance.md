# Load test

Command:

```bash
k6 run perf/shopstack-load.js
```

Scenarios: 10 virtual users for 30 seconds, each one reading the catalogue and the
category list through Nginx. Thresholds: p95 under 500 ms and error rate under 1%.

## Reference run

Environment: local Docker stack (Nginx, Spring Boot server, MySQL 8, Redis, Kafka) on
a 15 GB machine.

| Metric | Value |
|---|---|
| Requests | 1,140 |
| Throughput | 37.6 req/s |
| Checks passed | 100% (1,140 of 1,140) |
| Failed requests | 0.00% |
| Average latency | 13.9 ms |
| p90 | 25.2 ms |
| p95 | 37.5 ms |
| Max | 175.5 ms |

Both thresholds passed. The numbers describe a single-machine demo, not a production
capacity claim.

## Metrics

Prometheus scrapes the three services at `/actuator/prometheus`; the targets page is
in [screenshots/08-prometheus.png](screenshots/08-prometheus.png). JVM, HTTP and
connection-pool metrics are available without extra configuration.
