# Performance Tuning

## 1. Measuring First

> "You can't improve what you don't measure."

### Key metrics

| Metric | Good baseline | Alarm threshold |
|---|---|---|
| P50 latency | < 50ms | — |
| P99 latency | < 200ms | > 1s is a problem |
| P999 latency | < 500ms | — |
| Error rate | < 0.1% | > 1% needs investigation |
| Throughput | Depends on SLA | — |
| GC pause time | < 50ms per pause | > 200ms |
| Heap after GC | Stable flat line | Monotonically rising = leak |
| Thread pool queue depth | ~0 at rest | Growing = overloaded |
| DB connection pool wait | < 5ms | > 50ms = pool exhausted |

### Profiling in production (safe tools)

```bash
# Async-profiler — CPU + allocation profiler, < 1% overhead
./profiler.sh -d 60 -f flamegraph.html <pid>

# JFR (Java Flight Recorder)
jcmd <pid> JFR.start name=perf duration=120s filename=/tmp/profile.jfr
# Open in JDK Mission Control

# Record allocation hotspots
jcmd <pid> JFR.start name=alloc settings=alloc duration=60s
```

---

## 2. JVM-Level Performance

### Reduce object allocation

```java
// BAD — allocates StringBuilders and intermediate Strings on every call
public String buildStatus(List<Order> orders) {
    String result = "";
    for (Order o : orders) {
        result += o.getId() + ":" + o.getStatus() + ","; // new String each iteration
    }
    return result;
}

// GOOD — single StringBuilder, pre-sized
public String buildStatus(List<Order> orders) {
    StringBuilder sb = new StringBuilder(orders.size() * 40);
    for (Order o : orders) {
        sb.append(o.getId()).append(':').append(o.getStatus()).append(',');
    }
    return sb.toString();
}
```

### Object pooling for expensive objects

```java
// Commons Pool2
GenericObjectPool<Connection> pool = new GenericObjectPool<>(new ConnectionFactory());
pool.setMaxTotal(20);
pool.setMaxWaitMillis(3000);

// Usage
Connection conn = pool.borrowObject();
try {
    // use connection
} finally {
    pool.returnObject(conn);
}
```

### Records and value types (Java 21+)

```java
// Records — compact, auto hashCode/equals/toString, immutable
record UserSummary(UUID id, String name, String email) {}

// Avoids mutable class + getters + hashCode/equals boilerplate
// JIT can often inline and stack-allocate these
```

---

## 3. Database Performance

### Connection pool tuning

Too few connections → requests queue. Too many → DB CPU context-switching overhead.

```yaml
spring.datasource.hikari.maximum-pool-size: 20
spring.datasource.hikari.connection-timeout: 3000  # fail fast at 3s
```

Monitor: `HikariCP` exposes `hikaricp.connections.pending` — if this grows, pool is undersized.

### Query batching

```java
// BAD — N separate INSERT statements
for (OrderItem item : items) {
    orderItemRepo.save(item); // N round trips
}

// GOOD — batch insert
orderItemRepo.saveAll(items); // Spring Data batches via Hibernate

# application.properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

### Read replicas

```java
// Route reads to replica, writes to primary
@Configuration
public class DataSourceConfig {
    @Bean @Primary
    public DataSource primaryDataSource() { /* primary */ }

    @Bean
    public DataSource replicaDataSource() { /* replica */ }

    @Bean
    public DataSource routingDataSource() {
        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ? "REPLICA" : "PRIMARY";
            }
        };
        routing.setTargetDataSources(Map.of("PRIMARY", primary, "REPLICA", replica));
        return routing;
    }
}
```

---

## 4. Caching Strategy

### Cache hierarchy

```
Application (in-process L1 cache — Caffeine)
  → Redis (distributed L2 cache)
    → Database
```

### Caffeine local cache

```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .recordStats()   // exposes cache hit rate to Micrometer
    );
    return manager;
}

