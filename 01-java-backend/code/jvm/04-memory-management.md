# Memory Management & Object Lifecycle

## 1. Object Lifecycle

```
1. new Allocated keyword compiled → INVOKEVIRTUAL + NEW bytecodes
2. JVM allocates on Eden (Young Gen) using TLAB (Thread-Local Allocation Buffer)
3. Object initializer runs
4. References held by stack frames keep object reachable
5. If referenced across Minor GC cycles → promoted to Survivor → Old Gen
6. References released → object becomes unreachable → eligible for GC
7. Before collection: finalize() (deprecated) or Cleaner runs if registered
8. Memory reclaimed
```

### Thread-Local Allocation Buffer (TLAB)

Each thread gets a small private memory area in Eden. Object allocation = bump-a-pointer: O(1), no locking. TLAB is flushed during GC. This makes `new` cheap.

---

## 2. Reference Types

| Type | Cleared when | Use case |
|---|---|---|
| `StrongReference` | Never by GC | Normal objects |
| `SoftReference<T>` | Only when JVM is about to OOM | Image caches, last-resort caches |
| `WeakReference<T>` | Next GC cycle when no strong refs | `WeakHashMap`, canonicalizing maps |
| `PhantomReference<T>` | After finalization, before memory release | Off-heap resource cleanup, safer than finalize |

```java
// Soft reference cache
Map<String, SoftReference<byte[]>> imageCache = new ConcurrentHashMap<>();
imageCache.put("logo", new SoftReference<>(loadBytes("logo.png")));

byte[] logo = Optional.ofNullable(imageCache.get("logo"))
    .map(SoftReference::get)
    .orElseGet(() -> reloadAndCache("logo"));
```

### WeakHashMap

Automatically removes entries when the key has no more strong references. Common use: metadata maps where entries should expire with their subject.

```java
WeakHashMap<Servlet, RequestMetrics> metrics = new WeakHashMap<>();
// When servlet is undeployed and GC'd, its entry auto-removes
```

---

## 3. Memory Leaks — Common Patterns

### Static collections accumulating over time

```java
// BAD — grows forever
private static final List<String> auditLog = new ArrayList<>();
public void process(Request r) {
    auditLog.add(r.getDescription()); // never cleared
}
```

### Unclosed streams and connections

```java
// BAD
InputStream in = new FileInputStream(file);
// process...
// forgot in.close() → file descriptor leak

// GOOD — try-with-resources
try (InputStream in = new FileInputStream(file)) {
    // JVM calls in.close() automatically
}
```

### Inner class holding outer class reference

```java
class Outer {
    private byte[] data = new byte[100_000_000]; // 100MB

    class Inner implements Runnable { // non-static inner class
        // Holds implicit reference to Outer instance
        @Override public void run() { /* ... */ }
    }
}
// If Inner.run() is submitted to a long-lived thread pool:
// Outer + its 100MB data array cannot be GC'd
// FIX: make Inner a static nested class or a separate top-level class
```

### Listener registration without deregistration

```java
// BAD: register but never remove
eventBus.register(this);

// GOOD:
try {
    eventBus.register(this);
    doWork();
} finally {
    eventBus.unregister(this);
}
```

### ThreadLocal without remove()

```java
// BAD: with thread pools, threads are reused
ThreadLocal<UserContext> ctx = new ThreadLocal<>();
ctx.set(new UserContext(request)); // set in request
// ... if you forget ctx.remove(), next request on same thread sees old context

// GOOD: always remove in a finally block
try {
    ctx.set(new UserContext(request));
    handleRequest();
} finally {
    ctx.remove();
}
```

---

## 4. Off-Heap Memory

### Direct ByteBuffers

```java
// Allocated outside the JVM heap — not subject to GC heap limits
ByteBuffer direct = ByteBuffer.allocateDirect(1024 * 1024); // 1MB off-heap

// Must be released explicitly or via Cleaner
```

- Used by NIO libraries (Netty, Kafka) for zero-copy transfers.
- Configured by: `-XX:MaxDirectMemorySize=512m`.
- OOM message: `Direct buffer memory`.
- Leaks are invisible to heap profilers. Use `jcmd <pid> VM.native_memory`.

---

## 5. Profiling Tools

### JVM built-in diagnostics

```bash
# JVM heap summary
jmap -heap <pid>

# Histogram of live objects by class
jmap -histo:live <pid> | head -30

# Heap dump for MAT analysis
jmap -dump:format=b,file=heap.hprof <pid>

# GC activity summary
jstat -gcutil <pid> 1000  # every 1s

# JVM flags in use
jcmd <pid> VM.flags
jcmd <pid> VM.native_memory  # off-heap breakdown

# JFR recording (low overhead, production-safe)
jcmd <pid> JFR.start name=ProdRecording duration=60s filename=/tmp/rec.jfr
```

### Analyzing with Eclipse MAT

1. Load `heap.hprof`.
2. Run "Leak Suspects" report — automattically finds large retained object trees.
3. Check "Dominator Tree" — largest retained memory holders.
4. Check "Thread Overview" — see what each thread is holding.

### JFR (Java Flight Recorder)

- < 1% overhead — safe to run in production continuously.
- Captures: GC pauses, memory allocation hotspots, lock contention, exception rate, CPU profile.
- Analyze with JDK Mission Control (JMC).

---

## 6. Interview Q&A

**Q: What is a memory leak in Java and how do you find it?**  
A memory leak in Java is when objects remain reachable (referenced) but are no longer logically needed, preventing GC from reclaiming that memory. Heap grows monotonically over time. Detection: enable GC logging — if heap after each Full GC keeps rising, you have a leak. Take two heap dumps 10+ minutes apart, compare with Eclipse MAT, look for the largest retained object graphs whose size grew between snapshots.

**Q: What is the difference between SoftReference and WeakReference?**  
A `SoftReference` is cleared by the GC only when the JVM is about to throw an `OutOfMemoryError` — the JVM tries to keep soft-reference objects alive as long as possible. A `WeakReference` is cleared at the next GC cycle once there are no strong references, regardless of memory pressure. Use soft references for caches that should degrade gracefully before OOM; use weak references for canonicalizing mappings where entries should expire with their subjects.

**Q: Stack allocation via escape analysis — explain it.**  
JIT's escape analysis determines if an object's reference can "escape" the method that created it (e.g., stored in a field, returned, passed to another method). If an object does not escape, the JVM can allocate it on the stack (or even eliminate it entirely via scalar replacement, replacing the object with its field values stored in registers). Stack allocation means the object is freed when the stack frame pops — no GC pressure at all.
