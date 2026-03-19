package systemdesign.ratelimiter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate Limiter Implementations
 * Covers: Token Bucket, Sliding Window Counter, Fixed Window, Leaky Bucket
 * Production note: these in-memory versions suitable for single-node;
 *                 distribute state via Redis for multi-node deployments.
 */
public class RateLimiterPatterns {

    // ─────────────────────────────────────────────
    // 1. TOKEN BUCKET
    // Allows burst up to capacity, then tokens refill at a steady rate.
    // ─────────────────────────────────────────────

    static class TokenBucketRateLimiter {
        private final long capacity;          // max tokens (burst allowance)
        private final double refillRate;      // tokens per second
        private double tokens;
        private long lastRefillNanos;

        public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRate = refillRatePerSecond;
            this.tokens = capacity;           // start full
            this.lastRefillNanos = System.nanoTime();
        }

        public synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;   // rate limited
        }

        private void refill() {
            long nowNanos = System.nanoTime();
            double secondsElapsed = (nowNanos - lastRefillNanos) / 1_000_000_000.0;
            double newTokens = secondsElapsed * refillRate;
            tokens = Math.min(capacity, tokens + newTokens);
            lastRefillNanos = nowNanos;
        }
    }

    // ─────────────────────────────────────────────
    // 2. FIXED WINDOW COUNTER
    // Count requests in fixed time windows (e.g., 0–60s, 60–120s).
    // Problem: burst at window boundary (59s + 1s = 2x rate in 2s window).
    // ─────────────────────────────────────────────

    static class FixedWindowRateLimiter {
        private final int maxRequests;
        private final long windowMs;

        // userId → [windowStart, count]
        private final Map<String, long[]> windows = new ConcurrentHashMap<>();

        public FixedWindowRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        public boolean tryAcquire(String userId) {
            long now = System.currentTimeMillis();
            long[] state = windows.compute(userId, (k, v) -> {
                if (v == null || now - v[0] >= windowMs) {
                    // New or expired window
                    return new long[]{now, 0};
                }
                return v;
            });

            synchronized (state) {
                // Re-check after getting synchronized access
                if (now - state[0] >= windowMs) {
                    state[0] = now;
                    state[1] = 0;
                }
                if (state[1] < maxRequests) {
                    state[1]++;
                    return true;
                }
                return false;
            }
        }
    }

    // ─────────────────────────────────────────────
    // 3. SLIDING WINDOW COUNTER
    // Hybrid: estimate using current + previous window weighted by position.
    // Fixes boundary burst problem of fixed window. Used in practice (Nginx, Redis).
    // ─────────────────────────────────────────────

    static class SlidingWindowCounterRateLimiter {
        private final int maxRequests;
        private final long windowMs;

        // userId → [prevWindowStart, prevCount, currWindowStart, currCount]
        private final Map<String, long[]> counters = new ConcurrentHashMap<>();

        public SlidingWindowCounterRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        public boolean tryAcquire(String userId) {
            long now = System.currentTimeMillis();
            long currWindowStart = now - (now % windowMs);
            long prevWindowStart = currWindowStart - windowMs;

            long[] state = counters.computeIfAbsent(userId, k -> new long[4]);

            synchronized (state) {
                // Slide window
                if (state[2] != currWindowStart) {
                    if (state[2] == prevWindowStart) {
                        // Move current to previous
                        state[0] = state[2];
                        state[1] = state[3];
                    } else {
                        // Current window is too old, reset previous
                        state[0] = prevWindowStart;
                        state[1] = 0;
                    }
                    state[2] = currWindowStart;
                    state[3] = 0;
                }

                double prevWindowWeight = 1.0 - ((double)(now - currWindowStart) / windowMs);
                double estimatedCount = state[1] * prevWindowWeight + state[3];

                if (estimatedCount < maxRequests) {
                    state[3]++;
                    return true;
                }
                return false;
            }
        }
    }

    // ─────────────────────────────────────────────
    // 4. LEAKY BUCKET
    // Output flows at constant rate. Excess requests queued or dropped.
    // Smooths out traffic bursts.
    // ─────────────────────────────────────────────

    static class LeakyBucketRateLimiter {
        private final int capacity;
        private final double leakRatePerMs;   // requests leaked per ms
        private double waterLevel;            // current requests in bucket
        private long lastLeakNanos;

        public LeakyBucketRateLimiter(int capacity, int requestsPerSecond) {
            this.capacity = capacity;
            this.leakRatePerMs = requestsPerSecond / 1000.0;
            this.waterLevel = 0;
            this.lastLeakNanos = System.nanoTime();
        }

        public synchronized boolean tryAcquire() {
            leak();
            if (waterLevel < capacity) {
                waterLevel++;
                return true;
            }
            return false; // bucket full
        }

        private void leak() {
            long nowNanos = System.nanoTime();
            long elapsedMs = (nowNanos - lastLeakNanos) / 1_000_000;
            if (elapsedMs > 0) {
                waterLevel = Math.max(0, waterLevel - (elapsedMs * leakRatePerMs));
                lastLeakNanos = nowNanos;
            }
        }
    }

    // ─────────────────────────────────────────────
    // 5. DISTRIBUTED RATE LIMITER
    // In production: use Redis with Lua script for atomicity.
    // This simulates the logic; replace with actual Redis calls.
    // ─────────────────────────────────────────────

    /**
     * Redis Lua script for atomic token bucket rate limiting:
     *
     * local key = KEYS[1]
     * local capacity = tonumber(ARGV[1])
     * local refill_rate = tonumber(ARGV[2])
     * local now = tonumber(ARGV[3])
     * local requested = tonumber(ARGV[4])
     *
     * local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
     * local tokens = tonumber(bucket[1]) or capacity
     * local last_refill = tonumber(bucket[2]) or now
     *
     * -- Refill tokens based on elapsed time
     * local elapsed = (now - last_refill) / 1000
     * tokens = math.min(capacity, tokens + elapsed * refill_rate)
     *
     * if tokens >= requested then
     *     tokens = tokens - requested
     *     redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
     *     redis.call('EXPIRE', key, 3600)
     *     return 1  -- allowed
     * else
     *     redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
     *     redis.call('EXPIRE', key, 3600)
     *     return 0  -- rejected
     * end
     */
    static class RedisRateLimiterSimulator {
        // Simulates what the Lua script does above, in-memory for demo
        private final Map<String, double[]> buckets = new ConcurrentHashMap<>();
        private final int capacity;
        private final double refillRate;

        RedisRateLimiterSimulator(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
        }

        public boolean isAllowed(String key) {
            long nowMs = System.currentTimeMillis();
            double[] bucket = buckets.computeIfAbsent(key, k -> new double[]{capacity, nowMs});

            synchronized (bucket) {
                double elapsed = (nowMs - bucket[1]) / 1000.0;
                bucket[0] = Math.min(capacity, bucket[0] + elapsed * refillRate);
                bucket[1] = nowMs;

                if (bucket[0] >= 1.0) {
                    bucket[0] -= 1.0;
                    return true;
                }
                return false;
            }
        }
    }

    // ─────────────────────────────────────────────
    // 6. RATE LIMITER MIDDLEWARE SIMULATION
    // Shows how a rate limiter fits into an API gateway or Spring filter.
    // ─────────────────────────────────────────────

    static class RateLimiterMiddleware {
        record RateLimitConfig(int requestsPerSecond, int burstCapacity) {}
        record RateLimitResult(boolean allowed, long retryAfterMs, long remainingTokens) {}

        private final Map<String, TokenBucketRateLimiter> limiters = new ConcurrentHashMap<>();
        private final RateLimitConfig defaultConfig;

        RateLimiterMiddleware(RateLimitConfig defaultConfig) {
            this.defaultConfig = defaultConfig;
        }

        public RateLimitResult checkLimit(String userId) {
            TokenBucketRateLimiter limiter = limiters.computeIfAbsent(userId, k ->
                new TokenBucketRateLimiter(defaultConfig.burstCapacity(),
                                           defaultConfig.requestsPerSecond())
            );

            boolean allowed = limiter.tryAcquire();
            if (allowed) {
                return new RateLimitResult(true, 0, (long) limiter.tokens);
            } else {
                // Retry after = 1 / refillRate seconds in ms
                long retryAfterMs = (long)(1000.0 / defaultConfig.requestsPerSecond());
                return new RateLimitResult(false, retryAfterMs, 0);
            }
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Token Bucket (capacity=5, refillRate=2/sec) ===");
        TokenBucketRateLimiter tokenBucket = new TokenBucketRateLimiter(5, 2.0);
        for (int i = 0; i < 8; i++) {
            boolean allowed = tokenBucket.tryAcquire();
            System.out.println("Request " + (i + 1) + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
        }

        System.out.println("\nWaiting 2 seconds for refill...");
        Thread.sleep(2000);
        System.out.println("After 2s refill (expect ~4 tokens):");
        for (int i = 0; i < 5; i++) {
            boolean allowed = tokenBucket.tryAcquire();
            System.out.println("Request " + (i + 1) + ": " + (allowed ? "ALLOWED" : "BLOCKED"));
        }

        System.out.println("\n=== Fixed Window (max=3 per 1000ms) ===");
        FixedWindowRateLimiter fixedWindow = new FixedWindowRateLimiter(3, 1000);
        for (int i = 0; i < 5; i++) {
            System.out.println("user1 request " + (i + 1) + ": " +
                (fixedWindow.tryAcquire("user1") ? "ALLOWED" : "BLOCKED"));
        }

        System.out.println("\n=== Sliding Window Counter (max=5 per 1000ms) ===");
        SlidingWindowCounterRateLimiter sliding = new SlidingWindowCounterRateLimiter(5, 1000);
        for (int i = 0; i < 7; i++) {
            System.out.println("user1 request " + (i + 1) + ": " +
                (sliding.tryAcquire("user1") ? "ALLOWED" : "BLOCKED"));
        }

        System.out.println("\n=== Rate Limiter Middleware (5 rps, burst=10) ===");
        RateLimiterMiddleware middleware = new RateLimiterMiddleware(
            new RateLimiterMiddleware.RateLimitConfig(5, 10));
        for (int i = 0; i < 12; i++) {
            var result = middleware.checkLimit("user_abc");
            if (result.allowed()) {
                System.out.printf("Request %2d: ALLOWED (remaining tokens: %d)%n", i+1, result.remainingTokens());
            } else {
                System.out.printf("Request %2d: BLOCKED (retry after: %dms)%n", i+1, result.retryAfterMs());
            }
        }
    }
}
