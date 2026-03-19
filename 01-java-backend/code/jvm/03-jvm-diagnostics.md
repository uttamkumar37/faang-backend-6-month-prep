# JVM Diagnostics — Tools, Techniques, and Troubleshooting

---

## 1. JVM Diagnostic Toolbox Overview

```
Problem                  Tool(s)
─────────────────────────────────────────────────────
High CPU                 async-profiler (CPU), jstack, JFR
Memory leak / OOM        jmap (heap dump), Eclipse MAT, JFR
GC pressure              -Xlog:gc*, JFR, GCeasy.io
Thread deadlock          jstack, jcmd Thread.print
Slow method              async-profiler (wall-clock), JFR method profiling
Startup slowness         JFR startup profiling, -verbose:class
Native leak              jmap, NativeMemoryTracking (-XX:NativeMemoryTracking=detail)
```

---

## 2. `jps` — List Java Processes

```bash
jps -l        # PID + full main class name
jps -v        # PID + JVM flags
jps -lv       # both

# Output:
#  12345 com.example.OrderServiceApplication
#  67890 org.apache.kafka.Kafka
```

**Why use it**: find the PID of your running Java service before using other tools.

---

## 3. `jstack` — Thread Dump

A **thread dump** is a snapshot of every thread's stack trace at a point in time. Essential for diagnosing:
- Deadlocks (threads blocking each other)
- High CPU usage (find which thread/method is stuck in a loop)
- Thread pool exhaustion (all threads blocked on I/O)

```bash
# Take a thread dump
jstack <PID>                  # print to stdout
jstack <PID> > /tmp/td.txt   # save to file

# Or via jcmd (preferred for modern JVMs)
jcmd <PID> Thread.print > /tmp/td.txt

# Force dump from inside the process (no external tool needed)
kill -3 <PID>   # sends SIGQUIT — JVM prints thread dump to stderr
```

**Reading a thread dump**:

```
"http-nio-8080-exec-1" #57 daemon prio=5 os_prio=0 cpu=123.45ms elapsed=3600s tid=0x00007f1234 nid=0x1234
   java.lang.Thread.State: WAITING (parking)
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for <0x0000000780a5b3d0> (java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:194)
        at com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:126)   ← waiting for DB connection!
        at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:213)

Column meanings:
"http-nio-8080-exec-1"  thread name
#57                     thread number
daemon                  is it a daemon thread?
cpu=123.45ms            CPU time consumed
Thread.State: WAITING   thread state
```

**Thread states**:

| State | Meaning |
|---|---|
| `RUNNABLE` | Running or ready to run. May actually be executing or waiting for OS to schedule |
| `WAITING` | Waiting indefinitely for another thread to act (LockSupport.park, Object.wait) |
| `TIMED_WAITING` | Waiting with timeout (Thread.sleep, LockSupport.parkNanos) |
| `BLOCKED` | Trying to acquire a `synchronized` lock held by another thread |
| `NEW` | Created but not yet started |
| `TERMINATED` | Finished execution |

**Deadlock detection**: `jstack` automatically detects and reports deadlocks at the bottom of the dump:

```
Found one Java-level deadlock:
===================================
"Thread-A":
  waiting to lock monitor 0xABC for Object <0x123> (ConectionManager)
  which is held by "Thread-B"
"Thread-B":
  waiting to lock monitor 0XEF for Object <0x456> (UserCache)
  which is held by "Thread-A"
```

**Analyzing for high CPU** — take 3 dumps 5 seconds apart and look for threads that are `RUNNABLE` across all three at the same frame.

---

## 4. `jmap` — Heap Information

```bash
# Heap summary (quick check)
jmap -heap <PID>
# Output shows: GC type, heap regions, used/capacity per region

# Histogram of live objects by class (lightweight, no dump)
jmap -histo:live <PID> | head -30
# Output:
# num     #instances         #bytes  class name
#   1:        500000       80000000  [B  (byte arrays)
#   2:        200000       25600000  java.lang.String
#   3:         50000       12800000  com.example.OrderDto

# Full heap dump (heavy — pauses app briefly)
jmap -dump:live,format=b,file=/tmp/heap.hprof <PID>

# Or trigger on OOM automatically (set at startup)
# -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
```

**When to use histo vs full dump**:
- `jmap -histo:live` — quick check in production (< 1s, tiny pause), see which class is consuming most memory
- Full `jmap -dump` — for deep leak analysis, but pauses app for seconds on large heaps; prefer JFR for production

