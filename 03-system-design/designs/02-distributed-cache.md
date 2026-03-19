# Design a Distributed Cache (Like Redis)

## Requirements

**Functional:**
- GET, SET, DELETE operations.
- TTL support.
- Eviction (LRU).

**Non-Functional:**
- P99 latency < 1ms.
- 1 million reads/sec.
- 99.99% availability.
- Items stored in-memory.

---

## Core Data Structures

```
Hash Table: O(1) get/set
Doubly Linked List: O(1) LRU eviction tracking

Combined: LinkedHashMap (accessOrder=true) in Java
```

---

## Internals

### LRU Eviction

```
[Head (MRU)] ↔ A ↔ C ↔ B ↔ [Tail (LRU)]

GET C → move C to head:
[Head] ↔ C ↔ A ↔ B ↔ [Tail]

SET D (at capacity) → evict tail (B):
[Head] ↔ D ↔ C ↔ A ↔ [Tail]
```

### Memory Model

Each key-value entry estimates size:
- Key: string bytes
- Value: serialized bytes
- Overhead: ~100 bytes per entry header

When `usedMemory > maxMemory × 0.85` → evict LRU entries.

---

## Distributed Architecture

```
Client → Client Library (consistent hashing) → Cache Nodes

         ┌─────────────────────────────────────┐
         │     Cache Cluster (N nodes)          │
         │                                      │
         │  Node A    Node B    Node C    Node D │
         │  Keys 0-90° 90-180° 180-270° 270-360°│
         └─────────────────────────────────────┘
```

### Consistent Hashing + Virtual Nodes

```
Each physical node gets 150 virtual nodes (positions) on the ring.
hash(key) → find clockwise → owning node.
Adding node: only keys in the range shift = 1/N of total keys.
Removing node: only that node's keys rehash.
```

### Replication

```
Primary Shard → async replication → Replica 1
                                  → Replica 2

Read options:
- Read from primary: strong consistency
- Read from replica: eventual consistency, lower latency
```

---

## Network Protocol

Redis uses RESP (REdis Serialization Protocol):

```
SET key value:
*3          (3 arguments)
$3          (3 bytes)
SET
$3
key
$5
value

GET key → $5\r\nvalue\r\n  or  $-1\r\n (null)
```

Simple line-based protocol; binary-safe; easy to parse.

---

## Hot Key Problem

A single key receives disproportionate traffic (e.g., celebrity post).

**Solutions:**
1. Key splitting: `hotkey` → `hotkey:{0..9}` (random suffix), read from any shard.
2. Local in-process cache (Caffeine) in front of Redis: absorbs hot reads.
3. Read replicas: route reads to multiple replicas.

---

## Cache Stampede (Thundering Herd)

Happens when a popular cached item expires and thousands of requests simultaneously hit the DB.

**Solutions:**
1. **Lock**: first request acquires a Redis lock, others wait.
2. **Stale-while-revalidate**: serve stale while one thread refreshes.
3. **Probabilistic early expiration**: before TTL expires, probabilistically refresh with formula:
   ```
   currentTime - delta * beta * ln(random()) > expiry → refresh
   ```

---

## Monitoring Key Metrics

```
cache_hit_ratio       goal: > 95%
eviction_rate         alert if: high (means cache too small)
memory_usage          alert at: 80%
latency_p99           goal: < 1ms
connected_clients     alert if: approaching max_clients
replication_lag_seconds  alert if: > 100ms
```

---

## Interview Tips

- Start with single-node in-memory LRU cache design in Java.
- Then discuss distribution: consistent hashing, replication, sharding.
- Address the hot key problem and stampede explicitly — common follow-up.
- Explain write options: write-through vs write-behind vs cache-aside.

---

## Project Structure

```
distributed-cache/
├── src/main/java/com/cache/
│   ├── CacheServer.java              # main — listens on TCP port
│   ├── core/
│   │   ├── LruCache.java             # LinkedHashMap-backed, O(1) get/put
│   │   ├── TtlCache.java             # wraps LruCache, adds expiry
│   │   └── EvictionPolicy.java       # LRU / LFU strategy
│   ├── cluster/
│   │   ├── ConsistentHashRing.java   # routes key → node
│   │   └── ReplicationManager.java  # async replication to replicas
│   └── protocol/
│       └── RespParser.java           # RESP protocol (Redis-compatible)
└── pom.xml
```

## Core Implementation

```java
// Thread-safe LRU cache with TTL
public class TtlCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, Entry<V>> map;

    record Entry<V>(V value, long expiresAt) {}

    public TtlCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, true) { // accessOrder=true
            @Override protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> e) {
                return size() > capacity; // LRU eviction when full
            }
        };
    }

    public synchronized void put(K key, V value, long ttlMs) {
        long expiresAt = ttlMs > 0 ? System.currentTimeMillis() + ttlMs : Long.MAX_VALUE;
        map.put(key, new Entry<>(value, expiresAt));
    }

    public synchronized Optional<V> get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return Optional.empty();
        if (System.currentTimeMillis() > e.expiresAt()) {
            map.remove(key); // lazy expiration
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    public synchronized void delete(K key) { map.remove(key); }

    public synchronized int size() { return map.size(); }
}

// Consistent hash ring for distributing keys across nodes
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int vnodes;

    public ConsistentHashRing(int vnodes) { this.vnodes = vnodes; }

    public void addNode(String nodeId) {
        for (int i = 0; i < vnodes; i++)
            ring.put(hash(nodeId + "-vnode-" + i), nodeId);
    }

    public void removeNode(String nodeId) {
        for (int i = 0; i < vnodes; i++)
            ring.remove(hash(nodeId + "-vnode-" + i));
    }

    public String getNode(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("No nodes");
        Long h = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(h);
        return (entry != null ? entry : ring.firstEntry()).getValue();
    }

    private long hash(String s) {
        // MurmurHash-inspired mixing
        long h = 0;
        for (char c : s.toCharArray()) h = h * 31 + c;
        return h & Long.MAX_VALUE; // positive only
    }
}

// Cache-aside pattern (application side)
public class CacheAsideService {
    private final TtlCache<String, User> cache;
    private final UserRepository db;

    public User getUser(String userId) {
        return cache.get(userId).orElseGet(() -> {
            User u = db.findById(userId);
            cache.put(userId, u, Duration.ofMinutes(5).toMillis());
            return u;
        });
    }

    public void updateUser(String userId, User updated) {
        db.save(updated);
        cache.delete(userId); // invalidate — not update (avoids race condition)
    }
}
```
