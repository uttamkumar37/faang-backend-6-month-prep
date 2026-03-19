# Project 6: Social Media News Feed System

## Overview

A scalable, near-real-time news feed system that generates personalized timelines for users following other users — handling both normal users and celebrities (millions of followers). The canonical FAANG system design interview problem implemented as a portfolio backend.

**Business Value**: Powers the home feed for a social platform handling 500M DAU, with P99 timeline fetch under 100 ms.

## Why this is a strong portfolio project

This project forces you to confront the fan-out problem with competing trade-offs (write vs read amplification), the celebrity / hot-user problem, cache invalidation at scale, and eventual consistency with acceptable staleness. It is one of the 5 most common FAANG system design questions and building it proves depth that whiteboard discussion alone cannot.

---

## Features

- **Timeline generation**: Pull, push, and hybrid strategies per user segment.
- **Fan-out on write**: Pre-computed timelines in Redis for users with ≤ 10K followers.
- **Fan-out on read (pull merge)**: Celebrity post fetched at read time to avoid fan-out explosion.
- **Post ranking**: Reverse-chronological (default) and engagement-weighted ranking modes.
- **Pagination**: Cursor-based (post_id + timestamp) for efficient deep pagination.
- **Follow / unfollow**: Real-time graph updates, timeline invalidation.
- **Soft delete & moderation**: Deleted posts removed from all timelines without full recompute.
- **Anti-spam**: Deduplication of posts appearing multiple times via Redis Set.
- **Social graph**: Follower/following counts, mutual follows, block list enforced at feed query time.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21, virtual threads |
| Feed store | Redis Sorted Sets (score = post timestamp, member = post_id) |
| Fan-out | Kafka (fan-out-events topic), async consumers per shard |
| Post store | Cassandra (posts table — write-optimized, time-series partitioned) |
| Social graph | Neo4j (follows, blocks, mutes) OR PostgreSQL (adjacency list) |
| Search | Elasticsearch (hashtag search, user search) |
| Media | S3-compatible object store for images/videos |
| Cache | Redis (hot posts, user profiles, follower counts) |
| Observability | Micrometer, Prometheus, Jaeger |

---

## Functional Requirements

- Fetch the home feed (timeline) of a user: latest N posts from followed accounts.
- Publish a new post and propagate it to followers' timelines within 5 seconds.
- Follow or unfollow a user, with timeline effects applied immediately for normal users.
- Support cursor-based pagination without duplicates or gaps.
- Enforce block and mute lists at feed query time.

## Non-Functional Requirements

- 500M DAU, 50M active users posting up to 10M posts/min at peak.
- Timeline read P99 < 100 ms (cache-served paths).
- Post propagation to followers P90 < 5 seconds.
- Celebrity users (> 1M followers) served using pull-at-read; no fan-out writes.
- Feed freshness: at most 10 second staleness for normal user posts.

## Success Metrics

- Timeline read P99 latency (Redis sorted-set path) < 100 ms.
- Fan-out lag (post created → follower receives) P90 < 5 seconds.
- Cache hit rate on hot user posts > 95%.
- DB fallback rate (cache miss, Cassandra read) < 5% of requests.

---

## API Endpoints

```
POST /api/v1/posts
  Body: { "content": "...", "mediaUrls": [...], "visibility": "PUBLIC" }
  Response: { "postId": "...", "createdAt": "..." }

GET  /api/v1/feed?cursor=<post_id:timestamp>&limit=20
  Response: { "posts": [...], "nextCursor": "..." }

POST /api/v1/social/follow/{userId}
DELETE /api/v1/social/follow/{userId}
GET  /api/v1/social/{userId}/followers?cursor=...
GET  /api/v1/social/{userId}/following?cursor=...

DELETE /api/v1/posts/{postId}    (soft delete — removes from all feed caches)
```

---

## Fan-Out Decision Logic

```
User posts a new post:
  Is author a celebrity? (follower_count > CELEBRITY_THRESHOLD = 1,000,000)
  │
  ├── YES (celebrity):
  │     Store in Cassandra posts table only.
  │     Do NOT fan out to individual timelines.
  │     At read time: pull celebrity posts inline (merge with cached feed).
  │
  └── NO (normal user, ≤ 1M followers):
        Produce to Kafka fan-out-events topic
        Fan-out consumers (parallel, sharded by author_id):
          For each follower_id in author's follower list:
            ZADD feed:{follower_id} <timestamp_score> <post_id>
            ZREMRANGEBYRANK feed:{follower_id} 0 -1001  ← keep only latest 1000

On read (GET /feed):
  1. ZREVRANGEBYSCORE feed:{user_id}  ← from Redis, O(log n)
  2. Fetch celebrity posts from Cassandra (for all celebrities the user follows)
  3. Merge + re-sort by timestamp
  4. Hydrate post_ids → Cassandra posts (batch read, cache post content)
  5. Apply block/mute filtering
```

---

## Key Engineering Challenges

### 1. The Celebrity Problem
A user with 100M followers posting once causes 100M Redis ZADD operations. Solution: do not fan-out for celebrities. Instead, maintain a `celebrity_follows` set per user and query celebrity posts at read time from a hot-post cache (Redis key: `celebrity_posts:{celebrity_id}`).

### 2. Deep Pagination
Offset-based pagination requires scanning all prior results — O(n). Use cursor = `(last_seen_post_id, last_seen_timestamp)`. Redis ZREVRANGEBYSCORE with this score as the upper bound is O(log n + page_size).

### 3. Delete Propagation
On post delete: add `post_id` to a `deleted_posts` Redis Set (TTL 24h). Feed reader filters out any returned post_id present in this set. Background job cleans it from all feed caches asynchronously.

---

## Resume Impact Bullets

- Designed a hybrid push/pull news feed system supporting 500M DAU with P99 read latency under 100 ms using Redis Sorted Sets and lazy celebrity-post merging.
- Solved the celebrity fan-out problem by segmenting users above 1M followers to a read-time pull strategy, reducing fan-out Kafka message volume by 98% for top-1% users.
- Implemented cursor-based pagination on Redis sorted sets eliminating offset scans, keeping deep-page latency constant at O(log n + page_size).
