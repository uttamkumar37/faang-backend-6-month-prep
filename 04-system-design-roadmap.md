# System Design Roadmap

Use this document together with:
- `03-system-design/code/05-ratelimiter/RateLimiterPatterns.java`
- `03-system-design/code/06-idempotency/IdempotencyAndOutboxPatterns.java`
- `03-system-design/CAPACITY-CHEATSHEET.md`
- `03-system-design/INTERVIEW-GUIDE.md`

## 1. Learning path

### Phase 1: design foundations (basic)
Learn:
- requirement clarification: functional vs non-functional
- capacity estimation: requests per second, storage, bandwidth
- core components: load balancer, cache, database, message queue, object storage, CDN
- read-heavy vs write-heavy workload patterns
- client-server model, DNS, HTTP basics
- API design: REST, gRPC, WebSocket use cases

### Phase 2: data and distributed systems (intermediate)
Learn:
- SQL vs NoSQL selection criteria
- indexing strategies: B-tree, LSM-tree, covering index
- sharding and partitioning strategies
- replication: leader-follower, multi-leader, leaderless
- consistency models: strong, eventual, read-your-writes, monotonic reads
- transactions: ACID, 2PC, distributed sagas
- messaging semantics: at-most-once, at-least-once, exactly-once
- CAP theorem in practice
- distributed coordination: leader election, quorum
- MVCC, replica lag, and isolation anomalies in practical systems
- clock skew, fencing tokens, and split-brain prevention

### Phase 3: production depth (advanced)
Learn:
- observability: structured logging, metrics, traces
- security: authentication, authorization, encryption at rest and in transit
- multi-region architecture and active-active designs
- disaster recovery: RPO, RTO, failover automation
- rollout and migrations: blue-green, canary, feature flags
- cost optimization: right-sizing, tiered storage, caching ROI
- large-scale incident handling
- container platforms: Docker image hygiene, Kubernetes probes, autoscaling
- managed service trade-offs vs self-hosted infrastructure

## 2. Capacity estimation fundamentals

This is the first deep skill you must build. Estimation is not math theater — it shapes your design choices.

### Numbers every engineer must memorize

| Metric | Value |
|---|---|
| L1 cache access | ~1 ns |
| L2 cache access | ~10 ns |
| RAM access | ~100 ns |
| SSD random read | ~100 µs |
| HDD seek | ~10 ms |
| Network within datacenter | ~0.5 ms |
| Cross-region network | ~50–150 ms |
| 1 Gbps network throughput | ~125 MB/s |
| Single DB row read (indexed) | ~1 ms |
| Average URL fetch (HTTP) | ~200 ms |

### Powers of 2 approximations
- 1 KB = 10^3 bytes
- 1 MB = 10^6 bytes
- 1 GB = 10^9 bytes
- 1 TB = 10^12 bytes

### Traffic estimation template

Given: 1 million DAU, 10 requests per user per day.
Total requests per day = 10^7.
Requests per second = 10^7 / 86400 ≈ 116 RPS average.
Peak is usually 3-5x average. Peak ≈ 350-600 RPS.

Storage estimation template:
- 100 million users × 1 KB profile data = 100 GB
- 10 million messages/day × 1 KB each = 10 GB/day = 3.65 TB/year

Bandwidth estimation:
- 1 million images/day × 200 KB average = 200 GB/day = ~2.3 MB/s outbound

Interview habit:
- Round aggressively. 86400 seconds becomes "approximately 100K".
- State your assumptions before calculating.
- Ask if the scale is millions, hundreds of millions, or billions — the design changes significantly.

## 3. Core building blocks

Every system design answer uses a combination of these components. Know each one deeply.

### Load balancer
Purpose: distribute incoming traffic across multiple backend instances.

Types:
- Layer 4 (transport): routes based on IP and TCP port, fast, no content inspection.
- Layer 7 (application): routes based on URL, headers, cookies. Supports A/B routing, sticky sessions, SSL termination.

Algorithms:
- Round robin: equal distribution, simple.
- Least connections: routes to least-loaded instance. Better for variable-latency requests.
- IP hash: same client always hits the same server. Useful for local cache affinity.
- Weighted round robin: accounts for instance heterogeneity.

Theory:
- Layer 4 LBs are faster but cannot route based on content.
- Layer 7 LBs enable smart routing but add latency and complexity.
- In most FAANG-scale problems, the answer assumes Layer 7 LB (like AWS ALB or Nginx).

Health checks:
- LBs poll backend health endpoints and remove unhealthy instances automatically.
- Always design services with `/health` and `/ready` endpoints.

### CDN (Content Delivery Network)
Purpose: serve static and cacheable content from edge nodes close to users.

What to cache at CDN:
- static files: JS, CSS, images
- API responses that are publicly cacheable (product catalog, public feeds)
- NOT: user-specific or transactional content

Push vs pull CDN:
- Pull CDN: fetches from origin on first miss, caches automatically. Simpler to operate.
- Push CDN: you explicitly upload content. Used for large files like videos.

Cache-Control headers:
- `Cache-Control: max-age=86400` tells CDN to cache for 24 hours.
- `Cache-Control: no-cache` means always validate with origin.
- `Surrogate-Key` headers allow bulk invalidation by content group.

Theory:
- CDN reduces origin load and drops latency for geographically distributed users.
- Cache invalidation at CDN level is coarse-grained; design with immutable URLs (content-hash filenames) when possible.

### API Gateway
Purpose: single entry point for clients; handles cross-cutting concerns.

Responsibilities:
- request routing to backend services
- SSL termination
- authentication and token validation
- rate limiting per client
- request/response transformation
- API versioning
- circuit breaking and timeout enforcement

