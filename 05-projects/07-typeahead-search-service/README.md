# Project 7: Typeahead / Search Autocomplete Service

## Overview

A low-latency, high-throughput typeahead and search autocomplete service serving prefix-based suggestions as users type. Used in search bars, address inputs, product search, and user lookup. Designed for sub-20ms P99 latency at 100K queries per second.

**Business Value**: Increases search engagement by 35% and reduces zero-result searches by delivering relevant suggestions before the full query is submitted.

## Why this is a strong portfolio project

This project is one of the top 5 FAANG system design questions. It forces you to confront trie data structures, distributed prefix indexing, cache eviction, trending-query ranking, multi-language / Unicode support, and the trade-offs between in-memory and persistent indexes. Candidates who only know the theory — and cannot discuss latency budgets, index refresh rates, and ranking signals — fail the system design round. This project closes that gap.

---

## Features

- **Prefix search with ranking**: Returns top-K completions by relevance score (query frequency + recency).
- **Real-time trending**: Trending queries injected into suggestions within 30 seconds of spike.
- **Multi-type support**: Products, users, addresses, hashtags — each with a separate scoring model.
- **Personalization tier**: User's recent search history blended with global suggestions.
- **Typo tolerance**: Edit-distance-1 fuzzy matching for 3+ character prefixes.
- **Blacklist / content filtering**: Blocked terms removed from all responses.
- **Analytics**: Impression vs selection funnel tracked per suggestion position.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21, virtual threads |
| In-memory index | Redis (Sorted Sets for prefix ranges) |
| Fuzzy matching | BK-tree in-memory, or Elasticsearch fuzzy |
| Offline index build | Apache Spark (processes query logs) |
| Index store | Apache Cassandra (compressed trie snapshots) |
| Trending signals | Kafka Streams (count-min sketch, windowed aggregation) |
| Personalization | Redis (user_recent_queries:{user_id} sorted set, TTL 7d) |
| Search fallback | Elasticsearch (full-text fallback beyond prefix) |
| Observability | Micrometer, Prometheus, Grafana |

---

## Functional Requirements

- Return top-10 autocomplete suggestions for a given prefix within 20 ms P99.
- Refresh global ranking signals within 5 minutes of query log ingestion.
- Surface trending queries within 30 seconds of a spike.
- Serve personalized suggestions when a logged-in user token is present.
- Support multi-type entity search with independent ranking per type.

## Non-Functional Requirements

- 100K queries per second at P99 < 20 ms.
- Index covering 500M unique global query strings.
- Horizontal scale: stateless API tier behind load balancer.
- Zero-downtime index refresh (blue/green index swap).
- Blacklist enforcement propagated within 1 second of admin update.

## Success Metrics

- Autocomplete selection rate (CTR on suggestions) > 40%.
- Suggestion latency P99 < 20 ms from Redis path.
- Trending injection lag < 30 seconds.
- Zero-result top-1 suggestion rate < 2%.
- User satisfaction: correlation between suggestion rank and final query submission.

---

## API Endpoints

```
GET  /api/v1/suggest?q=java+b&limit=10&type=ALL&userId=<optional>
  Response: {
    "suggestions": [
      { "text": "java backend", "score": 98420, "type": "QUERY" },
      { "text": "java books", "score": 87130, "type": "QUERY" },
      ...
    ],
    "latencyMs": 8
  }

POST /api/v1/analytics/impression
  Body: { "prefix": "java b", "suggestions": [...], "sessionId": "..." }

POST /api/v1/analytics/selection
  Body: { "prefix": "java b", "selectedText": "java backend", "rank": 0 }

POST /api/v1/admin/blacklist
  Body: { "term": "...", "reason": "..." }

POST /api/v1/admin/reindex    (trigger manual index refresh)
```

---

## Ranking Formula

```
score(query, prefix) =
    W1 × global_frequency_score     (from query logs, last 30 days)
  + W2 × recency_boost              (exponential decay by last seen timestamp)
  + W3 × trending_boost             (from Kafka Streams count-min sketch)
  + W4 × personalization_score      (user's own query history match)
  + W5 × type_quality_score         (verified entity, curated content)

W1=0.5, W2=0.2, W3=0.2, W4=0.1, W5=0.0 (default, tunable)

Stored in Redis Sorted Set: ZADD suggest:{prefix} <score> <suggestion_text>
```

---

## Key Engineering Challenges

### 1. Storing Prefix Index in Redis
Instead of a full trie node per character (memory-expensive across nodes), store one Sorted Set per prefix:
- Key: `suggest:{prefix}` — e.g., `suggest:jav`, `suggest:java`, `suggest:java_b`
- Score: composite ranking score
- For a query of length L, one Redis ZREVRANGE call retrieves top-K: O(log n + K)
- Space: only popular prefixes stored (min frequency threshold); sparse branches omitted

### 2. Index Size Control
500M unique queries × avg 5 prefix entries each = 2.5B Redis keys. Too large for in-memory.
Solution: store only prefixes with total descendant query count above threshold (e.g., ≥ 100 global uses). Long-tail handled by Elasticsearch fallback.

### 3. Zero-Downtime Index Refresh
Build new index under a versioned namespace (`suggest_v2:{prefix}`). Once build is complete, atomically rename all keys using Redis RENAME batch or swap the version pointer key. API reads from the active version pointer.

### 4. Trending Spike Injection
Kafka Streams 30-second tumbling window counts queries. Count-min sketch provides space-efficient approximate top-K. Results pushed to Redis `suggest_trending:{prefix}` keys that override the global score temporarily (TTL = 5 min).

---

## Resume Impact Bullets

- Built a typeahead service serving 100K QPS at P99 < 20 ms using Redis Sorted Sets for prefix indexes with composite frequency-recency ranking scores.
- Solved the trending injection problem with a Kafka Streams count-min sketch pipeline that surfaces spiking queries within 30 seconds without a full index rebuild.
- Designed a zero-downtime index refresh using blue/green Redis namespace swapping, enabling hourly ranking updates with no latency impact.
