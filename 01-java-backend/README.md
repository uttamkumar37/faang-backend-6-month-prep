# 01 — Java Backend

## What's in this folder

All study material lives under `code/`, and each topic folder keeps the markdown notes next to the runnable Java examples.

## Folder order inside `01-java-backend`

1. `code/00-foundations/`
2. `code/collections/`
3. `code/concurrency/`
4. `code/jvm/`
5. `code/performance/`
6. `code/linux-networking/`
7. `code/springboot/`
8. `code/testing-delivery/`

### 1. code/00-foundations/
Core refresh notes and examples:
- `01-what-is-java-jdk-jvm.md`
- `02-code-execution-flow.md`
- `03-java-language-basics.md`
- `04-oop-concepts.md`
- `05-modern-java-features.md`
- `06-generics-and-type-system.md`
- `07-design-patterns.md`
- `GenericsExamples.java`
- `DesignPatternsExamples.java`

### 2. code/collections/
Collections theory plus examples:
- `01-list-and-queues.md`
- `02-set-and-maps.md`
- `03-collections.md`
- `04-stream-api.md`
- `CollectionsDeepDive.java`
- `LRUCacheImpl.java`
- `LRUCacheImplTest.java`
- `StreamApiExamples.java`

### 3. code/concurrency/
Concurrency and async patterns:
- `01-thread-fundamentals.md`
- `02-concurrency.md`
- `03-advanced-concurrency.md`
- `04-reactive-programming.md`
- `AdvancedConcurrencyExamples.java`
- `CompletableFuturePatterns.java`
- `ConcurrencyAndJvmExamples.java`
- `ReactiveProgrammingExamples.java`
- `ThreadPoolPatterns.java`

### 4. code/jvm/
JVM internals and diagnostics:
- `01-jvm-internals.md`
- `02-garbage-collectors.md`
- `03-jvm-diagnostics.md`
- `04-memory-management.md`
- `JvmDiagnosticsExample.java`

### 5. code/performance/
Performance tuning notes and examples:
- `01-jmh-benchmarking.md`
- `02-profiling-and-gc-tuning.md`
- `10-performance-tuning.md`
- `PerformanceTuning.java`

### 6. code/linux-networking/
Linux, OS, and networking depth for production backend work:
- `01-linux-and-process-basics.md`
- `02-networking-for-backend.md`
- `LinuxAndNetworkingExamples.java`

### 7. code/springboot/
Spring Boot, resilience, security, observability, and caching:
- `05-spring-boot.md`
- `06-microservices-patterns.md`
- `07-rest-api-design.md`
- `08-database-jpa.md`
- `09-security.md`
- `10-testing.md`
- `11-kafka-messaging.md`
- `12-observability.md`
- `13-caching-redis.md`
- `CachingRedisExamples.java`
- `ObservabilityConfig.java`
- `ResiliencePatterns.java`
- `SecurityConfig.java`

### 8. code/testing-delivery/
Testing strategy, CI/CD, and safe rollout practices:
- `01-testing-strategy.md`
- `02-ci-cd-release-engineering.md`
- `TestingAndReleasePatterns.java`

## Recommended learning order

Follow this order so fundamentals support the more production-heavy topics later.

1. `code/00-foundations/` to refresh core Java syntax, OOP, generics, and common design patterns.
2. `code/collections/` to understand the data structures you will rely on in interview coding and backend implementation.
3. `code/concurrency/` to build thread-safety, async composition, and executor reasoning.
4. `code/jvm/` to connect Java behavior to memory, GC, classloading, and production diagnostics.
5. `code/performance/` to learn profiling, benchmarking, and bottleneck analysis.
6. `code/linux-networking/` to build OS and network intuition for real backend incidents and latency analysis.
7. `code/springboot/` to apply the Java fundamentals in service design, data access, security, resilience, and observability.
8. `code/testing-delivery/` to round out production readiness with testing strategy, CI/CD, rollout safety, and migration discipline.

Recommended milestone:
- finish steps 1-4 before deep Spring Boot work
- finish steps 5-6 before system-design deep dives
- finish step 8 before treating a project as portfolio-ready

## Study method

1. Read the numbered markdown in the topic folder.
2. Open the corresponding code file and trace through every method.
3. Close the file and re-implement from memory.
4. Explain it out loud as if in an interview.
