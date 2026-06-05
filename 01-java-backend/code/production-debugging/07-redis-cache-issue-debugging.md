# Redis Cache Issue Debugging

## Purpose
Diagnose cache latency, stale data, hot keys, evictions, and cache stampedes.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Hit ratio by use case.
- Redis latency percentiles.
- Evictions, memory fragmentation, connected clients.
- Hot key frequency and command mix.
- Application fallback latency on cache miss.

## Commands
```bash
redis-cli INFO
redis-cli SLOWLOG GET 20
redis-cli --hotkeys
redis-cli LATENCY DOCTOR
redis-cli MEMORY STATS
```

## Logs To Inspect
- Cache miss spikes.
- Serialization/deserialization errors.
- Timeout and connection pool errors.
- Invalidation events.

## Root Cause Patterns
- Big key blocking Redis.
- TTL expired for many keys at once.
- Missing invalidation after write.
- Cache-aside stampede after deploy.
- Evictions due to memory pressure.

## Fix Strategy
Mitigate by bypassing cache for corrupted keys, warming critical keys, rate limiting miss path, or increasing memory temporarily. Fix with jittered TTL, single-flight loading, key splitting, and explicit invalidation.

## Prevention Strategy
Hot-key alerts, TTL jitter, maximum value size, cache contract tests, and dashboards separating cache hit latency from origin latency.

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
Example: Checkout latency spikes when a pricing cache expires at midnight. Add TTL jitter and single-flight recomputation so one request refreshes while others serve stale data briefly.
