# Project Portfolio Playbook

Use this guide to turn the projects in `05-projects/` into a portfolio that signals senior-backend readiness rather than tutorial-level work.

## 1. What strong projects prove

A strong portfolio project should prove at least four things:
- you can define a real product or platform problem
- you can choose system boundaries and justify trade-offs
- you can implement for reliability, observability, and scale
- you can explain results with metrics, not adjectives

If a project only proves CRUD ability, it will not carry much interview weight.

## 2. Which projects to prioritize

Build at least two flagship projects:

### Project 1: distributed backend core
Pick one of:
- `05-projects/02-order-processing-platform/`
- `05-projects/04-rate-limiter-service/`

This project should demonstrate:
- API design
- persistence model
- caching or messaging
- failure handling
- testing strategy
- performance or scale reasoning

### Project 2: AI-enabled backend product
Pick one of:
- `05-projects/01-ai-support-copilot/`
- `05-projects/03-incident-intelligence-backend/`

This project should demonstrate:
- ingestion or retrieval pipeline
- LLM integration
- safety and fallback strategy
- quality evaluation
- cost and latency trade-offs

## 3. The FAANG-quality checklist

Before calling a project portfolio-ready, make sure it covers these areas.

### Problem clarity
- One clear paragraph explaining the user problem.
- Constraints stated explicitly: latency, consistency, throughput, data size, safety, or cost.

### Architecture clarity
- A diagram with request flow and asynchronous components.
- One section called `Trade-offs`.
- One section called `Failure modes`.
- One section called `Observability`.

### Production realism
- Idempotency where writes can be retried.
- Timeouts, retries, and backoff where remote calls exist.
- Rate limiting or quota protection where abuse is possible.
- Health checks and configuration separation.

### Measurable outcomes
- At least one benchmark, load-test result, or estimation.
- Example metrics: P95 latency, throughput, retrieval hit rate, queue lag, cost per 1K requests.

### Interview readiness
- You can explain why you did not choose two other plausible designs.
- You can explain the data model and bottlenecks without opening the code.
- You can describe how the system fails and recovers.

## 4. What to add to every project README

Use this structure:

1. Problem statement
2. Functional requirements
3. Non-functional requirements
4. API surface
5. Data model
6. Architecture overview
7. Trade-offs
8. Failure modes and mitigations
9. Observability
10. Local run instructions
11. Benchmark or evaluation results
12. Resume-ready impact bullets

## 5. Resume-quality project bullets

Bad bullet:
- Built a rate limiter service using Redis.

Better bullet:
- Designed a distributed rate-limiter service with Redis-backed counters and sliding-window enforcement, targeting sub-10 ms decision latency at 5K requests/sec.

Bad bullet:
- Created an AI support bot.

Better bullet:
- Built a RAG-based support copilot with retrieval grounding and citation-based responses, reducing unsupported-answer risk through fallback handling and evaluation checks.

## 6. Month-by-month portfolio milestones

### Month 1
- Choose 2 flagship projects.
- Write the problem statement and architecture v1.

### Month 2
- Build the core API and persistence path.
- Add tests for core flows.

### Month 3
- Add async or caching behavior.
- Write the first observability plan.

### Month 4
- Add AI or advanced distributed-systems behavior.
- Benchmark at least one critical flow.

### Month 5
- Refine documentation and trade-offs.
- Practice explaining the project in 5, 10, and 20 minutes.

### Month 6
- Finalize README, architecture doc, and outcome metrics.
- Convert the work into resume bullets and interview stories.

## 7. Portfolio review questions

Ask these before you share the repo or use it in interviews:
- Does the project show a hard engineering decision?
- Does the README explain scale assumptions?
- Can someone understand the system without reading all code?
- Is there at least one result that sounds measurable and credible?
- Could I defend the trade-offs under follow-up questioning?