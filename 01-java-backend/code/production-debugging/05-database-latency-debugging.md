# Database Latency and Connection Pool Debugging

## Purpose
Diagnose slow database behavior from query plan to locks to application connection pools.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Query execution time versus pool wait time.
- Active connections, idle connections, acquisition timeout.
- Lock waits and deadlocks.
- Rows scanned versus rows returned.
- Replication lag for read replicas.

## Commands
```sql
EXPLAIN ANALYZE SELECT ...;
SELECT * FROM pg_stat_activity WHERE state <> 'idle';
SELECT * FROM pg_locks WHERE NOT granted;
```
```bash
jcmd <pid> Thread.print | grep -i jdbc -C 3
```

## Logs To Inspect
- Slow query log.
- Deadlock log.
- Application timeout stack traces.
- Migration timestamps.

## Root Cause Patterns
- Missing composite index.
- Query not sargable due to function on indexed column.
- Long transaction holding locks.
- N+1 ORM fetch pattern.
- Pool too small or connections leaked.

## Fix Strategy
Mitigate by killing runaway query, rolling back bad migration, reducing traffic, or disabling expensive filter. Fix with index, query rewrite, transaction shrink, fetch join/batching, or pool leak repair.

## Prevention Strategy
Query-plan review, slow-query alerts, connection pool dashboards, migration load tests, and ORM N+1 tests.

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
Example: API timeout shows 50 ms query execution but 900 ms Hikari acquisition time. Root cause is pool exhaustion from a missing `try-with-resources` in a reporting path.
