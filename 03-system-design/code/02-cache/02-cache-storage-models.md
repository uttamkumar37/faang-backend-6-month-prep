# Databases & Storage Deep Dive

## Indexing

### B-Tree Index

The default index in PostgreSQL, MySQL. Balanced tree, O(log n) read.

```
Internal nodes: keys + child pointers
Leaf nodes: keys + row pointers (heap tuples)
Leaf nodes linked → efficient range scans

B+ Tree variant (used by most RDBMS):
- All data in leaf nodes
- Internal nodes only for routing
- Supports ORDER BY, range queries, LIKE 'foo%'
```

### When Index Helps vs Hurts

| Situation | Index Helps? |
|---|---|
| WHERE id = 5 | Yes — point lookup |
| WHERE created_at BETWEEN ... | Yes — range scan |
| WHERE email LIKE '%gmail%' | No — leading wildcard |
| Low cardinality: WHERE gender = 'M' | Usually no (50% selectivity) |
| Frequent INSERT/UPDATE on column | No — write amplification |

### Composite Index Rules

```sql
INDEX (last_name, first_name, dob)

-- Uses index (leftmost prefix):
WHERE last_name = 'Smith'
WHERE last_name = 'Smith' AND first_name = 'John'

-- Does NOT use index:
WHERE first_name = 'John'  -- skips last_name
WHERE dob = '1990-01-01'   -- skips both
```

### Covering Index

Index contains all columns needed for the query → no table heap fetch.

```sql
CREATE INDEX idx_covering ON orders(user_id, status, created_at);
SELECT status, created_at FROM orders WHERE user_id = 5;
-- Satisfied entirely from index — no row lookup
```

---

## EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 100;

Seq Scan on orders  (cost=0.00..45.2 rows=1200)
  -- Bad! Table scan - add index.

vs:

Index Scan using idx_user_id on orders
  (cost=0.43..8.45 rows=10 actual time=0.023..0.034 rows=10)
  -- Good!
```

Key terms: Seq Scan (bad for large tables), Index Scan, Bitmap Index Scan, Nested Loop Join, Hash Join.

---

## Sharding Strategies

Distributing data across nodes:

### Range Sharding

```
Shard 0: user_id 1 – 1,000,000
Shard 1: user_id 1,000,001 – 2,000,000
```

Pro: range queries stay on one shard.  
Con: hotspots (newest users on last shard).

### Hash Sharding

```
shard = hash(user_id) % N
```

Pro: even distribution.  
Con: range queries span all shards.

### Directory Sharding

Lookup table: `user_id → shard_id`.  
Pro: flexible reassignment.  
Con: extra lookup overhead, single point of failure if lookup service fails.

---

## NoSQL Types Comparison

| Type | Database | Best For | Data Model |
|---|---|---|---|
| Key-Value | Redis, DynamoDB | Session, cache, config | key → blob |
| Document | MongoDB, Firestore | Semi-structured, nested | JSON documents |
| Wide-Column | Cassandra, HBase | Time-series, append-heavy | rows × dynamic columns |
| Graph | Neo4j | Relationships, recommendations | nodes + edges |
| Search | Elasticsearch | Full-text, faceted search | inverted index |
| Time-Series | InfluxDB, TimescaleDB | Metrics, IoT telemetry | sorted by time |

---

## Replication Models

### Single-Primary Replication

```
Primary (read + write)
   ↓ binary log (async)
Replica 1 (read-only)
Replica 2 (read-only)

Read scaling: route reads to replicas.
Risk: replication lag → stale reads.
```

### Multi-Primary Replication

Multiple primaries accept writes. Conflict resolution required.  
Risk: higher conflict rate. Used by: CockroachDB, Galera Cluster.

### Synchronous vs Asynchronous

- **Async**: primary commits without waiting for replicas → fast, risk of data loss if primary crashes.
- **Sync**: primary waits for at least one replica to confirm → no data loss, higher write latency.
- **Semi-sync**: AWS RDS default — wait for one replica before acknowledging.

---

## ACID vs BASE

| ACID | BASE |
|---|---|
| Atomicity — all or nothing | Basically Available |
| Consistency — valid state after txn | Soft state |
| Isolation — concurrent txns invisible | Eventually consistent |
| Durability — committed data persists | |

ACID: PostgreSQL, MySQL  
BASE: Cassandra, DynamoDB (can be tuned toward stronger consistency)

---

## Write-Ahead Log (WAL)

Every change written to WAL (disk) before modifying data pages.  
On crash: replay WAL from last checkpoint → no data loss.  
Enables: point-in-time recovery, logical replication.

---

## Interview Tips

- Explain composite index leftmost prefix rule — commonly asked.
- Know when to shard vs when to use read replicas.
- Differentiate OLTP (transactional, normalized) vs OLAP (analytical, denormalized, column-store).
- Index adds cost on writes — every insert/update must maintain the index.
