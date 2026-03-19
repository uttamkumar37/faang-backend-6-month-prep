# Project 4: Distributed Rate Limiter Service

## Overview

A standalone, distributed rate-limiting service exposed as a REST API and embeddable Spring Boot starter. Supports multiple algorithms (token bucket, sliding window, fixed window), multiple backends (Redis single + Redis Cluster), and per-tenant/per-route configuration.

**Business Value**: Protects platform APIs from abuse, enforces fair-use quotas, prevents cascade failures from traffic spikes.

## Why this is a strong portfolio project

This is a platform engineering project with clear backend depth: algorithm trade-offs, distributed consistency, configuration propagation, degraded-mode behavior, and service-as-a-product thinking.

---

## Features

- **Multiple algorithms**: Token bucket, sliding window log, sliding window counter, fixed window.
- **Multiple scopes**: Global, per-user, per-tenant, per-IP, per-route, composable.
- **Redis-backed**: Atomic Lua scripts guarantee consistency under concurrency.
- **REST API**: Any service can check quotas (`POST /api/v1/rate-limit/check`).
- **Spring Boot Starter**: Embed as library — `@RateLimit` annotation on controllers.
- **Admin API**: Manage quota configs, view current counters, unblock IPs/users.
- **Quota analytics**: Track top consumers, quota exhaustion events per time window.
- **Circuit breaker fallback**: If Redis is unavailable, falls back to in-process token bucket.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21 |
| Primary backend | Redis 7 Cluster (3 primaries, 3 replicas) |
| Fallback backend | Caffeine in-process (JVM-local) |
| Configuration | Database (PostgreSQL) + Redis pub/sub for hot reload |
| Auth | Spring Security (API key auth for service-to-service) |
| Observability | Micrometer, Prometheus, Grafana dashboard |

## Functional requirements

- Evaluate rate-limit decisions for multiple scopes and algorithms.
- Expose decisions over REST and library integration paths.
- Support admin configuration changes without restart.
- Return enough metadata for consuming services to enforce or forward limits.
- Continue protecting systems in a degraded mode during Redis incidents.

## Non-functional requirements

- P99 decision latency under 50 ms under normal Redis operation.
- No race conditions in distributed counter updates.
- Hot config propagation across pods within 100 ms.
- High availability with at least three replicas.
- Conservative fallback behavior when the distributed backend is unhealthy.

## Success metrics

- P99 decision latency below 50 ms.
- Redis circuit-breaker open time tracked and kept minimal.
- Config reload propagation under 100 ms across pods.
- Denied-request visibility by tenant, route, and scope.

---

## Algorithm Comparison

| Algorithm | Best For | Overhead | Burst Handling |
|---|---|---|---|
| Token bucket | General-purpose, allows short bursts | O(1) | Yes |
| Fixed window | Simple counters, billing quotas | O(1) | No (edge spike problem) |
| Sliding window counter | Better edge handling with low overhead | O(1) | Approximate |
| Sliding window log | Precise limiting (critical APIs) | O(N per window) | No |

---

## REST API

```
POST /api/v1/rate-limit/check
  Body: { "key": "user:123:post-api", "cost": 1 }
  Response 200: { "allowed": true,  "remaining": 47, "resetAt": "2025-01-01T12:00:00Z" }
  Response 200: { "allowed": false, "remaining": 0,  "retryAfter": 5 }

GET  /api/v1/rate-limit/config
POST /api/v1/admin/config        (create/update quota rule)
DELETE /api/v1/admin/unblock/{key}

GET  /api/v1/admin/analytics/top-consumers?window=1h
GET  /api/v1/admin/analytics/exhaustion-events?from=...&to=...
```

---

## Response Headers (for consuming APIs to forward to clients)

```
X-RateLimit-Limit:     100
X-RateLimit-Remaining: 47
X-RateLimit-Reset:     1735733400    (epoch seconds)
Retry-After:           5             (only on 429)
```

---

## Quota Configuration Model

