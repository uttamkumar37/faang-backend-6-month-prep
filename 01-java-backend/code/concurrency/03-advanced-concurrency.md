# Advanced Concurrency — Synchronizers, Fork/Join, Structured Concurrency

---

## 1. ReadWriteLock — Multiple Readers, One Writer

When reads are far more frequent than writes, a `ReentrantLock` unnecessarily blocks readers from each other. `ReadWriteLock` allows **concurrent reads** but exclusive writes:

```java
public class CachedData<T> {
    private T data;
    private volatile boolean cacheValid;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock  = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public T get() {
        readLock.lock();
        try {
            if (cacheValid) return data; // multiple threads can read simultaneously
        } finally {
            readLock.unlock();
        }
        // Cache miss — upgrade to write lock
        writeLock.lock();
        try {
            if (!cacheValid) { // double-check (another thread may have updated)
                data = loadFromDB();
                cacheValid = true;
            }
            // Lock downgrade: acquire read lock while holding write lock
            readLock.lock();
        } finally {
            writeLock.unlock(); // write lock released, read lock still held
        }
        try {
            return data;
        } finally {
            readLock.unlock();
        }
    }

    public void invalidate() {
        writeLock.lock();
        try {
            cacheValid = false;
        } finally {
            writeLock.unlock();
        }
    }
}
```

**Lock downgrading** (write→read) is allowed; **lock upgrading** (read→write) is NOT — it can deadlock.

---

## 2. StampedLock — Optimistic Read (Java 8)

`StampedLock` adds an **optimistic read mode** — read without acquiring a lock, then validate:

```java
public class Point {
    private double x, y;
    private final StampedLock lock = new StampedLock();

    public double distanceFromOrigin() {
        // Try optimistic read first (no blocking, no lock acquisition)
        long stamp = lock.tryOptimisticRead();
        double currentX = x, currentY = y;                    // copy values
        if (!lock.validate(stamp)) {                           // was there a write?
            stamp = lock.readLock();                           // fall back to real read lock
            try {
                currentX = x; currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }

    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlock(stamp);
        }
    }
}
```

**When to use StampedLock**: very high-frequency, mostly-read scenarios where even `ReadWriteLock` read contention is a bottleneck. Caveat: not reentrant, complex API — only use when profiling shows lock contention.

---

## 3. CountDownLatch — Wait for N Events

Start a count at N; decrement with `countDown()`; block with `await()` until count reaches 0:

```java
// Use case 1: wait for all services to initialize
int serviceCount = 3;
CountDownLatch ready = new CountDownLatch(serviceCount);

for (String serviceName : List.of("db", "cache", "mq")) {
    CompletableFuture.runAsync(() -> {
        initService(serviceName);
        ready.countDown(); // signal this service is ready
    });
}
ready.await(); // block until all 3 services are ready
System.out.println("All services up — starting HTTP server");

// Use case 2: race — start all threads at the same moment
CountDownLatch startGun = new CountDownLatch(1);
for (int i = 0; i < 10; i++) {
    new Thread(() -> {
        startGun.await(); // all threads wait at the starting line
        performLoad();
    }).start();
}
startGun.countDown(); // fire — all 10 threads start concurrently

// With timeout
boolean completed = latch.await(30, TimeUnit.SECONDS);
if (!completed) throw new TimeoutException("Services didn't start in 30s");
```

**Key property**: `CountDownLatch` is single-use — cannot be reset. For repeatable barriers, use `CyclicBarrier`.

---

## 4. CyclicBarrier — All Parties Meet at the Barrier

All threads must arrive at the barrier before any can proceed. Can be reset and reused:

```java
// Use case: parallel computation with synchronization rounds
int workerCount = 4;
CyclicBarrier barrier = new CyclicBarrier(workerCount, () -> {
    // "Barrier action" — runs once when all parties arrive, before any are released
    System.out.println("All workers finished round — merging results...");
    mergePartialResults();
});

for (int i = 0; i < workerCount; i++) {
    final int workerId = i;
    new Thread(() -> {
        for (int round = 0; round < 10; round++) {
            computePartialResult(workerId, round);
            barrier.await(); // wait for all workers to finish this round
            // After barrier: read merged results from previous round
            readMergedData();
        }
    }).start();
}
// CyclicBarrier auto-resets after each cycle
```

