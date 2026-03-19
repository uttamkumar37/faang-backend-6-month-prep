# jvm — JVM Internals, GC, and Diagnostics

> **Learning path (4 of 8):** `1. 00-foundations` → `2. collections` → `3. concurrency` → **`4. jvm`** → `5. performance` → `6. linux-networking` → `7. springboot` → `8. testing-delivery`

Connect Java behaviour to the underlying runtime: classloading, memory layout, garbage collectors, and live diagnostics.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-jvm-internals.md](01-jvm-internals.md) | Classloading pipeline, bytecode structure, JIT compilation (C1/C2), method area, code cache, inlining |
| 2 | [02-garbage-collectors.md](02-garbage-collectors.md) | Serial, Parallel, G1, ZGC, Shenandoah — algorithms, pauses, tuning flags, when to choose each |
| 3 | [03-jvm-diagnostics.md](03-jvm-diagnostics.md) | jstack, jmap, jcmd, heap dumps, thread dumps, GC logs, async-profiler, flight recorder |
| 4 | [04-memory-management.md](04-memory-management.md) | Heap regions, metaspace, stack frames, off-heap (ByteBuffer), memory leak patterns, OOME types |
| 5 | [JvmDiagnosticsExample.java](JvmDiagnosticsExample.java) | Programmatic heap/thread inspection, GC notification hooks, memory MXBean usage |

## How this fits in the bigger picture

```
concurrency/         ← Java Memory Model (JMM) basics — do this first
jvm/                 ← YOU ARE HERE — deeper mechanics behind JMM
performance/         ← profiling, benchmarking, GC tuning in practice
springboot/          ← Spring's classloading, native image (GraalVM) implications
```

## Study method

1. Read `01-jvm-internals.md` and draw the classloading delegation model on paper.
2. Read `02-garbage-collectors.md` — build a comparison table (algorithm, pause type, heap footprint, Java version).
3. Practice reading a GC log (`03-jvm-diagnostics.md`) — identify pause time, cause, and which region was collected.
4. Run `JvmDiagnosticsExample.java` and inspect the output with `jcmd` while the program is running.
5. Explain the difference between a heap dump and a thread dump, and when you would reach for each.
