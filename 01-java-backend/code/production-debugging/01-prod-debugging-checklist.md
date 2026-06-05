# Production Debugging Checklist

## Purpose
Use a repeatable triage flow for any backend incident.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## First 10 Minutes
- Confirm impact: affected endpoint, tenant, region, traffic percentage, error budget burn.
- Check recent changes: deploy, config, feature flag, schema migration, traffic spike.
- Read dashboards: latency, error rate, throughput, CPU, memory, GC, thread pool, connection pool, dependency latency.
- Decide mitigation: rollback, disable flag, scale out, shed load, pause consumer, bypass dependency.

## Linux/JVM Commands
```bash
ps -ef | grep java
pidstat -p <pid> 1
pidstat -t -p <pid> 1
top -H -p <pid>
jcmd <pid> Thread.print > threads.txt
jcmd <pid> GC.heap_info
jcmd <pid> VM.flags
jstat -gcutil <pid> 1s 10
```

## Root Cause Buckets
- CPU saturation: hot loop, serialization, regex, lock contention, GC pressure.
- Memory: leak, unbounded cache, high allocation rate, large payloads.
- Latency: DB, external API, connection pool, thread pool, queue backlog.
- Data correctness: transaction boundary, stale cache, duplicate message, missing idempotency.

## Interview Questions
- What would you check first and why?
- How do you distinguish mitigation from root-cause fix?
- What metric or alert would prevent recurrence?

## Common Mistakes
- Changing multiple variables before isolating the symptom.
- Ignoring recent deploys and configuration changes.
- Declaring root cause without evidence from metrics, dumps, traces, or query plans.

## Self-Check
- [ ] I can name the first three metrics for this incident type.
- [ ] I know the JVM or dependency command to collect evidence.
- [ ] I can propose one immediate mitigation and one durable prevention.

## Practical Example
Example: 5xx spike after deploy -> compare new error logs by route, rollback if customer impact is growing, then inspect stack traces and missing configuration in the failed version.
