# System Design Interview Guide

> How to structure and ace a 45-minute system design interview at FAANG.

---

## The 45-Minute Framework

```
00–05 min  Clarify Requirements
05–10 min  Capacity Estimation
10–15 min  High-Level Design
15–30 min  Deep Dive (interviewer-led)
30–40 min  Scaling & Trade-offs
40–45 min  Wrap-up & Your Questions
```

---

## Stage 1: Clarify Requirements (5 min)

**Never start drawing boxes before asking questions.**

### Functional Requirements — ask these:
- Who are the users? What do they do?
- What are the top 3 most important features?
- Is this read-heavy or write-heavy?
- Do we need real-time or eventual consistency?
- Global system or single region?

### Non-Functional Requirements — state or ask:
- Scale: DAU, QPS (see CAPACITY-CHEATSHEET.md)
- Availability: 99.9% = 8.7 hrs/yr downtime, 99.99% = 53 min/yr
- Latency: P99 < 100ms? < 500ms?
- Durability: data loss acceptable? (payments = no, feed = maybe)
- Consistency: strong vs eventual
- CAP trade-off: partition tolerance + (availability or consistency)

### What to explicitly say out loud:
> "I'll assume 100M DAU, 10:1 read/write ratio, and we need 99.9% availability.
> Latency target is P99 < 200ms for reads. Shall I proceed?"

---

## Stage 2: Capacity Estimation (5 min)

Quick math — don't get bogged down. Use round numbers.

```
1. QPS = DAU × actions_per_day / 86,400
2. Peak QPS = QPS × 3
3. Storage = writes_per_day × avg_size × retention × replication_factor
4. Bandwidth = peak_QPS × avg_response_size
```

**Say the numbers, then draw the implication:**
> "100K QPS means we need Redis for caching — a single MySQL can't handle that."
> "5 TB/day means we need distributed blob storage, not local disk."

---

## Stage 3: High-Level Design (5 min)

Draw the 4–5 core components. Talk while you draw.

### Minimal component set to always include:
```
Client → Load Balancer → API Servers → Cache → Database
                                          ↓
                                     Message Queue → Workers
                                          ↓
                                     Blob/Object Store (if media)
```

### Component shorthand:
| Need | Default Choice |
|------|---------------|
| API layer | REST (stateless) + API Gateway |
| Load balancing | L7 (nginx/AWS ALB) for HTTP |
| Caching | Redis (in-memory, TTL-based) |
| Primary DB | PostgreSQL (relational) or DynamoDB (KV/document) |
| Message queue | Kafka (high throughput) or SQS (simple) |
| Blob storage | S3 or equivalent |
| Search | Elasticsearch |
| CDN | CloudFront for static/media |

---

## Stage 4: Deep Dive (15 min — interviewer-led)

The interviewer will pick one area to go deep on. Be ready for any of:

### Data Model Deep Dive
- Show the key tables/collections and their schemas
- Explain indexing strategy (what columns are indexed and why)
- Explain sharding key choice

### API Design Deep Dive
- Define the 3 core endpoints (REST or gRPC)
- Show request/response shapes
- Explain pagination (cursor-based > offset for large datasets)

### Scaling Deep Dive
- Where are the bottlenecks?
- How does the system scale from 1K to 1M to 100M users?
- What breaks first and how do you fix it?

### Common Deep-Dive Questions & Good Answers:

**"How do you handle hot spots / celebrity problem?"**
> Fan-out on write for regular users, fan-out on read (pull model) for celebrities with >10K followers.

**"How do you prevent duplicate processing?"**
> Idempotency keys: client generates UUID, stored in DB/Redis.
> Check before processing, use DB unique constraint as the source of truth.

**"Single point of failure?"**
> Everything is replicated: DB has read replicas + standby, Redis is clustered, multiple API server instances behind LB, Kafka has replica partitions.

**"How do you ensure exactly-once delivery?"**
> Idempotent consumers + transactional outbox pattern.

**"Cache invalidation strategy?"**
> Write-through for critical data, cache-aside with TTL for feeds.
> Invalidate on write using pub/sub or event-driven cache busting.

**"Database choice?"**
> If relational data + transactions needed → PostgreSQL
> If need horizontal scaling + simple lookups → DynamoDB/Cassandra
> If need full-text search → Elasticsearch
> If need time-series → InfluxDB / TimescaleDB

---

## Stage 5: Scaling & Trade-offs (10 min)

Walk through the system at each order of magnitude.

