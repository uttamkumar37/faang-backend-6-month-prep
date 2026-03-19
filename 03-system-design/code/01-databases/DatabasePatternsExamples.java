package systemdesign.databases;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * Database Patterns for System Design
 *
 * Topics:
 *  1. Shard router — hash, range, and directory-based sharding
 *  2. Read replica router — with read-your-own-writes guarantee
 *  3. Connection pool simulation — HikariCP-style sizing
 *  4. Query retry with exponential backoff + circuit breaker
 *  5. Write-ahead log (WAL) — crash-safe write simulation
 *  6. MVCC — Multi-Version Concurrency Control (snapshot isolation)
 *  7. N+1 query problem — detection and batch loading
 */
public class DatabasePatternsExamples {

    // ─── Domain model ────────────────────────────────────────────────────────
    record User(long id, String name, String email, String department) {}
    record Order(long id, long userId, String status, double amount) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SHARD ROUTER — hash, range, directory strategies
    // ─────────────────────────────────────────────────────────────────────────

    interface ShardRouter {
        int getShard(long entityId);
        int shardCount();
    }

    // Hash-based: even distribution, no range query support
    static class HashShardRouter implements ShardRouter {
        private final int shards;
        HashShardRouter(int shards) { this.shards = shards; }
        @Override public int getShard(long id) { return (int)(Math.abs(id) % shards); }
        @Override public int shardCount()      { return shards; }
    }

    // Range-based: supports range queries, risk of hot shards
    static class RangeShardRouter implements ShardRouter {
        private final long rangeSize;
        private final int maxShards;
        RangeShardRouter(long rangeSize, int maxShards) {
            this.rangeSize = rangeSize;
            this.maxShards = maxShards;
        }
        @Override public int getShard(long id) { return (int) Math.min(id / rangeSize, maxShards - 1); }
        @Override public int shardCount()      { return maxShards; }
    }

    // Directory: flexible, can move entities, but lookup service is SPOFi
    static class DirectoryShardRouter implements ShardRouter {
        private final Map<Long, Integer> directory = new ConcurrentHashMap<>();
        private final AtomicInteger      counter   = new AtomicInteger(0);
        private final int shards;

        DirectoryShardRouter(int shards) { this.shards = shards; }

        @Override
        public int getShard(long id) {
            // Auto-assign new entities; existing entries return same shard
            return directory.computeIfAbsent(id, k -> counter.getAndIncrement() % shards);
        }

        // Allows manual re-routing (for hot-shard mitigation)
        void reassign(long entityId, int newShard) {
            directory.put(entityId, newShard);
            System.out.printf("  [Directory] entity %d reassigned to shard %d%n", entityId, newShard);
        }

        @Override public int shardCount() { return shards; }
    }

