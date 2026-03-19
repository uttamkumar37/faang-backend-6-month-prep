# Garbage Collectors — Deep Dive from Basic to Advanced

---

## 1. Why GC Exists — The Basics

In C/C++ you manually call `malloc()`/`free()` or `new`/`delete`. Forget to free → memory leak. Free too early → use-after-free crash. Java automates memory reclamation with a **Garbage Collector** — an automatic process that finds objects no longer reachable from any live reference and reclaims their memory.

```
Root objects (stack frames, static fields, JNI refs)
        │
        ▼
     Object A ──► Object B ──► Object D
        │
        ▼
     Object C

Object E (no reference from any root) → GARBAGE → eligible for collection
```

**Reachability**: An object is alive if it can be reached by following references from any GC root. GC roots include: local variables on any thread's stack, static fields of loaded classes, JNI global references.

---

## 2. Generational Hypothesis

Most objects die young — short-lived temporaries (log messages, request objects, DTOs). Very few survive long enough to become "old". This observation is the **generational hypothesis** and drives the design of all GCs in modern JVMs.

```
Heap layout (generational)

┌───────────────────────────────────────────────────────────┐
│                        YOUNG GEN                          │
│   Eden  │  Survivor S0  │  Survivor S1                    │
│  (large) │   (small)     │   (small)                       │
├───────────────────────────────────────────────────────────┤
│                        OLD GEN (Tenured)                   │
│  Objects that survived many minor GC cycles               │
└───────────────────────────────────────────────────────────┘
```

- **Eden**: new objects are allocated here (via TLAB — see memory management doc)
- **Survivor S0/S1**: objects that survive Eden collection bounce between these
- **Old Gen**: objects that crossed an age threshold (default 15 GC cycles) are promoted here

---

## 3. Minor GC (Young Generation Collection)

Minor GC is **stop-the-world** but very fast (usually < 10ms) because it only processes the small young generation:

```
Step 1: Mark — trace from GC roots, find all live objects in Eden + active Survivor
Step 2: Copy — copy live objects to the other (empty) Survivor space
        Dead objects in Eden are simply abandoned (no per-object work needed!)
Step 3: Age bump — each copied object increments its age counter
Step 4: Promote — objects with age >= MaxTenuringThreshold (default 15) → Old Gen
Step 5: Swap — the previously empty Survivor becomes the new "active" one
```

**Why copying is fast**: There are very few live objects (most died). Copying compacts memory — no fragmentation. The dead objects require zero work — the entire Eden region is "zeroed out" and reused.

---

## 4. Major / Full GC (Old Generation Collection)

Old Gen fills up over time as objects are promoted. When it can't allocate, a **major GC** is triggered — slow because old gen is large and most objects are live.

**Full GC** additionally includes young gen and Metaspace — typically triggered by:
- `System.gc()` call
- Explicit heap dump request
- After a concurrent GC fails partway through

---

## 5. GC Algorithms — From Serial to Modern

### 5.1 Serial GC (`-XX:+UseSerialGC`)

```
                ┌────────┐
Application ────▶│  STOP  │──── GC Thread ──── restart
                └────────┘
```

- Single-threaded GC — only one GC thread
- Simple mark-compact for old gen
- **Use case**: single-core machines, tiny heaps (< 1 GB), embedded devices
- **Not for**: servers, multi-core systems

### 5.2 Parallel GC (`-XX:+UseParallelGC`) — was default before Java 9

```
                ┌────────┐
Application ────▶│  STOP  │──── GC Threads (N) ──── restart
                └────────┘
```

- Like Serial but uses multiple GC threads — faster collection
- All GC phases still STW (stop-the-world)
- Optimizes for throughput (total work done per unit time)
- Flag: `-XX:ParallelGCThreads=N` (default = CPU count)
- **Use case**: batch processing, offline/background jobs where latency spikes are acceptable
- **Not for**: low-latency APIs, interactive apps

### 5.3 CMS (Concurrent Mark-Sweep) — REMOVED in Java 14

CMS was the first "low-latency" option — most GC work runs ***concurrent*** with your app:

```
Phase 1: Initial Mark    — STW, mark GC roots (fast)
Phase 2: Concurrent Mark — runs WITH app, follow references
Phase 3: Remark          — STW, re-mark objects changed during phase 2
Phase 4: Concurrent Sweep— runs WITH app, sweep garbage (no compact!)
```