Theory:
- API gateway prevents each individual service from reimplementing auth, rate limiting, and logging.
- It is a scalability and security boundary, not just a proxy.
- In interviews, mention that the gateway should not contain business logic.

### Reverse proxy vs forward proxy
- Forward proxy: sits in front of clients, hides client identity from server.
- Reverse proxy: sits in front of servers, hides server identity from clients (Nginx, HAProxy).

### DNS
- DNS translates domain names to IP addresses.
- TTL controls how long DNS entries are cached.
- Low TTL enables faster failover (useful for DR) but increases DNS query load.
- Weighted DNS routing is used for geographic distribution and canary deploys.

### Object storage
- Use for large blobs: images, videos, backups, logs, ML artifacts.
- Horizontally scalable, durable by design (AWS S3, GCS, Azure Blob).
- Not a database: no query capability, no transactions.
- Generate pre-signed URLs to allow temporary client-direct access without routing through your app server.

Theory:
- Object storage is cheaper per GB than block or database storage by orders of magnitude.
- Always store files in object storage and metadata (names, sizes, references) in a database.

## 4. Step-by-step interview answer framework

Use this order in every interview:

### Step 1: clarify requirements (3-5 minutes)
Ask:
- What are the functional requirements? What does the system do?
- What are the non-functional requirements? Latency, availability, consistency, durability?
- What is the scale? DAU, requests per second, data volume?
- Are there constraints? Budget, existing infrastructure, team size?
- What does success look like? Specific SLA targets?

### Step 2: capacity estimation (3-5 minutes)
State aloud:
- Reads per second, writes per second.
- Data size per entity and total storage over 5 years.
- Bandwidth requirements.
- Peak vs average traffic ratio.

### Step 3: define APIs and data model
Define:
- Core API endpoints with request and response shapes.
- Primary entities and their relationships.
- Identify read-heavy vs write-heavy access patterns.

### Step 4: high-level architecture
Draw and describe:
- Clients → CDN or API Gateway → Application servers → Databases, Caches, Queues.
- Label each component with its responsibility.
- Do not draw microservices until you justify the need.

### Step 5: database and storage deep dive
Decide:
- SQL or NoSQL and why.
- Partitioning strategy and partition key choice.
- Indexing and query patterns.
- Replication and read replicas.
- Consistency requirements per entity type.

### Step 6: cache strategy
Decide:
- What to cache and at what layer.
- Cache invalidation strategy.
- TTL tuning.
- Stampede and cold-start handling.

### Step 7: async processing and messaging
Decide:
- What can be done asynchronously.
- Queue vs event stream.
- Consumer group design.
- DLQ and retry policy.

### Step 8: scaling, reliability, and failure handling
Discuss:
- How each component scales horizontally.
- What happens when each component fails.
- How you handle partial failures in distributed flows.
- Idempotency design.
- Rate limiting and quota enforcement.

### Step 9: observability and security
Cover:
- Structured logging with correlation IDs.
- Key metrics: error rate, latency P95/P99, queue lag, cache hit ratio.
- Distributed tracing.
- AuthN and AuthZ model.
- Data encryption and secrets management.

### Step 10: trade-offs and improvements
Close with:
- What you sacrificed for simplicity.
- What you would improve with more time or scale.
- Migration and rollout strategy.

High-value interview habit:
- Before going deep, say what you are optimizing for: latency, availability, correctness, cost, or operator simplicity.
- Explicitly state when you are making a trade-off rather than letting it be implicit.

## 5. Database design deep dive

### SQL vs NoSQL selection

Use SQL (PostgreSQL, MySQL) when:
- you need ACID transactions across multiple rows or tables
- your schema is well-defined and structured
- you need complex queries, joins, and aggregations
- data volume is < 1-5 TB and writes are < 10K TPS

Use NoSQL when:
- you need horizontal write scalability beyond what a single SQL master supports
- your access pattern is always single-key or range lookup on known partition key
- you need flexible schema for rapidly evolving data
- you are building a time-series, document, graph, or wide-column workload

NoSQL types:
- Key-value: Redis, DynamoDB. Best for session store, cache, simple lookup.
- Document: MongoDB, Firestore. Best for flexible hierarchical data, product catalogs.
- Wide-column: Cassandra, HBase. Best for time-series, high-write IoT, activity feeds.
- Graph: Neo4j. Best for relationship traversal (social graphs, recommendation).

Theory:
- Most FAANG problems have a strong SQL core with a NoSQL layer for high-scale read or write.
- NoSQL does not mean schema-less at the application layer — you still need a consistent data contract.

### Indexing strategies

B-tree index:
- The default index type in PostgreSQL and MySQL.
- Good for equality, range queries, and sort operations.
- Writes are slightly slower because tree is updated on every insert.

LSM-tree (Log-Structured Merge-tree):
- Used by Cassandra, RocksDB, HBase.
- Writes are fast (appended to in-memory structure, flushed to disk).
- Reads are slower because multiple levels must be checked. Bloom filters help.
- Good for write-heavy workloads.

Covering index:
- An index that includes all columns needed to answer a query without touching the row data.
- Eliminates a second lookup to the heap (index-only scan).
- Use when a query pattern is very hot and latency is critical.

Theory:
- Every index speeds up reads and slows down writes.
- Indexing strategy is an access-pattern decision, not a schema decision.
- Explain which queries are hot before proposing indexes.

### Sharding and partitioning

Horizontal partitioning (sharding):
- Splits rows across multiple database nodes by a partition key.
- Each shard handles a subset of the data and a subset of the traffic.

