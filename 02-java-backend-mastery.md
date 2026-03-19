# Java Backend Mastery

Use this document in two ways:
- for theory revision before interviews
- for implementation practice using `01-java-backend/code/concurrency/ConcurrencyAndJvmExamples.java` and the related topic folders under `01-java-backend/code/`

## 1. Core Java topics you must know deeply

### JVM internals
Master:
- heap, stack, metaspace, code cache
- class loading and classloader hierarchy
- JIT compilation and warm-up effects
- escape analysis basics
- G1 GC and ZGC high-level behavior
- stop-the-world pauses and latency impact

Be able to answer:
- Why does an app have high GC pause time?
- What causes memory leaks in Java services?
- How would you analyze OutOfMemoryError in production?

Interview theory you must be able to say clearly:
- Most Java objects are allocated on the heap, while method frames and local references live on the stack.
- GC tuning is workload-dependent. Low-latency services care about pause times and tail latency more than pure throughput.
- A memory leak in Java usually means objects remain strongly reachable longer than intended, not that memory is manually forgotten.

Example talking point:
- If P99 latency rises with GC pauses, I would first inspect heap usage trend, allocation rate, old generation pressure, and whether object churn from serialization or caching is too high.

### Linux and networking depth
Know:
- process vs thread and file descriptor basics
- page cache, RSS, and why memory pressure affects latency
- TCP handshake, TLS handshake, and keep-alive reuse
- timeout budgets, retries, connection pooling, and load balancer behavior

Be able to discuss:
- why too many threads can hurt latency even before CPU is saturated
- how file descriptor exhaustion breaks seemingly unrelated parts of a service
- why retries without deadline budgeting can amplify an outage

### Memory management
Know:
- object allocation lifecycle
- strong, soft, weak references
- memory leaks through static fields, thread locals, caches, listeners
- heap dump analysis basics

### Multithreading and concurrency
Master:
- threads vs processes
- thread lifecycle
- synchronized, intrinsic locks, ReentrantLock
- volatile and visibility guarantees
- atomic classes and CAS basics
- happens-before relationships
- ExecutorService and thread pool sizing
- CompletableFuture composition
- producer-consumer pattern
- deadlock, starvation, livelock
- concurrent collections

Be able to discuss:
- when to use lock-free vs lock-based approaches
- how to tune thread pools for IO-bound vs CPU-bound work
- how to avoid contention in high-throughput services

Interview theory you must know:
- `volatile` gives visibility guarantees but not compound-operation atomicity.
- `synchronized` gives mutual exclusion plus happens-before semantics on lock release/acquire.
- `ConcurrentHashMap` improves throughput by reducing coarse-grained contention, but it does not make multi-step business logic automatically thread-safe.
- Bounded concurrency is often safer than unlimited async fan-out because downstream capacity is usually the real bottleneck.

Code pattern:

```java
ExecutorService ioExecutor = Executors.newFixedThreadPool(16);
Semaphore bulkhead = new Semaphore(8);

CompletableFuture<OrderSummary> future = CompletableFuture.supplyAsync(() -> {
	try {
		if (!bulkhead.tryAcquire(500, TimeUnit.MILLISECONDS)) {
			return OrderSummary.partial();
		}
		return downstreamClient.fetchOrders();
	} finally {
		bulkhead.release();
	}
}, ioExecutor);
```

Why interviewers like this example:
- it shows async composition
- it shows bulkhead isolation
- it shows that concurrency control is a reliability concern, not only a performance concern

### Collections internals
Know deeply:
- HashMap internals, collisions, resizing, treeification
- ConcurrentHashMap basics
- ArrayList vs LinkedList trade-offs
- HashSet, TreeSet, PriorityQueue use cases

### Performance and diagnostics
Know:
- JFR, VisualVM, async-profiler basics
- CPU profiling vs memory profiling
- thread dumps and lock contention analysis
- connection pool exhaustion diagnosis

Backend performance checklist:
- Check if latency is CPU-bound, IO-bound, lock-bound, or GC-bound before changing code.
- If DB calls dominate, optimize query shape and indexes before adding thread count.
- If connection pools are exhausted, increasing threads usually makes the incident worse.

## 2. Spring Boot mastery

### Core areas
You should know:
- bean lifecycle and dependency injection
- autoconfiguration concepts
- Spring MVC request flow
- configuration management
- validation and exception handling
- profiles and environment separation

Theory that matters in interviews:
- Dependency injection improves testability and separation of concerns, but over-abstraction can make services harder to trace.
- Spring Boot autoconfiguration is useful only if you understand what beans, filters, and interceptors are actually created.
- Validation, exception handling, and observability are part of API design, not optional polish.

