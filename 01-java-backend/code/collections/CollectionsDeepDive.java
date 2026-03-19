
package com.faangprep.javabackend.collections;

import java.util.*;
import java.util.concurrent.*;

/**
 * Collections Deep Dive — internals and advanced patterns
 *
 * Topics:
 *  1. HashMap — custom hashCode/equals, pre-sizing, collision behavior
 *  2. ConcurrentHashMap — atomic compound operations
 *  3. TreeMap — range queries, floor/ceiling
 *  4. PriorityQueue — top-K, median finder
 *  5. ArrayDeque — monotonic stack/queue
 *  6. LinkedHashMap — LRU with access-order
 *  7. CopyOnWriteArrayList — snapshot iteration
 *  8. Performance comparison: HashMap vs TreeMap
 */
public class CollectionsDeepDive {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. CORRECT hashCode + equals on a custom key
    // ─────────────────────────────────────────────────────────────────────────

    // Mutable key — dangerous!
    static class BadKey {
        int value;
        BadKey(int v) { this.value = v; }
        @Override public int hashCode() { return value; }
        @Override public boolean equals(Object o) { return o instanceof BadKey b && b.value == value; }
    }

    static void demonstrateMutableKeyProblem() {
        Map<BadKey, String> map = new HashMap<>();
        BadKey key = new BadKey(42);
        map.put(key, "hello");

        key.value = 99;  // mutate key AFTER insertion!
        System.out.println("map.get(key) after mutation: " + map.get(key)); // null — unfindable!

        // The key is still in the map, but mapped to wrong bucket now
        System.out.println("map size: " + map.size()); // still 1
    }

    // Correct immutable key (record is ideal)
    record ProductKey(String category, String sku) {
        // Records auto-generate hashCode/equals based on all components
    }

