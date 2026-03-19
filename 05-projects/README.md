# 05 — Projects

## What's in this folder

10 FAANG-quality backend + AI projects. Each has a README with problem statement, requirements, API design, and architecture; plus an architecture diagram doc.

| Project | Focus |
|---|---|
| 01-ai-support-copilot/ | RAG over enterprise docs, grounded answers, citations |
| 02-order-processing-platform/ | Distributed order lifecycle, saga, outbox, idempotency |
| 03-incident-intelligence-backend/ | AI-powered log/ticket summarization, root-cause hints |
| 04-rate-limiter-service/ | Platform-level rate limiting with distributed counters |
| 05-distributed-job-scheduler/ | Exactly-once cron, leader election, time-wheel, DAG dependencies |
| 06-social-media-feed-system/ | Fan-out write vs read, celebrity problem, Redis timeline at 500M DAU |
| 07-typeahead-search-service/ | Prefix index, trending, 100K QPS, zero-downtime reindex |
| 08-realtime-fraud-detection/ | Sub-50ms ML scoring, velocity counters, fail-open, 50K TPS |
| 09-live-leaderboard-gaming-backend/ | Redis sorted sets, WebSocket push, anti-cheat, seasonal resets |
| 10-multitenant-billing-engine/ | Idempotent charges, dunning, usage metering, immutable audit ledger |

## What makes a project FAANG-quality

Every project must show:
- Hard backend engineering problem, not a tutorial clone.
- Production-like architecture with failure modes addressed.
- Observability: metrics, structured logs, tracing consideration.
- At least one measurable outcome: latency, throughput, reliability, cost.
- Trade-off discussion in the architecture doc.

## How to use these

1. Read the project README first.
2. Read the architecture doc.
3. Implement it yourself — use the architecture as a guide.
4. Add a benchmark or load test result.
5. Write 3 bullet points of impact in your resume format.

## Portfolio artifact checklist

Every project should be able to answer these clearly:
- What user or platform problem does this solve?
- What scale assumptions drive the design?
- Which failure modes matter most?
- What metrics prove the solution works?
- What trade-offs were made on purpose?

Minimum deliverables per project:
- README with functional and non-functional requirements
- architecture doc with component flow, trade-offs, and failure mitigation
- one measurable target such as latency, throughput, noise reduction, or cost control
- three resume-ready impact bullets

## Best order to build these

1. `02-order-processing-platform/` for core distributed backend depth
2. `04-rate-limiter-service/` for platform and systems depth
3. `05-distributed-job-scheduler/` for distributed coordination and fault-tolerant scheduling
4. `06-social-media-feed-system/` for high-throughput read/write design and cache strategy
5. `08-realtime-fraud-detection/` for stream processing and low-latency ML serving
6. `09-live-leaderboard-gaming-backend/` for Redis data structures and WebSocket at scale
7. `07-typeahead-search-service/` for search indexing, ranking, and zero-downtime operations
8. `10-multitenant-billing-engine/` for financial correctness, idempotency, and audit trails
9. `01-ai-support-copilot/` for AI-enabled backend ownership
10. `03-incident-intelligence-backend/` for advanced observability plus AI integration

## Interview signal

Strong projects show:
- You understand distributed systems trade-offs.
- You make explicit choices, not default choices.
- You care about correctness, not just functionality.
- You think about scale, reliability, and operations.
