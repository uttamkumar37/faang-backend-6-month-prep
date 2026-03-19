
package com.faangprep.javabackend.collections;

import java.util.*;
import java.util.concurrent.locks.*;

/**
 * LRU Cache — 3 implementations from simple to thread-safe + full-featured
 *
 *  Impl 1: LinkedHashMap-based (simplest, not thread-safe)
 *  Impl 2: Manual doubly-linked list + HashMap (interview-style)
 *  Impl 3: Thread-safe with ReadWriteLock + stats
 */
public class LRUCacheImpl {

    // ─────────────────────────────────────────────────────────────────────────
    // IMPL 1 — LinkedHashMap (cleanest for production code)
    // ─────────────────────────────────────────────────────────────────────────

    static class SimpleLRUCache<K, V> {
        private final LinkedHashMap<K, V> cache;

        SimpleLRUCache(int capacity) {
            // accessOrder=true: get() moves entry to most-recently-used (tail)
            this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > capacity;
                }
            };
        }

        public V get(K key) {
            return cache.getOrDefault(key, null);
        }

        public void put(K key, V value) {
            cache.put(key, value);
        }

        public int size() { return cache.size(); }
        public boolean contains(K key) { return cache.containsKey(key); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPL 2 — Manual DLL + HashMap (classic interview answer)
    // ─────────────────────────────────────────────────────────────────────────

    static class LRUCache {
        private final int capacity;
        private int size;
        private final Map<Integer, Node> map;
        private final Node head; // MRU side dummy
        private final Node tail; // LRU side dummy

        LRUCache(int capacity) {
            this.capacity = capacity;
            this.map = new HashMap<>(capacity * 2);
            this.head = new Node(0, 0);
            this.tail = new Node(0, 0);
            head.next = tail;
            tail.prev = head;
        }

        public int get(int key) {
            Node node = map.get(key);
            if (node == null) return -1;
            moveToFront(node); // mark recently used
            return node.value;
        }

        public void put(int key, int value) {
            Node node = map.get(key);
            if (node != null) {
                node.value = value;
                moveToFront(node);
            } else {
                Node newNode = new Node(key, value);
                map.put(key, newNode);
                addToFront(newNode);
                size++;
                if (size > capacity) {
                    Node lru = removeLast();
                    map.remove(lru.key);
                    size--;
                }
            }
        }

        private void addToFront(Node node) {
            node.prev = head;
            node.next = head.next;
            head.next.prev = node;
            head.next = node;
        }

        private void remove(Node node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            node.prev = null;
            node.next = null;
        }

        private void moveToFront(Node node) {
            remove(node);
            addToFront(node);
        }

        private Node removeLast() {
            Node lru = tail.prev;
            remove(lru);
            return lru;
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder("[MRU→LRU]: ");
            Node cur = head.next;
            while (cur != tail) {
                sb.append('(').append(cur.key).append(':').append(cur.value).append(") ");
                cur = cur.next;
            }
            return sb.toString();
        }

        private static class Node {
            int key, value;
            Node prev, next;
            Node(int k, int v) { key = k; value = v; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMPL 3 — Thread-safe LRU with stats
    // ─────────────────────────────────────────────────────────────────────────

    static class ThreadSafeLRUCache<K, V> {
        private final SimpleLRUCache<K, V> inner;
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Lock readLock  = rwLock.readLock();
        private final Lock writeLock = rwLock.writeLock();
        private long hits = 0;
        private long misses = 0;

        ThreadSafeLRUCache(int capacity) {
            this.inner = new SimpleLRUCache<>(capacity);
        }

        public V get(K key) {
            // get() moves node to MRU — it modifies state, so needs write lock
            writeLock.lock();
            try {
                V value = inner.get(key);
                if (value != null) hits++; else misses++;
                return value;
            } finally {
                writeLock.unlock();
            }
        }

        public void put(K key, V value) {
            writeLock.lock();
            try {
                inner.put(key, value);
            } finally {
                writeLock.unlock();
            }
        }

        public boolean contains(K key) {
            readLock.lock(); // containsKey doesn't modify LRU order
            try {
                return inner.contains(key);
            } finally {
                readLock.unlock();
            }
        }

        public void printStats() {
            long total = hits + misses;
            System.out.printf("Cache stats: size=%d  hits=%d  misses=%d  hitRate=%.1f%%%n",
                    inner.size(), hits, misses, total > 0 ? hits * 100.0 / total : 0.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TESTS
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("═══ Impl 1: SimpleLRUCache ═══════════════════");
        SimpleLRUCache<Integer, String> simple = new SimpleLRUCache<>(3);
        simple.put(1, "one");
        simple.put(2, "two");
        simple.put(3, "three");
        simple.get(1);          // access 1 → MRU
        simple.put(4, "four");  // evicts LRU = 2
        System.out.println("Contains 2: " + simple.contains(2)); // false
        System.out.println("Contains 1: " + simple.contains(1)); // true

        System.out.println("\n═══ Impl 2: Manual DLL LRUCache ═════════════");
        LRUCache lru = new LRUCache(3);
        lru.put(1, 1); System.out.println(lru);
        lru.put(2, 2); System.out.println(lru);
        lru.put(3, 3); System.out.println(lru);
        System.out.println("get(1) = " + lru.get(1)); // 1 — moves to front
        System.out.println(lru);
        lru.put(4, 4); // evicts 2 (LRU)
        System.out.println("After put(4,4): " + lru);
        System.out.println("get(2) = " + lru.get(2)); // -1 — evicted

        System.out.println("\n═══ Impl 3: Thread-Safe LRU ══════════════════");
        ThreadSafeLRUCache<String, String> tsCache = new ThreadSafeLRUCache<>(100);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
        threads.add(Thread.ofVirtual().start(() -> {
                for (int j = 0; j < 50; j++) {
                    String key = "key-" + (j % 15); // more keys than capacity — evictions happen
                    String val = tsCache.get(key);
                    if (val == null) tsCache.put(key, "value-" + key);
                }
            }));
        }
        for (Thread t : threads) t.join();
        tsCache.printStats();
    }
}
