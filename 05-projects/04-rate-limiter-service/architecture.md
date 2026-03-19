# Project 4: Architecture — Distributed Rate Limiter Service

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Consuming Services                          │
│                                                                  │
│  Order-Service    User-Service    Search-API    External Gateway │
│       │               │               │               │         │
│       └───────────────┴───────────────┴───────────────┘         │
│                               │                                  │
│                  Option A: REST call                             │
│                  POST /api/v1/rate-limit/check                   │
│                               │                                  │
│                  Option B: Embedded Starter                      │
│                  @RateLimit annotation → in-process call         │
└───────────────────────────────┼──────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Rate Limiter Service                            │
│                  (Spring Boot 3, Java 21)                        │
│                                                                  │
│  ┌─────────────────┐   ┌──────────────────┐   ┌─────────────┐  │
│  │ Rate Limit API  │   │  Admin REST API  │   │ Config Mgr  │  │
│  │ /check          │   │  /config, /stats │   │  (pub/sub   │  │
│  │ /status         │   │  /unblock        │   │   reload)   │  │
│  └────────┬────────┘   └──────────────────┘   └──────┬──────┘  │
│           │                                          │          │
│           ▼                                          │          │
│  ┌─────────────────────────────────┐                 │          │
│  │    Algorithm Dispatcher          │◄────────────────┘          │
│  │    (resolve scope → algorithm)  │                            │
│  │    Key: user:123:post-orders    │                            │
│  │    → matches 'user:*' config    │                            │
│  └────────┬────────────────────────┘                            │
│           │                                                      │
│   ┌───────┴─────────────────────┐                               │
│   ▼                             ▼                               │
│  Redis Available            Redis Unavailable (CB OPEN)         │
│  → Lua script atomic        → Caffeine local fallback           │
│    ZADD/ZCARD on cluster      (eventual consistency,            │
│                                no cross-pod coordination)       │
└──────────────────────────────────────────────────────────────────┘
           │
           ▼
┌───────────────────────────────────────────────────────────────────┐
│          Redis 7 Cluster (3 primaries + 3 replicas)               │
│                                                                    │
│  Primary 0 (slots 0-5460)                                         │
│  Primary 1 (slots 5461-10922)                                     │
│  Primary 2 (slots 10923-16383)                                    │
│                                                                    │
│  Hash tags for key collocation:  {user:123}:endpoint:POST-orders  │
│  All keys for same user share a hash slot → single-node Lua exec  │
└────────────────────────────────────────────────────────────────────┘
```

---

## Key Resolution & Scope Matching

```
Incoming check request: key = "user:742:POST:/api/v1/orders"

Scope matching (priority order, higher first):
  1. route scope: "POST:/api/v1/orders"   → limit 50/min (priority 20) ← MATCH
  2. user scope:  "user:*"                → limit 100/min (priority 5)
  3. global scope: "*"                    → limit 1M/min (priority 0)

All matching scopes evaluated. Request allowed only if ALL pass.
Response: min remaining across all matching scopes.

Redis keys:
  rl:{route:POST:/api/v1/orders}:1704067200  (fixed window bucket)
  rl:{user:742}:POST-orders                  (sliding window sorted set)
```

---

## Token Bucket Redis Implementation

```
Key: "rl:tb:{scope}:{key}"
Stored as Redis Hash:
  tokens:    current token count  (float)
  last_refill: epoch ms of last refill

On each request (atomic Lua):
  1. Calculate elapsed = now - last_refill
  2. Refill: new_tokens = min(capacity, tokens + elapsed * rate_per_ms)
  3. If new_tokens >= cost:
       new_tokens -= cost
       HMSET tokens=new_tokens last_refill=now
       return ALLOW, new_tokens
  4. Else:
       return DENY, retry_after=ceil((cost - new_tokens) / rate_per_ms)
```

---

## Config Hot-Reload Flow

```
Admin changes quota config via Admin API
        │
        ▼
1. UPDATE quota_configs in PostgreSQL
2. PUBLISH "rate-limit-config-updated" on Redis channel "rl:config:updates"
        │
        ▼
All Rate Limiter pods subscribed to "rl:config:updates"
  → receive event
  → reload affected scope configs from PostgreSQL
  → update in-memory ConcurrentHashMap<String, QuotaConfig>
  → next request uses new config

Latency: < 100ms propagation across all pods.
Zero pod restarts required.
No missed requests during reload.
```

---

## Circuit Breaker for Redis Failures

```
States: CLOSED → OPEN → HALF-OPEN

Thresholds:
  - CLOSED → OPEN: 5 failures in 10-second window
  - OPEN duration: 30 seconds
  - HALF-OPEN: 1 probe request to Redis

Fallback behavior (OPEN state):
  - Use Caffeine local rate limiter
  - Each pod has its own counter (no cross-pod sync)
  - Effective limit = configured limit / pod count (conservative)
  - Log warning every 30 seconds: "Rate limiter running in degraded mode"

Recovery:
  - HALF-OPEN probe succeeds → CLOSED, flush Caffeine counters
  - HALF-OPEN probe fails → back to OPEN, extend 30s timer
```

---

## Monitoring Dashboard (Grafana)

```
Row 1: Traffic Overview
  - ratelimit_checks_total (rate, by allowed/denied)
  - ratelimit_denied_rate  (% of denied per scope)
  - Top 10 scopes by request volume

Row 2: Latency
  - P50/P99 of check latency (Redis + Lua execution)
  - Redis circuit breaker state (0=closed, 1=open)
  - Fallback activation events

Row 3: Config
  - Active quota config count
  - Config reload events timeline
  - Failed config reloads

Alerts:
  - ratelimit_denied_rate > 20% for 5 min → SEV3 (possible abuse)
  - redis_circuit_breaker_state = OPEN → SEV2 (degraded mode)
  - check_latency_p99 > 50ms → SEV3 (Redis slowdown)
```

---

## Deployment

```yaml
# 3 replicas minimum for HA
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rate-limiter-service
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: rate-limiter
        image: rate-limiter-service:latest
        resources:
          requests: { cpu: 250m, memory: 256Mi }
          limits:   { cpu: 500m, memory: 512Mi }
        env:
        - name: REDIS_CLUSTER_NODES
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: cluster-nodes
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 8080 }
          initialDelaySeconds: 5
          periodSeconds: 5
```

---

## Trade-offs

| Decision | Why it helps | What it costs |
|---|---|---|
| Redis Lua scripts for enforcement | Atomic distributed decisions in one round-trip | Operational dependency on Redis health |
| REST plus starter modes | Broader adoption across services | Two integration paths to maintain |
| Local fallback limiter | Prevents the rate limiter from becoming a hard SPOF | Loses cross-pod global coordination while degraded |
| Config in PostgreSQL with Redis pub/sub | Durable source of truth plus fast propagation | More moving parts than static config |

## Failure modes and mitigations

- Redis slot or node failure: route through cluster-aware client and fall back conservatively if atomic path is unavailable.
- Excessive denial due to bad config: keep versioned config changes and admin rollback path.
- Split-brain behavior in degraded mode: divide local allowance conservatively and log fallback state aggressively.
- Latency regression from remote checks: allow embedded starter mode for internal low-latency services.

## What to measure in a real implementation

- Decision latency by algorithm and backend.
- Circuit-breaker state duration and fallback request volume.
- Most frequently exhausted scopes and routes.
- Config reload success rate and propagation delay.
