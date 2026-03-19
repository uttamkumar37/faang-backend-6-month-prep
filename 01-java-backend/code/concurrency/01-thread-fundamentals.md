# Thread Fundamentals — Basics to Advanced

---

## 1. What is a Thread?

A **process** is an independent program with its own memory space. A **thread** is a lightweight unit of execution within a process — multiple threads share the same heap memory but each has its own:

- **PC register** (program counter — which instruction to execute next)
- **Stack** (method call frames, local variables)  
- **Thread-local storage**

```
Process (JVM)
├─ Heap (shared by all threads)
│   ├─ Objects, arrays, String pool
│   ├─ Static fields (Metaspace)
└─ Threads (each has private)
    ├─ Thread-1: [Stack] [PC]
    ├─ Thread-2: [Stack] [PC]
    └─ Thread-N: [Stack] [PC]
```

**Why threads?** Concurrency allows doing multiple things "at the same time" — serving many HTTP requests in parallel, overlapping CPU work with I/O waiting, using all CPU cores.

---

## 2. Creating Threads — All Ways

```java
// Way 1: extend Thread (tight coupling — not recommended)
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Running in: " + Thread.currentThread().getName());
    }
}
new MyThread().start();  // start() creates OS thread and calls run()

// Way 2: implement Runnable (preferred interface)
Runnable task = () -> System.out.println("Running in: " + Thread.currentThread().getName());
Thread t = new Thread(task, "my-thread");
t.start();

// Way 3: Callable (returns a result, can throw checked exceptions)
Callable<Integer> callable = () -> {
    Thread.sleep(100);
    return 42;
};
FutureTask<Integer> ft = new FutureTask<>(callable);
new Thread(ft).start();
Integer result = ft.get(); // blocks until complete

// Way 4: ExecutorService (recommended for production — thread reuse, pooling)
ExecutorService exec = Executors.newFixedThreadPool(4);
exec.submit(() -> System.out.println("Pooled task"));
exec.shutdown();

// IMPORTANT: always use .start(), NEVER .run()
// t.run() → executes synchronously on the CURRENT thread (no new thread created!)
// t.start() → creates new OS thread, which calls run() on it
```

---

## 3. Thread Lifecycle and States

```
              start()
  NEW ──────────────────────► RUNNABLE ◄──────────────────────────────────┐
                               │    ▲                                      │
                  synchronized │    │ lock acquired                        │
                      blocked  ▼    │                                      │
                            BLOCKED ─────────────────────────────────────►┤
                                                                           │
              Object.wait()  ▼                  notify/notifyAll()        │
              LockSupport.park()                   unpark()                │
              Thread.join()                        join completes          │
                           WAITING ─────────────────────────────────────►┤
                                                                           │
              Thread.sleep(n)                   timeout elapsed            │
              Object.wait(n)                    notify/timeout             │
              LockSupport.parkNanos()            unpark/timeout            │
                      TIMED_WAITING ───────────────────────────────────►┤
                                                                           │
                                                              TERMINATED ◄─┘
```

**RUNNABLE** includes both "actually executing on CPU" and "ready, waiting for OS to schedule" — Java doesn't distinguish.

```java
Thread t = new Thread(() -> {
    System.out.println("State inside: " + Thread.currentThread().getState()); // RUNNABLE
});
System.out.println("Before start: " + t.getState()); // NEW
t.start();
t.join(); // TIMED_WAITING or WAITING (depending on join signature)
System.out.println("After finish: " + t.getState()); // TERMINATED
```

---

## 4. Thread Identity and Properties

```java
Thread t = new Thread(() -> {}, "worker-1");

t.getName()            // "worker-1"
t.getId()              // unique long ID
t.getPriority()        // 1-10, default = 5 (Thread.NORM_PRIORITY)
t.isDaemon()           // false by default
t.getState()           // Thread.State enum
t.isAlive()            // true between start() and termination
t.isInterrupted()      // whether interrupt flag is set

// Current thread
Thread.currentThread().getName()
Thread.currentThread().getId()

// Priority — HINT to OS scheduler, not guaranteed
t.setPriority(Thread.MAX_PRIORITY); // 10
// In practice, priority has minimal effect on modern Linux (CFS scheduler)
```

---

## 5. Daemon Threads

**Daemon threads** are background service threads. The JVM exits when ALL non-daemon threads finish — daemon threads are killed without cleanup.

```java
Thread daemonThread = new Thread(() -> {
    while (true) {
        System.out.println("Background task...");
        try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
    }
});
daemonThread.setDaemon(true); // MUST set before start()
daemonThread.start();

// When main() finishes → JVM exits → daemon thread killed immediately
// GC threads, JIT threads, Finalizer thread are all daemon threads
```

---

## 6. Thread Interruption — The Right Way

Java threads can't be forcibly killed. The interruption model is **cooperative** — you signal a thread to stop, and it should check and respond:

```java
// Requesting termination: set the interrupt flag
thread.interrupt(); // sets the interrupted flag; doesn't stop the thread

// Checking inside the thread
while (!Thread.currentThread().isInterrupted()) {
    doWork();
}

// Blocking methods (sleep, wait, join, park) respond to interruption:
try {
    Thread.sleep(1000); // throws InterruptedException when flag is set
} catch (InterruptedException e) {
    // The flag is cleared when InterruptedException is thrown!
    Thread.currentThread().interrupt(); // re-set the flag!
    return; // or break; — propagate the fact that we were interrupted
}

// ANTIPATTERNS:
// ❌ catch (InterruptedException e) {} — swallows interrupt, thread never stops
// ❌ Thread.stop() — DEPRECATED, causes inconsistent state (breaks locks)
// ❌ Using a volatile boolean flag instead of interrupt — OK but reinvents the wheel
```

```java
// Example: interruptible worker
class Worker implements Runnable {
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                processNextTask();     // may throw if interrupted during blocking call
                Thread.sleep(10);      // cooperative sleep
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag
                break; // clean exit
            }
        }
        cleanup(); // always runs on clean shutdown
    }
}
```

---

## 7. `Thread.sleep()`, `yield()`, `join()`

```java
// sleep — pause this thread for at least N ms (no lock released)
Thread.sleep(100);    // milliseconds
Thread.sleep(0, 500); // milliseconds + nanoseconds

// yield — hint to scheduler: "I can give up my time slice now"
Thread.yield(); // may do nothing — just a hint, not guaranteed
// Use: compute-intensive loops where you don't want to starve other threads

// join — wait for another thread to finish
Thread t = new Thread(longTask);
t.start();
t.join();        // block until t finishes
t.join(5000);    // block at most 5 seconds
System.out.println("t is done: " + !t.isAlive());
```

---

## 8. ThreadLocal — Per-Thread Variables

`ThreadLocal<T>` gives each thread its own independent copy of a variable:

```java
// Classic use: per-request context (user ID, correlation ID, tenant ID)
public class RequestContext {
    private static final ThreadLocal<String> correlationId = new ThreadLocal<>();
    private static final ThreadLocal<User> currentUser = ThreadLocal.withInitial(() -> null);

    public static void setCorrelationId(String id) { correlationId.set(id); }
    public static String getCorrelationId()         { return correlationId.get(); }
    public static void clear()                      { correlationId.remove(); currentUser.remove(); }
}

// In a servlet filter (sets at request start, clears at end)
public void doFilter(HttpServletRequest req, ...) {
    try {
        RequestContext.setCorrelationId(req.getHeader("X-Correlation-ID"));
        chain.doFilter(req, res);
    } finally {
        RequestContext.clear(); // CRITICAL: prevents leak in thread pools!
    }
}
```

**ThreadLocal memory leak** — the most common mistake:

```java
// PROBLEM: Thread pool threads are long-lived.
// ThreadLocal value is stored in the Thread object itself.
// If you set a ThreadLocal but never call .remove(), the value stays in memory
// as long as the thread exists (forever in a pool!).
// This is especially bad with ClassLoader references in web apps (class unloading failure).

// FIX: always call ThreadLocal.remove() in a finally block
// FIX: use InheritableThreadLocal or Spring's RequestContextHolder which auto-cleans
```

### InheritableThreadLocal — Pass to child threads

```java
// InheritableThreadLocal: child threads inherit parent's value at birth
private static final InheritableThreadLocal<String> traceId = new InheritableThreadLocal<>();

traceId.set("trace-123");
Thread child = new Thread(() -> {
    System.out.println(traceId.get()); // "trace-123" — inherited from parent
});
child.start();

// WARNING: with thread pools, inheritance only happens when thread is CREATED.
// Pool threads are created once — subsequent tasks won't inherit new values.
// For virtual threads and executors, use ScopedValue (Java 21 preview) instead.
```

---

## 9. Object Wait/Notify — Low-Level Signaling

`wait()`/`notify()`/`notifyAll()` are the low-level building blocks for thread coordination. Must be called from a `synchronized` block:

```java
class BoundedBuffer<T> {
    private final Object[] items = new Object[100];
    private int putPos = 0, takePos = 0, count = 0;

    public synchronized void put(T item) throws InterruptedException {
        while (count == items.length) {
            wait(); // releases lock and waits — reacquires lock when woken
        }
        items[putPos] = item;
        putPos = (putPos + 1) % items.length;
        count++;
        notifyAll(); // wake all waiting consumers (and producers)
    }

    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) {
            wait(); // release lock, wait for producer
        }
        T item = (T) items[takePos];
        takePos = (takePos + 1) % items.length;
        count--;
        notifyAll(); // wake waiting producers
        return item;
    }
}
```

**Why `while` not `if` for wait?** — Spurious wakeups. A thread can wake up without `notify()` being called. Always re-check the condition in a loop after `wait()` returns. This is called the "guard loop" or "spin-wait" pattern.

**`notify()` vs `notifyAll()`**:
- `notify()` wakes ONE random waiting thread — can cause starvation if the wrong thread is woken
- `notifyAll()` wakes ALL waiting threads — safer, all re-check their conditions
- Use `notify()` only when you're certain all waiting threads are waiting for the same condition

