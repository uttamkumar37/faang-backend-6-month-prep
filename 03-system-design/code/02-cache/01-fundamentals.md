# System Design Fundamentals

## Scale Estimation Cheat Sheet

```
1 million = 10^6 requests/day ≈ 12 req/sec
10 million = 10^7 req/day ≈ 115 req/sec
1 billion = 10^9 req/day ≈ 11,500 req/sec

Storage:
- 1 char = 1 byte
- 1 tweet/message ~ 200 bytes
- 1 photo ~ 200 KB avg
- 1 video ~ 50 MB avg
- 1 year × 10M photos = 10M × 200KB = 2 TB/year

Latency (orders of magnitude):
L1 cache hit:         0.5 ns
Main memory:         100 ns
SSD read:         100,000 ns = 0.1 ms
Network (same DC):  500,000 ns = 0.5 ms
HDD seek:         10,000,000 ns = 10 ms
Network (cross DC):100,000,000 ns = 100 ms
```

---

## Core Components

### Load Balancer
- **L4** (transport layer): routes by IP/port, lowest latency, no content inspection.
- **L7** (application layer): routes by URL path, headers, cookies — enables content-based routing, SSL termination.
- **Algorithms**: Round-robin, Least connections, IP hash (sticky sessions), Weighted.

```
Clients → Load Balancer (L7) → [Server 1, Server 2, Server 3]
                                     ↓
                             Consistent Hash Ring (sticky user routing)
```

### CDN (Content Delivery Network)
- Push CDN: you pre-upload assets; best for static content that changes rarely.
- Pull CDN: CDN fetches from origin on first miss; best for popular dynamic content.
- **Cache-Control headers** determine TTL: `Cache-Control: public, max-age=86400`

### Database — SQL vs NoSQL Decision

| Criteria | SQL (Postgres/MySQL) | NoSQL |
|---|---|---|
| Data model | Relational, structured | Flexible schema |
| ACID | Strong | Eventual / BASE |
| Joins | Yes | No (denormalize) |
| Scale | Vertical + read replicas | Horizontal sharding |
| Use case | Financial, orders, users | Catalog, events, social graph |

**When to shard SQL**: single node > 1TB or > 10K qps writes.  
**Sharding strategies**: Range, Hash, Directory-based.

---

## Replication

### Master-Replica (Master-Slave)
```
Writes → Master → Replication lag → Replica 1
                                  → Replica 2 (read traffic)
```

**Read-after-write inconsistency**: user writes then reads from replica that hasn't replicated yet.  
**Solution**: route user's own reads to master for 1 minute after write.

### Multi-Master
- Multiple nodes accept writes.
- Conflict resolution needed (last-write-wins, CRDTs, application-level).

---

## Caching Strategies

```
1. Cache-aside (lazy loading)
   App → miss → DB → App stores in cache → cache hit

2. Write-through
   App → Cache → DB (synchronously)
   Pros: No stale data. Cons: Write latency.

3. Write-behind (write-back)
   App → Cache (ack) → async write to DB
   Pros: Low write latency. Cons: Data loss risk.

4. Read-through
   App → Cache → if miss → Cache fetches from DB → App
```

**Cache eviction**: LRU (most common), LFU, TTL-based.

---

## CAP Theorem

A distributed system can provide only 2 of 3:
- **C**onsistency: every read gets latest write.
- **A**vailability: every request gets a (non-error) response.
- **P**artition tolerance: system works despite network partition.

Since partitions are inevitable: choose **CP** or **AP**.

| System | Choice |
|---|---|
| Bank transactions | CP (Consistency > Availability) |
| Shopping cart | AP (Availability > Consistency) |
| DNS | AP |
| HBase, Zookeeper | CP |
| Cassandra, DynamoDB | AP |

---

## Consistent Hashing

Solves the "which server?" problem when servers scale up/down.

```
Hash ring (0 → 2^32)
Server A owns: 0-90°; Server B: 90-180°; Server C: 180-270°; Server D: 270-360°
Request: hash(key) → position → clockwise to next server

When server B removed: B's range goes to server C only.
Virtual nodes (vNodes): each server owns multiple positions for even distribution.
```

Applications: consistent hashing in Cassandra, Memcached pools, Dynamo.

---

## Message Queues

Why: decouple producers from consumers, handle bursts, enable async processing.

```
Producers → [Queue/Topic] → Consumers
                ↓
         Kafka: append-only log, replay possible, ordered per partition
         RabbitMQ: message queued, consumed once (push model)
         SQS: managed, at-least-once delivery, visibility timeout
```

**Kafka key concepts**:
- Topics → Partitions → Segments (files on disk).
- Consumer group: each partition consumed by one member → parallelism.
- Offset: position in log; consumer commits after processing.
- Retention: time-based or size-based.

---

## Interview Framework (RADIO)

1. **R**equirements: functional (what the system does), non-functional (scale, latency, availability).
2. **A**PI design: define the core APIs/endpoints first.
3. **D**ata model: entities, relationships, storage choice.
4. **I**nterface: high-level architecture diagram.
5. **O**ptimize: address bottlenecks — caching, sharding, CDN, async.

### Asking Good Clarifying Questions
- "How many daily active users?"
- "What's the read/write ratio?"
- "What's the latency SLA?"
- "Do we need strong consistency or is eventual acceptable?"
- "Is this geographically distributed?"