### Data access
Master:
- JPA/Hibernate fundamentals
- lazy vs eager loading
- N+1 query problem
- transaction boundaries and propagation
- optimistic vs pessimistic locking
- JDBC vs JPA trade-offs

Rule of thumb:
- Use JPA where domain modeling and productivity help.
- Use JDBC or custom queries where query shape and performance must be tightly controlled.
- For interview answers, show that you know where abstraction leaks.

### Security
Know:
- Spring Security filter chain basics
- JWT authentication
- OAuth2/OIDC concepts
- role-based authorization
- CSRF basics, CORS, secure headers
- secret management best practices

Security depth expected:
- Authentication answers who the caller is.
- Authorization answers what the caller may do.
- JWT reduces server-side session dependency but requires careful expiry, rotation, and claim validation.
- Internal services should still validate trust boundaries; being inside a VPC is not enough.

### Testing
Be strong in:
- unit tests with mocks
- integration tests
- repository tests
- controller tests
- Testcontainers for DB/Kafka integration
- contract testing basics

### Delivery and release safety
Know:
- CI pipelines, artifact reproducibility, and branch protections
- blue-green vs canary releases
- feature flags and rollback strategy
- backward-compatible schema migrations and expand-contract deploys

## 3. Microservices: what you should know deeply

### Service design
Master:
- service boundary identification
- database-per-service pattern
- sync vs async communication
- API gateway role
- backward-compatible API evolution

### Reliability patterns
You must know:
- retries with exponential backoff and jitter
- timeout configuration
- circuit breaker
- bulkhead isolation
- fallback strategies
- idempotent APIs and consumers
- outbox pattern
- saga pattern at high level

Theory that gets noticed:
- Retries without idempotency create duplicate writes.
- Timeouts must be shorter than the caller's deadline budget.
- Circuit breakers protect the whole system from a bad dependency; they do not fix the dependency itself.
- The outbox pattern is used because database commit and message publish are not atomically consistent in normal service code.

Example idempotency flow:

```java
public OrderResult createOrder(String idempotencyKey, CreateOrderRequest request) {
	return processedRequests.computeIfAbsent(idempotencyKey, key -> {
		String orderId = UUID.randomUUID().toString();
		outboxRepository.save(new OutboxEvent(orderId, "OrderCreated"));
		return new OrderResult(orderId, "CREATED");
	});
}
```

### Observability
Know:
- structured logging
- correlation IDs
- distributed tracing basics
- RED metrics: rate, errors, duration
- saturation metrics and alerting

### Deployment and operations
Know:
- health checks and readiness/liveness
- blue-green vs canary deployments
- rollback strategy
- autoscaling basics
- config and secret separation

## 4. REST API design

### What good API design means
Master:
- resource naming
- HTTP method semantics
- idempotency
- pagination design
- filtering and sorting
- versioning strategy
- status code discipline
- standard error response structure

### Interview-level examples
Be able to design:
- user profile service API
- order creation API with idempotency key
- notification preference APIs
- search API with pagination and filters

Reference response shape:

```json
{
	"data": {
		"orderId": "ord_123",
		"status": "CREATED"
	},
	"metadata": {
		"requestId": "req_456"
	},
	"errors": []
}
```

Good API design signals:
- idempotency for create/payment-like APIs
- consistent error shape
- pagination that scales beyond offset-only queries
- explicit validation failures instead of generic 500s

## 5. Performance and scalability

Know how to reason about:
- DB indexing and query plans
- connection pool sizing
- cache-aside pattern
- request batching
- async/offline processing
- minimizing serialization overhead
- P95/P99 latency vs average latency

What to say in interviews:
- Average latency hides user pain; P95 and P99 show tail behavior.
- Cache only after identifying stable read patterns and correctness requirements.
- Batch or async work when the user does not need immediate completion.

## 6. Real-world backend challenges to prepare stories for

Prepare strong examples from your work or personal projects for:
- high latency due to DB bottleneck
- message duplication in async system
- cache inconsistency issue
- production incident caused by downstream dependency
- scaling API under traffic spike
- rollout issue after deployment
- schema migration with no downtime

For each story, capture:
- problem
- scale
- diagnosis
- solution
- trade-offs
- result in metrics

Strong story template:
1. The service handled X RPS / Y events per day.
2. We saw a concrete symptom such as P99 degradation or duplicate processing.
3. I isolated the issue using metrics, logs, traces, or profiling.
4. I fixed the root cause and added guardrails.
5. I measured the business or reliability impact.