    static void correctKeyDemo() {
        Map<ProductKey, Double> prices = new HashMap<>(100); // pre-sized for 100 entries
        prices.put(new ProductKey("electronics", "iphone-15"), 999.99);
        prices.put(new ProductKey("electronics", "macbook-pro"), 2499.99);
        prices.put(new ProductKey("books", "clean-code"), 35.99);

        ProductKey key = new ProductKey("electronics", "macbook-pro");
        System.out.println("Price: " + prices.get(key));  // 2499.99
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. ConcurrentHashMap — atomic compound operations
    // ─────────────────────────────────────────────────────────────────────────

    static void concurrentHashMapDemo() throws InterruptedException {
        ConcurrentHashMap<String, List<String>> userEvents = new ConcurrentHashMap<>();

        // computeIfAbsent is atomic: list is created exactly once per key
        Runnable addEvent = () -> {
            String userId = "user-" + ThreadLocalRandom.current().nextInt(3);
            String eventId = "event-" + ThreadLocalRandom.current().nextInt(1000);
            userEvents.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(eventId);
        };

        // Run 200 concurrent tasks
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) futures.add(pool.submit(addEvent));
        for (Future<?> f : futures) { try { f.get(); } catch (Exception e) { /* ignore */ }}
        pool.shutdown();

        System.out.println("\nEvent counts per user:");
        userEvents.forEach((user, events) ->
                System.out.printf("  %s: %d events%n", user, events.size()));

        // merge: atomic increment per key
        ConcurrentHashMap<String, Integer> wordCount = new ConcurrentHashMap<>();
        String[] words = {"hello", "world", "hello", "java", "world", "hello"};
        for (String word : words) {
            wordCount.merge(word, 1, Integer::sum);
        }
        System.out.println("Word counts: " + wordCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. TreeMap — leader board, range queries
    // ─────────────────────────────────────────────────────────────────────────

    static void treeMapDemo() {
        // Score leaderboard — sorted by score descending
        TreeMap<Integer, String> leaderboard = new TreeMap<>(Comparator.reverseOrder());
        leaderboard.put(920, "Alice");
        leaderboard.put(875, "Bob");
        leaderboard.put(1050, "Charlie");
        leaderboard.put(790, "Diana");

        System.out.println("\nLeaderboard: " + leaderboard);
        System.out.println("Top player: " + leaderboard.firstEntry());
        System.out.println("Bottom player: " + leaderboard.lastEntry());

        // Range query: players with score between 850 and 950
        System.out.println("Scores 850-950: " + leaderboard.subMap(950, true, 850, true));

        // Floor/ceiling: nearest values
        System.out.println("Closest score >= 880: " + leaderboard.ceilingKey(880));
        System.out.println("Closest score <= 880: " + leaderboard.floorKey(880));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PriorityQueue — Top-K + Median Finder
    // ─────────────────────────────────────────────────────────────────────────

    // Find top-K largest elements in O(n log k) time, O(k) space
    static List<Integer> topK(int[] nums, int k) {
        // Min-heap of size k: always evicts the smallest → keeps the k largest
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(k);
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) minHeap.poll(); // evict smallest
        }
        List<Integer> result = new ArrayList<>(minHeap);
        result.sort(Collections.reverseOrder());
        return result;
    }

    // Running median — two heaps: max-heap for lower half, min-heap for upper half
    static class MedianFinder {
        private final PriorityQueue<Integer> lower = new PriorityQueue<>(Comparator.reverseOrder());
        private final PriorityQueue<Integer> upper = new PriorityQueue<>();

        void addNum(int num) {
            lower.offer(num);
            upper.offer(lower.poll());         // balance: upper always gets the largest from lower
            if (lower.size() < upper.size()) {
                lower.offer(upper.poll());     // keep lower.size() >= upper.size()
            }
        }

        double getMedian() {
            if (lower.size() > upper.size()) return lower.peek();
            return (lower.peek() + upper.peek()) / 2.0;
        }
    }

    static void heapDemo() {
        System.out.println("\nTop-3 of [3,1,9,7,2,8,4]: " + topK(new int[]{3, 1, 9, 7, 2, 8, 4}, 3));

        MedianFinder mf = new MedianFinder();
        int[] stream = {5, 3, 8, 1, 9, 2};
        for (int n : stream) {
            mf.addNum(n);
            System.out.printf("  Add %d → median: %.1f%n", n, mf.getMedian());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. ArrayDeque — monotonic stack (next greater element)
    // ─────────────────────────────────────────────────────────────────────────

    static int[] nextGreaterElement(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>(); // stores indices

        for (int i = 0; i < n; i++) {
            // Pop all indices whose value is less than nums[i]
            while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
                result[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. LinkedHashMap — LRU Cache
    // ─────────────────────────────────────────────────────────────────────────

    static class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        LruCache(int maxSize) {
            super(maxSize, 0.75f, true); // accessOrder = true
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    static void lruDemo() {
        System.out.println("\nLRU Cache demo:");
        LruCache<Integer, String> lru = new LruCache<>(3);
        lru.put(1, "one");
        lru.put(2, "two");
        lru.put(3, "three");
        lru.get(1);          // access 1 — moves to most-recently-used
        lru.put(4, "four");  // evicts least-recently-used (2)
        System.out.println("Keys (LRU order): " + lru.keySet());
        System.out.println("Contains key 2: " + lru.containsKey(2)); // false — evicted
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. CopyOnWriteArrayList — event listener registry
    // ─────────────────────────────────────────────────────────────────────────

    interface EventListener { void onEvent(String message); }

    static class EventBus {
        // Thread-safe for read-heavy register/unregister patterns
        // Each write operation creates a new array copy — expensive for writes, free for reads
        private final CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

        public void register(EventListener l) { listeners.add(l); }
        public void unregister(EventListener l) { listeners.remove(l); }

        public void publish(String event) {
            // Snapshot iteration — safe even if listeners modify during iteration
            for (EventListener l : listeners) {
                l.onEvent(event);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("═══ 1. Mutable Key Problem ═══════════════════");
        demonstrateMutableKeyProblem();
        correctKeyDemo();

        System.out.println("\n═══ 2. ConcurrentHashMap ════════════════════");
        concurrentHashMapDemo();

        System.out.println("\n═══ 3. TreeMap Leaderboard ══════════════════");
        treeMapDemo();

        System.out.println("\n═══ 4. Heap / TopK / Median ═════════════════");
        heapDemo();

        System.out.println("\n═══ 5. Monotonic Stack ══════════════════════");
        int[] input = {2, 1, 2, 4, 3, 5};
        System.out.println("Next greater: " + Arrays.toString(nextGreaterElement(input)));

        System.out.println("\n═══ 6. LRU Cache ════════════════════════════");
        lruDemo();

        System.out.println("\n═══ 7. Event Bus with COWAL ═════════════════");
        EventBus bus = new EventBus();
        bus.register(msg -> System.out.println("  Listener A: " + msg));
        bus.register(msg -> System.out.println("  Listener B: " + msg));
        bus.publish("OrderCreated");
        bus.publish("PaymentProcessed");
    }
}
