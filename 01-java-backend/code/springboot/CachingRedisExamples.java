package com.faangprep.javabackend.springboot;

// Dependencies required (add to pom.xml):
//   org.springframework.boot:spring-boot-starter-data-redis
//   org.springframework.boot:spring-boot-starter-cache
//   com.github.ben-manes.caffeine:caffeine
//   org.redisson:redisson-spring-boot-starter:3.30.x (for distributed lock)

import org.springframework.cache.annotation.*;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.*;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Caching and Redis — Spring Cache, RedisTemplate, and advanced cache patterns
 *
 * Topics:
 *  1. Spring Cache annotations — @Cacheable, @CachePut, @CacheEvict, @Caching
 *  2. RedisCacheManager config — JSON serializer, per-cache TTL, null handling
 *  3. RedisTemplate operations — String, Hash, List, Set, Sorted Set
 *  4. Caffeine in-process cache — eviction policies, stats
 *  5. Two-level cache (L1 Caffeine + L2 Redis)
 *  6. Distributed lock — SETNX pattern, Lua atomic release
 *  7. Cache stampede prevention — mutex + TTL jitter
 *  8. Cache warming — startup pre-population
 *  9. Cache aside vs read-through vs write-through patterns
 * 10. Cache invalidation — event-driven and versioned keys
 */
public class CachingRedisExamples {

    // ─── Domain model ────────────────────────────────────────────────────────
    record User(Long id, String name, String email, String department) {}
    record Product(Long id, String sku, String name, double price, int stock) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SPRING CACHE ANNOTATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Service
    static class UserService {

        // @Cacheable: returns cached value if key exists; calls method only on miss
        // SpEL keys: #id, #user.id, #root.method.name, #result, T(java.util.UUID).randomUUID()
        @Cacheable(
            cacheNames = "users",
            key = "#id",
            unless = "#result == null"      // don't cache null results
        )
        public User findById(Long id) {
            System.out.println("  [DB] loading user " + id); // only prints on cache miss
            return new User(id, "User-" + id, "user" + id + "@example.com", "Engineering");
        }

        // Conditional caching — only cache if condition is true
        @Cacheable(
            cacheNames = "users",
            key = "#name",
            condition = "#name.length() > 2"    // don't cache very short names
        )
        public User findByName(String name) {
            return new User(1L, name, name + "@example.com", "Sales");
        }

        // @CachePut: ALWAYS calls method AND updates cache (for write operations)
        // Use on save/update — keeps cache in sync
        @CachePut(cacheNames = "users", key = "#result.id")
        public User save(User user) {
            System.out.println("  [DB] saving user: " + user.name());
            return user; // saves to DB, #result becomes the saved object
        }

        // @CacheEvict: removes entries on delete/change
        @CacheEvict(cacheNames = "users", key = "#id")
        public void delete(Long id) {
            System.out.println("  [DB] deleting user " + id);
        }

        // Evict entire cache (e.g., after bulk import)
        @CacheEvict(cacheNames = "users", allEntries = true)
        public void evictAll() {
            System.out.println("  [CACHE] all user cache entries evicted");
        }

        // @Caching: combine multiple cache operations in one method
        @Caching(
            evict = {
                @CacheEvict(cacheNames = "users",       key = "#id"),
                @CacheEvict(cacheNames = "users-by-dept", allEntries = true)
            }
        )
        public void promoteUser(Long id, String newDepartment) {
            System.out.println("  [DB] promoting user " + id + " to " + newDepartment);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. RedisCacheManager CONFIGURATION
    // ─────────────────────────────────────────────────────────────────────────

    @Configuration
    static class RedisCacheConfig {

        @Bean
        public RedisCacheManager cacheManager(
                RedisConnectionFactory connectionFactory,
                ObjectMapper objectMapper) {

            // Default config: TTL 1 hour, JSON serialization
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(objectMapper)
                    )
                )
                .disableCachingNullValues()     // avoid caching nulls
                .computePrefixWith(cacheName -> "app:" + cacheName + ":"); // key prefix

            // Per-cache TTL overrides
            Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "users",         defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "products",      defaultConfig.entryTtl(Duration.ofHours(6)),
                "rate-limits",   defaultConfig.entryTtl(Duration.ofMinutes(1)),
                "sessions",      defaultConfig.entryTtl(Duration.ofHours(24))
            );