@Cacheable(value = "products", key = "#productId")
public Product getProduct(UUID productId) {
    return productRepo.findById(productId).orElseThrow();
}

@CacheEvict(value = "products", key = "#product.id")
public Product updateProduct(Product product) {
    return productRepo.save(product);
}
```

### Cache stampede prevention

When cache expires, many threads simultaneously hit the DB:

```java
// Solution: early recomputation (probabilistic refresh before expiry)
// Caffeine handles this via refreshAfterWrite + async loader:
Caffeine.newBuilder()
    .maximumSize(10_000)
    .refreshAfterWrite(Duration.ofMinutes(4)) // refresh at 4min, serve stale during refresh
    .expireAfterWrite(Duration.ofMinutes(5))  // hard expire at 5min
    .buildAsync(key -> loadFromDatabase(key));
```

---

## 5. Thread Pool Tuning

```
IO-bound service formula:
  pool size = target_concurrency × (1 + wait_time / service_time)
  
Example: 100 QPS, each request waits 100ms on DB, spends 10ms CPU:
  pool size = 100 × (1 + 100/10) = 100 × 11 = 1100 threads (use virtual threads!)

CPU-bound:
  pool size ≈ CPU_cores + 1
```

### Virtual threads for IO-bound (Java 21)

```java
// Replace fixed thread pools for IO-bound with virtual thread executor
@Bean
public Executor asyncTaskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

Monitor: track virtual carrier thread `jdk.VirtualThreadPinned` JFR events to detect pinning.

---

## 6. Async & Non-blocking

```java
// Async controller — releases HTTP thread while waiting on IO
@GetMapping("/reports/{id}")
public CompletableFuture<ReportResponse> generateReport(@PathVariable UUID id) {
    return reportService.generateAsync(id)          // runs on ioPool
        .thenApplyAsync(ReportResponse::from, ioPool);
}

// Spring's @Async
@Service
public class NotificationService {
    @Async("notificationPool")
    public CompletableFuture<Void> sendEmail(EmailRequest req) {
        emailClient.send(req);
        return CompletableFuture.completedFuture(null);
    }
}
```

---

## 7. Profiling Workflow

```
1. Measure current P99 latency — establish baseline
2. Enable JFR / async-profiler for 2 minutes under load
3. Open flame graph — find the tallest/widest stack frames
4. Identify hotspot: DB query, serialization, GC, lock contention?
5. Make ONE change
6. Measure again — compare P99 before and after
7. Repeat
```

**Never optimize without measurement. Never make multiple changes at once — you won't know which helped.**

---

## 8. Interview Q&A

**Q: How do you diagnose high P99 latency but normal P50 latency?**  
P99 problems indicate tail latency issues — a small percentage of requests are very slow. Causes: GC pauses (check GC logs for stop-the-world > 200ms), lock contention (async-profiler shows `BLOCKED` threads waiting on locks), cold cache misses for edge-case data, DB connection pool exhaustion (requests queue briefly), JIT deoptimization of hot paths. Start by correlating P99 spikes with GC pause log timestamps and thread dump lock contention.

**Q: What is cache stampede and how do you prevent it?**  
When a cached item expires simultaneously, many threads make a cache miss and all independently hit the database — the "thundering herd." Prevention strategies: (1) `refreshAfterWrite` + async refresh — serve stale data while refreshing in background; (2) probabilistic early expiry — randomly start refreshing before actual expiry; (3) distributed lock — only one thread refreshes, others wait for updated value; (4) local + distributed cache hierarchy — stagger TTLs so local cache acts as buffer.

**Q: What is the difference between Throughput and Latency, and how do they relate?**  
Throughput is how many requests per second the system handles. Latency is how long a single request takes. They are inversely related under load: adding more concurrent requests increases throughput until saturation, then latency increases sharply (queuing theory — M/M/1 queue). At 80% utilization, average latency is 5× the service time. At 95%, it's 20×. This is why you add capacity before hitting saturation, not after.
