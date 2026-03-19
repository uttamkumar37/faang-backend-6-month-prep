# Caching Deep Dive

## Why Cache?

```
L1 cache        : 0.5 ns
L2 cache        : 7 ns
RAM             : 100 ns
Redis           : ~0.5 ms (network)
SSD (local)     : 150 μs
Spinning disk   : 10 ms
Network (same DC): 0.5 ms
```

Cache sits between your service and slow storage, absorbing repeated reads.

---

## Cache Strategies

### Cache-Aside (Lazy Loading)

```
read:
  val = cache.get(key)
  if val == null:
    val = db.query(key)
    cache.put(key, val, TTL=300s)
  return val

write:
  db.update(key, newVal)
  cache.delete(key)   // invalidate, NOT update
```

**Pros**: Only populate what's actually requested.  
**Cons**: Cache miss penalty (3 trips). Stale data window until TTL expires or explicit invalidation.

### Write-Through

Every write goes to cache AND db simultaneously.

```
write:
  cache.put(key, val)
  db.update(key, val)

read:
  cache.get(key)  // always fresh
```

**Pros**: Cache always consistent.  
**Cons**: Write latency increased. Cache fills with cold data.

### Write-Behind (Write-Back)

Write to cache immediately, flush to DB asynchronously later.

```
write:
  cache.put(key, val, dirty=true)
  → background: batch flush dirty keys to DB every 5 seconds

read:
  cache.get(key)
```

**Pros**: Very low write latency.  
**Cons**: Data loss risk if cache crashes before flush.

### Read-Through

Cache sits in front of DB, handles all reads itself (populates itself on miss).

```
Service → Cache → (on miss) → DB
```

Differs from cache-aside: the cache itself queries DB, not the application.

---

## Cache Eviction Policies

| Policy | Description | Use Case |
|---|---|---|
| LRU | Evict least recently used | General purpose |
| LFU | Evict least frequently used | Content popularity (music, video) |
| TTL | Expire after fixed time | Session data, rate limit counters |
| FIFO | Evict oldest entry | Simple queues |
| Random | Evict random entry | Simple, cheap to implement |

---

## Redis Data Structures

```
String:    SET key val EX 300     → simple K/V, counters
Hash:      HSET user:1 name John  → object fields
List:      LPUSH queue taskId     → queues, stacks
Set:       SADD session:abc uid   → unique membership
Sorted Set:ZADD leaderboard 99 user1 → rankings, top-K
Bitmap:    SETBIT daily:2024 userId 1 → flags, bloom filters
HyperLogLog: PFADD unique_visits ip → cardinality estimation
Stream:    XADD events * ... → append-only log
```

---

## Cache Stampede (Thundering Herd)

When a popular key expires, thousands of requests all hit the DB simultaneously.

### Solution 1: Mutex/Lock

```
val = cache.get(key)
if val == null:
    if lock.tryAcquire(key, 100ms):     // one winner
        val = db.query(key)
        cache.put(key, val)
        lock.release(key)
    else:
        Thread.sleep(50ms)
        val = cache.get(key)            // re-read after lock held
```

### Solution 2: Stale-While-Revalidate

Serve stale value while background thread refreshes.

```
entry = cache.get(key)
if entry.isExpired():
    if !refreshInProgress(key):
        async { entry = db.query(key); cache.put(key, entry) }
    return entry.staleValue    // return stale immediately
```

### Solution 3: Probabilistic Early Refresh (XFetch)

```
// Refresh with increasing probability as TTL approaches 0
xfetch_should_refresh = currentTime - delta * beta * ln(random()) > expiry
```

---

## Cache Invalidation Patterns

**Hardest problem in computer science.**

### Event-Driven Invalidation

```
DB change → Kafka event → Cache Invalidation Consumer → cache.delete(key)
```

Ensures all cache instances flush on data change.

### Tag-Based Invalidation

```
cache.setWithTag("user:123:profile", val, tag="user:123")
cache.setWithTag("user:123:orders",  val, tag="user:123")

// On user update — invalidate all tagged entries:
cache.invalidateByTag("user:123")
```

---

## Cache Penetration

A key that will never exist in DB gets hammered repeatedly → DB overload.

**Solution**: Cache null values with short TTL (e.g., 30s).  
Or: **Bloom filter** — query bloom filter first; if definitely absent, return null immediately.

```
BloomFilter<String> bloomFilter = ...;
if (!bloomFilter.mightContain(key)) return null;  // skip DB

// Only reach DB if bloom filter says "maybe exists"
```

---

## Multi-Level Caching

```
Request → L1 (in-process Caffeine, ~100ms TTL, 10K entries)
               ↓ miss
           L2 (Redis cluster, ~5min TTL, 100M keys)
               ↓ miss
           L3 (DB)
```

L1 absorbs hot reads without network hop.  
L1 TTL very short (seconds) to avoid stale data divergence.

---

## Redis Sizing Formula

```
estimate keys = requestsPerDay × cacheDuration / totalUsers
memory = keys × (keySize + valueSize + 70 bytes overhead)

Example: 10M DAU, cache user profile for 1 hour
  active keys in cache = 10M × (3600/86400) ≈ 420K keys
  memory = 420K × (50 + 500 + 70) bytes ≈ 260 MB
```

---

## Interview Tips

- Always state which strategy you're using and WHY (tradeoffs).
- Cache stampede is a common follow-up for any caching question.
- Mention cache penetration + bloom filter if the key space is unbounded.
- For writes: invalidate, don't update — avoids race conditions.
