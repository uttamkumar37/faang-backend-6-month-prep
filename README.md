# 6-Month FAANG Backend Preparation Pack
### Target: SDE II / Senior Backend at Google, Microsoft, and top product companies

---

## How to use this pack

1. Start with `08-6-month-study-plan.md` — your week-by-week execution plan.
2. For each topic, open the numbered markdown note that sits beside the code in that module, then study and re-implement the Java example.
3. Use `10-mock-interview-bank.md` for weekly mock questions.
4. Build 2 flagship projects from `05-projects/`.
5. Track execution every week in `06-interview-tracker/`.
6. Practice behavioral leadership stories from `07-behavioral-leadership/` every month.
7. Add LLD practice from `06-low-level-design/` starting in month 2.
8. Add production debugging drills from `01-java-backend/code/production-debugging/` starting in month 3.
9. Run monthly gates from `09-monthly-evaluation/` before advancing.
10. Use `08-company-specific/` in months 5 and 6 for final company targeting.
11. Run the readiness self-score in `09-readiness-checklist.md` and `SDE-II-READINESS-BAR.md` monthly.
12. Use `LEARNING-NAVIGATION.md` when you want a direct map from topic to theory note, code file, and test.

---

## Senior Backend Additions

| Area | Start here | Why it matters |
|---|---|---|
| Interview tracking | [06-interview-tracker/README.md](06-interview-tracker/README.md) | Weekly progress, mistake log, scorecards, mock feedback, monthly review |
| Behavioral leadership | [07-behavioral-leadership/README.md](07-behavioral-leadership/README.md) | STAR stories for ownership, conflict, incidents, leadership, failure, mentoring, ambiguity |
| Production debugging | [01-java-backend/code/production-debugging/README.md](01-java-backend/code/production-debugging/README.md) | JVM/Linux incident playbooks for CPU, memory, APIs, DB, Kafka, Redis, thread pools |
| SQL/database practice | [02-dsa/code/15-sql/README.md](02-dsa/code/15-sql/README.md) | Joins, windows, indexing, transactions, pagination, N+1 |
| Low-level design | [06-low-level-design/README.md](06-low-level-design/README.md) | Machine-coding and object design drills with Java skeletons |
| Company strategy | [08-company-specific/README.md](08-company-specific/README.md) | Google, Microsoft, Amazon, Uber, Flipkart, Atlassian, LinkedIn prep focus |
| Monthly gates | [09-monthly-evaluation/README.md](09-monthly-evaluation/README.md) | Pass/fail criteria and recovery plans for each month |
| Final readiness bar | [SDE-II-READINESS-BAR.md](SDE-II-READINESS-BAR.md) | 10/10 SDE-II readiness definition across 16 areas |

---

## Actual Folder Structure

