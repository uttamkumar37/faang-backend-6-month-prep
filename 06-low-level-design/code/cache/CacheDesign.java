import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class CacheDesign {
    record CacheEntry<V>(V value, Instant expiresAt) {}

    static final class LruCache<K, V> {
        private final int capacity;
        private final Clock clock;
        private final LinkedHashMap<K, CacheEntry<V>> store;

        LruCache(int capacity, Clock clock) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
            this.capacity = capacity;
            this.clock = clock;
            this.store = new LinkedHashMap<>(16, 0.75f, true);
        }

        synchronized Optional<V> get(K key) {
            CacheEntry<V> entry = store.get(key);
            if (entry == null) return Optional.empty();
            if (clock.instant().isAfter(entry.expiresAt())) {
                store.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.value());
        }

        synchronized void put(K key, V value, Duration ttl) {
            if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
            store.put(key, new CacheEntry<>(value, clock.instant().plus(ttl)));
            evictIfNeeded();
        }

        synchronized void delete(K key) {
            store.remove(key);
        }

        private void evictIfNeeded() {
            while (store.size() > capacity) {
                K eldest = store.entrySet().iterator().next().getKey();
                store.remove(eldest);
            }
        }

        synchronized Map<K, CacheEntry<V>> snapshot() {
            return Map.copyOf(store);
        }
    }

    // Test ideas: TTL expiry, LRU order after get, update existing key, capacity one, concurrent get/put.
}