**`CountDownLatch` vs `CyclicBarrier`**:
- Latch: one set of events → many waiters. Count goes down to 0. Single use.
- Barrier: all parties synchronized at a meeting point. Repeatable. Barrier action supported.

---

## 5. Semaphore — Limiting Concurrent Access

A `Semaphore` controls access to a limited number of permits:

```java
// Use case: limit concurrent DB connections differently from pool
Semaphore semaphore = new Semaphore(10); // 10 permits = max 10 concurrent operations

public void processRequest() throws InterruptedException {
    semaphore.acquire(); // blocks if all permits are taken
    try {
        heavyOperation();
    } finally {
        semaphore.release(); // always release!
    }
}

// Non-blocking tryAcquire
if (semaphore.tryAcquire()) {
    try { heavyOperation(); } finally { semaphore.release(); }
} else {
    throw new ServiceUnavailableException("Too many concurrent requests — try again");
}

// tryAcquire with timeout
if (semaphore.tryAcquire(1, TimeUnit.SECONDS)) { ... }

// Acquire multiple permits at once
semaphore.acquire(3); // blocks until 3 permits available
semaphore.release(3);

// Fair semaphore — FIFO order (prevents starvation)
Semaphore fairSemaphore = new Semaphore(10, true);
```

---

## 6. Phaser — Flexible Multi-Phase Barrier (Java 7)

`Phaser` is the most flexible synchronizer — combines features of both `CountDownLatch` and `CyclicBarrier`, supports dynamic registration:

```java
Phaser phaser = new Phaser(1); // register 1 party (main thread)

for (int i = 0; i < 3; i++) {
    phaser.register(); // register a new party
    final int id = i;
    new Thread(() -> {
        System.out.println("Worker " + id + " started phase 0");
        phaser.arriveAndAwaitAdvance(); // wait for all → advance to phase 1

        System.out.println("Worker " + id + " started phase 1");
        phaser.arriveAndAwaitAdvance(); // wait for all → advance to phase 2

        System.out.println("Worker " + id + " done");
        phaser.arriveAndDeregister(); // deregister — no longer participates
    }).start();
}

phaser.arriveAndAwaitAdvance(); // main thread participates in phase 0
phaser.arriveAndAwaitAdvance(); // main thread participates in phase 1
phaser.arriveAndDeregister();   // main deregisters

// Advanced: tiered phasers for tree-structured parallelism
// Phaser child = new Phaser(parent);
```

---

## 7. Exchanger — Swap Objects Between Two Threads

```java
// Two threads synchronize at an exchange point and swap objects
Exchanger<List<String>> exchanger = new Exchanger<>();

// Producer thread
new Thread(() -> {
    List<String> buffer = new ArrayList<>();
    while (true) {
        buffer.add(generateData());
        if (buffer.size() == 100) {
            buffer = exchanger.exchange(buffer); // hand off full buffer, get empty one back
            buffer.clear(); // buffer is now the one returned by consumer
        }
    }
}).start();

// Consumer thread
new Thread(() -> {
    List<String> emptyBuffer = new ArrayList<>();
    while (true) {
        List<String> fullBuffer = exchanger.exchange(emptyBuffer); // get full, return empty
        processAll(fullBuffer);
        emptyBuffer = fullBuffer; // reuse
        emptyBuffer.clear();
    }
}).start();
```

---

## 8. Fork/Join Framework — Recursive Parallelism

The Fork/Join Framework (Java 7) divides work recursively and executes on a shared pool with **work stealing**:

```
Task
├─ fork subtask1 (goes into queue of thread 1)
├─ fork subtask2 (goes into queue of thread 2)
└─ join results
```

**Work stealing**: idle threads steal tasks from the tail of other threads' queues → high CPU utilization.