```
faang-backend-6-month-prep/
│
├── README.md                          ← this file
│
├── Roadmap docs (start here)
│   ├── 01-core-skills-required.md
│   ├── 02-java-backend-mastery.md
│   ├── 03-dsa-roadmap.md
│   ├── 04-system-design-roadmap.md
│   ├── 05-ai-genai-for-backend.md
│   ├── 06-project-portfolio.md
│   ├── 07-resume-profile-building.md
│   ├── 08-6-month-study-plan.md
│   ├── 09-readiness-checklist.md
│   ├── 10-mock-interview-bank.md
│   └── LEARNING-NAVIGATION.md
│
├── 01-java-backend/
│   └── code/
│       ├── 00-foundations/
│       │   ├── README.md
│       │   ├── 01-what-is-java-jdk-jvm.md
│       │   ├── 02-code-execution-flow.md
│       │   ├── 03-java-language-basics.md
│       │   ├── 04-oop-concepts.md
│       │   ├── 05-modern-java-features.md
│       │   ├── 06-generics-and-type-system.md
│       │   ├── 07-design-patterns.md
│       │   ├── DesignPatternsExamples.java
│       │   └── GenericsExamples.java
│       ├── collections/
│       │   ├── 01-list-and-queues.md
│       │   ├── 02-set-and-maps.md
│       │   ├── 03-collections.md
│       │   ├── 04-stream-api.md
│       │   ├── CollectionsDeepDive.java
│       │   ├── LRUCacheImpl.java
│       │   ├── LRUCacheImplTest.java
│       │   └── StreamApiExamples.java
│       ├── concurrency/
│       │   ├── 01-thread-fundamentals.md
│       │   ├── 02-concurrency.md
│       │   ├── 03-advanced-concurrency.md
│       │   ├── 04-reactive-programming.md
│       │   ├── AdvancedConcurrencyExamples.java
│       │   ├── CompletableFuturePatterns.java
│       │   ├── ConcurrencyAndJvmExamples.java
│       │   ├── ReactiveProgrammingExamples.java
│       │   └── ThreadPoolPatterns.java
│       ├── jvm/
│       │   ├── 01-jvm-internals.md
│       │   ├── 02-garbage-collectors.md
│       │   ├── 03-jvm-diagnostics.md
│       │   ├── 04-memory-management.md
│       │   └── JvmDiagnosticsExample.java
│       ├── performance/
│       │   ├── 01-jmh-benchmarking.md
│       │   ├── 02-profiling-and-gc-tuning.md
│       │   ├── 10-performance-tuning.md
│       │   └── PerformanceTuning.java
│       ├── linux-networking/
│       │   ├── 01-linux-and-process-basics.md
│       │   ├── 02-networking-for-backend.md
│       │   └── LinuxAndNetworkingExamples.java
│       ├── springboot/
│       │   ├── 05-spring-boot.md
│       │   ├── 06-microservices-patterns.md
│       │   ├── 07-rest-api-design.md
│       │   ├── 08-database-jpa.md
│       │   ├── 09-security.md
│       │   ├── 10-testing.md
│       │   ├── 11-kafka-messaging.md
│       │   ├── 12-observability.md
│       │   ├── 13-caching-redis.md
│       │   ├── CachingRedisExamples.java
│       │   ├── ObservabilityConfig.java
│       │   ├── ResiliencePatterns.java
│       │   └── SecurityConfig.java
│       ├── testing-delivery/
│       │   ├── 01-testing-strategy.md
│       │   ├── 02-ci-cd-release-engineering.md
│       │   └── TestingAndReleasePatterns.java
│       ├── production-debugging/
│       │   ├── README.md
│       │   ├── 01-prod-debugging-checklist.md
│       │   ├── 02-high-cpu-debugging.md
│       │   ├── 03-memory-leak-debugging.md
│       │   ├── 04-slow-api-debugging.md
│       │   ├── 05-database-latency-debugging.md
│       │   ├── 06-kafka-lag-debugging.md
│       │   ├── 07-redis-cache-issue-debugging.md
│       │   ├── 08-thread-pool-exhaustion.md
│       │   ├── 09-connection-pool-exhaustion.md
│       │   ├── 10-gc-pressure-debugging.md
│       │   ├── 11-incident-postmortem-template.md
│       │   └── IncidentDebuggingPlaybook.java
│
│       └── application.yml
│
├── 02-dsa/
│   └── code/
│       ├── 01-arrays/          01-arrays-strings.md, ArrayPatterns.java, ArrayPatternsTest.java, questions.md
│       ├── 02-strings/         02-strings.md, StringPatterns.java, questions.md
│       ├── 03-twopointers/     03-two-pointers.md, TwoPointerPatterns.java, questions.md
│       ├── 04-slidingwindow/   02-sliding-window.md, SlidingWindowPatterns.java, questions.md
│       ├── 05-binarysearch/    04-binary-search.md, BinarySearchPatterns.java, questions.md
│       ├── 06-stack/           05-stack-monotonic.md, StackPatterns.java, questions.md
│       ├── 07-linkedlist/      06-linked-list.md, LinkedListPatterns.java, questions.md
│       ├── 08-trees/           07-trees-bst.md, TreePatterns.java, questions.md
│       ├── 09-heaps/           08-heaps.md, HeapPatterns.java, questions.md
│       ├── 10-graphs/          09-graphs.md, GraphPatterns.java, questions.md
│       ├── 11-backtracking/    10-backtracking.md, BacktrackingPatterns.java, questions.md
│       ├── 12-dp/              11-dynamic-programming.md, DynamicProgrammingPatterns.java, questions.md
│       ├── 13-trie/            12-trie-union-find.md, TrieAndUnionFind.java, questions.md
│       ├── 14-intervals/       14-intervals.md, IntervalPatterns.java, IntervalPatternsTest.java, questions.md
│       └── 15-sql/             SQL joins, windows, indexes, transactions, pagination, schema, answers
│
├── 03-system-design/
│   ├── designs/
│   │   ├── 01-url-shortener.md
│   │   ├── 02-distributed-cache.md
│   │   ├── 03-rate-limiter.md
│   │   ├── 04-notification-system.md
│   │   ├── 05-twitter-feed.md
│   │   ├── 06-search-autocomplete.md
│   │   ├── 07-google-drive.md
│   │   ├── 08-payment-system.md
│   │   ├── 09-video-streaming.md
│   │   └── 10-web-crawler.md
│   └── code/
│       ├── 01-databases/          01-database-patterns.md, DatabasePatternsExamples.java
│       ├── 02-cache/              01-fundamentals.md, 02-cache-storage-models.md, 03-caching-deep-dive.md, 04-cap-consistency-models.md, CachePatterns.java, ConsistencyModelsExamples.java
│       ├── 03-consistent-hashing/ 01-consistent-hashing.md, ConsistentHashingExamples.java
│       ├── 04-messaging/          01-messaging-patterns.md, MessagingPatternsExamples.java
│       ├── 05-ratelimiter/        06-api-design-patterns.md, RateLimiterPatterns.java, RateLimiterPatternsTest.java
│       ├── 06-idempotency/        04-message-queues-kafka.md, 05-distributed-transactions.md, IdempotencyAndOutboxPatterns.java
│       ├── 07-circuitbreaker/     07-observability-and-slo.md, CircuitBreakerPatterns.java
│       ├── 08-observability/      01-observability-patterns.md, ObservabilityPatternsExamples.java
│       ├── 09-security/           01-security-patterns.md, SecurityPatternsExamples.java
│       ├── 10-database-internals/ 01-database-internals.md, DatabaseInternalsPatterns.java
│       ├── 11-distributed-systems-internals/ 01-distributed-systems-internals.md, DistributedSystemsInternalsExamples.java
│       └── 12-cloud-kubernetes/   01-docker-kubernetes-cloud.md, CloudPlatformPatterns.java
│
├── 04-ai-genai/
│   └── code/
│       ├── 01-llm-foundations/    01-llm-fundamentals.md
│       ├── 02-embeddings/         02-vector-databases.md, EmbeddingPipelineExample.java
│       ├── 03-rag/                03-rag-architecture.md, RagServiceExample.java
│       ├── 04-spring-ai/          04-spring-ai.md, SpringAiIntegrationExample.java
│       ├── 05-prompt-engineering/ 05-prompt-engineering.md
│       └── 06-production/         06-production-ai-backend.md, ProductionAiBackendExample.java
│
├── 05-projects/
│   ├── 01-ai-support-copilot/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 02-order-processing-platform/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 03-incident-intelligence-backend/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 04-rate-limiter-service/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 05-distributed-job-scheduler/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 06-social-media-feed-system/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 07-typeahead-search-service/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 08-realtime-fraud-detection/
│   │   ├── README.md
│   │   └── architecture.md
│   ├── 09-live-leaderboard-gaming-backend/
│   │   ├── README.md
│   │   └── architecture.md
│   └── 10-multitenant-billing-engine/
│       ├── README.md
│       └── architecture.md
│
├── 06-interview-tracker/
│   ├── README.md
│   ├── weekly-progress.md
│   ├── mistake-log.md
│   ├── dsa-pattern-scorecard.md
│   ├── system-design-scorecard.md
│   ├── java-backend-scorecard.md
│   ├── project-progress-tracker.md
│   ├── mock-feedback.md
│   └── monthly-review-template.md
│
├── 06-low-level-design/
│   ├── README.md
│   ├── 01-lld-principles.md
│   ├── 02-solid-design.md
│   ├── 03-design-patterns-in-interviews.md
│   ├── 04-parking-lot.md
│   ├── 05-splitwise.md
│   ├── 06-rate-limiter-lld.md
│   ├── 07-cache-lld.md
│   ├── 08-logging-framework.md
│   ├── 09-elevator-system.md
│   ├── 10-bookmyshow.md
│   └── code/
│       ├── parkinglot/ParkingLotDesign.java
│       ├── splitwise/SplitwiseDesign.java
│       ├── ratelimiter/RateLimiterDesign.java
│       ├── cache/CacheDesign.java
│       ├── logging/LoggingFrameworkDesign.java
│       ├── elevator/ElevatorSystemDesign.java
│       └── bookmyshow/BookMyShowDesign.java
│
├── 07-behavioral-leadership/
│   ├── README.md
│   ├── 01-star-method.md
│   ├── 02-ownership-stories.md
│   ├── 03-conflict-resolution.md
│   ├── 04-technical-leadership.md
│   ├── 05-production-incident-story.md
│   ├── 06-failure-and-learning.md
│   ├── 07-mentoring-and-collaboration.md
│   ├── 08-ambiguity-and-decision-making.md
│   └── story-bank.md
│
├── 08-company-specific/
│   ├── README.md
│   ├── google.md
│   ├── microsoft.md
│   ├── amazon.md
│   ├── uber.md
│   ├── flipkart.md
│   ├── atlassian.md
│   ├── linkedin.md
│   └── interview-patterns.md
│
├── 09-monthly-evaluation/
│   ├── README.md
│   ├── month-1-gate.md
│   ├── month-2-gate.md
│   ├── month-3-gate.md
│   ├── month-4-gate.md
│   ├── month-5-gate.md
│   └── month-6-final-readiness.md
│
└── SDE-II-READINESS-BAR.md
```

