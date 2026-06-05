# System Design Scorecard

## Purpose
Score senior backend design answers with the same dimensions interviewers use: clarity, scale, trade-offs, failure handling, and operational maturity.

## Study Steps
- Use this file during the Sunday review block.
- Fill concrete numbers first: solved count, mocks, defects, shipped project work.
- Mark pass or fail using the criteria in the file, then pick one recovery action.

## Rubric
| Dimension | Points | Strong signal |
|---|---:|---|
| Requirements and scope | 10 | Functional and non-functional requirements clarified |
| Scale and estimates | 10 | Back-of-envelope math drives architecture choices |
| APIs and data model | 15 | Clean contracts, idempotency, pagination, schema decisions |
| Core architecture | 20 | Components fit the workload and constraints |
| Data flow and consistency | 10 | Clear write/read paths and consistency choices |
| Bottlenecks and scaling | 10 | Hot keys, partitions, fanout, queue pressure handled |
| Reliability and failure modes | 10 | Retries, timeouts, DLQ, degradation, disaster recovery |
| Observability and operations | 10 | Metrics, logs, traces, SLOs, runbooks |
| Communication | 5 | Structured, concise, adjusts to interviewer prompts |

## Result Bands
- 85-100: senior-ready.
- 75-84: passable, polish weak sections.
- 60-74: borderline, likely downlevel risk.
- Below 60: rebuild design framework.

## Interview Questions
- What are your top three non-functional requirements?
- What breaks first at 10x traffic?
- What metric tells you the system is unhealthy?

## Common Mistakes
- Jumping into components before requirements.
- No data model or API contract.
- Mentioning Kafka, Redis, or NoSQL without workload justification.

## Self-Check
- [ ] I can finish a 45-minute design with 5 minutes for risks.
- [ ] Every design includes observability and failure modes.
- [ ] I can defend one alternative architecture.

## Practical Example
Example: In a notification service, a senior answer separates send request ingestion, template rendering, provider dispatch, idempotency keys, retries, DLQ, provider health, and user preference consistency.
