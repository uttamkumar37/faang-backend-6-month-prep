package com.faangprep.javabackend.collections;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for LRUCacheImpl — verifies all three implementations behave correctly.
 */
class LRUCacheImplTest {

    // ── SimpleLRUCache (LinkedHashMap-based) ─────────────────────────────────

    @Test
    void simpleLRU_basicPutAndGet() {
        var cache = new LRUCacheImpl.SimpleLRUCache<Integer, String>(3);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.put(3, "c");
        assertThat(cache.get(1)).isEqualTo("a");
        assertThat(cache.get(2)).isEqualTo("b");
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    void simpleLRU_evictsLeastRecentlyUsed() {
        var cache = new LRUCacheImpl.SimpleLRUCache<Integer, String>(2);
        cache.put(1, "a");
        cache.put(2, "b");
        cache.get(1);       // access 1 → 1 is now MRU, 2 is LRU
        cache.put(3, "c");  // capacity exceeded — 2 should be evicted
        assertThat(cache.contains(2)).isFalse();
        assertThat(cache.contains(1)).isTrue();
        assertThat(cache.contains(3)).isTrue();
    }

    @Test
    void simpleLRU_updateKeepsSizeConstant() {
        var cache = new LRUCacheImpl.SimpleLRUCache<Integer, String>(2);
        cache.put(1, "a");
        cache.put(1, "updated");
        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get(1)).isEqualTo("updated");
    }

    // ── LRUCache (manual DLL + HashMap — interview style) ────────────────────

    @Test
    void manualLRU_getMissingKeyReturnsMinusOne() {
        var cache = new LRUCacheImpl.LRUCache(2);
        assertThat(cache.get(99)).isEqualTo(-1);
    }

    @Test
    void manualLRU_putAndGet() {
        var cache = new LRUCacheImpl.LRUCache(2);
        cache.put(1, 10);
        cache.put(2, 20);
        assertThat(cache.get(1)).isEqualTo(10);
        assertThat(cache.get(2)).isEqualTo(20);
    }

    @Test
    void manualLRU_evictsLRUOnCapacityExceeded() {
        // LeetCode 146 example
        var cache = new LRUCacheImpl.LRUCache(2);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.get(1);        // access 1 → 1 is now MRU, 2 is LRU
        cache.put(3, 3);     // evicts key 2
        assertThat(cache.get(2)).isEqualTo(-1);  // 2 evicted
        cache.put(4, 4);     // evicts key 1 (1 is now LRU: 3 was accessed during put, then 4)
        assertThat(cache.get(1)).isEqualTo(-1);  // 1 evicted
        assertThat(cache.get(3)).isEqualTo(3);
        assertThat(cache.get(4)).isEqualTo(4);
    }

    @Test
    void manualLRU_updateExistingKeyDoesNotEvict() {
        var cache = new LRUCacheImpl.LRUCache(2);
        cache.put(1, 1);
        cache.put(2, 2);
        cache.put(1, 100);   // update key 1, no new key added
        cache.put(3, 3);     // should evict 2, not 1
        assertThat(cache.get(1)).isEqualTo(100);
        assertThat(cache.get(2)).isEqualTo(-1);
        assertThat(cache.get(3)).isEqualTo(3);
    }

    @Test
    void manualLRU_capacityOne() {
        var cache = new LRUCacheImpl.LRUCache(1);
        cache.put(1, 10);
        cache.put(2, 20);    // evicts 1
        assertThat(cache.get(1)).isEqualTo(-1);
        assertThat(cache.get(2)).isEqualTo(20);
    }
}
