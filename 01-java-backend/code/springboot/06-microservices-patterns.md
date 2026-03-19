# Microservices Patterns

## 1. Service Communication Patterns

### Synchronous (REST / gRPC)

```
Client → Service A → Service B → Service C
```

- Simple, familiar.
- Temporal coupling: if B is down, A fails.
- Latency = sum of all hops.
- Use for: read queries that need immediate response.

### Asynchronous (Message-based)

```
Service A → [Kafka topic] ← Service B (consumer group)
```

- Temporal decoupling: A publishes and forgets.
- B processes at its own pace.
- Use for: write operations, notifications, analytics pipelines.

---

## 2. Resilience Patterns

### Retry with Exponential Backoff + Jitter

```java
// With Resilience4j
RetryConfig config = RetryConfig.custom()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(500))
    .retryExceptions(ConnectTimeoutException.class, IOException.class)
    .ignoreExceptions(ValidationException.class) // don't retry client errors
    .build();

Retry retry = Retry.of("paymentGateway", config);
Supplier<PaymentResult> decorated = Retry.decorateSupplier(retry, 
    () -> paymentGateway.charge(request));
```

**Jitter**: Add random ±30% noise to backoff to prevent thundering herd when many clients restart simultaneously.

```
Backoff with jitter:
Attempt 1: 500ms ± rand(0,150ms)
Attempt 2: 1000ms ± rand(0,300ms)
Attempt 3: 2000ms ± rand(0,600ms)
```

### Circuit Breaker

```
CLOSED → (failure threshold exceeded) → OPEN → (wait period) → HALF_OPEN
   ↑_________________________success from HALF_OPEN________________________|
```

```java
CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
    .slidingWindowType(COUNT_BASED)
    .slidingWindowSize(10)
    .failureRateThreshold(50)        // open at 50% failure rate
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .permittedNumberOfCallsInHalfOpenState(3)
    .build();

CircuitBreaker cb = CircuitBreaker.of("inventoryService", cbConfig);
```

- **CLOSED**: requests pass through, failures counted.
- **OPEN**: all requests fail-fast, no downstream calls.
- **HALF_OPEN**: limited probe requests to check if service recovered.

### Bulkhead

Isolate failures to a subset of resources:

```java
// Thread pool bulkhead — each service gets its own thread pool
BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
    .maxConcurrentCalls(25)
    .maxWaitDuration(Duration.ofMillis(100))
    .build();

// Semaphore bulkhead — limit concurrent calls
Bulkhead bulkhead = Bulkhead.of("inventoryService", bulkheadConfig);
Supplier<Inventory> decorated = Bulkhead.decorateSupplier(bulkhead, 
    () -> inventoryService.check(productId));
```

### Timeout

```java
TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
    .timeoutDuration(Duration.ofSeconds(2))
    .build();

TimeLimiter timeLimiter = TimeLimiter.of("externalPayment", tlConfig);
```

Always set timeouts — without them, a slow dependency causes thread pool exhaustion.

---

## 3. Transactional Outbox Pattern

Guarantees exactly-once event publishing with database atomicity:

```
╔══════════════════════════════════════════════════════════════╗
║  Service Transaction                                         ║
║   1. UPDATE orders SET status='CONFIRMED' WHERE id = ?       ║
║   2. INSERT INTO outbox (type, payload, published=false)      ║
╚══════════════════════════════════════════════════════════════╝
                      ↓  same DB transaction — atomic
╔══════════════════════════════════════════════════════════════╗
║  Outbox Relay (separate process)                             ║
║   SELECT * FROM outbox WHERE published = false               ║
║   → publish to Kafka                                         ║
║   → UPDATE outbox SET published = true                       ║
╚══════════════════════════════════════════════════════════════╝
```

**Why**: If you publish to Kafka and then update the DB in two separate operations, either can fail — dual write problem. The outbox makes them atomic.

---

## 4. Saga Pattern

Long-running distributed transactions without a 2PC lock:

### Choreography-based (events)

```
OrderService → OrderPlaced event
    → InventoryService: reserve items → InventoryReserved
    → PaymentService: charge card → PaymentCompleted
    → OrderService: confirm order

On failure:
PaymentService: charge fails → PaymentFailed event
    → InventoryService: compensate → release items
    → OrderService: cancel order
```

- Decentralized, no orchestrator.
- Hard to track global state.
- Good for: simple flows, event-driven architectures.

### Orchestration-based (state machine)

```
OrderSaga (orchestrator) calls:
  1. InventoryService.reserve()
  2. PaymentService.charge()
  3. ShippingService.schedule()

On any failure: orchestrator calls compensating transactions
```

- Easier to understand and debug.
- Single point of failure (orchestrator must be highly available).
- Good for: complex workflows, when you need a clear audit trail.

---

## 5. API Gateway Pattern

```
Client → API Gateway
             ├── authenticate JWT
             ├── rate limit by client ID
             ├── route to service
             ├── aggregate responses (BFF pattern)
             └── circuit break failing backends
```

Use cases: Spring Cloud Gateway, Kong, AWS API Gateway, Nginx+Lua.

```java
// Spring Cloud Gateway route
@Bean
RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("order-service", r -> r
            .path("/api/orders/**")
            .filters(f -> f
                .stripPrefix(1)
                .requestRateLimiter(c -> c
                    .setRateLimiter(redisRateLimiter)
                    .setKeyResolver(userKeyResolver)))
            .uri("lb://order-service"))
        .build();
}
```

---

## 6. Distributed Tracing

```java
// With Micrometer + Zipkin / Jaeger (Spring Boot 3)
// Auto-instrumented — just add dependency and config

// Manual span creation
@Autowired
Tracer tracer;

public PaymentResult processPayment(PaymentRequest req) {
    Span span = tracer.nextSpan().name("payment.process").start();
    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
        span.tag("payment.amount", req.amount().toString());
        span.tag("payment.currency", req.currency());
        return gateway.charge(req);
    } catch (Exception e) {
        span.error(e);
        throw e;
    } finally {
        span.end();
    }
}
```

Config:
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling in prod
spring:
  zipkin:
    base-url: http://zipkin:9411
```

---

## 7. Interview Q&A

**Q: How does the Outbox pattern solve the dual-write problem?**  
Without outbox, you need to atomically write to the database AND publish an event to Kafka. These are two separate systems — if the service crashes between them, you get inconsistency (DB updated but event not sent, or event sent but DB rollback). The Outbox pattern writes the event to a database table in the same local transaction as the business data. A separate relay process reads unpublished outbox records and publishes them to Kafka, then marks them published. Since reading from the DB and Kafka publish can be retried idempotently, the event is delivered at-least-once with no lost events.

**Q: What is the difference between a circuit breaker and a retry?**  
A retry handles transient failures — it assumes the operation will succeed on a subsequent attempt. A circuit breaker handles systemic failures — when a downstream service is overwhelmed or down, retrying every call makes things worse (retry storm). The circuit breaker trips open after a failure threshold is breached, causing all subsequent calls to fail immediately without hitting the downstream service, giving it time to recover. Typically you combine both: retries for transient errors, circuit breaker to prevent overloading.

**Q: Choreography vs Orchestration saga — when to use each?**  
Choreography is better for simple 2-3 step flows where each service can react to events independently. It's fully decentralized with no single orchestrator to fail. Orchestration is better for complex flows (5+ steps), where you need clear visibility, explicit compensation logic, and consistent state tracking in one place. Orchestration is harder to change (one file vs. distributed event contracts) but much easier to debug and monitor.
