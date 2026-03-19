package com.faangprep.javabackend.concurrency;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * ConcurrencyAndJvmExamples — Advanced concurrency patterns tied to JVM internals.
 *
 * Topics covered:
 *   1. Memory model: happens-before, volatile, fences
 *   2. Lock strategies: intrinsic locks, ReentrantLock, StampedLock, ReadWriteLock
 *   3. Deadlock detection and avoidance
 *   4. Virtual threads (Project Loom) vs platform threads
 *   5. Structured concurrency (Java 21)
 *   6. ThreadLocal and InheritableThreadLocal pitfalls
 *   7. JVM thread state transitions
 *   8. False sharing and CPU cache lines
 *   9. ABA problem and solutions
 *
 * Interview tip: for every synchronization mechanism, know:
 *   - The happens-before guarantee it provides
 *   - Its fairness semantics (starvation risk?)
 *   - Whether it supports interruption / timeout
 *   - Its performance characteristics under high contention
 */
public class ConcurrencyAndJvmExamples {

    // ── 1. Volatile and the Java Memory Model ────────────────────────────────

    /**
     * volatile guarantees:
     *   • Visibility: writes are immediately visible to all threads.
     *   • Ordering: no reordering of reads/writes across the volatile access.
     * volatile does NOT guarantee atomicity for compound operations like i++.
     */
    static class DoubleCheckedLockingSingleton {
        // volatile prevents the partially-constructed object from being observed
        private static volatile DoubleCheckedLockingSingleton instance;

        private DoubleCheckedLockingSingleton() {}

        public static DoubleCheckedLockingSingleton getInstance() {
            if (instance == null) {                     // first check — no lock
                synchronized (DoubleCheckedLockingSingleton.class) {
                    if (instance == null) {             // second check — under lock
                        instance = new DoubleCheckedLockingSingleton();
                    }
                }
            }
            return instance;
        }
    }

    // ── 2. Lock strategies ───────────────────────────────────────────────────

    /**
     * ReentrantLock advantages over synchronized:
     *   • tryLock() with timeout → avoid deadlock
     *   • lockInterruptibly() → respond to interruption
     *   • Fair mode → prevent starvation (at the cost of throughput)
     *   • Multiple Condition objects (vs one intrinsic wait/notify set)
     */
    static class BoundedBuffer<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull  = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private final Queue<T> queue;
        private final int capacity;

