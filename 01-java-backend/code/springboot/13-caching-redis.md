# Caching and Redis — Spring Cache, Redis Patterns, Distributed Locking

---

## 1. Caching Patterns

### Cache-Aside (Lazy Loading) — most common pattern
Application manages the cache directly. Cache miss triggers a DB load:
```
read(key):
    value = cache.get(key)
    if value is null:
        value = db.get(key)      // cache miss → read from DB
        cache.set(key, value, TTL)
    return value

write(key, value):
    db.set(key, value)           // write to DB first
    cache.delete(key)            // invalidate cache (NOT cache.set — avoids stale race)
```
- **Pro**: only caches what's actually used; DB is source of truth; resilient to cache failure
- **Con**: cold start (first request always hits DB); possible stale window between write + invalidate

### Read-Through
Cache automatically loads from DB on miss (cache is the read interface):
```
read(key): cache handles the miss — calls DB internally, populates itself
write(key): application writes to DB; cache updated lazily or via write-through
```
- Common in JPA L2 cache (Ehcache, Infinispan intercept JPA reads)

### Write-Through
Every write goes through the cache to DB synchronously:
```
write(key, value): cache.set(key,value) → triggers synchronous write to DB
```
- **Pro**: cache always consistent with DB; no cache invalidation needed
- **Con**: every write has DB latency; wastes cache space on write-heavy, read-light data

### Write-Behind (Write-Back)
Write to cache, DB updated asynchronously:
```
write(key, value): cache.set(key, value) → queue DB write
                   DB updated eventually (background flush)
```
- **Pro**: extremely fast writes (in-memory only)
- **Con**: risk of data loss on crash before flush; complex consistency

### Refresh-Ahead
Cache proactively refreshes keys before they expire:
```
on access(key):
    if TTL_remaining < threshold:
        async refresh in background     // avoid expiry latency spike
    return cached value
```
- Caffeine's `refreshAfterWrite` implements this pattern

---

## 2. Spring Cache Abstraction

```java
// 1. Enable caching
@SpringBootApplication
@EnableCaching
public class Application { ... }

// 2. Configure a cache manager (bean)
@Bean
public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("users", "orders"); // in-memory, dev only
}

// 3. Annotate service methods
@Service
public class UserService {

    // @Cacheable: check cache first; if miss, execute method and store result
    @Cacheable(
        cacheNames = "users",
        key = "#id",                          // SpEL: use method parameter as key
        condition = "#id > 0",               // only cache if condition true (before method)
        unless = "#result == null"           // don't cache if result is null (after method)
    )
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null); // only called on cache miss
    }

    // @CachePut: always execute method AND update the cache (use for writes)
    @CachePut(cacheNames = "users", key = "#user.id")
    public User save(User user) {
        return userRepository.save(user);    // always runs; result stored in cache
    }

    // @CacheEvict: remove one or all entries
    @CacheEvict(cacheNames = "users", key = "#id")
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    // allEntries=true: clear the entire cache (e.g., bulk update)
    @CacheEvict(cacheNames = "users", allEntries = true)
    public void clearAllUsers() { }

    // @Caching: combine multiple cache annotations on one method
    @Caching(evict = {
        @CacheEvict(cacheNames = "users", key = "#user.id"),
        @CacheEvict(cacheNames = "usersByEmail", key = "#user.email")
    })
    public void updateUser(User user) {
        userRepository.save(user);
    }

    // @CacheConfig: class-level defaults
    @CacheConfig(cacheNames = "products")
    @Service
    class ProductService {
        @Cacheable(key = "#sku")  // inherits cacheNames = "products"
        public Product findBySku(String sku) { ... }
    }
}
```

### SpEL Key Expressions
```java
@Cacheable(key = "#id")                           // simple param
@Cacheable(key = "#user.id")                      // nested property
@Cacheable(key = "#p0.id + '-' + #p1")           // positional: p0 = first arg
@Cacheable(key = "#root.methodName + #id")        // method name in key
@Cacheable(key = "T(java.util.Objects).hash(#a, #b)") // hash of multiple args
```

---

## 3. Redis Cache Manager

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: secret
      lettuce:
        pool:
          max-active: 8
          max-idle: 4
