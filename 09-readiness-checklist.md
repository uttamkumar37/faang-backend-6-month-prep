# Readiness Checklist

## 1. DSA readiness

You are ready when most of these are true:
- I can solve most medium problems in 25-30 minutes.
- I can identify common patterns quickly.
- I speak my thought process clearly.
- My first full code version is usually close to correct.
- I test edge cases without being reminded.
- I can explain time and space complexity precisely.
- I have SQL/database practice coverage from `02-dsa/code/15-sql/`.
- I can solve backend SQL joins, windows, indexing, transaction, pagination, and N+1 scenarios.

## 2. Java/backend readiness

- I can explain JVM memory areas and common GC issues.
- I understand Java concurrency beyond syntax.
- I can reason about thread pools and backpressure.
- I know how to diagnose DB, cache, and latency problems.
- I can explain Spring Boot internals at interview depth.
- I can discuss API security, idempotency, and reliability patterns.

## 3. System design readiness

- I begin with requirements and assumptions.
- I can estimate scale quickly enough.
- I can justify SQL vs NoSQL choices.
- I can discuss caching and invalidation trade-offs.
- I can explain consistency model choices.
- I can identify failure points and mitigation.
- I include observability and deployment concerns.

## 4. Low-level design readiness

- I can clarify requirements before naming classes.
- I can produce entities, APIs, class diagram text, edge cases, and tests.
- I have practiced parking lot, Splitwise, rate limiter, cache, logging framework, elevator, and BookMyShow.
- I can code a Java skeleton within a machine-coding time box.
- I can explain extensibility without overengineering.
- I can handle concurrency-sensitive flows like seat locking, cache access, and rate limiting.

## 5. Production debugging readiness

- I can debug high CPU with `top -H`, thread dumps, and GC checks.
- I can distinguish memory leak from high allocation rate and GC pressure.
- I can debug slow APIs using route latency, traces, thread pools, DB pool wait, and downstream metrics.
- I can debug DB latency using query plans, locks, slow query logs, and connection pool metrics.
- I can debug Kafka lag, Redis hot keys/evictions, and thread pool exhaustion.
- I can write a postmortem with root cause, trigger, mitigation, prevention, owner, and due date.

## 6. AI/backend readiness

- I understand tokens, embeddings, vector search, and RAG.
- I can explain when RAG is better than fine-tuning.
- I can design an AI feature with latency/cost constraints.
- I know how to reduce hallucinations.
- I can discuss safety, evaluation, and fallback strategies.
- I can explain Java integration with AI APIs clearly.

## 7. Resume readiness

- Most bullets have metrics.
- My work shows ownership, not just implementation.
- I have at least 2 strong stories on scale/reliability/performance.
- I have at least 1 AI-related project or real exposure story.
- My summary is short and strong.

## 8. Behavioral readiness

Have STAR stories for:
- conflict with stakeholder or teammate
- production incident ownership
- difficult technical decision
- project with ambiguity
- mentoring someone
- failure and recovery
- influencing without authority
- mentoring and collaboration
- ambiguity and decision-making

## 9. Company-specific readiness

- I have selected at least 2 target companies.
- I have read the matching files in `08-company-specific/`.
- I have run at least 2 company-style mocks per target company.
- I can explain why the company and role fit my backend experience.
- I have verified round structure with recruiter when an interview is scheduled.

## 10. Monthly evaluation readiness

- I complete the relevant file in `09-monthly-evaluation/` at the end of every month.
- I do not advance if the gate score is below 75 or a critical category is below threshold.
- I maintain weekly progress, mistake log, mock feedback, and scorecards in `06-interview-tracker/`.

## 11. Final application bar

You should ideally complete before applying aggressively:
- 200 plus DSA problems with revision history
- 8 plus coding mocks
- 6 plus system design mocks
- 8 plus LLD/machine-coding designs
- 6 plus production debugging incident drills
- 2 polished backend projects
- 1 AI-enabled project or serious feature
- strong resume reviewed multiple times
- 7 plus behavioral stories with metrics
- company-specific revision for at least 2 target companies
- `SDE-II-READINESS-BAR.md` completed with no core area below interview-ready

## 12. Self-scoring rubric

Rate yourself from 1 to 5 on each:
- coding speed
- coding correctness
- Java depth
- backend production maturity
- system design clarity
- LLD / machine coding
- SQL and database depth
- production debugging
- distributed systems depth
- AI integration depth
- behavioral storytelling
- project quality
- resume quality
- company-specific readiness

Interpretation:
- 60-65: strong apply zone
- 52-59: close, fix specific gaps
- 42-51: not ready for strong loops yet
- below 42: rebuild fundamentals first