    static void shardRouterDemo() {
        System.out.println("=== 1. Shard Router ===");

        // Compare distribution across strategies
        int shards = 4;
        ShardRouter hash  = new HashShardRouter(shards);
        ShardRouter range = new RangeShardRouter(250, shards); // 250 IDs per shard
        DirectoryShardRouter dir = new DirectoryShardRouter(shards);

        Map<Integer, Integer> hashDist  = new TreeMap<>();
        Map<Integer, Integer> rangeDist = new TreeMap<>();
        Map<Integer, Integer> dirDist   = new TreeMap<>();

        for (long id = 1; id <= 1000; id++) {
            hashDist.merge(hash.getShard(id),   1, Integer::sum);
            rangeDist.merge(range.getShard(id), 1, Integer::sum);
            dirDist.merge(dir.getShard(id),     1, Integer::sum);
        }

        System.out.println("  Distribution of 1000 entities across 4 shards:");
        System.out.print("    Hash:      ");
        hashDist.forEach((s, c)  -> System.out.printf("shard%d=%d ", s, c));
        System.out.print("\n    Range:     ");
        rangeDist.forEach((s, c) -> System.out.printf("shard%d=%d ", s, c));
        System.out.print("\n    Directory: ");
        dirDist.forEach((s, c)   -> System.out.printf("shard%d=%d ", s, c));
        System.out.println();

        // Cross-shard query (scatter-gather) — hash sharding makes this expensive
        System.out.println("\n  Cross-shard query: SELECT * FROM orders WHERE user_id IN (1..10)");
        Set<Integer> shardSet = new HashSet<>();
        for (long id = 1; id <= 10; id++) shardSet.add(hash.getShard(id));
        System.out.println("  Must query shards: " + shardSet);
        System.out.println("  (Range sharding would stay on shard 0 for ids 1-10)");

        // Directory reassignment
        dir.getShard(42);                // auto-assign to some shard
        dir.reassign(42, 0);            // manually move hot entity to less-loaded shard
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. READ REPLICA ROUTER — read-your-own-writes guarantee
    // ─────────────────────────────────────────────────────────────────────────

    static class ReplicaRouter {
        private final String primary;
        private final List<String> replicas;
        private final AtomicInteger rrCounter = new AtomicInteger(0);
        // Track last write time per session to enforce read-your-own-writes
        private final Map<String, Long> sessionLastWrite = new ConcurrentHashMap<>();
        private final long replicationLagMs = 100; // assume ~100ms lag

        ReplicaRouter(String primary, List<String> replicas) {
            this.primary  = primary;
            this.replicas = List.copyOf(replicas);
        }

        // All writes always go to primary and record timestamp
        String routeWrite(String sessionId) {
            sessionLastWrite.put(sessionId, System.currentTimeMillis());
            return primary;
        }

        // Reads: replica if no recent write; primary if write was recent
        String routeRead(String sessionId) {
            Long lastWrite = sessionLastWrite.get(sessionId);
            if (lastWrite != null &&
                System.currentTimeMillis() - lastWrite < replicationLagMs + 50 /*safety margin*/) {
                // Recent writer — must read from primary to avoid stale read
                System.out.println("    [ReadRouter] recent write → routing to primary");
                return primary;
            }
            // Round-robin across replicas
            String replica = replicas.get(rrCounter.getAndIncrement() % replicas.size());
            System.out.println("    [ReadRouter] routing to replica: " + replica);
            return replica;
        }
    }

    static void readReplicaDemo() {
        System.out.println("\n=== 2. Read Replica Router ===");

        ReplicaRouter router = new ReplicaRouter(
            "primary-db",
            List.of("replica-1", "replica-2", "replica-3")
        );

        // Session A: writes then immediately reads (should read from primary)
        String sessionA = "session-A";
        System.out.println("  Session A — writes then reads:");
        System.out.println("    Write: " + router.routeWrite(sessionA));
        System.out.println("    Read:  " + router.routeRead(sessionA)); // → primary

        // Session B: no recent write (should read from replica)
        String sessionB = "session-B";
        System.out.println("  Session B — no prior writes:");
        System.out.println("    Read 1: " + router.routeRead(sessionB)); // → replica
        System.out.println("    Read 2: " + router.routeRead(sessionB)); // → replica (RR)
        System.out.println("    Read 3: " + router.routeRead(sessionB)); // → replica (RR)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CONNECTION POOL — capacity simulation
    // ─────────────────────────────────────────────────────────────────────────

    static class ConnectionPool {
        private final Semaphore available;
        private final int       maxSize;
        private final AtomicInteger waitCount   = new AtomicInteger(0);
        private final AtomicInteger acquireCount = new AtomicInteger(0);
        private final AtomicLong    totalWaitMs  = new AtomicLong(0);

        ConnectionPool(int maxSize) {
            this.maxSize   = maxSize;
            this.available = new Semaphore(maxSize, true);
        }

        // Borrow connection with timeout
        <T> T withConnection(long timeoutMs, Supplier<T> work)
                throws InterruptedException, TimeoutException {
            long start = System.currentTimeMillis();
            if (!available.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Connection pool exhausted (pool size=" + maxSize + ")");
            }
            totalWaitMs.addAndGet(System.currentTimeMillis() - start);
            waitCount.incrementAndGet();
            acquireCount.incrementAndGet();
            try {
                return work.get();
            } finally {
                available.release(); // MUST release in finally block
            }
        }

        String stats() {
            long acquires = acquireCount.get();
            long waitTotal = totalWaitMs.get();
            return String.format("size=%d available=%d acquires=%d avgWait=%.1fms",
                maxSize, available.availablePermits(), acquires,
                acquires == 0 ? 0 : (double) waitTotal / acquires);
        }
    }

    static void connectionPoolDemo() throws Exception {
        System.out.println("\n=== 3. Connection Pool ===");

        int poolSize = 10;
        ConnectionPool pool = new ConnectionPool(poolSize);

        // Simulate 50 concurrent requests each needing 20ms DB time
        int requestCount = 50;
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger timeouts  = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            final int reqId = i;
            Thread.ofVirtual().start(() -> {
                try {
                    pool.withConnection(500 /*ms timeout*/, () -> {
                        Thread.sleep(20); // simulate 20ms query
                        return "result-" + reqId;
                    });
                    successes.incrementAndGet();
                } catch (TimeoutException e) {
                    timeouts.incrementAndGet();
                } catch (Exception e) {
                    timeouts.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        System.out.printf("  Pool size=%d, requests=%d → successes=%d, timeouts=%d%n",
            poolSize, requestCount, successes.get(), timeouts.get());
        System.out.println("  " + pool.stats());

        // Little's Law analysis
        System.out.println("\n  Pool sizing (Little's Law: pool = RPS × avg_latency):");
        System.out.println("  100 RPS × 50ms = 5 connections needed");
        System.out.println("  1000 RPS × 50ms = 50 connections needed");
        System.out.println("  Rule: start at cpu*2, tune with pool wait metrics");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. QUERY RETRY WITH EXPONENTIAL BACKOFF
    // ─────────────────────────────────────────────────────────────────────────

    static class DatabaseClient {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final int           failCount;    // fail first N calls

        DatabaseClient(int failCount) { this.failCount = failCount; }

        String query(String sql) {
            int n = callCount.incrementAndGet();
            if (n <= failCount) throw new RuntimeException("DB transient error (attempt " + n + ")");
            return "result for: " + sql;
        }
    }

    static <T> T withRetry(Supplier<T> operation, int maxAttempts, long baseDelayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                if (attempt == maxAttempts) throw e;
                // Exponential backoff + jitter
                long delay = baseDelayMs * (1L << (attempt - 1))  // 100, 200, 400, 800...
                    + ThreadLocalRandom.current().nextLong(baseDelayMs / 2);
                System.out.printf("  Attempt %d failed: %s. Retrying in %dms...%n",
                    attempt, e.getMessage(), delay);
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new RuntimeException("unreachable");
    }

    static void retryDemo() {
        System.out.println("\n=== 4. Query Retry with Exponential Backoff ===");

        DatabaseClient db = new DatabaseClient(3); // first 3 calls fail

        String result = withRetry(
            () -> db.query("SELECT * FROM users WHERE id = 1"),
            5 /*maxAttempts*/,
            50 /*baseDelayMs*/
        );
        System.out.println("  Final result: " + result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. WRITE-AHEAD LOG (WAL) — crash-safe write simulation
    // ─────────────────────────────────────────────────────────────────────────

    static class WriteAheadLog {
        record LogEntry(long lsn, String operation, Map<String, Object> data) {}

        private final List<LogEntry>           log       = new ArrayList<>();
        private final Map<String, Object>      dataStore = new ConcurrentHashMap<>();
        private final AtomicLong               lsn       = new AtomicLong(0);
        private volatile long                  checkpointLsn = -1;

        // Write to WAL first, then to data store
        void write(String key, Object value) {
            // Step 1: Append to WAL (durable first — on disk in real system)
            long entryLsn = lsn.incrementAndGet();
            log.add(new LogEntry(entryLsn, "PUT", Map.of("key", key, "value", value)));
            System.out.printf("  [WAL  ] lsn=%d: PUT %s=%s%n", entryLsn, key, value);

            // Step 2: Apply to in-memory data store
            dataStore.put(key, value);
            System.out.printf("  [Store] applied: %s=%s%n", key, value);
        }

        // Checkpoint: mark safe point for log truncation
        void checkpoint() {
            checkpointLsn = lsn.get();
            System.out.println("  [WAL] checkpoint at lsn=" + checkpointLsn);
        }

        // Crash recovery: replay log entries after last checkpoint
        void recover() {
            dataStore.clear();
            System.out.println("  [Recovery] replaying WAL from beginning...");
            for (LogEntry entry : log) {
                if ("PUT".equals(entry.operation())) {
                    dataStore.put((String) entry.data().get("key"), entry.data().get("value"));
                    System.out.printf("  [Recovery] lsn=%d: restored %s=%s%n",
                        entry.lsn(), entry.data().get("key"), entry.data().get("value"));
                }
            }
            System.out.println("  [Recovery] complete. Store: " + dataStore);
        }
    }

    static void walDemo() {
        System.out.println("\n=== 5. Write-Ahead Log ===");

        WriteAheadLog wal = new WriteAheadLog();
        wal.write("user:1", "Alice");
        wal.write("user:2", "Bob");
        wal.checkpoint();
        wal.write("user:3", "Carol");

        // Simulate crash — data store lost, but WAL survives
        System.out.println("  --- CRASH! Data store wiped ---");
        wal.recover();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. MVCC — Multi-Version Concurrency Control
    // ─────────────────────────────────────────────────────────────────────────

    static class MvccStore {
        record Version(long txId, String value, boolean deleted) {}
        private final Map<String, List<Version>> store = new ConcurrentHashMap<>();
        private final AtomicLong txCounter = new AtomicLong(0);

        long beginTransaction() { return txCounter.incrementAndGet(); }

        void write(long txId, String key, String value) {
            store.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Version(txId, value, false));
        }

        void delete(long txId, String key) {
            store.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Version(txId, null, true));
        }

        // Read as-of a transaction snapshot (sees only versions with txId ≤ readAtTxId)
        // In real MVCC: each tx sees snapshot of committed state at tx start time
        Optional<String> read(long readAtTxId, String key) {
            List<Version> versions = store.getOrDefault(key, List.of());
            return versions.stream()
                .filter(v -> v.txId() <= readAtTxId)
                .max(Comparator.comparingLong(Version::txId))
                .filter(v -> !v.deleted())
                .map(Version::value);
        }
    }

    static void mvccDemo() {
        System.out.println("\n=== 6. MVCC (Snapshot Isolation) ===");

        MvccStore store = new MvccStore();

        // T1: initial write
        long t1 = store.beginTransaction();
        store.write(t1, "user:1", "Alice-v1");
        System.out.println("  T1 wrote user:1 = Alice-v1");

        // T2: started BEFORE T3's update (sees old value)
        long t2 = store.beginTransaction();
        System.out.println("  T2 started (will see Alice-v1)");

        // T3: updates the row
        long t3 = store.beginTransaction();
        store.write(t3, "user:1", "Alice-v2");
        System.out.println("  T3 wrote user:1 = Alice-v2");

        // T2 reads — sees its snapshot (Alice-v1, not Alice-v2)
        System.out.println("  T2 reads user:1 = " + store.read(t2, "user:1")); // Alice-v1
        // New transaction reads latest version
        long t4 = store.beginTransaction();
        System.out.println("  T4 reads user:1 = " + store.read(t4, "user:1")); // Alice-v2

        System.out.println("\n  MVCC benefits:");
        System.out.println("    - Readers never block writers");
        System.out.println("    - Writers never block readers");
        System.out.println("    - Each tx sees consistent snapshot");
        System.out.println("    - Old versions garbage-collected by VACUUM (Postgres)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. N+1 QUERY PROBLEM & BATCH LOADING
    // ─────────────────────────────────────────────────────────────────────────

    static class OrderRepository {
        private static final Map<Long, List<Order>> ordersByUser = Map.of(
            1L, List.of(new Order(101, 1, "placed", 100.0), new Order(102, 1, "shipped", 50.0)),
            2L, List.of(new Order(103, 2, "delivered", 200.0)),
            3L, List.of()
        );

        // Simulates single-user query (N+1 pattern)
        List<Order> findByUserId(long userId) {
            System.out.println("    [DB] SELECT * FROM orders WHERE user_id = " + userId);
            return ordersByUser.getOrDefault(userId, List.of());
        }

        // Batch query (anti N+1)
        Map<Long, List<Order>> findByUserIds(Collection<Long> userIds) {
            System.out.println("    [DB] SELECT * FROM orders WHERE user_id IN " + userIds);
            Map<Long, List<Order>> result = new HashMap<>();
            for (long id : userIds) result.put(id, ordersByUser.getOrDefault(id, List.of()));
            return result;
        }
    }

    static void nPlusOneDemo() {
        System.out.println("\n=== 7. N+1 Query Problem ===");

        List<User> users = List.of(
            new User(1, "Alice", "a@x.com", "Eng"),
            new User(2, "Bob",   "b@x.com", "Sales"),
            new User(3, "Carol", "c@x.com", "Finance")
        );
        OrderRepository repo = new OrderRepository();

        System.out.println("  BAD — N+1 pattern (1 query per user):");
        for (User u : users) {
            List<Order> orders = repo.findByUserId(u.id()); // 1 query per user!
            // process...
        }

        System.out.println("\n  GOOD — single batch query:");
        List<Long> userIds = users.stream().map(User::id).toList();
        Map<Long, List<Order>> allOrders = repo.findByUserIds(userIds); // 1 query total

        users.forEach(u -> {
            List<Order> orders = allOrders.getOrDefault(u.id(), List.of());
            // process...
        });
        System.out.println("  Orders loaded: " + allOrders.values().stream()
            .mapToInt(List::size).sum());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        shardRouterDemo();
        readReplicaDemo();
        connectionPoolDemo();
        retryDemo();
        walDemo();
        mvccDemo();
        nPlusOneDemo();
        System.out.println("\n=== All database pattern demos completed ===");
    }
}
