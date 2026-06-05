# Learning Navigation

Use this file as the fastest path from topic to material. Each row points to the note to read first, the Java file to study second, and the test file to use when validation exists.

## Java backend

| Topic | Read first | Study next | Validation |
|---|---|---|---|
| Java foundations | `01-java-backend/code/00-foundations/01-what-is-java-jdk-jvm.md` | `01-java-backend/code/00-foundations/GenericsExamples.java` and `01-java-backend/code/00-foundations/DesignPatternsExamples.java` | — |
| Collections | `01-java-backend/code/collections/03-collections.md` | `01-java-backend/code/collections/CollectionsDeepDive.java` | `01-java-backend/code/collections/LRUCacheImplTest.java` |
| LRU cache | `01-java-backend/code/collections/02-set-and-maps.md` | `01-java-backend/code/collections/LRUCacheImpl.java` | `01-java-backend/code/collections/LRUCacheImplTest.java` |
| Concurrency | `01-java-backend/code/concurrency/02-concurrency.md` | `01-java-backend/code/concurrency/ConcurrencyAndJvmExamples.java` | — |
| CompletableFuture | `01-java-backend/code/concurrency/03-advanced-concurrency.md` | `01-java-backend/code/concurrency/CompletableFuturePatterns.java` | — |
| JVM internals | `01-java-backend/code/jvm/01-jvm-internals.md` | `01-java-backend/code/jvm/JvmDiagnosticsExample.java` | — |
| Performance tuning | `01-java-backend/code/performance/10-performance-tuning.md` | `01-java-backend/code/performance/PerformanceTuning.java` | — |
| Linux and process basics | `01-java-backend/code/linux-networking/01-linux-and-process-basics.md` | `01-java-backend/code/linux-networking/LinuxAndNetworkingExamples.java` | — |
| Backend networking | `01-java-backend/code/linux-networking/02-networking-for-backend.md` | `01-java-backend/code/linux-networking/LinuxAndNetworkingExamples.java` | — |
| Spring Boot core | `01-java-backend/code/springboot/05-spring-boot.md` | `01-java-backend/code/springboot/ResiliencePatterns.java` | — |
| Security | `01-java-backend/code/springboot/09-security.md` | `01-java-backend/code/springboot/SecurityConfig.java` | — |
| Observability | `01-java-backend/code/springboot/12-observability.md` | `01-java-backend/code/springboot/ObservabilityConfig.java` | — |
| Testing strategy | `01-java-backend/code/testing-delivery/01-testing-strategy.md` | `01-java-backend/code/testing-delivery/TestingAndReleasePatterns.java` | — |
| CI/CD and release engineering | `01-java-backend/code/testing-delivery/02-ci-cd-release-engineering.md` | `01-java-backend/code/testing-delivery/TestingAndReleasePatterns.java` | — |
| Production debugging | `01-java-backend/code/production-debugging/README.md` | `01-java-backend/code/production-debugging/IncidentDebuggingPlaybook.java` | Use each incident playbook as a drill |

## DSA

