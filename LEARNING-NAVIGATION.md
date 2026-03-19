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

## AI / GenAI backend

| Topic | Read first | Study next | Validation |
|---|---|---|---|
| LLM fundamentals | `04-ai-genai/code/01-llm-foundations/01-llm-fundamentals.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| RAG architecture | `04-ai-genai/code/03-rag/03-rag-architecture.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| Prompt engineering | `04-ai-genai/code/05-prompt-engineering/05-prompt-engineering.md` | `04-ai-genai/code/03-rag/RagServiceExample.java` | — |
| Embeddings and vector search | `04-ai-genai/code/02-embeddings/02-vector-databases.md` | `04-ai-genai/code/02-embeddings/EmbeddingPipelineExample.java` | — |
| Spring AI integration | `04-ai-genai/code/04-spring-ai/04-spring-ai.md` | `04-ai-genai/code/04-spring-ai/SpringAiIntegrationExample.java` | — |
| Production AI backend | `04-ai-genai/code/06-production/06-production-ai-backend.md` | `04-ai-genai/code/06-production/ProductionAiBackendExample.java` | — |

## Recommended operating order

1. Follow `08-6-month-study-plan.md` for sequencing.
2. Use this file for exact navigation.
3. Use `09-readiness-checklist.md` monthly.
4. Use `10-mock-interview-bank.md` weekly.