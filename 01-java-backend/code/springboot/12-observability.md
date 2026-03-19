# Spring Boot Observability — Metrics, Logs, and Traces

---

## 1. The Three Pillars of Observability

```
Observability = Metrics + Logs + Traces

Metrics:  What is happening?      → aggregated numbers over time
          "p99 latency = 450ms"
          "error rate = 0.1%"
          "active connections = 42"

Logs:     What happened?          → discrete events, full details
          "2024-01-15T10:23:45 ERROR Order 123 failed: DB timeout"

Traces:   Why did it happen?      → request flow across services
          POST /orders → OrderService → InventoryService → PaymentGateway
                  12ms →         3ms →           4ms →           180ms
```

Spring Boot 3.x unifies all three via **Micrometer Observation API** — one annotation (`@Observed`) produces metrics, logs, and traces.

---

## 2. Micrometer Metrics

Micrometer is the metrics facade for Spring Boot — works with Prometheus, Datadog, CloudWatch, Graphite, etc.

### 2.1 Core Meter Types

```java
@Service
public class OrderService {

    private final MeterRegistry registry;

    // Counter — monotonically increasing number
    private final Counter ordersCreated;
    private final Counter ordersFailed;

    // Timer — measures duration + count + throughput
    private final Timer orderProcessingTimer;

    // Gauge — instantaneous value (snapshots)
    private final AtomicInteger pendingOrders;

    // DistributionSummary — measures values (not time) with histogram
    private final DistributionSummary orderValueSummary;

    public OrderService(MeterRegistry registry, OrderRepository repo) {
        this.registry = registry;

        this.ordersCreated = Counter.builder("orders.created")
            .description("Total orders created")
            .tag("region", "us-east-1")  // dimensions for filtering
            .register(registry);

        this.ordersFailed = Counter.builder("orders.failed")
            .description("Total failed orders")
            .register(registry);

        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Time to process an order")
            .publishPercentiles(0.5, 0.95, 0.99) // client-side percentiles
            .publishPercentileHistogram()           // server-side (Prometheus compatible)
            .register(registry);

        this.pendingOrders = new AtomicInteger(0);
        Gauge.builder("orders.pending", pendingOrders, AtomicInteger::get)
             .description("Orders waiting to be processed")
             .register(registry);

        this.orderValueSummary = DistributionSummary.builder("orders.value")
            .baseUnit("dollars")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public Order createOrder(OrderRequest req) {
        return orderProcessingTimer.record(() -> {  // auto-measure duration
            try {
                pendingOrders.incrementAndGet();
                Order order = doCreateOrder(req);
                ordersCreated.increment();
                orderValueSummary.record(order.getValue().doubleValue());
                return order;
            } catch (Exception e) {
                ordersFailed.increment(Tags.of("reason", e.getClass().getSimpleName()));
                throw e;
            } finally {
                pendingOrders.decrementAndGet();
            }
        });
    }
}
```

### 2.2 `@Timed` — Declarative Timing

```java
// Auto-timed via AOP — requires TimedAspect bean
@Configuration
public class MicrometerConfig {
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

@Service
public class OrderService {
    @Timed(value = "orders.processing", description = "Time to process order",
           percentiles = {0.5, 0.95, 0.99}, histogram = true)
    public Order createOrder(OrderRequest req) { ... }
}
```

---

## 3. Spring Boot Actuator — Built-in Metrics

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
  endpoint:
    health:
      show-details: when-authorized  # never|when-authorized|always
      probes:
        enabled: true  # /actuator/health/liveness and /actuator/health/readiness
  metrics:
    tags:
      application: ${spring.application.name}  # tag every metric with app name
      region: ${cloud.region:local}
```

**Key built-in metrics** (auto-collected by Spring Boot):

| Metric | Description |
|---|---|
| `http.server.requests` | Timer for every HTTP endpoint — has tags: uri, method, status |
| `jvm.memory.used` | Heap and metaspace usage |
| `jvm.gc.pause` | GC pause durations by action and cause |
| `hikaricp.connections.pending` | Waiting for a DB connection |
| `hikaricp.connections.acquire` | Time to acquire a connection |
| `system.cpu.usage` | Process CPU load |
| `executor.pool.size` | Thread pool sizes |
| `spring.kafka.consumer.lag` | Kafka consumer lag per partition |

---

## 4. Prometheus Integration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```yaml
# Prometheus scrape config (prometheus.yml)
scrape_configs:
  - job_name: 'order-service'
    scrape_interval: 15s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['order-service:8080']
    # Or for Kubernetes:
    # kubernetes_sd_configs: ...