Partition key choices:
- User ID: good for user-centric workloads, but large users may cause hot shards.
- Hash of primary key: uniform distribution, but range queries span all shards.
- Tenant ID: good for multi-tenant SaaS, but imbalanced tenants create hot shards.
- Time-based: good for time-series, but writes always hit the latest shard (hot write partition).

Hot partition mitigation:
- Add random suffix to partition key to spread writes across multiple shards.
- Use virtual nodes or consistent hashing to allow flexible redistribution.

Resharding cost:
- Resharding requires moving data while serving live traffic.
- Design resharding windows and migration steps from the start.

Theory:
- Partition key is the most important schema decision for NoSQL systems.
- A poor partition key creates a single-server bottleneck that defeats the purpose of distributed storage.

### Replication

Leader-follower (primary-replica):
- All writes go to the leader. Replicas serve reads.
- Replica lag means reads from replicas may be stale.
- If leader fails, a replica must be promoted. Risk of data loss if unreplicated writes exist.

Multi-leader:
- Multiple leaders accept writes. Conflicts must be detected and resolved.
- Used for multi-region active-active setups or offline-first products.
- Conflict resolution is application-specific; last-write-wins is simple but lossy.

Leaderless (quorum-based):
- Any node can accept writes and reads.
- Quorum read/write: W + R > N ensures overlap and prevents stale reads.
- Used by DynamoDB, Cassandra.

Replication lag implications:
- Read-after-write consistency requires reading from the leader or waiting for replica sync.
- Session tokens can pin a user to the same replica for consistency within a session.

### ACID and distributed transactions

ACID:
- Atomicity: all operations in a transaction succeed or all fail.
- Consistency: the database transitions from one valid state to another.
- Isolation: concurrent transactions do not see each other's intermediate state.
- Durability: committed data survives crashes.

Isolation levels (from weakest to strongest):
- Read uncommitted: sees dirty reads.
- Read committed: only sees committed data. Default in PostgreSQL.
- Repeatable read: same row returns same value within transaction.
- Serializable: full isolation, highest correctness, highest cost.

Two-phase commit (2PC):
- Distributed transaction protocol: prepare phase, then commit phase.
- A coordinator asks all participants to prepare, then instructs commit or rollback.
- Problem: if coordinator crashes after prepare but before commit, participants block.
- Not recommended for high-throughput systems due to its blocking nature.

Saga pattern:
- Alternative to 2PC for long-running distributed transactions.
- Each local transaction publishes an event or calls the next step.
- On failure, compensating transactions undo previous successful steps.
- Choreography saga: services react to events independently.
- Orchestration saga: a central orchestrator directs each step.

Theory:
- In interviews, 2PC is a valid answer for a single DB cluster but not for cross-service transactions.
- Sagas are the production-grade answer when writes span services. Accept eventual consistency and design compensating operations upfront.

## 6. Caching deep dive

### Cache hierarchy
- Client-side cache: browser cache, mobile app cache. Reduces network calls entirely.
- CDN cache: serves static or cacheable content from edge. Best for public, high-read data.
- Application-level cache: in-process cache (Guava, Caffeine) for hot objects. Zero network hop.
- Distributed cache: Redis, Memcached. Shared across app service instances.
- Database cache: query result cache inside the DB engine. Usually off by default.

### Cache patterns

Cache-aside (lazy loading):
- Application reads from cache first. On miss, reads from DB and writes to cache.
- Application controls what is cached.
- Cache is populated lazily, so cold start and stampede are risks.

```
Cache hit: read cache → return result
Cache miss: read DB → write to cache → return result
```

Write-through:
- Every write goes to both cache and DB synchronously.
- Cache is always warm. No stale data risk.
- Writes are slower due to dual-write overhead.
- Problem: caches pre-populated data that may never be read.

Write-back (write-behind):
- Write to cache only. Async persistence to DB later.
- Highest write throughput, lowest write latency.
- Risk of data loss if cache crashes before DB sync.
- Used where durability can briefly lag (analytics counters, activity metrics).

Read-through:
- Cache sits in front of DB. Cache fetches from DB automatically on miss.
- Application always talks to the cache, not the DB directly.
- Simplifies application code but reduces fine-grained control.

### Cache invalidation strategies

TTL-based expiry:
- Cache entries expire automatically after a defined time.
- Simplest strategy. No explicit invalidation needed.
- Problem: stale data for the duration of TTL.

Event-driven invalidation:
- Update or delete cache entry when the source data changes.
- Low stale window but requires tight coupling between write path and cache.
- Problem: race conditions between invalidation and re-population.

Versioned cache keys:
- Append a version or timestamp to cache keys.
- Old versions naturally become unreachable.
- Requires storing version metadata somewhere.

Stale-while-revalidate:
- Serve stale content immediately, refresh in background.
- Good for non-critical data where slightly stale is acceptable.

### Cache stampede (thundering herd) protection

Problem: when a hot cache key expires, many requests miss simultaneously and hammer the DB.

Solutions:
- Jitter: add random offset to TTLs so keys expire at different times.
- Mutex/lock: first request takes a lock, populates the cache. Others wait.
- Early recompute: proactively refresh cache before TTL expiry.
- Request coalescing: collapse in-flight duplicate requests into one upstream call.

### Redis deep dive
Know for interviews:
- Redis is single-threaded for commands, which simplifies reasoning about atomicity.
- Redis data structures: String, Hash, List, Set, Sorted Set, Bitmap, HyperLogLog, Streams.
- Sorted Set is used for leaderboards and rate limiter sliding window.
- Redis List is used for producer-consumer queues.
- Redis pub/sub is broadcast-only, not durable.
- Redis Streams provide durable, consumer-group-aware message delivery.
- Redis Cluster shards data across nodes using hash slots (16384 slots).
- Replication in Redis is async by default; `WAIT` command can enforce sync.

