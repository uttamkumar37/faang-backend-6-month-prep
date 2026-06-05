# Incident Postmortem Template

## Purpose
Turn production incidents into durable learning, prevention work, and interview-ready ownership stories.

## How to Use
Complete this template after every incident mock or real production issue. Score the write-up out of 100 and add action items to the project tracker.

## Incident Summary
- Title:
- Date/time:
- Service:
- Severity:
- Customer/business impact:
- Duration:
- Owner:

## Timeline
| Time | Event | Evidence | Decision |
|---|---|---|---|
| | Alert fired | | |
| | Mitigation started | | |
| | Recovery confirmed | | |

## Symptoms
- User-visible symptom:
- Internal symptom:
- Affected APIs/jobs:
- Error budget impact:

## First 5 Checks
1. Confirm scope: one endpoint, one region, one tenant, or global.
2. Check RED metrics: rate, errors, duration.
3. Check saturation: CPU, memory, thread pools, DB pool, queue lag.
4. Check recent changes: deploys, config, migrations, traffic shifts.
5. Capture logs/traces/dumps/query plans before destructive mitigation.

## Metrics to Inspect
- Request p95/p99, 5xx rate, saturation, queue depth.
- JVM heap/GC/thread metrics.
- DB latency, connection pool wait, slow queries.
- Redis hit/miss and command latency.
- Kafka consumer lag and retry/DLQ count.

## Logs to Inspect
- Application structured logs by correlation ID.
- Deployment logs and config-change logs.
- Dependency logs: DB, Redis, Kafka, ingress, container.

## Linux Commands
```bash
top
htop
ps aux | grep java
docker logs <container> --since 1h
docker stats
ss -tanp
netstat -tanp
lsof -p <pid>
curl -v http://localhost:8080/actuator/health
```

## JVM Commands
```bash
jstack <pid> > jstack.txt
jcmd <pid> Thread.print
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jmap -histo:live <pid> | head -50
```

## Database Commands Where Relevant
```sql
SHOW PROCESSLIST;
EXPLAIN SELECT * FROM posts WHERE category = 'java' ORDER BY created_at DESC;
SHOW ENGINE INNODB STATUS;
```

## Root Cause Patterns
- Missing index or query plan regression.
- Thread pool or connection pool exhaustion.
- Retry storm after dependency degradation.
- Cache stampede or Redis timeout.
- Bad deploy/config/migration.
- Data growth crossing a hidden limit.

## Immediate Mitigation
- Roll back, disable feature flag, reduce traffic, increase capacity, or shed load.
- Keep the mitigation reversible and record the exact command/config changed.
- Confirm recovery using user-visible metrics, not only logs.

## Long-Term Fix
- Code fix:
- Data/schema fix:
- Test/load-test fix:
- Alert/dashboard fix:
- Runbook fix:

## Prevention Strategy
- Add SLO alerting and owner.
- Add regression test or load test.
- Add capacity limit and graceful degradation.
- Add release checklist item.

## Interview Explanation
"I frame incidents around impact, timeline, evidence, mitigation, root cause, and prevention. I avoid blame, show ownership, and explain how I changed code, tests, alerts, or process so the same class of incident is less likely."

## Weekly Tracking Format
| Week | Incident scenario | MTTA | MTTR | Root cause quality | Prevention quality | Score /100 |
|---|---|---:|---:|---|---|---:|
| | | | | | | |

## Score Out of 100
- 20: Clear impact and timeline.
- 20: Evidence-driven root cause.
- 20: Good mitigation and rollback reasoning.
- 20: Durable prevention actions.
- 20: Strong communication and ownership.

## Pass/Fail Criteria
- Pass: 85+ with measurable customer impact and action items.
- Fail: Vague root cause, no evidence, no owner, or no prevention.

## Recovery Plan
Rewrite the postmortem with concrete timestamps, one metric screenshot equivalent, one root-cause proof, and three prevention actions.

## Common Mistakes
- Writing "service was slow" without impact or metric.
- Treating restart as root cause.
- Ending with "monitor more" without an alert or owner.

## Self-Check
- [ ] Root cause is specific and evidence-backed.
- [ ] Mitigation is separate from permanent fix.
- [ ] Each action item has owner, deadline, and verification.

## Practical Example
A login outage caused by DB connection pool exhaustion should produce a pool metric alert, query timeout, circuit breaker, load test, and runbook update.

## Interview Questions
- Tell me about a production incident you owned.
- How do you write a blameless postmortem?
- How do you make sure incident action items actually reduce risk?
