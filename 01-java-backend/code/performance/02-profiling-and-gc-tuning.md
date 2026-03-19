# Profiling and GC Tuning

---

## 1. The Performance Investigation Workflow

Never tune blindly. Always follow: **measure → profile → identify bottleneck → fix → measure again**.

```
Report: "App is slow"
       │
       ▼
1. Measure: What metric is slow? (latency p99? throughput? startup?)
       │
       ▼
2. Where: Is it CPU-bound? Memory-bound? I/O-bound? Lock contention?
       │
       ├─ High CPU     → CPU Flame Graph (Section 2)
       ├─ High memory  → Heap Profiling (Section 3)
       ├─ GC pauses    → GC Log Analysis (Section 4)
       ├─ Slow queries → DB Profiling (Section 6)
       └─ Contention   → Thread Dump Analysis
```

---

## 2. CPU Profiling — Flame Graphs with async-profiler

**async-profiler** uses OS-level `perf_events` (Linux) or `AsyncGetCallTrace` — it profiles at the native level, capturing JIT-compiled code, GC, and native frames. Unlike JMX/sampling profilers, it doesn't have safepoint bias.

```bash
# Attach to running JVM (replace 1234 with PID from jps)
./profiler.sh -d 60 -f cpu-profile.html 1234

# Profile modes
./profiler.sh -e cpu       # CPU samples (default)
./profiler.sh -e alloc     # allocation profiling (objects created per second)
./profiler.sh -e lock      # lock contention (where threads wait for monitors)
./profiler.sh -e wall      # wall-clock time (includes blocking/sleeping threads)

# Java agent mode (zero-overhead "always on" profiling)
java -agentpath:/path/to/libasyncProfiler.so=start,event=cpu,file=cpu.html MyApp

# Output formats
-f profile.html    # interactive flame graph
-f profile.jfr     # JFR format (open in JMC)
-f profile.svg     # static SVG
```

**Reading a CPU flame graph**:
```
                 ┌─────────────────────────────┐
                 │   HttpServlet.service()      │  ← wide = called often
                 ├──────────────┬──────────────┤
                 │ OrderService │  AuthFilter  │
                 ├──────────────┤              │
                 │  DBPool.get()│              │
                 ├──────────────┘              │
                 │  HikariCP.getConnection()   │
                 └─────────────────────────────┘
                    ↑ bottom-up: call stacks

Rules:
- Width = % CPU time spent in that frame (and all callees)
- Color = category (green=Java, yellow=C++, red=native)
- Look for: (1) wide tops (hot leaves = where CPU actually burns)
           (2) wide middle frames (large call trees)
- Narrow tops with wide bases = plumbing overhead, not actual work
```

---

## 3. Heap Profiling — Allocation Tracing

High allocation rate → frequent GC → latency spikes. Goal: find what's allocating most.

```bash
# Allocation profiling with async-profiler
./profiler.sh -d 30 -e alloc -f alloc.html 1234

# Output shows: flame graph of allocation call stacks
# Top allocators: which methods create the most objects

# JFR allocation profiling
jcmd 1234 JFR.start duration=60s settings=profile filename=alloc.jfr
# Open in JMC → Memory → Allocation Pressure
```

**Common allocation hotspots and fixes**:

| Pattern | Allocator | Fix |
|---|---|---|
| `String+String` in hot loop | String[] | `StringBuilder` |
| `new ArrayList<>()` per request | ArrayList instance | Reuse or pool |
| Boxing `int → Integer` in collections | Integer objects | Use primitive collections (Eclipse Collections, Trove) |
| JSON deserialization per request | many objects | Cache parsed objects |
| `String.format()` in hot path | char[] internally | Pre-format or use concatenation |
| `Exception` creation | stack trace | Pool or cache common exceptions |

---

## 4. GC Log Analysis

Enable GC logging (non-invasive, always on in production):