| Pattern | Read first | Study next | Validation |
|---|---|---|---|
| Arrays and prefix sums | `02-dsa/code/01-arrays/01-arrays-strings.md` | `02-dsa/code/01-arrays/ArrayPatterns.java` | `02-dsa/code/01-arrays/ArrayPatternsTest.java` |
| Strings | `02-dsa/code/02-strings/02-strings.md` | `02-dsa/code/02-strings/StringPatterns.java` | — |
| Two pointers | `02-dsa/code/03-twopointers/03-two-pointers.md` | `02-dsa/code/03-twopointers/TwoPointerPatterns.java` | — |
| Sliding window | `02-dsa/code/04-slidingwindow/02-sliding-window.md` | `02-dsa/code/04-slidingwindow/SlidingWindowPatterns.java` | — |
| Binary search | `02-dsa/code/05-binarysearch/04-binary-search.md` | `02-dsa/code/05-binarysearch/BinarySearchPatterns.java` | — |
| Stack and monotonic stack | `02-dsa/code/06-stack/05-stack-monotonic.md` | `02-dsa/code/06-stack/StackPatterns.java` | — |
| Linked list | `02-dsa/code/07-linkedlist/06-linked-list.md` | `02-dsa/code/07-linkedlist/LinkedListPatterns.java` | — |
| Trees and BST | `02-dsa/code/08-trees/07-trees-bst.md` | `02-dsa/code/08-trees/TreePatterns.java` | — |
| Heaps | `02-dsa/code/09-heaps/08-heaps.md` | `02-dsa/code/09-heaps/HeapPatterns.java` | — |
| Graphs | `02-dsa/code/10-graphs/09-graphs.md` | `02-dsa/code/10-graphs/GraphPatterns.java` | — |
| Backtracking | `02-dsa/code/11-backtracking/10-backtracking.md` | `02-dsa/code/11-backtracking/BacktrackingPatterns.java` | — |
| Dynamic programming | `02-dsa/code/12-dp/11-dynamic-programming.md` | `02-dsa/code/12-dp/DynamicProgrammingPatterns.java` | — |
| Trie and union-find | `02-dsa/code/13-trie/12-trie-union-find.md` | `02-dsa/code/13-trie/TrieAndUnionFind.java` | — |
| Intervals | `02-dsa/code/14-intervals/14-intervals.md` | `02-dsa/code/14-intervals/IntervalPatterns.java` | `02-dsa/code/14-intervals/IntervalPatternsTest.java` |
| SQL/backend database practice | `02-dsa/code/15-sql/README.md` | `02-dsa/code/15-sql/schema-practice.sql` and `02-dsa/code/15-sql/answers.md` | Practice queries in `SqlPracticeQuestions.md` |

## System design

| Topic | Read first | Study next | Validation |
|---|---|---|---|
| Databases | `03-system-design/code/01-databases/01-database-patterns.md` | `03-system-design/code/01-databases/DatabasePatternsExamples.java` | — |
| Cache patterns | `03-system-design/code/02-cache/03-caching-deep-dive.md` | `03-system-design/code/02-cache/CachePatterns.java` | — |
| CAP and consistency | `03-system-design/code/02-cache/04-cap-consistency-models.md` | `03-system-design/code/02-cache/ConsistencyModelsExamples.java` | — |
| Consistent hashing | `03-system-design/code/03-consistent-hashing/01-consistent-hashing.md` | `03-system-design/code/03-consistent-hashing/ConsistentHashingExamples.java` | — |
| Messaging | `03-system-design/code/04-messaging/01-messaging-patterns.md` | `03-system-design/code/04-messaging/MessagingPatternsExamples.java` | — |
| Rate limiting | `03-system-design/code/05-ratelimiter/06-api-design-patterns.md` | `03-system-design/code/05-ratelimiter/RateLimiterPatterns.java` | `03-system-design/code/05-ratelimiter/RateLimiterPatternsTest.java` |
| Idempotency and outbox | `03-system-design/code/06-idempotency/05-distributed-transactions.md` | `03-system-design/code/06-idempotency/IdempotencyAndOutboxPatterns.java` | — |
| Circuit breaker | `03-system-design/code/07-circuitbreaker/07-observability-and-slo.md` | `03-system-design/code/07-circuitbreaker/CircuitBreakerPatterns.java` | — |
| Observability | `03-system-design/code/08-observability/01-observability-patterns.md` | `03-system-design/code/08-observability/ObservabilityPatternsExamples.java` | — |
| Security patterns | `03-system-design/code/09-security/01-security-patterns.md` | `03-system-design/code/09-security/SecurityPatternsExamples.java` | — |
| Database internals | `03-system-design/code/10-database-internals/01-database-internals.md` | `03-system-design/code/10-database-internals/DatabaseInternalsPatterns.java` | — |
| Distributed systems internals | `03-system-design/code/11-distributed-systems-internals/01-distributed-systems-internals.md` | `03-system-design/code/11-distributed-systems-internals/DistributedSystemsInternalsExamples.java` | — |
| Cloud and Kubernetes | `03-system-design/code/12-cloud-kubernetes/01-docker-kubernetes-cloud.md` | `03-system-design/code/12-cloud-kubernetes/CloudPlatformPatterns.java` | — |

