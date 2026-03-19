# Project 9: Architecture — Live Leaderboard & Gaming Backend

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Client Layer                                  │
│   Mobile Game       Web Browser           Admin Dashboard            │
│   (REST + WS)       (REST + WS)           (REST)                    │
└──────────────┬──────────────────┬──────────────────────────────────┘
               │                  │ WebSocket
               ▼                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│              API Gateway (Spring Cloud Gateway)                       │
│   JWT auth, rate limit (100 req/s per player), WebSocket proxy        │
└──────┬──────────────────────────┬────────────────────────────────────┘
       │ REST                     │ WebSocket / STOMP
       ▼                          ▼
┌─────────────────┐    ┌──────────────────────────────────────────────┐
│  Score API      │    │  WebSocket Service (Spring WebSocket + STOMP) │
│  Service        │    │                                              │
│  (stateless,    │    │  STOMP broker relay → Redis Pub/Sub          │
│  20 pods)       │    │  Manages WS connections (sticky session       │
└────────┬────────┘    │  via AWS ALB target groups)                  │
         │             └──────────────┬───────────────────────────────┘
         │                            │
         │   score update + rank      │ rank-update events (sub to Redis)
         │   change notification      │
         ▼                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      Redis Cluster                                    │
│                                                                      │
│  Sorted Sets (leaderboards):                                         │
│  ├── leaderboard:global          (all players, score-ranked)         │
│  ├── leaderboard:region:{code}   (per region)                        │
│  ├── leaderboard:tier:{name}     (bronze/silver/gold)                │
│  └── leaderboard:archive:{seasonId}  (read-only, past seasons)       │
│                                                                      │
│  Pub/Sub channels:                                                   │
│  └── rank-updates:{playerId}                                         │
│                                                                      │
│  Rate limiting:                                                      │
│  └── score_rate:{playerId}       (anti-cheat sliding window)         │
│                                                                      │
│  Idempotency:                                                        │
│  └── score_submission:{sessionId}:{roundId}  (SET NX, TTL 24h)      │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ async persistence
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   PostgreSQL + TimescaleDB                            │
│                                                                      │
│  player_profiles          (id, username, region, tier, total_score)  │
│  score_history            (player_id, score_delta, created_at)       │
│  achievements             (player_id, type, awarded_at)              │
│  leaderboard_archive      (season_id, player_id, final_rank, score)  │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   Kafka Cluster                                       │
│                                                                      │
│  ├── score-events          (async DB write, achievement eval)         │
│  ├── achievement-events    (badge grant, push notification trigger)   │
│  └── season-reward-events  (end-of-season prize distribution)        │
└──────────────────────────────────────────────────────────────────────┘
                      │           │
                      ▼           ▼
            Achievement       Notification
            Service           Service
            (async badge       (push, email)
             evaluation)
```

---

## Score Submission Flow

```
POST /api/v1/scores
Body: { playerId, sessionId, roundId, delta: 450 }

1. Idempotency check:
   SET score_submission:{sessionId}:{roundId} "1" NX EX 86400
   IF result == nil → duplicate → return cached response

2. Anti-cheat rate check:
   ZADD  score_rate:{playerId} <now_ms> <uuid>
   EXPIRE score_rate:{playerId} 2
   ZCOUNT score_rate:{playerId} (now_ms - 1000) +inf
   IF count > MAX_SCORE_RATE_PER_SECOND → flag and reject

3. Score update (atomic Lua script):
   ZINCRBY leaderboard:global <delta> <playerId>
   → returns new total score

4. Rank before/after:
   new_rank = ZREVRANK leaderboard:global <playerId>
   IF old_rank != new_rank:
     PUBLISH rank-updates:{playerId} { newRank, oldRank, score }

5. Async (Kafka, non-blocking):
   Produce to score-events: { playerId, delta, newTotalScore, timestamp }

6. Return: { newScore, globalRank, change }
```

---

## WebSocket Session Management

```
Player connects: ws://host/ws/leaderboard

1. STOMP CONNECT with JWT token
   → Server validates JWT, extracts playerId

2. Player subscribes: SUBSCRIBE /topic/rank-updates/{playerId}
   → Server registers: Redis SUBSCRIBE rank-updates:{playerId}

3. Server-side WebSocket relay (Spring WebSocket service):
   Redis Pub/Sub listener → messagingTemplate.convertAndSend(destination, payload)