```

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // Default config for all caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()  // JSON (human-readable + survives class changes)
                )
            )
            .disableCachingNullValues(); // don't cache null returns

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "users",    defaultConfig.entryTtl(Duration.ofHours(1)),
            "sessions", defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "catalog",  defaultConfig.entryTtl(Duration.ofDays(1))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

---

## 4. Spring Data Redis — Direct Redis Access

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}

@Service
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;

    // String operations (most common — key→value)
    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    public Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, ttl); // SETNX
    }

    // Hash operations (partial updates without full re-serialization)
    public void setUserField(Long userId, String field, Object value) {
        redisTemplate.opsForHash().put("user:" + userId, field, value);
    }
    public Map<Object,Object> getUserFields(Long userId) {
        return redisTemplate.opsForHash().entries("user:" + userId);
    }
    public void incrementField(Long userId, String field, long delta) {
        redisTemplate.opsForHash().increment("user:" + userId, field, delta);
    }

    // List operations (recent items, message queues)
    public void pushRecent(String key, Object value) {
        redisTemplate.opsForList().leftPush(key, value);
        redisTemplate.opsForList().ltrim(key, 0, 99); // keep last 100
    }
    public List<Object> getRecent(String key, int count) {
        return redisTemplate.opsForList().range(key, 0, count - 1);
    }

    // Set operations (unique tags, deduplication)
    public void addTag(String tagSet, String item) {
        redisTemplate.opsForSet().add(tagSet, item);
    }
    public Set<Object> getCommonUsers(String set1, String set2) {
        return redisTemplate.opsForSet().intersect(set1, set2); // SINTER
    }

    // Sorted Set — leaderboards, rate limiting (score = counter/timestamp)
    public void updateScore(String leaderboard, String player, double delta) {
        redisTemplate.opsForZSet().incrementScore(leaderboard, player, delta);
    }
    public Set<ZSetOperations.TypedTuple<Object>> getTopN(String key, int n) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
    }

    // Pipelining — batch multiple commands in one network round-trip
    public void batchSet(Map<String, Object> entries, Duration ttl) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            entries.forEach((k, v) -> {
                connection.stringCommands().setEx(k.getBytes(),
                    ttl.getSeconds(), serialize(v));
            });
            return null;
        });
    }
}
```

---

## 5. Redis Data Structures — When to Use Each

| Structure | Redis Command | Java Use Case |
|---|---|---|
| **String** | `SET/GET/INCR` | Simple value cache, counters, distributed sequences |
| **Hash** | `HSET/HGET/HMGET` | User profile/session (update one field without re-serializing whole object) |
| **List** | `LPUSH/RPOP/LRANGE` | Recent items feed, simple message queue (use Streams for production) |
| **Set** | `SADD/SMEMBERS/SINTER` | Unique visitor tracking, user tag deduplication, online users |
| **Sorted Set** | `ZADD/ZRANGE/ZREVRANGE` | Leaderboards, priority queues, sliding window rate limiting |
| **Bitmap** | `SETBIT/BITCOUNT` | Daily active users (1 bit per user), feature flags per user |
| **HyperLogLog** | `PFADD/PFCOUNT` | Cardinality estimation (unique URLs, IPs) — uses ~12KB regardless of size |
| **Stream** | `XADD/XREAD` | Persistent event log with consumer groups (Kafka-lite within Redis) |

---

