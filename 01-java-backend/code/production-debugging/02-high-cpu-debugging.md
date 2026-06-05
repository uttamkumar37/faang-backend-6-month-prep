# High CPU Debugging

## Purpose
Diagnose Java CPU saturation from host metrics to thread-level root cause.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Process CPU percent and load average.
- CPU by thread.
- Request rate and p99 latency.
- GC CPU and allocation rate.
- Lock contention and runnable thread count.

## Commands
```bash
top -H -p <pid>
printf '%x
' <native_thread_id>
jcmd <pid> Thread.print > threads.txt
jstat -gcutil <pid> 1s 10
perf top -p <pid>
```

## Logs To Inspect
- Recent deploy markers.
- Route-level access logs with latency.
- Error spikes or retry storms.
- GC logs if CPU aligns with collection activity.

## Root Cause Patterns
- Infinite loop or bad termination condition.
- Expensive JSON serialization on large payloads.
- Catastrophic regex backtracking.
- Retry storm after dependency failure.
- GC pressure from high allocation rate.

## Fix Strategy
Mitigate with rollback, traffic shift, rate limiting, or disabling the hot path. Fix the algorithm, bound retries, precompile safer parsing, reduce allocation, or move expensive work async.

## Prevention Strategy
Add CPU and runnable-thread alerts, load tests for large payloads, retry budgets, and profiling in performance-sensitive code reviews.

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
Example: CPU is 390 percent on a 4-core box. `top -H` finds one hot Java thread; thread dump shows regex validation in a loop. Mitigate by disabling the rule, then replace regex with bounded parser tests.