```

```
# Format of actuator/prometheus output:
http_server_requests_seconds_count{method="POST",status="200",uri="/api/v1/orders"} 1234
http_server_requests_seconds_sum{method="POST",status="200",uri="/api/v1/orders"}  456.789
http_server_requests_seconds_bucket{le="0.05",...} 1200
http_server_requests_seconds_bucket{le="0.5",...}  1230
http_server_requests_seconds_bucket{le="1.0",...}  1234

# Prometheus query to compute p99:
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
```

---

## 5. Structured Logging

**Why structured logging?** Plain text logs are hard to query in Elasticsearch/CloudWatch/Loki. Structured JSON logs enable filtering by field (e.g., `traceId = "abc"`, `userId = "user-1"`).

```xml
<!-- Logback JSON with logstash-logback-encoder -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

```xml
<!-- logback-spring.xml -->
<configuration>
    <springProfile name="prod">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>traceId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="local,test">
        <include resource="org/springframework/boot/logging/logback/base.xml"/>
    </springProfile>
</configuration>
```

### MDC — Per-Request Context in Logs

```java
// Add correlationId to every log line in a request
@Component
public class MdcFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String correlationId = ((HttpServletRequest) req).getHeader("X-Correlation-Id");
        if (correlationId == null) correlationId = UUID.randomUUID().toString();

        MDC.put("correlationId", correlationId);    // propagated to all log statements
        MDC.put("userId", extractUserId(req));
        ((HttpServletResponse) res).setHeader("X-Correlation-Id", correlationId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear(); // CRITICAL: clear after request to prevent leaks in thread pools
        }
    }
}

// Now every log statement automatically includes correlationId and userId:
log.info("Processing order {}", orderId);
// Output: {"timestamp":"...","level":"INFO","message":"Processing order 123",
//          "correlationId":"abc-def","userId":"user-1"}
```

---

## 6. Distributed Tracing

Distributed tracing follows a request across service boundaries. Each request gets a **traceId**; each operation within the trace is a **span**:

```
Request: POST /api/v1/checkout (traceId=abc123)
│
├── Span: OrderService.createOrder      (2ms)
│   ├── Span: SELECT FROM orders         (0.5ms)
│   └── Span: INSERT INTO orders         (1ms)
│
├── Span: HTTP POST /inventory/reserve  (20ms)
│   └── [propagated via HTTP header: traceparent=00-abc123-span2-01]
│
└── Span: HTTP POST /payment/charge     (150ms)
    └── [propagated via HTTP header: traceparent=00-abc123-span3-01]
```

### Setup — Micrometer Tracing with Brave/Zipkin or OTel/Jaeger

```xml
<!-- Option A: Brave (Zipkin) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>

<!-- Option B: OpenTelemetry (Jaeger, Grafana Tempo) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # sample 10% of requests in production (1.0 = all)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

**Trace context propagation** happens automatically through Micrometer Tracing:
- HTTP requests: `traceparent` header (W3C standard) or `X-B3-TraceId` (Zipkin B3)  
- Kafka messages: headers propagated by Spring Kafka auto-instrumentation
- `@Async` methods, `CompletableFuture`: requires manual context propagation or `@Observed`

---

## 7. `@Observed` — Unified Metrics + Tracing

Spring Boot 3.x `@Observed` creates both a metric (timer) AND a trace span in one annotation:

```java
@Configuration
public class ObservabilityConfig {
    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry); // required to make @Observed work as AOP
    }
}

@Service
public class OrderService {
    @Observed(
        name = "order.creation",
        contextualName = "create-order",
        lowCardinalityKeyValues = {"service", "order-service"} // tags
    )
    public Order createOrder(OrderRequest req) {
        // Automatically:
        // 1. Creates a Timer metric: "order.creation" with matching tags
        // 2. Creates a trace span: "create-order"
        // 3. Records exception if thrown
        return doCreate(req);
    }
}
```

---

## 8. Health Indicators

```java
// Built-in health indicators (auto-configured):
// /actuator/health shows: db, diskSpace, kafka, redis, etc.

