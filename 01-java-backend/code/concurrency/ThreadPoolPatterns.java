package com.faangprep.javabackend.concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ThreadPoolPatterns — thread pool sizing, configuration, and execution strategies.
 *
 * Topics covered:
 *   1. ThreadPoolExecutor parameters explained
 *   2. CPU-bound vs I/O-bound sizing formulas
 *   3. Rejection policies and their trade-offs
 *   4. Bounded queues for back-pressure
 *   5. ForkJoinPool for divide-and-conquer
 *   6. Scheduled tasks with ScheduledExecutorService
 *   7. Work-stealing pool patterns
 *   8. Custom ThreadFactory for naming and monitoring
 *   9. Graceful shutdown
 *
 * FAANG interview tip: always explain WHY you chose a specific pool/queue size.
 * "It depends" is acceptable if followed by the formula and environmental factors.
 */
public class ThreadPoolPatterns {

    // ── 1. ThreadPoolExecutor anatomy ────────────────────────────────────────

    /**
     * ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAlive, unit, workQueue, factory, handler)
     *
     *   corePoolSize  — threads created eagerly and never killed (even if idle)
     *   maxPoolSize   — upper bound; extra threads created only when queue is FULL
     *   keepAlive     — idle thread (above core) is killed after this duration
     *   workQueue     — holds tasks when all core threads are busy
     *                   LinkedBlockingQueue (unbounded) → maxPoolSize is ignored!
     *                   ArrayBlockingQueue(N) → bounded → enables back-pressure
     *   handler       — RejectedExecutionHandler when queue AND maxPoolSize are saturated
     */
    static ThreadPoolExecutor buildProductionPool(int coreSize, int maxSize, int queueCapacity) {
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L, TimeUnit.SECONDS,                    // keepAlive for extra threads
            new ArrayBlockingQueue<>(queueCapacity),   // bounded → back-pressure
            new NamedThreadFactory("prod-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy()  // slow down producers when saturated
        );
    }

    // ── 2. Pool sizing formulas ───────────────────────────────────────────────

    /**
     * CPU-bound tasks (no blocking I/O):
     *   threads = Runtime.availableProcessors() + 1
     *   (+1 avoids one idle core when a thread is temporarily paused by GC/OS)
     *
     * I/O-bound tasks (blocking on network, DB, disk):
     *   threads = availableProcessors × (1 + waitTime / computeTime)
     *   e.g., if task spends 90% waiting: threads = 8 × (1 + 9) = 80
     *
     * Virtual threads (Java 21):
     *   threads = one per in-flight request (JVM multiplexes onto carrier threads)
     *   → eliminates the sizing problem for I/O-bound workloads completely
     */
    static int cpuBoundPoolSize() {
        return Runtime.getRuntime().availableProcessors() + 1;
    }

    static int ioBoundPoolSize(double waitTimeMs, double cpuTimeMs) {
        int cpus = Runtime.getRuntime().availableProcessors();
        // Little's Law adjustment: N = cpus × (1 + W/C)
        return (int) Math.ceil(cpus * (1.0 + waitTimeMs / cpuTimeMs));
    }

    // ── 3. Rejection policies ─────────────────────────────────────────────────

    /**
     * AbortPolicy      — default; throws RejectedExecutionException
     *                    Good for fast-fail; caller must handle the exception.
     *
     * CallerRunsPolicy — caller thread executes the task directly
     *                    Good for back-pressure: slows producers naturally.
     *
     * DiscardPolicy    — silently drops the task
     *                    Only acceptable when tasks are truly idempotent/best-effort.
     *
     * DiscardOldestPolicy — removes the oldest queued task and retries submission
     *                    Risk: can starve tasks that are waiting a long time.
     *
     * Custom policy    — log-and-queue-to-dead-letter, persist to disk, etc.
     */
    static class LogAndRejectPolicy implements RejectedExecutionHandler {
        private final AtomicLong rejectedCount = new AtomicLong(0);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            long count = rejectedCount.incrementAndGet();
            System.err.printf("[WARN] Task rejected (total=%d, queueSize=%d, activeThreads=%d)%n",
                count, executor.getQueue().size(), executor.getActiveCount());
            // In production: send to a dead-letter queue, record a metric, alert
            // throw new RejectedExecutionException("Queue full, task dropped");
        }