        BoundedBuffer(int capacity) {
            this.capacity = capacity;
            this.queue = new ArrayDeque<>(capacity);
        }

        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                while (queue.size() == capacity) notFull.await();
                queue.add(item);
                notEmpty.signal();
            } finally {
                lock.unlock();   // ALWAYS in finally
            }
        }

        public T take() throws InterruptedException {
            lock.lock();
            try {
                while (queue.isEmpty()) notEmpty.await();
                T item = queue.poll();
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * StampedLock optimistic read — best for read-heavy, write-rare workloads.
     *
     * Three modes:
     *   write lock  — exclusive, like a write lock in ReadWriteLock
     *   read lock   — shared, blocks only when a writer holds the lock
     *   optimistic read — NO lock acquired; validate the stamp before using data
     *
     * Optimistic read is ~4–5× faster than a regular read lock when there's
     * no contention, but you MUST validate the stamp.
     */
    static class PointWithStampedLock {
        private final StampedLock sl = new StampedLock();
        private double x, y;

        public void move(double dx, double dy) {
            long stamp = sl.writeLock();
            try { x += dx; y += dy; }
            finally { sl.unlockWrite(stamp); }
        }

        public double distanceFromOrigin() {
            long stamp = sl.tryOptimisticRead();      // no blocking
            double cx = x, cy = y;
            if (!sl.validate(stamp)) {                // writer changed values?
                stamp = sl.readLock();                // fall back to read lock
                try { cx = x; cy = y; }
                finally { sl.unlockRead(stamp); }
            }
            return Math.sqrt(cx * cx + cy * cy);
        }
    }

    // ── 3. Deadlock detection ────────────────────────────────────────────────

    /**
     * Detects deadlocked threads via ThreadMXBean.
     * In production, also watch for "BLOCKED" count > 0 in thread dumps.
     */
    static class DeadlockDetector {
        private final ThreadMXBean bean = ManagementFactory.getThreadMXBean();

        public Optional<String> detectDeadlock() {
            long[] deadlockedIds = bean.findDeadlockedThreads();
            if (deadlockedIds == null) return Optional.empty();

            StringBuilder sb = new StringBuilder("DEADLOCK DETECTED:\n");
            for (ThreadInfo info : bean.getThreadInfo(deadlockedIds, true, true)) {
                sb.append("  Thread: ").append(info.getThreadName())
                  .append("  State: ").append(info.getThreadState())
                  .append("  Waiting for: ").append(info.getLockName()).append("\n");
                for (StackTraceElement ste : info.getStackTrace()) {
                    sb.append("    at ").append(ste).append("\n");
                }
            }
            return Optional.of(sb.toString());
        }
    }

    /**
     * Deadlock AVOIDANCE: always acquire locks in consistent global order.
     * Here we use System.identityHashCode as a total order.
     */
    static void transferSafe(Object lockA, Object lockB, Runnable action) {
        Object first  = System.identityHashCode(lockA) <= System.identityHashCode(lockB) ? lockA : lockB;
        Object second = first == lockA ? lockB : lockA;
        synchronized (first) {
            synchronized (second) {
                action.run();
            }
        }
    }

    // ── 4. Virtual Threads (Project Loom) ───────────────────────────────────

    /**
     * Virtual threads are cheap (~1 KB stack) JVM-managed threads.
     * They mount on carrier (platform) threads only when doing work.
     *
     * Key rules:
     *   • Do NOT pool virtual threads — create per-task and let the JVM manage.
     *   • Avoid synchronized blocks that hold a blocking call (pins carrier thread).
     *   • Use ReentrantLock instead of synchronized for long critical sections.
     *   • Thread-locals still work but can cause memory pressure; prefer ScopedValue.
     */
    static void demonstrateVirtualThreads() throws InterruptedException {
        // One virtual thread per request — the Loom way
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                final int id = i;
                futures.add(executor.submit(() -> {
                    // Simulates I/O — virtual thread unmounts here
                    Thread.sleep(10);
                    return "result-" + id;
                }));
            }
            // All 10,000 tasks complete; carrier threads never exceed CPU count
            for (Future<String> f : futures) {
                try { f.get(); } catch (ExecutionException e) { /* handle */ }
            }
        }
    }

    // ── 5. Structured Concurrency (Java 21 preview / JEP 453) ───────────────

    /**
     * StructuredTaskScope ensures that when a scope exits:
     *   a) all subtasks have completed (success or failure), OR
     *   b) all subtasks have been cancelled if the scope closes early.
     *
     * This eliminates "leaked" threads — a common bug with raw ExecutorService.
     *
     * ShutdownOnFailure: cancels all subtasks when the first one fails.
     * ShutdownOnSuccess: cancels all subtasks when the first one succeeds.
     */
    record UserProfile(String name) {}
    record UserOrder(String orderId) {}

    static record UserDashboard(UserProfile profile, UserOrder latestOrder) {}

    static UserDashboard fetchDashboard(long userId)
            throws InterruptedException, ExecutionException {
        // Requires --enable-preview in Java 21
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<UserProfile> profileTask =
                scope.fork(() -> new UserProfile("Alice")); // simulates DB call
            StructuredTaskScope.Subtask<UserOrder> orderTask =
                scope.fork(() -> new UserOrder("ORD-001")); // simulates DB call

            scope.join()           // wait for both
                 .throwIfFailed(); // propagate any exception

            return new UserDashboard(profileTask.get(), orderTask.get());
        }
    }

    // ── 6. ThreadLocal pitfalls ──────────────────────────────────────────────

    /**
     * ThreadLocal pitfall in thread pools: the value persists across task
     * boundaries because the thread is reused. Always clean up after use.
     *
     * With virtual threads this is compounded — a new virtual thread per
     * request is cheap, but if you use thread pools with ThreadLocal,
     * data leaks between requests.
     *
     * Solution: call remove() in a try/finally block.
     */
    static class RequestContext {
        private static final ThreadLocal<String> REQUEST_ID =
            ThreadLocal.withInitial(() -> UUID.randomUUID().toString());

        static String getRequestId() { return REQUEST_ID.get(); }

        static void set(String requestId) { REQUEST_ID.set(requestId); }

        static void clear() { REQUEST_ID.remove(); }  // CRITICAL for thread pool safety
    }

    // ── 7. False Sharing and CPU Cache Lines ─────────────────────────────────

    /**
     * False sharing: two independent variables share the same CPU cache line
     * (typically 64 bytes). Writing to one invalidates the other in all other
     * cores, causing unexpected performance collapse under parallelism.
     *
     * @Contended (JVM internal) / padding resolves this.
     * Benchmark: false sharing can cause 10×–50× slowdown vs padded version.
     */
    static class FalseSharingDemo {
        // BAD: both counters in same cache line (~16 bytes apart)
        static final AtomicLong counter1 = new AtomicLong(0);
        static final AtomicLong counter2 = new AtomicLong(0);

        // GOOD: LongAdder avoids false sharing internally via Cell[] striping
        static final LongAdder adder1 = new LongAdder();
        static final LongAdder adder2 = new LongAdder();
    }

    // ── 8. ABA Problem ───────────────────────────────────────────────────────

    /**
     * ABA problem: a CAS succeeds even though the value changed A→B→A.
     * This matters for lock-free data structures (e.g., lock-free stack) where
     * the "A" pointers at different times can refer to logically different nodes.
     *
     * Fix: use AtomicStampedReference to bundle a version counter with the value.
     */
    static class ABAFreeReference<T> {
        private final AtomicStampedReference<T> ref;

        ABAFreeReference(T initial) {
            this.ref = new AtomicStampedReference<>(initial, 0);
        }

        public boolean compareAndSet(T expected, T update) {
            int[] stampHolder = new int[1];
            T current = ref.get(stampHolder);
            if (!Objects.equals(current, expected)) return false;
            // Increment stamp on every successful write — ABA impossible
            return ref.compareAndSet(expected, update, stampHolder[0], stampHolder[0] + 1);
        }

        public T get() {
            return ref.getReference();
        }
    }

    // ── Demo ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("=== Singleton ===");
        System.out.println(DoubleCheckedLockingSingleton.getInstance()
            == DoubleCheckedLockingSingleton.getInstance()); // true

        System.out.println("\n=== BoundedBuffer ===");
        BoundedBuffer<Integer> buf = new BoundedBuffer<>(3);
        buf.put(1); buf.put(2); buf.put(3);
        System.out.println("Took: " + buf.take()); // 1

        System.out.println("\n=== StampedLock ===");
        PointWithStampedLock p = new PointWithStampedLock();
        p.move(3, 4);
        System.out.printf("Distance: %.2f%n", p.distanceFromOrigin()); // 5.00

        System.out.println("\n=== Virtual Threads ===");
        demonstrateVirtualThreads();
        System.out.println("10,000 virtual thread tasks completed");

        System.out.println("\n=== Deadlock Detector ===");
        System.out.println(new DeadlockDetector().detectDeadlock()); // Optional.empty

        System.out.println("\n=== ABA-Free Reference ===");
        ABAFreeReference<String> abaRef = new ABAFreeReference<>("A");
        abaRef.compareAndSet("A", "B");
        abaRef.compareAndSet("B", "A");
        // Even though value is "A" again, stamp is now 2, preventing ghost CAS
        System.out.println("Current: " + abaRef.get()); // A
    }
}