---

## Recommended weekly time split

| Area | Hours/week |
|---|---|
| DSA coding practice | 10-12 |
| Java + backend theory | 5-6 |
| System design | 4-5 |
| LLD / machine coding | 2-3 |
| AI/GenAI backend | 4-5 |
| Project building | 6-8 |
| Production debugging drills | 1-2 |
| Resume + behavioral | 2-3 |
| Tracking, review, and mocks | 2 |

## Weekly Operating Instructions

- Monday: choose the DSA patterns, Java/backend topic, design prompt, LLD/problem area, project artifact, and behavioral story for the week.
- Wednesday: update [weekly-progress.md](06-interview-tracker/weekly-progress.md) and cut scope if execution is drifting.
- Sunday: run one mock or timed set, update [mistake-log.md](06-interview-tracker/mistake-log.md), score the week, and schedule repairs.

## Monthly Evaluation Reminders

- End of every month: complete the matching gate in [09-monthly-evaluation/](09-monthly-evaluation/README.md).
- Do not advance on calendar alone; a month passes only when the gate score is 75+ and no critical area is below the file threshold.
- Month 5 and month 6 must include company-specific revision, behavioral story rehearsal, and final SDE-II readiness scoring.

---

## Study sequence inside each topic folder

For every topic:
1. Read the numbered markdown doc in the relevant folder fully — understand before coding.
2. Study the code file, trace through it, and add comments.
3. Delete the code file and re-implement from scratch.
4. Test edge cases.
5. Write 3 interview sentences explaining the concept.

---

## Ground rules

- Depth beats breadth. One pattern understood deeply beats five memorized shallowly.
- Every system design answer must include trade-offs, failure modes, and observability.
- Every project needs a measurable outcome: latency, throughput, cost, or reliability metric.
- One mock interview every week without exception.

---

## Target outcome after 6 months

- Solve medium DSA problems consistently in under 30 minutes.
- Design 8+ backend systems with trade-off reasoning.
- Explain JVM, concurrency, Spring Boot, Kafka, Redis, and CAP theorem deeply.
- Build AI-enabled backend services with RAG pipelines.
- Present your work as an ownership-driven senior backend engineer.
- Complete 8+ LLD/machine-coding designs with Java skeletons and test cases.
- Debug common production incidents using JVM/Linux commands, metrics, logs, and postmortems.
- Pass company-targeted mock loops for at least 2 target companies.
- Meet the final SDE-II bar in [SDE-II-READINESS-BAR.md](SDE-II-READINESS-BAR.md).