```java
// Token bucket rate limiter with Redis
String script = """
    local key = KEYS[1]
    local capacity = tonumber(ARGV[1])
    local refillRate = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])
    local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
    local tokens = tonumber(data[1]) or capacity
    local lastRefill = tonumber(data[2]) or now
    local elapsed = now - lastRefill
    tokens = math.min(capacity, tokens + elapsed * refillRate)
    if tokens < 1 then
        return 0
    end
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
    redis.call('EXPIRE', key, 3600)
    return 1
    """;
```

Theory:
- Redis scripting (Lua) executes atomically, enabling compound read-modify-write without race conditions.

## 7. Messaging and event streaming deep dive

### Queue vs log-based stream

Queue (SQS, RabbitMQ):
- Message is consumed and deleted.
- Each message is delivered to one consumer only (unless fan-out topic is used).
- Good for task distribution: email sending, job processing, background work.
- Delivery is at-least-once by default.
- Visibility timeout prevents concurrent processing of the same message.

Log-based stream (Kafka, Kinesis):
- Messages are retained in a log for a configurable period.
- Multiple consumer groups can independently replay from any offset.
- Good for event sourcing, audit trails, reprocessing, CDC pipelines.
- Order is guaranteed within a partition.

Theory:
- Use queues when you want work to be done once and discarded.
- Use event streams when you want multiple independent consumers or the ability to replay.

### Kafka deep dive

Concepts you must know:
- Topic: a named, ordered log of records.
- Partition: a topic is split into partitions for parallelism.
- Offset: position of a message within a partition.
- Consumer group: a group of consumers that together consume all partitions of a topic. Each partition is assigned to exactly one consumer in the group.
- Leader and replicas: each partition has one leader and N-1 replicas. Reads and writes go to the leader.
- ISR (In-Sync Replicas): replicas that are caught up with the leader. `acks=all` waits for all ISRs to acknowledge.

Delivery guarantees:
- `acks=0`: fire and forget. Possible message loss.
- `acks=1`: leader acknowledges. Possible loss if leader fails before replication.
- `acks=all`: all ISRs acknowledge. Strongest durability guarantee.

Consumer offset management:
- Committing offsets after processing ensures at-least-once delivery.
- Committing before processing can result in message loss if processing fails (at-most-once).
- Idempotent consumers are the practical solution for exactly-once semantics.

Kafka for ordering:
- Within a partition, order is guaranteed.
- Across partitions, there is no global order.
- Route events for the same entity (same user, same order) to the same partition using entity ID as the partition key.

### Messaging patterns

Fan-out:
- One message published to a topic, multiple consumer groups each receive it.
- Example: order created event consumed by fulfillment, billing, and notification services.

Dead-letter queue (DLQ):
- Messages that fail processing after N retries are moved to a DLQ.
- Allows inspection and replay without blocking the main queue.
- Always design a DLQ for production messaging.

Outbox pattern:
- Write event to an outbox table in the same DB transaction as the business write.
- A relay process reads the outbox and publishes to Kafka or SQS.
- Ensures message is never lost even if the broker is temporarily unavailable.
- Exactly-once semantics at the write boundary.

```java
@Transactional
public OrderResult createOrder(String idempotencyKey, CreateOrderRequest req) {
    Order order = new Order(req);
    orderRepository.save(order);
    outboxRepository.save(new OutboxEvent(order.getId(), "OrderCreated", toJson(order)));
    return new OrderResult(order.getId(), "CREATED");
}
```

Saga choreography:
- Each service publishes an event on success and listens for events to continue the workflow.
- No central coordinator.
- Fault-tolerant by default: each service retries independently.
- Hard to reason about the overall flow; debugging requires tracing event chains.

Saga orchestration:
- A central orchestrator calls each service and drives the workflow.
- Easier to observe and debug.
- Orchestrator becomes a single point of failure unless made durable (DB-backed state machine).

## 8. Distributed systems fundamentals

### CAP theorem

Formal statement:
- In the presence of a network partition, a distributed system must choose between consistency and availability.
- You cannot have all three simultaneously.

In practice:
- Partition tolerance is not optional in networked systems; it must be assumed.
- The real choice is: when a partition occurs, do you return stale data (AP) or return an error (CP)?

Examples:
- CP systems: ZooKeeper, etcd, Spanner. Prioritize consistency, may refuse requests during partition.
- AP systems: Cassandra, DynamoDB (default). Prioritize availability, may return stale data.

Interview framing:
- Do not recite CAP academically.
- Explain the actual product effect: profile updates can tolerate eventual consistency, but payment confirmation or inventory deduction cannot be ambiguous.
- Identify per-entity consistency requirements: not all data must be consistent to the same degree.

### PACELC

Extends CAP to include normal-operation trade-offs:
- When Partition: choose Availability vs Consistency.
- Else (no partition): choose Latency vs Consistency.
- PACELC captures the reality that reducing consistency improves latency even without partitions.

### Quorum reads and writes

For N replicas, W writes, R reads:
- W + R > N ensures at least one overlapping node between read and write set (strong consistency).
- Setting W = N/2 + 1, R = N/2 + 1 is the standard majority quorum.
- Cassandra allows tuning W and R per query: QUORUM, ONE, ALL.

### Consistency models

Strong consistency:
- After a write completes, all subsequent reads see the new value.
- Requires coordinator overhead. Slower.
- Use for: financial balances, inventory deductions, leader election.

Eventual consistency:
- Reads will eventually reflect all writes, but may be stale immediately after a write.
- Higher availability and lower latency.
- Use for: user profiles, social counts, product views, preference settings.

