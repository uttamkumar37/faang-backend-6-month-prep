# performance — Benchmarking, Profiling & GC Tuning

> **Learning path (5 of 8):** `1. 00-foundations` → `2. collections` → `3. concurrency` → `4. jvm` → **`5. performance`** → `6. linux-networking` → `7. springboot` → `8. testing-delivery`

Measure before you optimize. Covers JMH micro-benchmarks, profiler-driven analysis, and GC flag tuning at production scale.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-jmh-benchmarking.md](01-jmh-benchmarking.md) | JMH setup, @Benchmark modes (Throughput, AverageTime, SampleTime), @State scopes, blackhole, common pitfalls |
| 2 | [02-profiling-and-gc-tuning.md](02-profiling-and-gc-tuning.md) | async-profiler, flame graphs, allocation profiling, GC log analysis, G1 tuning flags, pause budget |
| 3 | [10-performance-tuning.md](10-performance-tuning.md) | System-level patterns: object pooling, CPU cache locality, I/O batching, connection pool sizing, serialisation costs |
| 4 | [PerformanceTuning.java](PerformanceTuning.java) | Runnable examples illustrating allocation reduction, pool sizing, and batching patterns |

## How this fits in the bigger picture

```
jvm/                 ← GC algorithms and memory layout — prerequisite
performance/         ← YOU ARE HERE — measure, profile, tune
linux-networking/    ← OS-level tuning (TCP buffers, file descriptors)
springboot/          ← where you apply the patterns in production services
```

## Study method

1. Read `01-jmh-benchmarking.md` and set up a JMH project locally; run at least one @BenchmarkMode(Throughput) test.
2. Profile the benchmark with async-profiler and open the flame graph — identify the hottest frame.
3. Read `02-profiling-and-gc-tuning.md` and practice reading a GC log; calculate pause 99th percentile by hand.
4. Work through `10-performance-tuning.md` — for each pattern, ask "where would this hurt most in a real service?".
5. Implement one optimisation from `PerformanceTuning.java` in a personal project and measure the before/after with JMH.