        public long getRejectedCount() { return rejectedCount.get(); }
    }

    // ── 4. Custom ThreadFactory ─────────────────────────────────────────────

    /**
     * Always name your threads. "pool-3-thread-7" in a stack trace is useless.
     * Thread names show up in: jstack, thread dumps, Datadog/Grafana, profilers.
     */
    static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(0);
        private final boolean daemon;

        NamedThreadFactory(String prefix) { this(prefix, false); }

        NamedThreadFactory(String prefix, boolean daemon) {
            this.prefix = prefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, ex) ->
                System.err.printf("[ERROR] Uncaught exception in %s: %s%n",
                    thread.getName(), ex.getMessage())
            );
            return t;
        }
    }

    // ── 5. ForkJoinPool for divide-and-conquer ───────────────────────────────

    /**
     * ForkJoinPool uses WORK-STEALING: idle threads steal tasks from busy threads' queues.
     * Best for recursive divide-and-conquer tasks that are CPU-bound.
     *
     * The common pool is used by: parallel streams, CompletableFuture.supplyAsync(),
     * Arrays.parallelSort(). Be careful: submitting blocking tasks to the common pool
     * can stall ALL parallel streams in the JVM!
     */
    static class ParallelMergeSort extends RecursiveTask<int[]> {
        private final int[] arr;
        private static final int THRESHOLD = 1000; // base case size

        ParallelMergeSort(int[] arr) { this.arr = arr; }

        @Override
        protected int[] compute() {
            if (arr.length <= THRESHOLD) {
                int[] copy = arr.clone();
                Arrays.sort(copy);       // sequential for small arrays
                return copy;
            }
            int mid = arr.length / 2;
            ParallelMergeSort left  = new ParallelMergeSort(Arrays.copyOfRange(arr, 0, mid));
            ParallelMergeSort right = new ParallelMergeSort(Arrays.copyOfRange(arr, mid, arr.length));

            left.fork();                  // submit left to pool asynchronously
            int[] rightResult = right.compute(); // compute right in current thread
            int[] leftResult  = left.join();     // wait for left to finish

            return merge(leftResult, rightResult);
        }

        private static int[] merge(int[] a, int[] b) {
            int[] result = new int[a.length + b.length];
            int i = 0, j = 0, k = 0;
            while (i < a.length && j < b.length)
                result[k++] = a[i] <= b[j] ? a[i++] : b[j++];
            while (i < a.length) result[k++] = a[i++];
            while (j < b.length) result[k++] = b[j++];
            return result;
        }
    }

    // ── 6. Scheduled executor service ────────────────────────────────────────

    /**
     * ScheduledExecutorService vs Timer:
     *   Timer uses a single thread — one slow task delays all others.
     *   Timer swallows exceptions — a bug in one task kills all scheduled tasks.
     *   ScheduledExecutorService fixes both.
     *
     * scheduleAtFixedRate  — fires every N ms; if task takes >N, next fires immediately
     * scheduleWithFixedDelay — waits N ms AFTER task completes (more predictable)
     */
    static class HeartbeatScheduler {
        private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("heartbeat", true));  // daemon thread
        private final AtomicLong beatCount = new AtomicLong(0);
        private ScheduledFuture<?> future;

        public void start() {
            future = scheduler.scheduleAtFixedRate(
                () -> System.out.println("Heartbeat #" + beatCount.incrementAndGet()),
                0L, 1L, TimeUnit.SECONDS   // initial delay, period
            );
        }

        public void stop() {
            if (future != null) future.cancel(false);
            scheduler.shutdown();
        }

        public long getBeatCount() { return beatCount.get(); }
    }

    // ── 7. Graceful shutdown ──────────────────────────────────────────────────

    /**
     * shutdown()      — no new tasks; waits for in-flight tasks to finish
     * shutdownNow()   — interrupts running threads; returns list of pending tasks
     *
     * Pattern: first try graceful shutdown, then forceful.
     */
    static void gracefulShutdown(ExecutorService executor, long timeoutSeconds) {
        executor.shutdown();  // orderly
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                List<Runnable> pending = executor.shutdownNow(); // forceful
                System.err.printf("[WARN] %d tasks were not completed%n", pending.size());
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("[ERROR] Executor did not terminate");
                }
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt(); // preserve interrupt status
        }
    }

    // ── Demo ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== Pool sizing ===");
        System.out.println("CPU-bound pool: " + cpuBoundPoolSize());
        System.out.println("I/O-bound pool (90ms wait / 10ms cpu): " + ioBoundPoolSize(90, 10));

        System.out.println("\n=== ForkJoin merge sort ===");
        int[] arr = new Random().ints(10_000, 0, 100_000).toArray();
        int[] sorted = ForkJoinPool.commonPool().invoke(new ParallelMergeSort(arr));
        System.out.println("Sorted first 5: " + Arrays.toString(Arrays.copyOf(sorted, 5)));

        System.out.println("\n=== Production pool + rejection ===");
        var logPolicy = new LogAndRejectPolicy();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), new NamedThreadFactory("demo"), logPolicy);

        for (int i = 0; i < 10; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    System.out.println("Task " + id + " done on " + Thread.currentThread().getName());
                });
            } catch (RejectedExecutionException ignored) { /* handled by policy */ }
        }
        gracefulShutdown(pool, 5);
        System.out.println("Rejected: " + logPolicy.getRejectedCount());

        System.out.println("\n=== Heartbeat scheduler ===");
        HeartbeatScheduler hb = new HeartbeatScheduler();
        hb.start();
        Thread.sleep(3500);
        hb.stop();
        System.out.println("Total beats: " + hb.getBeatCount()); // ~3
    }
}
