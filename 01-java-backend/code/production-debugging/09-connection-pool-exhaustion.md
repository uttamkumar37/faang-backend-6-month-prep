# Connection Pool Exhaustion

## Purpose
Diagnose incidents where a Java service cannot borrow database connections quickly enough, causing slow APIs, timeouts, and cascading failures.

## How to Use
Use this playbook during production-debugging drills from month 3 onward. Practice explaining pool saturation, database latency, transaction scope, and mitigation in one coherent SDE-II answer.

## Symptoms
- API p95/p99 rises and many requests timeout.
- Logs show `Connection is not available`, Hikari timeout, or SQL transient connection exceptions.
- DB CPU may be normal while application threads wait on the pool.
- Thread dumps show many threads blocked in `HikariPool.getConnection`.

## First 5 Checks
1. Check Hikari active, idle, pending, and timeout metrics.
2. Check slow query logs and top endpoints using DB.
3. Capture thread dump to prove request threads wait on pool borrow.
4. Check transaction duration and external calls inside transactions.
5. Check recent deploys, traffic spike, pool-size changes, and DB max connections.

## Metrics to Inspect
- `hikaricp.connections.active`, `idle`, `pending`, `timeout`.
- API latency/error rate by endpoint.
- DB query latency, lock wait time, connection count.
- JVM thread count and executor queue depth.

## Logs to Inspect
- Hikari timeout logs.
- Slow SQL logs and application SQL timing logs.
- Deployment/config logs for pool-size or query changes.
- DB deadlock/lock-wait logs where relevant.

## Linux Commands
```bash
top
htop
ps -o pid,pcpu,pmem,cmd -p <pid>
docker logs <backend-container> --since 30m
docker stats
ss -tanp | grep 3306
netstat -tanp | grep 3306
lsof -i :3306
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

## JVM Commands
```bash
jstack <pid> > jstack.txt
jcmd <pid> Thread.print | grep -i Hikari -C 5
jcmd <pid> GC.heap_info
jcmd <pid> VM.system_properties
```

## Database Commands Where Relevant
```sql
SHOW PROCESSLIST;
SHOW STATUS LIKE 'Threads_connected';
SHOW VARIABLES LIKE 'max_connections';
EXPLAIN SELECT * FROM posts WHERE user_id = 10 ORDER BY created_at DESC;
SHOW ENGINE INNODB STATUS;
```

## Root Cause Patterns
- Slow query or missing index makes each connection stay busy too long.
- Transaction wraps remote API calls or expensive CPU work.
- N+1 query multiplies connection usage per request.
- Pool size too small for traffic or too large for DB capacity.
- Connection leak due to manual JDBC resource handling.

## Immediate Mitigation
- Roll back the offending deploy or disable the expensive endpoint.
- Reduce traffic, lower concurrency, or shed load.
- Temporarily increase pool size only if DB has connection headroom.
- Kill clearly stuck DB sessions after confirming impact.

## Long-Term Fix
- Optimize query plans and add indexes.
- Shorten transaction boundaries and remove external calls from transactions.
- Add request-level timeout, DB query timeout, and circuit breaker.
- Add pool metrics alerts and load tests for peak concurrency.

## Prevention Strategy
- Alert on pending connections and pool timeout count.
- Review every `@Transactional` method for scope and remote calls.
- Use projections/fetch joins to prevent N+1 query amplification.
- Capacity test DB and application pool settings together.

## Interview Explanation
"I check whether the database is slow or the application is exhausting its pool. Hikari pending/active metrics plus a thread dump prove pool wait. Then I inspect slow queries and transaction scope. The mitigation is rollback, load shedding, or temporary pool tuning; the durable fix is query/index work, shorter transactions, and alerts."

## Weekly Tracking Format
| Week | Scenario | Pool evidence | DB evidence | Score /100 | Pass/fail | Recovery |
|---|---|---|---|---:|---|---|
| | Slow feed API | Pending connections high | Missing `(user_id, created_at)` index | | | |

## Score Out of 100
- 25: Correct symptom isolation.
- 25: Pool/JVM/DB evidence.
- 20: Immediate mitigation.
- 20: Long-term fix and prevention.
- 10: Clear interview explanation.

## Pass/Fail Criteria
- Pass: 80+ and can distinguish DB latency from pool starvation.
- Fail: Only suggests increasing pool size without query/transaction analysis.

## Recovery Plan
Redo one slow-query scenario and one long-transaction scenario. For each, capture metrics, thread-dump evidence, mitigation, and prevention.

## Common Mistakes
- Increasing pool size beyond DB capacity.
- Ignoring transaction scope.
- Debugging SQL only after missing obvious pool pending metrics.

## Self-Check
- [ ] I can name the Hikari metrics to alert on.
- [ ] I can explain why pool size is not a universal fix.
- [ ] I can identify external calls inside transactions.

## Practical Example
A comments endpoint uses N+1 queries for author profiles. Under traffic, each request holds a connection long enough to saturate a pool of 10. Fix with fetch join/projection, index review, and an endpoint latency test.

## Interview Questions
- How do you debug Hikari connection timeout errors?
- How do you size an application DB pool?
- Why can increasing the connection pool make the incident worse?
