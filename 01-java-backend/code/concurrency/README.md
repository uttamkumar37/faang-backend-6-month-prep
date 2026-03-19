# concurrency — Java Concurrency & Reactive Programming

> **Learning path (3 of 8):** `1. 00-foundations` → `2. collections` → **`3. concurrency`** → `4. jvm` → `5. performance` → `6. linux-networking` → `7. springboot` → `8. testing-delivery`

Thread fundamentals through advanced async composition and reactive streams.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-thread-fundamentals.md](01-thread-fundamentals.md) | Thread lifecycle, synchronized, volatile, Java Memory Model, happens-before, intrinsic locks |
| 2 | [02-concurrency.md](02-concurrency.md) | ExecutorService, thread pools, Callable/Future, ReentrantLock, ReadWriteLock, atomic variables |
| 3 | [03-advanced-concurrency.md](03-advanced-concurrency.md) | StampedLock, CountDownLatch, CyclicBarrier, Semaphore, Phaser, fork/join, virtual threads (Java 21) |
| 4 | [04-reactive-programming.md](04-reactive-programming.md) | Project Reactor, Mono/Flux, backpressure, schedulers, error handling, WebFlux integration |
| 5 | [ThreadPoolPatterns.java](ThreadPoolPatterns.java) | Fixed, cached, scheduled, and work-stealing pool patterns with sizing heuristics |
| 6 | [ConcurrencyAndJvmExamples.java](ConcurrencyAndJvmExamples.java) | Runnable examples covering lock, atomic, and memory-visibility scenarios |
| 7 | [AdvancedConcurrencyExamples.java](AdvancedConcurrencyExamples.java) | Latch, barrier, semaphore, and fork/join examples |
| 8 | [CompletableFuturePatterns.java](CompletableFuturePatterns.java) | thenApply, thenCompose, allOf, anyOf, exceptionally, and timeout patterns |
| 9 | [ReactiveProgrammingExamples.java](ReactiveProgrammingExamples.java) | Mono/Flux creation, transformation, backpressure, and scheduler switching |

## How this fits in the bigger picture

```
00-foundations/      ← OOP, generics, language basics
collections/         ← data structures (ConcurrentHashMap, CopyOnWriteArrayList)
concurrency/         ← YOU ARE HERE — threading, async, reactive
jvm/                 ← GC, memory model at the hardware level
springboot/          ← @Async, WebFlux, reactive database drivers in practice
```

## Study method

1. Start with `01-thread-fundamentals.md` — you must understand the Java Memory Model before the rest makes sense.
2. Run `ConcurrencyAndJvmExamples.java` and observe visibility / ordering bugs first-hand.
3. Work through pool sizing in `ThreadPoolPatterns.java`; calculate the right size for CPU-bound vs I/O-bound tasks.
4. Practice writing a `CompletableFuture` chain from memory: fetch → transform → fallback → timeout.
5. Explain the difference between `Mono` and `Flux` backpressure out loud as if answering an interview question.