---

## 5. `jcmd` — Swiss-Army Knife (modern, preferred)

`jcmd` is the recommended replacement for `jstack`, `jmap`, `jinfo`. One tool, many commands:

```bash
# List all available commands for a PID
jcmd <PID> help

# Thread dump
jcmd <PID> Thread.print > /tmp/td.txt

# Heap histogram
jcmd <PID> GC.heap_info
jcmd <PID> GC.class_histogram | head -30

# Heap dump
jcmd <PID> GC.heap_dump filename=/tmp/heap.hprof

# JVM flags in use
jcmd <PID> VM.flags

# System properties
jcmd <PID> VM.system_properties

# Uptime
jcmd <PID> VM.uptime

# Native memory breakdown (requires NativeMemoryTracking)
jcmd <PID> VM.native_memory summary

# Trigger GC (use with caution in production!)
jcmd <PID> GC.run
```

---

## 6. Java Flight Recorder (JFR) — Production-Safe Profiler

JFR is built into the JVM (Java 11+, free production use after Java 11). It continuously records JVM and app events with **< 1% overhead**.

### Start a recording

```bash
# Option 1: At startup
java -XX:StartFlightRecording=filename=/tmp/app.jfr,duration=60s,settings=profile -jar app.jar

# Option 2: Attach to running process
jcmd <PID> JFR.start name=diag settings=profile duration=60s filename=/tmp/diag.jfr

# Option 3: Always-on circular buffer (dump on demand)
jcmd <PID> JFR.start name=continuous settings=default maxage=5m
# ... later, when problem detected:
jcmd <PID> JFR.dump name=continuous filename=/tmp/problem.jfr

# Stop a recording
jcmd <PID> JFR.stop name=diag

# Check running recordings
jcmd <PID> JFR.check
```

### Analyze with JDK Mission Control (JMC)

```bash
# Download from https://adoptium.net/jmc/
jmc &    # open GUI, load .jfr file
```

**Key JFR views to examine**:

| Tab | What to look for |
|---|---|
| Automated Analysis | JMC automatically flags problems — read these first |
| CPU → Method Profiling | Which methods are hot (top CPU consumers) |
| Memory → GC | GC pause times, allocation rate, heap usage trend |
| Memory → Heap Statistics | Which class instances are taking most memory |
| Memory → TLAB Allocations | Where objects are being allocated (allocation profiling) |
| Threads → Thread Dump | Periodic thread dumps captured during recording |
| I/O → Socket I/O | Slow network calls |
| I/O → File I/O | Disk bottlenecks |
| Lock → Java Monitor Statistics | Contended locks (cause of blocking) |

### JFR in code — custom events

```java
// Create a custom JFR event for business operations
@Name("com.example.OrderProcessing")
@Label("Order Processing")
@Description("Tracks order processing time")
@Category("Business")
public class OrderProcessingEvent extends jdk.jfr.Event {
    @Label("Order ID") public String orderId;
    @Label("Items Count") public int itemCount;
    @Label("Total Amount") public double totalAmount;
}

// Usage
OrderProcessingEvent event = new OrderProcessingEvent();
event.begin();
event.orderId = order.getId();
event.itemCount = order.getItems().size();
try {
    processOrder(order);
} finally {
    event.totalAmount = order.getTotal();
    event.commit(); // records duration automatically
}
```

---

## 7. async-profiler — CPU + Allocation Profiling