```sql
CREATE TABLE quota_configs (
    id              UUID PRIMARY KEY,
    scope           VARCHAR(50),    -- user, tenant, ip, route, global
    scope_value     VARCHAR(200),   -- 'user:*' | 'tenant:acme' | 'ip:*' | specific value
    algorithm       VARCHAR(30),    -- token_bucket | sliding_window_counter | fixed_window
    limit_count     BIGINT,
    window_seconds  INT,
    burst_allowance INT,            -- extra tokens above limit for token bucket
    enabled         BOOLEAN,
    priority        INT,            -- higher = evaluated first for key matching
    created_at      TIMESTAMP
);
-- Example rows:
-- ('global', '*', 'sliding_window_counter', 1000000, 60, 0, true, 0)
-- ('tenant', 'acme', 'token_bucket', 10000, 60, 2000, true, 10)
-- ('user',   '*',   'sliding_window_counter', 100, 60, 0, true, 5)
-- ('route',  'POST:/api/v1/orders', 'token_bucket', 50, 60, 10, true, 20)
```

---

## Redis Lua Scripts (atomic operations)

```lua
-- Token bucket check-and-consume (sliding_window_counter variant)
local key = KEYS[1]
local now = tonumber(ARGV[1])          -- current epoch ms
local window = tonumber(ARGV[2])       -- window in ms
local limit = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])

local window_start = now - window

-- Remove expired entries
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Count requests in window
local count = redis.call('ZCARD', key)
if (count + cost) > limit then
    return {0, limit - count, window_start + window}  -- denied
end

-- Add this request
redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
redis.call('PEXPIRE', key, window)
return {1, limit - count - cost, window_start + window}  -- allowed
```

---

## Running Locally

```bash
docker compose up -d redis postgres

./mvnw spring-boot:run

# Check rate limit
curl -X POST localhost:8080/api/v1/rate-limit/check \
  -H "X-API-Key: dev-service-key" \
  -d '{"key":"user:42:post-orders","cost":1}'

# Create a quota config
curl -X POST localhost:8080/api/v1/admin/config \
  -H "X-API-Key: admin-key" \
  -d '{"scope":"user","scopeValue":"*","algorithm":"sliding_window_counter",
       "limitCount":100,"windowSeconds":60}'
```

---

## Spring Boot Starter Usage

```java
// In consuming service pom.xml (library mode):
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>rate-limiter-starter</artifactId>
    <version>1.0.0</version>
</dependency>

// Annotation on controller:
@RateLimit(key = "#userId", limit = 100, window = 60, unit = TimeUnit.SECONDS)
@PostMapping("/orders")
public OrderResponse placeOrder(@RequestBody OrderRequest req, @AuthPrincipal String userId) { ... }
```

---

## Interview Talking Points

- Redis Lua scripts guarantee atomicity — the read-modify-write cycle for counter update is a single network round-trip with no race conditions.
- Sliding window log is accurate but O(N) memory per key; sliding window counter approximates it in O(1) by splitting into sub-buckets.
- Circuit breaker to in-process fallback ensures the rate limiter itself never becomes a SPOF for the platform it protects.
- Config hot-reload: config changes published via Redis pub/sub, all pods receive and update their local lookup table within 100ms — no pod restarts.

## Failure modes and mitigations

| Failure mode | Mitigation |
|---|---|
| Redis cluster unavailable | Circuit breaker opens and local fallback limiter takes over |
| Config mismatch across pods | Pub/sub reload plus periodic reconciliation from PostgreSQL |
| Hot key overload on one tenant | Hash-tagging, capacity planning, and per-scope throttling |
| Algorithm misuse on critical route | Keep route-specific overrides with clear priority rules |
| Rate limiter becomes bottleneck | Embedded starter option avoids network hop for some consumers |

## Resume-ready bullets

- Built a distributed rate-limiter service supporting multiple algorithms, scopes, and Redis-backed atomic enforcement for platform APIs.
- Added hot-reloadable quota configuration, per-scope analytics, and degraded-mode fallback so the limiter could protect upstream services even during dependency failures.
- Framed the service as a reusable platform capability through both REST and Spring Boot starter integration paths.