            return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()  // participates in @Transactional
                .build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. RedisTemplate OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Service
    static class RedisService {
        // In real code: @Autowired RedisTemplate<String, Object> redisTemplate;
        // Shown here as static demos using the API contract

        /** String operations — simple key/value with TTL */
        static void stringOpsDemo() {
            System.out.println("=== 3a. String Ops ===");
            // ValueOperations<String, String> ops = redisTemplate.opsForValue();
            // ops.set("session:abc123", "userId:42", Duration.ofMinutes(30));
            // String userId = ops.get("session:abc123");
            // Long views = ops.increment("page:views:homepage");  // atomic counter
            // ops.setIfAbsent("lock:resource", "owner", Duration.ofSeconds(30)); // distributed lock
            System.out.println("SET key val EX 30 / GET key / INCR counter / SET NX key val");
        }

        /** Hash operations — partial updates without serializing the whole object */
        static void hashOpsDemo() {
            System.out.println("=== 3b. Hash Ops ===");
            // HashOperations<String, String, Object> ops = redisTemplate.opsForHash();
            // ops.putAll("user:42", Map.of("name", "Alice", "email", "a@x.com", "score", 100));
            // ops.put("user:42", "score", 150);          // update ONE field — no re-serialize
            // String name  = (String) ops.get("user:42", "name");
            // Map<String, Object> user = ops.entries("user:42"); // get all fields
            // ops.increment("user:42", "score", 10);     // atomic field increment
            System.out.println("HSET user:42 name Alice / HGET / HINCRBY score 10");
        }

        /** List operations — queue, stack, recent activity */
        static void listOpsDemo() {
            System.out.println("=== 3c. List Ops ===");
            // ListOperations<String, String> ops = redisTemplate.opsForList();
            // ops.leftPush("recent:user:42", "viewed:product:123");  // newest first
            // ops.leftPushAll("recent:user:42", events...);
            // redisTemplate.execute(new SessionCallback<Object>() { ... }); // pipeline
            // ops.rightPop("queue:notifications");   // FIFO queue consumer
            // ops.leftPop("stack:undo");             // LIFO stack
            // Keep only last 20 items: ops.trim("recent:user:42", 0, 19);
            System.out.println("LPUSH list val / RPOP / LTRIM 0 19 (recent items)");
        }

        /** Sorted Set operations — leaderboard, rate limiting */
        static void sortedSetDemo() {
            System.out.println("=== 3d. Sorted Set Ops (Leaderboard) ===");
            // ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
            // ops.add("leaderboard:2024", "alice", 1500.0);   // ZADD score member
            // ops.incrementScore("leaderboard:2024", "alice", 50); // atomic +50
            // // Top 10 (highest score first):
            // Set<ZSetOperations.TypedTuple<String>> top10 = ops.reverseRangeWithScores("leaderboard:2024", 0, 9);
            // // Rank (0-indexed):
            // Long rank = ops.reverseRank("leaderboard:2024", "alice");
            System.out.println("ZADD board 1500 alice / ZINCRBY / ZREVRANGE 0 9 WITHSCORES");
        }

