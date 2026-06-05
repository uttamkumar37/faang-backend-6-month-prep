# GC Pressure Debugging

## Purpose
Diagnose Java services where allocation rate, garbage collection, or heap pressure causes latency spikes, high CPU, or outages.

## How to Use
Use this playbook during month 3+ production-debugging drills and after JVM-related mock incidents. Score yourself out of 100 after explaining symptoms, evidence, mitigation, fix, and prevention.

## Symptoms
- p95/p99 latency spikes without proportional traffic growth.
- CPU rises while business throughput stays flat.
- Frequent young GC, long mixed/full GC, or `OutOfMemoryError`.
- Thread dumps show many requests waiting while GC logs show stop-the-world pauses.

## First 5 Checks
1. Confirm whether latency correlates with GC pause time.
2. Compare heap used after GC across the incident window.
3. Check allocation rate by endpoint or background job.
4. Capture thread dump and heap histogram before restarting.
5. Check recent deploys, batch jobs, traffic mix, and object-size changes.

## Metrics to Inspect
- `jvm.gc.pause`, `jvm.memory.used`, `jvm.memory.max`, allocation rate, promotion rate.
- CPU user/system time, request p95/p99, error rate, queue depth.
- Container memory limit and OOM kill count.

## Logs to Inspect
- GC logs with pause duration, heap before/after, old-gen occupancy.
- Application logs around large payloads, retries, serialization, and batch processing.
- Container/runtime logs for OOM kills.

## Linux Commands
```bash
top -H -p <pid>
htop
ps -o pid,ppid,pcpu,pmem,rss,vsz,cmd -p <pid>
docker stats
docker logs <container> --since 30m
ss -tanp
lsof -p <pid> | head
```

## JVM Commands
```bash
jcmd <pid> VM.flags
jcmd <pid> VM.native_memory summary
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jcmd <pid> Thread.print > threads.txt
jmap -histo:live <pid> | head -50
jstack <pid> > jstack.txt
```

## Database Commands Where Relevant
```sql
EXPLAIN SELECT * FROM posts WHERE category = 'java' ORDER BY created_at DESC;
SHOW PROCESSLIST;
```

Use DB checks when GC pressure is caused by loading too many rows, N+1 queries, or unbounded result sets.

## Root Cause Patterns
- Unbounded collections, large response payloads, missing pagination.
- ORM N+1 query creating many entity graphs.
- Excessive JSON serialization or repeated regex/string allocations.
- Cache stampede or cache storing large objects without TTL.
- Container heap larger than actual memory headroom.

## Immediate Mitigation
- Reduce traffic using rate limits or temporarily disable the expensive path.
- Lower page size, stop batch jobs, or roll back the deploy.
- Restart only after capturing thread dump, heap histogram, and GC evidence.
- Increase memory only as a temporary mitigation with a rollback plan.

## Long-Term Fix
- Add pagination/keyset pagination, streaming, or backpressure.
- Fix N+1 queries with fetch joins, projections, or batch size.
- Reduce allocation hot paths and remove unnecessary object copies.
- Tune heap and GC only after code/data-shape fixes are understood.

## Prevention Strategy
- Track GC pause SLOs and allocation-rate dashboards.
- Load test large payloads and worst-case pages.
- Add alerts for old-gen occupancy, allocation rate, and container memory.
- Require performance review for endpoints that return collections.

## Interview Explanation
"I first prove whether GC is cause or symptom by correlating pause time, heap after GC, allocation rate, and request latency. I collect `jcmd` heap info, class histogram, and thread dumps before restart. Then I mitigate traffic or roll back, and fix the allocation source such as N+1, unbounded pagination, or oversized cache entries."

## Weekly Tracking Format
| Week | Scenario | Evidence captured | Root cause | Score /100 | Pass/fail | Recovery |
|---|---|---|---|---:|---|---|
| | GC pressure | GC log + heap histogram + latency chart | | | | |

## Score Out of 100
- 25: Symptom isolation and first checks.
- 25: Correct Linux/JVM evidence.
- 20: Root cause and mitigation.
- 20: Long-term prevention.
- 10: Clear interview explanation.

## Pass/Fail Criteria
- Pass: 80+ and can name the exact evidence needed before restart.
- Fail: Below 80, or cannot separate memory leak, high allocation rate, and container OOM.

## Recovery Plan
Redo this drill with one heap histogram, one GC log sample, and one endpoint-level allocation hypothesis.

## Common Mistakes
- Calling every GC issue a memory leak.
- Restarting before evidence collection.
- Tuning GC flags before fixing unbounded data loading.

## Self-Check
- [ ] I can explain young GC, old GC, and allocation rate.
- [ ] I can capture heap and thread evidence.
- [ ] I can propose mitigation without hiding root cause.

## Practical Example
A feed endpoint starts returning 500 posts with comments. GC pause rises from 20 ms to 700 ms. The fix is keyset pagination plus DTO projection, not only a larger heap.

## Interview Questions
- How do you debug high GC pause in a Spring Boot service?
- How do you distinguish memory leak from high allocation rate?
- What evidence do you collect before restarting a Java process?