Read-your-writes:
- A user always sees their own writes, even if others might see stale data.
- Implemented by routing a user's reads to the same replica they wrote to, or using sticky sessions.

Monotonic reads:
- Once a user sees a value, subsequent reads never see an older value.
- Prevents confusing reversals like "my post appeared, then disappeared, then appeared again."

Causal consistency:
- Writes that are causally related are seen in order by all nodes.
- "If you saw A, you should see B after A" if A causally precedes B.

### Leader election

Why needed:
- Distributed systems need coordination for tasks like: who writes to the primary DB, who runs a scheduled job, who holds a distributed lock.

How it works:
- Consensus algorithms (Raft, Paxos) elect a leader through a voting process.
- Each candidate requests votes. A candidate that receives a majority of votes becomes leader.
- ZooKeeper and etcd implement distributed leadership correctly using these algorithms.

Practical use:
- Never implement your own leader election; use ZooKeeper, etcd, or cloud-native equivalents.
- Discuss leader election in the context of who owns a task, not just as theory.

### Consistent hashing

Problem: when a distributed cache or DB cluster scales up or down, naive modulo hashing reassigns most keys.

Consistent hashing solution:
- Map both nodes and keys to positions on a virtual ring.
- Each key is assigned to the nearest node clockwise on the ring.
- When a node is added or removed, only the keys between the new and previous node are remapped.
- Virtual nodes: each physical node gets multiple positions on the ring to improve balance.

Use cases:
- Distributed cache routing (Memcached clustering).
- Sharding router in distributed key-value stores.

## 9. Reliability and resilience patterns

### Retry with exponential backoff and jitter

```java
public <T> T withRetry(Supplier<T> operation, int maxAttempts) {
    int attempt = 0;
    while (attempt < maxAttempts) {
        try {
            return operation.get();
        } catch (TransientException e) {
            attempt++;
            if (attempt == maxAttempts) throw e;
            long delay = (long) (Math.pow(2, attempt) * 100 + Math.random() * 100);
            Thread.sleep(delay);
        }
    }
    throw new RuntimeException("Unreachable");
}
```

Theory:
- Exponential backoff increases wait time between retries to reduce thundering-herd on recovering dependencies.
- Jitter adds randomness to prevent all retrying clients from hitting the server simultaneously.
- Always set a maximum retry count and a maximum total deadline.

### Timeout design

Types:
- Connection timeout: time to establish a connection.
- Request timeout: time to complete the full request including data transfer.
- Read timeout: time to receive data after connection is established.

Theory:
- Timeouts must be shorter than the caller's deadline budget.
- If service A depends on service B and A has a 5-second SLA, B's timeout must be < 5 seconds minus A's own processing overhead.
- Missing timeouts cause cascading failures: slow dependency blocks all threads.

### Circuit breaker

States:
- Closed: requests flow normally. Failure count is tracked.
- Open: requests are short-circuited immediately, no actual calls made. Failure rate exceeded threshold.
- Half-open: a probe request is allowed. If it succeeds, circuit closes. If it fails, circuit opens again.

```java
// Conceptual usage with Resilience4j
CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("paymentService");

Supplier<PaymentResult> decoratedCall = CircuitBreaker
    .decorateSupplier(circuitBreaker, () -> paymentClient.charge(request));

Try<PaymentResult> result = Try.ofSupplier(decoratedCall)
    .recover(CallNotPermittedException.class, ex -> PaymentResult.fallback());
```

Theory:
- Circuit breakers prevent cascading failures by stopping calls to a known-bad dependency.
- They do not fix the broken dependency; they protect the calling system while the dependency recovers.
- Combine circuit breakers with fallbacks: return cached data, degrade gracefully, or queue for retry.

### Bulkhead isolation

Problem: one slow dependency exhausts all threads and blocks unrelated features.

Solution: dedicate separate thread pools per downstream dependency.

```java
ExecutorService paymentExecutor = Executors.newFixedThreadPool(10);
ExecutorService inventoryExecutor = Executors.newFixedThreadPool(10);

// Payment threads cannot exhaust inventory threads and vice versa
CompletableFuture<PaymentResult> paymentFuture =
    CompletableFuture.supplyAsync(() -> paymentClient.charge(request), paymentExecutor);

CompletableFuture<InventoryResult> inventoryFuture =
    CompletableFuture.supplyAsync(() -> inventoryClient.reserve(itemId), inventoryExecutor);
```

### Idempotency

An operation is idempotent if applying it multiple times has the same effect as applying it once.

Idempotency key pattern:
- Client generates a unique key (UUID) per logical operation.
- Server checks if key was processed before.
- If yes, return cached result. If no, process and store result keyed by idempotency key.

```java
@Transactional
public OrderResult createOrder(String idempotencyKey, CreateOrderRequest request) {
    return processedOrders.computeIfAbsent(idempotencyKey, key -> {
        Order order = orderService.create(request);
        return new OrderResult(order.getId(), "CREATED");
    });
}
```

What to make idempotent:
- payment charges
- order creation
- inventory reservation
- email/SMS sends
- any operation that must not be duplicated

Theory:
- Retries without idempotency create duplicate writes, duplicate charges, or duplicate notifications.
- Idempotency key storage should be scoped to a reasonable TTL (24-72 hours is common).

### Rate limiting

Algorithms:
- Token bucket: bucket fills at a refill rate, burst allowed up to bucket capacity. Most common.
- Leaky bucket: requests drain at a fixed output rate. Smooths bursts more aggressively.
- Fixed window: count in the current time window. Boundary burst problem (100 req at end of window + 100 at start = 200 in any 10-second span).
- Sliding window log: exact, accurate, but memory-intensive. Store timestamp of every request.
- Sliding window counter: hybrid of fixed window + interpolation. Accurate and memory-efficient.

