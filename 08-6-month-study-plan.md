# 6-Month Study Plan

This plan assumes 22-28 focused hours per week outside regular work.
If you have less time, keep the sequence and reduce volume, not depth.

Study assets to use during this plan:
- Java/backend theory: `02-java-backend-mastery.md`
- DSA theory and patterns: `03-dsa-roadmap.md`
- System design theory: `04-system-design-roadmap.md`
- AI/backend theory: `05-ai-genai-for-backend.md`
- Code references: `01-java-backend/code/`, `02-dsa/code/`, `03-system-design/code/`, `04-ai-genai/code/`

## Monthly goals overview

### Month 1: reset fundamentals
Focus:
- Java core refresh
- arrays, strings, hashing, sliding window, binary search
- system design basics
- start flagship project setup

### Month 2: backend and concurrency depth
Focus:
- JVM, GC, collections, multithreading
- trees, heaps, intervals, linked lists
- Spring Boot production concepts
- project APIs and persistence layer

### Month 3: distributed systems and medium-depth design
Focus:
- Kafka, Redis, transactions, retries, idempotency
- graphs, tries, backtracking
- design drills on API-heavy systems
- add async flows and observability to project

### Month 4: advanced design + AI/RAG
Focus:
- CAP, consistency, messaging semantics, scaling patterns
- DP and hard medium problems
- build retrieval + embeddings pipeline
- start mock interviews seriously

### Month 5: interview simulation phase
Focus:
- timed DSA
- full system design mocks
- AI/backend architecture discussion
- resume and behavioral polish

### Month 6: application-ready execution
Focus:
- mixed mocks
- weak-area repair
- final project polish
- targeted applications and company-specific prep

## Weekly structure

### Monday
- 90 min DSA
- 60 min Java/backend theory
- 30 min notes and mistake log

### Tuesday
- 90 min DSA
- 90 min project implementation

### Wednesday
- 60 min system design
- 60 min AI/backend learning
- 60 min project implementation

### Thursday
- 90 min DSA
- 60 min Java or distributed systems review

### Friday
- 60 min design drill
- 90 min project implementation

### Saturday
- 2 hours revision of failed DSA problems
- 2 hours system design or AI deep dive
- 2 hours project work

### Sunday
- 1 mock interview or timed set
- 1 hour behavioral story practice
- 1 hour weekly review and planning

## 24-week execution plan

### Weeks 1-2
Goals:
- refresh Java syntax speed and collections
- solve 20-24 problems on arrays, strings, hashing
- learn system design answer framework
- choose 2 main projects

Deliverables:
- DSA tracker started
- first project repository created
- one resume draft baseline

Theory focus:
- read Java collections and complexity notes
- revise REST fundamentals and HTTP semantics

Code focus:
- implement two-pointer and sliding-window problems from `02-dsa/code/03-twopointers/TwoPointerPatterns.java` and `02-dsa/code/04-slidingwindow/SlidingWindowPatterns.java`

### Weeks 3-4
Goals:
- master sliding window, two pointers, binary search
- revise Spring Boot internals and API design
- design URL shortener and rate limiter
- implement first CRUD and auth foundations in project

Deliverables:
- 2 design writeups
- project skeleton with API docs

### Weeks 5-6
Goals:
- JVM, memory, GC, class loading
- linked list, stack, queue, heap patterns
- database indexing and transaction review
- add DB schema and cache layer to project

Deliverables:
- Java fundamentals notes
- one performance-focused mini writeup

Theory focus:
- understand heap vs stack vs metaspace
- understand why GC tuning without measuring allocation rate is usually shallow optimization

Code focus:
- study and re-implement selected parts of `ConcurrencyAndJvmExamples.java`

### Weeks 7-8
Goals:
- multithreading, locks, volatile, thread pools
- trees and BST mastery
- microservices resilience patterns
- implement async processing / messaging in project

Deliverables:
- concurrency cheat sheet
- project event flow diagram

### Weeks 9-10
Goals:
- Kafka, idempotency, retries, outbox concepts
- graphs: BFS, DFS, topo sort
- design notification service and order system
- add observability basics to project

Deliverables:
- 2 mock design recordings or notes
- metrics/logging plan for project

Theory focus:
- understand at-least-once delivery and why idempotency matters
- understand outbox pattern and consumer retries

Code focus:
- walk through `03-system-design/code/06-idempotency/IdempotencyAndOutboxPatterns.java`

### Weeks 11-12
Goals:
- Redis, caching, consistency trade-offs
- backtracking + trie
- secure APIs and Spring Security basics
- implement rate limiting or cache optimization in project

Deliverables:
- one polished backend feature with performance note

### Weeks 13-14
Goals:
- CAP theorem, replication, quorum, eventual consistency
- start dynamic programming seriously
- begin AI/GenAI foundations: tokens, embeddings, vector DB
- choose RAG use case for project

Deliverables:
- AI architecture draft
- DP pattern notes

### Weeks 15-16
Goals:
- RAG ingestion pipeline design
- implement chunking, embedding, vector retrieval
- design document Q and A system
- 2 timed coding mocks

Deliverables:
- working RAG prototype
- prompt version 1

Theory focus:
- understand chunking strategy, embedding flow, and citation-grounded generation

Code focus:
- implement or adapt the flow from `04-ai-genai/code/rag/RagServiceExample.java`

### Weeks 17-18
Goals:
- prompt engineering, evaluation, hallucination control
- design AI support copilot end to end
- refine DP and graph problem solving
- add citations and fallback logic to project

Deliverables:
- evaluation checklist for AI answers
- second project or advanced module started

### Weeks 19-20
Goals:
- full design simulations weekly
- hard medium / selective hard DSA focus
- finalize main project architecture docs
- improve resume bullets with metrics

Deliverables:
- final architecture diagram
- updated resume draft

### Weeks 21-22
Goals:
- Google-style coding communication practice
- Microsoft-style behavioral + practical system trade-offs
- 2 full mocks per week
- fix weak DSA/design areas

Deliverables:
- weakness tracker
- behavioral story bank

### Weeks 23-24
Goals:
- final polish and application readiness
- project README refinement
- targeted company prep and interview loops
- rest and sharpen, not random new content

Deliverables:
- application-ready resume
- project portfolio links
- readiness checklist complete

## Weekly review questions

Every Sunday answer:
- What pattern still feels weak?
- Which design topic did I avoid this week?
- Did I ship something visible in a project?
- What would I fail if interviewed tomorrow?
- What is the single most important focus for next week?

## Non-negotiables for the 6 months

- 1 mock every week minimum
- 1 revision day every week minimum
- 2 serious projects minimum
- written mistake log for DSA and design
- no passive-only learning for more than 2 days in a row

Definition of a good week:
- at least one solved coding pattern you can now explain from first principles
- one backend or AI concept you can defend in interview language
- one visible project artifact: code, design note, benchmark, or README improvement
