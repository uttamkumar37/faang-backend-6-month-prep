# JVM Internals

## 1. Memory Layout

```
┌─────────────────────────────────────────────────────────────────┐
│                          JVM Process                            │
│                                                                 │
│  ┌──────────────┐  ┌────────────────────────────────────────┐  │
│  │  Metaspace   │  │              Heap                       │  │
│  │ (class meta) │  │  ┌─────────────┐  ┌─────────────────┐  │  │
│  │ method area  │  │  │   Young Gen  │  │    Old Gen       │  │  │
│  │ string pool  │  │  │ Eden|S0|S1   │  │  tenured objs   │  │  │
│  └──────────────┘  │  └─────────────┘  └─────────────────┘  │  │
│                    └────────────────────────────────────────┘  │
│                                                                 │
│  Per-thread:                                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Stack (frames: local vars, operand stack, frame data)   │  │
│  │  PC Register  │  Native Method Stack                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Code Cache (JIT compiled native code)                         │
└─────────────────────────────────────────────────────────────────┘
```

### Heap Regions

| Region | Purpose | GC Action |
|---|---|---|
| Eden | New object allocation | Minor GC clears it |
| Survivor 0/1 (S0, S1) | Objects surviving 1+ minor GCs | Copied between them |
| Old / Tenured | Objects surviving threshold (default 15) minor GCs | Major GC |

### Metaspace (Java 8+, replaces PermGen)

- Stores class metadata, method descriptors, constant pools.
- Grows dynamically in native memory (not heap).
- OOM risk: class loader leaks (common with Tomcat hot deploys).
- Tuned with: `-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m`

### Stack

- One stack per thread. Holds stack frames.
- Each frame: local variable table, operand stack, reference to constant pool.
- `StackOverflowError` = too many nested calls.
- `-Xss` controls per-thread stack size (default 512k–1m).

---

## 2. Class Loading

### Lifecycle phases

```
Loading → Linking (Verify → Prepare → Resolve) → Initialization → Usage → Unloading
```

| Phase | What happens |
|---|---|
| Loading | Read .class bytes. Create `java.lang.Class` object. |
| Verification | Bytecode safety checks: type safety, no stack underflow. |
| Preparation | Allocate memory for static fields, set to defaults (0/null/false). |
| Resolution | Resolve symbolic references to direct references. |
| Initialization | Execute static initializers and assign static field values. |

### Delegation model (Parent-First)

```
Bootstrap CL (rt.jar, java.lang.*) 
  → Extension CL (ext/*.jar) 
    → Application CL (classpath) 
      → Custom CL (plugins, OSGi, hot-reload)
```

**Why it matters**: Double-checked locking on the class object is safe because initialization is guaranteed to run exactly once per class per ClassLoader context.

### Common interview pitfall

Multiple ClassLoaders can load the same class — they are NOT the same class. This causes `ClassCastException` with plugins and hot-deploy containers.

---

## 3. JIT Compilation

Java code path: Source → Bytecode → Interpreted → JIT compiled native code

### Tiered Compilation (default in Java 8+)

| Tier | Mode | When |
|---|---|---|
| 0 | Interpreter | First execution |
| 1 | C1 (simple, no profiling) | Low call count |
| 2 | C1 + limited profiling | Warming up |
| 3 | C1 + full profiling | Building profile data |
| 4 | C2 (aggressive optimization) | Hot methods (>10k invocations) |

### Key optimizations

- **Inlining**: Replaces a method call with the method body. Most impactful optimization.
- **Escape analysis**: If an object doesn't escape the current method it can be stack-allocated (no GC pressure) or scalar-replaced (fields stored in registers).
- **Loop unrolling**: Reduces loop overhead.
- **Dead code elimination**: Removes unreachable branches.
- **Branch prediction hints**: HotSpot learns the branch taken most often.

### Code Cache

Compiled native code lives in Code Cache. When it fills:
- `CodeCache is full. Compiler has been disabled.`
- Performance degrades to interpreter speed.
- Fix: `-XX:ReservedCodeCacheSize=512m`

---

## 4. Garbage Collection

### Minor GC — Young Generation

1. Eden fills up → Minor GC triggered.
2. Live objects copied to Survivor space (S1 if S0 in use).
3. Objects reaching tenuring threshold promoted to Old Gen.
4. Eden and old Survivor wiped.
5. Stop-The-World pause: typically < 10ms.

### Major/Full GC — Old Generation