Distributed rate limiting:
- Use Redis with Lua scripting for atomic check-and-decrement across all app instances.
- Store the rate limit key with TTL equal to the window duration.

Rate limiter placement:
- API Gateway: coarse-grained per-client limiting.
- Service-level: fine-grained limiting per endpoint or per internal API consumer.

## 10. Security in system design

### Authentication and authorization

Authentication (AuthN): who is the caller?
- JWT (JSON Web Token): stateless token with claims signed by the authentication server.
- OAuth2 + OIDC: standard delegated authorization protocol. Access token (OAuth2) + ID token (OIDC).
- API Key: simple shared secret for server-to-server communication.
- mTLS: mutual TLS where both client and server present certificates. Strong for service-to-service.

Authorization (AuthZ): what is the caller allowed to do?
- RBAC (Role-Based Access Control): permissions defined by role. Simple and auditable.
- ABAC (Attribute-Based Access Control): permissions evaluated from user, resource, and context attributes. More flexible, more complex.
- Policy-as-code: OPA (Open Policy Agent) externalizes authorization logic from application code.

JWT structure:
- Header: algorithm.
- Payload: claims (sub, exp, roles, tenant).
- Signature: signed with a secret (HMAC) or private key (RSA/EC).
- JWTs should always be validated for signature, expiry, audience, and issuer.
- Never store sensitive data in JWT payload because it is only encoded, not encrypted.

### Encryption

At rest:
- DB encryption: PostgreSQL TDE, RDS encryption.
- File/object: server-side encryption with customer-managed keys (SSE-KMS).
- Field-level encryption: encrypt specific sensitive columns (PII, payment data) before storing.

In transit:
- TLS 1.2 minimum, TLS 1.3 preferred for all service-to-service and client-to-service communication.
- Certificate rotation and management: use managed certificate services where possible.

Secrets management:
- Never store secrets in application code or environment variables unencrypted.
- Use Vault, AWS Secrets Manager, or Azure Key Vault.
- Rotate database passwords, API keys, and certificates without application restarts.

### Common attack vectors to know

Injection:
- SQL injection: use parameterized queries only, never string concatenation.
- SSRF (Server-Side Request Forgery): validate and allowlist outbound URLs. Never pass user-controlled URLs directly to HTTP clients.

Prompt injection (AI systems):
- Exists at the intersection of AI and system design.
- Malicious text in retrieved documents can hijack LLM instructions.

Broken access control:
- Always authorize at the resource level, not just at the route level.
- Check that the requesting user owns or has rights to the specific resource being accessed.

DDoS protection:
- Rate limiting at edge (CDN, WAF).
- IP reputation filtering.
- CAPTCHA for human verification where appropriate.

## 11. Observability and operations

### The three pillars

Logs:
- Use structured logs (JSON) so logs are machine-parseable.
- Include correlation ID, trace ID, service name, version, user ID (hashed for PII compliance).
- Log at appropriate levels: DEBUG in dev only, INFO for business events, WARN for degraded state, ERROR for failures.

Metrics:
- RED metrics (the standard): Rate (requests/sec), Errors (error rate), Duration (latency percentiles).
- USE metrics (for infrastructure): Utilization, Saturation, Errors.
- Collect: request rate, P50/P95/P99 latency, error rate, DB query time, cache hit/miss ratio, queue lag, thread pool utilization, GC pause time, heap usage.

Traces:
- Distributed trace: a chain of spans across services for a single request.
- Each span records: service name, operation name, start time, duration, errors, parent span ID.
- OpenTelemetry is the standard instrumentation library.
- Send traces to Jaeger, Zipkin, or cloud-native services (AWS X-Ray, Google Cloud Trace).

### Alerting principles
- Alert on symptoms, not causes: "error rate > 5% for 5 minutes" is better than "CPU > 80%".
- Alert on SLA-affecting conditions only. Too many alerts cause alert fatigue.
- Set up pages (PagerDuty) for P0/P1 SLA violations. Use tickets for slower degradations.

### Health endpoints
Every service must expose:
- `/health/live`: liveness check. Returns 200 if the process is running. Returns 503 if it should be restarted.
- `/health/ready`: readiness check. Returns 200 if the service can accept traffic. Returns 503 during startup or maintenance.
- Load balancers use readiness to route traffic. Orchestrators use liveness to restart failed pods.

## 12. Multi-region and disaster recovery

### Active-passive (cold/warm standby)
- Primary region serves all traffic.
- Standby region has replicas of data. Inactive or partially warm.
- Failover requires DNS switchover (minutes) and data lag risk.
- Simpler to operate. Higher RTO (recovery time objective).

### Active-active
- Multiple regions simultaneously serve traffic.
- Multi-leader replication or global routing to home region.
- Conflict resolution is required for concurrent writes to different regions.
- Near-zero RTO. Complex to implement correctly.

### RPO and RTO
- RPO (Recovery Point Objective): maximum acceptable data loss measured in time. "We can lose at most 5 minutes of data."
- RTO (Recovery Time Objective): maximum acceptable downtime. "Service must recover within 15 minutes."
- Lower RPO requires more frequent replication or synchronous cross-region writes.
- Lower RTO requires warm standby or active-active, not cold backup.

### Backup and restore strategy
- Database backups: daily full + incremental. Test restore regularly.
- Point-in-time recovery (PITR): replaying WAL logs to restore to any point in time.
- Object storage: versioning and cross-region replication.

