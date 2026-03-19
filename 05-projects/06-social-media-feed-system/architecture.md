# Project 6: Architecture — Social Media Feed System

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Client Layer                                 │
│      Mobile App         Web SPA           Third-party API consumer   │
└──────────────┬────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│               API Gateway (Spring Cloud Gateway)                      │
│   JWT auth, rate limiting (100 req/sec per user), CDN for media      │
└──────────────┬───────────────────────┬────────────────────────────────┘
               │                       │
       ┌───────┴────────┐     ┌────────┴────────┐
       ▼                ▼     ▼                 ▼
  Post Service     Feed Service       Social Graph Service
  (write posts,    (read timeline,    (follow/unfollow/block,
   fan-out         cursor pagination,  follower/following lists,
   trigger)        merge celebrities)  celebrity classification)
       │                │                       │
       ▼                │                       ▼
 Kafka Cluster          │              Neo4j / PostgreSQL
 ├── fan-out-events      │              (social graph store)
 ├── post-events         │
 └── moderation-events   │
       │                 │
       ▼                 │
 Fan-out Workers         │
 (Kafka consumers,       │
  sharded by author_id)  │
       │                 │
       ▼                 ▼
┌──────────────────────────────────────────────────────┐
│                    Cache Layer                        │
│                                                      │
│  Redis Cluster                                       │
│  ├── feed:{user_id}           sorted set by ts_score │
│  ├── celebrity_posts:{id}     latest 100 posts       │
│  ├── post:{post_id}           hydrated post JSON     │
│  ├── user_profile:{user_id}   name, avatar, counts   │
│  └── deleted_posts            bloom filter / set     │
└──────────────────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────┐
│                   Storage Layer                       │
│                                                      │
│  Cassandra (posts)           S3 (media)              │
│  ├── posts by (author, month) partition              │
│  └── post_id lookup table                            │
│                                                      │
│  Elasticsearch                                       │
│  └── full-text: hashtags, user search                │
└──────────────────────────────────────────────────────┘
```

---

## Read Path: Timeline Fetch

```
GET /api/v1/feed?cursor=<cursor>&limit=20

1. Feed Service:
   a. Parse cursor → (last_post_id, last_ts)
   b. ZREVRANGEBYSCORE feed:{user_id} (last_ts) -inf LIMIT 0 20
      → returns list of [post_id, score] from Redis   O(log n + 20)

2. For each post_id returned:
   - Check deleted_posts Redis Set → skip if present
   - GET post:{post_id} from Redis (cache hit ~95%)
   - On cache miss → SELECT from Cassandra, write back to Redis (TTL 1h)

3. Pull celebrity posts:
   - social_graph.getCelebrityFollows(user_id)
   - For each celebrity: ZREVRANGE celebrity_posts:{id} 0 4   (latest 5)
   - Merge with step 2 results, sort by timestamp DESC, take top 20

4. Apply filters:
   - Remove posts from blocked/muted users (block list cached in Redis Set)
   - Remove posts the user already saw (optional: seen_posts bloom filter)

5. Build next cursor from last item in result list

6. Return { posts: [...], nextCursor: ... }
```

---

## Write Path: Post Creation + Fan-out

```
POST /api/v1/posts

1. Post Service:
   - Validate content (length, spam check)
   - INSERT into Cassandra posts table
   - Cache post:{post_id} in Redis
   - Produce to Kafka:
       Topic: fan-out-events
       Key: author_id  (ensures ordered processing per author)
       Value: { postId, authorId, timestamp, visibility }

2. Fan-out Kafka Consumer (N consumer instances, partitioned by author_id):
   - Read author's follower_ids from Neo4j / PostgreSQL
   - IF follower_count > CELEBRITY_THRESHOLD (1M):
       DO NOT fan out
       ZADD celebrity_posts:{author_id} <ts> <post_id>
       EXPIRE celebrity_posts:{author_id} 7 DAYS
   - ELSE:
       Batch followers into chunks of 500
       For each batch (parallel):
         Redis pipeline:
           ZADD feed:{follower_id} <ts> <post_id>
           ZREMRANGEBYRANK feed:{follower_id} 0 -1001  ← keep 1000 max

3. For very active normal users (10K-1M followers):
   - Rate-limit fan-out burst: use Kafka consumer lag monitoring
   - Back-pressure: consumer uses pause()/resume() on the partition
```

---

## Follow / Unfollow Flow

```
POST /social/follow/{target_user_id}

1. Social Graph Service:
   INSERT follow_edge (follower=me, followee=target)

2. IF target is NOT celebrity:
   Backfill: fetch last 20 posts by target
   ZADD feed:{me_id} for each backfilled post

3. IF target IS celebrity:
   ADD target_id to celebrity_follows:{me_id} Redis Set
   No feed backfill needed (pulled at read time)

DELETE /social/follow/{target_user_id}

1. Social Graph Service: DELETE follow_edge
2. No immediate feed purge (stale posts will have no new entries; TTL will age them out)
3. Celebrity: SREM celebrity_follows:{me_id} target_id
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Redis node failure | Feed reads fall back to DB | Redis Cluster with 3-replica shards; fallback to Cassandra scan |
| Fan-out consumer lag | Feed delay > 5s for large accounts | Monitor consumer lag; scale consumers via Kubernetes HPA |
| Cassandra partition hotspot | Slow post reads for trending content | Post content cached in Redis; read repair on cache miss |
| Kafka consumer crash mid fan-out | Partial timeline population | Kafka consumer commit after full batch; re-processing is safe (ZADD is idempotent) |
| Social graph DB down | Follow/unfollow fails | Graph reads cached in Redis (TTL 5 min); writes queue in Kafka |

---

## Data Sizing

```
500M DAU × avg 20 feed reads/day = 10B reads/day
10B / 86400 s ≈ 115K reads/sec peak

10M posts/min = 167K writes/sec
Each write fan-outs to avg 300 followers = 50M Redis ZADD/sec
→ Requires ~100 Redis shards at 500K ops/sec each

Redis feed key size:
  feed:{user_id} = up to 1000 entries × 16 bytes (post_id + score) = 16 KB/user
  500M users = 8 TB Redis total feed storage
  → Tier: cache only for active users (last 30 days); 100M active = 1.6 TB
```

---

## Trade-offs

| Decision | Alternative | Why |
|---|---|---|
| Push (fan-out on write) for normal users | Pull on every read | Sub-100ms reads; write cost is acceptable for ≤1M follower users |
| Pull at read for celebrities | Fan-out write for everyone | Fan-out for 100M followers per post would overwhelm Redis with 100M writes |
| Redis Sorted Set for feed | Cassandra feed table | O(log n) random access, TTL, eviction — all native in Redis |
| Cassandra for posts | PostgreSQL | Write throughput at 167K writes/sec requires horizontal scale without coordinator |
| Cursor over offset | Page number offset | Stable cursor prevents duplicates / gaps when new posts are inserted |