**Problems with CMS**:
- No compaction → heap fragments over time → eventually "concurrent mode failure"
- Fragmentation triggers Full GC (Serial, very slow, minutes pause!)
- High CPU overhead from concurrent threads
- **Removed Java 14** — G1GC is strictly better

### 5.4 G1GC — Default since Java 9 (`-XX:+UseG1GC`)

G1 ("Garbage First") abandons fixed eden/old regions and divides the heap into equal-sized **regions** (typically 1–32 MB each):

```
Heap divided into ~2048 equal regions (each = 1-32 MB based on heap size)

[ E ][ E ][ S ][ O ][ O ][ E ][ H ][ O ][ E ][ S ][ O ][ H ] ...
  ↑               ↑         ↑
Eden         Survivor    Old Gen    H=Humongous (large objects > 50% region size)
```

**G1 GC Phases**:

```
1. Young Collection (STW — fast)
   - Evacuate live objects from Eden+Survivor regions to new Survivor/Old regions
   
2. Concurrent Root Region Scan
   - Scan survivor regions for references into old gen (concurrent)
   
3. Concurrent Marking
   - Trace the entire heap to find live objects in old gen (concurrent with app)
   - Uses SATB (Snapshot-At-The-Beginning) algorithm
   
4. Remark (STW — short)
   - Complete marking, process SATB buffers, reclaim empty regions
   
5. Cleanup (STW — very short + concurrent)
   - Account live data, reset regions, sort by garbage amount
   
6. Mixed Collection (STW)
   - Collect all young regions PLUS the most garbage-dense old regions
   - "Garbage First" = always collect highest-garbage regions (bang for buck)
```

**Key G1 parameter**: `-XX:MaxGCPauseMillis=200` (target pause goal — G1 tries to stay within this)

**G1 tuning flags**:
```bash
-Xms4g -Xmx4g                    # heap size (equal start/max prevents resize GCs)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200          # pause target (default 200ms)
-XX:G1HeapRegionSize=16m          # region size (1-32m, must be power of 2)
-XX:G1NewSizePercent=20           # min young gen % (default 5)
-XX:G1MaxNewSizePercent=40        # max young gen % (default 60)
-XX:G1MixedGCLiveThresholdPercent=85 # exclude dense-live regions from mixed GC
-XX:InitiatingHeapOccupancyPercent=45 # start concurrent marking when heap 45% full
```

**G1 promotion failure** — if G1 can't find a region for evacuation:
- Falls back to Full GC (serial compaction — very slow!)
- Fix: Give more heap, reduce allocation rate, lower `InitiatingHeapOccupancyPercent`

**Humongous objects** (> 50% of region size):
```java
// These go directly to Old Gen and are expensive — avoid allocating large arrays in hot paths
byte[] large = new byte[17_000_000]; // 17MB > 50% of 16MB region = humongous
```

### 5.5 ZGC — Ultra-low latency (Java 15+ production) (`-XX:+UseZGC`)

ZGC targets sub-millisecond pause times regardless of heap size (tested to 16TB heaps!):

```
Goal: ALL pauses < 1ms (regardless of heap size 8MB–16TB)

Key techniques:
  - Colored pointers (extra bits in 64-bit pointer encode GC state)
  - Load barriers (intercept every object read to do live state fixes)
  - Concurrent relocation (moves objects while app runs — no STW copy!)
  - Region-based (like G1, but regions are dynamically sized)
```

**ZGC phases**:

```
Minor pause 1: Pause Mark Start    — scan thread stacks for GC roots (~0.1ms)
Concurrent:    Concurrent Mark     — trace entire object graph (app runs)
Minor pause 2: Pause Mark End      — process remaining reference queue (~0.5ms)
Concurrent:    Concurrent Relocate — actually MOVE objects while app runs!
Minor pause 3: Pause Relocate Start— roots point to new locations (~0.1ms)
```

**How can ZGC move objects concurrently?**  
When your code reads a pointer, a **load barrier** intercepts it — if the object has been moved, the barrier fixes the pointer on the fly, transparently returning the new address. The app never sees a stale pointer.

