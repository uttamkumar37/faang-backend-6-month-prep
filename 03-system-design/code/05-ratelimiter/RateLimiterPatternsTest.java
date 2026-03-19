package systemdesign.ratelimiter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RateLimiterPatterns — verifies token bucket and sliding window behaviour.
 */
class RateLimiterPatternsTest {

    // ── Token Bucket ──────────────────────────────────────────────────────────

    @Test
    void tokenBucket_allowsBurstUpToCapacity() {
        // capacity=5, refill=1 req/s — should allow 5 immediate requests
        var limiter = new RateLimiterPatterns.TokenBucketRateLimiter(5, 1.0);
        int allowed = 0;
        for (int i = 0; i < 5; i++) if (limiter.tryAcquire()) allowed++;
        assertThat(allowed).isEqualTo(5);
    }

    @Test
    void tokenBucket_blocksWhenExhausted() {
        var limiter = new RateLimiterPatterns.TokenBucketRateLimiter(3, 1.0);
        // Drain the bucket
        limiter.tryAcquire(); limiter.tryAcquire(); limiter.tryAcquire();
        // 4th request should be rejected immediately (no time has passed to refill)
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void tokenBucket_refillsOverTime() throws InterruptedException {
        var limiter = new RateLimiterPatterns.TokenBucketRateLimiter(2, 10.0); // 10 tokens/sec
        limiter.tryAcquire(); limiter.tryAcquire(); // drain
        assertThat(limiter.tryAcquire()).isFalse();  // empty

        Thread.sleep(200); // 200ms → ~2 tokens refilled at 10/sec

        assertThat(limiter.tryAcquire()).isTrue();   // refilled
    }

    // ── Fixed Window Counter ──────────────────────────────────────────────────

    @Test
    void fixedWindow_allowsUpToLimit() throws Exception {
        // maxRequests=3 per 1 second window
        var limiter = new RateLimiterPatterns.FixedWindowRateLimiter(3, 1000);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();  // 4th in same window
    }

    @Test
    void fixedWindow_resetsAfterWindowExpiry() throws Exception {
        var limiter = new RateLimiterPatterns.FixedWindowRateLimiter(2, 200); // 200ms window
        limiter.tryAcquire(); limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse();

        Thread.sleep(250); // wait for window to expire
        assertThat(limiter.tryAcquire()).isTrue();   // new window
    }
}
