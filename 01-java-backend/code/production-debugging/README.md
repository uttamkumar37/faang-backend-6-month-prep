# Production Debugging and Incident Readiness

## Purpose
Provide interview-ready playbooks for diagnosing Java backend incidents under pressure.

## Study Steps
- Study the generic checklist first.
- Practice one incident scenario per week from month 3 onward.
- Use commands on a local Java process where possible.
- After every project feature, add one metric and one failure mode.

## Coverage
| Topic | File |
|---|---|
| General debugging | 01-prod-debugging-checklist.md |
| High CPU | 02-high-cpu-debugging.md |
| Memory leak and GC pressure | 03-memory-leak-debugging.md |
| Slow APIs and p99 latency | 04-slow-api-debugging.md |
| Database latency | 05-database-latency-debugging.md |
| Kafka lag | 06-kafka-lag-debugging.md |
| Redis/cache issues | 07-redis-cache-issue-debugging.md |
| Thread pool exhaustion | 08-thread-pool-exhaustion.md |
| Connection pool exhaustion | 09-connection-pool-exhaustion.md |
| GC pressure | 10-gc-pressure-debugging.md |
| Postmortem | 11-incident-postmortem-template.md |
| Java examples | IncidentDebuggingPlaybook.java |

## Interview Answer Shape
1. Clarify symptom and scope.
2. Name the first 3 metrics.
3. Name the artifact to capture: thread dump, heap dump, GC log, trace, query plan, consumer lag.
4. Give two likely root causes.
5. Give mitigation, fix, and prevention.

## Interview Questions
- How do you debug high CPU in a Java service?
- What is the difference between latency, saturation, and error-rate symptoms?
- How do you prevent the same incident from recurring?

## Common Mistakes
- Restarting before collecting evidence.
- Reading logs before checking saturation metrics.
- Calling every memory issue a leak without checking allocation rate and GC behavior.

## Self-Check
- [ ] I can explain which command gives thread dumps and heap histograms.
- [ ] I can separate mitigation from root-cause fix.
- [ ] Every project has at least basic RED metrics.

## Practical Example
Example: Slow API triage starts with p95/p99 by route, dependency spans, thread pool saturation, DB pool wait time, and recent deploys before changing SQL or cache settings.