**ZGC flags**:
```bash
-XX:+UseZGC
-Xms8g -Xmx8g
-XX:ConcGCThreads=4              # concurrent GC threads (default = CPU/4)
-XX:ZCollectionInterval=5        # proactive GC every 5 seconds (default 0 = reactive)
-XX:ZUncommitDelay=300           # return freed memory to OS after 5 min idle
```

**Generational ZGC (Java 21)** — ZGC now has generations (young/old), dramatically improving throughput:
```bash
-XX:+UseZGC -XX:+ZGenerational   # Java 21+ recommended
```

### 5.6 Shenandoah GC (OpenJDK) (`-XX:+UseShenandoahGC`)

Similar goal to ZGC (sub-ms pauses), different approach — uses **forwarding pointers** inside each object:

```
Each object gets a forwarding pointer slot prepended:
   Old address: [fwd ptr | object data]
   After move:  fwd ptr → new location

Uses "Brooks pointers" — barrier checks fwd ptr on every access
```

- Available from OpenJDK 12+
- Very good for interactive applications, similar latency to ZGC
- Not available in Oracle JDK (Red Hat/OpenJDK project)

---

## 6. GC Comparison Table

| GC | Default | Pause goal | Throughput | Best for |
|---|---|---|---|---|
| Serial | JDK client | Long (100s ms+) | Low | Single-core, tiny heaps |
| Parallel | JDK 8 server | Medium (100-500ms) | **Highest** | Batch jobs |
| G1 | JDK 9–21+ | Medium-low (< 200ms) | High | Most server apps |
| ZGC | Opt-in | **< 1ms** | Good | Low-latency APIs |
| Shenandoah | Opt-in | **< 1ms** | Good | Interactive apps |

---

## 7. GC Log Analysis

Enable GC logging (add to JVM args):

```bash
# Java 11+ unified GC logging
-Xlog:gc*:file=/var/log/app-gc.log:time,uptime,level,tags:filecount=5,filesize=20m

# Key flags for analysis
-Xlog:gc+heap=debug          # heap occupancy before/after GC
-Xlog:gc+stats               # GC phase timing stats
-Xlog:safepoint              # safepoint entry/exit times
```

**Reading GC logs** — G1 example:

```
[2024-01-15T10:30:45.123+0000][0.456s][gc,start ] GC(32) Pause Young (Normal) (G1 Evacuation Pause)
[2024-01-15T10:30:45.123+0000][0.456s][gc,task  ] GC(32) Using 8 workers of 8 for evacuation
[2024-01-15T10:30:45.198+0000][0.531s][gc,heap  ] GC(32) Eden regions: 150->0(170)
[2024-01-15T10:30:45.198+0000][0.531s][gc,heap  ] GC(32) Survivor regions: 15->14(22)
[2024-01-15T10:30:45.198+0000][0.531s][gc,heap  ] GC(32) Old regions: 85->85
[2024-01-15T10:30:45.198+0000][0.531s][gc,heap  ] GC(32) Humongous regions: 2->1
[2024-01-15T10:30:45.198+0000][0.531s][gc      ] GC(32) Pause Young (Normal) (G1 Evacuation Pause) 5324M->3298M(8192M) 75.234ms

                              ^^^^^ collected                           ^^^^^^^^ pause dur
```

**GCeasy.io** — paste GC log for automatic analysis (pauses histogram, allocation rate, promotion rate).

**Common problems from GC logs**:

| Log message | Meaning | Fix |
|---|---|---|
| `Pause Young (Concurrent Start)` | Concurrent marking triggered | Normal — if frequent, heap may be too small |
| `Pause Full (Allocation Failure)` | Full GC! Evacuation failed | Add heap, reduce allocation rate, lower `IHOP` |
| `To-space exhausted` | Survivor regions full, forced promotion | Add heap, check for object retention |
| `Humongous allocation` | Large object bypassed young gen | Pool large objects, or increase G1HeapRegionSize |
| Very frequent minor GCs | High allocation rate | Profile with async-profiler allocation mode |

---

## 8. Choosing the Right GC — Decision Flow