- Happens when Old Gen fills up.
- Much longer pause: 100ms–several seconds.
- Full GC also collects Young Gen and Metaspace.

### G1GC (default Java 9+)

```
Heap divided into equal regions (~2048 by default).
Each region can play Eden/Survivor/Old/Humongous roles dynamically.

Phases:
1. Young GC (STW) — evacuate Eden + Survivor
2. Concurrent Marking — identify live objects in Old regions
3. Mixed GC (STW) — collect Young + most-garbage Old regions
4. Full GC (fallback, STW) — only if concurrent can't keep up
```

Key tuning:
```
-XX:+UseG1GC
-Xms4g -Xmx4g           # fix heap size to prevent resizing pauses
-XX:MaxGCPauseMillis=200 # pause target (best effort)
-XX:G1HeapRegionSize=16m # region size (power of 2, 1m-32m)
-XX:G1NewSizePercent=20  # min young gen %
-XX:G1MaxNewSizePercent=40
```

### ZGC (Java 15+ production-ready)

- Concurrent compaction: almost no STW pauses
- Pause target: < 1ms regardless of heap size
- Higher CPU overhead (~5-15%)
- When to use: heap > 8GB, latency-critical services  
```
-XX:+UseZGC
-XX:SoftMaxHeapSize=30g   # hint to use less before GC
```

### Shenandoah (OpenJDK alternative to ZGC)

- Very similar goals to ZGC, pure open-source lineage.
- Good for < 4GB heaps too.

---

## 5. GC Diagnosis Workflow

```
1. Is it GC-related? → Check: CPU high, latency spikes, heap chart sawtooth
2. Enable GC logging:
   -Xlog:gc*:file=gc.log:time,uptime,level,tags
3. What collection? → Minor (young) vs GC (mixed/full)
4. Pause duration → target < 200ms for G1
5. Heap after GC → if Old Gen doesn't shrink → memory leak
6. Promotion failure → Old Gen full before Minor GC live objects promoted
7. Humongous allocation → objects > 50% G1 region → always goes to Old Gen
```

### Heap Dump Analysis

```bash
# Trigger heap dump
jmap -dump:format=b,file=heap.hprof <pid>
# Or on OOM:
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp

# Analyze with:
# - Eclipse MAT (best for leak suspects)
# - JProfiler
# - VisualVM
```

### OOM Types

| OOM Message | Cause |
|---|---|
| `Java heap space` | Heap exhausted. Leak or undersized heap. |
| `GC overhead limit exceeded` | > 98% time in GC, < 2% freed. |
| `Metaspace` | Class loader leak. Too many dynamic proxies. |
| `Direct buffer memory` | Off-heap NIO buffers exceed `-XX:MaxDirectMemorySize` |
| `Unable to create native thread` | OS limit on threads or stack overflow. |

---

## 6. JVM Flags Cheat Sheet

```bash
# Heap
-Xms4g -Xmx4g

# GC
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=5,filesize=20m

# JIT
-XX:+TieredCompilation              # default on
-XX:ReservedCodeCacheSize=512m

# Diagnostics
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
-XX:+PrintFlagsFinal -version       # print all effective flags

# Threading (virtual threads — Java 21)
# No flags needed — use Executors.newVirtualThreadPerTaskExecutor()
```

---

## 7. Interview Q&A

**Q: What is the difference between stack and heap?**  
Stack is per-thread, stores local variables and method call frames, fixed size. Heap is process-wide, stores all objects, managed by GC. Primitive locals live on stack; objects always live on heap (unless escape analysis stack-allocates them).

**Q: When does MetaSpace grow continuously?**  
When ClassLoaders are not being garbage collected. This happens with dynamic class generation (CGLIB proxies, Groovy scripts, frequent deployments in web containers) when the ClassLoader holding the class is still reachable.

**Q: Why can a Full GC take seconds but a Minor GC takes milliseconds?**  
Minor GC operates only on the small Young Generation using a copy-collect algorithm. Full GC must trace the entire live object graph of the Old Generation (which can be GBs), compact or sweep it, and collect the entire heap atomically with a Stop-The-World pause.

**Q: How does G1GC keep pause times predictable?**  
G1 splits the heap into equal-sized regions and tracks the "garbage density" of each region. During a Mixed GC it prioritizes collecting Old regions with the most garbage first (Garbage First). It limits how many regions it collects per pause cycle to stay within the `MaxGCPauseMillis` target.
