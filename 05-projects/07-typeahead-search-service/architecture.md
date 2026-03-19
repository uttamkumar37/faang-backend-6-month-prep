# Project 7: Architecture — Typeahead Search Service

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Client Layer                                  │
│   Web Browser (keystroke listener)   Mobile App   Internal Services  │
└──────────────────────────┬───────────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│             CDN / Edge Cache (Cloudflare / CloudFront)                │
│   Cache popular prefixes (top 1000) with TTL=30s at edge             │
│   Cache-Control: public, max-age=30                                   │
└──────────────────────────┬───────────────────────────────────────────┘
                           │  (cache miss → origin)
                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│              Suggest API Service (Spring Boot, stateless)            │
│   Horizontal scale: 20 pods, HPA on CPU/latency                      │
│   Virtual threads: handles 100K QPS with small thread pool           │
└──────┬──────────────────────────────────┬────────────────────────────┘
       │                                  │
       ▼                                  ▼
┌─────────────────┐             ┌─────────────────────────┐
│  Redis Cluster  │             │  Elasticsearch Cluster   │
│  (primary path) │             │  (fallback: fuzzy match, │
│                 │             │   long-tail, full-text)  │
│  suggest:{pfx}  │             └─────────────────────────┘
│  sorted sets    │
│  trending:{pfx} │
│  blacklist set  │
│  user_recent:   │
│    {user_id}    │
└─────────────────┘

           ┌──────────────────────────────────────────┐
           │        Offline Ranking Pipeline           │
           │                                          │
           │  Query Logs (Kafka topic)                │
           │       │                                  │
           │       ▼                                  │
           │  Spark Streaming / Batch                 │
           │  ├── Aggregate query frequencies         │
           │  ├── Compute recency decay scores        │
           │  └── Merge with entity quality scores    │
           │       │                                  │
           │       ▼                                  │
           │  Index Builder Service                   │
           │  ├── Build suggest_v{N}:{prefix} keys    │
           │  ├── Write to Redis staging namespace    │
           │  └── Atomic namespace swap on completion │
           └──────────────────────────────────────────┘

           ┌──────────────────────────────────────────┐
           │       Real-Time Trending Pipeline         │
           │                                          │
           │  Query events → Kafka fan-out-events     │
           │       │                                  │
           │       ▼                                  │
           │  Kafka Streams app                       │
           │  ├── 30s tumbling window                 │
           │  ├── Count-min sketch (top-K approx.)    │
           │  └── Emit: trending top-100 prefixes     │
           │       │                                  │
           │       ▼                                  │
           │  Redis: ZADD suggest_trending:{prefix}   │
           │         score_override for 5 min TTL     │
           └──────────────────────────────────────────┘
```

---

## Request Flow: Suggest Keystroke

```
User types "java b" → client debounce 150ms → GET /suggest?q=java+b

1. Suggest API: check blacklist
   SISMEMBER blacklist "java b" → false → continue

2. Normalize prefix: lowercase, trim, remove special chars
   "java b" → "java_b"

3. Redis ZREVRANGEBYSCORE suggest:java_b +inf -inf WITHSCORES LIMIT 0 15
   Returns: [("java backend", 98420), ("java books", 87130), ...]
   Latency: ~1–3 ms

4. Check trending overlay:
   ZREVRANGEBYSCORE suggest_trending:java_b +inf -inf WITHSCORES LIMIT 0 5
   Merge trending hits (override scores for matched items)

5. If user token present:
   ZREVRANGE user_recent:{user_id} 0 4  → recent queries
   Filter by prefix "java b" → personal boosts
   Merge into result (personal results ranked highest)

6. If Redis returns < 5 results (sparse prefix):
   Fallback: Elasticsearch fuzzy prefix query
   GET /suggest_es?q=java+b&fuzziness=AUTO&size=10

7. Apply blacklist post-filter (remove any blacklisted terms)

8. Return top-10 deduplicated results

Total latency target: < 20ms P99
```

---

## Index Build Pipeline

```
Every 1 hour (cron job triggers):

1. Spark batch reads query_logs Kafka topic (last 24h)
   Aggregates: (query_text, count, last_seen_timestamp)

2. For each (query, freq) above threshold (freq >= 100):
   score = compute_ranking_score(freq, last_seen, entity_quality)
   For each prefix of query (len 1..len(query)):
     Emit (prefix, query_text, score)

3. Index Builder writes to Redis staging:
   Version: v{epoch_timestamp}
   ZADD suggest_v{N}:{prefix} <score> <query_text>

4. Build validation:
   Random sample 1000 prefixes → spot-check result quality
   IF pass → atomic swap:
     RENAME suggest_v{N}:{pfx} → suggest:{pfx}  (per prefix)
     Or: SCRIPT LOAD + EVAL for atomic batch rename

5. Cleanup old version keys (previous suggest_v{N-1}:*)
```

---

## Trending Injection (Kafka Streams)

```
Kafka topic: search-query-events
  Key: normalized_query
  Value: { query, userId, timestamp, type }

Kafka Streams topology:
  stream.groupByKey()
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(30)))
    .count()
    .toStream()
    .filter((key, count) -> count > TRENDING_THRESHOLD)
    .foreach((windowedKey, count) -> {
        String prefix = windowedKey.key();
        for (int len = 2; len <= prefix.length(); len++) {
            String pfx = prefix.substring(0, len);
            redis.zadd("suggest_trending:" + pfx, computeTrendingScore(count), prefix);
            redis.expire("suggest_trending:" + pfx, 300);  // 5 min TTL
        }
    });
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Redis cluster node down | Increased latency, partial results | Redis Cluster replication; fallback to Elasticsearch |
| Index build job fails mid-way | Stale index | Versioned namespaces — old index remains active until new one is fully validated |
| Trending pipeline lag | Trending queries not surfaced | Monitor consumer lag; operator alert if lag > 60s |
| Blacklist update delay | Blocked term appears briefly | Blacklist applied post-fetch in API (not just in index); propagates in < 1s via Redis pub/sub |
| Elasticsearch down | Long-tail / fuzzy fails | Return best-effort results from Redis; degrade gracefully with shorter list |

---

## Data Sizing

```
Prefix index:
  Popular prefixes (freq >= 100): ~50M unique prefixes
  Each sorted set: avg 20 suggestions × 50 bytes = 1 KB
  Total: 50M × 1 KB = 50 GB Redis — fits in 5 nodes × 12 GB each

Query throughput:
  100K QPS × 2 Redis calls (suggest + trending) = 200K Redis ops/sec
  Redis cluster at 200K ops/sec: ~4 nodes at 50K ops/sec each (well within limits)
```

---

## Trade-offs

| Decision | Alternative | Why |
|---|---|---|
| Redis Sorted Set per prefix | Single trie node per character | Simpler to shard, no cross-node trie traversal, O(log n) retrieval |
| Store only popular prefixes | Store all prefixes | Limits Redis memory; long-tail served by Elasticsearch |
| Count-min sketch for trending | Exact counter per query | Exact counters for 500M unique queries require enormous memory; CMS gives good approximation in KB |
| Edge CDN for top-1000 prefixes | Pure origin serving | Top prefixes ("a", "th", "go") are requested millions of times/sec; CDN absorbs 60% of load |
| Versioned namespace swap | Incremental key updates | Avoids partial-index reads during refresh; atomic swap ensures consistent state |
