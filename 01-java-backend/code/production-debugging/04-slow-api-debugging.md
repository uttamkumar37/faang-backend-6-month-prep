# Slow API Debugging

## Purpose
Diagnose p95/p99 latency in Java REST APIs across app, database, cache, and downstream services.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- RED metrics by route: rate, errors, duration.
- p50/p95/p99 split by endpoint.
- Thread pool active count, queue depth, rejected tasks.
- DB pool wait time and query latency.
- Downstream timeout and retry count.

## Commands
```bash
curl -w '@curl-format.txt' -o /dev/null -s https://service/api
jcmd <pid> Thread.print > threads.txt
ss -tanp | grep <port>
pidstat -d -p <pid> 1
```

## Logs To Inspect
- Correlation-id trace for slow requests.
- Access logs with route and latency.
- Timeout/retry logs.
- Recent deploy, config, feature flag changes.

## Root Cause Patterns
- N+1 queries or missing index.
- Blocking call inside limited thread pool.
- Connection pool exhaustion.
- Cache miss storm or hot key.
- Downstream retry amplification.

## Fix Strategy
Mitigate with timeout reduction, load shedding, cache warmup, rollback, or disabling slow feature. Fix query plans, batch calls, add backpressure, and tune pools after measuring.

## Prevention Strategy
Route-level SLOs, distributed tracing, pool saturation alerts, timeout budgets, and performance tests for high-cardinality requests.

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
Example: p99 jumps but CPU is normal. Traces show 800 ms DB wait before query execution, indicating connection pool exhaustion rather than SQL execution time.