```bash
# Java 9+ unified logging
-Xlog:gc*:file=/var/log/gc.log:time,uptime,tags:filecount=10,filesize=20m

# Java 8 (older format)
-XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps
-Xloggc:/var/log/gc.log -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=20M
```

**Reading a G1GC log entry**:
```
[2024-01-15T10:23:45.123+0000][0.456s][gc,start] GC(42) Pause Young (Normal) (G1 Evacuation Pause)
[2024-01-15T10:23:45.156+0000][0.489s][gc      ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 1024M->512M(2048M) 33.456ms
                                                          ↑phase        ↑reason         ↑before→after(heap)  ↑pause duration

# Breakdown format
[gc,phases] GC(42)   Pre Evacuate Collection Set:   0.1ms
[gc,phases] GC(42)   Evacuate Collection Set:       28.4ms    ← most time here
[gc,phases] GC(42)   Post Evacuate Collection Set:  2.1ms
[gc,phases] GC(42)   Other:                         2.9ms
```

**GC log analysis with GCEasy.io or GCViewer**:
```bash
# Open browser at https://gceasy.io → upload gc.log
# Or run locally:
java -jar gcviewer.jar gc.log  # shows throughput, pause time histogram
```

**What to look for**:

| Symptom | Likely Cause | Action |
|---|---|---|
| Frequent young GC (< 1s between) | Short-lived object flood / heap too small | Increase heap or fix allocation hotspot |
| Long pause times (> 200ms) | Too much live data / heap too small | Increase `-Xmx`, switch GC |
| Humongous object allocation fallback | Objects > 50% of G1 region size | Increase `-XX:G1HeapRegionSize` |
| Mixed GC never starts | Old gen never hits reclaimable threshold | Lower `-XX:G1HeapWastePercent` |
| Full GC | Promotion failure / metaspace full / explicit `System.gc()` | Increase heap, disable explicit GC |
| Metaspace OOM | Too many classloaders / dynamic proxies | Set `-XX:MaxMetaspaceSize`, fix class leaks |

---

## 5. G1GC Tuning Workflow

G1GC is the default from Java 9+. Start with the simplest configuration and tune incrementally:

```bash
# Step 1: Set heap size (start 4x live data size)
-Xms4g -Xmx4g          # same min and max eliminates heap resizing overhead

# Step 2: Set pause target (default 200ms — lower for latency-sensitive services)
-XX:MaxGCPauseMillis=100   # G1 will try to meet this, not guarantee

# Step 3: Observe GC logs — are pauses hitting the target?
# If young GC pauses exceed target:
-XX:G1NewSizePercent=20    # lower young gen (fewer objects to evacuate)
-XX:G1MaxNewSizePercent=40 # cap young gen growth

# Step 4: Check mixed GC frequency
# If old gen fills up faster than mixed GC can reclaim:
-XX:InitiatingHeapOccupancyPercent=35   # start concurrent marking earlier (default 45)
-XX:G1MixedGCLiveThresholdPercent=65    # only collect regions < 65% live (default 85)
-XX:G1HeapWastePercent=5                # stop mixed GC when reclaimable < 5% (default 10)

# Step 5: Humongous object handling
# Object allocated > 50% of region size → placed in humongous region → Full GC trigger
-XX:G1HeapRegionSize=16m  # increase region size (valid: 1m-32m, power of 2)
# Rule: region size > 2x max single object size

# Step 6: Tune concurrent threads
-XX:ConcGCThreads=4       # threads for concurrent marking (default = ~1/4 of ParallelGCThreads)
-XX:ParallelGCThreads=8   # threads for STW phases (default = num CPUs)
```

---

## 6. ZGC in Production (Java 15+)

ZGC: sub-millisecond pauses, scales to TB heaps, fully concurrent:

```bash
# Enable ZGC (Java 17+ recommended, Generational ZGC Java 21)
-XX:+UseZGC

# Java 21: Generational ZGC (lower allocations, lower CPU overhead)
-XX:+UseZGC -XX:+ZGenerational

# Heap sizing (ZGC needs headroom for concurrent relocation)
-Xmx16g  # set high — ZGC uses more virtual memory but not RSS

# Performance tuning
-XX:ZCollectionInterval=5  # force GC every 5 seconds (prevents lazy warm cache)
-XX:ZFragmentationLimit=25 # limit fragmentation (default 25%)

# Monitor ZGC
-Xlog:gc*:file=zgc.log

# Key ZGC metric: "Allocation Stall"
# If you see: [gc,alloc] Out Of Memory: Java heap space
# → increase Xmx or reduce allocation rate
```

**ZGC vs G1GC decision**:
| | G1GC | ZGC |
|---|---|---|
| Pause target | 100-200ms achievable | Sub-millisecond |
| Throughput overhead | Low | 5-15% more CPU (concurrent work) |
| Heap size | Up to 64GB | Up to tens of TB |
| Best for | General purpose, mixed workloads | Latency-critical, large heaps |

---

## 7. Reducing Allocation Rate

The best GC tuning is generating less garbage:

```java
// 1. Avoid temporary objects in hot paths
// BAD
String key = "user:" + userId + ":profile"; // creates multiple String objects
// GOOD (if this runs millions of times/sec)
String key = new StringBuilder(32).append("user:").append(userId).append(":profile").toString();
// BETTER: cache the template format
static final String KEY_TEMPLATE = "user:%d:profile";
String key = String.format(KEY_TEMPLATE, userId); // still allocates — use StringBuilder for max perf

// 2. Reuse buffers
// BAD
byte[] buffer = new byte[8192]; // new allocation per request
// GOOD
static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);
byte[] buffer = BUFFER.get(); // reuse per thread

// 3. Use primitive collections where possible
// BAD
List<Integer> list = new ArrayList<Integer>(); // Integer boxing overhead
// GOOD
int[] array = new int[expectedSize]; // no boxing
// Or: Eclipse Collections IntList, IntArrayList

// 4. Object pooling (only when profiling proves allocation is the bottleneck)
ObjectPool<ExpensiveObject> pool = new GenericObjectPool<>(factory);
ExpensiveObject obj = pool.borrowObject();
try { use(obj); } finally { pool.returnObject(obj); }
```

---

## 8. False Sharing — CPU Cache Line Contention

When two threads read/write different fields that share a CPU cache line (64 bytes), writes to one invalidate the other's cache line:

```
Thread 1 reads/writes: counter1 (at address 0x1000)
Thread 2 reads/writes: counter2 (at address 0x1008) ← same 64-byte cache line!

Thread 1 write → invalidates Thread 2's cache line
Thread 2 must reload from L3/RAM → massive slowdown despite no logical sharing
```

```java
// PROBLEM: false sharing
class Counters {
    volatile long counter1 = 0; // bytes 0-7
    volatile long counter2 = 0; // bytes 8-15 — SAME CACHE LINE as counter1!
}

// FIX: pad fields to separate cache lines
class Counters {
    volatile long counter1 = 0;
    long p1, p2, p3, p4, p5, p6, p7; // 7 × 8 = 56 bytes padding
    volatile long counter2 = 0;       // starts at least 64 bytes after counter1
}

// CLEANER: @Contended annotation (JDK 8+, requires -XX:-RestrictContended)
@sun.misc.Contended
class Counter {
    volatile long value;
}
// JVM applies 128-byte padding around @Contended fields automatically
```

---

## 9. Connection Pool Tuning (HikariCP)

