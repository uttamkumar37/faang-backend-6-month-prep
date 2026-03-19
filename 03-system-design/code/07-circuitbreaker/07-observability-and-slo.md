# Observability: Metrics, Tracing, Logging

## The Three Pillars

```
Observability
├── Metrics    — aggregated numbers over time (latency, error rate, throughput)
├── Logging    — timestamped event records with context
└── Tracing    — end-to-end request flow across services
```

---

## Metrics

### The Four Golden Signals (Google SRE)

| Signal | What to Measure | Alert Threshold |
|---|---|---|
| **Latency** | P50, P95, P99, P99.9 | P99 > SLO target |
| **Traffic** | Requests/sec, events/sec | Sudden drop (may indicate issue) |
| **Errors** | 5xx rate, exception rate | > 0.1% error rate |
| **Saturation** | CPU%, memory%, queue depth | CPU > 80%, queue depth growing |

### SLI / SLO / SLA

```
SLI (Service Level Indicator): actual measured metric
   e.g., 99.3% of requests responded in < 200ms

SLO (Service Level Objective): target we aim for
   e.g., 99.9% of requests < 200ms over 30 days

SLA (Service Level Agreement): contractual commitment (subset of SLO)
   e.g., 99.5% uptime — violation → credits to customer

Error budget = 1 - SLO
   99.9% SLO → 0.1% error budget = 43.8 min/month downtime allowed
```

### Prometheus + Grafana Stack

```yaml
# Prometheus pulls metrics from /actuator/prometheus endpoint every 15s
scrape_configs:
  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service:8080']

# Alert rule
- alert: HighErrorRate
  expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01
  for: 5m
  labels:
    severity: critical
```

### Micrometer (Spring Boot)

```java
@Component
public class OrderMetrics {
    private final Counter orderCounter;
    private final Timer orderTimer;
    private final DistributionSummary orderAmount;

    public OrderMetrics(MeterRegistry registry) {
        this.orderCounter = Counter.builder("orders.created")
            .tag("region", "us-east")
            .description("Total orders created")
            .register(registry);
        this.orderTimer = Timer.builder("order.processing.time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        this.orderAmount = DistributionSummary
            .builder("order.amount.usd")
            .register(registry);
    }
}
```

---

## Distributed Tracing

Track a request as it flows through multiple services.

```
Request → Service A → Service B → DB
               ↓
          Trace: traceId = abc123
          Spans:
            A.handleRequest (0–50ms)
              B.processOrder (10–45ms)
                DB.query      (15–40ms)
```

### Trace Propagation (W3C traceparent)

```
HTTP Header: traceparent: 00-{traceId}-{spanId}-01

Service A generates: traceId = abc123, spanId = span001
Service A calls B with: traceparent: 00-abc123-span001-01
Service B reads traceId from header, creates new span: spanId = span002
```

### OpenTelemetry (OTEL)

Standard API across all observability vendors:

```java
// Spring Boot auto-configures OTEL if spring-boot-starter-actuator + micrometer-tracing on classpath
// Just add:
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans

// Manual span:
Tracer tracer = ... // injected
Span span = tracer.nextSpan().name("db-query").start();
try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
    // work
} finally {
    span.end();
}
```

### Tracing Backend Options

| Tool | Type | Notes |
|---|---|---|
| Jaeger | OSS | CNCF project, Kubernetes-native |
| Zipkin | OSS | Simple, Spring Cloud Sleuth compatible |
| AWS X-Ray | Managed | Deep AWS integration |
| Datadog APM | Commercial | Full observability platform |
| Honeycomb | Commercial | High-cardinality traces |

---

## Structured Logging

Log as JSON, include trace context:

```json
{
  "timestamp": "2024-01-15T10:30:01.234Z",
  "level": "ERROR",
  "service": "order-service",
  "traceId": "abc123",
  "spanId": "span002",
  "userId": "user-456",
  "orderId": "order-789",
  "message": "Payment failed",
  "error": "InsufficientFundsException",
  "duration_ms": 45
}
```

### Log Levels

```
TRACE: fine-grained debug (loop iterations)
DEBUG: diagnostic values (method entry/exit with params)
INFO:  business events (order created, user logged in)
WARN:  recoverable issues (retry, degraded mode)
ERROR: failures requiring attention (exception, data integrity)
```

### ELK Stack / OpenSearch

```
Application container → Filebeat → Logstash (filter/transform) → Elasticsearch → Kibana

Or simpler:
Application → CloudWatch Logs → CloudWatch Insights
```

---

## Alerting Strategy

```
Alert only on symptoms, not causes:
  Good: P99 latency > 500ms for 5 min → alert
  Bad:  CPU > 70% → alert (CPU high doesn't mean users are affected)

Alert on error budget burn:
  Burn rate > 14.4x normal → alert (5% of monthly budget gone in 1 hour)
  
Alert fatigue: too many alerts → on-call ignores them.
  Keep weekly alert count < 10. Fix root cause, don't just silence.
```

---

## Interview Tips

- Start with the four golden signals to structure your observability answer.
- Explain traceId propagation across services — shows distributed systems depth.
- Error budget concept shows you understand the SLO/reliability tradeoffs.
- Structured JSON logging so you can search by traceId, userId, etc. in Kibana.
