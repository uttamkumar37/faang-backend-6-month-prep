import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterDesign {
    record Decision(boolean allowed, Duration retryAfter) {}
    record RateLimitRule(int capacity, int refillTokens, Duration refillEvery) {}

    interface RateLimitAlgorithm {
        Decision allow(String key);
    }

    static final class TokenBucketLimiter implements RateLimitAlgorithm {
        private final RateLimitRule rule;
        private final Clock clock;
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

        TokenBucketLimiter(RateLimitRule rule, Clock clock) {
            if (rule.capacity() <= 0 || rule.refillTokens() <= 0) throw new IllegalArgumentException("invalid rule");
            this.rule = rule;
            this.clock = clock;
        }

        public Decision allow(String key) {
            Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(rule.capacity(), clock.instant()));
            synchronized (bucket) {
                refill(bucket);
                if (bucket.tokens > 0) {
                    bucket.tokens--;
                    return new Decision(true, Duration.ZERO);
                }
                return new Decision(false, rule.refillEvery());
            }
        }

        private void refill(Bucket bucket) {
            Instant now = clock.instant();
            long periods = Duration.between(bucket.lastRefill, now).dividedBy(rule.refillEvery());
            if (periods > 0) {
                long tokensToAdd = periods * rule.refillTokens();
                bucket.tokens = (int) Math.min(rule.capacity(), bucket.tokens + tokensToAdd);
                bucket.lastRefill = bucket.lastRefill.plus(rule.refillEvery().multipliedBy(periods));
            }
        }
    }

    static final class Bucket {
        int tokens;
        Instant lastRefill;
        Bucket(int tokens, Instant lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }

    // Test ideas: first burst, refill after time, concurrent callers, invalid rule, unknown key isolation.
}
