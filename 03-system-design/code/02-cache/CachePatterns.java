package systemdesign.cache;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/**
 * Cache Patterns in Java
 * Covers: LRU, TTL cache, cache-aside, multi-level cache, stampede prevention.
 */
public class CachePatterns {

    // ─────────────────────────────────────────────
    // 1. LRU CACHE WITH TTL  (production—grade)
    // Thread-safe, TTL per entry, O(1) get/put/evict
    // ─────────────────────────────────────────────

    static class LruTtlCache<K, V> {
        private static class Entry<V> {
            final V value;
            final long expiryNanos;
            Entry(V value, long ttlNanos) {
                this.value = value;
                this.expiryNanos = System.nanoTime() + ttlNanos;
            }
            boolean isExpired() { return System.nanoTime() > expiryNanos; }
        }

        private final int maxSize;
        private final LinkedHashMap<K, Entry<V>> cache;
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();

        public LruTtlCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new LinkedHashMap<>(16, 0.75f, true) {  // accessOrder=true
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                    return size() > maxSize;
                }
            };
        }

        public synchronized Optional<V> get(K key) {
            Entry<V> entry = cache.get(key);
            if (entry == null) { misses.incrementAndGet(); return Optional.empty(); }
            if (entry.isExpired()) { cache.remove(key); misses.incrementAndGet(); return Optional.empty(); }
            hits.incrementAndGet();
            return Optional.of(entry.value);
        }

        public synchronized void put(K key, V value, Duration ttl) {
            cache.put(key, new Entry<>(value, ttl.toNanos()));
        }

        public synchronized void invalidate(K key) { cache.remove(key); }

        public double hitRate() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0 : (double) hits.get() / total;
        }

        public String stats() {
            return String.format("size=%d hits=%d misses=%d hitRate=%.2f%%",
                cache.size(), hits.get(), misses.get(), hitRate() * 100);
        }
    }

    // ─────────────────────────────────────────────
    // 2. CACHE-ASIDE PATTERN
    // Application manages cache population and invalidation.
    // ─────────────────────────────────────────────

    interface UserRepository {
        User findById(long id);
        void save(User user);
    }

    record User(long id, String name, String email) {}

    static class CacheAsideUserService {
        private final LruTtlCache<Long, User> cache;
        private final UserRepository repo;

        CacheAsideUserService(UserRepository repo) {
            this.cache = new LruTtlCache<>(1000);
            this.repo = repo;
        }

        // Read: cache-aside (check cache → miss → load DB → populate cache)
        public User getUser(long userId) {
            return cache.get(userId)
                .orElseGet(() -> {
                    User user = repo.findById(userId);
                    if (user != null) {
                        cache.put(userId, user, Duration.ofMinutes(5));
                    }
                    return user;
                });
        }

        // Write: write to DB, then INVALIDATE (don't update) cache
        // Invalidate avoids race condition: write-then-read ordering vs reading stale value.
        public void updateUser(User user) {
            repo.save(user);
            cache.invalidate(user.id());  // Next read will reload from DB
        }
    }

    // ─────────────────────────────────────────────
    // 3. CACHE STAMPEDE PREVENTION
    // When popular key expires → many concurrent DB queries.
    // ─────────────────────────────────────────────

    static class StampedeAwareCache<K, V> {
        private final LruTtlCache<K, V> cache;
        private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

        StampedeAwareCache(int maxSize) {
            this.cache = new LruTtlCache<>(maxSize);
        }

        // Strategy: "promise collapsing" — all concurrent misses share one DB call
        public CompletableFuture<V> getAsync(K key, Function<K, V> loader, Duration ttl) {
            Optional<V> cached = cache.get(key);
            if (cached.isPresent()) {
                return CompletableFuture.completedFuture(cached.get());
            }

            // Check if a load is already in flight for this key
            CompletableFuture<V> promise = new CompletableFuture<>();
            CompletableFuture<V> existing = inFlight.putIfAbsent(key, promise);

            if (existing != null) {
                // Another thread already started loading → wait for its result
                return existing;
            }

            // We're the "winner" — we load from DB
            CompletableFuture.runAsync(() -> {
                try {
                    V value = loader.apply(key);
                    cache.put(key, value, ttl);
                    promise.complete(value);
                } catch (Exception e) {
                    promise.completeExceptionally(e);
                } finally {
                    inFlight.remove(key);
                }
            });

            return promise;
        }
    }

    // ─────────────────────────────────────────────
    // 4. MULTI-LEVEL CACHE (L1 local + L2 distributed)
    // L1: fast in-process (Caffeine-style), short TTL
    // L2: Redis-like shared cache, longer TTL
    // ─────────────────────────────────────────────

    interface RemoteCache<K, V> {
        Optional<V> get(K key);
        void put(K key, V value, Duration ttl);
    }

    static class MultiLevelCache<K, V> {
        private final LruTtlCache<K, V> l1;    // in-process, short TTL
        private final RemoteCache<K, V> l2;    // Redis, longer TTL
        private final Duration l1Ttl;
        private final Duration l2Ttl;

        MultiLevelCache(int l1MaxSize, RemoteCache<K, V> l2, Duration l1Ttl, Duration l2Ttl) {
            this.l1 = new LruTtlCache<>(l1MaxSize);
            this.l2 = l2;
            this.l1Ttl = l1Ttl;
            this.l2Ttl = l2Ttl;
        }

        public Optional<V> get(K key) {
            // Check L1 first (no network hop)
            Optional<V> l1Val = l1.get(key);
            if (l1Val.isPresent()) return l1Val;

            // L1 miss → check L2 (Redis)
            Optional<V> l2Val = l2.get(key);
            if (l2Val.isPresent()) {
                l1.put(key, l2Val.get(), l1Ttl);  // backfill L1
                return l2Val;
            }

            return Optional.empty();  // total cache miss
        }

        public void put(K key, V value) {
            l1.put(key, value, l1Ttl);
            l2.put(key, value, l2Ttl);
        }

        // On update: invalidate both levels
        public void invalidate(K key) {
            l1.invalidate(key);
            // In multi-node: broadcast L1 invalidation via pub/sub
            // Redis: DEL key + publish "cache:invalidate" channel
        }
    }

    // ─────────────────────────────────────────────
    // 5. WRITE-BEHIND (WRITE BACK) CACHE
    // Write to cache immediately; batch flush to DB asynchronously.
    // ─────────────────────────────────────────────

    static class WriteBehindCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        private final Queue<Map.Entry<K, V>> dirtyQueue = new ConcurrentLinkedQueue<>();
        private final Function<Map.Entry<K, V>, Void> dbWriter;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        WriteBehindCache(Function<Map.Entry<K, V>, Void> dbWriter, long flushIntervalMs) {
            this.dbWriter = dbWriter;
            scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs,
                TimeUnit.MILLISECONDS);
        }

        public void put(K key, V value) {
            cache.put(key, value);
            dirtyQueue.add(Map.entry(key, value));  // mark dirty
        }

        public Optional<V> get(K key) {
            return Optional.ofNullable(cache.get(key));
        }

        private void flush() {
            List<Map.Entry<K, V>> batch = new ArrayList<>();
            Map.Entry<K, V> entry;
            while ((entry = dirtyQueue.poll()) != null) batch.add(entry);

            if (!batch.isEmpty()) {
                batch.forEach(dbWriter::apply);
                System.out.println("Flushed " + batch.size() + " dirty entries to DB");
            }
        }

        public void close() { scheduler.shutdown(); }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== LRU TTL Cache ===");
        LruTtlCache<String, String> lruCache = new LruTtlCache<>(3);
        lruCache.put("a", "apple", Duration.ofMillis(500));
        lruCache.put("b", "banana", Duration.ofSeconds(10));
        lruCache.put("c", "cherry", Duration.ofSeconds(10));

        System.out.println("get(a): " + lruCache.get("a"));  // present
        lruCache.put("d", "dragon", Duration.ofSeconds(10));   // evicts LRU
        System.out.println("cache stats: " + lruCache.stats());

        Thread.sleep(600);
        System.out.println("get(a) after 600ms TTL expiry: " + lruCache.get("a")); // expired

        System.out.println("\n=== Cache-Aside with Simulated Repo ===");
        UserRepository mockRepo = new UserRepository() {
            Map<Long, User> db = Map.of(1L, new User(1L, "Alice", "alice@example.com"));
            @Override public User findById(long id) {
                System.out.println("  [DB HIT] Loading user " + id + " from database");
                return db.get(id);
            }
            @Override public void save(User user) {
                System.out.println("  [DB WRITE] Saving user " + user.id());
            }
        };
        CacheAsideUserService userService = new CacheAsideUserService(mockRepo);
        System.out.println("First load:  " + userService.getUser(1L));  // DB hit
        System.out.println("Second load: " + userService.getUser(1L));  // cache hit
        userService.updateUser(new User(1L, "Alice Updated", "alice@example.com"));
        System.out.println("After update: " + userService.getUser(1L)); // DB hit again

        System.out.println("\n=== Write-Behind Cache ===");
        WriteBehindCache<Long, String> writeBehind = new WriteBehindCache<>(
            entry -> { System.out.println("  DB sync: " + entry.getKey() + "=" + entry.getValue()); return null; },
            200  // flush every 200ms
        );
        writeBehind.put(1L, "v1");
        writeBehind.put(2L, "v2");
        writeBehind.put(3L, "v3");
        System.out.println("Immediate read (no DB trip): " + writeBehind.get(1L));
        Thread.sleep(300);  // let flush run
        writeBehind.close();
    }
}