[async-profiler](https://github.com/jvm-profiling-tools/async-profiler) uses perf/dtrace for accurate CPU profiling — catches C code, GC, and JIT compilation unlike JVMTI profilers.

```bash
# Download and extract async-profiler
# https://github.com/async-profiler/async-profiler/releases

# CPU profiling — 30 seconds, output flame graph
./asprof -d 30 -f /tmp/cpu-flame.html <PID>

# Allocation profiling — find where objects are created
./asprof -e alloc -d 30 -f /tmp/alloc-flame.html <PID>

# Wall-clock profiling — includes threads blocked on I/O
./asprof -e wall -d 30 -f /tmp/wall-flame.html <PID>

# Lock profiling — find what's causing blocking
./asprof -e lock -d 30 -f /tmp/lock-flame.html <PID>
```

**Reading a flame graph**:
```
Top of flame = leaves (methods doing actual work)
Bottom = entry points (main, thread start)
Width = time spent (wide = hot = worth optimizing)
Click to zoom in

   [String.chars]
   [OrderValidator.validate]         ←── wide: this method is hot!
   [OrderService.processOrder]
   [OrderController.submitOrder]
   [DispatcherServlet.doDispatch]
   [Thread.run]
```

---

## 8. `jstat` — GC Statistics Live

```bash
# GC stats every 1 second, 20 times
jstat -gc <PID> 1000 20
# Columns: S0C S1C S0U S1U EC EU OC OU MC MU YGC YGCT FGC FGCT GCT

# Easier to read: GC cause and utilization
jstat -gcutil <PID> 1000 20
# S0  S1   E    O    M    YGC  YGCT  FGC  FGCT   GCT
# 0.0 80.5 72.3 45.2 97.6 156  1.432  3   0.892  2.324

# Column meanings:
# E = Eden % used
# O = Old gen % used
# YGC = Young GC count
# FGC = Full GC count (should be ~0 in healthy apps)
# GCT = total GC time in seconds
```

**Healthy signs**: FGC stays at or near 0. Old gen (%O) stays below 70-80%. Eden (%E) cycles up and down.

---

## 9. Native Memory Tracking (NMT)

Helps when the JVM process uses MORE memory than `-Xmx` indicates — native (off-heap) leak:

```bash
# Enable at startup (slight overhead ~5-10%)
java -XX:NativeMemoryTracking=summary -jar app.jar

# Get native memory summary
jcmd <PID> VM.native_memory summary

# Output example:
# Native Memory Tracking:
# Total: reserved=5120MB, committed=1024MB
#
# -              Java Heap (reserved=4096MB, committed=512MB)
#                               (mmap: reserved=4096MB, committed=512MB)
#
# -              Class (reserved=1GB, committed=128MB)
#                               (classes #25000)
# -              Thread (reserved=50MB, committed=50MB)
#                               (thread #100, stack: reserved=50MB, committed=50MB)
# -              Code (reserved=250MB, committed=50MB)
#                               (mmap: reserved=250MB, committed=50MB)
# -              GC (reserved=10MB, committed=10MB)
# -        Compiler (reserved=1MB, committed=1MB)
# -          Symbol (reserved=30MB, committed=30MB)
# -   Native Memory Tracking (reserved=3MB, committed=3MB)
# -        Arena Chunk (reserved=2MB, committed=2MB)
# -             Other (reserved=10MB, committed=10MB)  ← look here for non-JVM native

# Detailed (for leak investigation — more overhead)
java -XX:NativeMemoryTracking=detail
jcmd <PID> VM.native_memory detail
```

---

## 10. Heap Dump Analysis with Eclipse MAT

MAT (Memory Analyzer Tool) is the gold standard for heap dump analysis.

```bash
# 1. Get heap dump
jcmd <PID> GC.heap_dump filename=/tmp/heap.hprof
# or automatically on OOM: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/

# 2. Open in Eclipse MAT
#    Download: https://eclipse.dev/mat/
#    Open: File → Open Heap Dump → select .hprof

# 3. Key actions in MAT:
#    - "Leak Suspects" report: auto-analysis of top memory hogs
#    - Dominator tree: which objects "own" most memory (retained heap)
#    - OQL queries: Object Query Language for custom investigation
```

**MAT OQL examples**:

```sql
-- Find all String objects longer than 1000 chars
SELECT * FROM java.lang.String s WHERE s.value.length > 1000

-- Find all HttpSession objects
SELECT * FROM javax.servlet.http.HttpSession

-- Find objects of specific class holding most memory
SELECT * FROM com.example.OrderCache

-- Find ArrayList with more than 10000 elements
SELECT * FROM java.util.ArrayList a WHERE a.size > 10000
```

**Retained vs Shallow heap**:
- **Shallow heap**: memory occupied by the object itself (fields only)
- **Retained heap**: memory freed if this object and everything only reachable through it were collected. A `Map` with 1M entries has small shallow heap but huge retained heap.

In the dominator tree, sort by **retained heap** to find the real memory hogs.

---

## 11. Common JVM Problems and Diagnosis Workflow

### OOM: Java heap space
```
java.lang.OutOfMemoryError: Java heap space

Diagnosis:
1. jmap -histo:live <PID> | head -20 → find class with exploding instance count
2. Heap dump → MAT Leak Suspects
3. Look for: unbounded caches, session objects not expiring, event listeners not removed
Fix: fix the leak, or increase -Xmx as temporary measure
```

### OOM: Metaspace
```
java.lang.OutOfMemoryError: Metaspace

Meaning: class metadata is growing — typically due to class leaks
Diagnosis: jstat -gc → MC (Metaspace capacity) growing unboundedly
           Check for frameworks doing dynamic proxy generation in a loop
           jcmd <PID> GC.class_histogram | grep "$$" (dynamic proxy classes)
Fix: -XX:MaxMetaspaceSize=256m (caps it, converts to OOME earlier)
     Fix the class leak (usually dynamic classloading without proper classloader unloading)
```

### StackOverflowError
```
java.lang.StackOverflowError

Meaning: thread stack overflow — infinite recursion
Diagnosis: jstack reveals the thread with 1000s of identical frames
Fix: fix the recursion bug
     If legitimate deep recursion: -Xss2m (increase stack size per thread, default 512k-1m)
```

### High CPU — not GC
```
1. top → find Java process PID
2. top -H -p <PID> → find thread IDs consuming CPU (decimal)
3. printf '%x\n' <thread-id> → convert to hex
4. jstack <PID> → find thread "nid=0x<hex>" in dump → look at its stack trace
```

### Slow startup — class loading
```
# Verbose class loading log
java -verbose:class -jar app.jar 2>&1 | head -100
# Shows: [0.123s][info][class,load] Loading com.example.MyClass source: jar:file://...

# Check for duplicate JAR files on classpath (same class loaded from multiple jars)
# Check for slow classpath scanning
```

---

## 12. Production Diagnostics Cheat Sheet

```bash
# Is my app healthy? Quick check
jcmd <PID> VM.uptime
jstat -gcutil <PID> 1000 5          # GC patterns
jmap -histo:live <PID> | head -20   # top memory consumers

# Is there a deadlock?
jstack <PID> | grep -A 20 "deadlock"

# Is a thread pool saturated?
jstack <PID> | grep "http-nio" | grep -c "WAITING\|BLOCKED"

# Is GC causing problem?
jstat -gcutil <PID> 1000 10 | awk '{print $10, $11}' # YGC count + time

# What JVM flags are active?
jcmd <PID> VM.flags | tr ' ' '\n' | grep -E "Xmx|GC|Heap|Thread"

# Start a 60s JFR recording for analysis
jcmd <PID> JFR.start name=quick settings=profile duration=60s filename=/tmp/quick.jfr
```

---

## 13. Interview Q&A

**Q: How do you diagnose a memory leak in a Java application?**  
Start with `jmap -histo:live <PID>` to see which class has an unexpectedly large number of instances — it's fast (< 1s) and safe in production. Then take a full heap dump (`jcmd <PID> GC.heap_dump`) and analyze with Eclipse MAT. Use the "Leak Suspects" report for automated analysis, then check the dominator tree (sort by retained heap) to find what's actually holding memory. Common culprits: unbounded caches, static collections growing forever, unclosed resources, inner class references to outer objects.

**Q: What's the difference between a thread dump and a heap dump?**  
A **thread dump** captures the state of all threads and their stack traces at a point in time — used to diagnose deadlocks, thread pool exhaustion, or pinpointing which thread is consuming CPU. A **heap dump** captures a snapshot of all objects in the JVM heap — used to diagnose memory leaks, OOM errors, and excessive memory usage. Thread dump = who is doing what. Heap dump = what objects exist and how much memory they use.

**Q: How would you diagnose high CPU usage in a Java service?**  
(1) Use `top` to confirm Java is the CPU consumer. (2) Use `top -H -p <PID>` to find which OS thread (TID) is hot. (3) Convert TID to hex: `printf '%x\n' <tid>`. (4) Take a `jstack <PID>` thread dump, find the thread with that hex nid. (5) Examine its stack trace — often infinite recursion, hot loop, or busy-wait in cache/retry logic. (6) For sustained analysis, use async-profiler: `./asprof -d 30 -f /tmp/cpu.html <PID>` to get a CPU flame graph.

**Q: What is JFR and why is it better than sampling profilers?**  
Java Flight Recorder (JFR) is a low-overhead (< 1%) always-on event recorder built into the JVM. It captures JVM internals (GC pauses, JIT compilation, class loading, I/O, thread scheduling, locks) alongside application events — not just CPU samples. Regular JVMTI profilers take thread dumps at fixed intervals and suffer from "sampling bias" — they miss short-lived methods and interpret `WAITING` threads poorly. JFR captures actual durations of events (e.g., "this lock was held for 45ms") rather than snapshots, giving accurate attribution. It's safe for production use; traditional profilers add 5–30% overhead.
