# 03 — System Design

## What's in this folder

This folder is split into three layers:
- root guides for interview execution
- `designs/` for full worked system-design case studies
- `code/` for pattern notes plus Java reference implementations

### Reference Guides (root)

| File | Content |
|---|---|
| `CAPACITY-CHEATSHEET.md` | Back-of-envelope math: units, latency tables, per-system estimates, 3-step framework |
| `INTERVIEW-GUIDE.md` | 45-minute interview framework, deep-dive Q&A, red flags, FAANG-specific tips, scoring rubric |

### designs/
Full worked system designs — step by step with trade-off discussion (ordered simple → complex):

| # | File | Design | Key Topics |
|---|---|---|---|
| 1 | `01-url-shortener.md` | URL Shortener | Base62, caching, redirect logic |
| 2 | `02-distributed-cache.md` | Distributed Cache | LRU+TTL, consistent hashing, cache-aside |
| 3 | `03-rate-limiter.md` | Rate Limiter | Token bucket, sliding window, Redis Lua |
| 4 | `04-notification-system.md` | Notification System | Fan-out, DLQ, channel routing |
| 5 | `05-twitter-feed.md` | Twitter-style Feed | Fan-out on write vs read, celebrity problem |
| 6 | `06-search-autocomplete.md` | Search Autocomplete | Trie, top-K cache, prefix ranking |
| 7 | `07-google-drive.md` | Google Drive | Chunked upload, dedup, sync, S3 |
| 8 | `08-payment-system.md` | Payment System | Idempotency, outbox, double-entry ledger |
| 9 | `09-video-streaming.md` | Video Streaming (YouTube) | HLS/ABR, CDN, async encoding, view counts |
| 10 | `10-web-crawler.md` | Web Crawler | Bloom filter dedup, Frontier, politeness, SSRF |

### code/ — Learn in this order
Each numbered folder contains the concept note next to the Java implementation.

| # | Folder | Theory notes | Java file(s) | Covers |
|---|---|---|---|---|
| 1 | `01-databases/` | `01-database-patterns.md` | `DatabasePatternsExamples.java` | Sharding, replication, connection pool, WAL, MVCC, N+1 fix |
| 2 | `02-cache/` | `01-fundamentals.md`, `02-cache-storage-models.md`, `03-caching-deep-dive.md`, `04-cap-consistency-models.md` | `CachePatterns.java`, `ConsistencyModelsExamples.java` | LRU+TTL, cache-aside, stampede, CAP, quorum, vector clocks |
| 3 | `03-consistent-hashing/` | `01-consistent-hashing.md` | `ConsistentHashingExamples.java` | Hash ring, virtual nodes, Bloom filter, Count-Min Sketch, HyperLogLog |
| 4 | `04-messaging/` | `01-messaging-patterns.md` | `MessagingPatternsExamples.java` | Broker, consumer groups, at-least-once, fan-out, competing consumers |
| 5 | `05-ratelimiter/` | `06-api-design-patterns.md` | `RateLimiterPatterns.java` | Token bucket, sliding window log, fixed window, distributed counter |
| 6 | `06-idempotency/` | `04-message-queues-kafka.md`, `05-distributed-transactions.md` | `IdempotencyAndOutboxPatterns.java` | Idempotency keys, outbox pattern, saga |
| 7 | `07-circuitbreaker/` | `07-observability-and-slo.md` | `CircuitBreakerPatterns.java` | State machine, retry+backoff, bulkhead, timeout |
| 8 | `08-observability/` | `01-observability-patterns.md` | `ObservabilityPatternsExamples.java` | Counter/Gauge/Histogram, tracing, SLO/error budget, health checks |
| 9 | `09-security/` | `01-security-patterns.md` | `SecurityPatternsExamples.java` | JWT/OAuth2, PBKDF2, SSRF prevention, login rate-limit, CSRF, XSS |
| 10 | `10-database-internals/` | `01-database-internals.md` | `DatabaseInternalsPatterns.java` | B-tree vs LSM, MVCC, replica lag, query shape, online migrations |
| 11 | `11-distributed-systems-internals/` | `01-distributed-systems-internals.md` | `DistributedSystemsInternalsExamples.java` | Quorums, Raft mental model, fencing, idempotency, clock skew |
| 12 | `12-cloud-kubernetes/` | `01-docker-kubernetes-cloud.md` | `CloudPlatformPatterns.java` | Containers, probes, HPA, config separation, managed-service trade-offs |

## Interview answer framework

1. Clarify requirements and constraints.
2. Estimate scale: QPS, storage, bandwidth — use `CAPACITY-CHEATSHEET.md`.
3. Define APIs.
4. Draw high-level components.
5. Deep dive into DB, cache, queue.
6. Discuss scaling bottlenecks.
7. Discuss failure modes and reliability.
8. Observability and deployment.
9. Trade-offs and future improvements.

> See `INTERVIEW-GUIDE.md` for a detailed 45-minute script and scoring rubric.