        /** Set operations — unique visitors, friend connections */
        static void setOpsDemo() {
            System.out.println("=== 3e. Set Ops ===");
            // SetOperations<String, String> ops = redisTemplate.opsForSet();
            // ops.add("active-users:2024-01-01", "alice", "bob", "carol");
            // ops.add("active-users:2024-01-02", "alice", "dave");
            // // Returning users (visited both days):
            // Set<String> returning = ops.intersect("active-users:2024-01-01", "active-users:2024-01-02");
            // // Users unique to day 1:
            // Set<String> churned   = ops.difference("active-users:2024-01-01", "active-users:2024-01-02");
            System.out.println("SADD / SISMEMBER / SINTERSTORE (returning users)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. CAFFEINE — in-process L1 cache configuration
    // ─────────────────────────────────────────────────────────────────────────

    @Configuration
    static class CaffeineConfig {

        @Bean
        public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache() {
            return Caffeine.newBuilder()
                .maximumSize(10_000)                // evict LRU when exceeds 10k entries
                .expireAfterWrite(Duration.ofMinutes(5))   // TTL from write
                .expireAfterAccess(Duration.ofMinutes(2))  // idle TTL (whichever is shorter)
                .refreshAfterWrite(Duration.ofMinutes(1))  // async refresh before expiry
                .recordStats()         // enables hit rate, eviction count metrics
                .removalListener((key, value, cause) ->
                    System.out.println("  [Caffeine] evicted " + key + " reason: " + cause))
                .build();
        }

        // Spring Cache abstraction backed by Caffeine
        @Bean
        public CaffeineCacheManager caffeineCacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager("products", "config");
            manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
            );
            return manager;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. TWO-LEVEL CACHE — L1 (Caffeine, fast) + L2 (Redis, shared)
    // ─────────────────────────────────────────────────────────────────────────

    static class TwoLevelCache<V> {
        private final Map<String, V> l1 = new ConcurrentHashMap<>();  // Caffeine in real code
        private final Map<String, V> l2 = new HashMap<>();            // Redis in real code

        // get: L1 → L2 → source
        V get(String key, Supplier<V> loader) {
            // 1. L1 hit (sub-millisecond, no network)
            V l1Value = l1.get(key);
            if (l1Value != null) {
                System.out.println("  L1 hit: " + key);
                return l1Value;
            }
            // 2. L2 (Redis) hit (single network hop ~1ms)
            V l2Value = l2.get(key);
            if (l2Value != null) {
                System.out.println("  L2 hit: " + key);
                l1.put(key, l2Value);  // populate L1
                return l2Value;
            }
            // 3. Cache miss — load from source (DB etc.)
            System.out.println("  MISS loading: " + key);
            V fresh = loader.get();
            l2.put(key, fresh);   // write to Redis with TTL (simulated)
            l1.put(key, fresh);   // write to Caffeine
            return fresh;
        }

        // set: update both levels
        void set(String key, V value) {
            l1.put(key, value);
            l2.put(key, value);
        }

        // invalidate: remove from both levels
        void invalidate(String key) {
            l1.remove(key);
            l2.remove(key);
        }

        // IMPORTANT: on L1 invalidation from other nodes, publish to Redis pub/sub
        // In real code: redisTemplate.convertAndSend("cache-invalidation", key)
        // Other nodes subscribe and call l1.remove(key) when they receive the event
    }

    static void twoLevelCacheDemo() {
        System.out.println("=== 5. Two-Level Cache ===");
        TwoLevelCache<User> cache = new TwoLevelCache<>();
        User db = new User(1L, "Alice", "alice@example.com", "Engineering");

        // First access — full miss, loads from DB
        User u1 = cache.get("user:1", () -> db);
        User u2 = cache.get("user:1", () -> db);  // L1 hit
        cache.invalidate("user:1");
        User u3 = cache.get("user:1", () -> db);  // both levels miss again
        System.out.println("user: " + u3.name());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. DISTRIBUTED LOCK — preventing concurrent cache population
    // ─────────────────────────────────────────────────────────────────────────

    static class DistributedLockDemo {

        // WRONG: two operations — NOT atomic, race condition possible
        static void wrongRedisLock(Map<String, String> redis, String key, String owner) {
            // if (!redis.containsKey(key)) redis.put(key, owner + ":30s");  // race!
            System.out.println("WRONG: GET then SET is NOT atomic — two threads can both see miss");
        }

        // CORRECT with Redis: SetIfAbsent (SET key value NX EX 30) — atomic
        // boolean acquired = ops.setIfAbsent(lockKey, owner, Duration.ofSeconds(30));
        // if (!acquired) throw new LockUnavailableException();
        // try { doWork(); } finally { releaseLock(lockKey, owner); }

        // RELEASE with Lua (atomic check-and-delete — prevents releasing another owner's lock)
        // RedisScript<Long> script = RedisScript.of("""
        //     if redis.call('get', KEYS[1]) == ARGV[1] then
        //         return redis.call('del', KEYS[1])
        //     else
        //         return 0
        //     end
        //     """, Long.class);
        // redisTemplate.execute(script, List.of(lockKey), owner);

        // BEST: Redisson (handles renewal, fair queuing, cluster modes)
        static void redissonLockPattern() {
            System.out.println("""
                Redisson pattern:
                  RLock lock = redissonClient.getLock("order:lock:" + orderId);
                  boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
                  if (!acquired) throw new LockBusyException();
                  try { processOrder(orderId); }
                  finally { lock.unlock(); }
                """);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. CACHE STAMPEDE / THUNDERING HERD PREVENTION
    // ─────────────────────────────────────────────────────────────────────────

    static class StampedePreventedCache<V> {
        private final Map<String, V>        store   = new ConcurrentHashMap<>();
        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        private final Random                rng     = new Random();

        // Problem: When a popular key expires, thousands of threads all see miss
        // and simultaneously hit the database — DATABASE OVERLOAD

        // Solution 1: per-key mutex (only first thread fetches, others wait)
        V getWithMutex(String key, Supplier<V> loader) {
            V cached = store.get(key);
            if (cached != null) return cached;

            // computeIfAbsent ensures exactly ONE lock per key
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            try {
                // Double-check: another thread may have loaded while we waited
                cached = store.get(key);
                if (cached != null) return cached;

                V fresh = loader.get();  // only ONE thread calls the DB
                store.put(key, fresh);
                return fresh;
            } finally {
                lock.unlock();
                locks.remove(key); // cleanup; use WeakHashMap in production
            }
        }

        // Solution 2: TTL jitter — spread expiry to avoid synchronized expiry
        // Instead of: ops.set(key, value, Duration.ofHours(1));
        // Use:        ops.set(key, value, Duration.ofSeconds(3600 + rng.nextInt(300)));
        //             → expiry is 60–65 min, items in batch don't all expire at once

        // Solution 3: probabilistic early re-computation (PER algorithm)
        // When remaining TTL < threshold * -log(random), recompute proactively
    }

    static void stampedeDemo() {
        System.out.println("=== 7. Cache Stampede Prevention ===");
        StampedePreventedCache<User> cache = new StampedePreventedCache<>();
        AtomicInteger dbHits = new java.util.concurrent.atomic.AtomicInteger(0);

        // Simulate 10 concurrent threads trying to load same key
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                cache.getWithMutex("user:1", () -> {
                    dbHits.incrementAndGet(); // only ONE should increment
                    return new User(1L, "Alice", "a@x.com", "Engineering");
                });
            }));
        }
        threads.forEach(t -> { try { t.join(); } catch (InterruptedException e) {} });
        System.out.println("DB hits (should be 1): " + dbHits.get());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. CACHE INVALIDATION STRATEGIES
    // ─────────────────────────────────────────────────────────────────────────

    static void cacheInvalidationDemo() {
        System.out.println("=== 8. Cache Invalidation ===");

        System.out.println("""
            Strategy 1 — Cache-Aside (Lazy loading, most common):
              Read:  Get from cache. On miss, load from DB, write to cache, return.
              Write: Write to DB, then DELETE the cache key (not update).
              Why delete not update? Writing DB+cache is not atomic; deletion is safer.
              Problem: Cache miss on first read after write — one cold request hits DB.

            Strategy 2 — Write-Through:
              Write: Write to cache AND DB (synchronous, cache is always fresh).
              Read:  Always a cache hit (after first write).
              Use:   When read speed matters more than write latency.

            Strategy 3 — Write-Behind (Write-Back):
              Write: Write to cache immediately, async batch-write to DB.
              Risk:  Data loss if cache crashes before flush.
              Use:   Write-heavy workloads, metrics, event logging.

            Strategy 4 — Event-Driven Invalidation:
              On data change → publish to Kafka topic "cache-invalidation".
              Cache service subscribes, evicts stale keys.
              Benefit: Works across multiple service instances.
              @KafkaListener(topics = "user-updated")
              void onUserUpdated(UserUpdatedEvent event) {
                  redisTemplate.delete("users::" + event.userId());  // Spring cache key
              }

            Strategy 5 — Versioned Keys:
              Instead of invalidating, change the key:
              "user:42:v1" → change to "user:42:v2" when data changes.
              Old version expires naturally; no explicit invalidation needed.
              Benefit: Trivially simple, great for CDN cache busting.
              """);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. RATE LIMITING WITH REDIS SORTED SET
    // ─────────────────────────────────────────────────────────────────────────

    static void rateLimitDemo() {
        System.out.println("=== 9. Redis Rate Limiter (Sliding Window) ===");
        System.out.println("""
            Sliding Window with ZSet (100 req/min per user):

            String key = "rate:user:" + userId;
            long now = System.currentTimeMillis();
            long windowStart = now - 60_000;   // 60 seconds ago

            // Atomic check-and-increment via Lua script:
            String lua = \"\"\"
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])
                local limit  = tonumber(ARGV[3])
                redis.call('ZREMRANGEBYSCORE', key, '-inf', window)    -- remove old
                local count = redis.call('ZCARD', key)                 -- current count
                if count < limit then
                    redis.call('ZADD', key, now, now)                  -- add request
                    redis.call('EXPIRE', key, 60)                      -- reset TTL
                    return 1  -- allowed
                end
                return 0      -- rejected
                \"\"\"

            Long result = redisTemplate.execute(script, List.of(key),
                String.valueOf(now), String.valueOf(windowStart), "100");
            if (result == 0) throw new RateLimitExceededException();
            """);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. CACHE-HIT METRICS AND OBSERVABILITY
    // ─────────────────────────────────────────────────────────────────────────

    static void cacheMetricsDemo() {
        System.out.println("=== 10. Cache Metrics ===");
        System.out.println("""
            With Caffeine (stats enabled):
              CacheStats stats = cache.stats();
              double hitRate    = stats.hitRate();          // 0.0 to 1.0
              long   hitCount   = stats.hitCount();
              long   missCount  = stats.missCount();
              long   evictions  = stats.evictionCount();
              double loadTime   = stats.averageLoadPenalty(); // ns per miss

            Spring Actuator exposes via /actuator/metrics/cache.gets:
              tag cacheName, tag result=hit|miss
              → histogram of load times per cache

            Alert thresholds:
              hitRate < 0.7   → cache is mostly cold; review TTL and key strategy
              evictions high  → cache is too small; increase maximumSize
              loadTime > 20ms → DB or downstream too slow; optimize queries
            """);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== Caching & Redis Examples ===\n");

        System.out.println("--- Spring Cache Annotations ---");
        System.out.println("@Cacheable, @CachePut, @CacheEvict — see UserService inner class");

        System.out.println("\n--- RedisTemplate Operations ---");
        RedisService.stringOpsDemo();
        RedisService.hashOpsDemo();
        RedisService.listOpsDemo();
        RedisService.sortedSetDemo();
        RedisService.setOpsDemo();

        twoLevelCacheDemo();

        System.out.println("\n=== 6. Distributed Lock ===");
        DistributedLockDemo.wrongRedisLock(null, null, null);
        DistributedLockDemo.redissonLockPattern();

        stampedeDemo();
        cacheInvalidationDemo();
        rateLimitDemo();
        cacheMetricsDemo();

        System.out.println("\n=== All caching demos completed ===");
    }
}
