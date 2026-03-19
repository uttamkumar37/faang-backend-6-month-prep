# Database Patterns for System Design

## Sharding (Horizontal Partitioning)

### Why Shard?

Single DB hit limits: ~100K QPS read, ~10K QPS write, ~10TB practical size.  
Beyond that → shard (split rows across multiple DB instances).

### Sharding Strategies

#### 1. Range-Based Sharding
```
user_id:    1–1M    → shard-1
user_id:  1M–2M    → shard-2
user_id:  2M–3M    → shard-3

Pros: simple, range queries stay on one shard
Cons: hotspots (active users in latest range = shard-n overloaded)
```

#### 2. Hash-Based Sharding
```
shard = hash(user_id) % N

Pros: even distribution, no hotspots
Cons: range queries scatter across all shards (expensive)
      resharding on N change is disruptive
```

#### 3. Consistent Hashing (Distribution-aware)
```
shard = consistent_hash_ring.lookup(user_id)

Pros: minimal key remapping on node add/remove (only K/N move)
      vnodes prevent imbalance
Cons: more complex to implement; cross-shard queries still hard
```

#### 4. Directory-Based Sharding
```
lookup_service.getShard(entity_id) → shard ID

Pros: flexible placement, can move entities, supports heterogeneous shards
Cons: lookup service is a bottleneck and SPOF; need caching
```

### Choosing a Shard Key

| Anti-patterns | Problem |
|---------------|---------|
| `created_at` as shard key | All writes go to latest shard ("hot end") |
| Low cardinality (e.g., country) | Uneven load if country sizes differ |
| Frequently changing field | Writes must move entity to different shard |

**Good shard keys:** user_id, tenant_id, entity UUID — high cardinality + stable.

---

## Replication

### Leader-Follower (Primary-Replica)

```
         [ Primary ]
        /     |     \
  [Replica] [Replica] [Replica]

Writes: Primary only (serialized)
Reads:  Any replica (load distributed)
Lag:    Replication is async by default → replica may be stale
```

**Replication lag problems:**
1. "Read your own writes" — user writes, then reads from stale replica → sees old data.  
   Fix: Route writes + subsequent reads to primary for 1 min; or sticky sessions.

2. "Monotonic reads" — user reads from replica A (newer), then replica B (older).  
   Fix: Session stickiness to same replica.

3. "Consistent Prefix Reads" — partitioned writes arrive out of order on replica.  
   Fix: Causally related writes go to same partition.

### Multi-Leader (Active-Active)

```
  [DC-East Primary] ←→ [DC-West Primary]
       |                      |
   [Replicas]            [Replicas]

Writes: Either primary
Conflict resolution required:
  - Last-Write-Wins (LWW) — loses data on concurrent writes
  - Application-level merge (e.g., operational transform, CRDT)
  - "Conflict-free" design: avoid conflicting writes via sharding
```

### Leaderless Replication (Dynamo-style)

```
Quorum reads and writes:
  N = total replicas
  W = write quorum (writes must succeed on W nodes)
  R = read quorum (reads from R nodes, return latest version)

  W + R > N → guaranteed overlap → strong consistency

  Common: N=3, W=2, R=2  (tolerates 1 failure, consistent reads)
           N=3, W=1, R=3  (fast writes, strong read consistency)
           N=3, W=3, R=1  (strongest write durability, fast reads)
```

---

## Indexing Strategy

### B-Tree vs LSM-Tree

| Attribute | B-Tree (PostgreSQL/MySQL) | LSM-Tree (Cassandra/RocksDB) |
|-----------|--------------------------|------------------------------|
| Write performance | Random writes (slower) | Sequential writes only (faster) |
| Read performance | Single disk seek | Merge multiple SSTables (slower) |
| Space amplification | Low | Higher (multiple SSTables) |
| Write amplification | Medium | High (compaction) |
| Best for | Read-heavy, random access | Write-heavy, time-series |

### Index Types

```
B-Tree index    → equality + range queries (most common)
Hash index      → equality only — no ORDER BY, no range
Bitmap index    → low-cardinality columns (gender, status) — great for analytics
GiST/GIN        → full-text, geometry, JSONB
Partial index   → WHERE status = 'active' (smaller, faster for narrow queries)
Covering index  → INCLUDE (col1, col2) — avoids heap lookup
Composite index → order matters: (a, b, c) supports (a), (a,b), (a,b,c)
                  does NOT help for (b) or (b,c) queries — leftmost prefix rule
```

### N+1 Query Problem

```sql
-- BAD: N+1 (1 query for users + N queries for each user's orders)
SELECT * FROM users WHERE dept='eng';        -- returns 100 users
-- then for each user: SELECT * FROM orders WHERE user_id = ?

-- GOOD: JOIN or IN clause
SELECT u.*, o.* FROM users u
JOIN orders o ON o.user_id = u.id
WHERE u.dept = 'eng';
```

---

## Connection Pooling

```
Without pool:
  Each request opens TCP connection → 3-way handshake → TLS → auth
  = 50-100ms overhead per request

With pool:
  10-200 connections pre-established and reused
  Connection checkout → execute → return to pool

Pool sizing (Little's Law):
  pool_size = avg_concurrent_requests × avg_db_time_seconds

  Example: 500 RPS × 10ms avg DB time = 5 connections needed
  Rule of thumb: start with cpu_cores * 2, tune with monitoring
```

### HikariCP settings (Spring Boot default pool)

```yaml
spring.datasource.hikari:
  maximum-pool-size: 20       # max connections
  minimum-idle: 5             # keep warm
  idle-timeout: 600000        # idle conn returned after 10 min
  connection-timeout: 30000   # fail fast if pool exhausted (30s)
  max-lifetime: 1800000       # recycle after 30 min (avoid stale)
  keepalive-time: 60000       # ping every 1 min to avoid network timeout
```

---

## Read vs Write Separation

```
Write path:
  App → Primary DB → async replicate to read replicas

Read path:
  App → Read Replica (route by load or session)

Lag risk: read-your-own-writes requires routing to primary
  Solution A: read from primary for 1s after write (time window)
  Solution B: track "replication token" — wait until replica catches up
  Solution C: client tracks last write timestamp; replica uses wait_for_position()
```

---

## Schema Design Anti-Patterns

| Anti-Pattern | Problem | Fix |
|--------------|---------|-----|
| EAV (Entity-Attribute-Value) | No types, no indexes, expensive joins | Schema per entity type |
| One big table for everything | Locks, scans, bloat | Normalized schema |
| `SELECT *` | Fetches unnecessary columns, no covering index | Explicit column list |
| Nullable FK chains | NULL propagation, complex queries | Avoid deep nullable relations |
| UUID primary keys (random) | B-tree fragmentation, slower writes | ULID or sequential UUID |
| Soft delete via `deleted_at` | Full-table scans unless partial index | Archive table pattern |

---

## Database Interview Quick Reference

| Question | Answer |
|----------|--------|
| How to scale writes beyond one DB? | Shard by user_id with consistent hashing |
| How to scale reads? | Add read replicas, cache hot data in Redis |
| How does Cassandra handle availability? | Leaderless, quorum reads/writes, all nodes equal |
| What is MVCC? | Each write creates new version; readers see snapshot at start of tx |
| When to use NoSQL vs SQL? | NoSQL: high write throughput, flexible schema, wide column; SQL: complex queries, ACID |
| What is WAL? | Write-Ahead Log — writes logged before data pages; enables crash recovery and replication |