## Low-level design / machine coding

| Topic | Read first | Study next | Validation |
|---|---|---|---|
| LLD principles | `06-low-level-design/01-lld-principles.md` | `06-low-level-design/02-solid-design.md` | Explain one design in 10 minutes |
| Design patterns | `06-low-level-design/03-design-patterns-in-interviews.md` | Existing Java design examples | Identify real variation points |
| Parking lot | `06-low-level-design/04-parking-lot.md` | `06-low-level-design/code/parkinglot/ParkingLotDesign.java` | Park/exit/no-spot test ideas |
| Splitwise | `06-low-level-design/05-splitwise.md` | `06-low-level-design/code/splitwise/SplitwiseDesign.java` | Equal/exact/mismatch test ideas |
| Rate limiter | `06-low-level-design/06-rate-limiter-lld.md` | `06-low-level-design/code/ratelimiter/RateLimiterDesign.java` | Burst/refill/concurrency test ideas |
| Cache | `06-low-level-design/07-cache-lld.md` | `06-low-level-design/code/cache/CacheDesign.java` | TTL/LRU/capacity test ideas |
| Logging framework | `06-low-level-design/08-logging-framework.md` | `06-low-level-design/code/logging/LoggingFrameworkDesign.java` | Level/filter/appender test ideas |
| Elevator | `06-low-level-design/09-elevator-system.md` | `06-low-level-design/code/elevator/ElevatorSystemDesign.java` | Scheduling/state transition test ideas |
| BookMyShow | `06-low-level-design/10-bookmyshow.md` | `06-low-level-design/code/bookmyshow/BookMyShowDesign.java` | Seat lock/expiry/concurrency test ideas |

## AI / GenAI backend

| Topic | Read first | Study next | Validation |
|---|---|---|---|
| LLM fundamentals | `04-ai-genai/code/01-llm-foundations/01-llm-fundamentals.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| RAG architecture | `04-ai-genai/code/03-rag/03-rag-architecture.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| Prompt engineering | `04-ai-genai/code/05-prompt-engineering/05-prompt-engineering.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| Embeddings and vector search | `04-ai-genai/code/02-embeddings/02-vector-databases.md` | `04-ai-genai/code/02-embeddings/EmbeddingPipelineExample.java` | — |
| Spring AI integration | `04-ai-genai/code/04-spring-ai/04-spring-ai.md` | `04-ai-genai/code/04-spring-ai/SpringAiIntegrationExample.java` | — |
| Production AI backend | `04-ai-genai/code/06-production/06-production-ai-backend.md` | `04-ai-genai/code/06-production/ProductionAiBackendExample.java` | — |

## Tracking, behavioral, company, and evaluation

| Area | Start here | Use weekly/monthly |
|---|---|---|
| Interview tracking | `06-interview-tracker/README.md` | `weekly-progress.md`, `mistake-log.md`, scorecards, `mock-feedback.md` |
| Behavioral leadership | `07-behavioral-leadership/README.md` | `story-bank.md` plus one story practice every week |
| Company-specific strategy | `08-company-specific/README.md` | Use in month 5 and month 6 for target companies |
| Monthly evaluation | `09-monthly-evaluation/README.md` | Complete the matching month gate before advancing |
| Final SDE-II bar | `SDE-II-READINESS-BAR.md` | Score monthly and during final readiness review |

## Production debugging drills

| Incident type | Playbook | Java reference |
|---|---|---|
| General triage | `01-java-backend/code/production-debugging/01-prod-debugging-checklist.md` | `01-java-backend/code/production-debugging/IncidentDebuggingPlaybook.java` |
| High CPU | `01-java-backend/code/production-debugging/02-high-cpu-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Memory leak / GC pressure | `01-java-backend/code/production-debugging/03-memory-leak-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Slow API | `01-java-backend/code/production-debugging/04-slow-api-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Database latency | `01-java-backend/code/production-debugging/05-database-latency-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Kafka lag | `01-java-backend/code/production-debugging/06-kafka-lag-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Redis/cache issue | `01-java-backend/code/production-debugging/07-redis-cache-issue-debugging.md` | `IncidentDebuggingPlaybook.java` |
| Thread pool exhaustion | `01-java-backend/code/production-debugging/08-thread-pool-exhaustion.md` | `IncidentDebuggingPlaybook.java` |
| Postmortem | `01-java-backend/code/production-debugging/09-incident-postmortem-template.md` | Use after every incident mock |

