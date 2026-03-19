# Observability Patterns

## Why Observability?

A system is **observable** when you can understand its internal state from its external outputs —
without deploying new code.

```
Monolith → one log file, one process, one deployment
Microservices → 50+ services, cross-service traces, dynamic instances
   → you CANNOT debug by SSH-ing into a box any more
```

**The three pillars:**

| Pillar  | Question answered              | Tool examples                          |
|---------|-------------------------------|----------------------------------------|
| Metrics | Is the system healthy *now*?  | Prometheus, Micrometer, CloudWatch     |
| Logs    | What happened in detail?      | ELK, Loki, CloudWatch Logs, Splunk     |
| Traces  | Where did this request go?    | Jaeger, Zipkin, OpenTelemetry, X-Ray   |

---

## Metrics

### Types

| Type      | Meaning                       | Reset? | Example                         |
|-----------|-------------------------------|--------|---------------------------------|
| Counter   | Monotonically increasing      | No     | `http_requests_total`           |
| Gauge     | Current point-in-time value   | Yes    | `jvm_heap_used_bytes`           |
| Histogram | Distribution of values        | No     | `http_request_duration_seconds` |
| Summary   | Pre-computed percentiles      | No     | `rpc_duration_seconds_p99`      |

### RED Method (service-level — for request-driven services)
- **R**ate — requests per second
- **E**rror rate — % of requests failing
- **D**uration — latency distribution (p50, p95, p99)

### USE Method (resource-level — for infrastructure)
- **U**tilization — % of time resource is busy
- **S**aturation — how much extra work is queued
- **E**rrors — error events per unit time

### Four Golden Signals (Google SRE)
1. Latency
2. Traffic
3. Errors
4. Saturation

### Histogram vs Summary
```
Histogram: observe() puts value into configurable buckets
   +++ aggregatable across instances (add bucket counts)
   --- requires predefined bucket boundaries

Summary: client pre-computes φ-quantiles
   +++ accurate for a single instance
   --- not aggregatable (cannot merge 10 instances)
```

---

## SLO / SLI / SLA / Error Budget

```
SLI (indicator): measured value of a behaviour
   e.g. "99.2% of requests completed in < 200ms over the past 30 days"

SLO (objective): target threshold for an SLI
   e.g. "99.9% of requests must complete in < 200ms"

SLA (agreement): contractual commitment with penalties
   e.g. "if SLO breached, customer gets credit"

Error budget: how much unreliability you can afford
   error_budget = 1 - SLO = 0.1% of request time
               = 30 * 24 * 60 * 0.001 = 43.2 minutes/month
```

### Burn Rate Alerting
```
burn_rate = error_rate / error_budget_rate

burn_rate > 14.4  → page immediately (fast burn: budget gone in 2 hours)
burn_rate >  6    → page within 6 hours
burn_rate >  1    → warning (burning budget, but won't breach in 30 days)
```

---

## Distributed Tracing

### Concepts
```
Trace: end-to-end journey of a single request
  └─ Span: one unit of work (one service call, one DB query)
       ├─ traceId: identifies the whole trace
       ├─ spanId: identifies this specific span
       ├─ parentSpanId: links to caller's span
       ├─ startTime, duration
       └─ tags/attributes: key-value annotations

TraceContext propagation:
  HTTP header: traceparent: 00-{traceId}-{spanId}-{flags}
  Message queue: embed in message headers
  gRPC metadata
```

### OpenTelemetry (OTEL)
```
Language SDK → OTEL Collector → Backend (Jaeger, Zipkin, DataDog, Tempo)

Instrumentation types:
  Auto: Java agent rewrites bytecode — zero code change
  Manual: explicit Span.start() / Span.end() calls
```

### Sampling
```
Head-based sampling: decision at trace root (random %)
  Pro: simple  Con: misses rare errors

Tail-based sampling: route to collector, decide after seeing full trace
  Pro: always capture errors/slow traces
  Con: expensive (must buffer full trace before deciding)
```

---

## Structured Logging

### Why structured?
```
Unstructured: "User 12345 placed order 67890 at 2024-01-15T10:23:45"
   grep-able but not machine-parseable

Structured (JSON): {"ts":"2024-01-15T10:23:45Z","level":"INFO",
  "msg":"order placed","userId":12345,"orderId":67890,"traceId":"abc123"}
   → queryable in Kibana, Loki, Splunk
```

### Correlation IDs
```
Request arrives at API gateway → generate correlationId
Propagate via MDC (Mapped Diagnostic Context) to all log lines
  → log.info("processing", "correlationId", MDC.get("correlationId"))
Service-to-service: pass in HTTP header X-Correlation-ID

Query: correlationId=abc123 → all log lines for that request
```

### Log Levels (correct usage)
| Level | When to use                                    |
|-------|------------------------------------------------|
| ERROR | Unhandled exception; needs immediate attention |
| WARN  | Degraded state; system still working           |
| INFO  | Normal business events (order placed, etc.)    |
| DEBUG | Detailed flow (disable in production)          |
| TRACE | Very verbose (packet-level, disable always)    |

---

## Health Checks

```
Liveness probe:  Is the process alive? (restart if fails)
Readiness probe: Is the service ready for traffic? (remove from LB if fails)
Startup probe:   Is the app still initialising? (prevents premature liveness checks)
```

### Composite health
```
/health/live  → JVM alive? → 200/503
/health/ready → DB reachable? + cache connected? + external APIs up? → 200/503

Kubernetes uses these to know when to route traffic or restart
```

---

## Alert Design

### Symptom-based vs Cause-based
```
Cause-based (bad):  "CPU > 80% for 5 minutes"
   → triggers constantly; hard to know impact; noisy

Symptom-based (good): "Error rate > 1% for 5 minutes"
   → directly correlates to user impact
```

### Alert fatigue
- Too many alerts → on-call engineer ignores them
- Fix: alert on SLO burn rate, not raw metrics
- Use `for: 5m` in Prometheus to avoid flapping

---

## Interview Quick Reference

| Topic              | Key number / formula                                 |
|--------------------|------------------------------------------------------|
| p99 latency        | 99th percentile: 99% of requests faster than this    |
| Error budget       | 1 - SLO (99.9% SLO = 43.2 min/month)                |
| Burn rate page     | > 14.4× budget consumption → immediate page          |
| Trace header       | `traceparent: 00-{16-byte-traceId}-{8-byte-spanId}-{flags}` |
| Histogram buckets  | Cumulative! le="0.1" includes all ≤ 100ms            |
| Counter vs Gauge   | Counter never decreases, Gauge can go up or down     |
| MDC                | Thread-local correlation ID propagation in logging   |
| OTEL               | Vendor-neutral observability SDK (metrics+logs+traces)|