Default pool settings are rarely optimal:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # default 10 — formula: (2 × CPU cores) + effective spindle count
      minimum-idle: 5              # keep 5 idle connections warm
      connection-timeout: 30000    # 30s wait for a connection before throwing (default 30s)
      idle-timeout: 600000         # 10m before idle connection is removed (default 10m)
      max-lifetime: 1800000        # 30m max connection lifetime (shorter than DB/firewall timeout)
      keepalive-time: 30000        # 30s keepalive ping to keep connections alive through firewalls
      
      # HikariCP Metrics (via Micrometer)
      register-mbeans: true
```

**Pool sizing formula (HikariCP recommendation)**:
```
pool_size = (2 × CPU_cores) + effective_spindle_count

For SSD (no spindle): pool_size = (2 × 8 cores) + 1 = 17 ≈ 20 connections
```

**HikariCP metrics to monitor**:
| Metric | Normal | Problem signal |
|---|---|---|
| `hikaricp.connections.pending` | 0 | > 0 consistently → pool starved |
| `hikaricp.connections.acquire` (p99) | < 5ms | > 100ms → pool exhausted |
| `hikaricp.connections.usage` (p99) | < 500ms | > 2s → queries too slow |
| `hikaricp.connections.timeout.total` | 0 | Any → connection timeout, pool too small |

---

## 10. Database Query Profiling

```sql
-- PostgreSQL: find slow queries
SELECT query, mean_exec_time, calls, total_exec_time, rows
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 20;

-- Explain query execution plan
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123 AND status = 'PENDING';
-- Look for: "Seq Scan" (table scan) vs "Index Scan" on large tables
-- Look for: actual vs estimated rows (large discrepancy → stale statistics → ANALYZE)
-- Look for: "Hash Join" vs "Nested Loop" for join strategies

-- Enable slow query log in PostgreSQL
-- postgresql.conf:
log_min_duration_statement = 200  -- log queries taking > 200ms
```

```yaml
# Spring Boot slow query log
spring:
  jpa:
    show-sql: false   # too noisy in production — use p6spy or micrometer instead
  
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE  # log bind parameters (careful with PII!)
    
# Better: p6spy — log SQL with actual bind values and execution time
# Add dependency: com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0
```

---

## 11. Interview Q&A

**Q: You see high CPU usage in production. How do you diagnose whether it's GC, application code, or JIT?**  
First, check GC logs for concurrent GC CPU usage (G1/ZGC use multiple threads). Run `jstat -gcutil <pid> 1000` and watch the `GCT` (GC Time) column — if it grows faster than 10-15% of wall time, GC is the CPU consumer. If GC is normal, attach async-profiler in CPU mode (`./profiler.sh -d 30 -e cpu -f cpu.html <pid>`). The flame graph shows where CPU cycles actually go — application code, JIT compilation stubs, or native code. JIT compilation shows up as `CompileBroker` frames; application hotspots show as wide leaf frames.

**Q: What is false sharing and how does `@Contended` fix it?**  
CPU caches work in 64-byte "cache lines". If two threads write to different fields that happen to sit in the same 64-byte line, each write signals "this line is dirty" to all CPU cores, forcing the other core to reload from L3 cache or RAM on every read — even though it's reading a completely different field. The fix: pad the fields to ensure they occupy separate cache lines. `@sun.misc.Contended` (requires `-XX:-RestrictContended`) instructs the JVM to add 128 bytes of padding around the annotated field, ensuring it occupies its own cache line. This matters most for high-frequency counters in concurrent code — it can be the difference between 10 million ops/sec and 100 million ops/sec.

**Q: What GC tuning flags would you set for a latency-sensitive microservice (p99 < 50ms) on Java 21?**  
Use Generational ZGC: `-XX:+UseZGC -XX:+ZGenerational`. Set heap size with headroom: `-Xms4g -Xmx8g`. Enable GC logging for visibility: `-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=20m`. Add `-XX:ZCollectionInterval=5` to force periodic collection (prevents "lazy" GC from letting heap drift large). Monitor allocation stalls — if they occur, increase heap or profile allocations with async-profiler's `-e alloc` mode to reduce object creation rate.