## Behavioral story path

| Competency | File | When to practice |
|---|---|---|
| STAR structure | `07-behavioral-leadership/01-star-method.md` | Month 1 and before every mock |
| Ownership | `07-behavioral-leadership/02-ownership-stories.md` | Month 1 onward |
| Conflict | `07-behavioral-leadership/03-conflict-resolution.md` | Month 2 onward |
| Technical leadership | `07-behavioral-leadership/04-technical-leadership.md` | Month 2 onward |
| Production incident | `07-behavioral-leadership/05-production-incident-story.md` | Month 3 onward |
| Failure and learning | `07-behavioral-leadership/06-failure-and-learning.md` | Month 4 onward |
| Mentoring | `07-behavioral-leadership/07-mentoring-and-collaboration.md` | Month 2 onward |
| Ambiguity | `07-behavioral-leadership/08-ambiguity-and-decision-making.md` | Month 1 onward |
| Story inventory | `07-behavioral-leadership/story-bank.md` | Update weekly |

## Company-specific path

| Company | File | Best use |
|---|---|---|
| Google | `08-company-specific/google.md` | Coding communication, scalable design, ambiguity |
| Microsoft | `08-company-specific/microsoft.md` | Practical trade-offs, collaboration, customer focus |
| Amazon | `08-company-specific/amazon.md` | Leadership Principles, ownership, operational excellence |
| Uber | `08-company-specific/uber.md` | Marketplace scale, real-time systems, reliability |
| Flipkart | `08-company-specific/flipkart.md` | DSA, LLD/machine coding, commerce backend |
| Atlassian | `08-company-specific/atlassian.md` | Code design, system design, values, collaboration |
| LinkedIn | `08-company-specific/linkedin.md` | Feed/search/graph systems and product impact |
| Cross-company patterns | `08-company-specific/interview-patterns.md` | Before choosing target companies |

## Monthly gates

| Month | Gate file | Required decision |
|---|---|---|
| Month 1 | `09-monthly-evaluation/month-1-gate.md` | Fundamentals pass/fail |
| Month 2 | `09-monthly-evaluation/month-2-gate.md` | Java, concurrency, LLD start |
| Month 3 | `09-monthly-evaluation/month-3-gate.md` | Distributed systems and debugging |
| Month 4 | `09-monthly-evaluation/month-4-gate.md` | Advanced design, DP, AI/RAG |
| Month 5 | `09-monthly-evaluation/month-5-gate.md` | Interview simulation and company strategy |
| Month 6 | `09-monthly-evaluation/month-6-final-readiness.md` | Final SDE-II readiness |

## Recommended operating order

1. Follow `08-6-month-study-plan.md` for sequencing.
2. Use this file for exact navigation.
3. Use `06-interview-tracker/weekly-progress.md` every week.
4. Use `10-mock-interview-bank.md` weekly.
5. Use `09-monthly-evaluation/` and `09-readiness-checklist.md` monthly.
6. Use `08-company-specific/` and `SDE-II-READINESS-BAR.md` heavily in months 5 and 6.