### Schema and data migrations with zero downtime
Strategy:
1. Expand: add new column/table in backward-compatible way.
2. Migrate: backfill data in batches. Never update millions of rows in a single transaction.
3. Switch: update application code to use the new structure.
4. Cleanup: remove old columns or tables after confirming the new path is stable.

Theory:
- Never do a big-bang schema migration on a live production database.
- Broken migrations are a top cause of production incidents at scale.

## 13. API design at scale

### REST API conventions
- Use nouns for resource URLs: `/users/{id}/orders` not `/getOrdersForUser`.
- Use HTTP methods semantically: GET (read), POST (create), PUT (replace), PATCH (partial update), DELETE.
- Return consistent error shapes: include error code, human-readable message, request ID.
- Use HTTP status codes correctly: 200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500.

### Pagination
Offset-based:
- `GET /items?offset=0&limit=20`. Simple but slow at large offsets.

Cursor-based:
- `GET /items?cursor=<opaque_token>&limit=20`. Stable with concurrent writes. Scales well.
- Cursor encodes the last-seen position (timestamp, ID) and is opaque to the client.

### Idempotency for mutation APIs
- Include `Idempotency-Key` header for POST requests on critical operations.
- Store processed results with key. Return cached result on duplicate.

### API versioning strategies
- URL versioning: `/v1/orders`, `/v2/orders`. Explicit, easy to route.
- Header versioning: `Accept: application/vnd.api.v2+json`. Cleaner URLs.
- Use semantic versioning. Never break existing clients without deprecation window.

### gRPC vs REST
Use gRPC when:
- internal service-to-service communication where both sides are controlled.
- you need bidirectional streaming.
- you need strict schema enforcement via Protobuf.
- you want lower serialization overhead.

Use REST when:
- external-facing APIs consumed by web, mobile, or third parties.
- content negotiation or browser compatibility required.
- team is not familiar with Protobuf tooling.

## 14. Questions to master

### 1. URL shortener
Scale: 100M URLs, 10B redirects/day.

Focus on:
- Hash generation: base62 encoding of auto-increment ID or hash-then-trim.
- Uniqueness: collision detection with DB unique constraint or pre-generated key service.
- Read-heavy scaling: aggressive caching (Redis), CDN for popular URLs.
- Analytics: async event stream for click tracking, do not make analytics synchronous.

Strong answer elements:
- Separate the write path (URL creation) from the read path (redirection).
- Redis cache with TTL matching URL expiry policy.
- Use HTTP 301 (permanent) vs 302 (temporary) redirect strategically: 301 is cached by browsers (less tracking), 302 always hits your server (better analytics).

### 2. Notification service
Scale: 1B users, multi-channel (email, SMS, push, in-app).

Focus on:
- Fan-out: store notifications per user vs fan-out at query time. Fan-out on write for moderate-follower users.
- Channel routing: abstract notification type from delivery channel. Users configure preferences.
- Retries with DLQ per channel. Email and SMS have different SLAs.
- Rate limiting: do not spam users. Per-user, per-channel rate limits.
- Template storage: externalize templates, support localization.

Strong answer elements:
- Use Kafka topic per notification type.
- Channel workers subscribe and handle channel-specific delivery and retry logic.
- Track notification state (PENDING, SENT, FAILED, READ) in DB.
- Idempotent delivery to avoid duplicate sends.

### 3. Rate limiter
Scale: 100K RPS globally.

Focus on:
- Algorithm trade-offs: token bucket (bursts OK), leaky bucket (smooth output), sliding window (accurate).
- Distributed counter: Redis with Lua for atomic check-and-decrement.
- Rate limit by: user ID, IP, API key, tenant.
- Response headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

```java
public boolean allow(String key) {
    Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, clock.millis()));
    synchronized (bucket) {
        refill(bucket);
        if (bucket.tokens == 0) return false;
        bucket.tokens--;
        return true;
    }
}
```

Trade-off discussion:
- Token bucket allows bursts up to bucket capacity.
- Sliding window log is most accurate but stores every request timestamp.
- Sliding window counter is a good balance: accurate and O(1) per check.

### 4. Chat system
Scale: 100M conversations, real-time delivery.

Focus on:
- WebSocket for real-time bidirectional messaging.
- Message ordering: use sequence number per conversation guaranteed by DB sequence or Redis INCR.
- Online presence: maintain user-to-connection-server mapping in Redis.
- Offline delivery: store undelivered messages. Deliver on reconnection.
- Horizontal scaling of WebSocket servers: use a pub/sub layer (Redis pub/sub or Kafka) so any server can deliver to any user.

Strong answer elements:
- Message storage: Cassandra (HBase) for high-write message logs, partitioned by conversation ID.
- Fanout service broadcasts to all participants in a group.
- Read receipts: async status updates, not blocking the send path.

### 5. News feed
Scale: 500M users, celebrities with 100M followers.

Focus on:
- Fan-out on write: precompute feeds for followers at post time. Low read latency, high write amplification.
- Fan-out on read: compute feed at read time. Low write cost, high read latency.
- Hybrid: fan-out on write for normal users, fan-out on read for celebrities.
- Feed storage: Redis sorted set (score = timestamp) for recent feed, DB for older.
- Ranking: chronological is simple. ML ranking requires feature store and online inference, beyond scope of most interviews.

Strong answer elements:
- Celebrities (verified with many followers) are pulled at read time and merged with pre-computed feed.
- Pagination with cursor on feed sorted set.
- Feed cache TTL with delta merge for real-time updates.

### 6. Order management system
Scale: 10K orders/sec, financial accuracy required.