## 6. Caffeine — High-Performance Local Cache

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Configuration
@EnableCaching
public class CaffeineConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)                     // max entries (LRU eviction when full)
            .expireAfterWrite(Duration.ofMinutes(5)) // TTL from last write
            .expireAfterAccess(Duration.ofMinutes(2)) // TTL from last read
            .refreshAfterWrite(Duration.ofSeconds(90)) // async refresh (needs CacheLoader)
            .recordStats()                           // enable hit rate metrics
        );
        return mgr;
    }

    // CacheLoader for sync refresh — called on refreshAfterWrite
    @Bean
    public AsyncCacheLoader<Object, Object> loader(UserRepository repo) {
        return (key, executor) ->
            CompletableFuture.supplyAsync(() -> repo.findById((Long) key).orElse(null), executor);
    }
}
```

**Caffeine eviction policies:**
- `maximumSize(n)` — evicts using W-TinyLFU (frequency + recency)
- `expireAfterWrite(d)` — entry expires d after write
- `expireAfterAccess(d)` — entry expires d after last read or write
- `refreshAfterWrite(d)` — **async** reload after d; stale value served until refresh completes
- `weakKeys()` / `softValues()` — GC-eligible entries (careful: unpredictable eviction)

---

## 7. Two-Level Cache — L1 (Caffeine) + L2 (Redis)

```java
// Pattern: check Caffeine first (nanosecond), fall back to Redis (milliseconds),
// fall back to DB (tens of milliseconds)
@Service
public class TwoLevelCache<V> {
    private final Cache<String, V> l1;     // Caffeine
    private final RedisTemplate<String, V> l2;
    private final Duration l1Ttl = Duration.ofMinutes(1);
    private final Duration l2Ttl = Duration.ofMinutes(30);

    public V get(String key, Supplier<V> loader) {
        // L1 hit
        V value = l1.getIfPresent(key);
        if (value != null) return value;

        // L2 hit
        value = l2.opsForValue().get(key);
        if (value != null) {
            l1.put(key, value);           // backfill L1
            return value;
        }

        // DB miss
        value = loader.get();
        l2.opsForValue().set(key, value, l2Ttl);
        l1.put(key, value);
        return value;
    }

    public void invalidate(String key) {
        l1.invalidate(key);
        l2.delete(key);
    }
}
```

**Invalidation challenge**: when a node updates Caffeine, other nodes' Caffeine caches are still stale. Solutions:
- Short L1 TTL (accept ~1 min stale window)
- Redis pub/sub invalidation broadcast: `publisher.publish("cache:invalidate", key)` → each node subscribes and evicts its L1 cache

---

## 8. Distributed Locking with Redis

### Naive SETNX approach (dangerous — don't use):
```java
// DANGEROUS: SETNX then EXPIRE are two separate commands — not atomic!
// If process crashes between SETNX and EXPIRE, lock never expires → deadlock
redis.setnx("lock:" + key, "1");      // acquire
redis.expire("lock:" + key, 30);      // set TTL — NOT ATOMIC with SETNX
```

### Correct atomic approach (SET NX PX):
```java
// Single atomic command — NX = only if not exists; PX = millisecond TTL
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent("lock:" + key, clientId, Duration.ofSeconds(30));

if (Boolean.TRUE.equals(acquired)) {
    try {
        doWork();
    } finally {
        // Release only if WE own the lock (check + delete must be atomic)
        String releaseScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        redisTemplate.execute(
            RedisScript.of(releaseScript, Long.class),
            List.of("lock:" + key), clientId   // ARGV[1] = unique clientId (UUID)
        );
    }
}
```

### Redisson — production-grade distributed lock:
```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.2</version>
</dependency>
```

```java
@Service
public class PaymentService {
    @Autowired
    private RedissonClient redisson;

