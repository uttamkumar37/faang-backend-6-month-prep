# Java Concurrency

## 1. Threads and the Java Memory Model

### Thread lifecycle

```
NEW → RUNNABLE → (WAITING | TIMED_WAITING | BLOCKED) → TERMINATED
```

- `BLOCKED`: a thread waiting to acquire an intrinsic lock (synchronized).
- `WAITING`: a thread parked by `Object.wait()`, `LockSupport.park()`, `Thread.join()`.
- `TIMED_WAITING`: same but with a timeout.

### Java Memory Model (JMM)

Each CPU has its own cache. Without rules, threads can see stale data.

The JMM defines **happens-before** (HB) relationships that guarantee visibility:

| Action | Establishes HB with |
|---|---|
| `volatile` write | All subsequent `volatile` reads of that variable |
| `synchronized` block exit | All subsequent entries into a synchronized block on the same lock |
| `Thread.start()` | All actions in the started thread |
| `Thread.join()` | All actions before `join()` returns |
| Object construction | First use of the object (if safely published) |

**Key rule**: If action A happens-before B, then B sees all of A's memory writes.

---

## 2. volatile

```java
private volatile boolean shutdown = false;

// Thread 1
void stop() { shutdown = true; }

// Thread 2
void run() {
    while (!shutdown) {
        doWork();
    }
}
```

`volatile` guarantees:
- Every write is immediately visible to all threads.
- Reads always go to main memory (bypass CPU cache).
- **Does NOT guarantee atomicity** for compound operations like `i++` (read-modify-write).

When to use:
- Single-writer, multi-reader flag.
- Publishing an immutable object reference safely.
- Status indicators.

When NOT to use:
- Incrementing a counter — use `AtomicLong`.
- Any check-then-act pattern — use `synchronized` or a lock.

---

## 3. synchronized

```java
class Counter {
    private int count = 0;

    public synchronized void increment() { count++; }
    public synchronized int get() { return count; }
}
```

- Intrinsic lock (monitor) on `this` for instance methods.
- Intrinsic lock on the `Class` object for static methods.
- No two threads can hold the same intrinsic lock simultaneously.
- Guarantees both visibility and atomicity.

### Lock biasing and contention

JVM starts with a biased lock (single thread — very cheap). Under contention it inflates to a real mutex. Under high contention consider `ReentrantLock` for more control.

---

## 4. ReentrantLock

```java
private final ReentrantLock lock = new ReentrantLock();

public void transfer(Account to, int amount) {
    lock.lock();
    try {
        this.balance -= amount;
        to.balance += amount;
    } finally {
        lock.unlock(); // always in finally
    }
}
```

Advantages over `synchronized`:
- `tryLock(timeout)` — avoids blocking forever.
- `lockInterruptibly()` — can be interrupted while waiting.
- `ReentrantReadWriteLock` — separate read and write locks (multiple readers OK).
- Fair mode: FIFO ordering of lock acquisition.

```java
ReadWriteLock rwLock = new ReentrantReadWriteLock();
Lock readLock = rwLock.readLock();
Lock writeLock = rwLock.writeLock();
```

---

## 5. Atomic Classes

```java
AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet(); // atomic read-modify-write via CAS

AtomicReference<Config> config = new AtomicReference<>(initial);
config.compareAndSet(expected, newConfig); // safe hot-reload
```

| Class | Use case |
|---|---|
| `AtomicInteger` / `AtomicLong` | Counters, IDs |
| `AtomicBoolean` | Single flag with CAS |
| `AtomicReference<V>` | Object reference hot-swap |
| `LongAdder` / `LongAccumulator` | High-contention counters (faster than AtomicLong) |
| `AtomicStampedReference` | Prevents ABA problem with version stamp |

**LongAdder** beats `AtomicLong` under high contention: it internally maintains per-CPU-stripe counters, reducing CAS conflicts.

---

## 6. Thread Pools

```java
// CPU-bound: #threads = #cores + 1
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors() + 1
);

// IO-bound: can be much larger; threads mostly waiting on I/O
ExecutorService ioPool = Executors.newFixedThreadPool(100);

// Schedule recurring tasks
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
scheduler.scheduleAtFixedRate(this::flushMetrics, 0, 30, TimeUnit.SECONDS);
```

### Custom ThreadPoolExecutor

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,       // minimum living threads
    maximumPoolSize,    // max threads under load
    keepAliveTime,      // extra threads die after this idle time
    TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(1000),        // bounded queue (prefer this!)
    new ThreadPoolExecutor.CallerRunsPolicy() // backpressure: caller runs task
);
```

**Why bounded queues?** Unbounded queues (`LinkedBlockingQueue` default) silently accumulate millions of tasks, causing OOM. Bounded queues expose backpressure via the rejection handler.

### Rejection policies

| Policy | Behavior |
|---|---|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` |
| `CallerRunsPolicy` | Caller thread runs the task (natural backpressure) |
| `DiscardPolicy` | Silently drops newest task |
| `DiscardOldestPolicy` | Drops oldest queued task |

