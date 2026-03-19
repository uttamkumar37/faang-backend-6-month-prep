# Rate Limiter

## Purpose

Protect backend services from:
- DDoS attacks.
- Brute-force authentication.
- API abuse (exceeding paid quota).
- Resource exhaustion from noisy neighbors.

## Common Algorithms

### 1. Token Bucket

```
Config: capacity=10 tokens, refill=1 token/sec

State: { tokens: 8, last_refill_time: T }

On request:
  elapsed = now - last_refill_time
  tokens = min(capacity, tokens + elapsed × rate)
  if tokens >= 1: allow, tokens--
  else: reject 429

Pros: handles bursts (up to capacity), simple
Cons: bursty (can exhaust bucket instantly)
```

### 2. Leaky Bucket

```
Requests → [Queue (bucket)] → process at fixed rate (e.g. 100 req/sec)
If queue full → reject

Pros: smooth outgoing rate, protects downstream
Cons: bursty input still fails, adds latency
```

### 3. Fixed Window Counter

```
Window: 1 minute slots (12:00, 12:01, ...)
Counter: how many requests in current minute

Pros: simple
Cons: boundary problem — 500 at 12:00:59 + 500 at 12:01:00 = 1000 in 2 seconds
```

### 4. Sliding Window Log

```
Maintain sorted list of request timestamps.
On request:
  Remove timestamps older than (now - window)
  If count < limit: allow, append now
  Else: reject

Pros: most accurate
Cons: O(n) memory per user
```

### 5. Sliding Window Counter (hybrid — best for production)

```
Rate = (previous_window_hits × (1 - elapsed/window)) + current_window_hits

Example (limit=100/min):
  Current minute (12:01) has 40 hits.
  Previous minute (12:00) had 75 hits.
  40 seconds into current minute.
  Estimate = 75 × (20/60) + 40 = 25 + 40 = 65 → allow
```

---

## System Design

```
Client → API Gateway / Rate Limiter Middleware
              ↓
         Redis (distributed counter)
              ↓
         Backend Service
```

### Redis Implementation (Token Bucket)

```lua
-- Lua script (atomic execution in Redis)
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])  -- tokens per second
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or now

local elapsed = math.max(0, now - last_refill)
tokens = math.min(capacity, tokens + elapsed * refill_rate)

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return 1  -- allowed
else
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    return 0  -- rejected
end
```

### Response Headers (Standard)

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1640000060
Retry-After: 30
```

---

## Rate Limit Keys

| Strategy | Key Format | Use Case |
|---|---|---|
| Per IP | `rl:ip:{ip}` | DDoS, unauthenticated |
| Per User | `rl:user:{userId}` | API quota |
| Per API Key | `rl:apikey:{key}` | B2B rate limiting |
| Per Endpoint | `rl:user:{userId}:endpoint:{path}` | Fine-grained control |

---

## Multi-Tier Limits

```yaml
limits:
  - key: user
    limits:
      - period: 1s    limit: 10    # burst protection
      - period: 60s   limit: 100   # per minute
      - period: 3600s limit: 1000  # per hour
```

---

## Distributed Considerations

- Single Redis node = SPOF. Use Redis Cluster or Redis Sentinel.
- Clock skew between servers → use Redis `TIME` command for server-side timestamps.
- Race condition with `GET + SET`: use Lua scripts or Redis atomic ops (`INCR`, `MULTI/EXEC`).

---

## Interview Tips

- Token bucket for most API gatway scenarios; sliding window log for accuracy.
- Always mention the headers you return (429 + Retry-After).
- Discuss where to place it: in API gateway (centralized) vs in-service middleware (distributed).
- Multiple layers: firewall rate limits by IP, gateway by user, service by endpoint.

---

## Project Structure

```
rate-limiter/
├── src/main/java/com/ratelimiter/
│   ├── RateLimiterApplication.java
│   ├── filter/
│   │   └── RateLimitFilter.java      # Spring OncePerRequestFilter
│   ├── algorithm/
│   │   ├── TokenBucket.java          # in-process, single instance
│   │   ├── SlidingWindowLog.java     # accurate, sorted set per user
│   │   └── FixedWindowCounter.java   # Redis INCR + EXPIRE
│   ├── service/
│   │   └── RateLimiterService.java   # delegates to algorithm + Redis
│   └── config/
│       └── RateLimitConfig.java      # limits per API tier
└── pom.xml
```

## Core Implementation

```java
// Token Bucket — in-memory, per-user state in Redis
public class TokenBucketLimiter {
    // Redis key: rate:bucket:{userId}
    // Redis hash fields: tokens, lastRefill

    private final RedisTemplate<String, String> redis;
    private final int capacity;     // max tokens
    private final double refillRate; // tokens per second

    public boolean allowRequest(String userId) {
        String key = "rate:bucket:" + userId;
        long now = System.currentTimeMillis();

        // Lua script for atomic read-modify-write
        String script = """
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens') or ARGV[3])
            local last   = tonumber(redis.call('HGET', KEYS[1], 'last')   or ARGV[4])
            local elapsed = (tonumber(ARGV[2]) - last) / 1000.0
            tokens = math.min(tonumber(ARGV[3]), tokens + elapsed * tonumber(ARGV[1]))
            if tokens >= 1 then
                tokens = tokens - 1
                redis.call('HSET', KEYS[1], 'tokens', tokens, 'last', ARGV[2])
                redis.call('EXPIRE', KEYS[1], 3600)
                return 1
            else
                redis.call('HSET', KEYS[1], 'last', ARGV[2])
                return 0
            end
            """;
        Long result = redis.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(key),
            String.valueOf(refillRate), String.valueOf(now),
            String.valueOf(capacity), String.valueOf(now)
        );
        return Long.valueOf(1).equals(result);
    }
}

// Sliding Window Log — sorted set per user
public class SlidingWindowLogLimiter {
    private final RedisTemplate<String, String> redis;
    private final int maxRequests;
    private final long windowMs;

    public boolean allowRequest(String userId) {
        String key = "rate:log:" + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        redis.executePipelined((RedisCallback<?>) conn -> {
            conn.zRemRangeByScore(key.getBytes(), 0, windowStart); // remove old
            conn.zAdd(key.getBytes(), now, (now + "-" + userId).getBytes());
            conn.expire(key.getBytes(), windowMs / 1000 + 1);
            return null;
        });

        Long count = redis.opsForZSet().zCard(key);
        return count != null && count <= maxRequests;
    }
}

// Spring Filter — enforce limit, return 429
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    @Autowired RateLimiterService limiter;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String userId = req.getHeader("X-User-Id");
        if (userId != null && !limiter.allowRequest(userId)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "1");
            res.setHeader("X-RateLimit-Limit", "100");
            res.getWriter().write("{\"error\":\"rate limit exceeded\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
```