// Custom health indicator
@Component("paymentGateway")
public class PaymentGatewayHealthIndicator extends AbstractHealthIndicator {

    private final PaymentGatewayClient client;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            boolean alive = client.ping();
            if (alive) {
                builder.up()
                       .withDetail("responseTimeMs", client.getLastResponseTime());
            } else {
                builder.down().withDetail("reason", "Ping returned false");
            }
        } catch (Exception e) {
            builder.down().withException(e);
        }
    }
}

// Response:
// GET /actuator/health
// {
//   "status": "UP",
//   "components": {
//     "db": {"status": "UP", "details": {"database": "PostgreSQL", "validationQuery": "isValid()"}},
//     "paymentGateway": {"status": "UP", "details": {"responseTimeMs": 45}},
//     "diskSpace": {"status": "UP", "details": {"total": 500000000, "free": 300000000}}
//   }
// }
```

### Kubernetes Liveness vs Readiness Probes

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true

# Kubernetes deployment spec
livenessProbe:
  httpGet:
    path: /actuator/health/liveness    # "am I alive?" — if DOWN: restart the pod
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness   # "am I ready to serve?" — if DOWN: remove from LB
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```

**Liveness vs Readiness**:
- **Liveness**: Is the JVM alive and not deadlocked? `DOWN` → Kubernetes restarts pod
- **Readiness**: Can this pod handle traffic? E.g., DB pool initialized, Kafka consumer connected? `DOWN` → Kubernetes stops routing to this pod (no restart)

---

## 9. Alerting — SLA with Percentile Histograms

```java
// Register Timer with histogram for SLA alerting
Timer slaTimer = Timer.builder("payment.processing.time")
    .publishPercentileHistogram()  // enables histogram_quantile() in Prometheus
    .sla(               // SLA boundary counters (how many requests met SLA?)
        Duration.ofMillis(100),
        Duration.ofMillis(500),
        Duration.ofSeconds(1)
    )
    .register(registry);

// Prometheus alerting rule (alerts.yml)
// groups:
//   - name: order-service
//     rules:
//       - alert: HighP99Latency
//         expr: histogram_quantile(0.99, rate(payment_processing_time_seconds_bucket[5m])) > 1.0
//         for: 2m
//         labels:
//           severity: critical
//         annotations:
//           summary: "p99 payment latency > 1s for 2 minutes"
```

---

## 10. Interview Q&A

**Q: What is the difference between a Counter, Gauge, and Timer in Micrometer?**  
A `Counter` is a monotonically increasing number — only goes up, never down. Use it to count events: orders created, errors thrown, cache misses. A `Gauge` represents a current value that can go up and down — it's a snapshot: queue depth, active connections, memory used. A `Timer` measures both the count and the duration of events — it produces {count, sum, max, percentiles} and is the right tool for latency measurement: request processing time, DB query time, external API call time.

**Q: How does distributed tracing work across service boundaries?**  
A `traceId` is generated at the entry point (e.g., API gateway or first service). Each service call creates a new `spanId` as a child of the current span. The trace context (traceId + spanId) is propagated in HTTP headers — either B3 format (`X-B3-TraceId`, `X-B3-SpanId`) or W3C TraceContext format (`traceparent`). Each service reports its spans to a tracing collector (Zipkin, Jaeger, Grafana Tempo). The collector assembles spans into a complete timeline by traceId, showing the full request flow across services and where time was spent.

**Q: What is the difference between liveness and readiness probes in Kubernetes?**  
A liveness probe answers "is this process alive?" — if it fails, Kubernetes restarts the pod. Use it to detect JVM deadlocks or catastrophic failures from which the app cannot recover. A readiness probe answers "can this pod serve traffic?" — if it fails, Kubernetes removes the pod from the load balancer but does NOT restart it. Use it to signal "not ready yet" during startup (waiting for DB pool to initialize, warming caches) or temporary unavailability. This is the critical distinction: a readiness failure means "take me out of rotation", a liveness failure means "I'm broken, restart me".