```java
// RecursiveTask<V> for tasks that return a value
class MergeSort extends RecursiveTask<int[]> {
    private final int[] array;
    private final int lo, hi;
    private static final int THRESHOLD = 256; // below this: sequential

    MergeSort(int[] array, int lo, int hi) {
        this.array = array; this.lo = lo; this.hi = hi;
    }

    @Override
    protected int[] compute() {
        if (hi - lo <= THRESHOLD) {
            return sortSequentially(array, lo, hi); // base case
        }
        int mid = (lo + hi) / 2;
        MergeSort leftTask  = new MergeSort(array, lo, mid);
        MergeSort rightTask = new MergeSort(array, mid, hi);
        rightTask.fork();         // schedule right subtask asynchronously
        int[] left = leftTask.compute(); // run left subtask directly (important: compute, not fork+join)
        int[] right = rightTask.join();  // wait for right subtask
        return merge(left, right);
    }
}

// RecursiveAction for tasks with no return value
class ParallelIncrement extends RecursiveAction {
    private final long[] data;
    private final int from, to;

    @Override
    protected void compute() {
        if (to - from <= 1000) {
            for (int i = from; i < to; i++) data[i]++; // base case
            return;
        }
        int mid = (from + to) / 2;
        invokeAll(
            new ParallelIncrement(data, from, mid),
            new ParallelIncrement(data, mid, to)
        ); // fork both and join — convenience method
    }
}

// Use common pool (CPU-count threads)
ForkJoinPool pool = ForkJoinPool.commonPool();
int[] sorted = pool.invoke(new MergeSort(data, 0, data.length));

// Custom pool (for I/O, or to isolate from common pool)
ForkJoinPool myPool = new ForkJoinPool(4);
myPool.invoke(task);
myPool.shutdown();
```

**Performance tips**:
- Threshold matters — too small (too much overhead), too large (insufficient parallelism). Profile to find the sweet spot.
- Use `compute()` for one subtask and `fork()`+`join()` for the other — never `fork()` both (loses direct execution benefit)
- Don't block in ForkJoin tasks — it starves carrier threads (similar to virtual thread pinning)

---

## 9. Structured Concurrency (Java 21 preview)

Structured concurrency ensures that concurrent tasks are scoped to their parent — tasks can't outlive the code block that started them:

```java
// Java 21 StructuredTaskScope
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User>  userTask  = scope.fork(() -> fetchUser(userId));
    Subtask<Order> orderTask = scope.fork(() -> fetchOrder(orderId));

    scope.join();          // wait for both
    scope.throwIfFailed(); // propagate first exception if any

    return new Response(userTask.get(), orderTask.get());
} // scope auto-closes — all forked tasks are done here

// ShutdownOnSuccess — cancel remaining tasks when first succeeds (for redundancy)
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
    scope.fork(() -> fetchFromReplica1());
    scope.fork(() -> fetchFromReplica2());
    scope.fork(() -> fetchFromReplica3());
    scope.joinUntil(Instant.now().plusSeconds(5)); // deadline
    return scope.result(); // fastest result
}
```

**Benefits over CompletableFuture**:
- Errors propagate cleanly — no silent swallowing
- Cancellation is scoped — child tasks cancelled when parent exits
- Thread dumps show task hierarchy (not just anonymous CompletableFuture callbacks)

---

## 10. ScopedValue — Replace ThreadLocal for Virtual Threads (Java 21)

`ScopedValue` is the modern replacement for `ThreadLocal` when using virtual threads — immutable, inheritable, bounded lifetime:

```java
// Declare in the class providing context
public static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

// Set for a bounded scope (unlike ThreadLocal.set() which is global for the thread)
ScopedValue.where(CONTEXT, new RequestContext("user-123", "trace-abc"))
           .run(() -> {
               // CONTEXT.get() returns the RequestContext within this scope
               service.processOrder(orderId);
               // ... nested calls can read CONTEXT.get()
           });
// After run() exits, the binding is gone — no manual remove() needed

// In a nested method, anywhere in the call graph:
void processOrder(String orderId) {
    RequestContext ctx = CONTEXT.get(); // always valid within the scope
    log.info("Processing order {} for user {}", orderId, ctx.userId());
}
```

