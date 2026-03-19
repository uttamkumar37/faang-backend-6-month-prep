
package com.faangprep.javabackend.performance;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

/**
 * Java Performance Tuning — measurable examples
 *
 * Topics:
 *  1. String building — bad vs good allocation
 *  2. Stream vs loop — when each wins
 *  3. Collection pre-sizing
 *  4. Object pooling pattern
 *  5. Lazy initialization patterns
 *  6. Batch processing with chunking
 *  7. Benchmarking harness (poor-man's JMH)
 */
public class PerformanceTuning {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. STRING BUILDING
    // ─────────────────────────────────────────────────────────────────────────

    // BAD: O(n²) — creates a new String on each iteration
    static String buildBad(List<String> parts) {
        String result = "";
        for (String part : parts) {
            result += part + ",";
        }
        return result;
    }

    // GOOD: O(n) — pre-sized, single allocation
    static String buildGood(List<String> parts) {
        StringBuilder sb = new StringBuilder(parts.size() * 20);
        for (String part : parts) {
            sb.append(part).append(',');
        }
        if (!sb.isEmpty()) sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    // BEST: String.join — JDK optimized, cleanest
    static String buildBest(List<String> parts) {
        return String.join(",", parts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. STREAM vs LOOP
    // ─────────────────────────────────────────────────────────────────────────

    // Streams win on: readability, functional composition
    // Loops win on: primitive operations, early exit, mutation in body

    static long sumWithStream(int[] nums) {
        return Arrays.stream(nums).asLongStream().sum();
    }

    static long sumWithLoop(int[] nums) {
        long sum = 0;
        for (int n : nums) sum += n; // no boxing, no lambda overhead
        return sum;
    }

    // For primitive arrays, IntStream/LongStream avoids boxing
    static OptionalInt maxWithStream(int[] nums) {
        return Arrays.stream(nums).max();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. COLLECTION PRE-SIZING
    // ─────────────────────────────────────────────────────────────────────────

    static void collectionSizingDemo() {
        int n = 100_000;

        // Without initial capacity: ArrayList resizes multiple times (copies array)
        long start = System.nanoTime();
        List<Integer> noPresize = new ArrayList<>();
        for (int i = 0; i < n; i++) noPresize.add(i);
        long noPre = System.nanoTime() - start;

        // With initial capacity: single allocation, no resize
        start = System.nanoTime();
        List<Integer> presize = new ArrayList<>(n);
        for (int i = 0; i < n; i++) presize.add(i);
        long pre = System.nanoTime() - start;

        System.out.printf("ArrayList no pre-size: %,d µs  with pre-size: %,d µs  speedup: %.1fx%n",
                noPre / 1000, pre / 1000, (double) noPre / pre);

        // HashMap: default capacity 16, load factor 0.75
        // Pre-size to avoid rehashing:
        int expected = 1000;
        Map<String, String> map = new HashMap<>((int)(expected / 0.75) + 1);
        // No rehashing will occur up to 'expected' entries
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. OBJECT POOLING
    // ─────────────────────────────────────────────────────────────────────────

    // For expensive-to-create objects: DB connections, HTTP clients, parsers
    static class SimpleObjectPool<T> {
        private final BlockingQueue<T> pool;
        private final java.util.function.Supplier<T> factory;
        private final AtomicInteger created = new AtomicInteger(0);
        private final int maxSize;

        SimpleObjectPool(int maxSize, java.util.function.Supplier<T> factory) {
            this.maxSize = maxSize;
            this.factory = factory;
            this.pool = new ArrayBlockingQueue<>(maxSize);
        }

        public T borrow() throws InterruptedException {
            T obj = pool.poll(); // try non-blocking first
            if (obj != null) return obj;

            // Try to create a new one
            if (created.get() < maxSize) {
                int c = created.incrementAndGet();
                if (c <= maxSize) {
                    return factory.get();
                }
                created.decrementAndGet();
            }

            // Pool exhausted — wait up to 2 seconds
            obj = pool.poll(2, TimeUnit.SECONDS);
            if (obj == null) throw new RuntimeException("Pool exhausted");
            return obj;
        }

        public void returnToPool(T obj) {
            if (!pool.offer(obj)) {
                // Pool full — discard (this shouldn't happen in a well-sized pool)
                created.decrementAndGet();
            }
        }

        public int available() { return pool.size(); }
        public int totalCreated() { return created.get(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. LAZY INITIALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    // Holder pattern — JVM guarantees thread-safe single initialization
    static class ExpensiveService {
        private ExpensiveService() {
            System.out.println("ExpensiveService initialized");
        }

        private static final class Holder {
            static final ExpensiveService INSTANCE = new ExpensiveService();
        }

        public static ExpensiveService getInstance() {
            return Holder.INSTANCE;  // JVM initializes Holder class lazily on first access
        }

        public String process(String input) { return input.toUpperCase(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. BATCH PROCESSING WITH CHUNKING
    // ─────────────────────────────────────────────────────────────────────────

    // Process large collections in chunks to limit memory pressure
    static <T> void processBatched(List<T> items, int chunkSize,
                                    java.util.function.Consumer<List<T>> processor) {
        for (int i = 0; i < items.size(); i += chunkSize) {
            List<T> chunk = items.subList(i, Math.min(i + chunkSize, items.size()));
            processor.accept(chunk);
            System.out.printf("  Processed chunk %d/%d (%d items)%n",
                    (i / chunkSize) + 1, (items.size() + chunkSize - 1) / chunkSize, chunk.size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. SIMPLE BENCHMARK HARNESS
    // ─────────────────────────────────────────────────────────────────────────

    // Not a substitute for JMH, but good for quick comparisons
    static long benchmark(String name, int iterations, Runnable task) {
        // Warm up: let JIT compile the hot path
        for (int i = 0; i < 1000; i++) task.run();

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) task.run();
        long total = System.nanoTime() - start;
        long avgNs = total / iterations;
        System.out.printf("  %-40s %,7d ns/op  total=%,d ms%n",
                name, avgNs, total / 1_000_000);
        return avgNs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("═══ 1. String Building Benchmark ════════════");
        List<String> parts = IntStream.range(0, 1000).mapToObj(i -> "item-" + i).toList();
        benchmark("String += concatenation", 100, () -> buildBad(parts));
        benchmark("StringBuilder", 100, () -> buildGood(parts));
        benchmark("String.join", 100, () -> buildBest(parts));

        System.out.println("\n═══ 2. Stream vs Loop ════════════════════════");
        int[] nums = IntStream.range(0, 100_000).toArray();
        benchmark("Arrays.stream().sum()", 1000, () -> sumWithStream(nums));
        benchmark("for loop sum", 1000, () -> sumWithLoop(nums));

        System.out.println("\n═══ 3. Collection Pre-sizing ════════════════");
        collectionSizingDemo();

        System.out.println("\n═══ 4. Object Pooling ═══════════════════════");
        SimpleObjectPool<StringBuilder> sbPool = new SimpleObjectPool<>(
                10, () -> new StringBuilder(1024));
        StringBuilder sb = sbPool.borrow();
        System.out.println("Borrowed from pool. Available: " + sbPool.available());
        sb.setLength(0); // reset before return!
        sbPool.returnToPool(sb);
        System.out.println("Returned to pool. Available: " + sbPool.available());

        System.out.println("\n═══ 5. Lazy Initialization ══════════════════");
        System.out.println("Before first access — service not initialized yet");
        String result = ExpensiveService.getInstance().process("hello");
        System.out.println("Result: " + result);

        System.out.println("\n═══ 6. Batched Processing ════════════════════");
        List<Integer> largeList = IntStream.range(0, 250).boxed().toList();
        processBatched(largeList, 100, chunk -> {
            // In real code: batch INSERT, batch API call, etc.
            chunk.stream().mapToLong(Long::valueOf).sum(); // just consume
        });
    }
}
