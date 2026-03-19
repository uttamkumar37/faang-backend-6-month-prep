# Project 9: Live Leaderboard & Gaming Backend

## Overview

A real-time leaderboard and gaming session management backend capable of handling millions of concurrent players — tracking scores, global and friend-level rankings, streaks, achievements, seasonal resets, and live pub/sub score updates. Used in scenarios from mobile gaming to trading competitions to fitness challenges.

**Business Value**: Drives 3× daily engagement through competitive ranking features and real-time notification of rank changes, directly tied to DAU and retention metrics.

## Why this is a strong portfolio project

Leaderboards seem simple until you must handle 10M players, frequent score updates, pagination across millions of rows, and real-time rank-change notifications — all at once. This project demonstrates Redis advanced data structures (Sorted Sets), WebSocket / SSE for real-time push, time-series seasonal management, atomic rank updates, and database / cache consistency patterns. These are the exact patterns used in gaming, fintech trading platforms, and e-commerce promotions.

---

## Features

- **Global leaderboard**: Top-N rankings across all players, updated in real time.
- **Regional / segment leaderboards**: Separate leaderboards by country, platform, skill tier.
- **Friends leaderboard**: Personalised ranking among a user's social graph peers.
- **Real-time rank push**: Rank-change events pushed to clients via WebSocket (Spring WebSocket).
- **Score history**: Time-series score snapshots for trend charts.
- **Achievements & badges**: Milestone triggers (first win, 10-streak, top-100).
- **Seasonal resets**: Automated periodic leaderboard archival and reset with configurable cadence.
- **Anti-cheat threshold**: Submissions above a configurable per-second score rate are flagged.
- **Replay protection**: Idempotent score submission by session + round ID.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21 |
| WebSocket | Spring WebSocket + STOMP (score push) |
| Leaderboard store | Redis Sorted Sets (ZADD, ZRANK, ZRANGEBYSCORE) |
| Persistence | PostgreSQL (player profiles, achievement history, seasonal archive) |
| Sessions | Redis (active game sessions, TTL-managed) |
| Achievements | Kafka (achievement-events topic, async evaluation) |
| Time-series scores | TimescaleDB or PostgreSQL + partitioning |
| Pub/sub for push | Redis Pub/Sub → Spring WebSocket relay |
| Observability | Micrometer, Prometheus, Grafana |

---

## Functional Requirements

- Accept score submission for a player + game session; update leaderboard atomically.
- Return paginated global and friends leaderboards with correct rank numbers.
- Push real-time rank-change notifications to connected WebSocket clients.
- Evaluate achievement milestones asynchronously without blocking score submission.
- Archive and reset leaderboards on a configurable seasonal cadence.

## Non-Functional Requirements

- 500K score submissions per second during peak game events.
- Global leaderboard read P99 < 10 ms for top-100 queries.
- WebSocket rank-change push delivered within 500 ms of score update.
- Leaderboard consistent after score submission: rank visible within 200 ms.
- Zero duplicate score crediting via idempotent submission by (session_id, round_id).

## Success Metrics

- Score submission to rank-visible latency P99 < 200 ms.
- WebSocket push delivery latency P99 < 500 ms.
- Global leaderboard top-100 read P99 < 10 ms.
- Achievement evaluation processing lag < 2 seconds.
- Anti-cheat flag rate tracked weekly as a quality signal.

---

## API Endpoints

```
POST /api/v1/scores
  Body: { "playerId": "...", "sessionId": "...", "roundId": 5, "delta": 450 }
  Response: { "newScore": 12450, "globalRank": 342, "change": +12 }
  Idempotency: (sessionId + roundId) — duplicate submission ignored

GET  /api/v1/leaderboard/global?limit=100&cursor=<rank_offset>
  Response: { "entries": [{ "rank": 1, "playerId": "...", "score": 98420 }, ...], "total": 1000000 }

GET  /api/v1/leaderboard/friends?playerId=<id>&limit=50
  Response: { "myRank": 3, "entries": [...] }

GET  /api/v1/leaderboard/segment/{segmentId}?limit=100

GET  /api/v1/players/{playerId}/rank         (current global rank + score)
GET  /api/v1/players/{playerId}/history      (time-series score snapshots)
GET  /api/v1/players/{playerId}/achievements

WEBSOCKET  ws://host/ws/leaderboard
  SUBSCRIBE /topic/rank-updates/{playerId}
  → Server pushes: { "newRank": 341, "previousRank": 353, "score": 12450 }
```

