# Project Progress Tracker

## Purpose
Track flagship backend projects as interview evidence: architecture, code quality, observability, performance, and ownership stories.

## Study Steps
- Use this file during the Sunday review block.
- Fill concrete numbers first: solved count, mocks, defects, shipped project work.
- Mark pass or fail using the criteria in the file, then pick one recovery action.

## Project Table
| Project | Status | Backend depth | Metrics | Design doc | Demo path | Resume story |
|---|---|---|---|---|---|---|
| Project 1 | Not started/In progress/Ready | | | | | |
| Project 2 | Not started/In progress/Ready | | | | | |
| AI feature | Not started/In progress/Ready | | | | | |

## Readiness Rubric
| Dimension | Points | Evidence |
|---|---:|---|
| Requirements and APIs | 15 | OpenAPI/examples, validation, error model |
| Data model and transactions | 15 | Schema, indexes, isolation choices |
| Async/reliability | 15 | Kafka/outbox/retry/DLQ/idempotency where relevant |
| Cache/performance | 10 | Redis or measured optimization |
| Observability | 15 | Logs, metrics, traces, dashboards/runbook |
| Tests | 15 | Unit, integration, failure-path tests |
| Documentation and story | 15 | Architecture, trade-offs, quantified impact |

Pass: 80+ for at least two projects by month 6.

## Interview Questions
- What technical decision in this project would you defend to a principal engineer?
- What failed during development and how did you fix it?
- Which metric proves this project is production-minded?

## Common Mistakes
- Building CRUD-only projects with no failure handling.
- No benchmark, load test, or measurable result.
- Not having a crisp 2-minute ownership story.

## Self-Check
- [ ] Every project has a design doc and a working demo path.
- [ ] At least one project has async processing and observability.
- [ ] Resume bullets include scale, reliability, or latency metrics.

## Practical Example
Example: Order platform story: 'Added outbox-based order events, reduced duplicate shipment risk by enforcing idempotency keys, and exposed lag/error metrics for replay safety.'