### Multi-layer scaling checklist:
```
10K users   → Single server + single DB is fine
100K users  → Add read replicas, Redis cache, CDN for static assets
1M users    → DB sharding, message queue for async work, separate services
10M users   → Multi-region, data partitioning, consistent hashing
100M users  → Full microservices, CQRS, event sourcing, global CDN
```

### Trade-offs to always mention:
| Decision | Trade-off |
|----------|-----------|
| SQL vs NoSQL | Consistency vs Scale |
| Sync vs Async | Latency vs Throughput |
| Strong vs Eventual consistency | Correctness vs Availability |
| Cache-aside vs Write-through | Simplicity vs Freshness |
| Monolith vs Microservices | Fast iteration vs Operational complexity |
| Fan-out on write vs read | Read performance vs Storage + write latency |

---

## Stage 6: Wrap-Up (5 min)

### Summarize your design out loud:
> "To summarize: we have a load-balanced API tier, Redis for hot reads, PostgreSQL with sharding for persistence, Kafka for async fan-out to followers, and S3 for media. The main trade-off is eventual consistency on the feed for scalability."

### Good questions to ask the interviewer:
- "Is there a specific component you'd like me to go deeper on?"
- "Are there any constraints I should revisit?"
- "How does the team handle [monitoring/deployment/data migrations] in practice?"

---

## Topic-by-Topic Cheat Sheet

### URL Shortener
- Key insight: Base62 encoding of auto-increment ID → short key
- Hot path: read-heavy → Redis cache entire keyspace
- Scale: 100K QPS reads, Redis single node sufficient

### Distributed Cache
- Key insight: consistent hashing for node distribution
- Eviction: LRU default, TTL for perishable data
- Cache stampede: use probabilistic early recomputation or Redis lock

### Rate Limiter
- Token bucket: bursty traffic allowed
- Sliding window log: accurate but high memory
- Fixed window counter: simple but spike at boundary
- Implementation: Redis Lua script for atomic operations

### Notification System
- Separate services per channel (push, email, SMS)
- Priority queue: urgent > normal
- DLQ for failed deliveries
- Fan-out via Kafka topic per user segment

### Twitter / Social Feed
- Write path: tweet → Kafka → fan-out workers → pre-computed timelines in Redis
- Celebrity exception: pull model for users with >10K followers
- Timeline: Redis sorted set by timestamp

### Search Autocomplete
- Trie with frequency counters
- Top-K cache at each trie node for O(1) suggestion
- Offline rebuild vs real-time update trade-off
- CDN for common prefixes

### Google Drive
- Chunked upload: 4 MB blocks, dedup by hash
- Delta sync: only changed blocks transferred
- Metadata DB: maps file → version → list of chunks
- Blob store (S3): actual content

### Payment System
- Idempotency: unique payment ID prevents duplicate charge
- Double-entry ledger: debit + credit always balance
- Outbox pattern: DB + Kafka in same transaction
- Saga for distributed transactions

---

## Red Flags to Avoid

| Anti-Pattern | Why It's Bad |
|---|---|
| Starting to code before requirements | Shows poor scoping |
| Jumping to microservices immediately | Over-engineering; adds complexity |
| Forgetting durability/replication | Shows naivety |
| Single points of failure everywhere | Shows lack of reliability thinking |
| No mention of monitoring/alerting | Shows lack of production experience |
| Not discussing trade-offs | Interviewers want to see nuanced thinking |
| Getting lost in one area | Time management is evaluated |
| Using SQL for everything | "Use the right tool" mindset expected |

---

## FAANG-Specific Tips

### Amazon
- Frame everything around customer impact ("this keeps latency < 100ms for customers")
- Amazon Leadership Principles matter even in design interviews
- Prefer AWS services when relevant: SQS, SNS, DynamoDB, S3, Kinesis

### Google
- Expect deeper algorithms discussion (consistent hashing, Chord, Paxos/Raft)
- Web crawler and search indexing are common questions
- Bigtable, Spanner, Chubby internals may come up

### Meta
- Social graph at scale is a common theme
- TAO (Facebook's graph cache) concept is useful to know
- Real-time messaging and feed algorithms

### Microsoft / Netflix / Uber
- Domain-specific: Azure services, video streaming, geo-distributed systems
- Trade-off discussions heavily weighted

---

## Signal Rubric (What Interviewers Score)

| Signal | What They Look For |
|---|---|
| Problem scoping | Did you ask the right questions? |
| Technical breadth | Do you know the tools? |
| Technical depth | Can you explain internals? |
| Trade-off analysis | Do you evaluate options? |
| Communication | Can you explain clearly while drawing? |
| Scalability thinking | Does your design hold at 100× scale? |
| Pragmatism | Is the design realistic to build? |
