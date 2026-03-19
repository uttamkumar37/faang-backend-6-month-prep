
package com.faangprep.javabackend.concurrency;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Advanced Concurrency — 9 production patterns with Java 21
 *
 * Patterns:
 *  1. MetricsCounter     — high-contention counter (LongAdder)
 *  2. BackendExecutors   — IO/CPU/virtual thread pool factory
 *  3. ParallelFetcher    — CompletableFuture fan-out + Semaphore bulkhead
 *  4. EventPipeline      — bounded BlockingQueue producer–consumer
 *  5. ReadHeavyCache     — ReadWriteLock for read-heavy workloads
 *  6. DeadSafeTransfer   — ordered lock acquisition to prevent deadlock
 *  7. LazyPool           — double-checked locking for lazy singleton
 *  8. ConfigManager      — AtomicReference hot-reload
 *  9. VirtualWorkerPool  — virtual thread drain pattern
 */
public class AdvancedConcurrencyExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. HIGH-CONTENTION METRICS COUNTER
    // ─────────────────────────────────────────────────────────────────────────

    static class MetricsCounter {
        // LongAdder beats AtomicLong under high contention:
        // maintains per-CPU-stripe cells, reduces CAS retries
        private final LongAdder requests = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalLatencyMs = new LongAdder();

        public void recordRequest(long latencyMs, boolean isError) {
            requests.increment();
            totalLatencyMs.add(latencyMs);
            if (isError) errors.increment();
        }

        public void report() {
            long reqs = requests.sum();
            long errs = errors.sum();
            long totalMs = totalLatencyMs.sum();
            System.out.printf("Requests: %,d  Errors: %,d  Error%%: %.2f  AvgLatency: %.1fms%n",
                    reqs, errs,
                    reqs > 0 ? errs * 100.0 / reqs : 0.0,
                    reqs > 0 ? totalMs * 1.0 / reqs : 0.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. EXECUTOR FACTORY
    // ─────────────────────────────────────────────────────────────────────────

    static class BackendExecutors {
        // CPU-bound: cores + 1 threads — context-switching overhead beyond this
        static final ExecutorService CPU_POOL = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() + 1,
                Runtime.getRuntime().availableProcessors() + 1,
                0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(500),
                r -> Thread.ofPlatform().name("cpu-worker-", 0).daemon(true).unstarted(r),
                new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure
        );

        // IO-bound: can be much larger; blocked threads don't consume CPU
        static final ExecutorService IO_POOL = new ThreadPoolExecutor(
                50, 200,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2000),
                r -> Thread.ofPlatform().name("io-worker-", 0).daemon(true).unstarted(r),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // Java 21 virtual threads: one per task, OS thread released when blocked on IO
        static final ExecutorService VIRTUAL_POOL = Executors.newVirtualThreadPerTaskExecutor();

        static {
            Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
                CPU_POOL.shutdown();
                IO_POOL.shutdown();
                VIRTUAL_POOL.shutdown();
            }));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PARALLEL FETCHER WITH SEMAPHORE BULKHEAD
    // ─────────────────────────────────────────────────────────────────────────

    record UserProfile(String userId, String name, List<String> recentOrders) {}

    static class ParallelFetcher {
        // Bulkhead: at most 20 concurrent downstream calls regardless of thread count
        private final Semaphore semaphore = new Semaphore(20, true);

        public CompletableFuture<UserProfile> fetchUserProfile(String userId) {
            CompletableFuture<String> userFuture =
                    CompletableFuture.supplyAsync(() -> fetchUser(userId), BackendExecutors.VIRTUAL_POOL);

            CompletableFuture<List<String>> ordersFuture =
                    CompletableFuture.supplyAsync(() -> fetchOrders(userId), BackendExecutors.VIRTUAL_POOL);

            return userFuture.thenCombine(ordersFuture,
                    (name, orders) -> new UserProfile(userId, name, orders));
        }

        private String fetchUser(String userId) {
            try {
                semaphore.acquire();
                try {
                    Thread.sleep(20); // simulate network IO
                    return "User-" + userId;
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        }

        private List<String> fetchOrders(String userId) {
            try {
                semaphore.acquire();
                try {
                    Thread.sleep(30); // simulate network IO
                    return List.of("order-1", "order-2");
                } finally {
                    semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        }

        // Fan-out: fetch N profiles concurrently, fail fast on any error
        public List<UserProfile> fetchBatch(List<String> userIds) throws ExecutionException, InterruptedException {
            List<CompletableFuture<UserProfile>> futures = userIds.stream()
                    .map(this::fetchUserProfile)
                    .toList();

            CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allDone.join();  // blocks until all complete; throws CompletionException on first failure

            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. BOUNDED EVENT PIPELINE (PRODUCER–CONSUMER)
    // ─────────────────────────────────────────────────────────────────────────

    record Event(String type, String payload) {}

    static class EventPipeline {
        private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>(1000);
        private volatile boolean running = true;

        // Producer: non-blocking offer with backpressure reporting
        public boolean produce(Event event) {
            boolean accepted = queue.offer(event);
            if (!accepted) {
                System.out.println("BACKPRESSURE: event dropped, queue full");
            }
            return accepted;
        }

        // Consumer: blocks until event available or interrupted
        public void startConsumer(String name) {
            Thread.ofVirtual().name(name).start(() -> {
                while (running || !queue.isEmpty()) {
                    try {
                        Event event = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (event != null) {
                            process(event);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println(name + " consumer stopped");
            });
        }

        public void stop() { running = false; }

        private void process(Event e) {
            System.out.printf("  Processing [%s] %s%n", e.type(), e.payload());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. READ-HEAVY CACHE WITH ReadWriteLock
    // ─────────────────────────────────────────────────────────────────────────

    static class ReadHeavyCache<K, V> {
        private final Map<K, V> store = new HashMap<>();
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Lock readLock = rwLock.readLock();
        private final Lock writeLock = rwLock.writeLock();

        // Multiple threads can read concurrently
        public V get(K key) {
            readLock.lock();
            try {
                return store.get(key);
            } finally {
                readLock.unlock();
            }
        }

        // Only one writer; blocks all readers and other writers
        public void put(K key, V value) {
            writeLock.lock();
            try {
                store.put(key, value);
            } finally {
                writeLock.unlock();
            }
        }

        public int size() {
            readLock.lock();
            try {
                return store.size();
            } finally {
                readLock.unlock();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. DEADLOCK-SAFE MONEY TRANSFER
    // ─────────────────────────────────────────────────────────────────────────

    static class Account {
        final int id;
        private long balance;
        private final ReentrantLock lock = new ReentrantLock();

        Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        // Always acquire locks in a consistent global order (by ID)
        // This prevents the circular wait condition required for deadlock
        static void transfer(Account from, Account to, long amount) {
            Account first  = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to   : from;

            first.lock.lock();
            try {
                second.lock.lock();
                try {
                    if (from.balance < amount) throw new IllegalStateException("Insufficient funds");
                    from.balance -= amount;
                    to.balance += amount;
                    System.out.printf("Transferred %d from account-%d to account-%d%n",
                            amount, from.id, to.id);
                } finally {
                    second.lock.unlock();
                }
            } finally {
                first.lock.unlock();
            }
        }

        long getBalance() {
            lock.lock();
            try { return balance; } finally { lock.unlock(); }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. LAZY INITIALIZED SINGLETON (Double-Checked Locking)
    // ─────────────────────────────────────────────────────────────────────────

    static class LazyConnectionPool {
        // volatile: ensures the reference is fully visible after construction
        private static volatile LazyConnectionPool INSTANCE;
        private final List<String> connections;

        private LazyConnectionPool() {
            System.out.println("Initializing connection pool...");
            this.connections = new ArrayList<>();
            for (int i = 0; i < 10; i++) connections.add("conn-" + i);
        }

        public static LazyConnectionPool getInstance() {
            if (INSTANCE == null) {                    // first check — no lock
                synchronized (LazyConnectionPool.class) {
                    if (INSTANCE == null) {            // second check — with lock
                        INSTANCE = new LazyConnectionPool();
                    }
                }
            }
            return INSTANCE;
        }

        public String borrow() {
            return connections.isEmpty() ? null : connections.remove(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. HOT CONFIG RELOAD WITH AtomicReference
    // ─────────────────────────────────────────────────────────────────────────

    record Config(int maxRetries, long timeoutMs, String region) {}

    static class ConfigManager {
        private final AtomicReference<Config> configRef = new AtomicReference<>(
                new Config(3, 2000L, "us-east-1")
        );

        // Readers never block — atomic dereference
        public Config get() {
            return configRef.get();
        }

        // Writer atomically replaces config — CAS ensures no lost updates
        public Config reload(Config newConfig) {
            Config old = configRef.getAndSet(newConfig);
            System.out.printf("Config reloaded: %s → %s%n", old, newConfig);
            return old;
        }

        // Conditional update — only update if config hasn't changed since read
        public boolean updateIfUnchanged(Config expected, Config updated) {
            return configRef.compareAndSet(expected, updated);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. VIRTUAL THREAD WORKER POOL DRAIN
    // ─────────────────────────────────────────────────────────────────────────

    static class VirtualWorkerPool {
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final List<CompletableFuture<String>> futures = new CopyOnWriteArrayList<>();

        public void submit(String workId) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                    return "done-" + workId;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            }, executor);
            futures.add(future);
        }

        public List<String> drainResults() throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            all.get(10, TimeUnit.SECONDS);
            return futures.stream().map(CompletableFuture::join).toList();
        }

        public void shutdown() throws InterruptedException {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN DEMO
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("═══ 1. MetricsCounter ═══════════════════════");
        MetricsCounter counter = new MetricsCounter();
        counter.recordRequest(45, false);
        counter.recordRequest(120, false);
        counter.recordRequest(500, true);
        counter.report();

        System.out.println("\n═══ 2. ParallelFetcher ══════════════════════");
        ParallelFetcher fetcher = new ParallelFetcher();
        UserProfile profile = fetcher.fetchUserProfile("u-42").get();
        System.out.println("Fetched: " + profile);

        System.out.println("\n═══ 3. EventPipeline ═══════════════════════");
        EventPipeline pipeline = new EventPipeline();
        pipeline.startConsumer("consumer-1");
        pipeline.startConsumer("consumer-2");
        for (int i = 0; i < 5; i++) {
            pipeline.produce(new Event("ORDER_PLACED", "order-" + i));
        }
        Thread.sleep(200);
        pipeline.stop();

        System.out.println("\n═══ 4. ReadHeavyCache ══════════════════════");
        ReadHeavyCache<String, String> cache = new ReadHeavyCache<>();
        cache.put("k1", "v1");
        System.out.println("cache.get(k1) = " + cache.get("k1"));

        System.out.println("\n═══ 5. Deadlock-Safe Transfer ═══════════════");
        Account alice = new Account(1, 1000);
        Account bob = new Account(2, 500);
        Account.transfer(alice, bob, 200);
        System.out.printf("Alice: %d  Bob: %d%n", alice.getBalance(), bob.getBalance());

        System.out.println("\n═══ 6. ConfigManager Hot-Reload ═════════════");
        ConfigManager cfg = new ConfigManager();
        System.out.println("Current: " + cfg.get());
        cfg.reload(new Config(5, 3000L, "eu-west-1"));
        System.out.println("After reload: " + cfg.get());

        System.out.println("\n═══ 7. VirtualWorkerPool ════════════════════");
        VirtualWorkerPool pool = new VirtualWorkerPool();
        for (int i = 0; i < 10; i++) pool.submit("work-" + i);
        List<String> results = pool.drainResults();
        System.out.println("Results: " + results);
        pool.shutdown();
    }
}