---

## Core Leaderboard Operations

```java
// Score submission (atomic):
ZADD leaderboard:global <new_score> <player_id>
// Redis Sorted Set: O(log N) insert/update

// Global rank (0-indexed, ZREVRANK for descending):
ZREVRANK leaderboard:global <player_id>
// Returns rank position counting from 0 (0 = top player)

// Top 100 players:
ZREVRANGEBYSCORE leaderboard:global +inf -inf WITHSCORES LIMIT 0 100
// O(log N + K)

// Page around a player's rank:
rank = ZREVRANK leaderboard:global <player_id>
ZREVRANGE leaderboard:global (rank-5) (rank+5) WITHSCORES
// Returns ±5 neighbours around the player's current rank

// Friends leaderboard:
friend_ids = graph.getFriends(player_id)  // from graph DB
scores = ZMSCORE leaderboard:global friend_ids[]  // batch lookup
sort friends by score desc → virtual rank
```

---

## Real-Time Push Design

```
Score submitted → POST /api/v1/scores
  │
  ▼
Score Service:
  old_rank = ZREVRANK leaderboard:global player_id
  ZADD leaderboard:global new_score player_id
  new_rank = ZREVRANK leaderboard:global player_id
  
  IF new_rank != old_rank:
    Publish to Redis Pub/Sub channel: rank-updates:{player_id}
    Message: { newRank, previousRank, score, timestamp }

WebSocket Relay (Spring WebSocket + STOMP):
  Subscribes to all Redis rank-update channels
  On message received:
    messagingTemplate.convertAndSend("/topic/rank-updates/" + playerId, event)

Client receives push in < 500ms with no polling
```

---

## Seasonal Reset Flow

```
Cron job: runs at season end (configurable)

1. Archive current leaderboard:
   ZREVRANGEBYSCORE leaderboard:global +inf -inf WITHSCORES → all entries
   INSERT INTO leaderboard_archive (season_id, player_id, final_score, final_rank, achieved_at)

2. Assign season rewards:
   Produce to Kafka: season-rewards-events (top-K players get badges/rewards)

3. Reset leaderboard:
   RENAME leaderboard:global leaderboard:archive:{season_id}   ← preserve for 90 days
   DEL leaderboard:global                                       ← fresh start

4. Notify all connected WebSocket clients: { "type": "SEASON_RESET", "newSeasonId": "..." }

5. Send push notification via notification service to all players
```

---

## Key Engineering Challenges

### 1. Friends Leaderboard at Scale
ZMSCORE fetches scores for all friend_ids in one call (O(N) where N = friend count). For users with 5000 friends, this is a 5K-member batch read from one Redis call — fast (< 5ms) and no sorted set per user needed. Rank is computed in-memory by the API after the batch fetch.

### 2. Anti-Cheat Score Rate Limiting
Use a Redis sliding-window rate limiter per player: ZADD score_rate:{player_id} <now> <uuid> / ZCOUNT in last second. If count exceeds threshold, reject and flag the submission in a `cheat_suspects` set for analyst review.

### 3. Preventing Rank-Change Notification Storms
On high-traffic events (top-10 players compete), rank changes happen 1000× per second. Debounce per-player rank push: buffer rank changes in Redis and flush at most once per 200ms per player using a scheduled task.

---

## Resume Impact Bullets

- Built a real-time leaderboard handling 500K score submissions/sec with P99 rank-visible latency under 200ms using Redis Sorted Sets with O(log N) atomic ZADD/ZREVRANK operations.
- Implemented a WebSocket rank-change push system using Redis Pub/Sub as the relay bus, delivering rank updates to connected clients within 500ms of score submission.
- Designed a friends leaderboard using O(N) ZMSCORE batch lookups with in-memory rank computation, eliminating the need for per-user sorted sets and reducing Redis memory by 80% for social graph leaderboards.
