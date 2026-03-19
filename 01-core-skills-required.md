# Core Skills Required

This document defines what you must know and the depth expected for SDE II / Senior Backend roles.

## Target competency matrix

| Skill | Depth Required | What interviewers expect |
|---|---|---|
| Java core | Advanced | JVM internals, memory, collections, concurrency, performance trade-offs |
| DSA | Advanced | Fast problem solving, clean code, strong communication |
| System Design | Advanced | Scale, consistency, failure handling, cost/performance trade-offs |
| LLD / OOD | Intermediate to Advanced | Clean abstractions, extensibility, domain modeling |
| Spring Boot | Advanced | Production-grade APIs, security, testing, resilience |
| SQL + NoSQL | Advanced | Schema/indexing decisions, scaling, consistency |
| Distributed systems | Advanced | Messaging, idempotency, retries, consensus basics |
| Caching | Intermediate to Advanced | Cache patterns, invalidation, stampede prevention |
| Cloud / DevOps | Intermediate | Docker, CI/CD, deploys, observability |
| AI / GenAI basics | Intermediate to Advanced | API integration, RAG, embeddings, evaluation |
| Leadership / behavioral | Advanced | Ownership, influence, conflict handling, mentoring |

## Skill breakdown

### 1. Java
Know:
- JVM architecture
- GC behavior and tuning basics
- Java Memory Model
- concurrency primitives
- collections internals
- stream API trade-offs
- profiling and debugging

Depth expected:
- Intermediate is not enough.
- You must be able to explain why a design is safe, performant, and scalable.

### 2. DSA
Know:
- pattern-based problem solving
- complexity analysis
- edge case handling
- clean coding under time pressure

Depth expected:
- You should solve most medium problems in 25-30 minutes.
- You should be comfortable discussing multiple approaches.

### 3. System design
Know:
- requirement gathering
- capacity estimation
- storage, cache, queue, database choices
- consistency models
- reliability and observability

Depth expected:
- For 5 YOE, you must go beyond boxes and arrows.
- You need to reason about bottlenecks, failure domains, and growth.

### 4. Backend engineering
Know:
- REST API design
- authentication and authorization
- database transactions
- async processing
- rate limiting
- retries and circuit breakers
- testing pyramid
- deployment and rollback basics

### 5. AI / GenAI backend literacy
Know:
- how LLM APIs are used in backend systems
- prompt design basics
- embeddings and vector search
- RAG architecture
- latency, cost, hallucination control

Depth expected:
- You do not need to be an ML researcher.
- You must be able to design and implement AI-enabled product features safely.

## What gets candidates selected

Interviewers look for:
- structured thinking
- correctness first, optimization second
- strong trade-off language
- strong ownership examples
- ability to connect theory with production realities

## What gets candidates rejected

Common failure patterns:
- knows frameworks but not fundamentals
- memorized system design answers without trade-off reasoning
- weak DSA communication
- no measurable impact in resume/projects
- treats AI as prompt-only instead of system design + evaluation problem