---

## 10. Virtual Threads vs Platform Threads (Java 21)

```java
// Platform (OS) thread — ~1MB stack, managed by OS
Thread.ofPlatform().name("platform-1").start(task);
Executors.newFixedThreadPool(200);  // max ~200 practical

// Virtual thread — ~few KB stack, managed by JVM carrier pool
Thread.ofVirtual().name("virtual-1").start(task);
Executors.newVirtualThreadPerTaskExecutor(); // 1M+ concurrent

// Key JVM flags for thread monitoring
-Djdk.trackAllThreads=true    // track all virtual threads in jstack
-XX:+PrintVMOptions

// Virtual threads are NOT faster for CPU-bound work
// They shine when threads are mostly BLOCKED on I/O
```

Virtual thread internals:

```
JVM maintains a ForkJoinPool of "carrier" (platform) threads (= CPU count)

Virtual thread executing:
├─► Carrier thread 1 ──── [virtual thread A running]
├─► Carrier thread 2 ──── [virtual thread B running]
└─► Carrier thread N ──── [virtual thread C running]

Virtual thread blocks on I/O:
    JVM "unmounts" it from carrier thread
    Carrier thread is FREE to run another virtual thread
    When I/O completes, virtual thread is rescheduled on any available carrier
```

---

## 11. Thread Groups — Legacy

```java
// ThreadGroups are mostly a legacy API (pre-Java 5).
// The main use case today is exception handling:
ThreadGroup group = new ThreadGroup("workers") {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        System.err.println("Thread " + t.getName() + " crashed: " + e.getMessage());
        // Log, alert, etc.
    }
};
Thread t = new Thread(group, task, "worker-1");
t.start();

// Better modern alternative: Thread.setDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    log.error("UNCAUGHT exception in thread {}", t.getName(), e);
    alertingService.sendAlert("Thread crash: " + t.getName());
});
```

---

## 12. Common Thread Pitfalls

### Starting a thread inside a constructor — never do this

```java
public class Service {
    private final List<String> data = new ArrayList<>();
    
    public Service() {
        // DANGER: 'this' escapes before constructor finishes!
        // Another thread might see Service in partially-constructed state
        new Thread(this::processData).start();
    }
}

// FIX: factory method pattern
public static Service create() {
    Service s = new Service();  // constructor finishes
    s.startBackgroundThread();  // now safe to pass 'this'
    return s;
}
```

### Sharing mutable state without synchronization

```java
// WRONG: counter++ is NOT atomic (it's read-modify-write = 3 operations)
private int counter = 0;
void increment() { counter++; } // data race!

// FIX: use AtomicInteger
private final AtomicInteger counter = new AtomicInteger(0);
void increment() { counter.incrementAndGet(); }
```

---

## 13. Interview Q&A

**Q: What is the difference between `sleep()` and `wait()`?**  
`Thread.sleep(ms)` pauses the current thread for *at least* the given time — it does NOT release any locks held. It's a static method, always acts on the current thread. `Object.wait()` must be called from a `synchronized` block — it atomically releases the lock and suspends the thread until `notify()` or `notifyAll()` is called. When re-awakened, it re-acquires the lock before returning. Use `sleep` for timed delays, `wait`/`notify` for thread coordination on a shared condition.

**Q: What happens when you call `interrupt()` on a sleeping thread?**  
If the thread is in `TIMED_WAITING` (sleeping) or `WAITING` (in `wait()`/`join()`), an `InterruptedException` is immediately thrown, the thread wakes up, and the interrupt flag is **cleared** by the JVM. If the thread is `RUNNABLE`, only the flag is set — no exception occurs. The thread must check `Thread.currentThread().isInterrupted()` periodically to notice. After catching `InterruptedException`, you should call `Thread.currentThread().interrupt()` to restore the flag, allowing callers up the stack to see the interruption.

**Q: What is a ThreadLocal and what's its memory leak risk?**  
`ThreadLocal<T>` stores a per-thread value — each thread sees its own isolated copy. The value is stored in a `ThreadLocalMap` inside the `Thread` object itself. The leak risk: in thread pools, threads are never garbage-collected. If you call `set()` but never `remove()`, the value lives in the thread forever (class instances, class loaders, potentially gigabytes). In web containers this causes class unloading failures on redeployment. Fix: always call `remove()` in a `finally` block after using the value.

**Q: What's the difference between `notify()` and `notifyAll()`?**  
Both are called on a monitor object from a `synchronized` block. `notify()` wakes exactly one waiting thread (chosen arbitrarily by the JVM) — if there are multiple threads waiting for different conditions on the same monitor, wrong threads may be woken, causing livelock or performance issues. `notifyAll()` wakes all waiting threads — each re-checks its condition and re-waits if not satisfied. Generally safer to use `notifyAll()` unless you can guarantee all waiters are waiting for the same single condition and the action of one wakeup can satisfy exactly one of them.
