# Memory Leak and GC Pressure Debugging

## Purpose
Separate true leaks from high allocation rate and diagnose heap growth safely.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Heap used after full GC.
- Old generation occupancy trend.
- Allocation rate.
- GC pause time and frequency.
- Container memory and OOM kill events.

## Commands
```bash
jcmd <pid> GC.heap_info
jstat -gcutil <pid> 1s 20
jcmd <pid> GC.class_histogram > histo.txt
jmap -histo:live <pid> | head -40
jcmd <pid> GC.heap_dump /tmp/service.hprof
```

## Logs To Inspect
- GC logs around pause spikes.
- OOM stack traces.
- Cache growth and batch size logs.
- Recent feature flags increasing payload retention.

## Root Cause Patterns
- Static map or unbounded in-memory cache.
- Listener/subscriber not removed.
- Large request payloads retained in logs or MDC.
- Batch job collecting all rows before processing.
- Connection/resource leak holding object graphs.

## Fix Strategy
Reduce blast radius with restart or scale-out only after collecting evidence. Bound cache size, stream batches, close resources, remove listener references, and add payload limits.

## Prevention Strategy
Use bounded collections, cache maximum size, heap alerts after-GC, load tests with long soak, and heap dump review in severe incidents.

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
Example: Old gen rises after every full GC. Histogram shows millions of `OrderDto` retained by a static retry map. Fix by TTL-bounding retries and adding eviction metrics.
