# Project 5: Architecture — Distributed Job Scheduler

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Admin / Client Layer                        │
│   Developer CLI      Admin REST API          Grafana Dashboard       │
└──────────────┬──────────────────┬────────────────────────────────────┘
               │                  │
               ▼                  ▼
┌──────────────────────────────────────────────────────────────────────┐
│                       API Gateway (Spring Cloud Gateway)              │
│           Auth (JWT), rate limiting, request logging                  │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                ▼                ▼                ▼
      ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐
      │  Job Registry │  │  Admin API   │  │  Execution History   │
      │  Service      │  │  Service     │  │  Query Service       │
      │  (register,   │  │  (pause,     │  │  (paginated reads,   │
      │   update,del) │  │   resume,    │  │   audit trail)       │
      └──────┬────────┘  │   trigger)   │  └──────────────────────┘
             │           └──────┬───────┘
             └──────────┬───────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Scheduler Cluster (StatefulSet)                   │
│                                                                      │
│  ┌────────────────────────┐    ┌────────────────────────┐           │
│  │  Scheduler Node 1      │    │  Scheduler Node 2      │           │
│  │  [LEADER]              │    │  [FOLLOWER / hot-standb]│           │
│  │                        │    │                         │           │
│  │  Time-Wheel Engine     │    │  Time-Wheel Engine      │           │
│  │  Leader Lock Holder    │    │  Watching heartbeat     │           │
│  │  Trigger Loop (1s)     │    │  Ready to take over     │           │
│  └────────────┬───────────┘    └─────────────────────────┘           │
│               │                                                      │
│   Leader election via PostgreSQL advisory lock                       │
│   Heartbeat refresh every 2s, TTL 10s                                │
└───────────────┬──────────────────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       Execution Dispatch Layer                       │
│                                                                      │
│   Acquire advisory lock(jobId, slotEpoch)                           │
│         │                                                            │
│         ▼                                                            │
│   INSERT execution_history(status=TRIGGERED)                        │
│         │                                                            │
│         ├──► HTTP Executor (webhook)                                 │
│         ├──► Kafka Executor (produce message)                        │
│         ├──► gRPC Executor (unary call)                              │
│         └──► Internal Handler (Java bean invoke)                    │
│                                                                      │
│   Execution reports SUCCESS/FAILED → UPDATE execution_history       │
└──────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌──────────────────────────────────────────────────────┐
│                   Data Layer                          │
│                                                      │
│  PostgreSQL                  Redis Cluster           │
│  ├── jobs                    ├── leader lock token   │
│  ├── job_triggers            ├── hot job metadata     │
│  ├── job_dependencies        └── rate-limit counters │
│  ├── execution_history                               │
│  └── scheduler_heartbeat                             │
│                                                      │
│  Kafka                                               │
│  ├── job-events              (lifecycle events)      │
│  └── job-dlq                 (dead-letter queue)     │
└──────────────────────────────────────────────────────┘
```

---

## Sequence: Cron Job Trigger (Leader Node)

```
Every 1 second — Trigger Loop on Leader Node:
  1. Query: SELECT * FROM job_triggers
             WHERE next_fire_time <= NOW() + INTERVAL '1s'
             AND status = 'PENDING'
             LIMIT 500
             FOR UPDATE SKIP LOCKED   ← prevents follower racing

  2. For each due trigger:
     a. Acquire PostgreSQL advisory lock: pg_try_advisory_lock(jobId, slotEpoch)
        If lock NOT acquired → another node handling it (skip)
     b. INSERT execution_history (job_id, status=TRIGGERED, triggered_at=NOW())
     c. Dispatch to executor (async — CompletableFuture, virtual thread)
     d. UPDATE job_triggers: next_fire_time = next_cron_time(cron_expr, NOW())

  3. Executor reports result:
     UPDATE execution_history SET status=SUCCESS/FAILED, completed_at=NOW()
     IF status=FAILED AND attempt < max_retries:
       Schedule retry_trigger at NOW() + backoff_duration
     ELSE IF status=FAILED:
       Produce to job-dlq Kafka topic
```

---

## Sequence: Leader Election

```
All nodes start up:
  Each node races to execute:
    INSERT INTO scheduler_heartbeat (node_id, updated_at=NOW())
    WITH pg_advisory_lock(SCHEDULER_LEADER_LOCK_KEY)

  First node to acquire lock = LEADER
  Leader refreshes heartbeat every 2 seconds

Follower health check (every 3 seconds):
  SELECT updated_at FROM scheduler_heartbeat WHERE is_leader=true
  IF NOW() - updated_at > 10s:
    Attempt INSERT + pg_advisory_lock → if success, become leader
    Publish LEADER_CHANGE event to Kafka (admin notification)
```

---

## DAG Execution Flow

```
Job C depends on Job A and Job B

  A ──┐
      ├──► C (BLOCKED — waiting for A and B)
  B ──┘

On A completes:
  INSERT job_dependency_completions (C, A)
  Check: SELECT COUNT(*) FROM job_dependencies WHERE child=C
         vs SELECT COUNT(*) FROM job_dependency_completions WHERE child=C
  Both parents done? → Enqueue C for immediate trigger

On B completes:
  Same check → now both parents complete → C is triggered
```

---

## Retry Backoff Strategy

```
Attempt 1 → immediate
Attempt 2 → wait  30s
Attempt 3 → wait  2m
Attempt 4 → wait  8m
Attempt 5 → wait 30m  (then → DLQ)

Formula: delay = base_delay × 2^(attempt-1) with ±10% jitter
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Leader node crashes mid-dispatch | Trigger might be lost | Advisory lock TTL expires; follower takes over and re-dispatches |
| Duplicate dispatch by two nodes | Duplicate execution | `pg_try_advisory_lock(jobId, slotEpoch)` — exactly one node acquires |
| Clock skew between nodes | Missed or double-fired cron slot | All time decisions use PostgreSQL NOW() as single clock source |
| Executor HTTP target down | Job fails | Retry with backoff; DLQ after max attempts |
| PostgreSQL unavailable | Scheduler paused | Jobs queue up; leader loop retries with circuit breaker |
| Kafka unavailable | DLQ and events lost | Use transactional outbox for event publishing; retry until Kafka recovers |

---

## Observability

```yaml
# Key metrics
scheduler_triggered_total{job_name, executor_type}
scheduler_execution_duration_seconds{job_name}   # P50/P95/P99
scheduler_trigger_lag_seconds{job_name}          # wall_clock - scheduled_time
scheduler_failures_total{job_name, failure_type}
scheduler_dlq_depth                              # dead-letter queue backlog
scheduler_leader_elections_total                 # how often leadership changes
```

---

## Trade-offs

| Decision | Alternative | Why this choice |
|---|---|---|
| PostgreSQL advisory lock for leader election | ZooKeeper / etcd | Eliminates ZK ops burden; PostgreSQL already in stack |
| `FOR UPDATE SKIP LOCKED` for trigger scan | Optimistic retry | Prevents thundering-herd on multi-node clusters |
| Time-wheel in-memory | Poll DB every second | O(1) vs O(n) for 100K jobs; reduces DB read pressure |
| Async executor dispatch | Synchronous | Trigger loop must not block on slow HTTP targets |