Focus on:
- Inventory consistency: use optimistic locking or compare-and-swap for reservation.
- Idempotency: order creation must be safe to retry.
- Payment workflow: reserve → charge → confirm. Use saga for cross-service consistency.
- Event-driven design: OrderCreated → InventoryReserved → PaymentCharged → OrderConfirmed.
- Outbox pattern: OrderCreated event published atomically with order insert.

Strong backend answer elements:
- Use an idempotency key for client retries.
- Persist order and outbox event in the same DB transaction.
- Make inventory reservation time-bound; release if payment does not complete within N minutes.
- DLQ for failed events. Alert on DLQ depth.

### 7. Metrics ingestion pipeline
Scale: 1B data points/day, time-series query.

Focus on:
- High write throughput: Kafka ingest layer absorbs burst writes.
- Time-series storage: InfluxDB, TimescaleDB (Postgres extension), or Cassandra with time-ordered partition key.
- Aggregation pipeline: pre-aggregate by minute, hour, day to speed up query.
- Retention policy: raw data retained for 30 days, aggregated data for 1 year.
- Schema: metric name, tags as dimensions, timestamp, value.

Strong answer elements:
- Ingest service validates schema and writes to Kafka.
- Consumer writes to time-series DB in batches.
- Grafana or Prometheus-compatible query layer.
- Compaction and TTL for old raw data.

### 8. Search autocomplete
Scale: 10M queries/day, P95 < 50ms.

Focus on:
- Trie data structure for prefix matching.
- In-memory trie index with periodic sync to persistence.
- For large datasets: Redis Sorted Set with prefix-scored members, or Elasticsearch.
- Query caching: short-lived cache for popular prefixes.
- Ranking: by frequency (precomputed), personalization at query time.

Strong answer elements:
- Aggregate queries to compute prefix popularity.
- Separate offline index building from real-time serving.
- Top-K results per prefix pre-computed and stored.

### 9. Google Drive / distributed file storage
Scale: 1B files, multi-device sync.

Focus on:
- Chunking: split large files into fixed-size chunks for deduplication and delta sync.
- Metadata DB: store file hierarchy, chunk references, version history.
- Block storage: object storage (S3) for actual chunk data.
- Sync protocol: detect changes via checksum on client, upload only changed chunks.
- Conflict resolution: merge, last-writer-wins, or manual conflict presentation.

Strong answer elements:
- Deduplication at chunk level saves storage for repeated content.
- Pre-signed S3 URLs for client-direct upload/download without routing through app server.
- Notification service (WebSocket or long-poll) to push sync events to connected clients.

### 10. AI-powered document Q and A system
Scale: 1M documents, 100K users.

Focus on:
- Ingestion pipeline: load → clean → chunk → embed → store in vector DB.
- Query path: embed query → vector search → rerank → build grounded prompt → LLM → return with citations.
- Chunking strategy: 500–1000 token chunks with overlap. Preserve section context.
- Security: per-user and per-tenant document access control enforced before vector search.
- Evaluation: track retrieval relevance and answer faithfulness.

Strong answer elements:
- Hybrid retrieval: keyword search (BM25) + semantic search, merged with RRF (Reciprocal Rank Fusion).
- Prompt injection guard: sanitize retrieved content before passing to LLM.
- Latency budget: vector search < 50ms, LLM call < 2s, total < 3s P95.

## 15. What strong candidates do

- ask clarification questions before drawing architecture
- state what they are optimizing for before going deep
- identify scale assumptions explicitly
- compare 2 options before selecting one
- talk about failure scenarios naturally
- reason about trade-offs without overengineering
- connect design to real production consequences

Senior signal:
- They discuss migrations, backward compatibility, operator visibility, and what happens when a dependency fails at 2 a.m.
- They say: "I would validate this with load testing before committing to this design."

## 16. What weak candidates do

- jump into microservices without need
- ignore data model and consistency
- ignore operational concerns
- never discuss cost or latency
- give memorized answers with no adaptation
- draw boxes without explaining traffic flow, failure modes, or data lifecycle

Common red flags in interviews:
- "Use Kafka for everything" without justification.
- "Use Redis for everything" without differentiating what is being cached.
- Designing for 1 billion users when the prompt says 1 million.
- Never asking about consistency, durability, or failure expectations.

## 17. Trade-off vocabulary

Use these phrases naturally in interviews:

| Trade-off | Language to use |
|---|---|
| Consistency vs availability | "I would choose eventual consistency here because the user impact of stale data is low, but that trade-off would not be acceptable for payment state." |
| Write amplification vs read simplicity | "Fan-out on write precomputes which reduces read latency but multiplies write cost. Given the read-to-write ratio here, I prefer the write overhead." |
| Normalization vs denormalization | "I would denormalize the user display name into the post table to avoid a join on every feed render, accepting that we need to propagate name changes asynchronously." |
| Simple vs correct | "The simpler at-least-once delivery is fine here because the consumer is idempotent." |
| Latency vs cost | "I would add a CDN layer here, accepting the added cost to meet the 50ms P95 SLA." |

## 18. Practice structure

Each week do:
- 2 short design drills: 30-40 minutes each
- 1 full mock: 60 minutes with peer or recorded for self-review
- 1 review session where you critique your own answer against the step-by-step framework

Track every practice session with:
- problem name
- scale assumption you started with
- components you chose
- what you missed that you would add next time
- trade-offs articulated clearly

## 19. Senior-level expectation

At your experience level, you should show:
- sound judgment under ambiguity
- ownership mindset: you own the design end to end
- cross-component reasoning: when the DB choice changes, what else changes?
- awareness of migration, rollout, and failure handling
- cost awareness: "this design would cost roughly X, which at Y scale matters because..."
- operational empathy: "the on-call engineer needs to understand what broke from the alerts alone"
