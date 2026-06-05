# Thread Pool Exhaustion

## Purpose
Diagnose blocked Java executor pools, servlet thread saturation, and rejection storms.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Active threads, pool size, queue depth.
- Task execution time and wait time.
- Rejected task count.
- Servlet container busy threads.
- Downstream latency called by pool tasks.

## Commands
```bash
jcmd <pid> Thread.print > threads.txt
grep -E "BLOCKED|WAITING|pool-|http-nio" threads.txt | head -80
pidstat -t -p <pid> 1
```

## Logs To Inspect
- RejectedExecutionException.
- Timeout logs from downstream clients.
- Request logs waiting near timeout boundary.
- Deployment config for pool size and queue capacity.

## Root Cause Patterns
- Blocking IO inside a small compute pool.
- Unbounded queue hiding saturation until latency explodes.
- Pool shared between unrelated workloads.
- Missing timeout on external call.
- Deadlock or lock convoy.

## Fix Strategy
Mitigate with traffic shedding, increasing replicas, pausing non-critical work, or reverting the bad path. Fix by separating pools, bounding queues, adding timeouts, using bulkheads, and removing blocking calls.

## Prevention Strategy
Expose executor metrics, use bounded queues, define timeout budgets, and load test slow downstream behavior.

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
Example: Async notification executor has 20 threads and an unbounded queue. Provider latency rises to 5 seconds, queue grows, and request callbacks time out. Add bounded queue, provider timeout, and DLQ handoff.