**ThreadLocal vs ScopedValue**:
| Feature | ThreadLocal | ScopedValue |
|---|---|---|
| Mutability | Mutable (`.set()`) | Immutable once set |
| Cleanup | Manual `.remove()` | Automatic (scope boundary) |
| Virtual thread safe | Risk of inheritance issues | Designed for virtual threads |
| Lookup | Hash map lookup | Optimized direct access |

---

## 11. Concurrent Collections Reference

```java
// Thread-safe List alternatives
CopyOnWriteArrayList<T> list; // reads lock-free, writes copy entire array
// Good for: rarely-written, frequently-read (event listener lists)
// Bad for: high write frequency (expensive copies)

// Thread-safe Queue (FIFO)
ConcurrentLinkedQueue<T> q;   // lock-free, unbounded, non-blocking
ArrayBlockingQueue<T> q;      // bounded, blocks on full/empty
LinkedBlockingQueue<T> q;     // optionally bounded, blocks
PriorityBlockingQueue<T> q;   // unbounded, blocks only on take when empty
DelayQueue<Delayed> q;        // elements available only after delay expires

// Thread-safe Deque
ConcurrentLinkedDeque<T> deq;  // lock-free
LinkedBlockingDeque<T> deq;    // blocking

// Thread-safe Map
ConcurrentHashMap<K,V> map;    // best general-purpose
ConcurrentSkipListMap<K,V> map; // sorted, lock-free (skip list structure)

// Thread-safe Set
ConcurrentSkipListSet<E> set;  // sorted concurrent set
CopyOnWriteArraySet<E> set;    // best for small, rarely-changed sets

// Specially useful methods on ConcurrentHashMap
map.computeIfAbsent(key, k -> new ArrayList<>()); // atomic compute
map.computeIfPresent(key, (k, v) -> v + 1);
map.compute(key, (k, v) -> v == null ? 1 : v + 1);
map.merge(key, 1, Integer::sum);                   // like compute but cleaner for counters
map.forEach(4, (k, v) -> process(k, v));           // parallel iteration, parallelism threshold=4
```

---

## 12. Interview Q&A

**Q: What is the difference between CountDownLatch and CyclicBarrier?**  
`CountDownLatch` is a one-time gate: N events happen (count down to 0), M waiters are released. It cannot be reset. Example: wait for 3 services to initialize. `CyclicBarrier` is a meeting point: N parties all arrive, then all proceed together. It resets automatically for the next cycle. Example: parallel computation with synchronization rounds. A `CyclicBarrier` also supports a "barrier action" — a Runnable that runs once when all parties arrive, before they're released.

**Q: When would you use a Semaphore vs a fixed thread pool?**  
A thread pool limits concurrency by limiting the number of threads that exist. A `Semaphore` limits concurrency by limiting the number of permits available — threads may exist but block waiting for a permit. Use a thread pool when you want to limit the resources consumed by *creating threads*. Use a `Semaphore` when you want to rate-limit a specific operation regardless of which thread executes it — e.g., limit concurrent database connections, concurrent API calls to an external service, or concurrent file-system reads.

**Q: What is work stealing in the ForkJoin framework?**  
Each worker thread in a `ForkJoinPool` has its own double-ended task queue. When a thread creates subtasks with `fork()`, they go into its own local queue. When a thread finishes its local work, instead of sitting idle, it "steals" tasks from the *tail* (other end) of another thread's queue. This ensures all threads stay busy and the pool achieves high CPU utilization. The push/pop is LIFO (depth-first) for the owner; stealing is FIFO — this minimizes contention between owner and thief.

**Q: What is structured concurrency and why is it better than CompletableFuture?**  
Structured concurrency (Java 21) enforces that concurrent tasks are scoped to a code block — they must complete before the block exits. This guarantees: (1) No task leaks — tasks can't outlive their scope. (2) Errors propagate cleanly to the parent — no silent swallowing. (3) Cancellation is coherent — cancelling the scope cancels all children. With `CompletableFuture`, tasks run on arbitrary thread pools with no parent-child relationship, errors must be manually propagated, and diagnosing "hanging tasks" in production is hard. Structured concurrency makes concurrent code read like sequential code in terms of scope.