```
What matters most?

High throughput (batch, analytics)
    └─► Parallel GC (-XX:+UseParallelGC)

Standard server app (< 4GB heap, latency < 200ms acceptable)
    └─► G1GC (default, good all-around choice)

Low latency required (API p99 < 50ms, heap 4GB+)
    └─► ZGC with Generational (-XX:+UseZGC -XX:+ZGenerational, Java 21)
         or Shenandoah

Real-time or interactive (pauses must be < 1ms always)
    └─► ZGC (Java 21 Generational ZGC is the best option currently)

Microservice with tiny heap (< 512MB)
    └─► G1GC is fine, Serial GC if single-threaded
    └─► GraalVM native image (no JVM GC at all, instant startup)
```

---

## 9. GC Tuning Checklist

```bash
# Step 1: Baseline measurement — get GC logs
-Xlog:gc*:file=/tmp/gc.log:time,uptime:filecount=3,filesize=20m

# Step 2: Set equal Xms and Xmx (avoid resize GCs)
-Xms4g -Xmx4g

# Step 3: Set GC threads (don't exceed physical CPUs)
-XX:ParallelGCThreads=4        # STW workers
-XX:ConcGCThreads=2            # concurrent workers (G1/ZGC)

# Step 4: For G1 — tune pause target first
-XX:MaxGCPauseMillis=100       # try lower, watch if GC can meet it

# Step 5: For G1 — if Old Gen fills up too fast
-XX:InitiatingHeapOccupancyPercent=35  # start marking earlier (default 45)

# Step 6: Monitor with JFR
-XX:StartFlightRecording=filename=/tmp/app.jfr,duration=60s,settings=profile
```

---

## 10. Object Finalization — Deprecated Patterns

```java
// DEPRECATED: finalize() called by GC before reclaiming — unpredictable timing
@Override
protected void finalize() throws Throwable {
    cleanup(); // NEVER rely on this — GC may never call it!
}

// GOOD: Explicit lifecycle
public class Connection implements AutoCloseable {
    @Override
    public void close() { cleanup(); }
}

try (Connection conn = new Connection()) {
    conn.use();
} // close() called here, guaranteed

// GOOD: Cleaner API (Java 9+, non-blocking alternative to finalize)
Cleaner cleaner = Cleaner.create();
cleaner.register(resource, () -> cleanup()); // cleanup runs when resource becomes unreachable
```

---

## 11. Interview Q&A

**Q: What is the difference between Minor GC, Major GC, and Full GC?**  
**Minor GC** collects only the young generation (Eden + Survivors) — fast (< 10ms typically) because it's small and uses copying. **Major GC** collects the old generation — slower since old gen is larger and most objects are live. **Full GC** collects the entire heap including young, old, and Metaspace — the slowest, often triggered by GC failure conditions or `System.gc()`. In G1, you should never see Full GC during normal operation.

**Q: What is stop-the-world (STW) and why does it happen?**  
A STW pause halts ALL application threads so the GC can work on a consistent heap snapshot. It's needed because: (1) moving objects requires updating all references to them; if the app continues running mid-move, you'd get dangling pointers. (2) Tracing the reference graph requires it to not change while you're following edges. Modern GCs (G1, ZGC) minimize STW to very short pauses for specific phases only.

**Q: Why does ZGC achieve sub-millisecond pauses while G1 can't?**  
G1 must stop the app during evacuation (copying live objects) because it needs to update all references to moved objects atomically. ZGC uses **load barriers** (code injected at every object read) to fix stale pointers on-the-fly as the app reads them — so relocation can happen concurrently. The only STW phases are just scanning thread stacks for GC roots — a few hundred microseconds regardless of heap size.

**Q: What causes a promotion failure in G1?**  
When G1 evacuates young generation regions, if there's no free region to copy live objects into, it triggers an "evacuation failure" — G1 falls back to a slow Full GC (serial compaction). This kills latency. Causes: heap too small, allocation rate too high, live data growing unexpectedly. Fix: increase heap, lower `InitiatingHeapOccupancyPercent` so concurrent marking starts earlier, or reduce object retention.

**Q: What are humongous objects in G1 and why are they a problem?**  
Objects larger than 50% of a region size bypass young gen and go directly to Old Gen (occupying one or more fully dedicated "humongous" regions). They are collected only during concurrent marking + mixed GC — not minor GCs. If humongous allocations are frequent, old gen fills up faster, triggering more expensive GC. Fix: increase `G1HeapRegionSize` so the threshold is higher, or object pool large allocations.
