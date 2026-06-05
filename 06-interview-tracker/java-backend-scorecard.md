# Java Backend Scorecard

## Purpose
Measure whether Java backend knowledge is deep enough for SDE-II follow-up questions, not just API familiarity.

## Study Steps
- Use this file during the Sunday review block.
- Fill concrete numbers first: solved count, mocks, defects, shipped project work.
- Mark pass or fail using the criteria in the file, then pick one recovery action.

## Areas
| Area | Score 0-5 | Senior signal |
|---|---:|---|
| Java language and collections | | Complexity, mutability, equals/hashCode, generics |
| JVM memory and GC | | Heap, metaspace, allocation, GC logs, tuning trade-offs |
| Concurrency | | Thread pools, locks, volatile, CompletableFuture, backpressure |
| Spring Boot | | Lifecycle, auto-config, transactions, validation, testing |
| REST/API design | | Idempotency, pagination, versioning, error models |
| SQL/JPA | | Indexes, isolation, N+1, connection pools, query plans |
| Kafka/messaging | | Delivery semantics, ordering, lag, retries, DLQ |
| Redis/cache | | Invalidation, TTL, stampede, eviction, hot keys |
| Observability | | RED/USE metrics, tracing, logs, SLOs |
| Production debugging | | CPU, memory, GC, latency, thread dumps, postmortems |

## Pass/Fail Criteria
- Pass: average 4.0+, no area below 3.5, and production debugging at least 4 by month 5.
- Fail: can define concepts but cannot diagnose incidents or explain runtime behavior.

## Interview Questions
- What happens inside Spring when a request enters a controller?
- How would you debug high p99 latency in a Java service?
- What is the difference between Kafka retry, DLQ, and idempotent consumer logic?

## Common Mistakes
- Memorizing Spring annotations without lifecycle knowledge.
- Talking about GC tuning before measuring allocation rate.
- Ignoring connection pool and thread pool limits in API latency.

## Self-Check
- [ ] I can explain each topic with internals, production risk, and a Java example.
- [ ] I can answer follow-ups without changing the subject.
- [ ] I can connect Java concepts to system design decisions.

## Practical Example
Example: For `@Transactional`, interview-ready depth includes proxying, rollback rules, isolation, propagation, lazy loading risks, and why self-invocation can bypass the proxy.