    public void processPayment(String orderId) {
        RLock lock = redisson.getLock("payment:" + orderId);
        try {
            // tryLock(waitTime, leaseTime, unit) — avoids indefinite waiting
            boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!acquired) throw new ConcurrentModificationException("Order already processing");

            // critical section — automatically released after 30s (prevents deadlocks)
            debitAccount(orderId);
            updateOrderStatus(orderId);
            sendConfirmation(orderId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    // Fair lock variant — preserves order of lock requests (FIFO)
    public void processOrdered(String key) throws InterruptedException {
        RLock fairLock = redisson.getFairLock("ordered:" + key);
        fairLock.lock();
        try { doWork(); }
        finally { fairLock.unlock(); }
    }
}
```

---

## 9. Cache Stampede (Thundering Herd) Problem

When a popular cache key expires, many concurrent requests all miss and hammer the DB simultaneously:

```
t=0: "product:123" expires
t=0.001: 500 threads all get cache miss
t=0.001: 500 threads all query DB for product:123
          → DB overloaded, cascade failure
```

**Solutions:**

```java
// Solution 1: Mutex lock on miss — only one thread loads, others wait
@Cacheable("products")
public Product getProduct(Long id) {
    // Spring AOP + synchronized ConcurrentHashMap of locks per key
}

// Manual mutex implementation:
private final ConcurrentHashMap<Long, CompletableFuture<Product>> loadingFutures = new ConcurrentHashMap<>();

public Product getProductSafe(Long id) {
    Product cached = cache.getIfPresent(id);
    if (cached != null) return cached;

    CompletableFuture<Product> future = loadingFutures.computeIfAbsent(id,
        k -> CompletableFuture.supplyAsync(() -> {
            Product p = db.findById(k);
            cache.put(k, p);
            loadingFutures.remove(k);
            return p;
        })
    );
    return future.join(); // other threads join the same future
}

// Solution 2: Jitter — add random TTL offset to spread expiration times
Duration ttl = Duration.ofMinutes(10).plus(
    Duration.ofSeconds(ThreadLocalRandom.current().nextInt(60)) // ±60s jitter
);
redis.opsForValue().set(key, value, ttl);
```

---

## 10. Cache Invalidation Strategies

```java
// Event-driven invalidation via Kafka (strongly consistent)
@KafkaListener(topics = "product.updated")
public void onProductUpdated(ProductUpdatedEvent event) {
    cacheManager.getCache("products").evict(event.getProductId());
    log.info("Invalidated cache for product {}", event.getProductId());
}

// Versioned key pattern (avoids invalidation entirely — safe for CDNs)
// key: "product:{id}:v{version}" — increment version on every update
// Old cached data becomes orphaned (expires naturally) — no invalidation needed
// Useful for immutable-ish data (product catalog, config)

// Redis Keyspace Notifications (subscribe to TTL expiry events)
// application.yml: spring.data.redis.keyspace-notifications.enabled=true
@Bean
public RedisMessageListenerContainer keyExpiryContainer(RedisConnectionFactory factory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(
        (message, pattern) -> handleKeyExpiry(message.toString()),
        new PatternTopic("__keyevent@0__:expired")
    );
    return container;
}
```

---

## 11. Interview Q&A

**Q: What's the difference between `@Cacheable` and `@CachePut`?**  
`@Cacheable` is for reads — it checks the cache first. If the key is present, the method body is skipped entirely and the cached value is returned. If absent (cache miss), the method runs and the result is stored in the cache. `@CachePut` always executes the method and always updates the cache with the return value — it never bypasses the method. Use `@CachePut` on write/update operations where you want to keep the cache warm with fresh data after a save.

**Q: Why should you not use `SETNX` + `EXPIRE` as a distributed lock?**  
`SETNX` and `EXPIRE` are two separate Redis commands. If the process crashes or the connection drops between them, the lock key exists without a TTL and will never expire — deadlocking all future attempts to acquire the lock. The correct approach is the single atomic `SET key value NX PX milliseconds` command, which sets the key, only if it doesn't exist, with a TTL — all in one operation. Additionally, the value should be a unique client ID so that the release script (a Lua script) can verify ownership before deleting the lock.

**Q: What is the cache stampede problem and how do you solve it?**  
Cache stampede (thundering herd) occurs when many concurrent requests simultaneously encounter a cache miss on the same key (typically when a popular key expires). All threads go to the DB simultaneously, overwhelming it. Solutions include: (1) Mutex-on-miss — first thread acquires a lock and loads the data; other threads wait for the same future/result. (2) TTL jitter — add random offset to TTLs so keys don't expire simultaneously. (3) Staggered refresh — use Caffeine's `refreshAfterWrite` or probabilistic early expiration to refresh before the key fully expires. (4) Redis locking — use `SETNX` to ensure only one thread does the DB load.

**Q: When would you use a Redis Hash vs a String for caching a User object?**  
Use a **String** (JSON-serialized whole object) when you typically need all fields together and updates are infrequent or replace the whole object. Use a **Hash** when you frequently update only specific fields of an object — with a Hash, you can `HSET user:42 lastLoginAt "2024-01-15"` without reading and re-serializing the entire object. This reduces network payload and eliminates race conditions where two concurrent writers serializing the full object overwrite each other's changes. Hashes are also more memory-efficient in Redis when the number of fields is small (Redis uses ziplist encoding internally for small hashes).