---

## 7. CompletableFuture

```java
// Sequential chain
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchUser(userId), ioPool)
    .thenApplyAsync(user -> enrichUser(user), ioPool)
    .thenApply(user -> user.getName());

// Fan-out: run multiple tasks in parallel, combine
CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(() -> getUser(id));
CompletableFuture<List<Order>> ordersFuture = CompletableFuture.supplyAsync(() -> getOrders(id));

CompletableFuture<UserProfile> profile = userFuture.thenCombine(
    ordersFuture,
    (user, orders) -> new UserProfile(user, orders)
);

// Wait for all
CompletableFuture.allOf(f1, f2, f3).join();

// First to complete wins
CompletableFuture.anyOf(primary, fallback).thenApply(r -> (String) r);

// Error handling
future
    .exceptionally(ex -> "default-value")
    .handle((result, ex) -> ex != null ? fallback : result);
```

### Executor selection

- Default: `ForkJoinPool.commonPool()` — fine for CPU-bound, BAD for IO-bound (can block all common pool threads).
- Always pass a dedicated `ioPool` for IO operations.

---

## 8. BlockingQueue — Producer-Consumer

```java
BlockingQueue<Event> queue = new LinkedBlockingQueue<>(1000);

// Producer thread
while (true) {
    Event e = generateEvent();
    queue.put(e); // blocks if full
}

// Consumer thread
while (true) {
    Event e = queue.take(); // blocks if empty
    process(e);
}
```

Use `ArrayBlockingQueue` (bounded, single lock) or `LinkedBlockingQueue` (bounded, separate head/tail locks — higher throughput). Avoid `SynchronousQueue` unless you specifically want handoff with zero buffering.

---

## 9. Virtual Threads (Java 21)

```java
// Old: platform thread per task (expensive, ~1MB stack)
ExecutorService old = Executors.newFixedThreadPool(200);

// New: virtual thread per task (cheap, ~KB, millions possible)
ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();

// Or directly:
Thread.ofVirtual().start(() -> handleRequest(request));
```

Virtual threads are **M:N mapped** to platform (OS) threads. The JVM schedules them and automatically unmounts a virtual thread when it blocks on IO, allowing the platform thread to run another virtual thread.

**What changes:**
- IO-bound services can use thread-per-request model again (like Go goroutines).
- No more reactive/async spaghetti just for throughput.
- `synchronized` blocks still pin the virtual thread to its carrier — use `ReentrantLock` for IO-heavy critical sections.

**What does NOT change:**
- CPU-bound tasks: limited by CPU cores, virtual threads don't help.
- Memory: virtual threads have tiny stacks, but your objects still use heap.
- Thread-locals: still work but can waste memory with millions of threads — consider `ScopedValue` (preview in Java 21).

---

## 10. Concurrency Bugs

### Race Condition

```java
// BAD: non-atomic check-then-act
if (balance >= amount) {           // Thread A reads: 100
    balance -= amount;             // Thread B reads: 100 — both proceed!
}

// FIX: use synchronized or AtomicInteger.compareAndSet()
```

### Deadlock

```java
// Thread 1: lock A then B
// Thread 2: lock B then A → deadlock

// FIX: always acquire locks in a consistent global order
// Enforce by comparing System.identityHashCode() of objects
```

### Starvation

A low-priority thread never gets CPU time because high-priority threads always win. Fix: fair lock (`new ReentrantLock(true)`), or ensure bounded wait times.

### Livelock

Threads keep changing state in response to each other but never progress (like two people stepping aside for each other in a hallway). Fix: introduce randomness or back-off.

---

## 11. Interview Q&A

**Q: What is the difference between `volatile` and `synchronized`?**  
`volatile` guarantees visibility but not atomicity — reading and writing a `volatile` field is atomic, but compound operations are not. `synchronized` guarantees both visibility and atomicity by establishing a happens-before relationship and allowing only one thread in the critical section at a time.

**Q: When would you use `LongAdder` instead of `AtomicLong`?**  
When you have many threads all incrementing the same counter (high contention). `AtomicLong` uses a single CAS loop, and under contention many threads spin retrying. `LongAdder` splits the counter into per-stripe cells, reducing contention at the cost of a more expensive `sum()` call.

**Q: What is the virtual thread pinning problem?**  
When a virtual thread executes a `synchronized` block it becomes "pinned" to its carrier platform thread — the carrier cannot be released to run other virtual threads during that block. If the virtual thread then blocks on IO inside that `synchronized` block, the carrier blocks too, reducing throughput. Fix: replace `synchronized` with `ReentrantLock` for IO-heavy critical sections.

**Q: What is happens-before and why does it matter?**  
The JVM and CPU are free to reorder memory operations for performance. The happens-before relationship defines a set of rules that prevent certain reorderings. If A happens-before B, then all of A's memory writes are visible to B. Without these guarantees, a correctly-looking program can observe stale data. `volatile` writes, synchronized blocks, and `Thread.start()` all establish happens-before edges.