4. Sticky sessions:
   WebSocket connections are long-lived; require sticky routing.
   AWS ALB: cookie-based stickiness to pod.
   On pod crash → client auto-reconnects (exponential backoff in SDK)

Scale: 10M concurrent WebSocket connections
  → 1000 pods × 10K connections/pod
  → Each pod maintains ~10K Redis SUBSCRIBE channels
```

---

## Friends Leaderboard Query

```
GET /api/v1/leaderboard/friends?playerId=player-1&limit=50

1. Fetch friend IDs: graph.getFriends(player-1) → [id1, id2, ..., id5000]
   (Cached in Redis: friends:{player_id}, TTL 5min)

2. Batch score lookup (single Redis call):
   ZMSCORE leaderboard:global [id1, id2, ..., id5000]
   → Returns [score1, score2, ..., score5000]
   (O(N) where N = friend count)

3. Pair friend_id → score, sort descending in-memory (Java):
   Comparator.comparingLong(Entry::score).reversed()

4. Assign virtual ranks 1..N within friend group

5. Find requesting player's position in sorted list

6. Return top-50 + player's own rank
```

---

## Seasonal Reset Sequence

```
Season end detected (cron or admin trigger):

T+0s:   Acquire distributed lock: SET season_reset_lock NX EX 300

T+0s:   BEGIN TRANSACTION (PostgreSQL):
          INSERT INTO leaderboard_archive
            SELECT '{seasonId}', player_id, score, RANK() OVER(ORDER BY score DESC)
            FROM (
              SELECT unnest(members) as player_id, unnest(scores) as score
              FROM redis_leaderboard_dump      ← from ZRANGEBYSCORE dump
            ) t
          COMMIT

T+30s:  Produce season-reward-events to Kafka (top 100 players)

T+60s:  Redis atomic season swap:
          RENAME leaderboard:global leaderboard:archive:{seasonId}
          DEL leaderboard:global   ← new empty sorted set created on first ZADD

T+61s:  Broadcast to all WebSocket clients:
          PUBLISH ws:broadcast { type: SEASON_RESET, newSeasonId, startsAt }

T+90s:  Release season_reset_lock
        Log season reset metrics (Grafana annotation marker)
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Redis sorted set node down | Leaderboard reads fail | Redis Cluster 3-shard × 2-replica; replicated sorted sets survive single-node failure |
| Score deducted / overflowed | Incorrect total | ZINCRBY is atomic; no partial deduct possible. Score capped at MAX_LONG via Lua script |
| WebSocket pod crash | Connected users lose push | Client SDK auto-reconnects; STOMP re-SUBSCRIBEs; rank state served fresh from API on reconnect |
| Duplicate score submission | Player gains extra score | SET NX idempotency key per (sessionId, roundId); duplicate returns cached response |
| Season reset failure mid-way | Partial archive, corrupted leaderboard | Distributed lock prevents concurrent resets; RENAME is atomic; archive written before reset |
| Anti-cheat false positive | Legitimate player blocked | Flagged submissions queued for manual review; not permanently blocked without analyst confirmation |

---

## Observability

```yaml
# Critical metrics
leaderboard_score_submissions_total{status=accepted|rejected|duplicate}
leaderboard_rank_update_latency_ms          # time from submit to rank-visible P99
leaderboard_ws_push_latency_ms             # time from rank-change to client delivery
leaderboard_ws_connections_active          # concurrent WebSocket connections
leaderboard_redis_ops_per_second           # ZADD + ZREVRANK rate
leaderboard_anticheat_flags_total          # suspicious submission rate
leaderboard_season_archive_size            # entries per season
```

---

## Trade-offs

| Decision | Alternative | Why |
|---|---|---|
| Redis Sorted Set for global leaderboard | PostgreSQL with indexed score column | ZADD/ZREVRANK O(log N) vs SQL rank window function O(N log N) scan; Redis is 1000× faster for real-time |
| ZMSCORE batch for friends leaderboard | Per-user friend leaderboard sorted set | No per-user index to maintain on follow/unfollow; slight latency increase offset by simpler consistency model |
| Redis Pub/Sub for WS relay | Direct WS pod-to-pod messaging | Pub/Sub decouples score path from WS delivery; score API pods don't need to know which WS pod holds the connection |
| Sticky WebSocket sessions | Session state in Redis | Avoids double-hop latency; Redis used only for state storage not message routing |
| Lua script for atomic ZINCRBY + rank read | Two separate commands | Guarantees old_rank is read before any concurrent update changes it; prevents race in rank-change detection |
